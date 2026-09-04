package gomule.grail;

import com.google.common.io.Resources;
import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2s.D2Character;
import gomule.gui.D2ItemList;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs D2GrailScanner over the same real, modded fixtures D2CharacterTest and D2ItemImagePanelTest
 * already use -- see those files' comments for what each one carries. Every assertion below was
 * derived from actually running the scanner against these fixtures (not guessed), so it also
 * doubles as a pin against a regression in either the scanner or the index's key computation --
 * see D2GrailIndexTest's id-drift test for why that matters here specifically: several of the
 * uniques below (e.g. "Heaven Facet") only land on the right key because of that fix.
 */
public class D2GrailScannerTest {

    // Same three paladin snapshots D2CharacterTest/D2ItemImagePanelTest use:
    //   - pally3.d2s: the socketed unique scepter "Hand of Blessed Light" with two socketed unique
    //     "Heaven Facet" jewels, plus the runewords "Call to Arms" and "Heart of the Oak".
    //   - pally4.d2s: the fullest stash, carrying the set items "Immortal King's Stone Crusher"
    //     and "Animal Kinship".
    //   - pally5.d2s: a second "Hand of Blessed Light", this one with 5 sockets (4 Heaven Facets
    //     plus one Colossal Jewel), and the untranslated "cs2" charm (a unique, displayed as
    //     "Renewed Black Cleft").
    private static final String[] D2S_FIXTURES = {
            "charFiles/pally3.d2s", "charFiles/pally4.d2s", "charFiles/pally5.d2s",
    };

    @Test
    public void scanningRealModdedCharactersThrowsNothing() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        List<D2ItemList> lLists = loadD2sFixtures();

        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(lLists);

