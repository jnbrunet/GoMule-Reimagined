package gomule.grail;

import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Runewords tab's two filters, both headless (no Swing class is touched -- D2GrailModel and
 * D2GrailRunewords are deliberately Swing-free, which is what lets this test exist):
 * <ul>
 *   <li>the left tree, which had only "All" -- a runeword's allowed bases are a LIST
 *   (runes.txt itype1..itype6), not the single base category the Uniques tree filters on, so the
 *   tab had no sub-tree at all;</li>
 *   <li>a "which runes do I have" selection that lists the words those runes can make.</li>
 * </ul>
 */
public class D2GrailRunewordFilterTest {

    /**
     * The tree's leaves, derived from runes.txt's own itype columns rather than hardcoded. Asserted
     * against the full expected list because it is the feature's visible contract -- and because
     * two codes deliberately share one label: the Necromancer's umbrella "necr" and its one child
     * "head" ("Voodoo Heads") are the same piece of gear, so they collapse into a single
     * "Necromancer Head" entry rather than two that would filter identically.
     */
    @Test
    public void theTreeListsEveryBaseTypeARunewordAllows() {
        D2TxtFile.constructTxtFiles("./d2111");
        assertEquals(Arrays.asList(
                        "Amazon Bow", "Amazon Spear", "Any Armor", "Any Shield", "Any Weapon", "Armor",
                        "Assassin Claw", "Axe", "Barbarian Item", "Circlet", "Club", "Druid Item", "Hammer",
                        "Helm", "Knife", "Mace", "Melee Weapon", "Missile Weapon", "Necromancer Head",
                        "Paladin Auric Shield", "Paladin Item", "Polearm", "Scepter", "Sorceress Orb",
                        "Spear", "Staff", "Sword", "Wand", "Warlock Grimoire"),
                D2GrailRunewords.allBaseTypeLabels());
    }

    /**
     * Selecting a base type lists the words that can be put in it. A runeword allowing several
     * types appears under each -- "Spirit" is swords OR shields -- which is the whole reason the
     * node means "words I can put in this" rather than "words whose base is this".
     */
    @Test
    public void selectingABaseTypeNarrowsToTheWordsThatFitIt() {
        D2TxtFile.constructTxtFiles("./d2111");

        List<String> lHelm = runewordNames(D2GrailModel.baseTypeId("Helm"), null, false);
        assertTrue(lHelm.contains("Bulwark"), lHelm.toString());
        assertTrue(lHelm.contains("Lore"), lHelm.toString());
        assertFalse(lHelm.contains("Spirit"), "Spirit is swords or shields, not helms: " + lHelm);

        List<String> lSword = runewordNames(D2GrailModel.baseTypeId("Sword"), null, false);
        List<String> lShield = runewordNames(D2GrailModel.baseTypeId("Any Shield"), null, false);
        assertTrue(lSword.contains("Spirit"), lSword.toString());
        assertTrue(lShield.contains("Spirit"), lShield.toString());

        // "All" is unchanged, and every leaf is a strict subset of it.
        assertEquals(208, runewordNames(D2GrailModel.ALL_ID, null, false).size());
        assertTrue(lHelm.size() < 208 && !lHelm.isEmpty(), "a leaf must actually narrow: " + lHelm.size());
    }

    /**
     * The default, "what can I make right now" mode: a word is listed only when the selection
     * covers every rune it needs. Tal + Thul + Ort + Amn are exactly Spirit's four runes.
     */
    @Test
    public void selectedRunesListOnlyTheWordsTheyCanComplete() {
        D2TxtFile.constructTxtFiles("./d2111");
        Set<Integer> lOwned = new TreeSet<Integer>(Arrays.asList(7, 9, 10, 11)); // Tal, Ort, Thul, Amn

        List<String> lMakeable = runewordNames(D2GrailModel.ALL_ID, lOwned, false);
        assertTrue(lMakeable.contains("Spirit"), lMakeable.toString());
        for (String lName : lMakeable) {
            for (Integer lRune : runesOf(lName)) {
                assertTrue(lOwned.contains(lRune),
                        lName + " needs rune #" + lRune + ", which is not selected");
            }
        }
        // Nothing needing a rune outside the selection may appear.
        assertFalse(lMakeable.contains("Enigma"), "Enigma needs Jah/Ith/Ber: " + lMakeable);
    }

