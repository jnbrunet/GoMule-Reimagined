package randall.d2files;

import gomule.item.D2Prop;
import gomule.item.D2PropCollection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2TxtFile.propToStat()'s handling of properties.txt's two "cast a skill" shapes: the six
 * skill-on-event codes ("att-skill"/"hit-skill"/"gethit-skill"/"kill-skill"/"death-skill"/
 * "levelup-skill", uiRangeType 7) and the charge-granting "charged" (uiRangeType 6). Neither
 * stores the plain (min, max, par) triple the generic path assumes:
 *
 * <pre>
 *   code        par      min                                   max
 *   *-skill     Skill    % Chance (If 0, then default to 5)    Skill Level
 *   charged     Skill    # of Max Charges                      Skill Level
 * </pre>
 *
 * Before this fix both rendered NOTHING at all in a missing item's grail tooltip, for two
 * independent reasons: "par" holds a skill NAME in every uniqueitems.txt/setitems.txt/sets.txt/
 * runes.txt row that uses them (only magicsuffix.txt uses raw numeric ids), which propToStat's
 * pParam int-parse rejected by returning an empty list; and even with a numeric param, the generic
 * path zeroed pVals[2] and never filled pVals[1], while D2Prop's renderers for these stats
 * (descfunc 15 and 24) expect the [skill level, skill id, chance] / [skill level, skill id,
 * charges, max charges] layout that D2PropCollection.readProp() builds from a real item's
 * bitstream.
 * <p>
 * The reported symptom, and this suite's headline case: Schaefer's Hammer (uniqueitems.txt *ID
 * 257, code 7wh) has prop1 "hit-skill" par="Static Field" min=10 max=10, i.e. "10% Chance to cast
 * level 10 Static Field on striking" on the mod's own item page -- the one line the grail tooltip
 * was missing versus that page, every other line already matching.
 * <p>
 * propToStat is shared production code (D2Item.addSetProperties, the item-load path, calls it with
 * no try/catch), so an unresolvable skill must degrade to "no prop" rather than throw -- covered
 * below.
 */
public class D2PropToStatSkillEventTest {

    /**
     * The headline case, at the propToStat level: a skill NAME param resolves, and the three pVals
     * land in D2Prop's own descfunc-15 order -- [skill level, skill id, % chance] -- which is what
     * makes the rendered line read "10% Chance to cast level 10 Static Field on striking" rather
     * than transposing the chance and the level (both happen to be 10 on Schaefer's Hammer, so the
     * pVals are asserted directly against a skill id that could not come out right by accident:
     * skills.txt's "Static Field" is *Id 42).
     */
    @Test
    public void hitSkillWithASkillNameParamResolvesAndRenders() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("hit-skill", "10", "10", "Static Field", 0);

        assertEquals(1, stats.size(), "hit-skill must now resolve to exactly one prop: " + stats);
        D2Prop prop = (D2Prop) stats.get(0);
        assertEquals(198, prop.getPNum(), "item_skillonhit is itemstatcost.txt *ID 198");
        assertEquals(10, prop.getPVals()[0], "pVals[0] is the skill LEVEL (the max column)");
        assertEquals(42, prop.getPVals()[1], "pVals[1] is the skill id -- skills.txt Static Field is 42");
        assertEquals(10, prop.getPVals()[2], "pVals[2] is the % CHANCE (the min column)");

