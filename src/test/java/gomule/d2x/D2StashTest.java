package gomule.d2x;

import com.google.common.io.Resources;
import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.item.D2Item;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class D2StashTest {

    // D2Stash (the clipboard) is the one item reader that never called D2Item.setFormatVersion()
    // at all -- unlike .d2s/.d2i, its own "ATMA" file format has no field that says what D2R
    // version the items inside were copied from, so it relied entirely on whatever a previously-
    // opened character/stash in the same session had already set the (process-wide, static)
    // format version to. That's harmless if a current-format file happened to load first, but the
    // clipboard is constructed during D2FileManager's own startup (D2ViewClipboard.getInstance(),
    // called from createRightPane()) -- before the user has opened anything -- so in practice it
    // always ran with the version still at its old/legacy default. A real clipboard item
    // ("Oakheart", copied from a current D2R Reimagined character) crashed with the same "misread
    // a stat with no Save Bits" symptom as every other post-v99 trailing-bit gap fixed elsewhere
    // in this codebase, because none of those fixes were being applied.
    //
    // Reproduced here with a synthetic ATMA file wrapping a real Oakheart's exact bytes (pulled
    // from an already-validated real shared-stash fixture), with the format version explicitly
    // reset to the legacy default first to simulate "GoMule just started, nothing opened yet" --
    // confirmed this exact setup throws D2Item's real error, byte-for-byte matching the player's
    // report, with D2Stash.readAtmaItems()'s fix reverted, and is fixed by it.
    @Test
    public void clipboardLoadsRealItemEvenWhenNothingElseHasSetTheFormatVersionYet() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");

        String stashFile = new File(
                Resources.getResource("sharedStash/ModernSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();
        D2SharedStash stash = new D2SharedStashReader().readStash(stashFile);
        D2Item oakheart = ((List<D2Item>) stash.getItemList()).stream()
                .filter(i -> "Oakheart".equals(i.getItemName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Oakheart not found"));
        byte[] itemBytes = oakheart.get_bytes();

        File tempFile = File.createTempFile("clipboard", ".d2x");
        Files.write(tempFile.toPath(), buildAtmaClipboardFile(itemBytes));

        // Simulates "GoMule just started, no character/stash opened yet" -- mirrors D2ItemTest's
        // resetItemFormatVersion() pattern.
        D2Item.setFormatVersion(99);

        D2Stash clipboardStash = new D2Stash(tempFile.getAbsolutePath());

        assertEquals(1, clipboardStash.getNrItems());
        assertEquals("Oakheart", ((D2Item) clipboardStash.getItemList().get(0)).getItemName());
    }

    // Builds the layout D2Stash.readAtmaItems() expects: "D2X" + numItems(16) + versionNr(16) +
    // checksum(32, computed with these same 4 bytes treated as zero) + item bytes from byte 11.
    private byte[] buildAtmaClipboardFile(byte[] itemBytes) {
        byte[] clipboardBytes = new byte[11 + itemBytes.length];
        clipboardBytes[0] = 'D';
        clipboardBytes[1] = '2';
        clipboardBytes[2] = 'X';
        clipboardBytes[3] = 1; // numItems
        clipboardBytes[4] = 0;
        clipboardBytes[5] = 99; // versionNr -- ATMA's own format version, always 99
        clipboardBytes[6] = 0;
        System.arraycopy(itemBytes, 0, clipboardBytes, 11, itemBytes.length);
        int checksum = calculateAtmaChecksum(clipboardBytes);
        clipboardBytes[7] = (byte) (checksum & 0xFF);
        clipboardBytes[8] = (byte) ((checksum >> 8) & 0xFF);
        clipboardBytes[9] = (byte) ((checksum >> 16) & 0xFF);
        clipboardBytes[10] = (byte) ((checksum >> 24) & 0xFF);
        return clipboardBytes;
    }

    // Mirrors D2Stash.calculateAtmaCheckSum()'s algorithm exactly (private there, and there's no
    // way to build a file this class will accept without it).
    private int calculateAtmaChecksum(byte[] data) {
        long checksum = 0;
        for (int i = 0; i < data.length; i++) {
            long b = data[i] & 0xFF;
            if (i >= 7 && i <= 10) b = 0;
            long upshift = checksum << 33 >>> 32;
            long add = b + ((checksum >>> 31) == 1 ? 1 : 0);
            checksum = upshift + add;
        }
        return (int) checksum;
    }
}
