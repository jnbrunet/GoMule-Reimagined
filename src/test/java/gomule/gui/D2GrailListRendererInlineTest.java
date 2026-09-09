package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailIndex;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import javax.swing.JLabel;
import javax.swing.JList;
import java.awt.Component;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Runewords tab draws each row as the item description itself rather than an icon and a name.
 * A runeword has no artwork of its own -- runes.txt gives it no invfile, so the icon a row showed
 * was its FIRST RUNE's sprite, identical for every word starting with the same rune -- and its
 * whole content is table data nobody recognises from a name, so browsing meant hovering every row
 * in turn.
 * <p>
 * Headless: JList and JLabel need no display, only a windowed frame would, so the renderer can be
 * driven directly here.
 */
public class D2GrailListRendererInlineTest {

    /**
     * Which rows draw their description inline -- runewords only. Uniques and set items have real
     * sprites, and 400+ full descriptions would be a wall of text to scroll rather than a list to
     * scan, so they keep the icon-and-name row. The Sets tab's plain-String group headers are not
     * entries at all.
     */
    @Test
    public void onlyRunewordRowsRenderTheirDescriptionInline() {
        D2TxtFile.constructTxtFiles("./d2111");
        assertTrue(D2GrailListRenderer.rendersDescriptionInline(rowOf(runeword("Bulwark"))));
        assertFalse(D2GrailListRenderer.rendersDescriptionInline(rowOf(byName(D2GrailKey.Type.UNIQUE, "Harlequin Crest"))));
        assertFalse(D2GrailListRenderer.rendersDescriptionInline(rowOf(byName(D2GrailKey.Type.SET, "Angelic Sickle"))));
        assertFalse(D2GrailListRenderer.rendersDescriptionInline("== Angelic Raiment ==   0 / 4"));
        assertFalse(D2GrailListRenderer.rendersDescriptionInline(null));
    }

    /**
     * The cell carries the same description the hover tooltip did -- base types, rune sequence,
     * level and every bonus line -- plus a "Missing" marker, and no icon (the rune sprite it used
     * to show was misleading anyway).
     */
    @Test
    public void aRunewordCellCarriesTheWholeDescription() {
        D2TxtFile.constructTxtFiles("./d2111");
        JList<Object> lList = new JList<Object>();
        D2GrailListRenderer lRenderer = new D2GrailListRenderer(null);
        D2GrailModel.Row lRow = rowOf(runeword("Bulwark"));

        Component lCell = lRenderer.getListCellRendererComponent(lList, lRow, 0, false, false);
        assertTrue(lCell instanceof JLabel, "the cell is a label: " + lCell);
        JLabel lLabel = (JLabel) lCell;
        String lText = lLabel.getText();

        assertTrue(lText.contains("Bulwark"), lText);
        assertTrue(lText.contains("Helm"), lText);
        assertTrue(lText.contains("Shael Rune (#13) + Io Rune (#16) + Sol Rune (#12)"), lText);
        assertTrue(lText.contains("Required Level: 35"), lText);
        assertTrue(lText.contains("+20% Faster Hit Recovery"), lText);
        assertTrue(lText.contains("Missing"), "an unfound word must still say so: " + lText);
        assertTrue(lText.endsWith("</html>"), lText);
        assertTrue(lLabel.getIcon() == null, "no icon on an inline row");
        assertTrue(lLabel.getToolTipText() == null, "the body IS the tooltip, so no popup on top of it");
    }

    /**
     * Each runeword keeps its OWN label, and a unique still goes through the renderer's single
     * shared one. This is what makes a list of 208 full descriptions usable rather than a
     * half-second freeze on every filter click: Swing caches a label's parsed HTML on the label
     * itself, keyed by its text, so one shared label re-parses every row's HTML each time JList
     * measures the list -- and it measures every row, not just the visible ones. Measured over the
     * real 208: ~0.4s per relayout shared, ~2ms with a label each.
     */
    @Test
    public void everyRunewordKeepsItsOwnLabelSoSwingCanCacheItsParsedHtml() {
        D2TxtFile.constructTxtFiles("./d2111");
        JList<Object> lList = new JList<Object>();
        D2GrailListRenderer lRenderer = new D2GrailListRenderer(null);

        D2GrailModel.Row lBulwark = rowOf(runeword("Bulwark"));
        D2GrailModel.Row lInsight = rowOf(runeword("Insight"));

        Component lFirst = lRenderer.getListCellRendererComponent(lList, lBulwark, 0, false, false);
        Component lOther = lRenderer.getListCellRendererComponent(lList, lInsight, 1, false, false);
        Component lFirstAgain = lRenderer.getListCellRendererComponent(lList, lBulwark, 0, true, false);

        assertNotSame(lFirst, lOther, "two runewords must not share one label");
        assertSame(lFirst, lFirstAgain, "the same runeword must get its label back, HTML already parsed");
        assertTrue(((JLabel) lFirstAgain).getText().contains("Bulwark"),
                "and it must still hold its own description");

        Component lUnique = lRenderer.getListCellRendererComponent(
                lList, rowOf(byName(D2GrailKey.Type.UNIQUE, "Harlequin Crest")), 2, false, false);
        assertSame(lRenderer, lUnique, "a unique still renders through the shared renderer");
    }

    // --------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------

    private static D2GrailEntry runeword(String pName) {
        return byName(D2GrailKey.Type.RUNEWORD, pName);
    }

    private static D2GrailEntry byName(D2GrailKey.Type pType, String pName) {
        for (D2GrailEntry lEntry : D2GrailIndex.getEntries()) {
            if (lEntry.getKey().getType() == pType && pName.equals(lEntry.getDisplayName())) {
                return lEntry;
            }
        }
        throw new AssertionError(pName + " not found among the indexed " + pType + " entries");
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
