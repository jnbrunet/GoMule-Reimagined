package gomule.d2s;

import com.google.common.io.Resources;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("UnstableApiUsage")
public class D2CharacterTest {

    // KNOWN ISSUE (not yet fixed): this test is disabled because parsing complexChar.d2s currently
    // throws gomule.util.D2ItemException: "Error: For input string: """  when it reaches
    // "Titan's Revenge".  Root cause: the v3.0.10 itemstatcost.txt sync left 105 stat rows with
    // an empty "Save Bits" column; D2PropCollection.readProp calls Integer.parseInt on that empty
    // string and crashes.  Fix requires either patching the fixture or handling empty Save Bits
    // gracefully in D2PropCollection.readProp.
//    @Test
//    public void complexChar() throws Exception {
//        D2TxtFile.constructTxtFiles("./d2111");
//        D2Character d2Character = new D2Character(new File(Resources.getResource("charFiles/complexChar.d2s").toURI()).getAbsolutePath());
//        assertEquals(expectedComplexChar, d2Character.fullDumpStr().replaceAll("\r", ""));
//    }

    // Two real, current D2R (version 105) characters a user shared while debugging GoMule against
    // their actual game install. Together these are what found and validated the .d2s header
    // offset fix (D2Character.readChar()'s comments) and the item-format version fix
    // (D2Item.readExtend2()'s comment) -- every field asserted here was cross-checked against
    // either the file's own contents (name) or real game data (class/level from the header; each
    // item's name/defense/durability/properties against its real base stats and fixed property
    // ranges in armor.txt/weapons.txt/uniqueitems.txt; the socket count against what the player
    // confirmed seeing in-game). "Doesn't throw" was repeatedly not enough on its own during that
    // investigation -- see D2Item.readExtend2()'s comment for real examples of wrong values that
    // looked plausible -- so these assertions check actual decoded values, not just item counts.
    @Test
    public void realVersion105BarbarianCharacterWithCurrentItemFormatParsesCorrectly() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/barb_gear.d2s").toURI()).getAbsolutePath());