    /**
     * The "Show partial results" mode: every word that uses at least one selected rune, i.e. "what
     * am I part-way to?". Strictly wider than the default mode and strictly narrower than no filter
     * at all -- both directions asserted, since a filter that silently matched everything would
     * look identical to a working one on the happy path.
     */
    @Test
    public void partialMatchListsEveryWordUsingAnySelectedRune() {
        D2TxtFile.constructTxtFiles("./d2111");
        Set<Integer> lOwned = new TreeSet<Integer>(Arrays.asList(7, 9, 10, 11));

        List<String> lStrict = runewordNames(D2GrailModel.ALL_ID, lOwned, false);
        List<String> lPartial = runewordNames(D2GrailModel.ALL_ID, lOwned, true);

        assertTrue(lPartial.containsAll(lStrict), "partial must include everything strict does");
        assertTrue(lPartial.size() > lStrict.size(), "partial must be wider: "
                + lPartial.size() + " vs " + lStrict.size());
        assertTrue(lPartial.size() < 208, "partial must still be a filter: " + lPartial.size());
        for (String lName : lPartial) {
            boolean lUsesOne = false;
            for (Integer lRune : runesOf(lName)) {
                lUsesOne |= lOwned.contains(lRune);
            }
            assertTrue(lUsesOne, lName + " uses none of the selected runes");
        }
    }

    /**
     * An empty selection means "filter off", not "nothing matches" -- otherwise the tab would look
     * broken until the first click. Selecting every rune is the other end of the same scale and
     * must also show everything.
     */
    @Test
    public void anEmptySelectionShowsEverythingAndSoDoesAFullOne() {
        D2TxtFile.constructTxtFiles("./d2111");
        assertEquals(208, runewordNames(D2GrailModel.ALL_ID, new HashSet<Integer>(), false).size());

        Set<Integer> lEveryRune = new TreeSet<Integer>();
        for (int i = 1; i <= D2GrailRunewords.RUNE_COUNT; i++) {
            lEveryRune.add(Integer.valueOf(i));
        }
        assertEquals(208, runewordNames(D2GrailModel.ALL_ID, lEveryRune, false).size());
    }

    /**
     * Scope check, matching how the tier checkboxes are scoped to Uniques/Sets: a unique is not
     * made of runes, so a rune selection left over from the Runewords tab must be completely inert
     * on the others rather than emptying them.
     */
    @Test
    public void theRuneFilterIsInertOnTheOtherTabs() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.UNIQUE);
        int lBefore = lModel.getRows().size();

        lModel.setSelectedRunes(new TreeSet<Integer>(Arrays.asList(7, 9, 10, 11)));
        assertEquals(lBefore, lModel.getRows().size(), "a rune selection must not touch the Uniques tab");

        lModel.setTab(D2GrailKey.Type.SET);
        int lSets = lModel.getRows().size();
        lModel.setRunePartialMatch(true);
        assertEquals(lSets, lModel.getRows().size(), "nor the Sets tab");
    }

    /**
     * The two filters are independent and compose: the runes I have, among the words that fit one
     * particular kind of base.
     */
    @Test
    public void theBaseTypeAndRuneFiltersCompose() {
        D2TxtFile.constructTxtFiles("./d2111");
        Set<Integer> lOwned = new TreeSet<Integer>(Arrays.asList(7, 9, 10, 11));

        List<String> lSwords = runewordNames(D2GrailModel.baseTypeId("Sword"), lOwned, false);
        assertTrue(lSwords.contains("Spirit"), lSwords.toString());
        List<String> lHelms = runewordNames(D2GrailModel.baseTypeId("Helm"), lOwned, false);
        assertFalse(lHelms.contains("Spirit"), "Spirit is not a helm word: " + lHelms);
    }

    // --------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------

    private static List<String> runewordNames(String pSelection, Set<Integer> pRunes, boolean pPartial) {
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(D2GrailKey.Type.RUNEWORD);
        lModel.setCategorySelection(pSelection);
        if (pRunes != null) {
            lModel.setSelectedRunes(pRunes);
        }
        lModel.setRunePartialMatch(pPartial);
        List<String> lNames = new ArrayList<String>();
        for (D2GrailModel.Row lRow : lModel.getRows()) {
            lNames.add(lRow.getEntry().getDisplayName());
        }
        return lNames;
    }

    private static List<Integer> runesOf(String pDisplayName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() == D2GrailKey.Type.RUNEWORD
                    && pDisplayName.equals(lEntry.getDisplayName())) {
                return D2GrailRunewords.runeNumbers(lEntry.getSourceRow());
            }
        }
        throw new AssertionError(pDisplayName + " not found among the indexed runewords");
    }
}