        assertTrue(render(stats).contains("10% Chance to cast level 10 Static Field on striking"),
                "expected Schaefer's Hammer's real line: " + render(stats));
    }

    /**
     * The chance and the level are genuinely different columns, not two ends of one range, so a row
     * where they differ must not swap them -- and a row where the chance happens to be numerically
     * LARGER than the level must not be "corrected" into a backwards range either. Real row:
     * uniqueitems.txt's "Fallen Hero's Disgrace" (*ID 1213) prop8 "death-skill" par="Hailstorm"
     * min=100 ("% Chance") max=15 ("Skill Level"), the row this project's own uiRangeType note was
     * originally raised against.
     */
    @Test
    public void chanceAndLevelAreNeverTransposed() {
        D2TxtFile.constructTxtFiles("./d2111");
        String rendered = render(D2TxtFile.propToStat("death-skill", "100", "15", "Hailstorm", 0));
        assertTrue(rendered.contains("100% Chance to cast level 15 Hailstorm when you Die"),
                "chance=100 (min) and level=15 (max) must stay in their own slots: " + rendered);
    }

    /**
     * magicsuffix.txt spells the same "par" column as a raw numeric skill id rather than a name
     * (e.g. "42" for Static Field), so both spellings have to resolve to the same prop.
     */
    @Test
    public void numericSkillIdParamResolvesIdenticallyToTheName() {
        D2TxtFile.constructTxtFiles("./d2111");
        String fromName = render(D2TxtFile.propToStat("hit-skill", "10", "10", "Static Field", 0));
        String fromId = render(D2TxtFile.propToStat("hit-skill", "10", "10", "42", 0));
        assertEquals(fromName, fromId, "a numeric skill id must render exactly as the skill's name does");
    }

    /**
     * properties.txt's own "*Min" note for all six skill-on-event codes is "% Chance (If 0, then
     * default to 5)" -- so a 0 (or blank) min column means a 5% chance, never a "0% Chance to
     * cast", which would read as an effect that can never fire.
     */
    @Test
    public void zeroChanceDefaultsToFivePercent() {
        D2TxtFile.constructTxtFiles("./d2111");
        assertTrue(render(D2TxtFile.propToStat("hit-skill", "0", "10", "Static Field", 0))
                        .contains("5% Chance to cast level 10 Static Field on striking"),
                "a 0 chance column must default to 5%");
        assertTrue(render(D2TxtFile.propToStat("hit-skill", "", "10", "Static Field", 0))
                        .contains("5% Chance to cast level 10 Static Field on striking"),
                "a blank chance column must default to 5% too");
    }

    /**
     * "charged" (item_charged_skill, *ID 204, descfunc 24) shares the skill-name param problem but
     * has its own four-slot layout: [skill level, skill id, charges, max charges]. Its "min" column
     * is the max-charge count, and an item nobody has found yet is shown at full charges, so that
     * one number fills both. Real row: uniqueitems.txt's "Spellsteel" prop9 charged par="Holy Bolt"
     * min=100 max=10 -> "Level 10 Holy Bolt (100/100 Charges)".
     * <p>
     * This also covers D2Prop's descfunc-24 renderer itself, which previously filled the
     * "ModStre10d" template ("Level %d %s (%d/%d Charges)") with the charge count in the LEVEL slot
     * and then prefixed its own "Level n skill " in front of the half-substituted remainder --
     * "Level 10 Holy Bolt Level 100 %s (100/100 Charges)". That mangling hit real, found charged
     * items too, not just this table-sourced path.
     */
    @Test
    public void chargedResolvesToAFullyChargedSkill() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("charged", "100", "10", "Holy Bolt", 0);

        assertEquals(1, stats.size(), "charged must now resolve to exactly one prop: " + stats);
        assertEquals(204, ((D2Prop) stats.get(0)).getPNum(), "item_charged_skill is itemstatcost.txt *ID 204");

        String rendered = render(stats);
        assertTrue(rendered.contains("Level 10 Holy Bolt (100/100 Charges)"), rendered);
        assertFalse(rendered.contains("%s"), "the template must be fully substituted: " + rendered);
        assertFalse(rendered.contains("%d"), "the template must be fully substituted: " + rendered);
    }

    /**
     * A "par" naming no skill at all must drop just that one property line -- never throw, since
     * D2Item.addSetProperties calls propToStat unguarded and an exception there would abort loading
     * the whole item rather than blanking one tooltip line.
     */
    @Test
    public void unresolvableSkillDropsOnlyThatPropRatherThanThrowing() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("hit-skill", "10", "10", "No Such Skill At All", 0);
        assertFalse(stats == null, "must return an empty list, not null");
        assertTrue(stats.isEmpty(), "an unresolvable skill must resolve to no props: " + stats);
    }

    /**
     * The rescue is scoped to the seven codes above and must not leak into the OTHER properties.txt
     * shape that also carries a skill in "par": "oskill_hide" (item_nonclassskill), whose
     * non-numeric param still degrades to "nothing added" exactly as before -- it is an internal
     * backend grant the game never shows, and D2PropCollection has its own dedicated suppression
     * for it (isHiddenSkillGrant). Real row: Schaefer's Hammer's own last property is
     * oskill_hide par="Hidden Charm Passive", which must stay invisible.
     */
    @Test
    public void oskillHideIsUnaffected() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("oskill_hide", "1", "1", "Hidden Charm Passive", 0);
        assertTrue(stats.isEmpty(), "oskill_hide must keep resolving to no props: " + stats);
    }

    private static String render(ArrayList stats) {
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        collection.applyOp(1);
        return collection.generateDisplay(0, 1).toString();
    }
}
