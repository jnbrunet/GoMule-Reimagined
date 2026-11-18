package gomule.d2i;

import com.google.common.io.BaseEncoding;
import gomule.d2i.D2SharedStash.D2SharedStashPane;
import gomule.item.D2Item;
import gomule.util.D2BitReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class D2SharedStashReader {

    static final byte[] STASH_HEADER_START = BaseEncoding.base16().decode("55AA55AA");

    // A sanity bound on the marker scan, not a format limit. It was 7 (the classic layout) until
    // the 2026-11-03 update: the game then wrote the live stash with 17 panes (ten new ordinary
    // tabs after the stackable one), and "> 7" rejected the whole file. See
    // D2SharedStash.getTabPaneIndices() for how they are shown.
    static final int MAX_PANES = 32;

    public D2SharedStash readStash(String filename) throws Exception {
        return readStash(filename, new D2BitReader(filename));
    }

    public D2SharedStash readStash(String filename, D2BitReader bitReader) throws Exception {
        int[] stashHeaderOffsets = bitReader.findBytes(STASH_HEADER_START);
        if (stashHeaderOffsets.length > D2SharedStashReader.MAX_PANES) throw new RuntimeException("Stash unsupported");
        byte[] fileContent = bitReader.getFileContent();
        List<D2SharedStashPane> result = new ArrayList<>();
        for (int paneIndex = 0; paneIndex < stashHeaderOffsets.length; paneIndex++) {
            int paneStart = stashHeaderOffsets[paneIndex];
            // Each pane's start offset (and so, each pane's exact byte span) is known up front by
            // scanning the raw bytes for the "55AA55AA" marker, independent of how any pane's
            // contents get interpreted -- so a pane that fails to parse (the try/catch below)
            // doesn't prevent finding or reading whatever panes come after it, and originalBytes
            // (this pane's exact span, used by D2SharedStashPane.isIncomplete() panes to save
            // back byte-for-byte instead of attempting -- and risking corrupting -- a
            // reconstruction GoMule doesn't have enough understanding to get right; see
            // fromItemsPartial()'s comment) is always available even when the pane's own header
            // or item count turns out to be garbage.
            int paneEnd = (paneIndex + 1 < stashHeaderOffsets.length) ? stashHeaderOffsets[paneIndex + 1] : fileContent.length;
            byte[] originalBytes = Arrays.copyOfRange(fileContent, paneStart, paneEnd);
            bitReader.set_byte_pos(paneStart);
            try {
                result.add(readSharedStashPane(bitReader, filename, originalBytes));
            } catch (Exception pEx) {
                result.add(D2SharedStashPane.fromItemsPartial(new ArrayList<>(), 0,
                        "Pane " + (paneIndex + 1) + " of " + stashHeaderOffsets.length + " failed to parse: " + pEx,
                        originalBytes));
            }
        }
        return new D2SharedStash(filename, result, fileContent);
    }

    private D2SharedStashPane readSharedStashPane(D2BitReader bitReader, String filename, byte[] originalBytes) throws Exception {
        int stashPaneStart = bitReader.get_byte_pos();
        int stashPaneEnd = stashPaneStart + originalBytes.length;
        D2SharedStash.Header header = D2SharedStash.Header.fromBytes(bitReader);
        // Same version drift as the .d2s character format (see D2Character.readChar() and
        // D2Item.setFormatVersion()): the header layout itself hasn't changed, but a strict
        // equality check rejects every later D2R patch. Accept 99 and anything newer.
        if (header.getVersion() < 99)
            throw new RuntimeException("Incorrect shared stash version: " + header.getVersion());
        D2Item.setFormatVersion(header.getVersion());
        // Find this pane's "JM" item-list marker, but only WITHIN the pane's own byte span. The scan
        // is otherwise unbounded: with no JM after this point it returns -1, which set_byte_pos turns
        // into a garbage position that then "reads" an absurd item count and dumps a stack trace per
        // bogus item. A real file exposes exactly this -- "ModernSharedStashSoftCoreV2.d2i" ends in a
        // declared-but-unused ~5.5KB trailing pane that is all zero bytes: a valid header (version
        // 105) with no JM and no items (its garbage count came out 21930 = 0x55AA, i.e. the next
        // marker's bytes read from position -1). Treat a pane with no in-span JM as an empty pane
        // preserved verbatim, rather than parsing zero padding as thousands of phantom items.
        int jmOffset = bitReader.findNextFlag("JM", bitReader.get_byte_pos());
        if (jmOffset < 0 || jmOffset >= stashPaneEnd) {
            return D2SharedStashPane.fromItemsPartial(new ArrayList<>(), header.getGold(),
                    "No item-list (JM) marker in this pane -- empty/padding pane preserved verbatim", originalBytes);
        }
        bitReader.set_byte_pos(jmOffset);
        bitReader.skipBytes(2);
        int numItems = (int) bitReader.read(16);
        List<D2Item> result = new ArrayList<>();
        // Same situation as D2Character.readItems(): there's no reliable way to skip just one bad
        // item and resync on the next, since each item's length is only known once it has parsed
        // successfully. Keep everything read so far in this pane and stop.
        for (int i = 0; i < numItems; i++) {
            try {
                result.add(D2Item.readItem(filename, bitReader, 75, i + 1 < numItems));
            } catch (Exception pEx) {
                String reason = "Item " + (i + 1) + " of " + numItems + " failed to parse: " + pEx;
                try {
                    return D2SharedStashPane.fromItemsPartial(result, header.getGold(), reason, originalBytes);
                } catch (Exception pGridEx) {
                    // Building the grid for the items read so far failed too (e.g. one of them
                    // has an overlapping/corrupt position, itself a symptom of the same
                    // misalignment that made the next item fail) -- fall back to an empty pane
                    // rather than letting this escape and be mistaken, by the per-pane catch in
                    // readStash(), for a completely different pane's failure.
                    return D2SharedStashPane.fromItemsPartial(new ArrayList<>(), header.getGold(),
                            reason + "; also failed to keep the items read before that: " + pGridEx, originalBytes);
                }
            }
        }
        int calculatedLength = bitReader.get_byte_pos() - stashPaneStart;
        if (calculatedLength != header.getLength())
            throw new RuntimeException("Incorrect shared stash length: " + calculatedLength + " expected: " + header.getLength());
        try {
            return D2SharedStashPane.fromItems(result, header.getGold());
        } catch (RuntimeException pGridEx) {
            // Every item parsed and the pane's byte length matches its header exactly, so the read is
            // correct -- yet constructPaneGrid found two items sharing a cell. That is the signature
            // of a "Modern"/DLC auto-arranging STACKABLE tab (this file's rune/gem/orb tab is one: 78
            // runes, gems, Orbs, Pandemonium keys and Uber materials, many stacked on the same slot),
            // which doesn't use the one-item-per-cell grid GoMule tiles panes on. Keep the correctly
            // parsed items so they still appear in the flat item list and search, but mark the pane
            // incomplete: that writes it back verbatim (originalBytes) instead of reconstructing a
            // layout GoMule doesn't model, and hides it from the visible tab strip (see
            // D2SharedStash.getVisibleTabCount()) -- how these stackable tabs were always meant to be
            // handled, only now with their contents recovered instead of dropped.
            return D2SharedStashPane.fromItemsStacked(result, header.getGold(),
                    "Stackable tab (" + result.size() + " items) preserved verbatim: " + pGridEx.getMessage(), originalBytes);
        }
    }
}
