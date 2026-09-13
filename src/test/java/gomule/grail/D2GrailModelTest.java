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
        assertEquals(1471, lModel.getTabProgress().getTotal(), "checked scope is the whole table");

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

    /**
     * The real-use request: on the Sets tab, Show: Found used to filter row by row, so a 2-of-4
     * set showed only the 2 owned pieces and hid what completes it. A set with at least one
     * structurally-passing found piece must instead show ALL its pieces -- found and missing
     * alike -- so the player can see what is left. Confirmed against a real fixture too (see this
     * change's report): scanning pally3/4/5.d2s's "Immortal King's Stone Crusher" resurrects the
     * whole 6-piece "Immortal King" set, 1 found + 5 missing.
     */
    @Test
    public void aStartedSetShowsAllItsPiecesUnderShowFound() {
        D2GrailEntry lPieceA = setPiece("Test Set", "Piece A", 91001, D2GrailEntry.Tier.NORMAL);
        D2GrailEntry lPieceB = setPiece("Test Set", "Piece B", 91002, D2GrailEntry.Tier.NORMAL);
        D2GrailEntry lPieceC = setPiece("Test Set", "Piece C", 91003, D2GrailEntry.Tier.NORMAL);
        D2GrailModel lModel = new D2GrailModel(Arrays.asList(lPieceA, lPieceB, lPieceC));
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setStatus(D2GrailModel.Status.FOUND);

        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        lFindings.put(lPieceA.getKey(), foundOnce());
        lModel.setFindings(lFindings);

        List<D2GrailModel.Row> lRows = lModel.getRows();
        assertEquals(3, lRows.size(), "the whole set must show, not just the 1 owned piece");
        assertTrue(rowNamed(lRows, "Piece A").isFound());
        assertFalse(rowNamed(lRows, "Piece B").isFound());
        assertFalse(rowNamed(lRows, "Piece C").isFound());
    }

    /**
     * The other half of the same behavior: a set with NOTHING found under Show: Found stays fully
     * hidden, exactly as before -- this is not "always show every piece of every set", only sets
     * the player has actually started.
     */
    @Test
    public void aCompletelyUnfoundSetShowsNoRowsUnderShowFound() {
        D2GrailEntry lPieceA = setPiece("Test Set", "Piece A", 91001, D2GrailEntry.Tier.NORMAL);
        D2GrailEntry lPieceB = setPiece("Test Set", "Piece B", 91002, D2GrailEntry.Tier.NORMAL);
        D2GrailModel lModel = new D2GrailModel(Arrays.asList(lPieceA, lPieceB));
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setStatus(D2GrailModel.Status.FOUND);
        lModel.setFindings(new HashMap<>());

        assertTrue(lModel.getRows().isEmpty());
        assertTrue(lModel.getSetGroups().isEmpty(), "no header either -- nothing to show for this set");
    }

    /**
     * The tier filter decides whether a SET is shown, never which of its pieces are (see
     * D2GrailModel.passesTier). A set with NO piece in any enabled tier is still suppressed
     * completely -- so the checkboxes keep narrowing the list, they just never show half a set.
     */
    @Test
    public void tierFilterStillSuppressesASetWithNoPieceInAnEnabledTier() {
        D2GrailEntry lEliteFound = setPiece("Test Set", "Elite Piece", 91001, D2GrailEntry.Tier.ELITE);
        D2GrailEntry lEliteMissing = setPiece("Test Set", "Other Elite Piece", 91002, D2GrailEntry.Tier.ELITE);
        D2GrailModel lModel = new D2GrailModel(Arrays.asList(lEliteFound, lEliteMissing));
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setStatus(D2GrailModel.Status.FOUND);
        lModel.setTierEnabled(D2GrailEntry.Tier.ELITE, false);

        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        lFindings.put(lEliteFound.getKey(), foundOnce());
        lModel.setFindings(lFindings);

        assertTrue(lModel.getRows().isEmpty(),
                "every piece of this set is Elite, which is unchecked, so the set must not appear");
        assertTrue(lModel.getSetGroups().isEmpty(), "no header either");
    }

    /**
     * The reported bug: with "Elite" alone checked, Immortal King listed 2 of its 6 pieces under a
     * "0 / 2" header. Its pieces genuinely span three tiers -- a Normal Avenger Guard, three
     * Exceptional War Belt/Gauntlets/Boots and an Elite Sacred Armor + Ogre Maul -- and filtering
     * piece-by-piece tore the set apart, hiding the four pieces still needed to complete it and
     * understating the set's size. A set is the unit the player tracks on this tab, so one
     * qualifying piece shows the whole set: 6 rows under a "n / 6" header, whichever single tier is
     * checked.
     */
    @Test
    public void aTierFilterNeverSplitsASetAcrossTiers() {
        D2TxtFile.constructTxtFiles("./d2111");
        for (D2GrailEntry.Tier lOnlyTier : new D2GrailEntry.Tier[]{D2GrailEntry.Tier.NORMAL,
                D2GrailEntry.Tier.EXCEPTIONAL, D2GrailEntry.Tier.ELITE}) {
            D2GrailModel lModel = new D2GrailModel();
            lModel.setTab(D2GrailKey.Type.SET);
            for (D2GrailEntry.Tier lTier : D2GrailEntry.Tier.values()) {
                lModel.setTierEnabled(lTier, lTier == lOnlyTier);
            }

            D2GrailModel.SetGroup lImmortalKing = null;
            for (D2GrailModel.SetGroup lGroup : lModel.getSetGroups()) {
                if ("Immortal King".equals(lGroup.getSetName())) {
                    lImmortalKing = lGroup;
                }
            }
            assertTrue(lImmortalKing != null, "Immortal King must show with only " + lOnlyTier + " checked");
            assertEquals(6, lImmortalKing.getRows().size(),
                    "all six pieces, with only " + lOnlyTier + " checked");
            assertEquals(6, lImmortalKing.getTotal(),
                    "and the header must state the set's real size, not the filtered one");
        }
    }

    /**
     * The counterpart: the checkboxes must still do something. "Sigon's Complete Steel" is entirely
     * Normal/Exceptional in ./d2111, so checking Elite alone must remove it from the list
     * altogether, rather than the set-level rule turning the filter into a no-op.
     */
    @Test
    public void aTierFilterStillRemovesSetsEntirelyOutsideIt() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.SET);

        int lAllTiers = lModel.getSetGroups().size();

        for (D2GrailEntry.Tier lTier : D2GrailEntry.Tier.values()) {
            lModel.setTierEnabled(lTier, lTier == D2GrailEntry.Tier.ELITE);
        }
        int lEliteOnly = lModel.getSetGroups().size();

        assertTrue(lEliteOnly < lAllTiers,
                "Elite-only must show fewer sets than every tier does: " + lEliteOnly + " vs " + lAllTiers);
        for (D2GrailModel.SetGroup lGroup : lModel.getSetGroups()) {
            boolean lHasElite = false;
            for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
                if (lEntry.getKey().getType() == D2GrailKey.Type.SET
                        && lGroup.getSetName().equals(lEntry.getSetName())
                        && lEntry.isChronicle()
                        && lEntry.getTier() == D2GrailEntry.Tier.ELITE) {
                    lHasElite = true;
                }
            }
            assertTrue(lHasElite, lGroup.getSetName() + " has no Elite piece and must not be listed");
        }
    }

    /**
     * Search must only ever narrow which of a started set's rows are DISPLAYED -- never affect
     * whether the set qualifies as "started" in the first place, or a set would vanish and
     * reappear as the user types (plan section 7's search behavior, extended to this new mode).
     */
    @Test
    public void searchNarrowsRowsWithoutHidingAQualifyingSetEntirely() {
        D2GrailEntry lPieceA = setPiece("Test Set", "Piece A", 91001, D2GrailEntry.Tier.NORMAL);
        D2GrailEntry lPieceB = setPiece("Test Set", "Piece B", 91002, D2GrailEntry.Tier.NORMAL);
        D2GrailModel lModel = new D2GrailModel(Arrays.asList(lPieceA, lPieceB));
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setStatus(D2GrailModel.Status.FOUND);

        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        lFindings.put(lPieceA.getKey(), foundOnce());
        lModel.setFindings(lFindings);

        // A search matching neither piece's own name, but the set qualifies via Piece A -- if
        // search were allowed to affect qualification, this would need to hide the whole set;
        // instead it must simply narrow the displayed rows down (here, to none), while the set
        // still exists as "started" (getSetGroups() would still find it -- see the next block).
        lModel.setSearchText("this matches nothing at all");
        assertTrue(lModel.getRows().isEmpty(), "search narrows to zero visible rows");

        // Searching for "Piece B" alone must still find it (proving it was never excluded from
        // consideration by the found-piece decision, only by the text of the previous search).
        lModel.setSearchText("Piece B");
        List<D2GrailModel.Row> lRows = lModel.getRows();
        assertEquals(1, lRows.size());
        assertEquals("Piece B", lRows.get(0).getEntry().getDisplayName());
        assertFalse(lRows.get(0).isFound());
    }

    /**
     * Scope check: this new behavior is Sets-tab-only. Show: Found on the Uniques tab keeps its
     * ordinary per-row meaning -- there is no set to complete there, so a missing unique must never
     * leak into the Found list.
     */
    @Test
    public void showFoundOnUniquesTabIsUnaffected() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);
        lModel.setStatus(D2GrailModel.Status.FOUND);

        D2GrailEntry lHarlequinCrest = findByDisplayName(D2GrailIndex.getEntries(), "Harlequin Crest");
        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        lFindings.put(lHarlequinCrest.getKey(), foundOnce());
        lModel.setFindings(lFindings);

        List<D2GrailModel.Row> lRows = lModel.getRows();
        assertEquals(1, lRows.size(), "only the one found unique, not the rest of the tab");
        assertTrue(lRows.get(0).isFound());
    }

    /**
     * The search box matches an entry's properties as well as its name, through an injected
     * D2GrailModel.SearchTextProvider (the window supplies the list renderer's real, whole-tooltip
     * one; this pins the mechanism with a two-line stub instead, so the model stays testable
     * without the gui package). Three things at once: the provider IS consulted, its text is
     * stripped of HTML before matching, and an entry it returns nothing for is simply not matched
     * rather than throwing.
     */
    @Test
    public void searchAlsoMatchesTheTextTheProviderSupplies() {
        D2GrailEntry lWithAffix = setPiece("Test Set", "Piece A", 92001, D2GrailEntry.Tier.NORMAL);
        D2GrailEntry lWithout = setPiece("Test Set", "Piece B", 92002, D2GrailEntry.Tier.NORMAL);
        D2GrailModel lModel = new D2GrailModel(Arrays.asList(lWithAffix, lWithout));
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setSearchTextProvider(pEntry -> lWithAffix == pEntry
                ? "<html><font color='#4850b8'>+20% Increased Attack Speed</font></html>"
                : null);

        lModel.setSearchText("increased attack speed");
        assertEquals(1, lModel.getRows().size());
        assertEquals("Piece A", lModel.getRows().get(0).getEntry().getDisplayName());

        // Tags are stripped, so a query may not match across one: "font" appears only inside markup.
        lModel.setSearchText("font");
        assertTrue(lModel.getRows().isEmpty(), "HTML markup is not searchable text");

        // And with no provider at all, search is name-only exactly as it always was.
        D2GrailModel lNameOnly = new D2GrailModel(Arrays.asList(lWithAffix, lWithout));
        lNameOnly.setTab(D2GrailKey.Type.SET);
        lNameOnly.setSearchText("increased attack speed");
        assertTrue(lNameOnly.getRows().isEmpty());
        lNameOnly.setSearchText("Piece A");
        assertEquals(1, lNameOnly.getRows().size());
    }

    /**
     * A provider that blows up on one entry costs that entry its affix search, never the whole
     * search box -- the same "degrade to one missing line" stance the tooltip code takes.
     */
    @Test
    public void aProviderThatThrowsOnlyCostsThatEntryItsAffixSearch() {
        D2GrailEntry lBroken = setPiece("Test Set", "Piece A", 92003, D2GrailEntry.Tier.NORMAL);
        D2GrailEntry lFine = setPiece("Test Set", "Piece B", 92004, D2GrailEntry.Tier.NORMAL);
        D2GrailModel lModel = new D2GrailModel(Arrays.asList(lBroken, lFine));
        lModel.setTab(D2GrailKey.Type.SET);
        lModel.setSearchTextProvider(pEntry -> {
            if (lBroken == pEntry) {
                throw new IllegalStateException("no properties for you");
            }
            return "+20% Increased Attack Speed";
        });

        lModel.setSearchText("increased attack speed");
        assertEquals(1, lModel.getRows().size(), "the healthy entry still matches");
        assertEquals("Piece B", lModel.getRows().get(0).getEntry().getDisplayName());
    }

    private static D2GrailEntry setPiece(String pSetName, String pDisplayName, int pId, D2GrailEntry.Tier pTier) {
        return new D2GrailEntry(
                D2GrailKey.set(pId), pDisplayName, "abc", "Test Base", pTier,
                "sword", "Swords", D2GrailCategories.RootGroup.WEAPONS,
                pSetName, 0, "", "General", "", true, null);
    }

    private static D2GrailFinding foundOnce() {
        D2GrailFinding lFinding = new D2GrailFinding();
        lFinding.record(null, "C:/saves/test.d2s", "test.d2s");
        return lFinding;
    }

    private static D2GrailModel.Row rowNamed(List<D2GrailModel.Row> pRows, String pName) {
        for (D2GrailModel.Row lRow : pRows) {
            if (pName.equals(lRow.getEntry().getDisplayName())) {
                return lRow;
            }
        }
        throw new AssertionError(pName + " not found among rows: " + pRows);
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