        assertFalse(lFindings.isEmpty(), "expected the scan to find something in these fixtures");
    }

    @Test
    public void handOfBlessedLightIsFoundAcrossAllThreeFiles() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(loadD2sFixtures());

        // "Hand of Blessed Light" is uniqueitems.txt *ID 146 -- an early row, well before any of
        // the section-header rows that make *ID drift away from the real searchByID id, so its
        // "*ID" column and its real key still happen to agree here.
        D2GrailFinding lHbl = lFindings.get(D2GrailKey.unique(146));
        assertNotNull(lHbl, "Hand of Blessed Light not found by the scanner");
        assertEquals(3, lHbl.getCopies(), "one copy in each of pally3/4/5");
        assertEquals(3, lHbl.getFileNames().size(), "3 distinct full paths -- pally3/4/5.d2s on disk");
        assertNotNull(lHbl.getFirstItem());
        assertEquals("Hand of Blessed Light", lHbl.getFirstItem().getItemName());

        // All three fixtures share the SAME in-game character name ("pally" -- see
        // D2CharacterTest), so D2GrailScanner.displayFileName()'s D2Character rule (getCharName()
        // + ".d2s", precedented by D2ItemListAll.getFilename(D2Item)) collapses all three into one
        // identical display string. Real, expected behavior, not a bug: the 3 distinct full paths
        // above are what actually matters for uniqueness/double-click, the display form is only
        // ever a label.
        assertEquals(java.util.Collections.singleton("pally.d2s"), lHbl.getFileDisplayNames());
    }

    /**
     * The dedicated recursion test: a unique jewel (Heaven Facet) socketed into another item never
     * appears in that file's own getItemList() -- only inside the carrying item's
     * getiSocketedItems() -- so this can only pass if D2GrailScanner actually walks sockets.
     * <p>
     * Also the concrete regression pin for the id-drift fix in D2GrailIndex: the real unique_id
     * D2Item.getUniqueID() returns for this jewel is 1400 (confirmed by running the scanner), NOT
     * uniqueitems.txt's own "*ID" column text for that row (1398, which belongs to a different
     * row, "Adamantine Facet" -- see D2GrailIndexTest). If D2GrailIndex ever regressed back to
     * keying uniques by raw "*ID" text, this assertion would fail because key 1400 would resolve
     * to nothing (or to the wrong entry).
     */
    @Test
    public void heavenFacetSocketedInsideHandOfBlessedLightIsFoundByRecursion() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(loadD2sFixtures());

        D2GrailFinding lHeavenFacet = lFindings.get(D2GrailKey.unique(1400));
        assertNotNull(lHeavenFacet, "Heaven Facet (only ever found socketed) not found by the scanner");
        // 2 sockets in pally3's copy + 4 in pally5's 5-socket copy, plus whatever loose copies (if
        // any) sit unsocketed elsewhere in these stashes -- observed running the scanner: 11.
        assertEquals(11, lHeavenFacet.getCopies());

        D2GrailEntry lEntry = D2GrailIndex.getByKey(D2GrailKey.unique(1400));
        assertNotNull(lEntry);
        assertEquals("ÿc4Heaven Facet", lEntry.getDisplayName());
    }

    @Test
    public void callToArmsAndHeartOfTheOakRunewordsAreFoundByName() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(loadD2sFixtures());

        D2GrailFinding lCallToArms = lFindings.get(D2GrailKey.runeword("Call to Arms"));
        assertNotNull(lCallToArms, "Call to Arms not found by the scanner");
        assertEquals(3, lCallToArms.getCopies());

        D2GrailFinding lHeartOfTheOak = lFindings.get(D2GrailKey.runeword("Heart of the Oak"));
        assertNotNull(lHeartOfTheOak, "Heart of the Oak not found by the scanner");
        assertEquals(2, lHeartOfTheOak.getCopies());

        // Both are found under the runeword's OWN identity, not as their (very different) base
        // weapons -- confirms getRuneWordIndex() is what identify() actually keys off of.
        assertEquals("Call to Arms", lCallToArms.getFirstItem().getItemName());
    }

    @Test
    public void setItemsAreFoundWithTheCorrectSetName() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(loadD2sFixtures());

        D2GrailFinding lStoneCrusher = lFindings.get(D2GrailKey.set(75));
        assertNotNull(lStoneCrusher, "Immortal King's Stone Crusher not found by the scanner");
        assertEquals(1, lStoneCrusher.getCopies());
        D2GrailEntry lStoneCrusherEntry = D2GrailIndex.getByKey(D2GrailKey.set(75));
        assertEquals("Immortal King", lStoneCrusherEntry.getSetName());

        D2GrailFinding lAnimalKinship = lFindings.get(D2GrailKey.set(147));
        assertNotNull(lAnimalKinship, "Animal Kinship not found by the scanner");
        assertEquals(1, lAnimalKinship.getCopies());
        D2GrailEntry lAnimalKinshipEntry = D2GrailIndex.getByKey(D2GrailKey.set(147));
        assertEquals("Nature's Grove", lAnimalKinshipEntry.getSetName());
    }

    /**
     * pally5.d2s's "cs2" charm has no localized string of its own (D2Item.java's own
     * getTranslationOrNull fallback -- see CLAUDE.md) but is still a real unique underneath, and
     * the scanner must find it like any other. It is also a non-Chronicle entry
     * (disableChronicle=1), which the scan must still report -- filtering by Chronicle scope is a
     * display-layer concern, not the scanner's.
     */
    @Test
    public void untranslatedUniqueCharmIsStillFoundAndIsNonChronicle() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(loadD2sFixtures());

        D2GrailEntry lRenewedBlackCleft = findByDisplayName("ÿc4Renewed Black Cleft");
        assertNotNull(lRenewedBlackCleft, "the cs2 unique charm not found in the index");
        assertFalse(lRenewedBlackCleft.isChronicle());

        D2GrailFinding lFinding = lFindings.get(lRenewedBlackCleft.getKey());
        assertNotNull(lFinding, "the cs2 unique charm not found by the scanner");
        assertEquals(1, lFinding.getCopies());
    }

    /**
     * The whole reason D2GrailScanner takes D2ItemList directly instead of D2ItemListAll: a .d2i
     * shared stash is invisible to D2ItemListAll (its constructor only aggregates
     * getCharList()/getStashList() -- see D2GrailScanner's class javadoc), so scanning a
     * D2SharedStash is the one case that would silently regress if a future change routed the
     * scanner through D2ItemListAll instead. Reuses the same fixture (and the same real unique,
     * "Oakheart") that D2StashTest already trusts.
     */
    @Test
    public void sharedStashD2iFilesAreScannedDirectly() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        String lStashFile = new File(
                Resources.getResource("sharedStash/ModernSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();
        D2SharedStash lStash = new D2SharedStashReader().readStash(lStashFile);

        List<D2ItemList> lLists = new ArrayList<>();
        lLists.add(lStash);
        Map<D2GrailKey, D2GrailFinding> lFindings = D2GrailScanner.scan(lLists);

        assertFalse(lFindings.isEmpty(), "expected findings from a real shared stash");

        D2GrailEntry lOakheart = findByDisplayName("Oakheart");
        assertNotNull(lOakheart, "Oakheart not found in the index");
        assertTrue(lOakheart.getKey().getType() == D2GrailKey.Type.UNIQUE);
        assertNotNull(lFindings.get(lOakheart.getKey()), "Oakheart not found by the scanner in the shared stash");

        // Set items and runewords are also visible straight from a .d2i pane, not just uniques.
        assertNotNull(lFindings.get(D2GrailKey.set(75)), "Immortal King's Stone Crusher missing from the shared stash scan");
        assertNotNull(lFindings.get(D2GrailKey.runeword("War")), "runeword 'War' missing from the shared stash scan");

        // D2SharedStash has no type-specific short-name rule (unlike D2Character/D2Stash), so it
        // falls back to the path's own basename -- still short, never the directory-laden full path.
        D2GrailFinding lOakheartFinding = lFindings.get(lOakheart.getKey());
        assertEquals(java.util.Collections.singleton("ModernSharedStashSoftCoreV2.d2i"),
                lOakheartFinding.getFileDisplayNames());
        // The full path is exactly what D2FileManager.iItemLists/iOpenWindows are keyed by, and
        // therefore exactly what D2FileManager.focusFileWindow() (the double-click target) needs --
        // proving getFileNames() still holds it, unchanged, is the headless proxy for "double-click
        // still works" that an actual Swing click can't be given here.
        assertEquals(java.util.Collections.singleton(lStashFile), lOakheartFinding.getFileNames());
    }

    /**
     * The regression pin for the "Found in:" full-path leak: D2FileManager.focusFileWindow()
     * (double-click) needs the exact full path D2ItemList.getFilename() returns -- its
     * iItemLists/iOpenWindows are keyed by that string -- so D2GrailScanner.displayFileName() must
     * never be used for the STORED value, only ever for the separately-tracked display one. This
     * pins the shortening rule directly for all three concrete D2ItemList types (plan section 3.3's
     * "Found in: Barbarian.d2s", not a full Windows/Unix path):
     * D2Character -> getCharName() + ".d2s", D2Stash (.d2x) -> getFileNameEnd(), and a
     * D2SharedStash (.d2i, no type-specific short form) -> the path's own basename.
     */
    @Test
    public void displayFileNameShortensEachConcreteListTypeWithoutTouchingTheStoredFullPath() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");

        // .d2s
        String lD2sPath = new File(Resources.getResource("charFiles/pally3.d2s").toURI()).getAbsolutePath();
        D2Character lCharacter = new D2Character(lD2sPath);
        assertEquals("pally.d2s", D2GrailScanner.displayFileName(lCharacter, lCharacter.getFilename()));
        assertEquals(lD2sPath, lCharacter.getFilename(), "the full path itself must be untouched");

        // .d2i
        String lD2iPath = new File(
                Resources.getResource("sharedStash/ModernSharedStashSoftCoreV2.d2i").toURI())
                .getAbsolutePath();
        D2SharedStash lSharedStash = new D2SharedStashReader().readStash(lD2iPath);
        assertEquals("ModernSharedStashSoftCoreV2.d2i",
                D2GrailScanner.displayFileName(lSharedStash, lSharedStash.getFilename()));
        assertEquals(lD2iPath, lSharedStash.getFilename());

        // .d2x -- a minimal synthetic ATMA file (just the "D2X" magic + a deliberately-wrong
        // checksum, which D2Stash.readAtmaItems() tolerates by simply reading zero items rather
        // than throwing -- see its own source): only getFilename()/getFileNameEnd() matter here,
        // not any actual item content.
        File lD2xFile = File.createTempFile("scanner-display-name", ".d2x");
        lD2xFile.deleteOnExit();
        java.nio.file.Files.write(lD2xFile.toPath(), new byte[]{'D', '2', 'X', 0, 0, 0, 0, 0, 0, 0, 0});
        gomule.d2x.D2Stash lStash = new gomule.d2x.D2Stash(lD2xFile.getAbsolutePath());
        assertEquals(lStash.getFileNameEnd(), D2GrailScanner.displayFileName(lStash, lStash.getFilename()));
        assertEquals(lD2xFile.getAbsolutePath(), lStash.getFilename());
        // The temp file's own full path is longer than its basename (it lives under the OS temp
        // directory, not in the working directory) -- proving real shortening happened here too,
        // not just a coincidental no-op equality.
        assertTrue(lStash.getFilename().length() > D2GrailScanner.displayFileName(lStash, lStash.getFilename()).length());
    }

    @Test
    public void nullAndEmptyInputsAreTolerated() {
        assertTrue(D2GrailScanner.scan(null).isEmpty());
        assertTrue(D2GrailScanner.scan(new ArrayList<D2ItemList>()).isEmpty());

        List<D2ItemList> lWithNull = new ArrayList<>();
        lWithNull.add(null);
        assertTrue(D2GrailScanner.scan(lWithNull).isEmpty());
    }

    private static List<D2ItemList> loadD2sFixtures() throws Exception {
        List<D2ItemList> lLists = new ArrayList<>();
        for (String lFixture : D2S_FIXTURES) {
            // The item format version is a STATIC on D2Item, set only when a D2Character reads a
            // header (see CLAUDE.md) -- constructing the character is what makes getUniqueID() /
            // getSetID() / getRuneWordIndex() below real instead of garbage.
            D2Character lCharacter = new D2Character(
                    new File(Resources.getResource(lFixture).toURI()).getAbsolutePath());
            assertFalse(lCharacter.isItemsIncomplete(), lFixture + ": " + lCharacter.getItemsIncompleteReason());
            lLists.add(lCharacter);
        }
        return lLists;
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
