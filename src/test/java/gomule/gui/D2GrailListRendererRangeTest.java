package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailIndex;
import gomule.grail.D2GrailModel;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The missing-item tooltip's new "show both ends of the range" behaviour (D2GrailListRenderer's
 * PropSlot/addSlotIfPresent/renderSlotsWithRanges + the mergeRangeFragments/mergeLinePiece/
 * mergeNumberToken merge helpers), verified two ways: end-to-end through the public
 * D2GrailListRenderer.tooltipFor() entry point against a REAL uniqueitems.txt row ("Wrath of the
 * Seraphim", *ID 618, code 7ws -- see this class's own javadoc in D2GrailListRenderer for the raw
 * column values), and directly against the package-private merge helpers for the edge cases that
 * are awkward to provoke through a real item (a negative pair, a mismatched-literal fallback, a
 * differing-line-count fallback).
 * <p>
 * Does not touch D2TxtFile.propToStat() or D2PropCollection at all -- neither is modified by this
 * change, and neither is exercised here beyond what tooltipFor() already calls into.
 */
public class D2GrailListRendererRangeTest {

    // --------------------------------------------------------------------------------------
    // End-to-end: Wrath of the Seraphim (real uniqueitems.txt row)
    // --------------------------------------------------------------------------------------

    /**
     * Wrath of the Seraphim's prop1 "dmg%" (min1=300/max1=400) and prop2 "dmg-max" (min2=100/
     * max2=200) are GoMule's own verified regression case for this feature: today (pre-fix)
     * GoMule shows only "+400% Enhanced Damage" (the max, since "item_maxdamage_percent" contains
     * "max") and only "+100 to Maximum Damage" (the min, since propToStat's max-collapsing branch
     * doesn't apply to "maxdamage" -- an inconsistency in ITS OWN RIGHT, one stat showing its top
     * end and the other its bottom, that this feature also resolves by simply always showing both).
     * After the fix both must show the full range from the mod's own website: "+300-400% Enhanced
     * Damage" and "+100-200 to Maximum Damage".
     */
    @Test
    public void wrathOfTheSeraphimShowsBothVerifiedRanges() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lWrath = findByDisplayName("Wrath of the Seraphim");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWrath), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("+300-400% Enhanced Damage"),
                "prop1 dmg% (300-400) should render as a range: " + lTooltip);
        assertTrue(lTooltip.contains("+100-200 to Maximum Damage"),
                "prop2 dmg-max (100-200) should render as a range: " + lTooltip);
    }

    /**
     * Every OTHER property on this same row has min==max and must render EXACTLY as it always
     * has -- a single value, no "100-100"-shaped range ever invented for a stat that never had
     * one. Covers prop7 "pal" (3-3, class skill levels), prop8 "swing3" (40-40, IAS) and prop4
     * "mana-kill" (15-15, mana after each kill) -- three different properties.txt "func" shapes
     * (item_addclassskills, a plain percent stat, a plain flat stat) so this isn't just pinning
     * one lucky case.
     */
    @Test
    public void wrathOfTheSeraphimEqualMinMaxPropsAreUnchanged() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lWrath = findByDisplayName("Wrath of the Seraphim");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWrath), null);

        assertTrue(lTooltip.contains("+3 to Paladin Skill Levels"), lTooltip);
        assertTrue(lTooltip.contains("+40% Increased Attack Speed"), lTooltip);
        assertTrue(lTooltip.contains("+15 to Mana after each Kill"), lTooltip);

        // None of these must have picked up a spurious "N-N" range rendering.
        assertFalse(lTooltip.contains("+3-3 to Paladin Skill Levels"), lTooltip);
        assertFalse(lTooltip.contains("+40-40% Increased Attack Speed"), lTooltip);
        assertFalse(lTooltip.contains("+15-15 to Mana after each Kill"), lTooltip);
    }

    /**
     * prop5 "dmg%/lvl" (par5=16, min5/max5 both blank) is a per-level property, not a min/max
     * range: the value lives in "par", read identically by both the min- and max-flavoured pass,
     * so it must merge back to the exact single-value display it had before this change --
     * "(Based on Character Level)", not a range of any kind.
     */
    @Test
    public void wrathOfTheSeraphimPerLevelPropRendersAsASingleValue() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lWrath = findByDisplayName("Wrath of the Seraphim");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWrath), null);

        assertTrue(lTooltip.contains("(Based on Character Level)"),
                "prop5 dmg%/lvl (par=16) should still render its per-level suffix: " + lTooltip);
    }

    /**
     * A slot with a filled min column and a BLANK max column -- Gheed's Wager's real prop8
     * "cheap" (par8=-10, min8=10, max8 blank; "cheap"'s properties.txt uiRangeType is blank, so
     * it IS range-eligible) -- must still render its one real value ("10% Reduced Vendor Prices"),
     * never regress to "0" from the max-flavoured pass otherwise reading two blank columns
     * (propToStat(code, "", "", param, 0) resolves to a bare 0). See
     * D2GrailListRenderer.renderSlotsWithRanges's javadoc for the full normalization rationale.
     * Verified empirically against the real row: today's (pre-feature) single-pass output is
     * "Reduces all Vendor Prices 10%", so that is what must survive unchanged.
     */
    @Test
    public void gheedsWagerBlankMaxColumnStillShowsItsRealValue() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lGheedsWager = findByDisplayName("Gheed's Wager");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lGheedsWager), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("Reduces all Vendor Prices 10%"),
                "Gheed's Wager's real \"cheap\" (10, blank max) must not regress to 0%: " + lTooltip);
        assertFalse(lTooltip.contains("Reduces all Vendor Prices 0%"), lTooltip);
    }

    /**
     * The second, independent regression this feature had to be corrected for mid-implementation:
     * a properties.txt code whose min/max columns are NOT one ranged number at all, per its own
     * "uiRangeType" column. "skill-rand" (uiRangeType=2, "Min Skill ID"/"Max Skill ID") is the
     * cleanest REAL, verifiable case in ./d2111: "Ormus' Robes" prop5/6/7 = skill-rand, par=3,
     * min=36, max=65 -- three DIFFERENT numeric skill ids to choose from, not a range of one stat.
     * Confirmed empirically: today's (pre-feature) single-pass output is "+65 to Fire Bolt
     * (Sorceress Only)" (repeated for all three identical slots); WITHOUT the uiRangeType gate,
     * this feature's own two-pass machinery would instead invent a nonsensical "+36-65 to Fire
     * Bolt" (feeding 36 through one pass and 65 through the other, then merging them as if they
     * were a real range). The gate must keep producing the real, single "+65" value, never a range
     * of skill ids.
     * <p>
     * (hit-skill/charged/death-skill share the same gate but are covered separately -- see
     * fallenHerosDisgraceNeverInventsADeathSkillRange below and D2PropToStatSkillEventTest. Those
     * codes' "par" columns hold a SKILL NAME rather than a numeric id, which propToStat used to
     * reject outright, so at the time this test was written they rendered nothing at all and could
     * not prove anything through their OUTPUT the way skill-rand can; they render properly now, and
     * this test stays on skill-rand because uiRangeType 2 is the shape it was written to pin.)
     */
    @Test
    public void ormusRobesSkillRandNeverInventsASkillIdRange() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lOrmusRobes = findByDisplayName("Ormus' Robes");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lOrmusRobes), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("+65 to Fire Bolt (Sorceress Only)"),
                "skill-rand's real single value (65) must still render: " + lTooltip);
        assertFalse(lTooltip.contains("36-65"), "must never invent a skill-id range: " + lTooltip);
        assertFalse(lTooltip.contains("65-36"), "must never invent a skill-id range: " + lTooltip);
    }

    /**
     * "Fallen Hero's Disgrace" (*ID 1213) is the row the parent task's own uiRangeType correction
     * was raised against: prop8 "death-skill" (uiRangeType=7) has min8=100 ("% Chance") and
     * max8=15 ("Skill Level") -- two different quantities, not a range, and min &gt; max to boot.
     * When this test was written the slot rendered NOTHING at all -- its par8 is the skill NAME
     * "Hailstorm" (not a numeric id), which D2TxtFile.propToStat() failed to parse as an int and
     * silently returned no D2Prop for. That separate gap is now fixed (see
     * D2PropToStatSkillEventTest), so the row's real line is asserted here too: the chance (100,
     * the min column) and the skill level (15, the max column) must stay in their own slots. The
     * negative-space assertions remain the point of this test, though: neither "100-15" nor
     * "15-100" (nor the " to " form of either) may ever appear as an invented death-skill range,
     * while this same row's two GENUINE ranges (prop6 "red-dmg" 10-15, prop7 "red-mag" 10-15, both
     * uiRangeType-blank) must still render correctly -- proving the gate neither merges the
     * skill-event pair nor suppresses a real range on the same row.
     */
    @Test
    public void fallenHerosDisgraceNeverInventsADeathSkillRange() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lFallenHero = findByDisplayName("Fallen Hero's Disgrace");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lFallenHero), null);

        assertNotNull(lTooltip);
        assertTrue(lTooltip.contains("100% Chance to cast level 15 Hailstorm when you Die"), lTooltip);
        assertFalse(lTooltip.contains("100-15"), lTooltip);
        assertFalse(lTooltip.contains("15-100"), lTooltip);
        assertFalse(lTooltip.contains("100 to 15"), lTooltip);
        assertFalse(lTooltip.contains("15 to 100"), lTooltip);
        // The row's two genuine ranges (prop6/prop7, both real, non-skill, uiRangeType-blank
        // codes) must still render correctly alongside the broken death-skill slot.
        assertTrue(lTooltip.contains("Damage Reduced by 10-15"), lTooltip);
        assertTrue(lTooltip.contains("Magic Damage Reduced by 10-15"), lTooltip);
    }

    // --------------------------------------------------------------------------------------
    // Direct unit coverage of the merge helpers
    // --------------------------------------------------------------------------------------

    @Test
    public void mergeRangeFragmentsReturnsEitherSideWhenIdentical() {
        String lFragment = "<font color=\"#4850b8\">+3 to Paladin Skill Levels<br>&#10;</font>";
        assertEquals(lFragment, D2GrailListRenderer.mergeRangeFragments(lFragment, lFragment));
    }

    @Test
    public void mergeRangeFragmentsMergesASingleDifferingNumber() {
        String lMin = "<font color=\"#4850b8\">+300% Enhanced Damage<br>&#10;</font>";
        String lMax = "<font color=\"#4850b8\">+400% Enhanced Damage<br>&#10;</font>";
        String lMerged = D2GrailListRenderer.mergeRangeFragments(lMin, lMax);
        assertEquals("<font color=\"#4850b8\">+300-400% Enhanced Damage<br>&#10;</font>", lMerged);
    }

    @Test
    public void mergeRangeFragmentsUsesToFormForANegativePair() {
        String lMin = "<font color=\"#4850b8\">-90 to Cold Resist<br>&#10;</font>";
        String lMax = "<font color=\"#4850b8\">-70 to Cold Resist<br>&#10;</font>";
        String lMerged = D2GrailListRenderer.mergeRangeFragments(lMin, lMax);
        assertEquals("<font color=\"#4850b8\">-90 to -70 to Cold Resist<br>&#10;</font>", lMerged);
    }

    /**
     * A literal-text mismatch between the two sides (here, a "+" sign present only on the max
     * side -- exactly what happens when a stat's rendered sign flips across zero, e.g. Gheed's
     * Wager's real res-all -20/20) must fall back to the max piece unchanged, never splice the two
     * different wordings together.
     */
    @Test
    public void mergeRangeFragmentsFallsBackToMaxOnMismatchedLiteral() {
        String lMin = "<font color=\"#4850b8\">-20% to All Resistances<br>&#10;</font>";
        String lMax = "<font color=\"#4850b8\">+20% to All Resistances<br>&#10;</font>";
        String lMerged = D2GrailListRenderer.mergeRangeFragments(lMin, lMax);
        assertEquals(lMax, lMerged);
    }

    /**
     * A different number of rendered lines between the two passes (should not happen in practice
     * given the identical pipeline run on both sides, but is the documented, safe fallback) must
     * return the max fragment whole rather than try to zip mismatched lines together.
     */
    @Test
    public void mergeRangeFragmentsFallsBackToMaxOnDifferingLineCount() {
        String lMin = "<font color=\"#4850b8\">+3 to Paladin Skill Levels<br>&#10;</font>";
        String lMax = "<font color=\"#4850b8\">+3 to Paladin Skill Levels<br>&#10;"
                + "+40% Increased Attack Speed<br>&#10;</font>";
        String lMerged = D2GrailListRenderer.mergeRangeFragments(lMin, lMax);
        assertEquals(lMax, lMerged);
    }

    @Test
    public void mergeLinePieceMergesACombinedDamageLine() {
        // Simulates combineProps() fusing a min-damage and max-damage property into one line, the
        // way the parent task's javadoc describes ("Adds 10 - 30 damage" / "Adds 20 - 40 damage").
        String lMerged = D2GrailListRenderer.mergeLinePiece("Adds 10 - 30 damage", "Adds 20 - 40 damage");
        assertEquals("Adds 10-20 - 30-40 damage", lMerged);
    }

    @Test
    public void mergeNumberTokenReturnsTheSharedValueWhenEqual() {
        assertEquals("15", D2GrailListRenderer.mergeNumberToken("15", "15"));
    }

    @Test
    public void mergeNumberTokenBuildsAnAscendingRangeForNonNegativeValues() {
        assertEquals("300-400", D2GrailListRenderer.mergeNumberToken("300", "400"));
    }

    @Test
    public void mergeNumberTokenUsesToFormWhenEitherSideIsNegative() {
        assertEquals("-90 to -70", D2GrailListRenderer.mergeNumberToken("-90", "-70"));
        assertEquals("-20 to 20", D2GrailListRenderer.mergeNumberToken("-20", "20"));
    }

    @Test
    public void mergeNumberTokenNeverInventsABackwardsRange() {
        // min > max: emit the max token alone rather than "20-10".
        assertEquals("10", D2GrailListRenderer.mergeNumberToken("20", "10"));
    }

    // --------------------------------------------------------------------------------------
    // Base-stat lines apply the item's OWN modifiers (D2Item.applyItemMods(), D2Item.java roughly
    // lines 1509-1682, mirrored by D2GrailListRenderer's accumulateItemMods/computeWeaponDamage/
    // applyArmorFormula/applyPercentPlusFlat/applyPercentRequirement -- see their javadocs).
    // --------------------------------------------------------------------------------------

    /**
     * Wrath of the Seraphim's base weapon (7ws -> weapons.txt "mindam"=37, "maxdam"=43) run
     * through D2Item.applyItemMods()'s own weapon-damage formula against this unique's real prop1
     * "dmg%" (300-400) and prop5 "dmg%/lvl" (par=16, -> 198 at ASSUMED_CHARACTER_LEVEL 99, same in
     * both flavours) and prop2 "dmg-max" (100-200), computed by hand:
     * <pre>
     *   min-flavoured: minDamage = floor(37/100*300 + 37)             = 148
     *                  maxDamage = floor(43/100*(300+198) + 43+100)   = floor(214.14+143) = 357
     *   max-flavoured: minDamage = floor(37/100*400 + 37)             = 185
     *                  maxDamage = floor(43/100*(400+198) + 43+200)   = floor(257.14+243) = 500
     * </pre>
     * giving "One Hand Damage: 148-185 to 357-500" -- matching the mod's own website's "148-185"
     * low end exactly (its "345-501" high end differs only because the website assumes a different
     * character level for the per-level stat, not a defect here -- GoMule's own number must match
     * GoMule's own found-item math, not the website's).
     */
    @Test
    public void wrathOfTheSeraphimBaseDamageAppliesItsOwnModifiers() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lWrath = findByDisplayName("Wrath of the Seraphim");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lWrath), null);

        assertTrue(lTooltip.contains("One Hand Damage: 148-185 to 357-500"), lTooltip);
        assertFalse(lTooltip.contains("One Hand Damage: 37 - 43"),
                "must no longer show the raw, unmodified weapons.txt base range: " + lTooltip);
    }

    /**
     * "Maelstrom" (base code "ywn" = "Yew Wand", weapons.txt mindam=2/maxdam=8): its real
     * uniqueitems.txt row has elemental (ltng-min/ltng-max), resistance, mana, cast-rate and skill
     * properties, but NONE of the pNum 17/21/22/218/219 (EDmg/MinDmg/MaxDmg/MaxDmg-per-level/
     * MaxDmg%-per-level) stats D2Item.applyItemMods() folds into weapon damage -- so both the
     * min- and max-flavoured accumulations come out all-zero, and the base range must render
     * BYTE-IDENTICAL to before this change: "2 - 8", the plain " - " separator, never a "2-2 to
     * 8-8"-shaped range invented from nothing.
     */
    @Test
    public void aWeaponWithNoDamageModifyingPropertyKeepsTheUnmodifiedBaseRange() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lMaelstrom = findByDisplayName("Maelstrom");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lMaelstrom), null);

        assertTrue(lTooltip.contains("One Hand Damage: 2 - 8"), lTooltip);
        assertTrue(lTooltip.contains("Durability: 250"), lTooltip);
        assertTrue(lTooltip.contains("Required Level: 14"), lTooltip);
    }

    /**
     * Gheed's Wager (troll belt, base code "utc" -> armor.txt minac=59/maxac=66) has a real
     * prop4 "ac%" (100-200% Enhanced Defense, pNum 16) and no flat "ac" (armorclass) or "+Def/lvl"
     * property, so by hand: A (low side) = floor(59/100*100 + 59) = 118, B (high side) =
     * floor(66/100*200 + 66) = 198 -- the Defense line must have moved off its bare base
     * "59 - 66" onto "118 - 198", per D2Item.applyItemMods()'s own armor formula (the low end
     * through the min-flavoured armourTriple, the high end through the max-flavoured one).
     */
    @Test
    public void gheedsWagerDefenseAppliesItsOwnEnhancedDefense() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lGheedsWager = findByDisplayName("Gheed's Wager");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lGheedsWager), null);

        assertTrue(lTooltip.contains("Defense: 118 - 198"), lTooltip);
        assertFalse(lTooltip.contains("Defense: 59 - 66"),
                "must no longer show the raw, unmodified armor.txt base range: " + lTooltip);
    }

    /**
     * "Steelgoad" (Voulge, base durability=250) has a real prop6 "dur" (min=20/max=40, pNum 73,
     * flat +Durability -- a genuine RANGE, unlike Gheed's Wager's equal-ended "cheap"): by hand,
     * min-flavoured = floor(250/100*0 + (250+20)) = 270, max-flavoured = floor(250/100*0 +
     * (250+40)) = 290, so "Durability: 270-290" -- a modifier genuinely applying, contrasted below
     * with "Maelstrom" (no durability-modifying property at all), whose "Durability: 250" must stay
     * exactly its own base value.
     */
    @Test
    public void steelgoadDurabilityAppliesItsOwnFlatDurabilityBonus() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lSteelgoad = findByDisplayName("Steelgoad");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lSteelgoad), null);

        assertTrue(lTooltip.contains("Durability: 270-290"), lTooltip);
    }

    @Test
    public void maelstromDurabilityHasNoModifierToApply() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lMaelstrom = findByDisplayName("Maelstrom");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lMaelstrom), null);

        assertTrue(lTooltip.contains("Durability: 250"), lTooltip);
    }

    /**
     * "Steeldriver" (Great Maul, base reqstr=99) has a real prop1 "ease" (min=max=-50, pNum 91,
     * "Requirements -#%"): D2Item.applyItemMods()'s own "-Req" arithmetic, {@code iReqStr +
     * (int)(iReqStr * percentReqirementsModifier)}, gives 99 + (int)(99*-0.5) = 99 + (int)(-49.5)
     * = 99 - 49 = 50 (Java's int cast truncates toward zero, exactly as D2Item's own cast does) --
     * a requirement genuinely reduced, not merely unchanged. Both flavours resolve identically
     * here (min1==max1==-50), so this renders as a single number, not a range -- proving the
     * modifier "applies" doesn't require the base-stat LINE itself to become a range, only that it
     * differ from the item's own unmodified base ("Required Strength: 99" would have been the
     * bare, unmodified weapons.txt value).
     */
    @Test
    public void steeldriverRequiredStrengthAppliesItsOwnRequirementReduction() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lSteeldriver = findByDisplayName("Steeldriver");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lSteeldriver), null);

        assertTrue(lTooltip.contains("Required Strength: 50"), lTooltip);
        assertFalse(lTooltip.contains("Required Strength: 99"),
                "must apply the item's own \"ease\" (-50% requirements), not the bare base: " + lTooltip);
    }

    /**
     * The rendered "Requirements" LINE itself, for both signs -- the counterpart to the two tests
     * above, which only pin the numeric Required Strength/Dexterity the line is supposed to explain.
     * item_req_percent (pNum 91) used to reach D2Prop's generic descfunc-19 branch, which renders by
     * substituting the raw value into properties.txt's fixed "*Tooltip" text -- and for "ease" that
     * text is "Requirements -#%", a literal minus owing nothing to the value. Both real items below
     * came out wrong in the running app, in opposite directions: Steeldriver's own -50 printed its
     * sign into the template's ("Requirements --50%"), while The Grandfather's +25/+50 was announced
     * as a reduction directly above its own correctly-computed, RAISED requirements. D2Prop now
     * routes pNum 91 to the signed-percent renderer (funcN 4 / dispLoc 2) so the displayed sign is
     * the value's own.
     * <p>
     * That both signs are real, rather than one being a data error to compensate for, is settled by
     * the game itself: ./d2111 uses both (49 negative "ease" rows, 40 positive), and a real save's
     * found "Sin and Greed" -- setitems.txt "ease" min=20 max=20 -- decodes its property from the
     * bitstream as +20, the table value verbatim. So positive genuinely means "requirements
     * increased", which is also how the mod's own site describes this item.
     */
    @Test
    public void requirementPercentLineFollowsTheValuesOwnSign() {
        D2TxtFile.constructTxtFiles("./d2111");

        String lSteeldriver = D2GrailListRenderer.tooltipFor(rowOf(findByDisplayName("Steeldriver")), null);
        assertTrue(lSteeldriver.contains("Requirements -50%"), lSteeldriver);
        assertFalse(lSteeldriver.contains("Requirements --50%"),
                "the template's literal minus must not stack onto an already-negative value: " + lSteeldriver);

        String lGrandfather = D2GrailListRenderer.tooltipFor(rowOf(findByDisplayName("The Grandfather")), null);
        assertTrue(lGrandfather.contains("Requirements +25-50%"), lGrandfather);
        assertFalse(lGrandfather.contains("Requirements -25"),
                "a positive value raises requirements and must not be shown as a reduction: " + lGrandfather);
    }

    /**
     * The Grandfather's raised requirements themselves, the arithmetic the line above explains:
     * Colossus Blade's base reqstr=189/reqdex=110 through D2Item.applyItemMods()'s own
     * {@code req + (int)(req * percent/100.0)} with its truncation toward zero --
     * 189 + (int)(47.25) = 236 and 189 + (int)(94.5) = 283; 110 + (int)(27.5) = 137 and
     * 110 + (int)(55.0) = 165.
     */
    @Test
    public void grandfatherRequirementsAreRaisedByItsPositiveEase() {
        D2TxtFile.constructTxtFiles("./d2111");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(findByDisplayName("The Grandfather")), null);

        assertTrue(lTooltip.contains("Required Strength: 236-283"), lTooltip);
        assertTrue(lTooltip.contains("Required Dexterity: 137-165"), lTooltip);
        assertFalse(lTooltip.contains("Required Strength: 189"),
                "must apply the item's own \"ease\", not the bare base: " + lTooltip);
    }

    // --------------------------------------------------------------------------------------
    // isRangeEligible's second exclusion: properties.txt func1=15/func2=16 ("min feeds one stat,
    // max feeds a DIFFERENT stat", never a range) vs. func1=1|21/func2=3 ("the same rolled value
    // copied to several stats", a genuine range -- see D2GrailListRenderer.isRangeEligible).
    // --------------------------------------------------------------------------------------

    /**
     * "Hand of Blessed Light"'s real prop7 "dmg-norm" (min=20, max=45) is properties.txt's own
     * func1=15/func2=16 shape (stat1=mindamage, stat2=maxdamage): NOT a randomly-rolled 20-45
     * bonus, but "+20 to Minimum Damage AND +45 to Maximum Damage, always both". Ranging it
     * (the bug this test pins the fix for) merged the min-flavoured pass's "Adds 20 - 20 Damage"
     * and the max-flavoured pass's "Adds 45 - 45 Damage" into doubled garbage, "Adds 20-45 - 20-45
     * Damage". Excluding it restores today's correct, pre-existing "Adds 20 - 45 Damage" (the
     * spaced " - ", D2Prop's own combined-damage rendering -- NOT this class's range-merge dash)
     * -- and, because dmgTriple[1]=20/dmgTriple[2]=45 now feed identically into BOTH flavours'
     * base-stat weapon-damage computation, also fixes "One Hand Damage" itself (previously
     * "56-86 to 107-143", wrongly spreading the flat 20..45 as a roll on both ends; correctly
     * "56-61 to 132-143" -- see D2GrailListRendererTest.missingWeaponUniqueTooltipShowsDamage for
     * the full by-hand arithmetic).
     */
    @Test
    public void dmgNormIsExcludedFromRangingBothThePropertyLineAndTheBaseDamage() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lHandOfBlessedLight = findByDisplayName("Hand of Blessed Light");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lHandOfBlessedLight), null);

        assertTrue(lTooltip.contains("Adds 20 - 45 Damage"), lTooltip);
        assertFalse(lTooltip.contains("Adds 20-45 - 20-45 Damage"),
                "dmg-norm's min/max are two DIFFERENT stats, never a range: " + lTooltip);
        assertTrue(lTooltip.contains("One Hand Damage: 56-61 to 132-143"), lTooltip);
        assertFalse(lTooltip.contains("One Hand Damage: 56-86 to 107-143"),
                "must not spread the flat dmg-norm min/max as if it were a roll range: " + lTooltip);
    }

    /**
     * "Sepia Shard"'s real prop2 "dmg-cold" (min=20, max=30, par=200 frames) is the SAME
     * func1=15/func2=16 family as dmg-norm (stat1=coldmindam, stat2=coldmaxdam), just for cold
     * damage instead of physical. Before the fix this "happened" to look right only by
     * coincidence -- each flavour's min-flavoured/max-flavoured D2Prop pair collapsed to the SAME
     * single value on its own side, so the top-level merge saw two identical fragments and
     * short-circuited -- but it too must go through the exclusion path (verified by the by-hand
     * base-damage math elsewhere; this test pins the property line itself): "Adds 20 - 30 Cold
     * Damage Over 8 Secs (200 Frames)", the spaced " - ", never "20-30" glued together as if this
     * class's own range-merge had produced it.
     */
    @Test
    public void dmgColdIsExcludedFromRanging() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lSepiaShard = findByDisplayName("Sepia Shard");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lSepiaShard), null);

        assertTrue(lTooltip.contains("Adds 20 - 30 Cold Damage Over 8 Secs (200 Frames)"), lTooltip);
        assertFalse(lTooltip.contains("Adds 20-30 Cold Damage"), lTooltip);
    }

    /**
     * The counterpart negative-space check: "The Chieftain"'s real prop4 "res-all" (min=10,
     * max=20) is properties.txt's func1=1/func2=3 family (the SAME rolled value copied to all
     * four resistance stats) -- a genuine range, and isRangeEligible's func1=15/func2=16 check
     * must NOT exclude it. Must still render "All Resistances +10-20", proving the new exclusion
     * is keyed on the exact func1/func2 pairing, not on some broader "any code with a stat1/stat2"
     * over-exclusion that would also catch this family.
     */
    @Test
    public void resAllStaysRangeEligible() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailEntry lTheChieftain = findByDisplayName("The Chieftain");
        String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lTheChieftain), null);

        assertTrue(lTooltip.contains("All Resistances +10-20"), lTooltip);
    }

    // --------------------------------------------------------------------------------------
    // Helpers (matching D2GrailListRendererTest's own conventions)
    // --------------------------------------------------------------------------------------

    private static D2GrailModel.Row rowOf(D2GrailEntry pEntry) {
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
