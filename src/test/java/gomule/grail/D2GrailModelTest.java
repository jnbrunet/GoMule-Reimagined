package gomule.grail;

import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2GrailModel's filter/statistics logic, entirely headless: no Swing class is imported or
 * instantiated anywhere in this file, and -- critically -- D2FileManager is never referenced
 * either, since merely loading that class pops a real window as a static side effect (its
 * singleton field is initialized eagerly). D2GrailModel itself never imports Swing, which is what
 * makes this possible.
 */
public class D2GrailModelTest {

    @Test
    public void chronicleScopeMovesTheDenominatorWithoutTouchingFindings() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);

        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        lModel.setFindings(lFindings);

        assertEquals(419, lModel.getTabProgress().getTotal(), "default scope is Chronicle-only");

        lModel.setIncludeNonChronicle(true);
        assertEquals(1470, lModel.getTabProgress().getTotal(), "checked scope is the whole table");

        // The load-bearing assertion: toggling the checkbox must not have replaced (or touched)
        // the findings map -- see D2GrailModel.setIncludeNonChronicle's javadoc. Same reference,
        // not just equal content, is the point: nothing rebuilt it.
        assertSame(lFindings, lModel.getFindings());

        lModel.setIncludeNonChronicle(false);
        assertEquals(419, lModel.getTabProgress().getTotal(), "unchecking returns to exactly the prior state");
    }

    @Test
    public void tierCheckboxesFilterUniqueRows() {
        D2TxtFile.constructTxtFiles("./d2111");

        // Independently derive the expected NORMAL-only, chronicle=true unique count straight from
        // the index, so this test isn't just re-asserting whatever D2GrailModel happens to compute.
        int lExpectedNormalOnly = 0;
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() == D2GrailKey.Type.UNIQUE && lEntry.isChronicle()
                    && lEntry.getTier() == D2GrailEntry.Tier.NORMAL) {
                lExpectedNormalOnly++;
            }
        }
        assertTrue(lExpectedNormalOnly > 0 && lExpectedNormalOnly < 419,
                "sanity: expected a real subset, got " + lExpectedNormalOnly);

        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);
        lModel.setTierEnabled(D2GrailEntry.Tier.EXCEPTIONAL, false);
        lModel.setTierEnabled(D2GrailEntry.Tier.ELITE, false);

        assertEquals(lExpectedNormalOnly, lModel.getTabProgress().getTotal());
        assertEquals(lExpectedNormalOnly, lModel.getRows().size());
        for (D2GrailModel.Row lRow : lModel.getRows()) {
            assertEquals(D2GrailEntry.Tier.NORMAL, lRow.getEntry().getTier());
        }
    }

    @Test
    public void tierCheckboxesAreInertOnTheRunewordsTab() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.RUNEWORD);
        lModel.setTierEnabled(D2GrailEntry.Tier.NORMAL, false);
        lModel.setTierEnabled(D2GrailEntry.Tier.EXCEPTIONAL, false);
        lModel.setTierEnabled(D2GrailEntry.Tier.ELITE, false);

        // Every runeword is Tier.NONE, which is not any of the three checkboxes -- if this model
        // applied the tier filter uniformly across tabs, unchecking all three would (wrongly) hide
        // every runeword. It must not.
        assertEquals(208, lModel.getTabProgress().getTotal());
    }

    @Test
    public void missingStatusFilterDoesNotForceProgressToZero() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);

        D2GrailEntry lHarlequinCrest = findByDisplayName(D2GrailIndex.getEntries(), "Harlequin Crest");
        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        D2GrailFinding lFinding = new D2GrailFinding();
        lFinding.record(null, "C:/saves/test.d2s", "test.d2s");
        lFindings.put(lHarlequinCrest.getKey(), lFinding);
        lModel.setFindings(lFindings);

        // Plan section 7: the progress bar must keep showing the real percentage under Show:
        // Missing, not force 0% just because every currently-listed row is (by definition) missing.
        lModel.setStatus(D2GrailModel.Status.MISSING);
        assertEquals(1, lModel.getTabProgress().getFound(), "the found item still counts toward progress");
        assertTrue(lModel.getTabProgress().getTotal() > 1);

        // But the row list itself must exclude the found entry.
        boolean lHarlequinCrestStillListed = lModel.getRows().stream()
                .anyMatch(pRow -> "Harlequin Crest".equals(pRow.getEntry().getDisplayName()));
        assertFalse(lHarlequinCrestStillListed, "a found entry must not appear under Show: Missing");
    }

    @Test
    public void searchMatchesAnEntryWhoseRawNameCarriesAColorCode() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);
        lModel.setIncludeNonChronicle(true); // Heaven Facet's neighbours aren't all chronicle=true

        // The real display name is "ÿc4Heaven Facet" (see D2GrailIndexTest) -- a plain, code-free
        // search string must still find it, both because the entry's own ÿc4 must be stripped
        // before comparing and because "harl"-style substring search (plan section 10) must work
        // on the visible text a user would actually type.
        lModel.setSearchText("heaven facet");
        List<D2GrailModel.Row> lRows = lModel.getRows();
        assertEquals(1, lRows.size());
        assertEquals("ÿc4Heaven Facet", lRows.get(0).getEntry().getDisplayName());
    }

    @Test
    public void setHeaderSizeFollowsChronicleScope() {
        // A small, synthetic, fully-controlled two-piece set instead of hunting for a real one
        // with the right chronicle split: one chronicle=true piece (found) and one chronicle=false
        // piece (not found), both otherwise identical. Built directly through D2GrailEntry's
        // public constructor -- the same one D2GrailIndex itself uses -- rather than depending on
        // D2GrailIndex/txt data, so this test needs no ./d2111 tables at all.
        D2GrailEntry lChronicleTrue = new D2GrailEntry(
                D2GrailKey.set(90001), "Test Piece A", "abc", "Test Base", D2GrailEntry.Tier.NORMAL,
                "sword", "Swords", D2GrailCategories.RootGroup.WEAPONS,
                "Test Set", 2, "", "General", "", true, null);
        D2GrailEntry lChronicleFalse = new D2GrailEntry(
                D2GrailKey.set(90002), "Test Piece B", "abc", "Test Base", D2GrailEntry.Tier.NORMAL,
                "sword", "Swords", D2GrailCategories.RootGroup.WEAPONS,
                "Test Set", 2, "", "General", "", false, null);
        List<D2GrailEntry> lEntries = Arrays.asList(lChronicleTrue, lChronicleFalse);

        D2GrailModel lModel = new D2GrailModel(lEntries);
        lModel.setTab(D2GrailKey.Type.SET);

        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        D2GrailFinding lFinding = new D2GrailFinding();
        lFinding.record(null, "C:/saves/test.d2s", "test.d2s");
        lFindings.put(lChronicleTrue.getKey(), lFinding);
        lModel.setFindings(lFindings);

        List<D2GrailModel.SetGroup> lGroups = lModel.getSetGroups();
        assertEquals(1, lGroups.size());
        assertEquals("Test Set", lGroups.get(0).getSetName());
        assertEquals(1, lGroups.get(0).getTotal(), "only the chronicle=true piece counts by default");
        assertEquals(1, lGroups.get(0).getFound());

        lModel.setIncludeNonChronicle(true);
        lGroups = lModel.getSetGroups();
        assertEquals(2, lGroups.get(0).getTotal(), "both pieces count once non-Chronicle items are included");
        assertEquals(1, lGroups.get(0).getFound(), "the found count itself is unaffected by the toggle");
    }

    @Test
    public void uncategorizedUniquesAreReachableThroughTheDedicatedNode() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);
        lModel.setIncludeNonChronicle(true); // several of the 25 are chronicle=false
        lModel.setCategorySelection(D2GrailModel.UNCATEGORIZED_ID);

        List<D2GrailModel.Row> lRows = lModel.getRows();
        assertEquals(25, lRows.size());
        for (D2GrailModel.Row lRow : lRows) {
            assertEquals(null, lRow.getEntry().getUiCategoryCode());
        }
    }

    @Test
    public void aStaleClassSelectionFromTheSetsTabMatchesNothingOnTheUniquesTab() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setCategorySelection(D2GrailModel.classId("pal"));
        assertTrue(lModel.getTabProgress().getTotal() == 0 || lModel.getCategoryProgress().getTotal() > 0);

        // Switch tabs without clearing the selection -- plan section 10's "changer d'onglet avec
        // une catégorie sélectionnée : pas d'exception, pas de sélection périmée".
        lModel.setTab(D2GrailKey.Type.UNIQUE);
        List<D2GrailModel.Row> lRows = lModel.getRows(); // must not throw
        assertTrue(lRows.isEmpty(), "a Sets-tab CLASS: selection must match nothing on the Uniques tab");
    }

    private static D2GrailEntry findByDisplayName(List<D2GrailEntry> pEntries, String pName) {
        for (D2GrailEntry lEntry : pEntries) {
            if (pName.equals(lEntry.getDisplayName())) {
                return lEntry;
            }
        }
        throw new AssertionError(pName + " not found in the index");
    }
}
