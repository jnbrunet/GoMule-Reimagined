package gomule.d2i;

import com.google.common.io.BaseEncoding;
import com.google.common.io.Resources;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import gomule.util.D2BitReader;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class D2SharedStashReaderTest {

    @Test
    public void simpleStash() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        byte[] simpleStash = BaseEncoding.base16()
                .decode(
                        "55AA55AA0000000063000000F2A416004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564D6900855AA55AA0000000063000000000000004D00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001000A2000564F6472255AA55AA0000000063000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000");
        D2SharedStash stash =
                new D2SharedStashReader().readStash("somethingSoftCore.d2i", new D2BitReader(simpleStash));
        assertEquals(1484018, stash.getPane(0).getGold());
        assertEquals(0, stash.getPane(1).getGold());
        assertEquals(0, stash.getPane(2).getGold());
        assertTrue(stash.isSC());
        assertFalse(stash.isHC());
        assertEquals(
                singletonList("TP\n" + "Version: Resurrected\n"), getItemDumps(stash.getPane(0)));
        assertEquals(singletonList("ID\n" + "Version: Resurrected\n"), getItemDumps(stash.getPane(1)));
        assertEquals(emptyList(), getItemDumps(stash.getPane(2)));
        StringWriter out = new StringWriter();
        stash.fullDump(new PrintWriter(out));
        assertEquals(
                "somethingSoftCore.d2i\n" + "\n"
                        + "\n"
                        + "TP\n"
                        + "Version: Resurrected\n"
                        + "\n"
                        + "ID\n"
                        + "Version: Resurrected\n"
                        + "Finished: somethingSoftCore.d2i\n\n",
                out.toString().replaceAll("\r", ""));
    }

    @Test
    public void issue1RealWorldStashLoadsWithoutDisconnecting() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String filename = new java.io.File(
                Resources.getResource("sharedStash/SharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();
        D2SharedStash stash = new D2SharedStashReader().readStash(filename);

        assertEquals(7, stash.getPanes().size());
        assertEquals(2500000, stash.getPane(0).getGold());
        assertEquals(2500000, stash.getPane(1).getGold());
        assertEquals(957236, stash.getPane(2).getGold());
        assertEquals(0, stash.getPane(3).getGold());
        assertEquals(0, stash.getPane(4).getGold());
        assertEquals(0, stash.getPane(5).getGold());
        assertEquals(0, stash.getPane(6).getGold());
        assertEquals(82, stash.getPane(0).getItems().size());
        assertEquals(23, stash.getPane(1).getItems().size());
        assertEquals(22, stash.getPane(2).getItems().size());
        assertEquals(17, stash.getPane(3).getItems().size());
        assertEquals(42, stash.getPane(4).getItems().size());
        assertEquals(34, stash.getPane(5).getItems().size());
        assertEquals(64, stash.getPane(6).getItems().size());
    }

    // A second real shared stash, from the same Reimagined mod patch that converted two of its
    // tabs to hold runes/keys/gems (see the user's report). Panes 0-4 are completely ordinary and
    // exercised four more real item-format gaps independently of that conversion -- a set item
    // ("Death Knight's Demon Blade") with a fixed, never-rolled threshold-bonus property
    // (readExtend2's set-bonus comment), the same flag-29 quirk fixed earlier needing a
    // different trailing-bit count depending on whether the skill it grants is randomly picked
    // or just named (D2Item.hasRandomlyPickedSkillProperty()'s comment), and -- the deciding
    // finding -- proof that the long-standing "socketed XOR ethereal" trailing-bit rule was wrong
    // the whole time: every character file that validated it happened to have its 1-bit error
    // masked by D2Item's byte-rounding, and a real unique armor here ("Adamantine Mail") exposed
    // it by landing on a byte boundary that didn't absorb the error. The real rule is simpler:
    // always exactly 1 trailing bit, never 2, regardless of either flag.
    // Pane 5 is a "Modern"/DLC auto-arranging STACKABLE rune/gem/orb tab. Its 63 entries -- runes,
    // gems, elixir-family Orbs, Worldstone Shards, Pandemonium keys, Rune Pliers and an Uber Ancient
    // material -- ARE all regular items and now decode in full, with the pane's byte length matching
    // its header exactly. An earlier read gave up at the first byte-aligned gem and mistook the rest
    // for "non-item storage"; the isLooseRuneOrGem (byte-aligned loose gem), isReimaginedQuestItem
    // (ques-with-blank-quest-column shards/keys/materials) and "grab"-type (Rune Pliers) fixes
    // disproved that -- every one is a real, fully decoded item. What the tab genuinely does NOT match
    // is GoMule's one-item-per-cell grid: its items stack on shared slots, so the pane is kept (its
    // items appear in the flat item list and search) but marked incomplete -- hidden from the tab
    // strip and written back verbatim rather than reconstructed into a layout GoMule doesn't model
    // (see D2SharedStashPane.fromItemsStacked). Pane 6 is a declared-but-empty trailing pane with no
    // "JM" item marker at all (~5.5KB of zero padding); it is likewise kept as an empty,
    // verbatim-preserved pane instead of being parsed as thousands of phantom items. The partial-load
    // fallback keeps both clearly-marked-incomplete instead of losing every pane, the five good ones
    // included.
    //
    // Originally, isItemsIncomplete() also blocked saving outright -- but a real player ran into
    // exactly that: wanting to move an item between the 5 working tabs, but unable to save *any*
    // change because the 2 broken tabs made the whole stash refuse to save. Fixed by having
    // D2SharedStashReader capture each incomplete pane's exact original bytes (found the same
    // offset-independent way as the pane boundaries themselves) and having D2SharedStashWriter
    // write those back verbatim instead of attempting a reconstruction GoMule doesn't have enough
    // understanding of these 2 tabs to get right. Confirmed via a full save-and-reread round trip
    // below: the 5 working panes save and reread correctly, and the 2 broken panes' bytes are
    // byte-for-byte identical to the original file, both immediately after saving and again after
    // a fresh read of the saved file.
    @Test
    public void secondRealWorldStashWithConvertedTabsLoadsPartiallyAndSavesTheRestSafely() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        byte[] originalBytes = Resources.toByteArray(Resources.getResource("sharedStash/ModernSharedStashSoftCoreV2.d2i"));
        java.io.File tempFile = java.io.File.createTempFile("ModernSharedStashSoftCoreV2", ".d2i");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), originalBytes);

        D2SharedStash stash = new D2SharedStashReader().readStash(tempFile.getAbsolutePath());

        assertEquals(7, stash.getPanes().size());
        assertTrue(stash.isItemsIncomplete());
        // The 2 converted (incomplete) tabs are hidden from the UI; the 5 real tabs remain.
        assertEquals(5, stash.getVisibleTabCount());

        // Pane 0 used to load all 45 items and no longer does -- the one case a table-driven
        // set-bonus rule cannot survive, and a deliberate trade. This file is the OLDEST snapshot
        // of the user's stash, and it holds a "Maadi's Soul" saved while the mod's setitems.txt
        // still gave that item a rolled threshold-4 bonus ("extra_bonespears", aprop4a). The
        // installed mod has since deleted that bonus (it is a plain prop7 now), so the item's
        // stored bonus list has nothing left in the table to be found by, and the 5 items after it
        // in the pane desync. ./d2111/setitems.txt is what decides this, and it has to match the
        // INSTALLED mod or currently-saved items break instead: leaving the table stale is what
        // produced the user's actual bug report (an "Afterlife" in a live stash desyncing the 33
        // items after it -- see afterlifeStashWithThreeSetBonusListsLoadsTheWholeFirstTab below,
        // and readExtend2's set-bonus comment for the full reasoning). Reading undeclared
        // thresholds speculatively was tried as a way to have both and is not viable: the
        // 9-bit-id trial-read probe is not strong enough to run unguarded and over-read real items
        // in two other fixtures here. So the current table wins and this one old pane goes
        // partial. Everything else in the file is unaffected.
        assertTrue(stash.getPane(0).isIncomplete());
        assertTrue(stash.getPane(0).getIncompleteReason().contains("failed to parse"));
        assertNotNull(stash.getPane(0).getOriginalBytes(), "a partial pane must still save verbatim");
        assertEquals(850000, stash.getPane(0).getGold());
        // Pane 0 is a visible tab, so -- unlike the two hidden converted tabs -- it does now report
        // a partial load to the user, which is the honest answer for a pane GoMule cannot decode.
        assertTrue(stash.hasVisibleIncompletePane());

        assertEquals(50, stash.getPane(1).getItems().size());
        assertEquals(46, stash.getPane(2).getItems().size());
        assertEquals(44, stash.getPane(3).getItems().size());
        assertEquals(32, stash.getPane(4).getItems().size());
        for (int i = 1; i <= 4; i++) {
            assertFalse(stash.getPane(i).isIncomplete(), "pane " + i + " should have loaded completely");
        }

        // Pane 5 is the stackable rune/gem/orb tab: still incomplete (hidden + saved verbatim), but
        // its 63 real items are now recovered instead of dropped, so they show up in searches.
        assertTrue(stash.getPane(5).isIncomplete());
        assertEquals(63, stash.getPane(5).getItems().size());
        assertTrue(stash.getPane(5).getIncompleteReason().contains("Stackable"));
        assertNotNull(stash.getPane(5).getOriginalBytes());
        // Concrete spot-checks across the item families that each needed a parser fix, proving the
        // whole tab decoded and not just that the count happens to line up.
        assertTrue(pane5Has(stash, "Tal Rune"), "loose rune");          // isLooseRuneOrGem
        assertTrue(pane5Has(stash, "Emerald"), "byte-aligned loose gem"); // isLooseRuneOrGem alignment
        assertTrue(pane5Has(stash, "Worldstone Shard"), "ques family");   // isReimaginedQuestItem
        assertTrue(pane5Has(stash, "Key of Destruction"), "Pandemonium key"); // isReimaginedQuestItem
        assertTrue(pane5Has(stash, "Rune Pliers"), "grab-type tool");     // "grab" iType
        // Pane 6 is the empty trailing/padding pane: no JM marker, no items, preserved verbatim.
        assertTrue(stash.getPane(6).isIncomplete());
        assertEquals(0, stash.getPane(6).getItems().size());
        assertTrue(stash.getPane(6).getIncompleteReason().contains("No item-list"));
        assertNotNull(stash.getPane(6).getOriginalBytes());

        // Find pane 0, 5 and 6's exact byte ranges in the original file directly (independently of
        // the reader under test), to check their preserved bytes against, below. Pane 0 is in this
        // list now that it loads partially: the point of the byte-preserving save is precisely that
        // a pane GoMule can't decode still survives a save untouched, so nothing in it is lost on
        // disk even though the UI can't show it.
        int[] markerOffsets = new D2BitReader(originalBytes.clone()).findBytes(D2SharedStashReader.STASH_HEADER_START);
        byte[] originalPane0Bytes = java.util.Arrays.copyOfRange(originalBytes, markerOffsets[0], markerOffsets[1]);
        byte[] originalPane5Bytes = java.util.Arrays.copyOfRange(originalBytes, markerOffsets[5], markerOffsets[6]);
        byte[] originalPane6Bytes = java.util.Arrays.copyOfRange(originalBytes, markerOffsets[6], originalBytes.length);
        assertArrayEquals(originalPane0Bytes, stash.getPane(0).getOriginalBytes());
        assertArrayEquals(originalPane5Bytes, stash.getPane(5).getOriginalBytes());
        assertArrayEquals(originalPane6Bytes, stash.getPane(6).getOriginalBytes());

        stash.saveInternal(null); // must not throw -- this is the fix

        byte[] savedBytes = java.nio.file.Files.readAllBytes(tempFile.toPath());
        int[] savedMarkerOffsets = new D2BitReader(savedBytes.clone()).findBytes(D2SharedStashReader.STASH_HEADER_START);
        assertEquals(7, savedMarkerOffsets.length);
        assertArrayEquals(originalPane0Bytes, java.util.Arrays.copyOfRange(savedBytes, savedMarkerOffsets[0], savedMarkerOffsets[1]));
        assertArrayEquals(originalPane5Bytes, java.util.Arrays.copyOfRange(savedBytes, savedMarkerOffsets[5], savedMarkerOffsets[6]));
        assertArrayEquals(originalPane6Bytes, java.util.Arrays.copyOfRange(savedBytes, savedMarkerOffsets[6], savedBytes.length));

        D2SharedStash reread = new D2SharedStashReader().readStash(tempFile.getAbsolutePath());
        assertEquals(7, reread.getPanes().size());
        assertTrue(reread.isItemsIncomplete());
        assertTrue(reread.getPane(0).isIncomplete());
        assertEquals(850000, reread.getPane(0).getGold());
        assertEquals(50, reread.getPane(1).getItems().size());
        assertEquals(46, reread.getPane(2).getItems().size());
        assertEquals(44, reread.getPane(3).getItems().size());
        assertEquals(32, reread.getPane(4).getItems().size());
        // Saved verbatim, so the stackable tab re-reads to the same 63 recovered items.
        assertEquals(63, reread.getPane(5).getItems().size());
        assertTrue(reread.getPane(5).isIncomplete());
        assertEquals(0, reread.getPane(6).getItems().size());
        assertTrue(reread.getPane(6).isIncomplete());
    }

    // True if pane 5 holds an item whose (color-code-prefixed) display name contains the substring.
    private static boolean pane5Has(D2SharedStash stash, String namePart) {
        return stash.getPane(5).getItems().stream()
                .anyMatch(it -> it.getItemName() != null && it.getItemName().contains(namePart));
    }

    // A later save of the same "Modern" DLC stash, kept as its own fixture because it carries the
    // item this test exists for: an elemental Facet ("Spring Facet", uid 392) with flag 29 CLEAR. The
    // 48-bit elemental-Facet trailing blob turned out to be the flag-29 skill blob (facets grant a
    // level-up/death skill), present only when the item actually sets flag 29 -- but every Facet
    // sampled before this file happened to have it set, so isElementalFacet's skip was applied
    // unconditionally. This file has two Spring Facets side by side in a real stash tab (pane 2), one
    // with flag 29 set and one clear; the flag-clear one over-read by exactly those 6 bytes and
    // desynced the ~30 items after it -- the D2Item.readExtend NPE the user reported. Gating the skip
    // on check_flag(29) (like the non-Facet blob already was) fixes it. Pane 2 is a normal, VISIBLE
    // tab here, so this is asserted through the ordinary loaded item list, not a hidden/verbatim pane.
    @Test
    public void modernStashWithFlag29ClearSpringFacetLoadsTheVisibleTabAndSavesUnchanged() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        byte[] originalBytes = Resources.toByteArray(Resources.getResource("sharedStash/SpringFacetSharedStashSoftCoreV2.d2i"));
        java.io.File tempFile = java.io.File.createTempFile("SpringFacetSharedStashSoftCoreV2", ".d2i");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), originalBytes);

        D2SharedStash stash = new D2SharedStashReader().readStash(tempFile.getAbsolutePath());

        // Five real tabs load; the stackable rune/gem tab (pane 5) and empty padding pane (pane 6)
        // are hidden, so the user sees no false "[LOADED PARTIALLY]" warning.
        assertEquals(7, stash.getPanes().size());
        assertEquals(5, stash.getVisibleTabCount());
        assertFalse(stash.hasVisibleIncompletePane());

        // Pane 2 is a normal visible tab that previously crashed the whole load. It now decodes fully,
        // including BOTH Spring Facets -- the one with flag 29 clear (the culprit) and the one set.
        assertFalse(stash.getPane(2).isIncomplete(), "the Spring Facet tab must load completely");
        assertEquals(75, stash.getPane(2).getItems().size());
        long springFacets = stash.getPane(2).getItems().stream()
                .filter(it -> it.getItemName() != null && it.getItemName().contains("Spring Facet"))
                .count();
        assertEquals(2, springFacets, "both Spring Facets (flag-29 set and clear) decode");

        // The stackable rune/gem tab is recovered here too (78 items), and the flag-29 fix is what let
        // its own facets decode; spot-check the same fix-per-family items as the fixture above.
        assertTrue(stash.getPane(5).isIncomplete());
        assertEquals(78, stash.getPane(5).getItems().size());
        assertTrue(pane5Has(stash, "Rune Pliers"));
        assertTrue(pane5Has(stash, "Key of Destruction"));

        // Save must not throw and must leave the file byte-for-byte unchanged (5 tabs reconstructed
        // losslessly, the 2 incomplete tabs written verbatim).
        stash.saveInternal(null);
        byte[] savedBytes = java.nio.file.Files.readAllBytes(tempFile.toPath());
        assertArrayEquals(originalBytes, savedBytes);
    }

    // A third real shared stash, from a non-DLC character -- confirming that none of the fixes
    // above (the partial-load fallback, or the byte-preserving save fix) are DLC-specific special
    // cases or otherwise change behavior for the common, non-DLC case: a real player asked
    // exactly this question after seeing a DLC stash's "[LOADED PARTIALLY]" indicator, since
    // non-DLC shared stashes never had the 2 tabs that got converted to hold runes/keys/gems.
    // Nothing in D2SharedStashReader/Writer checks for "is this DLC" at all -- it's driven purely
    // by what's actually in the file (here, exactly the original 3 panes, no converted tabs), so
    // there's no separate code path to verify; this is the same reader/writer, just confirming it
    // doesn't misfire on the simpler, far more common case.
    @Test
    public void thirdRealWorldStashFromNonDlcCharacterLoadsAndSavesFullyWithNoFalsePartialLoad() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        byte[] originalBytes = Resources.toByteArray(Resources.getResource("sharedStash/NonDlcSharedStashSoftCoreV2.d2i"));
        java.io.File tempFile = java.io.File.createTempFile("NonDlcSharedStashSoftCoreV2", ".d2i");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), originalBytes);

        D2SharedStash stash = new D2SharedStashReader().readStash(tempFile.getAbsolutePath());

        assertEquals(3, stash.getPanes().size());
        assertFalse(stash.isItemsIncomplete());
        // Non-DLC stashes have no converted tabs, so nothing is hidden -- all 3 tabs stay visible.
        assertEquals(3, stash.getVisibleTabCount());
        for (int i = 0; i < 3; i++) {
            assertFalse(stash.getPane(i).isIncomplete(), "pane " + i + " should have loaded completely");
            assertNull(stash.getPane(i).getOriginalBytes(), "pane " + i + " loaded fine, shouldn't need preserved bytes");
        }
        assertEquals(2, stash.getPane(0).getItems().size());
        assertEquals(2, stash.getPane(1).getItems().size());
        assertEquals(2, stash.getPane(2).getItems().size());
        assertTrue(stash.getItemList().stream().anyMatch(i -> "Tome of Town Portal".equals(i.getItemName())));
        assertTrue(stash.getItemList().stream().anyMatch(i -> "Tome of Identify".equals(i.getItemName())));
        // Also exercises the Gem Bag fix (D2Item.isLooseRuneOrGem's comment) against a third,
        // independent real file.
        assertTrue(stash.getItemList().stream().anyMatch(i -> i.getItemName() != null && i.getItemName().contains("Gem Bag")));

        stash.saveInternal(null); // must not throw

        D2SharedStash reread = new D2SharedStashReader().readStash(tempFile.getAbsolutePath());
        assertEquals(3, reread.getPanes().size());
        assertFalse(reread.isItemsIncomplete());
        assertEquals(6, reread.getItemList().size());
    }

    // A fourth real shared stash: a later snapshot of the same Reimagined mod stash as
    // ModernSharedStashSoftCoreV2.d2i above, after the player had added, among other things, a run
    // of Antidote Potions ("yps", itemtype "apot") to the first tab. That tab -- pane 0, an
    // ordinary tab, not one of the DLC-converted rune/gem/key tabs -- stopped loading partway
    // through: it decoded 14 items and then failed on the 15th with the same null-message
    // NullPointerException the character-file potion bugs produced, because "apot" was the one
    // simple beltable-potion class not yet covered by the byte-aligned padding-byte fix in
    // D2Item.readExtend() (isSimpleBeltablePotion()'s comment). Each byte-aligned antidote potion's
    // own end came out a byte short, so the first one desynced every item after it in the tab.
    // With "apot" folded into that fix, all 47 items in the first tab decode. Panes 5 and 6 are the
    // two DLC-converted tabs and stay incomplete-but-preserved exactly as in the second stash's
    // test above -- so the two hidden tabs still cause no visible "[LOADED PARTIALLY]" warning.
    @Test
    public void fourthRealWorldStashWithByteAlignedAntidotePotionsLoadsFirstTabFully() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String filename = new java.io.File(
                Resources.getResource("sharedStash/AntidotePotionSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();

        D2SharedStash stash = new D2SharedStashReader().readStash(filename);

        assertEquals(7, stash.getPanes().size());

        // The regression: the first tab used to stop at 14 items; it now loads all 47.
        assertFalse(stash.getPane(0).isIncomplete(), "first tab should load completely");
        assertEquals(47, stash.getPane(0).getItems().size());
        assertEquals(1938499, stash.getPane(0).getGold());
        assertTrue(stash.getPane(0).getItems().stream()
                        .anyMatch(it -> "Antidote Potion".equals(it.getItemName())),
                "first tab should contain the antidote potions that used to break it");

        // Panes 1-4 are ordinary tabs and load fully too.
        for (int i = 0; i <= 4; i++) {
            assertFalse(stash.getPane(i).isIncomplete(), "pane " + i + " should have loaded completely");
        }

        // Panes 5 and 6 are the two DLC-converted tabs: still incomplete, still byte-preserved, and
        // still hidden from the UI, so no visible tab reports a partial load.
        assertTrue(stash.isItemsIncomplete());
        assertEquals(5, stash.getVisibleTabCount());
        assertFalse(stash.hasVisibleIncompletePane());
        assertTrue(stash.getPane(5).isIncomplete());
        assertNotNull(stash.getPane(5).getOriginalBytes());
        assertTrue(stash.getPane(6).isIncomplete());
        assertNotNull(stash.getPane(6).getOriginalBytes());
    }

    // A fifth real shared stash: a later snapshot again, in which a Warlock (the Reimagined mod's
    // added class) grand charm granting "+1 to Chaos Skills" sat alone in the third tab. The charm
    // parsed fine but its skill-tab property rendered as "+1 to Unknown Tree (P 188)", because
    // getSkillTree() had no case for the tab the charm actually stores. item_addskill_tab stores the
    // GLOBAL "class * 8 + tab" index (Warlock is class 7, so its three tabs are 56/57/58), whereas
    // getSkillTree() had only the mod's generation-time sequential numbers (21/22/23 from
    // properties.txt's war-tab-rand), which no stored item ever carries. This reads the real charm
    // straight from the file and confirms the fix end to end: it now renders "Chaos Skills (Warlock
    // Only)", and nothing in the whole stash falls through to "Unknown Tree". (This is the check the
    // earlier, synthetic-only 21/22/23 tests could not make -- they hard-coded the wrong value and
    // "passed" against it; only a real charm's bytes could reveal the true stored index of 58.)
    @Test
    public void fifthRealWorldStashWithWarlockCharmRendersSkillTabName() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String filename = new java.io.File(
                Resources.getResource("sharedStash/WarlockCharmSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();

        D2SharedStash stash = new D2SharedStashReader().readStash(filename);

        List<String> allDumps = stash.getPanes().stream()
                .flatMap(p -> getItemDumps(p).stream())
                .collect(Collectors.toList());

        assertTrue(allDumps.stream().anyMatch(d -> d.contains("Chaos Skills (Warlock Only)")),
                "the real Warlock grand charm should render its skill tab name");
        assertTrue(allDumps.stream().noneMatch(d -> d.contains("Unknown Tree")),
                "no item should fall through to 'Unknown Tree (P 188)'");
    }

    // A sixth real shared stash, and the one that proved ./d2111/setitems.txt has to be kept in
    // sync with the installed mod rather than frozen. The user's log showed the usual null-message
    // NullPointerException out of D2Item.readExtend while opening this file: its first tab decoded
    // 15 items and then died. The culprit was item 15, a set item -- "Afterlife" (Hades'
    // Underworld, set id 192, a Demonhide Armor). The installed mod gives that row three rolled
    // threshold bonuses (aprop1a "nec", aprop2a "regen", aprop3a "regen-mana"), and this save
    // stores all three lists back-to-back: +1 Necromancer skills (item_addclassskills, class 2),
    // then hpregen, then manarecoverybonus. The shipped table still had the older row, which
    // declared threshold 3 only, so the parser read exactly one list and ended the item 52 bits
    // early -- desyncing all 33 items after it in the tab, which is what the reported crash
    // actually was. Syncing the table is the fix; the two directions this can break in, and why
    // probing the bitstream instead is not an option, are laid out in readExtend2's set-bonus
    // comment and in the second stash's test above.
    // This also pins down what the "declares a bonus" half of that rule has to be: the mod moved
    // "The River Stix"'s nofreeze bonus from aprop4a (with amin/amax) to aprop2a (a fixed value,
    // no amin/amax), so a rule keyed on "declares a *rolled* bonus" stopped finding the list this
    // file's own copy of that ring still stores. Both rings are in here, in the first tab.
    @Test
    public void afterlifeStashWithThreeSetBonusListsLoadsTheWholeFirstTab() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String filename = new java.io.File(
                Resources.getResource("sharedStash/AfterlifeSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();

        D2SharedStash stash = new D2SharedStashReader().readStash(filename);

        assertEquals(7, stash.getPanes().size());

        // The regression: the first tab used to stop at 15 items; it now loads all 49.
        assertFalse(stash.getPane(0).isIncomplete(), "first tab should load completely");
        assertEquals(49, stash.getPane(0).getItems().size());
        assertEquals(2500000, stash.getPane(0).getGold());
        assertTrue(stash.getPane(0).getItems().stream()
                        .anyMatch(it -> "Afterlife".equals(it.getItemName())),
                "first tab should contain the Afterlife that used to break it");
        // The item right after Afterlife in the file -- the first casualty of its 52-bit under-read,
        // and so the thing that proves the boundary is right rather than merely non-throwing.
        assertTrue(stash.getPane(0).getItems().stream()
                        .anyMatch(it -> "Cleglaw's Tooth".equals(it.getItemName())),
                "the item immediately after Afterlife should decode");
        // Afterlife's three stored bonus lists decode into their real stats, not just into the
        // right number of bits: +1 Necromancer skills is the first list's own value (class 2).
        D2Item lAfterlife = stash.getPane(0).getItems().stream()
                .filter(it -> "Afterlife".equals(it.getItemName()))
                .findFirst().orElseThrow(AssertionError::new);
        String lDump = D2ItemRenderer.itemDump(lAfterlife, true).replace("\r", "");
        assertTrue(lDump.contains("Necromancer"), "Afterlife's threshold-1 bonus (nec) should render: " + lDump);

        // All of the other visible tabs load fully too.
        for (int i = 0; i <= 4; i++) {
            assertFalse(stash.getPane(i).isIncomplete(), "pane " + i + " should have loaded completely");
        }
        assertEquals(39, stash.getPane(1).getItems().size());
        assertEquals(29, stash.getPane(2).getItems().size());

        // Panes 5 and 6 are the two DLC-converted tabs: still incomplete, still byte-preserved and
        // still hidden, so no visible tab reports a partial load.
        assertEquals(5, stash.getVisibleTabCount());
        assertFalse(stash.hasVisibleIncompletePane());
        assertTrue(stash.getPane(5).isIncomplete());
        assertNotNull(stash.getPane(5).getOriginalBytes());
        assertTrue(stash.getPane(6).isIncomplete());
        assertNotNull(stash.getPane(6).getOriginalBytes());
    }

    private List<String> getItemDumps(D2SharedStash.D2SharedStashPane pane) {
        return pane.getItems().stream()
                .map(it -> D2ItemRenderer.itemDump(it, true).replace("\r", ""))
                .collect(Collectors.toList());
    }
}
