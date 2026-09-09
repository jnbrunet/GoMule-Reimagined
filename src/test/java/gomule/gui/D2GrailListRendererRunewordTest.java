package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailIndex;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The missing-item tooltip for a RUNEWORD (D2GrailListRenderer.appendMissingRuneword). Before it
 * existed, hovering a runeword the player had not made showed nothing but its name: a runeword's
 * source row is a runes.txt row, which carries none of the prop1..N/par/min/max columns the
 * unique/set path reads, and no single base item for the base-stat lines either -- so every column
 * that path asked for came back empty.
 * <p>
 * What the tooltip has to show, taken from the mod's own item page for "Bulwark" (the reference
 * screenshot this was built against): the bases it can be made in, the rune sequence with each
 * rune's number, the level it needs, and the full bonus list -- which is the runeword's OWN
 * properties (runes.txt T1Code1..T1Code7) PLUS each rune's gems.txt bonus for the kind of base it
 * is socketed into, exactly as the game adds them.
 * <p>
 * Headless: like the other tests in this package, no Swing class or D2FileManager is touched --
 * tooltipFor() is pure data work over the .txt tables.
 */
public class D2GrailListRendererRunewordTest {

    /**
     * The reference case, line by line against the mod's own "Bulwark" page. Shael + Io + Sol in a
     * helm: five of its lines come from its own runes.txt row (hp% 5, ac% 75-100, red-dmg% 10-15,
     * regen 30, lifesteal 4-6), and three come from nowhere in that row at all -- "+20% Faster Hit
     * Recovery", "+10 to Vitality" and "Damage Reduced by 7" are the helmMod entries of Shael, Io
     * and Sol respectively, which is the whole reason the rune contributions have to be added here
     * rather than the T1Code columns being rendered alone.
     */
    @Test
    public void bulwarkShowsItsBasesRunesLevelAndFullBonusList() {
        D2TxtFile.constructTxtFiles("./d2111");
        String lTooltip = tooltipOf("Bulwark");

        assertNotNull(lTooltip);
        // The base it can be made in. itemtypes.txt labels the "helm" type "Merc Equip" (a note
        // about who can wear it); the tooltip overrides that one label to the slot's real name.
        assertTrue(lTooltip.contains("Helm"), lTooltip);
        assertFalse(lTooltip.contains("Merc Equip"), lTooltip);

        // The rune sequence, with the numbers the mod's own rune strings already carry -- and
        // without their colour markup or the second "~Pick Up~" line the high runes add.
        assertTrue(lTooltip.contains("Shael Rune (#13) + Io Rune (#16) + Sol Rune (#12)"), lTooltip);
        assertFalse(lTooltip.contains("ÿc"), "colour codes must be stripped: " + lTooltip);

        // Highest rune level requirement: Shael 29, Io 35, Sol 27.
        assertTrue(lTooltip.contains("Required Level: 35"), lTooltip);

        // The runeword's own properties.
        assertTrue(lTooltip.contains("+75-100% Enhanced Defense"), lTooltip);
        assertTrue(lTooltip.contains("4-6% Life stolen per hit"), lTooltip);
        assertTrue(lTooltip.contains("Increase Maximum Life 5%"), lTooltip);
        assertTrue(lTooltip.contains("Damage Reduced by 10-15%"), lTooltip);

        // The three lines that exist only because the runes themselves are in a helm.
        assertTrue(lTooltip.contains("+20% Faster Hit Recovery"), "Shael's helmMod: " + lTooltip);
        assertTrue(lTooltip.contains("+10 to Vitality"), "Io's helmMod: " + lTooltip);
        assertTrue(lTooltip.contains("Damage Reduced by 7"), "Sol's helmMod: " + lTooltip);
    }

    /**
     * "Insight" is the case that proves the skill-granting properties are resolved, not skipped:
     * its whole reason to exist is its Meditation aura (T1Code6 "aura" par="Meditation" 12-17), and
     * "par" there is a skill NAME, which propToStat used to reject outright -- so the aura, and its
     * "oskill" Critical Strike, were both simply absent. Also covers the several-allowed-bases,
     * single-kind case: polearm/spear/staff/missile are all weapons, so the runes' weapon bonuses
     * merge straight into the one list rather than being split into labelled blocks.
     */
    @Test
    public void insightShowsItsAuraAndGrantedSkill() {
        D2TxtFile.constructTxtFiles("./d2111");
        String lTooltip = tooltipOf("Insight");

        assertTrue(lTooltip.contains("Level 12-17 Meditation Aura When Equipped"), lTooltip);
        assertTrue(lTooltip.contains("to Critical Strike"), lTooltip);
        assertTrue(lTooltip.contains("Polearm / Spear / Staff / Missile Weapon"), lTooltip);
        // All four bases are weapons, so there is one merged list and no per-kind block.
        assertFalse(lTooltip.contains("In a Weapon:"), lTooltip);
        // Sol's weaponMod (+9 minimum damage) is merged in.
        assertTrue(lTooltip.contains("+9 to Minimum Damage"), lTooltip);
    }

