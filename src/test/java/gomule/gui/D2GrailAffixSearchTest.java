package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search box matches what an item DOES, not only what it is called: typing "increased attack
 * speed" finds every item that grants it, instead of the none that happen to be named that.
 * <p>
 * Lives in gomule.gui because it wires the real provider -- D2GrailListRenderer.searchTextFor,
 * which renders an entry's whole table-sourced description -- into the (Swing-free) model. The
 * model's own D2GrailModelTest covers the mechanism with a stub provider instead.
 */
public class D2GrailAffixSearchTest {

    /**
     * The headline case, on every tab: an affix nobody's item is NAMED after still finds the items
     * that grant it, and every row that comes back really does carry it.
     */
    @Test
    public void searchingAnAffixFindsTheItemsThatGrantIt() {
        D2TxtFile.constructTxtFiles("./d2111");
        for (D2GrailKey.Type lTab : D2GrailKey.Type.values()) {
            D2GrailModel lModel = modelFor(lTab);
            lModel.setSearchText("increased attack speed");
            List<D2GrailModel.Row> lRows = lModel.getRows();

            assertTrue(lRows.size() > 10, lTab + " should find plenty: " + lRows.size());
            assertTrue(lRows.size() < lModel.getTabProgress().getTotal(),
                    lTab + " must still be a filter, not everything: " + lRows.size());
            for (D2GrailModel.Row lRow : lRows) {
                assertTrue(descriptionOf(lRow.getEntry()).contains("increased attack speed"),
                        lRow.getEntry().getDisplayName() + " does not actually grant it");
            }
        }
    }

    /**
     * A property that only exists on a handful of entries, to prove the match is on the rendered
     * text rather than something broad: "Meditation" is an aura three runewords grant and no
     * unique or set item does, and no entry anywhere is named after it.
     */
    @Test
    public void aRareAffixNarrowsToJustTheEntriesThatHaveIt() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lRunewords = modelFor(D2GrailKey.Type.RUNEWORD);
        lRunewords.setSearchText("meditation aura");
        List<String> lNames = names(lRunewords.getRows());
        assertTrue(lNames.contains("Insight"), lNames.toString());
        assertFalse(lNames.isEmpty(), "the aura words must be found");

        D2GrailModel lUniques = modelFor(D2GrailKey.Type.UNIQUE);
        lUniques.setSearchText("meditation aura");
        assertTrue(lUniques.getRows().isEmpty(), "no unique grants it: " + names(lUniques.getRows()));
    }

    /**
     * Searching by name still works exactly as before -- both the entry's own name and its base
     * item's, which is what the box did before affixes were added to it.
     */
    @Test
    public void nameAndBaseItemSearchStillWork() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = modelFor(D2GrailKey.Type.UNIQUE);

        lModel.setSearchText("Harlequin");
        assertTrue(names(lModel.getRows()).contains("Harlequin Crest"), "by its own name");

        lModel.setSearchText("shako");
        assertTrue(names(lModel.getRows()).contains("Harlequin Crest"), "by its base item's name");
    }

    /**
     * The affix text is the entry's DEFINITION, from the tables -- never one rolled copy's numbers
     * -- so the same query returns the same rows whether or not the player owns the item. Asserted
     * as "no findings are consulted": the provider is handed an entry, and the model caches the
     * result per entry, so a search cannot depend on what is open.
     */
    @Test
    public void anAffixSearchDoesNotDependOnWhatHasBeenFound() {
        D2TxtFile.constructTxtFiles("./d2111");
        D2GrailModel lModel = modelFor(D2GrailKey.Type.UNIQUE);
        lModel.setSearchText("faster cast rate");
        int lBefore = lModel.getRows().size();

        lModel.setFindings(new java.util.HashMap<D2GrailKey, gomule.grail.D2GrailFinding>());
        assertTrue(lBefore == lModel.getRows().size(), "findings must not change what an affix matches");
        assertTrue(lBefore > 0, "and it must actually match something");
    }

    private static D2GrailModel modelFor(D2GrailKey.Type pTab) {
        D2GrailModel lModel = new D2GrailModel();
        lModel.setTab(pTab);
        lModel.setSearchTextProvider(D2GrailListRenderer::searchTextFor);
        return lModel;
    }

    private static String descriptionOf(D2GrailEntry pEntry) {
        return D2GrailListRenderer.searchTextFor(pEntry)
                .replaceAll("<[^>]*>", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static List<String> names(List<D2GrailModel.Row> pRows) {
        List<String> lNames = new ArrayList<String>();
        for (D2GrailModel.Row lRow : pRows) {
            lNames.add(lRow.getEntry().getDisplayName());
        }
        return lNames;
    }
}
