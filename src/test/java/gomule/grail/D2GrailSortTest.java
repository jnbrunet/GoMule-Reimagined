package gomule.grail;

import gomule.item.D2ItemRenderer;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grail list is ordered by the level an entry requires -- the order the player progresses
 * through -- rather than alphabetically, on all three tabs. The level itself comes from
 * D2GrailRequiredLevel, which is also what a missing item's tooltip prints, so the ordering and the
 * displayed number can never disagree.
 */
public class D2GrailSortTest {

    /**
     * The three shapes the level comes in, each against a real row: a unique's own "lvl req" (which
     * overrides its base item's -- Harlequin Crest is 62 on a Shako whose own levelreq is lower), a
     * set piece's, and a runeword's highest rune (Bulwark is Shael 29 / Io 35 / Sol 27, and the
     * mod's own page says Level 35).
     */
    @Test
    public void requiredLevelFollowsEachEntryKindsOwnRule() {
        D2TxtFile.constructTxtFiles("./d2111");
        assertEquals(Integer.valueOf(62), D2GrailRequiredLevel.of(byName(D2GrailKey.Type.UNIQUE, "Harlequin Crest")));
        assertEquals(Integer.valueOf(85), D2GrailRequiredLevel.of(byName(D2GrailKey.Type.UNIQUE, "Schaefer's Hammer")));
        assertEquals(Integer.valueOf(47), D2GrailRequiredLevel.of(byName(D2GrailKey.Type.SET, "Immortal King's Will")));
        assertEquals(Integer.valueOf(2), D2GrailRequiredLevel.of(byName(D2GrailKey.Type.SET, "Arctic Binding")));
        assertEquals(Integer.valueOf(35), D2GrailRequiredLevel.of(byName(D2GrailKey.Type.RUNEWORD, "Bulwark")));

        // "Law" is Hel + Hel, both levelreq 0, and carries its requirement as a "levelreq" property
        // instead -- the one word where the two readings differ, and the reason the ordering key
        // takes the higher of them rather than sorting it first as a level-0 item.
        assertEquals(Integer.valueOf(26), D2GrailRequiredLevel.of(byName(D2GrailKey.Type.RUNEWORD, "Law")));

        // A quest unique with no stated requirement anywhere stays null rather than becoming a
        // displayed 0 -- D2Item.getReq()'s own semantics.
        assertNull(D2GrailRequiredLevel.of(byName(D2GrailKey.Type.UNIQUE, "Horadric Staff")));
        assertEquals(0, D2GrailRequiredLevel.orZero(byName(D2GrailKey.Type.UNIQUE, "Horadric Staff")));
    }

    /**
     * Every tab's rows come out in non-decreasing level order, with the name as the tie-break so
     * entries sharing a level keep the alphabetical order the list used to have throughout.
     */
    @Test
    public void everyTabIsOrderedByLevelThenName() {
        D2TxtFile.constructTxtFiles("./d2111");
        for (D2GrailKey.Type lTab : D2GrailKey.Type.values()) {
            D2GrailModel lModel = new D2GrailModel();
            lModel.setTab(lTab);
            List<D2GrailModel.Row> lRows = lModel.getRows();
            assertTrue(lRows.size() > 100, lTab + " should have a real list: " + lRows.size());

            int lPreviousLevel = Integer.MIN_VALUE;
            String lPreviousName = "";
            for (D2GrailModel.Row lRow : lRows) {
                int lLevel = D2GrailRequiredLevel.orZero(lRow.getEntry());
                String lName = D2ItemRenderer.stripColorCodes(lRow.getEntry().getDisplayName())
                        .toLowerCase(Locale.ROOT);
                assertTrue(lLevel >= lPreviousLevel,
                        lTab + ": " + lName + " (level " + lLevel + ") came after level " + lPreviousLevel);
                if (lLevel == lPreviousLevel) {
                    assertTrue(lName.compareTo(lPreviousName) >= 0,
                            lTab + ": " + lName + " came after " + lPreviousName + " at the same level");
                }
                lPreviousLevel = lLevel;
                lPreviousName = lName;
            }
        }
    }

    /**
     * Concrete anchors rather than only the "is sorted" invariant, which an all-equal list would
     * also satisfy: the first unique must be a level-less quest oddity and the last a level-95 one,
     * and Bulwark must sit above the level-11 Knowledge and below the level-69 words.
     */
    @Test
    public void theOrderRunsFromTheEarliestItemToTheLatest() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lUniques = new D2GrailModel();
        lUniques.setTab(D2GrailKey.Type.UNIQUE);
        List<D2GrailModel.Row> lRows = lUniques.getRows();
        assertEquals(95, D2GrailRequiredLevel.orZero(lRows.get(lRows.size() - 1).getEntry()),
                "the last unique is the highest-level one");

        D2GrailModel lRunewords = new D2GrailModel();
        lRunewords.setTab(D2GrailKey.Type.RUNEWORD);
        List<D2GrailModel.Row> lWords = lRunewords.getRows();
        assertEquals("Knowledge", lWords.get(0).getEntry().getDisplayName(),
                "El + El + El is the earliest runeword there is");
        assertTrue(indexOf(lWords, "Bulwark") > indexOf(lWords, "Knowledge"), "level 35 after level 11");
        assertTrue(indexOf(lWords, "Bulwark") < indexOf(lWords, "Enigma"), "level 35 before level 65");
    }

    /**
     * Sets are ordered by the level of their EARLIEST piece -- when the set starts being wearable
     * at all -- so a header and the rows under it read in the same direction down the list.
     */
    @Test
    public void setGroupsAreOrderedByTheirEarliestPiece() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.SET);

        int lPrevious = Integer.MIN_VALUE;
        for (D2GrailModel.SetGroup lGroup : lModel.getSetGroups()) {
            int lLowest = Integer.MAX_VALUE;
            for (D2GrailModel.Row lRow : lGroup.getRows()) {
                lLowest = Math.min(lLowest, D2GrailRequiredLevel.orZero(lRow.getEntry()));
            }
            assertTrue(lLowest >= lPrevious,
                    lGroup.getSetName() + " (from level " + lLowest + ") came after level " + lPrevious);
            // And the rows inside the header follow the same order.
            int lRowPrevious = Integer.MIN_VALUE;
            for (D2GrailModel.Row lRow : lGroup.getRows()) {
                int lLevel = D2GrailRequiredLevel.orZero(lRow.getEntry());
                assertTrue(lLevel >= lRowPrevious, lGroup.getSetName() + " rows are out of order");
                lRowPrevious = lLevel;
            }
            lPrevious = lLowest;
        }
        assertEquals("Arctic Gear", lModel.getSetGroups().get(0).getSetName(),
                "the level-2 set comes first");
    }

    private static int indexOf(List<D2GrailModel.Row> pRows, String pDisplayName) {
        for (int i = 0; i < pRows.size(); i++) {
            if (pDisplayName.equals(pRows.get(i).getEntry().getDisplayName())) {
                return i;
            }
        }
        throw new AssertionError(pDisplayName + " not in the list");
    }

    private static D2GrailEntry byName(D2GrailKey.Type pType, String pName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() == pType && pName.equals(lEntry.getDisplayName())) {
                return lEntry;
            }
        }
        throw new AssertionError(pName + " not found among the indexed " + pType + " entries");
    }
}