    /**
     * "Spirit" is one of the 17 runewords whose allowed bases span more than one KIND (swords or
     * shields), where the runes contribute completely different bonuses to each -- so a single
     * merged list would be right for at most one of them. Its own properties print once, then one
     * labelled block per kind.
     */
    @Test
    public void spiritSplitsItsRuneBonusesPerKindOfBase() {
        D2TxtFile.constructTxtFiles("./d2111");
        String lTooltip = tooltipOf("Spirit");

        assertTrue(lTooltip.contains("Sword / Any Shield"), lTooltip);
        // Its own properties, shared by both.
        assertTrue(lTooltip.contains("+2 to All Skills"), lTooltip);
        // Ort in a sword adds lightning damage; Ort in a shield adds lightning resistance.
        assertTrue(lTooltip.contains("In a Weapon:"), lTooltip);
        assertTrue(lTooltip.contains("Adds 1 - 50 Lightning Damage"), lTooltip);
        assertTrue(lTooltip.contains("In a Shield:"), lTooltip);
        assertTrue(lTooltip.contains("Lightning Resist +35%"), lTooltip);
    }

    /**
     * "Exile" pins three separate things that were each broken in their own way:
     * <ul>
     *   <li>its base type is the class-item umbrella "pala", which names no kind of gear itself
     *   (its ancestry stops at the abstract "clas") and has to be resolved through its one child,
     *   Auric Shields -- otherwise its runes contribute nothing;</li>
     *   <li>its T1Code5 "skilltab" par=10 is a table-numbered skill tab (0-23, three per class in
     *   class order), NOT the "class * 8 + tab" index stat 188 stores and D2Prop.getSkillTree
     *   decodes; unconverted it read as "+2 to Cold Skills (Sorceress Only)" on a Paladin
     *   runeword;</li>
     *   <li>its T1Code7 "rep-dur" keeps its value in "par" (25) with blank min/max, which resolved
     *   to 0 and made D2Prop's "Repairs 1 Durability in 100/value Seconds" renderer divide by zero
     *   -- throwing out of generateDisplay and blanking this entire tooltip down to just the name.
     *   </li>
     * </ul>
     */
    @Test
    public void exileResolvesItsClassBaseSkillTabAndRepairRate() {
        D2TxtFile.constructTxtFiles("./d2111");
        String lTooltip = tooltipOf("Exile");

        assertTrue(lTooltip.contains("+2 to Offensive Aura Skills (Paladin Only)"), lTooltip);
        assertFalse(lTooltip.contains("Sorceress Only"), lTooltip);
        assertTrue(lTooltip.contains("Repairs 1 Durability in 4 Seconds"), lTooltip);
        assertTrue(lTooltip.contains("Level 13-16 Defiance Aura When Equipped"), lTooltip);
        // Vex/Ohm/Ist/Dol in an auric shield: Dol's shieldMod is "Replenish Life +7".
        assertTrue(lTooltip.contains("Replenish Life +7"), lTooltip);
    }

    /**
     * Every runeword in the index, not just the hand-picked ones: each must produce a real tooltip
     * -- no exception (tooltipFor swallows one into a bare name, which is exactly the symptom this
     * whole change fixes), no unsubstituted format placeholder, no leaked colour markup, and always
     * the four structural pieces. This is what catches a mod update adding a runeword whose base
     * type or property shape none of the cases above happens to cover.
     */
    @Test
    public void everyRunewordRendersACompleteTooltip() {
        D2TxtFile.constructTxtFiles("./d2111");
        int lChecked = 0;
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() != D2GrailKey.Type.RUNEWORD) {
                continue;
            }
            lChecked++;
            String lTooltip = D2GrailListRenderer.tooltipFor(rowOf(lEntry), null);
            String lName = lEntry.getDisplayName();
            assertNotNull(lTooltip, lName);
            assertTrue(lTooltip.contains("Rune"), lName + " must list its runes: " + lTooltip);
            // Either the rune-derived line ("Required Level: 35") or, for the handful of words
            // whose runes have no level requirement of their own, the runes.txt "levelreq" property
            // rendering itself as "Required Level +26" -- "Law" is Hel + Hel, both levelreq 0.
            assertTrue(lTooltip.contains("Required Level"), lName + " must state its level: " + lTooltip);
            assertFalse(lTooltip.contains("%s"), lName + " has an unsubstituted %s: " + lTooltip);
            assertFalse(lTooltip.contains("%d"), lName + " has an unsubstituted %d: " + lTooltip);
            assertFalse(lTooltip.contains("ÿc"), lName + " leaked colour markup: " + lTooltip);
            assertFalse(lTooltip.contains("<font color=\"#4850b8\"></font>"),
                    lName + " rendered no properties at all: " + lTooltip);
        }
        assertEquals(208, lChecked, "every complete runeword in ./d2111");
    }

    private static String tooltipOf(String pDisplayName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() == D2GrailKey.Type.RUNEWORD
                    && pDisplayName.equals(lEntry.getDisplayName())) {
                return D2GrailListRenderer.tooltipFor(rowOf(lEntry), null);
            }
        }
        throw new AssertionError(pDisplayName + " not found among the indexed runewords");
    }

    private static D2GrailModel.Row rowOf(D2GrailEntry pEntry) {
        D2GrailModel lModel = new D2GrailModel(Collections.singletonList(pEntry));
        lModel.setTab(pEntry.getKey().getType());
        lModel.setIncludeNonChronicle(true);
        for (D2GrailModel.Row lRow : lModel.getRows()) {
            if (lRow.getEntry() == pEntry) {
                return lRow;
            }
        }
        throw new AssertionError("row not found for " + pEntry.getKey());
    }
}
