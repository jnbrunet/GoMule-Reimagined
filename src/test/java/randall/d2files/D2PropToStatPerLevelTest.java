package randall.d2files;

import com.google.common.io.Resources;
import gomule.d2s.D2Character;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import gomule.item.D2PropCollection;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2TxtFile.propToStat()'s per-level fix: a per-level property (properties.txt code ending in
 * "/lvl", resolving to an itemstatcost.txt "item_*_perlevel" stat) whose min/max are both blank
 * stores its fixed value in "par" instead -- before this fix, propToStat unconditionally zeroed
 * "par" out again two lines later unless the resolved stat name contained "max" or "length", so
 * every one of these silently rendered as "+0" regardless of table data or character level.
 * <p>
 * propToStat is shared production code with four real callers: D2Item.addSetProperties (the set
 * partial/full-set bonuses), D2Item.java's gem property reading, and D2GrailListRenderer's two
 * missing-item tooltip call sites (base properties, and the set-bonus sections). This fix only
 * changes propToStat itself, so all four inherit it identically; the found-item path
 * (D2Item.addSetProperties -> D2ItemRenderer.getItemPropertyString) additionally already calls
 * D2PropCollection.applyOp() once on every real item's whole property collection
 * (D2Item.applyItemMods(), unconditionally, right after every property is read) -- which is the
 * method that actually does the character-level multiplication (D2Prop.generateDisplay's own cLvl
 * parameter is never used for this; only applyOp() is), so the found path needed no other change.
 * D2GrailListRenderer has no equivalent construction step and calls applyOp() itself explicitly.
 */
public class D2PropToStatPerLevelTest {

    /**
     * The exact verification the fix's correctness rests on: a real per-level property, read two
     * different ways at the same character level, must produce byte-for-byte the same text.
     * <p>
     * charFiles/pally9.d2s (a level-85 character) carries a real "Cleglaw's Pincers" (Cleglaw's
     * Brace), whose "2 items" bonus is read straight from the save's bitstream and rendered as
     * "+850 to Attack Rating (Based on Character Level)". setitems.txt's own "Cleglaw's Pincers"
     * row defines that exact bonus as aprop1a="att/lvl", amin1a/amax1a blank, apar1a="20" -- so
     * feeding propToStat("att/lvl", "", "", "20", 0) through the same
     * tidy()/applyOp(85)/generateDisplay(0, 85) pipeline D2GrailListRenderer uses must produce the
     * identical string. (20 * 85 / 2 = 850 -- item_tohit_perlevel's itemstatcost.txt divisor is
     * 2, not the "eighths" most other per-level stats use, which is exactly why this had to be
     * verified against a real value rather than assumed.)
     */
    @Test
    public void perLevelPropertyMatchesTheRealFoundItemExactly() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        File file = new File(Resources.getResource("charFiles/pally9.d2s").toURI());
        D2Character character = new D2Character(file.getAbsolutePath());
        int charLevel = character.getCharLevel();
        assertEquals(85, charLevel, "this test's expected numbers are pinned to level 85");

        D2Item cleglaws = findItemNamed(character.getItemList(), "Cleglaw's Pincers");
        String foundDump = D2ItemRenderer.itemDump(cleglaws, false);
        String expectedLine = "+850 to Attack Rating (Based on Character Level)";
        assertTrue(foundDump.contains(expectedLine),
                "sanity check on the fixture itself failed -- expected to find '" + expectedLine
                        + "' in the real found item's own dump: " + foundDump);

        // setitems.txt "Cleglaw's Pincers": aprop1a="att/lvl" amin1a="" amax1a="" apar1a="20"
        ArrayList stats = D2TxtFile.propToStat("att/lvl", "", "", "20", 0);
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        collection.applyOp(charLevel);
        String tableSourced = collection.generateDisplay(0, charLevel).toString();

