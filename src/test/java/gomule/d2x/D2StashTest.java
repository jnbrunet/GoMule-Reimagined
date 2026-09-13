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

    // Two independent bugs made magic items show Required Level 1, both verified against this
    // real Reimagined stash ("gomule.d2x"):
    //   1. D2Item read the affix tables' name column as "Name", but magicprefix.txt and
    //      magicsuffix.txt spell it "name" -- the lookup is case-sensitive, so it returned "".
    //      Items lost their affix names AND the affixes' levelreq, leaving the base's own.
    //   2. Affix ids are 1-based (0 = no affix), so id N is row N-1. Indexing the row directly
    //      resolved every affix one row too far.
    // The jewel below is the ground truth for both: in game it is "Ivory Jewel of Thunder",
    // Required Level 56 -- prefix id 156 -> row 155 (Ivory, jewl, levelreq 56), suffix id 85 ->
    // row 84 (of Thunder, jewl, levelreq 49). Before the fix it read as "Enlightened Jewel of
    // Blight" (rows 156/85: Miocene on body armour, of Blight on weapons) with level 3.
    @Test
    public void magicItemsTakeTheirAffixNamesAndLevelRequirement() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");

        List<D2Item> items = loadStashFixture();

        D2Item jewel = findByName(items, "Ivory Jewel", "of Thunder");
        assertEquals(56, jewel.getReqLvl());
        // A charm, for the same reason: Coral (lcha) + of Vitality (lcha), the higher wins.
        assertEquals(61, findByName(items, "Coral Grand Charm", "of Vitality").getReqLvl());
    }

    // A unique's level comes from the row its id resolves to -- the row the displayed name also
    // comes from -- and the game never cross-checks that row's "code" against the item's own base
    // code. D2Item used to require a match, so an item whose base code had drifted from its unique
    // row kept the row's name but fell back to the BASE item's levelreq, i.e. 1 for a quiver.
    // Reimagined re-pointed "Flames of Sanctuary" (unique id 1470) from Arrows (aqv) to Bolts
    // (cqv), and this stash holds both an older arrows copy and a current bolts copy: the arrows
    // one is exactly the mismatch case, and both are Required Level 80 in the current data.
    @Test
    public void uniquesKeepTheirLevelEvenWhenTheirBaseCodeDriftedFromTheirRow() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");

        List<D2Item> items = loadStashFixture();

        assertEquals(80, findByType(items, "Flames of Sanctuary", "cqv").getReqLvl());
        assertEquals(80, findByType(items, "Flames of Sanctuary", "aqv").getReqLvl());
    }

    private List<D2Item> loadStashFixture() throws Exception {
        String stashFile = new File(Resources.getResource("d2x/reimaginedCharms.d2x").toURI()).getAbsolutePath();
        return new D2Stash(stashFile).getItemList();
    }

    // Item names carry D2R colour codes in the middle ("Ivory Jewel<c2>*<c3> of Thunder"), so
    // match on the affixes at either end rather than on the whole string.
    private D2Item findByName(List<D2Item> pItems, String pStart, String pEnd) {
        return pItems.stream()
                .filter(i -> i.getItemName().startsWith(pStart) && i.getItemName().endsWith(pEnd))
                .findFirst()
                .orElseThrow(() -> new AssertionError(pStart + " ... " + pEnd + " not found"));
    }

    private D2Item findByType(List<D2Item> pItems, String pName, String pItemType) {
        return pItems.stream()
                .filter(i -> pName.equals(i.getItemName()) && pItemType.equals(i.getItem_type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(pName + " (" + pItemType + ") not found"));
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
