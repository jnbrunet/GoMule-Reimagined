package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailIndex;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2GrailListRenderer.tooltipFor() for a MISSING entry: pure data work (D2TxtFile.propToStat() +
 * D2PropCollection, fed straight from D2GrailEntry.getSourceRow()) with no D2Item and no display
 * involved, so it is fully testable headless despite living in gomule.gui alongside the rest of
 * the Swing view classes. Does not reference D2FileManager.
 */
public class D2GrailListRendererTest {

    @Test
    public void missingUniqueTooltipRendersNameTierAndProperties() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHarlequinCrest = findByDisplayName("Harlequin Crest");
        D2GrailModel.Row lRow = rowOf(lHarlequinCrest, null);

        String lTooltip = D2GrailListRenderer.tooltipFor(lRow, null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("Harlequin Crest"));
        assertTrue(lTooltip.contains("Elite"), "Harlequin Crest is an Elite-tier base");
        // "allskills" (uniqueitems.txt prop1) renders through propToStat/D2PropCollection into a
        // real "+N to All Skills"-shaped line -- proving the prop1..prop9 pipeline actually ran,
        // not just that the name/tier header printed.
        assertTrue(lTooltip.toLowerCase(java.util.Locale.ROOT).contains("skill"),
                "expected a rendered property mentioning a skill bonus, got: " + lTooltip);
    }

    /**
     * A unique never has set bonuses (it isn't part of any set) -- this is the negative-space pin
     * for the new set-bonus sections: a unique's missing tooltip must come out byte-for-byte the
     * same shape it always has, with no "Set (N items):" or "Full Set Bonus:" heading ever
     * appended, since appendMissingSetBonuses() is only ever called for D2GrailKey.Type.SET.
     */
    @Test
    public void missingUniqueTooltipHasNoSetBonusSections() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHarlequinCrest = findByDisplayName("Harlequin Crest");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lHarlequinCrest, null), null);

        assertFalse(lTooltip.contains("Set ("), "a unique must never show a per-threshold set section");
        assertFalse(lTooltip.contains("Full Set Bonus"), "a unique must never show a full-set section");
    }

    /**
     * The real, concrete verification case: "Angelic Sickle" (Angelic Raiment, a small 4-piece
     * set). Confirmed against the raw tables (d2111/setitems.txt, d2111/sets.txt):
     * <ul>
     *   <li>setitems.txt: aprop1a="dmg%" 100-100 (2 items), aprop2a="swing3" 30-30 (3 items) --
     *   no aprop3a/4a/5a at all, so no "Set (4/5/6 items):" section should ever appear.</li>
     *   <li>sets.txt (FULLSET, index "Angelic Raiment"): PCode2a="dex" 10-10, PCode3a="mana"
     *   50-50, no PCode4a/5a; FCode1.."FCode5" = res-all+25, nofreeze+1, mag%+100, regen-mana+25,
     *   addxp+10 (FCode6/7/8 empty).</li>
     * </ul>
     * So "Set (2 items):" must combine dmg%+dex, "Set (3 items):" must combine swing3+mana, no
     * "Set (4/5/6 items):" heading may appear at all, and "Full Set Bonus:" must show all five
     * FCode1-5 stats. The exact rendered HTML this produces is reproduced in this change's report
     * to the reviewer, alongside the raw table values above.
     */
    @Test
    public void missingSetItemTooltipShowsPerThresholdAndFullSetBonuses() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lAngelicSickle = findByDisplayName("Angelic Sickle");
        D2GrailModel.Row lRow = rowOf(lAngelicSickle, null);

        String lTooltip = D2GrailListRenderer.tooltipFor(lRow, null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("Angelic Sickle"));

        assertTrue(lTooltip.contains("Set (2 items):"));
        assertTrue(lTooltip.contains("Enhanced Damage"), "aprop1a (dmg%, 2 items) missing");
        assertTrue(lTooltip.contains("Dexterity"), "sets.txt PCode2a (dex, 2 items) missing");

        assertTrue(lTooltip.contains("Set (3 items):"));
        assertTrue(lTooltip.contains("Increased Attack Speed"), "aprop2a (swing3, 3 items) missing");
        assertTrue(lTooltip.contains("Mana"), "sets.txt PCode3a (mana, 3 items) missing");

        // Angelic Raiment only defines bonuses through 3 items in both tables -- no 4/5/6-item
        // section may appear (plan: "skip any section that has no properties").
        assertFalse(lTooltip.contains("Set (4 items):"));
        assertFalse(lTooltip.contains("Set (5 items):"));
        assertFalse(lTooltip.contains("Set (6 items):"));

        assertTrue(lTooltip.contains("Full Set Bonus:"));
        assertTrue(lTooltip.contains("All Resistances"), "FCode1 (res-all) missing");
        assertTrue(lTooltip.contains("Cannot Be Frozen"), "FCode2 (nofreeze) missing");
        assertTrue(lTooltip.contains("Experience Gained"), "FCode5 (addxp) missing");

        // Label color matches D2ItemRenderer.getItemPropertyString's own red section-label style.
        assertTrue(lTooltip.contains("<font color='red'>Set (2 items): </font>"));
        assertTrue(lTooltip.contains("<font color='red'>Full Set Bonus: </font>"));
    }

    /**
     * A larger set (Immortal King, 6 pieces): every one of thresholds 2..6 has real data in BOTH
     * tables (setitems.txt "Immortal King's Stone Crusher" row: aprop1a.."aprop5a" = dmg-fire,
     * dmg-ltng, dmg-cold, dmg-mag, dmg-norm; sets.txt FULLSET "Immortal King": PCode2a.."PCode5a"
     * all "att" at increasing values, FCode1.."FCode7" a mix including two skill grants), so this
     * is the case that actually exercises all five per-threshold sections at once, not just the
     * two Angelic Raiment happens to define.
     */
    @Test
    public void missingSetItemFromASixPieceSetShowsAllFiveThresholds() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lStoneCrusher = findByDisplayName("Immortal King's Stone Crusher");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lStoneCrusher, null), null);

        for (int lThreshold = 2; lThreshold <= 6; lThreshold++) {
            assertTrue(lTooltip.contains("Set (" + lThreshold + " items):"),
                    "missing threshold " + lThreshold + " in: " + lTooltip);
        }
        assertTrue(lTooltip.contains("Full Set Bonus:"));
        assertTrue(lTooltip.contains("Barbarian Skill"), "FCode2 (bar, a Barbarian skill-tab grant) missing");
    }

    /**
     * A set item whose set name doesn't resolve to any sets.txt/FULLSET row at all (e.g. a future
     * mod entry, or simply bad data) must still render -- at minimum its own name and its own
     * setitems.txt properties -- rather than the missing FULLSET lookup taking the whole tooltip
     * down. The item's OWN per-threshold bonuses (setitems.txt "aprop*", independent of FULLSET)
     * still appear; only the sets.txt-sourced halves are affected: no "Full Set Bonus:" section at
     * all (FCode1-8 all come from FULLSET), and the "Set (2/3 items):" sections that DO appear are
     * missing their PCode contribution (no "Dexterity"/"Mana", Angelic Raiment's own PCode2a/3a).
     */
    @Test
    public void missingSetItemWithNoResolvableSetRowStillRendersItsName() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lAngelicSickle = findByDisplayName("Angelic Sickle");
        D2GrailEntry lWithUnknownSet = new D2GrailEntry(
                lAngelicSickle.getKey(), lAngelicSickle.getDisplayName(), lAngelicSickle.getBaseItemCode(),
                lAngelicSickle.getBaseItemName(), lAngelicSickle.getTier(), lAngelicSickle.getUiCategoryCode(),
                lAngelicSickle.getUiCategoryLabel(), lAngelicSickle.getRootGroup(),
                "DefinitelyNotARealSetName", lAngelicSickle.getSetSize(), lAngelicSickle.getSetUiClassCode(),
                lAngelicSickle.getSetUiClassLabel(), lAngelicSickle.getInvfile(), lAngelicSickle.isChronicle(),
                lAngelicSickle.getSourceRow());

        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWithUnknownSet, null), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("Angelic Sickle"));
        // The item's own setitems.txt properties (not sets.txt-sourced) still render fine --
        // only the FULLSET-dependent sections are affected by the unresolvable set name.
        assertTrue(lTooltip.contains("Attack Rating"), "the item's own prop1 (att) should still render");
        assertTrue(lTooltip.contains("Set (2 items):"), "the item's own aprop1a still renders with no FULLSET row");
        // "+10 to Dexterity" specifically (Angelic Raiment's PCode2a) -- not the bare substring
        // "Dexterity", which now also appears legitimately in this item's own "Required Dexterity:
        // 25" base-stat line (a change unrelated to the set-bonus machinery this test targets).
        assertFalse(lTooltip.contains("+10 to Dexterity"), "Angelic Raiment's PCode2a can't appear without a FULLSET row");
        assertFalse(lTooltip.contains("Full Set Bonus"), "no full-set section without a resolvable set row");
    }

    @Test
    public void aSetHeaderStringHasNoTooltip() {
        assertNull(D2GrailListRenderer.tooltipFor("== Angelic Raiment ==   2 / 4", null));
    }

    /**
     * An entry with no source row at all (missingTooltip()'s "if (lRow != null)" branch never
     * runs) must still degrade to at least the name, never throw and never leave the tooltip
     * blank -- the same defensive stance CLAUDE.md documents for every other "nothing usable to
     * render" situation in this codebase. Can't easily corrupt a REAL entry's prop columns
     * in-place without touching D2TxtFile's shared static table (which every other test also
     * reads), so this builds a synthetic entry with a null source row instead.
     */
    @Test
    public void anEntryWithNoSourceRowDegradesToTheNameInsteadOfThrowing() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lEntry = findByDisplayName("Harlequin Crest");
        D2GrailEntry lEntryWithNoSourceRow = new D2GrailEntry(
                lEntry.getKey(), lEntry.getDisplayName(), null, null, D2GrailEntry.Tier.NORMAL,
                null, null, null, null, 0, null, null, "", true, null);
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lEntryWithNoSourceRow, null), null);
        assertNotNull(lTooltip);
        assertFalse(lTooltip.isEmpty());
        assertTrue(lTooltip.contains("Harlequin Crest"));
    }

    /**
     * A missing ARMOR unique: "Harlequin Crest" (base code "uap" = "Shako", armor.txt). Verified by
     * hand against armor.txt's real "Shako" row: minac=98, maxac=141 (-> "Defense: 98 - 141"),
     * block=0 (a helm, not a shield -- no "Chance to Block" line), durability=12/nodurability=0
     * (-> "Durability: 12"), reqstr=50 (-> "Required Strength: 50"), reqdex=0 (omitted),
     * levelreq=43 -- but overridden by uniqueitems.txt's OWN "lvl req"=62 for this unique
     * (-> "Required Level: 62", not 43).
     */
    @Test
    public void missingArmorUniqueTooltipShowsDefenseRangeAndRequirements() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHarlequinCrest = findByDisplayName("Harlequin Crest");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lHarlequinCrest, null), null);

        assertTrue(lTooltip.contains("Defense: 98 - 141"), lTooltip);
        assertFalse(lTooltip.contains("Chance to Block"), "Shako is a helm, not a shield: " + lTooltip);
        assertTrue(lTooltip.contains("Durability: 12"), lTooltip);
        assertTrue(lTooltip.contains("Required Level: 62"), "unique's own lvl req (62) must win over the base's (43): " + lTooltip);
        assertTrue(lTooltip.contains("Required Strength: 50"), lTooltip);
        assertFalse(lTooltip.contains("Required Dexterity"), "Shako's reqdex is 0, must be omitted: " + lTooltip);
    }

    /**
     * A missing WEAPON unique: "Hand of Blessed Light" (base code "9ws" = "Divine Scepter",
     * weapons.txt). Verified by hand against weapons.txt's real "Divine Scepter" row: mindam=16,
     * maxdam=38, both "1or2handed" and "2handed" blank (-> one-hand-only "One Hand Damage: 16 -
     * 38"), durability=250, reqstr=103, reqdex blank (omitted), levelreq=25 -- but overridden by
     * uniqueitems.txt's OWN "lvl req"=55 for this unique (-> "Required Level: 55", not 25).
     */
    @Test
    public void missingWeaponUniqueTooltipShowsDamage() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHandOfBlessedLight = findByDisplayName("Hand of Blessed Light");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lHandOfBlessedLight, null), null);

        assertTrue(lTooltip.contains("One Hand Damage: 16 - 38"), lTooltip);
        assertFalse(lTooltip.contains("Two Hand Damage"), "not 2handed/1or2handed: " + lTooltip);
        assertTrue(lTooltip.contains("Durability: 250"), lTooltip);
        assertTrue(lTooltip.contains("Required Level: 55"), "unique's own lvl req (55) must win over the base's (25): " + lTooltip);
        assertTrue(lTooltip.contains("Required Strength: 103"), lTooltip);
        assertFalse(lTooltip.contains("Required Dexterity"), lTooltip);
    }

    /**
     * armor.txt itself carries an always-zero "mindam"/"maxdam" pair on every row (confirmed
     * against "Shako": mindam=0, maxdam=0) -- the regression pin for a real bug caught while
     * writing this feature: naively rendering weapon columns off ANY base row (rather than only one
     * that actually came from weapons.txt, checked via D2TxtFileItemProperties.getFileName()) would
     * print a bogus "One Hand Damage: 0 - 0" on every single piece of armor.
     */
    @Test
    public void missingArmorTooltipNeverShowsPhantomZeroDamage() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHarlequinCrest = findByDisplayName("Harlequin Crest");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lHarlequinCrest, null), null);
        assertFalse(lTooltip.contains("Damage: 0 - 0"), lTooltip);
        assertFalse(lTooltip.toLowerCase(java.util.Locale.ROOT).contains("hand damage"), lTooltip);
    }

    /**
     * A base code that resolves to no row at all (a future mod entry, or bad data) must still
     * render the entry's name and its own properties -- the missing base-stats section simply
     * contributes nothing, rather than the D2TxtFile.search() failure taking the whole tooltip down.
     */
    @Test
    public void missingEntryWithUnresolvableBaseCodeStillRendersNameAndProperties() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHarlequinCrest = findByDisplayName("Harlequin Crest");
        D2GrailEntry lWithUnknownBaseCode = new D2GrailEntry(
                lHarlequinCrest.getKey(), lHarlequinCrest.getDisplayName(), "zzznotarealcode",
                lHarlequinCrest.getBaseItemName(), lHarlequinCrest.getTier(), lHarlequinCrest.getUiCategoryCode(),
                lHarlequinCrest.getUiCategoryLabel(), lHarlequinCrest.getRootGroup(), null, 0, null, null,
                lHarlequinCrest.getInvfile(), lHarlequinCrest.isChronicle(), lHarlequinCrest.getSourceRow());

        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWithUnknownBaseCode, null), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("Harlequin Crest"));
        assertTrue(lTooltip.toLowerCase(java.util.Locale.ROOT).contains("skill"),
                "the entry's own properties (from its real source row) should still render: " + lTooltip);
        assertFalse(lTooltip.contains("Defense"), "no base row means no base stats at all: " + lTooltip);
        assertFalse(lTooltip.contains("Required Level"), lTooltip);
        assertFalse(lTooltip.contains("Required Strength"), lTooltip);
    }

    private static D2GrailModel.Row rowOf(D2GrailEntry pEntry, Object pUnusedFinding) {
        // D2GrailModel.Row's constructor is package-private (gomule.grail); build one the same
        // way D2ViewGrail does, through a tiny one-entry model instead of reflection.
        D2GrailModel lModel = new D2GrailModel(java.util.Collections.singletonList(pEntry));
        lModel.setTab(pEntry.getKey().getType());
        lModel.setIncludeNonChronicle(true);
        for (D2GrailModel.Row lRow : lModel.getRows()) {
            if (lRow.getEntry() == pEntry) {
                return lRow;
            }
        }
        throw new AssertionError("row not found for " + pEntry.getKey());
    }

    private static D2GrailEntry findByDisplayName(String pName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (pName.equals(lEntry.getDisplayName())) {
                return lEntry;
            }
        }
        throw new AssertionError(pName + " not found in the index");
    }
}