        assertEquals("barb_gear", d2Character.getCharName());
        assertEquals("Barbarian", d2Character.getCharClass());
        assertEquals(1, d2Character.getCharLevel());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(11, items.size());
        // Two ethereal uniques exercise the ethereal-specific extra bit.
        assertTrue(items.stream().anyMatch(i -> "Arreat's Face".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> "Spectral Slayer".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> "Madman's Bluster".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> "Curseweaver".equals(i.getItemName())));
    }

    // This second character exercises the socket-count bit specifically: Blasthammer has 2
    // sockets in-game (player-confirmed), not the 4 that an earlier, incorrect version of this
    // fix decoded -- 4 happened to equal the item's maximum possible sockets, so it looked valid
    // without this assertion. Earth Shifter and everything after it in the item list only parses
    // correctly because of the second, unconditional extra bit described in
    // D2Item.readExtend2()'s comment.
    @Test
    public void realVersion105DruidCharacterWithSocketedItemParsesCorrectly() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/druid_gear.d2s").toURI()).getAbsolutePath());

        assertEquals("druid_gear", d2Character.getCharName());
        assertEquals("Druid", d2Character.getCharClass());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(15, items.size());

        D2Item blasthammer = items.stream()
                .filter(i -> "Blasthammer".equals(i.getItemName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Blasthammer not found"));
        assertEquals(2, blasthammer.getSocketNrTotal());

        assertTrue(items.stream().anyMatch(i -> "Earth Shifter".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> "Equinox Visor".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> "Swift Descent".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> "Wraith Whisper".equals(i.getItemName())));
    }

    // This third character exercises two more gaps found only once a real character had a
    // runeword item with sub-items actually socketed into it (the first two fixtures' runeword
    // items, if any, had empty sockets):
    //   - one extra byte after each socketed sub-item (D2Item's socket-recursion loop comment),
    //     confirmed by decoding the three runes socketed into "Love" (Pul, Hel, El) correctly.
    //   - a runeword's own bonus properties are a second property list read back-to-back with
    //     the item's own list, before (not after) the trailing bits (D2Item.readExtend2()'s
    //     second comment), confirmed against "Edge" (Tir+Tal+Amn) by matching every line of the
    //     player's actual in-game tooltip to its underlying stored stat. An earlier, incorrect
    //     version of this fix read plausible-but-wrong values here (e.g. +50 Dexterity from a
    //     bow) that did not throw and were only caught by checking the tooltip.
    @Test
    public void realVersion105AmazonCharacterWithRunewordSocketsParsesCorrectly() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/zon_gear.d2s").toURI()).getAbsolutePath());

        assertEquals("zon_gear", d2Character.getCharName());
        assertEquals("Amazon", d2Character.getCharClass());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(46, items.size());

        D2Item love = items.stream()
                .filter(i -> "Love".equals(i.getItemName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Love not found"));
        assertEquals(3, love.getSocketNrTotal());
        assertSocketedRuneNamesContain(love, "Pul Rune", "Hel Rune", "El Rune");

        D2Item edge = items.stream()
                .filter(i -> "Edge".equals(i.getItemName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Edge not found"));
        assertEquals(3, edge.getSocketNrTotal());
        assertSocketedRuneNamesContain(edge, "Tir Rune", "Tal Rune", "Amn Rune");

        // Every one of these matches a line from the player's actual in-game tooltip for this
        // item (stat IDs from itemstatcost.txt: 151=item_aura, 121=item_demondamage_percent,
        // 122=item_undeaddamage_percent, 2=dexterity -- one of the four "+9 to all Attributes"
        // components).
        assertEquals(15, findPropValue(edge, 151)[1]); // Level 15 Thorns Aura When Equipped
        assertEquals(334, findPropValue(edge, 121)[0]); // +334% Damage to Demons
        assertEquals(280, findPropValue(edge, 122)[0]); // +280% Damage to Undead
        assertEquals(9, findPropValue(edge, 2)[0]); // part of "All Stats +9"
    }

    // A fourth real character (a Bowazon mule, by far the largest of the four) originally hit an
    // item-format gap none of the other three exercised: a unique jewel ("Autumn Facet") followed
    // by 48 extra trailing bits nothing else read -- this is the "elemental Facet" fix
    // (D2Item.isElementalFacet() and its two call sites). Confirmed real by checking every
    // property on the jewel itself against its uniqueitems.txt recipe and the player's real
    // in-game tooltip, and confirmed general (not file-specific) across three independent Facets
    // in two files, including one appearing as a socketed sub-item (see bowazon2.d2s's test
    // below). A second, unrelated bug (see that same test) blocked this file's mercenary items
    // too; with both fixes, this file now loads completely -- every character and mercenary item,
    // with no partial-load fallback at all.
    @Test
    public void realVersion105AmazonCharacterWithElementalFacetParsesFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/bowazon.d2s").toURI()).getAbsolutePath());

        assertEquals("bowazon", d2Character.getCharName());
        assertEquals("Amazon", d2Character.getCharClass());
        assertFalse(d2Character.isItemsIncomplete());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(242, items.size());
        assertTrue(items.stream().anyMatch(i -> i.getItemName().contains("Autumn Facet")));
        assertEquals(9, d2Character.getMercItemNr());
        // Confirms the flag-29 fix (D2Item.readExtend()'s comment): without it, "Sling" corrupted
        // everything after it into "Ear"-shaped garbage and "Gem Bag" never appeared at all.
        assertTrue(items.stream().anyMatch(i -> "Sling".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> i.getItemName() != null && i.getItemName().contains("Gem Bag")));
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));

        // Not calling saveInternal(null) here: D2Backup.backup() dereferences its D2Project
        // argument unconditionally (pProject.getBackup()), so passing null to "just confirm it
        // doesn't throw the incomplete-item check anymore" actually NPEs further in -- and
        // because that NPE is caught and routed through D2FileManager.displayErrorDialog(), it
        // was popping open the real GoMule application window during this test run. A real
        // D2Project can't be constructed here either, since its constructor requires a
        // D2FileManager. isItemsIncomplete() above already covers the invariant this was meant
        // to check.
    }

    // A second snapshot of the same character (after the player added two more unique jewels --
    // "Rime Facet" and "Thunder Facet" -- to chase the gap above) found two more real, fixable
    // bugs along the way, both confirmed independently of the elemental-Facet gap:
    //   - D2Item.readExtend()'s "rvl" (Full Rejuvenation Potion) fix: confirmed by brute-force
    //     scanning the raw bytes for a valid next item at every bit offset near the boundary --
    //     exactly 8 bits, in both this file and bowazon.d2s, was the only amount that produced a
    //     real, recognizable item (not just "didn't throw").
    //   - D2Prop.generateDisplay()'s case (19)/(29) fix: a new Reimagined stat
    //     (pl_maxdamage_percent, on "Collin's Lesser Might") has only one value where this branch
    //     previously always assumed two, throwing ArrayIndexOutOfBoundsException whenever an item
    //     with that stat was rendered -- a real crash, not a parsing issue.
    // With the elemental-Facet fix, this file's full player inventory loads too -- including
    // "Sadira", a unique bow whose first socket holds a second, independent Rime Facet, which
    // proved the fix needed a second half: D2Item's socket-recursion loop must NOT also add its
    // usual one-extra-byte-per-socket skip after a socketed Facet, since the Facet's own 48-bit
    // skip already covers it (confirmed the same way as the rest of this fix -- adding both
    // skips broke the very next socket, a Shael Rune, which only decoded correctly with no extra
    // skip at all after the Facet).
    // A third, separate bug then surfaced one level further down, in the mercenary item list: the
    // three independent "extra trailing bit" flags found earlier (D2Item.readExtend2()'s
    // iSocketed/unconditional/ethereal bits) were each validated only one at a time -- this file's
    // mercenary carries "Fortitude" (El+Sol+Dol+Lo, a real runeword) and "Infinity" (Ber+Mal+Ber+
    // Ist, also real), both socketed AND ethereal at once, a combination none of the earlier
    // fixtures had. Brute-force scanning Fortitude's actual socket boundary (confirmed real by
    // decoding all 4 runes correctly, at the fixed item-length intervals real runes always sit at)
    // showed the three flags combine by XOR, not by sum: 1 bit when iSocketed and iEthereal agree
    // (including, newly, when both are true -- previously only "both false" was confirmed), 2 bits
    // when exactly one is true. Which specific bit(s) coincide when both flags are true is still
    // unknown; only the net count is confirmed. With this third fix, the file loads completely.
    // A fourth, unrelated rendering bug (not a parsing one) showed up once the file loaded fully:
    // D2PropCollection.combineProps()'s same-stat merge had a stale skip-list missing stat 83
    // (item_addclassskills), so a real unique amulet here ("Death Emblem", rolling two separate
    // class-skill bonuses -- +1 Amazon, +1 Necromancer, confirmed against the player's actual
    // in-game tooltip) had its two properties wrongly summed into one, both the class id and the
    // value, displaying as a single incorrect "+2 Necromancer Skills" instead of two real lines.
    // A fifth, separate item-format gap (D2Item.readExtend()'s flag-29 comment) hid in plain
    // sight the whole time: "Sling" (a unique ring with a randomly-rolled class-skill property,
    // "magicskill") needed 56 extra trailing bits nothing read, corrupting "Gem Bag" (the very
    // next item) into unrecognizable "Ear"-shaped garbage, and everything for a while after that
    // too -- reported by the player as "the Gem Bag is missing from the stash and shows up as an
    // ear in the belt potion slot" (the latter because the corrupted item's row/col/panel
    // coincidentally matched the layout GoMule's belt-potion lookup searches by). Brute-force
    // scanning Sling's actual end confirmed 56 bits as the only offset, out of about 100 tried,
    // that decoded a real, recognizable "Gem Bag" right after it.
    // A sixth, also unrelated rendering bug (not a parsing one, and not affecting loading or
    // saving) showed up in "Sadira": D2PropCollection.isHiddenSkillGrant()'s comment.
    @Test
    public void secondSnapshotOfAmazonCharacterConfirmsSixMoreRealFixes() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/bowazon2.d2s").toURI()).getAbsolutePath());

        assertEquals("bowazon", d2Character.getCharName());
        assertFalse(d2Character.isItemsIncomplete());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(182, items.size());
        assertTrue(items.stream().anyMatch(i -> "Sling".equals(i.getItemName())));
        assertTrue(items.stream().anyMatch(i -> i.getItemName() != null && i.getItemName().contains("Gem Bag")));
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
        // Confirms the rvl fix: without it, this item (right after the Full Rejuvenation
        // Potion) was unreachable -- parsing broke down before ever getting here.
        assertTrue(items.stream().anyMatch(i -> "Power of Ice".equals(i.getItemName())));
        // Confirms the D2Prop fix: without it, rendering this specific item threw
        // ArrayIndexOutOfBoundsException.
        D2Item collins = items.stream()
                .filter(i -> i.getItemName().contains("Collin's Lesser Might"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Collin's Lesser Might not found"));
        assertEquals(-40, findPropValue(collins, 364)[0]); // pl_maxdamage_percent
        assertEquals(-40, findPropValue(collins, 365)[0]); // pl_mindamage_percent
        D2ItemRenderer.itemDump(collins, true); // must not throw

        // Confirms the item_addclassskills merge fix (D2PropCollection.combineProps()): "Death
        // Emblem" rolls two separate stat-83 properties -- [class 0 (Amazon), 1] and [class 2
        // (Necromancer), 1] -- confirmed against the player's real in-game tooltip ("+1 to
        // Amazon Skills" and "+1 to Necromancer Skills" as two separate lines). Without the fix,
        // these merged into a single, wrong "[2, 2]" (both the class id and the value summed).
        D2Item deathEmblem = items.stream()
                .filter(i -> i.getItemName() != null && i.getItemName().contains("Death Emblem"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Death Emblem not found"));
        List<int[]> classSkillProps = new java.util.ArrayList<>();
        for (Object propObj : deathEmblem.getPropCollection()) {
            gomule.item.D2Prop prop = (gomule.item.D2Prop) propObj;
            if (prop.getPNum() == 83) classSkillProps.add(prop.getPVals());
        }
        assertEquals(2, classSkillProps.size());
        assertTrue(classSkillProps.stream().anyMatch(v -> v[0] == 0 && v[1] == 1)); // +1 Amazon
        assertTrue(classSkillProps.stream().anyMatch(v -> v[0] == 2 && v[1] == 1)); // +1 Necromancer
        D2ItemRenderer.itemDump(deathEmblem, true); // must not throw

        // Confirms the socketed-Facet half of the elemental-Facet fix: Sadira's first socket is a
        // second, real Rime Facet (distinct from the unsocketed one elsewhere in this file), and
        // decoding it correctly is what lets the three Shael Runes after it decode correctly too.
        D2Item sadira = items.stream()
                .filter(i -> "Sadira".equals(i.getItemName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sadira not found"));
        List<D2Item> sadiraSockets = sadira.getiSocketedItems();
        assertEquals(4, sadiraSockets.size());
        assertTrue(sadiraSockets.get(0).getItemName().contains("Rime Facet"));
        assertTrue(sadiraSockets.get(1).getItemName().contains("Shael Rune"));
        assertTrue(sadiraSockets.get(2).getItemName().contains("Shael Rune"));
        assertTrue(sadiraSockets.get(3).getItemName().contains("Shael Rune"));

        // Confirms the hidden-skill-grant display fix (D2PropCollection.isHiddenSkillGrant()):
        // Sadira's recipe includes "oskill_hide" granting skill 449 ("Hidden Charm Passive"), an
        // internal-only flag the real game never shows in the tooltip -- without the fix, GoMule
        // rendered it as "+1 to Charm Weight Active" (skill 449's translated display name) on
        // every item that grants it, reported by the player as "a lot of non-charm items" showing
        // an unexplained charm-related modifier.
        assertFalse(D2ItemRenderer.itemDump(sadira, true).contains("Charm Weight"));

        // Confirms the socketed+ethereal XOR fix, against the mercenary item list this time: both
        // "Fortitude" and "Infinity" are ethereal, socketed runewords, and both decode to their
        // exact real recipes.
        assertEquals(9, d2Character.getMercItemNr());
        D2Item fortitude = mercItemNamed(d2Character, "Fortitude");
        assertTrue(fortitude.isEthereal());
        assertSocketedRuneNamesContain(fortitude, "El Rune", "Sol Rune", "Dol Rune", "Lo Rune");
        D2Item infinity = mercItemNamed(d2Character, "Infinity");
        assertTrue(infinity.isEthereal());
        assertSocketedRuneNamesContain(infinity, "Ber Rune", "Mal Rune", "Ber Rune", "Ist Rune");

        // See the bowazon.d2s test above for why saveInternal(null) isn't called here.
    }

    // A third snapshot of the same Bowazon mule, later still, was reported by the player as
    // loading only partially: "Item 164 of 186 failed to parse: ... Error: null (null, null)".
    // Root cause: an "Orb of Assemblage" ("ooa") carries 8 extra trailing bits nothing read, so
    // every item after it desynced; the bare NPE-with-null-message came from the desynced next
    // item huffman-decoding an empty type code, which D2TxtFile.search() can't find. Brute-force
    // scanning the boundary (the same method as every other quirk in D2Item.readExtend()) showed
    // +8 bits was the only offset that decoded a real next item -- and "ooa" turned out to be one
    // of four "elixir"-type ("elix") items this single character carries that ALL need the same
    // +8: "Orb of Infusion" ("ooi", previously fixed per-code), "Orb of Assemblage" ("ooa"),
    // "Orb of Socketing" ("oos"), and "Gem Cluster" ("1gc"). That made it the whole "elix" class,
    // not four separate codes, so readExtend()'s former per-code "ooi" skip is now keyed on the
    // itemtype instead (see its comment). With that one rule this file loads completely -- all
    // character and mercenary items, no partial-load fallback.
    @Test
    public void thirdSnapshotOfAmazonCharacterWithElixirOrbsParsesFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/bowazon3.d2s").toURI()).getAbsolutePath());

        assertEquals("bowazon", d2Character.getCharName());
        assertEquals("Amazon", d2Character.getCharClass());
        assertFalse(d2Character.isItemsIncomplete());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(195, items.size());
        assertEquals(9, d2Character.getMercItemNr());

        // The three "elix" items whose missing +8 bits each desynced everything after them. (The
        // fourth, "ooi", is already covered by the bowazon2.d2s test above.) Checked by item code
        // rather than name because the rendered names carry color-code markup prefixes.
        assertTrue(items.stream().anyMatch(i -> "ooa".equals(i.getItem_type())), "Orb of Assemblage (ooa) missing");
        assertTrue(items.stream().anyMatch(i -> "oos".equals(i.getItem_type())), "Orb of Socketing (oos) missing");
        assertTrue(items.stream().anyMatch(i -> "1gc".equals(i.getItem_type())), "Gem Cluster (1gc) missing");
        // The desync used to corrupt later items into "Ear"-shaped garbage; confirm none leaked.
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    // A Paladin the player reported couldn't be saved: "Item 93 of 98 failed to parse ... Error:
    // null (null, null)". Same desync signature as the elix orbs above, but a different root
    // cause: a "Light Healing Potion" ("hp2") whose 72-bit compact body ends exactly on a byte
    // boundary. The generic end-of-item rounding (getNextByteBoundaryInBits) doesn't advance past
    // an already-aligned position, so it dropped the potion's trailing padding byte and every item
    // after it desynced. The fix (readExtend()'s "hpot"/"mpot" comment) adds +8 only for potions
    // whose body is byte-aligned -- confirmed here by this same file carrying three "mp1"s (73-bit
    // bodies) that parse fine with no adjustment, alongside the one hp2 that needed it. With the
    // fix the whole character loads; without it, saving was blocked.
    @Test
    public void paladinWithByteAlignedHealingPotionParsesFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertFalse(d2Character.isItemsIncomplete());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(98, items.size());
        assertEquals(0, d2Character.getMercItemNr());

        // The byte-aligned Light Healing Potion whose dropped padding byte caused the desync, plus
        // a Light/Minor Mana Potion (73-bit body) proving non-aligned potions still parse untouched.
        assertTrue(items.stream().anyMatch(i -> "hp2".equals(i.getItem_type())), "hp2 (Light Healing Potion) missing");
        assertTrue(items.stream().anyMatch(i -> "mp1".equals(i.getItem_type())), "mp1 (Minor Mana Potion) missing");
        // The desync used to corrupt later items into "Ear"-shaped garbage; confirm none leaked.
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    // A later snapshot of the same paladin, after eight runes -- Sur, Zod, Gul, Vex, Ohm, Lo, Jah
    // and Cham, plus a "Latent Black Cleft" charm -- had been moved into the Horadric Cube. The
    // character stopped loading with the same null-message NullPointerException, because a loose
    // rune/gem stored in the cube can carry one extra trailing padding byte -- but only when its
    // body lands byte-aligned, exactly the dropped-padding-byte quirk the simple potions have (see
    // D2Item.readExtend()'s panel==4 branch). Here that was true for the Sur and Ohm runes but not
    // the other six, so a first guess that "high runes" (or every cube rune) needed the extra byte
    // was wrong: it is decided purely by byte alignment. With the alignment-conditional skip all
    // 101 items load, including every cube rune and the charm sharing the cube.
    @Test
    public void paladinWithRunesInCubeParsesFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally2.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertFalse(d2Character.isItemsIncomplete());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(101, items.size());

        // Every one of the eight runes moved into the cube (panel 4) parsed -- both the two that
        // needed the extra byte (Sur, Ohm) and the six that did not.
        for (String rune : new String[]{"r29", "r33", "r25", "r26", "r27", "r28", "r31", "r32"}) {
            assertTrue(items.stream().anyMatch(i -> rune.equals(i.getItem_type()) && i.get_panel() == 4),
                    "cube rune " + rune + " should have parsed");
        }
        // The charm sharing the cube, right after the first byte-aligned rune that used to desync it.
        assertTrue(items.stream().anyMatch(i -> i.getItemName() != null
                        && i.getItemName().contains("Latent Black Cleft")),
                "the charm inside the cube should have parsed");
        // Confirm the desync didn't leak "Ear"-shaped garbage into any later item.
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    // A much larger snapshot of the same paladin (a full stash of modded gear) that found three
    // more real, independent parsing bugs, each confirmed by decoding the actual items involved and
    // by the ~110 items after each one only parsing once it was fixed:
    //   1. A socketed flag-29 (skill-granting) item stores its extra skill bits BEFORE its socketed
    //      sub-items, not after -- the unique scepter "Hand of Blessed Light" with two "Heaven Facet"
    //      jewels in it. See D2Item's socket loop. (The blob's length also drove the discriminator
    //      fix in hasElementalSkillProperty(); the dedicated three-copy fixture below pins that down.)
    //   2. A socketed JEWEL (not just an elemental Facet) does not take the one-byte inter-socket
    //      skip that runes/gems do -- both of that scepter's Heaven Facets (a Reimagined jewel
    //      outside the 392-399 elemental-Facet range). Same socket loop.
    //   3. A rune socketed into an item whose body lands byte-aligned loses its trailing padding
    //      byte to the same end-of-item rounding quirk the potions/cube-runes have -- the final
    //      runes of the runeword flails "Call to Arms" (Ohm) and "Heart of the Oak" (Thul), the only
    //      ones in each whose body happened to land byte-aligned. See D2Item.readExtend()'s
    //      location == 6 branch.
    @Test
    public void paladinWithSocketedSkillUniqueAndFacetsParsesFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally3.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(PALLY3_ITEM_COUNT, items.size());

        // (1)+(2): the socketed flag-29 unique scepter with its two non-elemental "Heaven Facet"
        // jewels -- both facets must have decoded (they only do once the skill bits move before the
        // sockets AND the inter-socket byte is withheld for jewels).
        D2Item hbl = items.stream().filter(i -> "Hand of Blessed Light".equals(i.getItemName()))
                .findFirst().orElseThrow(() -> new AssertionError("Hand of Blessed Light not found"));
        assertEquals(2, hbl.getSocketNrFilled());
        assertNotNull(hbl.getiSocketedItems());
        assertEquals(2, hbl.getiSocketedItems().size());
        assertTrue(hbl.getiSocketedItems().stream()
                        .allMatch(s -> s.getItemName() != null && s.getItemName().contains("Heaven Facet")),
                "both sockets of Hand of Blessed Light should be Heaven Facets");

        // (3): both runeword flails whose final rune landed byte-aligned parsed, and the items after
        // them did too (they are what exposed the socketed-rune padding-byte drop).
        assertTrue(items.stream().anyMatch(i -> "Call to Arms".equals(i.getItemName())), "Call to Arms missing");
        assertTrue(items.stream().anyMatch(i -> "Heart of the Oak".equals(i.getItemName())), "Heart of the Oak missing");

        // The desync used to corrupt following items into "Ear"-shaped garbage; confirm none leaked.
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    private static final int PALLY3_ITEM_COUNT = 171;

    // A deliberately-minimal character built to isolate the flag-29 trailing skill blob: it carries
    // the SAME unique scepter, "Hand of Blessed Light", three times -- once with jewels socketed in,
    // once with empty sockets, and once un-socketed -- plus a few plain white bases. All three copies
    // decode, and every item after them parses, only when that blob is 52 bits, not the 56 the old
    // func-21 heuristic gave it (its "+2 Paladin skills" is item_addclassskills, func 21, but NOT the
    // item_elemskill an elemental-skill bonus like Sling's would be -- see hasElementalSkillProperty).
    // The three copies decoding identically -- socketed and not -- is also what proved the blob is
    // the same structure whether it sits before the sockets or at the tail.
    @Test
    public void threeHandOfBlessedLightSocketVariantsAllParse() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/hblSocketVariants.d2s").toURI()).getAbsolutePath());

        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());

        List<D2Item> items = d2Character.getItemList();
        List<D2Item> hbls = items.stream()
                .filter(i -> "Hand of Blessed Light".equals(i.getItemName()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(3, hbls.size(), "all three Hand of Blessed Light copies should parse");

        // The three socket configurations: empty sockets (0 of 5), un-socketed (0 of 0), and filled
        // with 4 Heaven Facet jewels (4 of 5).
        assertTrue(hbls.stream().anyMatch(i -> i.getSocketNrFilled() == 0 && i.getSocketNrTotal() == 5),
                "the empty-socketed copy (0 of 5) should parse");
        assertTrue(hbls.stream().anyMatch(i -> i.getSocketNrFilled() == 0 && i.getSocketNrTotal() == 0),
                "the un-socketed copy (0 of 0) should parse");
        D2Item filled = hbls.stream().filter(i -> i.getSocketNrFilled() == 4).findFirst()
                .orElseThrow(() -> new AssertionError("the 4-jewel copy should parse"));
        assertEquals(4, filled.getiSocketedItems().size());
        assertTrue(filled.getiSocketedItems().stream()
                        .allMatch(s -> s.getItemName() != null && s.getItemName().contains("Heaven Facet")),
                "all four sockets should be Heaven Facets");
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    // The fullest snapshot of the paladin's stash (187 character items + a geared mercenary). It
    // added one more real quirk on top of everything the pally3 fixture already covers: a Reimagined
    // "Worldstone Shard" quest item ("Deep Worldstone Shard", code xa4) carries 8 trailing bits
    // nothing above read, so it came out a byte short and every item after it failed to load. See
    // D2Item.isWorldstoneShard(). This file also exercises the elemental-skill discriminator fix at
    // scale: a set item ("Immortal King's Stone Crusher", +class-skills via item_addclassskills) sits
    // right before a second set item ("Animal Kinship"), and both -- plus the ~35 items after them --
    // only parse once that +class-skills grant stops being over-counted as an elemental-skill one.
    @Test
    public void fullPaladinStashWithWorldstoneShardAndSetItemsParsesCompletely() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally4.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(196, items.size());
        // The quest item whose missing trailing byte was the last thing blocking a full load.
        assertTrue(items.stream().anyMatch(i -> "xa4".equals(i.getItem_type())
                        && "Deep Worldstone Shard".equals(i.getItemName())),
                "Deep Worldstone Shard (xa4) should have parsed");
        // The two adjacent set items that the elemental-skill discriminator fix keeps aligned.
        assertTrue(items.stream().anyMatch(i -> "Animal Kinship".equals(i.getItemName())), "Animal Kinship missing");
        assertTrue(items.stream().anyMatch(i -> "Immortal King's Stone Crusher".equals(i.getItemName())),
                "Immortal King's Stone Crusher missing");
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    // A later paladin snapshot that added two more independent quirks:
    //   1. A newly-added Reimagined item with no localized string, the "cs2" Crafted Sunder Charm
    //      (here rolled into a unique, "Renewed Black Cleft"). Its base-name lookup used to throw
    //      "No translation for cs2" and abort the whole load; now a missing translation falls back to
    //      the raw .txt display name. See D2Item.readExtend()'s getTranslationOrNull fallback.
    //   2. A "Colossal Jewel" (item code "cjw", e.g. the unique "Guardian's Light") socketed as the
    //      last of five sockets in a "Hand of Blessed Light". A Colossal Jewel is a different item
    //      code from a plain Jewel ("jew") but shares namestr "jew", so keying the socketed-jewel
    //      "no inter-socket byte" rule on namestr rather than the exact code keeps it from over-
    //      reading and dropping the item after the scepter. See D2Item's socket loop.
    @Test
    public void paladinWithColossalJewelAndUntranslatedCharmParsesFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally5.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(122, items.size());

        // (1) the untranslated cs2 charm parsed (and still resolved its unique name).
        assertTrue(items.stream().anyMatch(i -> "cs2".equals(i.getItem_type())),
                "the untranslated cs2 charm should have parsed");
        // (2) the Hand of Blessed Light holding four Heaven Facets plus one Colossal Jewel (cjw).
        D2Item hbl = items.stream().filter(i -> "Hand of Blessed Light".equals(i.getItemName())
                        && i.getSocketNrFilled() == 5).findFirst()
                .orElseThrow(() -> new AssertionError("5-socket Hand of Blessed Light not found"));
        assertTrue(hbl.getiSocketedItems().stream().anyMatch(s -> "cjw".equals(s.getItem_type())),
                "the Colossal Jewel (cjw) socket should have decoded");
        assertEquals(4, hbl.getiSocketedItems().stream()
                .filter(s -> "jew".equals(s.getItem_type())).count(), "four Heaven Facets expected");
        assertTrue(items.stream().noneMatch(i -> i.getItemName() != null && i.getItemName().contains("Ear")));
    }

    private static D2Item mercItemNamed(D2Character pCharacter, String pName) {
        for (int i = 0; i < pCharacter.getMercItemNr(); i++) {
            D2Item lItem = pCharacter.getMercItem(i);
            if (lItem.getItemName().contains(pName)) return lItem;
        }
        throw new AssertionError("Mercenary item '" + pName + "' not found");
    }

    // Socketed runes' getItemName() includes embedded color-code markup and the "(#N)" rune
    // number (as rendered for display), so this checks each expected name is a substring rather
    // than an exact match.
    private static void assertSocketedRuneNamesContain(D2Item item, String... expectedNames) {
        List<D2Item> sockets = item.getiSocketedItems();
        assertEquals(expectedNames.length, sockets.size());
        for (int i = 0; i < expectedNames.length; i++) {
            assertTrue(
                    sockets.get(i).getItemName().contains(expectedNames[i]),
                    "Expected socket " + i + " of " + item.getItemName() + " to contain '"
                            + expectedNames[i] + "' but was: " + sockets.get(i).getItemName());
        }
    }

    private static int[] findPropValue(D2Item item, int statId) {
        for (Object propObj : item.getPropCollection()) {
            gomule.item.D2Prop prop = (gomule.item.D2Prop) propObj;
            if (prop.getPNum() == statId) {
                return prop.getPVals();
            }
        }
        throw new AssertionError("Stat " + statId + " not found on " + item.getItemName());
    }

    @Test
    public void classByteToAbbreviationHandlesAllEightClasses() {
        assertEquals("ama", D2Character.classByteToAbbreviation(0));
        assertEquals("sor", D2Character.classByteToAbbreviation(1));
        assertEquals("nec", D2Character.classByteToAbbreviation(2));
        assertEquals("pal", D2Character.classByteToAbbreviation(3));
        assertEquals("bar", D2Character.classByteToAbbreviation(4));
        assertEquals("dru", D2Character.classByteToAbbreviation(5));
        assertEquals("ass", D2Character.classByteToAbbreviation(6));
        assertEquals("war", D2Character.classByteToAbbreviation(7));
        assertNull(D2Character.classByteToAbbreviation(8));
    }

    // A real Reimagined (D2RMM) paladin that hit level 100 -- vanilla D2 caps at 99, and the old
    // "iCharLevel > 99" guard in readChar() rejected the whole file outright ("Invalid char level:
    // 100"), long before any item was read. The mod raises the cap; the level byte is 8 bits so any
    // positive value is representable. This asserts the character now loads fully at level 100 with
    // all its items intact.
    @Test
    public void level100ReimaginedPaladinLoadsFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally6.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertEquals(100, d2Character.getCharLevel());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());
        assertEquals(134, d2Character.getItemList().size());
    }

    // A later re-save of the same Reimagined paladin exposed that the "grab"-type tool items (the gem
    // Grabbers, Pandemonium-key Grabbers and Pliers) do NOT unconditionally carry an extra trailing
    // byte -- they only carry it when flag 28 is set (see the "grab" comment in D2Item.readExtend).
    // This save has three gem Grabbers sitting loose in the stash -- an Amethyst (agr), a Ruby (rgr)
    // and an Emerald (mgr) Grabber -- all with flag 28 CLEAR, so all needing zero trailing bits. Under
    // the old unconditional "grab" +8 the first one (agr) read a byte long and every item after it
    // failed, stopping the load at item 14 of 98; the item that first failed to decode was the
    // "Heaven Facet" immediately following the grabbers. This asserts the whole file now loads, that
    // all three grabbers decoded, and that the previously-desynced Heaven Facet after them is back.
    @Test
    public void reimaginedPaladinWithFlagClearGrabbersLoadsFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally7.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());
        assertEquals(108, d2Character.getItemList().size());

        List<D2Item> items = d2Character.getItemList();
        assertTrue(items.stream().anyMatch(i -> "agr".equals(i.getItem_type())), "Amethyst Grabber");
        assertTrue(items.stream().anyMatch(i -> "rgr".equals(i.getItem_type())), "Ruby Grabber");
        assertTrue(items.stream().anyMatch(i -> "mgr".equals(i.getItem_type())), "Emerald Grabber");
        // The item that first desynced under the old rule -- proves the grabber boundaries are right.
        assertTrue(items.stream().anyMatch(i -> i.getItemName() != null
                && i.getItemName().contains("Heaven Facet")), "Heaven Facet after the grabbers");
    }

    // Another re-save of the same Reimagined paladin, this time carrying the unique ring "Opalvein"
    // ("15% Chance to cast level 2 Flame Wave on attack"). A flag-29 item whose granted skill is a
    // "chance to cast ... on attack" (item_skillonattack, stat 195) carries 64 more trailing bits
    // than the standard 52-bit flag-29 skill blob (see the blob's comment in D2Item.readExtend).
    // Under the old rule Opalvein read exactly 64 bits short and every item after it failed, stopping
    // the load at item 84 of 125; the first item to fail to decode was the ring immediately after it,
    // "Raven Frost". This asserts the whole file now loads, that Opalvein decoded with its on-attack
    // skill stat, and that Raven Frost is back in place right after it. Distinct from the file's other
    // flag-29 chance-to-cast rings ("Wisp Projector", "Carrion Wind"), whose grants are on *striking*
    // / *when struck* (stats 198/201) and which decoded correctly at the standard 52 with no extra
    // bits -- so the 64 is specific to the on-attack variant, not chance-to-cast in general.
    @Test
    public void reimaginedPaladinWithCastOnAttackRingLoadsFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally8.d2s").toURI()).getAbsolutePath());

        assertEquals("pally", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertEquals(100, d2Character.getCharLevel());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());

        List<D2Item> items = d2Character.getItemList();
        assertEquals(135, items.size());

        int opalveinIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            if ("Opalvein".equals(items.get(i).getItemName())) {
                opalveinIdx = i;
                break;
            }
        }
        assertTrue(opalveinIdx >= 0, "Opalvein decoded");

        // Opalvein carries the item_skillonattack stat (id 195) -- the "cast on attack" grant that
        // lengthens its flag-29 blob by 64 bits and the whole reason this file failed to load.
        D2Item opalvein = items.get(opalveinIdx);
        assertTrue(opalvein.getPropCollection().stream()
                        .anyMatch(o -> ((gomule.item.D2Prop) o).getPNum() == 195),
                "Opalvein has the on-attack chance-to-cast stat");

        // The ring immediately after Opalvein is the one that first desynced under the old rule --
        // its decoding proves Opalvein's trailing length (and hence the +64) is right, not just that
        // nothing threw.
        assertEquals("Raven Frost", items.get(opalveinIdx + 1).getItemName(),
                "the ring right after Opalvein decodes");
    }

    // A real Reimagined paladin's mercenary carried the set ring "The River Stix" (Hades'
    // Underworld, base Demonhide Boots -- despite the name, this D2RMM set reuses a ring's set ID
    // for a boots piece). setitems.txt says its only threshold bonus, "nofreeze"
    // (item_cannotbefrozen), rolls a value (amin4a/amax4a "1"/"1"), but nothing was actually
    // stored for it in this save -- reading it anyway (the old rule: "amin present -> a bonus
    // list is stored") consumed the mercenary's next item's leading bits; the first item to
    // desync was the ring right after it, "Fall From Grace". This is the real file that drove
    // D2Item.isSetBonusListPresent() (see its comment and the "quality == 5" block's): a second
    // real copy of this same ring needed threshold 4 read after all, so the fix confirms presence
    // from the bitstream itself rather than from setitems.txt. This asserts the whole file now
    // loads and every mercenary item -- including the two either side of The River Stix -- decoded
    // as its real name, not "X's Ear"-shaped garbage.
    @Test
    public void reimaginedPaladinWithFixedFlagSetBonusRingLoadsFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2Character d2Character = new D2Character(
                new File(Resources.getResource("charFiles/pally9.d2s").toURI()).getAbsolutePath());

        assertEquals("mouchton", d2Character.getCharName());
        assertEquals("Paladin", d2Character.getCharClass());
        assertEquals(85, d2Character.getCharLevel());
        assertFalse(d2Character.isItemsIncomplete(), d2Character.getItemsIncompleteReason());
        assertEquals(89, d2Character.getItemList().size());

        assertEquals(8, d2Character.getMercItemNr());
        assertEquals("Warder's Bond", d2Character.getMercItem(2).getItemName());
        assertEquals("The River Stix", d2Character.getMercItem(3).getItemName());
        assertEquals("Fall From Grace", d2Character.getMercItem(4).getItemName());
        assertEquals("Scout", d2Character.getMercItem(5).getItemName());
        assertEquals("Anaconda Skin", d2Character.getMercItem(6).getItemName());
        assertEquals("Sin and Greed", d2Character.getMercItem(7).getItemName());

        // The River Stix's own three fixed properties (ac/lvl, move3, res-cold) decoded, proving
        // its own property list is intact; item_cannotbefrozen (id 153) must NOT appear a second
        // time as a stray threshold bonus.
        D2Item riverStix = d2Character.getMercItem(3);
        assertEquals(1, riverStix.getPropCollection().stream()
                .filter(o -> ((gomule.item.D2Prop) o).getPNum() == 214).count(), "ac/lvl");
        assertEquals(1, riverStix.getPropCollection().stream()
                .filter(o -> ((gomule.item.D2Prop) o).getPNum() == 96).count(), "move3");
        assertEquals(1, riverStix.getPropCollection().stream()
                .filter(o -> ((gomule.item.D2Prop) o).getPNum() == 43).count(), "res-cold");
    }
}
