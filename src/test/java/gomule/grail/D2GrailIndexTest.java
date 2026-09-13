package gomule.grail;

import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins D2GrailIndex against the real ./d2111 data. Most of these numbers were originally
 * specified in PLAN-holy-grail.md section 1.2 / the task description this feature was built from;
 * where this test's numbers differ from those, it is because building the index (and, even more
 * importantly, cross-checking it against what D2GrailScannerTest's real save fixtures actually
 * carry) turned up a real discrepancy -- see countsMatchWhatTheRealDataActuallyContains() and
 * uniqueKeysMatchWhatSearchByIdActuallyReturnsNotTheIdColumnText() below for the two that did.
 */
public class D2GrailIndexTest {

    /**
     * uniqueitems.txt turns out to hold not just the one "Expansion" separator row the plan called
     * out, but five more blank-*ID "section header" comment rows further down the table ("Armor",
     * "Elite Uniques", "Rings", "Class Specific", "Warlock Class Pack" -- verified against
     * ./d2111), which are not real items either. None of the plan's stated 1475/424 uniques
     * figures accounted for those, so the real, correct totals are 1475-5=1470 and 424-5=419.
     * setitems.txt has no equivalent extra rows, so its counts match the plan exactly. runes.txt
     * has a different one-off wrinkle instead: one runeword ("Doom") is legitimately split across
     * two complete=1 rows sharing the identical Name ("Doom1") -- one restricted to staves, the
     * other to axes/polearms/clubs/hammers/maces -- which D2GrailIndex deliberately collapses into
     * a single entry (see buildRunewords's dedup comment), making the real runeword total 208, not
     * 209.
     */
    @Test
    public void countsMatchWhatTheRealDataActuallyContains() {
        D2TxtFile.constructTxtFiles("./d2111");
        List<D2GrailEntry> lEntries = D2GrailIndex.getEntries();

        int lUniques = 0, lUniquesChronicle = 0;
        int lSets = 0, lSetsChronicle = 0;
        int lRunewords = 0, lRunewordsChronicle = 0;

        for (D2GrailEntry lEntry : lEntries) {
            switch (lEntry.getKey().getType()) {
                case UNIQUE:
                    lUniques++;
                    if (lEntry.isChronicle()) lUniquesChronicle++;
                    break;
                case SET:
                    lSets++;
                    if (lEntry.isChronicle()) lSetsChronicle++;
                    break;
                case RUNEWORD:
                    lRunewords++;
                    if (lEntry.isChronicle()) lRunewordsChronicle++;
                    break;
                default:
                    throw new AssertionError("unknown key type " + lEntry.getKey().getType());
            }
        }

        assertEquals(1471, lUniques,
                "total usable unique entries (1483 rows - 7 disabled - 5 blank-*ID section-header rows)");
        assertEquals(419, lUniquesChronicle, "unique entries with chronicle=true");

        assertEquals(455, lSets, "total usable set-item entries");
        assertEquals(135, lSetsChronicle, "set-item entries with chronicle=true");

        assertEquals(208, lRunewords, "total distinct runeword entries (209 complete=1 rows, minus "
                + "the 'Doom'/'Doom1' pair collapsed into one entry)");
        assertEquals(208, lRunewordsChronicle, "runewords are always chronicle=true -- runes.txt "
                + "has no disableChronicle column");
    }

    /**
     * The load-bearing regression test for the id-drift discovery: D2Item.java resolves a save's
     * raw unique_id via D2TxtFile.UNIQUES.searchByID(id) -- a purely positional lookup (physical
     * row index, shifted by one once past the single "Expansion" row) that has no idea the table
     * also contains those five blank-*ID section-header rows. So past the first of those, the
     * "*ID" column's own text permanently drifts away from what searchByID (and therefore every
     * real save file) actually means by that number.
     * <p>
     * Confirmed against real data: the "Heaven Facet" jewel socketed into "Hand of Blessed Light"
     * in the pally3/4/5 grail-scanner fixtures (see D2GrailScannerTest) carries unique_id=1400 in
     * every one of them -- exactly searchByID(1400) -- but that row's own "*ID" column reads 1398.
     * If D2GrailIndex keyed uniques by the raw "*ID" column text (as originally planned), key 1400
     * would resolve to the unrelated row one physical position later ("Adamantine Facet" here)
     * instead, silently misattributing every real Heaven Facet find.
     */
    @Test
    public void uniqueKeysMatchWhatSearchByIdActuallyReturnsNotTheIdColumnText() {
        D2TxtFile.constructTxtFiles("./d2111");

        D2GrailEntry lHeavenFacet = D2GrailIndex.getByKey(D2GrailKey.unique(1400));
        assertNotNull(lHeavenFacet, "unique id 1400 (what real saves actually carry) not found");
        assertEquals("ÿc4Heaven Facet", lHeavenFacet.getDisplayName());

        // Key 1398 -- the "Heaven Facet" row's own (misleading) "*ID" column text -- must resolve
        // to whatever row is really at that position instead, not to Heaven Facet.
        D2GrailEntry lWhatIsReallyAt1398 = D2GrailIndex.getByKey(D2GrailKey.unique(1398));
        assertNotNull(lWhatIsReallyAt1398);
        assertFalse("ÿc4Heaven Facet".equals(lWhatIsReallyAt1398.getDisplayName()),
                "key 1398 must not also resolve to Heaven Facet");
    }