        assertTrue(tableSourced.contains(expectedLine),
                "table-sourced path must match the real found item exactly, got: " + tableSourced);
    }

    /**
     * The ordering regression this class exists to pin: a per-level stat whose *name* happens to
     * contain the substring "max" must still be recognized as per-level, not swallowed by the
     * "max" branch first. Real data: setitems.txt's "Civerb's Cudgel" (Civerb's Vestments) has
     * aprop1a="dmg/lvl" amin1a="" amax1a="" apar1a="12"; properties.txt resolves "dmg/lvl" to
     * stat1="item_maxdamage_perlevel" (itemstatcost.txt: op=4, op base=level, op param=3, i.e.
     * divisor 8) -- "max" sits right there in the stat name. Before reordering the per-level check
     * ahead of the "max" branch, this rendered "+0 to Maximum Damage" (the "max" branch fired
     * first and took the blank amax1a); expected now, at the tooltip's assumed level 99:
     * 12 * 99 / 8 = floor(148.5) = 148.
     */
    @Test
    public void perLevelStatWhoseNameContainsMaxIsStillTreatedAsPerLevel() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg/lvl", "", "", "12", 0);
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        collection.applyOp(99);
        String rendered = collection.generateDisplay(0, 99).toString();
        assertTrue(rendered.contains("+148 to Maximum Damage (Based on Character Level)"),
                "expected 12 * 99 / 8 = 148, got: " + rendered);
    }

    /**
     * The existing "max" handling (propsStatCode.indexOf("max") != -1 -> use pMax instead of
     * pMin) must still fire exactly as before -- "dmg%"'s own forced stat1 override
     * ("item_maxdamage_percent", the pCode.equals("dmg%") special case already in propToStat)
     * is the simplest real example: passing min=10/max=20 must render the MAX value (20), not 10.
     */
    @Test
    public void existingMaxHandlingIsUnchanged() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg%", "10", "20", "", 0);
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        String rendered = collection.generateDisplay(0, 1).toString();
        assertTrue(rendered.contains("20"), "expected the MAX value (20), got: " + rendered);
        assertFalse(rendered.contains(">10<") || rendered.startsWith("10"), "did not expect the min value: " + rendered);
    }

    /**
     * The existing "length" handling (propsStatCode.indexOf("length") != -1 -> use pParam as the
     * value) must still fire exactly as before. "dmg-cold" resolves to three stats
     * (coldmindam, coldmaxdam, coldlength); its "length" stat is exactly the shape a per-level fix
     * could accidentally interfere with (both branches read pVals[2]/par), so this pins the real,
     * already-verified value from D2GrailIndexTest/D2GrailScannerTest's "Immortal King's Stone
     * Crusher" (setitems.txt aprop3a="dmg-cold" apar3a="150" amin3a="250" amax3a="500" -> "Adds
     * 250 - 500 Cold Damage Over 6 Secs (150 Frames)").
     */
    @Test
    public void existingLengthHandlingIsUnchanged() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("dmg-cold", "250", "500", "150", 0);
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        String rendered = collection.generateDisplay(0, 1).toString();
        assertTrue(rendered.contains("250") && rendered.contains("500"), "expected the min-max damage range: " + rendered);
        assertTrue(rendered.contains("150 Frames"), "expected the length/duration (par=150) to still use the "
                + "\"length\" branch, not be zeroed or rerouted by the new per-level branch: " + rendered);
    }

    /**
     * "oskill_hide" (and "hit-skill") carry a skill NAME in "par", not a number -- propToStat's
     * existing pParam parse already returns early (nothing added) the moment that fails to parse
     * as an integer, for every property, per-level or not. This must keep degrading to "nothing
     * added, no exception" rather than the new per-level branch (which only ever looks at pVals[2]
     * after a successful parse) being reachable with a non-numeric value at all.
     */
    @Test
    public void nonNumericParamPropertyIsUnaffected() {
        D2TxtFile.constructTxtFiles("./d2111");
        ArrayList stats = D2TxtFile.propToStat("oskill_hide", "1", "1", "Hidden Charm Passive", 0);
        D2PropCollection collection = new D2PropCollection();
        //noinspection unchecked
        collection.addAll(stats);
        collection.tidy();
        String rendered = collection.generateDisplay(0, 99).toString();
        assertFalse(rendered.contains("Based on Character Level"),
                "a non-numeric-param property must never be mistaken for a per-level one: " + rendered);
    }

    private static D2Item findItemNamed(List<D2Item> pItems, String pName) {
        for (D2Item lItem : pItems) {
            if (pName.equals(lItem.getItemName())) {
                return lItem;
            }
        }
        throw new AssertionError(pName + " not found in the fixture's item list");
    }
}
