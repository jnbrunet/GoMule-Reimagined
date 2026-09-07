package randall.d2files;

import gomule.item.D2Prop;
import gomule.item.D2PropCollection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2TxtFile.propToStat()'s func-implied-stat fix: a handful of properties.txt codes ("dmg-min",
 * "dmg-max", "dmg%", "indestruct", "ethereal") carry no "stat1" column at all -- the stat is
 * implied by the "func" column instead, a modding convention the data itself uses rather than
 * naming the stat directly. Before this fix, only "dmg%" was ever rescued (a single hardcoded
 * special case); "dmg-min", "dmg-max" and "indestruct" silently produced zero D2Props -- and
 * therefore no tooltip/property line at all -- for every item that used them. Real example this
 * restores: uniqueitems.txt's "Wrath of the Seraphim" (*ID 618) has prop2="dmg-max" min=100
 * max=200 ("+100-200 to Maximum Weapon Damage" on the mod's own site), which GoMule rendered
 * nothing for at all.
 * <p>
 * Keying choice: the mapping is keyed on the literal properties.txt <b>code</b> (dmg-min,
 * dmg-max, indestruct), not on the numeric "func" value, matching the style of the single
 * pre-existing "dmg%" rescue this replaces. Verified this is exactly as safe as keying on func
 * would be here: within properties.txt's rows with an empty stat1, each of func 5 (dmg-min),
 * func 6 (dmg-max), func 7 (dmg%) and func 20 (indestruct) is used by exactly one code, so there
 * is no code-vs-func ambiguity to trade away -- and keying on the code avoids an extra column
 * lookup and int parse that keying on func would need.
 * <p>
 * "ethereal" (func 23, also stat1-empty) is deliberately left unrescued: there is no
 * itemstatcost.txt stat for it at all (searched for, confirmed absent) -- ethereal is a plain
 * item flag, not a stat with a value, so there is nothing to synthesize a D2Prop from. A found
 * item's real "Ethereal" line comes from D2Item.isEthereal() (a bitstream flag) via
 * D2ItemRenderer's own dedicated rendering, entirely separate from this property-list pipeline.
 * <p>
 * propToStat is shared production code; D2Item.addSetProperties (the item-load path) calls it
 * with no try/catch at all, so an unresolvable code (one properties.txt has no row for whatsoever,
 * e.g. a mod build newer than the loaded ./d2111 tables) must degrade to "no props", never throw
 * -- an uncaught NPE there would abort loading the whole item, not just blank one tooltip line.
 */
public class D2PropToStatFuncImpliedStatTest {

    /**
     * setitems.txt/uniqueitems.txt real example: "mindamage" is itemstatcost.txt *ID 21.
     */
    @Test
    public void dmgMinResolvesToMinDamage() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg-min", "50", "60", "", 0);
        assertEquals(1, stats.size(), "dmg-min must resolve to exactly one prop now, got: " + stats);
        D2Prop prop = (D2Prop) stats.get(0);
        assertEquals(21, prop.getPNum(), "mindamage is itemstatcost.txt *ID 21");

        String rendered = render(stats, 0, 1);
        assertTrue(rendered.contains("+50 to Minimum Damage"), "expected the min value, got: " + rendered);
    }

    /**
     * Wrath of the Seraphim (*ID 618): prop2="dmg-max" min=100 max=200. "maxdamage" is
     * itemstatcost.txt *ID 22. Note this also exercises the pre-existing, untouched "max"
     * value-selection branch (propsStatCode.indexOf("max") != -1 -> use pMax): "maxdamage"
     * contains "max", so the rendered line takes the single max value (200), not a 100-200
     * range -- that collapsing behaviour is not part of this fix and is pinned here exactly as
     * observed, not as originally hoped for on the mod site.
     */
    @Test
    public void dmgMaxResolvesToMaxDamage() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg-max", "100", "200", "", 0);
        assertEquals(1, stats.size(), "dmg-max must resolve to exactly one prop now, got: " + stats);
        D2Prop prop = (D2Prop) stats.get(0);
        assertEquals(22, prop.getPNum(), "maxdamage is itemstatcost.txt *ID 22");

        String rendered = render(stats, 0, 1);
        assertTrue(rendered.contains("+200 to Maximum Damage"),
                "expected the pre-existing \"max\" branch to select pMax (200), not a 100-200 range: " + rendered);
    }

    /**
     * indestruct (func 20) -> "item_indesctructible" (itemstatcost.txt's own misspelling, *ID
     * 152) -- matched exactly, not "corrected", since that is the literal stat name in the data.
     */
    @Test
    public void indestructResolvesToItemIndesctructible() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("indestruct", "1", "1", "", 0);
        assertEquals(1, stats.size(), "indestruct must resolve to exactly one prop now, got: " + stats);
        D2Prop prop = (D2Prop) stats.get(0);
        assertEquals(152, prop.getPNum(), "item_indesctructible is itemstatcost.txt *ID 152");

        String rendered = render(stats, 0, 1);
        assertTrue(rendered.contains("Indestructible"), "expected an Indestructible line, got: " + rendered);
    }

    /**
     * Regression: "dmg%"'s pre-existing rescue (the only one that existed before this fix) must
     * still behave identically -- 300/400 still renders "+400% Enhanced Damage", the exact
     * property from Wrath of the Seraphim's own prop1.
     */
    @Test
    public void dmgPercentIsUnchanged() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg%", "300", "400", "", 0);
        assertEquals(1, stats.size());
        D2Prop prop = (D2Prop) stats.get(0);
        assertEquals(17, prop.getPNum(), "item_maxdamage_percent is itemstatcost.txt *ID 17");

        String rendered = render(stats, 0, 1);
        assertTrue(rendered.contains("+400% Enhanced Damage"), "dmg% must be unchanged, got: " + rendered);
    }

    /**
     * Regression: a code with a real (non-empty) stat1/stat2 column, unrelated to the
     * func-implied-stat rescue entirely, must be unaffected by it. "dmg-norm" resolves straight
     * from properties.txt's own stat1="mindamage"/stat2="maxdamage" columns, no rescue involved.
     */
    @Test
    public void codeWithRealStat1IsUnchanged() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg-norm", "20", "45", "", 0);
        assertEquals(2, stats.size(), "dmg-norm has two real stat columns (mindamage, maxdamage): " + stats);
        String rendered = render(stats, 0, 1);
        assertTrue(rendered.contains("20") && rendered.contains("45"), "expected the min-max range unchanged: " + rendered);
    }

    /**
     * Regression: another real-stat1 code ("lifesteal") is unaffected by the rescue mapping.
     */
    @Test
    public void lifestealIsUnchanged() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("lifesteal", "8", "8", "", 0);
        assertEquals(1, stats.size());
        String rendered = render(stats, 0, 1);
        assertTrue(rendered.contains("8% Life stolen per hit"), "lifesteal must be unchanged, got: " + rendered);
    }

    /**
     * "ethereal" (func 23, also stat1-empty) is documented, pinned behaviour: it must keep
     * producing nothing, not have a stat invented for it by a future "fix". D2Item.isEthereal()'s
     * own dedicated bitstream-flag rendering is the real source of the "Ethereal" line for found
     * items; this pipeline has no flag to synthesize one from for a missing entry.
     */
    @Test
    public void etherealProducesNothing() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("ethereal", "1", "1", "", 0);
        assertTrue(stats.isEmpty(), "ethereal has no itemstatcost.txt stat and must stay unrescued: " + stats);
    }

    /**
     * The null-guard this round added: a code properties.txt has no row for at all (e.g. one from
     * a mod build newer than the loaded ./d2111 tables) must degrade to "no props", never throw --
     * D2Item.addSetProperties calls propToStat with no try/catch, so an uncaught NPE here would
     * abort loading the whole item, not merely blank one tooltip line.
     */
    @Test
    public void unknownCodeReturnsEmptyListRatherThanThrowing() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("totally-unknown-code", "1", "1", "", 0);
        assertFalse(stats == null, "must return an empty list, not null");
        assertTrue(stats.isEmpty(), "an unknown code must resolve to no props: " + stats);
    }

    private static String render(ArrayList stats, int qFlag, int cLvl) {
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        collection.applyOp(cLvl);
        return collection.generateDisplay(qFlag, cLvl).toString();
    }
}