    @Test
    public void keysAreUnique() {
        D2TxtFile.constructTxtFiles("./d2111");
        List<D2GrailEntry> lEntries = D2GrailIndex.getEntries();

        Set<D2GrailKey> lSeen = new HashSet<>();
        for (D2GrailEntry lEntry : lEntries) {
            assertTrue(lSeen.add(lEntry.getKey()), "duplicate key: " + lEntry.getKey());
        }
    }

    @Test
    public void noEntryHasANullOrEmptyDisplayName() {
        D2TxtFile.constructTxtFiles("./d2111");
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            assertNotNull(lEntry.getDisplayName(), "null display name for " + lEntry.getKey());
            assertFalse(lEntry.getDisplayName().isEmpty(),
                    "empty display name for " + lEntry.getKey());
        }
    }

    /**
     * NOT every unique/set entry resolves to a category -- verified against ./d2111: 25 of them
     * don't, and legitimately so. Ten (e.g. "Gore Ripper", *ID 295) have a blank base "code" at
     * all -- apparently reserved/never-finished unique slots -- so there is no base item to derive
     * a category from. The other fifteen (Keychain, the three Key Grabbers, the Gem Bag and the
     * various Grabbers/Pliers) DO have a real base item, but that base item's itemtypes.txt "type"
     * row has no UICategory and neither does the row its own Equiv1 points at (both "stor" and its
     * Equiv1 "misc" are blank) -- these are inventory-management utility items with no equipment
     * slot, so having no equipment category is the correct answer, not a gap in D2GrailCategories.
     * <p>
     * What this test actually pins down is narrower and more useful: whenever D2GrailIndex DOES
     * produce a category code for an entry, that code must resolve to both a label and a root
     * group -- if it resolved to only one of the two, D2GrailIndex and D2GrailCategories would
     * have drifted apart (this is exactly the shape of gap the "dns"/Colossal-Jewel fix closed).
     */
    @Test
    public void everyProducedCategoryCodeResolvesToALabelAndARootGroup() {
        D2TxtFile.constructTxtFiles("./d2111");
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getUiCategoryCode() == null) {
                continue;
            }
            assertNotNull(lEntry.getUiCategoryLabel(),
                    "category code '" + lEntry.getUiCategoryCode() + "' with no label, for "
                            + lEntry.getKey() + " '" + lEntry.getDisplayName() + "'");
            assertNotNull(lEntry.getRootGroup(),
                    "category code '" + lEntry.getUiCategoryCode() + "' with no root group, for "
                            + lEntry.getKey() + " '" + lEntry.getDisplayName() + "'");
        }
    }

    /**
     * The flip side of the test above: the 25 known-uncategorizable entries really do come back
     * with no category at all (not a half-resolved code/label/root), and the count is pinned so a
     * future change that starts silently swallowing more categories gets noticed.
     */
    @Test
    public void exactlyTheKnownUncategorizableEntriesHaveNoCategory() {
        D2TxtFile.constructTxtFiles("./d2111");
        int lUncategorized = 0;
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() == D2GrailKey.Type.RUNEWORD) {
                continue;
            }
            if (lEntry.getUiCategoryCode() == null) {
                lUncategorized++;
                assertEquals(null, lEntry.getUiCategoryLabel());
                assertEquals(null, lEntry.getRootGroup());
            }
        }
        assertEquals(25, lUncategorized);
    }

    @Test
    public void harlequinCrestIsAnEliteUnique() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHarlequinCrest = findByDisplayName("Harlequin Crest");
        assertNotNull(lHarlequinCrest, "Harlequin Crest not found in the index");
        assertEquals(D2GrailEntry.Tier.ELITE, lHarlequinCrest.getTier());
        assertEquals("uap", lHarlequinCrest.getBaseItemCode());
        assertEquals("helms", lHarlequinCrest.getUiCategoryCode());
        assertEquals(D2GrailCategories.RootGroup.ARMOR, lHarlequinCrest.getRootGroup());
    }

    @Test
    public void angelicSickleIsInTheAngelicRaimentSet() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lAngelicSickle = findByDisplayName("Angelic Sickle");
        assertNotNull(lAngelicSickle, "Angelic Sickle not found in the index");
        assertEquals(D2GrailKey.Type.SET, lAngelicSickle.getKey().getType());
        assertEquals("Angelic Raiment", lAngelicSickle.getSetName());
        // Angelic Raiment has 4 pieces total (Sickle, Mantle, Halo, Wings), all chronicle=true.
        assertEquals(4, lAngelicSickle.getSetSize());
    }

    @Test
    public void insightIsAKnownRuneword() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lInsight = D2GrailIndex.getByKey(D2GrailKey.runeword("Insight"));
        assertNotNull(lInsight, "runeword 'Insight' not found in the index");
        assertEquals(D2GrailKey.Type.RUNEWORD, lInsight.getKey().getType());
        assertEquals(D2GrailEntry.Tier.NONE, lInsight.getTier());
        assertTrue(lInsight.isChronicle());
        // Icon comes from the first rune of the word: Rune1 = "r08" = Ral Rune.
        assertEquals("invrRal", lInsight.getInvfile());
    }

    /**
     * At least one entry is known, real, and deliberately excluded from the in-game Chronicle --
     * the "Crafted Cold Rupture" duplicate of a normal unique charm (uniqueitems.txt marks it
     * disableChronicle=1, unlike its "PreCrafted" and "Crafted"-less siblings).
     * <p>
     * item-names.json translates this row's "index" key to "ÿc4Renewed Cold Rupture" -- an actual
     * rename, not just a color-code prefix on the raw uniqueitems.txt string -- which is exactly
     * the kind of translation surprise D2GrailIndex.translateOrFallback exists to just pass
     * through rather than second-guess.
     */
    @Test
    public void craftedColdRuptureIsAKnownNonChronicleUnique() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lCraftedColdRupture = findByDisplayName("ÿc4Renewed Cold Rupture");
        assertNotNull(lCraftedColdRupture, "Crafted Cold Rupture (displayed as 'Renewed Cold Rupture') not found");
        assertEquals("cs2", lCraftedColdRupture.getBaseItemCode());
        assertFalse(lCraftedColdRupture.isChronicle());
    }

    /**
     * item-names.json translates this row's "index" key ("Guardian's Light") to
     * "ÿc4Guardian's Light" -- the ÿc4 color code is real translated output here, not a test
     * artifact, and stripping it is a display-layer concern (plan section 7,
     * D2ItemRenderer.stripColorCodes) that Phase 1/2 deliberately leaves untouched.
     */
    @Test
    public void colossalJewelUniquesResolveTheirUICatOverrideCategory() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lGuardiansLight = findByDisplayName("ÿc4Guardian's Light");
        assertNotNull(lGuardiansLight, "Guardian's Light not found in the index");
        assertEquals("cjw", lGuardiansLight.getBaseItemCode());
        assertEquals("dns", lGuardiansLight.getUiCategoryCode());
        assertEquals(D2GrailCategories.RootGroup.MISC, lGuardiansLight.getRootGroup());
        assertEquals(D2GrailEntry.Tier.NORMAL, lGuardiansLight.getTier());
    }

    /**
     * "Gore Ripper" (*ID 295): a real, non-disabled unique row with a real translated name but a
     * blank "code" column -- apparently a reserved/never-finished unique slot. Documents that
     * D2GrailIndex includes it (the plan's literal skip rules are only "Expansion" and disabled)
     * rather than silently dropping it, with a sensible NORMAL/uncategorized fallback rather than
     * a crash.
     */
    @Test
    public void aUniqueWithNoBaseItemCodeIsStillIndexedWithSensibleFallbacks() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lGoreRipper = findByDisplayName("Gore Ripper");
        assertNotNull(lGoreRipper, "Gore Ripper not found in the index");
        assertEquals("", lGoreRipper.getBaseItemCode());
        assertEquals(D2GrailEntry.Tier.NORMAL, lGoreRipper.getTier());
        assertEquals(null, lGoreRipper.getUiCategoryCode());
        assertEquals(null, lGoreRipper.getRootGroup());
    }

    private static D2GrailEntry findByDisplayName(String pName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (pName.equals(lEntry.getDisplayName())) {
                return lEntry;
            }
        }
        return null;
    }
}
