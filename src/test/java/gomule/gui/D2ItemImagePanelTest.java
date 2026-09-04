package gomule.gui;

import com.google.common.io.Resources;
import gomule.d2s.D2Character;
import gomule.item.D2Item;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import randall.d2files.D2TxtFile;

import java.awt.Image;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Covers the one genuinely fragile part of the new .d2x stash preview: resolving an item's
 * inventory sprite. The Swing painting itself is not tested (it needs no test -- it is plain
 * drawImage arithmetic), but D2ItemImagePanel.imageFor() is the guard that keeps a missing or
 * unresolvable sprite from taking the whole stash view down, and that guard is worth pinning.
 * <p>
 * Everything here runs headless: no window, no JFrame, no setVisible. D2ImageCache decodes DC6
 * files into BufferedImages, which needs no display.
 * <p>
 * NOTE ON THE WORKING DIRECTORY: D2ImageCache builds RELATIVE paths ("resources/gfx/<x>.dc6"), so
 * these tests only exercise the real sprite lookup when the test JVM's working directory is the
 * project root. Gradle's Test task defaults its workingDir to the project directory, so that holds
 * here; assertWorkingDirectoryIsProjectRoot() below fails loudly (rather than degrading into a
 * vacuous "everything returned null, nothing threw" pass) if that ever changes.
 */
public class D2ItemImagePanelTest {

    // Real, current Reimagined (D2RMM) saves already used as parsing fixtures by D2CharacterTest.
    // Picked for coverage of what the sprite lookup actually has to survive rather than for size:
    //   - pally4.d2s: the fullest paladin stash (196 items) -- quest items, set items, worldstone
    //     shards, i.e. the widest spread of modded item codes in the fixture set.
    //   - pally3.d2s: 171 items including the socketed flag-29 unique scepter and its Heaven Facet
    //     jewels, so getiSocketedItems() below actually has sockets to walk.
    //   - pally5.d2s: carries the "cs2" charm that has NO localized string at all, plus a Colossal
    //     Jewel -- the closest thing the fixtures have to "a brand new modded item the image lookup
    //     has never seen", which is exactly the fallback path this panel exists to survive.
    private static final String[] FIXTURES = {
            "charFiles/pally4.d2s",
            "charFiles/pally3.d2s",
            "charFiles/pally5.d2s",
    };

    /**
     * Every item of several real modded characters -- including everything socketed into them and
     * every mercenary item -- must resolve through imageFor() without throwing.
     * <p>
     * Observed when this test was written: 692 items examined across the three fixtures (264 +
     * 239 + 189, sockets and mercenary gear included), and all 692 of them -- 100% -- resolved a
     * non-null Image. The assertion below is deliberately looser than that
     * -- it only demands the large majority -- because the point being locked in is "no item, not
     * even an untranslated modded one, can throw here", not "the shipped resources/gfx happens to
     * contain a sprite for every item that exists". A future modded item with a genuinely missing
     * .dc6 should make the panel fall back, not make this test red.
     */
    @Test
    public void everyItemOfRealModdedCharactersResolvesAnImageWithoutThrowing() throws Exception {
        assertWorkingDirectoryIsProjectRoot();
        D2TxtFile.constructTxtFiles("./d2111");

        int examined = 0;
        int withImage = 0;

        for (String fixture : FIXTURES) {
            // The item format version is a STATIC on D2Item, set only when a D2Character reads a
            // header (see CLAUDE.md). Constructing the character first is what makes the items
            // below real rather than garbage.
            D2Character character = new D2Character(
                    new File(Resources.getResource(fixture).toURI()).getAbsolutePath());

            List<D2Item> items = new ArrayList<>();
            collect(character.getItemList(), items);
            for (int i = 0; i < character.getMercItemNr(); i++) {
                collect(java.util.Collections.singletonList(character.getMercItem(i)), items);
            }

            int fixtureWithImage = 0;
            for (D2Item item : items) {
                Image image;
                try {
                    image = D2ItemImagePanel.imageFor(item);
                } catch (Throwable t) {
                    // imageFor() swallows RuntimeException and Error; anything reaching here is a
                    // real regression in the guard, so report exactly which item broke it.
                    fail("imageFor threw for item '" + item.getItemName() + "' (type="
                            + item.getItem_type() + ", image=" + item.get_image() + ") in "
                            + fixture + ": " + t, t);
                    throw new AssertionError("unreachable");
                }
                if (image != null) {
                    fixtureWithImage++;
                }
            }

            System.out.println("[imageFor] " + fixture + ": items=" + items.size()
                    + " withImage=" + fixtureWithImage);
            examined += items.size();
            withImage += fixtureWithImage;
        }

        System.out.println("[imageFor] TOTAL: items=" + examined + " withImage=" + withImage);

        assertTrue(examined > 400, "expected the fixtures to yield a few hundred items, got " + examined);
        // Proves the resources/gfx path really works and this test is not vacuous.
        assertTrue(withImage > 0, "no item at all resolved a sprite -- the resources/gfx lookup is broken "
                + "(is the test working directory still the project root?)");
        // Faithful to what was actually observed (100%), with headroom for a future item whose
        // sprite is genuinely absent from resources/gfx.
        assertTrue(withImage >= examined * 9 / 10,
                "expected the large majority of items to resolve a sprite, got " + withImage + " of " + examined);
    }

