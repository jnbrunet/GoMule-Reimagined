package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailIndex;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * weapons.txt). Verified by hand against weapons.txt's real "Divine Scepter" row (mindam=16,
     * maxdam=38, both "1or2handed" and "2handed" blank -> one-hand-only) run through
     * D2Item.applyItemMods()'s own weapon-damage formula against this unique's real prop2 "dmg%"
     * (130-160% Enhanced Damage) and prop7 "dmg-norm" (min=20, max=45 -- NOT a rolled range: its
     * properties.txt row is func1=15/func2=16, "min feeds mindamage, max feeds maxdamage", two
     * DIFFERENT stats always both applied -- see D2GrailListRenderer.isRangeEligible's javadoc, and
     * D2GrailListRendererRangeTest for the dedicated "Adds 20 - 45 Damage" pin), so dmgTriple[1]=20
     * and dmgTriple[2]=45 identically in BOTH flavours:
     * <pre>
     *   min low  = floor(16/100*130 + 16+20) = floor(20.8+36) = 56
     *   min high = floor(16/100*160 + 16+20) = floor(25.6+36) = 61
     *   max low  = floor(38/100*130 + 38+45) = floor(49.4+83) = 132
     *   max high = floor(38/100*160 + 38+45) = floor(60.8+83) = 143
     * </pre>
     * giving "One Hand Damage: 56-61 to 132-143", not weapons.txt's bare, unmodified "16 - 38"
     * (GoMule showed the raw base before the item-modifier feature this pins). durability=250 (no
     * durability-modifying prop on this row, so unaffected), reqstr=103 (ditto, unaffected),
     * reqdex blank (omitted), levelreq=25 -- but overridden by uniqueitems.txt's OWN "lvl req"=55
     * for this unique (-> "Required Level: 55", not 25).
     */
    @Test
    public void missingWeaponUniqueTooltipShowsDamage() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHandOfBlessedLight = findByDisplayName("Hand of Blessed Light");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lHandOfBlessedLight, null), null);

        assertTrue(lTooltip.contains("One Hand Damage: 56-61 to 132-143"), lTooltip);
        assertFalse(lTooltip.contains("One Hand Damage: 16 - 38"),
                "the item's own dmg%/dmg-norm modifiers must be applied, not the bare base range: " + lTooltip);
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

    /**
     * The reported bug, end to end: "Schaefer's Hammer" (uniqueitems.txt *ID 257, code 7wh) showed
     * every line from the mod's own item page EXCEPT its first one, "10% Chance to cast level 10
     * Static Field on striking" -- its prop1 "hit-skill", par="Static Field", min=10 ("% Chance"),
     * max=10 ("Skill Level"). D2TxtFile.propToStat() dropped the whole property because "par" is a
     * skill NAME rather than a number (see D2PropToStatSkillEventTest for that fix's own unit
     * coverage), so nothing reached this tooltip at all.
     * <p>
     * Asserted here rather than only at the propToStat level because the missing-item tooltip is
     * where the bug was actually seen, and because the line has to survive this class's whole
     * min/max two-pass range machinery: "hit-skill" is uiRangeType 7, so isRangeEligible() must
     * keep feeding BOTH passes the row's original (10, 10) columns and the two fragments must merge
     * back into one unchanged line -- never an invented "10-10" range across the chance and the
     * level, which are two different quantities.
     */
    @Test
    public void schaefersHammerShowsItsChanceToCastLine() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lSchaefers = findByDisplayName("Schaefer's Hammer");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lSchaefers, null), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("10% Chance to cast level 10 Static Field on striking"),
                "prop1 hit-skill must render the mod site's own line: " + lTooltip);
        assertFalse(lTooltip.contains("10-10"), "chance and skill level are not a range: " + lTooltip);
        // The rest of the item must be untouched by the new property -- these are the lines the
        // tooltip already showed correctly before the fix.
        assertTrue(lTooltip.contains("+250-300% Enhanced Damage"), lTooltip);
        assertTrue(lTooltip.contains("Adds 50 - 500 Lightning Damage"), lTooltip);
    }

    /**
     * A uniqueitems.txt "propN" column may name a row of propertygroups.txt -- a D2R-era table
     * ./d2111 did not even carry -- instead of a properties.txt code, meaning "roll exactly one of
     * these". propToStat finds no properties.txt row for such a code and returns nothing, so every
     * one of them used to vanish from the tooltip silently.
     * <p>
     * "Renewed Flame Rift" (uniqueitems.txt index "Crafted Flame Rift") is the case this was
     * reported on: six of its nine property slots are Incendiary-Affix1..6, and the tooltip showed
     * nothing but its two fixed sunder lines ("Monster Fire Immunity is Sundered", "Fire Resist
     * -70%") plus its charm weight. Each group's candidates come from the group row's own
     * Prop1..Prop8 / ModMinN / ModMaxN columns, verified against d2111/propertygroups.txt:
     * Affix1 = extra-fire 5-15 | pierce-fire 5-15 | fireskill 1, Affix2 = mag% 20-30 | gold% 40-60,
     * Affix3 = hp 20-50 | mana 20-50 | ac 50-100, Affix4 = move1 10-20 | balance1 15-30 |
     * all-stats 5-10, Affix6 = swing1 5-15 | cast1 5-15.
     * <p>
     * FIVE of the six slots show, not six: Incendiary-Affix5 (res-fire 70 | res-fire 35) is the one
     * whose minN/maxN -- the pick COUNT for a group slot -- are both blank, so it rolls nothing and
     * must not be listed at all. Confirmed against the game by the player: a Renewed Flame Rift
     * never grants that fire resistance, which is what leaves its fixed "Fire Resist -70%" penalty
     * standing. See rollsAtLeastOnce.
     */
    @Test
    public void missingUniqueTooltipShowsEachPropertyGroupsCandidates() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lRenewedFlameRift = findByStrippedDisplayName("Renewed Flame Rift");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lRenewedFlameRift, null), null);

        assertNotNull(lTooltip);
        // The fixed properties it already showed, unchanged.
        assertTrue(lTooltip.contains("Monster Fire Immunity is Sundered"), lTooltip);
        assertTrue(lTooltip.contains("Fire Resist -70%"), lTooltip);
        // One "One of:" heading per group slot that actually rolls -- five of the row's six.
        assertEquals(5, countOccurrences(lTooltip, "One of:"),
                "one heading per rolling propertygroups.txt slot on the row: " + lTooltip);
        // Every candidate of every group, each with the group's own ModMin-ModMax range.
        assertTrue(lTooltip.contains("+5-15% to Fire Skill Damage"), lTooltip);
        assertTrue(lTooltip.contains("to Enemy Fire Resistance"), lTooltip);
        assertTrue(lTooltip.contains("+1 to Fire Skills"), lTooltip);
        assertTrue(lTooltip.contains("20-30% Better Chance of Getting Magic Items"), lTooltip);
        assertTrue(lTooltip.contains("40-60% Extra Gold from Monsters"), lTooltip);
        assertTrue(lTooltip.contains("+20-50 to Life"), lTooltip);
        assertTrue(lTooltip.contains("+20-50 to Mana"), lTooltip);
        assertTrue(lTooltip.contains("+50-100 Defense"), lTooltip);
        assertTrue(lTooltip.contains("+10-20% Faster Run/Walk"), lTooltip);
        assertTrue(lTooltip.contains("+15-30% Faster Hit Recovery"), lTooltip);
        assertTrue(lTooltip.contains("All Stats +5-10"), lTooltip);
        assertTrue(lTooltip.contains("+5-15% Increased Attack Speed"), lTooltip);
        assertTrue(lTooltip.contains("+5-15% Faster Cast Rate"), lTooltip);
        // Affix5 picks zero candidates (blank minN/maxN), so neither of its two res-fire
        // alternatives may appear -- nor, obviously, the "105" they would have summed to had they
        // been pooled into one collection instead of rendered one candidate at a time.
        assertFalse(lTooltip.contains("Fire Resist +70%"), lTooltip);
        assertFalse(lTooltip.contains("Fire Resist +35%"), lTooltip);
        assertFalse(lTooltip.contains("105"), lTooltip);
    }

    /**
     * The other shape a property group takes: "Wraithstep" (unique mirrored boots) references
     * "skilltab-war", whose single candidate is "skilltab" with ParMin 21 / ParMax 23 -- a range of
     * skill-tab IDS, not a value range, one of which the item grants. All three must be listed
     * (the Warlock's Demon, Eldritch and Chaos tabs), each at the group's own ModMin/ModMax of 1.
     * <p>
     * "+1 to Fire Skills" on the Flame Rift above is the companion pin for a second bug this same
     * case exposed: item_elemskillfire is named only in properties.txt's stat2 column, never a
     * stat1, so D2Prop's descfunc-19 fallback could not find its tooltip row and read its value out
     * of pVals' last slot -- which is propToStat's always-zeroed "par" -- printing "+0".
     */
    @Test
    public void missingUniqueTooltipExpandsAPropertyGroupsParameterRange() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lWraithstep = findByStrippedDisplayName("Wraithstep");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWraithstep, null), null);

        assertNotNull(lTooltip);
        assertEquals(1, countOccurrences(lTooltip, "One of:"), lTooltip);
        assertTrue(lTooltip.contains("+1 to Demon Skills"), lTooltip);
        assertTrue(lTooltip.contains("+1 to Eldritch Skills"), lTooltip);
        assertTrue(lTooltip.contains("+1 to Chaos Skills"), lTooltip);
        // Its own fixed properties are untouched by the group slot.
        assertTrue(lTooltip.contains("+30% Faster Run/Walk"), lTooltip);
        assertTrue(lTooltip.contains("+10-15 to Dexterity"), lTooltip);
    }

    /**
     * Negative space: an ordinary unique names no property group at all, so no "One of:" heading
     * may ever appear on one. Guards against propertyGroupMembers() mistaking a plain
     * properties.txt code for a group.
     */
    @Test
    public void anOrdinaryUniqueHasNoPropertyGroupSection() {
        D2TxtFile.constructTxtFiles("./d2111");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(findByDisplayName("Harlequin Crest"), null), null);

        assertFalse(lTooltip.contains("One of:"), lTooltip);
    }

    private static int countOccurrences(String pText, String pNeedle) {
        int lCount = 0;
        int lAt = pText.indexOf(pNeedle);
        while (lAt >= 0) {
            lCount++;
            lAt = pText.indexOf(pNeedle, lAt + pNeedle.length());
        }
        return lCount;
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

    /**
     * Same lookup as findByDisplayName, for the entries whose display name carries the raw "ÿc4"
     * colour-code markup the translation tables store (every crafted sunder charm does).
     */
    private static D2GrailEntry findByStrippedDisplayName(String pName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            String lName = lEntry.getDisplayName();
            if (lName != null && pName.equals(gomule.item.D2ItemRenderer.stripColorCodes(lName))) {
                return lEntry;
            }
        }
        throw new AssertionError(pName + " not found in the index");
    }
}