    /**
     * The documented contract: no selection means no image, and no NullPointerException either.
     */
    @Test
    public void imageForNullItemReturnsNull() {
        assertNull(D2ItemImagePanel.imageFor(null));
    }

    /**
     * The robustness the panel advertises, on the exact failure D2ImageCache actually has:
     * getDC6Image() dereferences pItem.getItemName() with no null check, so an item whose name
     * never got translated blows up inside the cache. imageFor() must absorb that.
     * <p>
     * D2Item is a plain non-final class with non-final getters, so Mockito can stub it directly
     * without going anywhere near its (bitstream-driven) constructor.
     */
    @Test
    public void imageForSwallowsNullNameNullPointerException() {
        D2Item item = Mockito.mock(D2Item.class);
        Mockito.when(item.get_image()).thenReturn(null);
        Mockito.when(item.getItem_type()).thenReturn("xyz");
        Mockito.when(item.getItemName()).thenReturn(null); // what D2ImageCache dereferences

        assertNull(D2ItemImagePanel.imageFor(item));
    }

    /**
     * Same guard, but for an item that throws outright rather than merely returning null -- the
     * shape a future accessor-level failure would take. It must still degrade to "no image".
     */
    @Test
    public void imageForSwallowsAThrowingAccessor() {
        D2Item item = Mockito.mock(D2Item.class);
        Mockito.when(item.get_image()).thenThrow(new IllegalStateException("boom"));

        assertNull(D2ItemImagePanel.imageFor(item));
    }

    /**
     * An item whose sprite file simply does not exist under resources/gfx must not throw either.
     * This is the "brand new modded item, art not shipped" case the panel's fallback exists for.
     */
    @Test
    public void imageForAnItemWithAnUnknownSpriteDoesNotThrow() {
        D2Item item = Mockito.mock(D2Item.class);
        Mockito.when(item.get_image()).thenReturn("no_such_sprite_for_a_reimagined_item");
        Mockito.when(item.getItem_type()).thenReturn("zzz");
        Mockito.when(item.getItemName()).thenReturn("Some Untranslated Reimagined Item");

        D2ItemImagePanel.imageFor(item); // must not throw; a null or a fallback sprite are both fine
    }

    private static void collect(List<D2Item> source, List<D2Item> target) {
        if (source == null) {
            return;
        }
        for (D2Item item : source) {
            if (item == null) {
                continue;
            }
            target.add(item);
            collect(item.getiSocketedItems(), target);
        }
    }

    // D2ImageCache resolves "resources/gfx/..." relative to the working directory, so a test run
    // from anywhere else would silently resolve nothing and still pass. Anchor on d2111/ and
    // resources/gfx/, the two directories the fixtures and the sprite lookup both need.
    private static void assertWorkingDirectoryIsProjectRoot() {
        File workingDir = new File(".").getAbsoluteFile();
        System.out.println("[imageFor] working directory = " + workingDir.getParent());
        assertTrue(new File("d2111").isDirectory(),
                "expected ./d2111 -- test working directory is not the project root: " + workingDir);
        assertNotNull(new File("resources" + File.separator + "gfx").listFiles(),
                "expected ./resources/gfx -- test working directory is not the project root: " + workingDir);
    }
}
