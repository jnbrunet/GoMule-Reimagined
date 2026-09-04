package gomule.gui;

import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailFinding;
import gomule.grail.D2GrailFirstSeenStore;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import gomule.item.D2PropCollection;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Renders one row of the Holy Grail list: a colour icon + the item's quality-coloured name +
 * "Found" (with copy count/files) for a found entry, or a grey icon + grey name + "Missing" for
 * one that isn't (plan section 7). Also renders the Sets tab's plain-String set headers
 * ("== Angelic Raiment == 2 / 4", plan section 3.2) that {@code D2ViewGrail} mixes into the same
 * list model, so the whole list only needs one renderer.
 * <p>
 * Every failure mode this touches -- a missing .dc6, an entry with no invfile, a finding with no
 * first item -- degrades to "no icon" rather than throwing, the same guard D2ItemImagePanel.imageFor
 * uses (not reused directly: that method is private to gomule.gui.D2ItemImagePanel and this class
 * has a second case that method never needs -- rendering a MISSING entry, which has no D2Item at
 * all, only an invfile string, so it must call D2ImageCache.getDC6Image(String) instead).
 */
public class D2GrailListRenderer extends JLabel implements ListCellRenderer<Object> {

    private static final long serialVersionUID = 1L;

    // Plan section 7: gold for unique/runeword, green for set -- matches D2Item.getItemColor()
    // for a real found item; used here only when no real D2Item is available to ask directly (a
    // missing entry has none, and a found entry can in principle have recorded a null first item --
    // see D2GrailFinding.record's contract).
    private static final Color UNIQUE_OR_RUNEWORD_COLOR = new Color(255, 222, 173);
    private static final Color SET_COLOR = Color.green.darker();
    private static final Color MISSING_COLOR = Color.GRAY;

    public D2GrailListRenderer() {
        setOpaque(true);
        setIconTextGap(8);
        setVerticalAlignment(TOP);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> pList, Object pValue, int pIndex,
                                                    boolean pIsSelected, boolean pCellHasFocus) {
        setFont(pList.getFont());
        setBackground(pIsSelected ? pList.getSelectionBackground() : pList.getBackground());

        if (pValue instanceof String) {
            renderSetHeader(pList, (String) pValue, pIsSelected);
            return this;
        }

        renderRow(pList, (D2GrailModel.Row) pValue, pIsSelected);
        return this;
    }

    private void renderSetHeader(JList<?> pList, String pHeaderText, boolean pIsSelected) {
        setIcon(null);
        setToolTipText(null);
        setFont(pList.getFont().deriveFont(java.awt.Font.BOLD));
        setForeground(pIsSelected ? pList.getSelectionForeground() : pList.getForeground());
        setText(pHeaderText);
    }

    private void renderRow(JList<?> pList, D2GrailModel.Row pRow, boolean pIsSelected) {
        D2GrailEntry lEntry = pRow.getEntry();
        D2GrailFinding lFinding = pRow.getFinding();
        boolean lFound = pRow.isFound();

        setIcon(iconFor(lEntry, lFinding, lFound));

        String lName = D2ItemRenderer.stripColorCodes(nullToEmpty(lEntry.getDisplayName()));
        if (!lEntry.isChronicle()) {
            // Plan section 7: entries only visible because "Include non-Chronicle items" is
            // checked are marked with a trailing "*" and a tooltip explaining why.
            lName = lName + " *";
            setToolTipText("Not part of the in-game Chronicle");
        } else {
            setToolTipText(null);
        }

        Color lNameColor = pIsSelected ? pList.getSelectionForeground()
                : lFound ? qualityColor(lEntry, lFinding) : MISSING_COLOR;

        StringBuilder lHtml = new StringBuilder("<html>");
        lHtml.append("<font color='#").append(toHex(lNameColor)).append("'>")
                .append(escapeHtml(lName)).append("</font><br>");

        String lSubtitle = subtitle(lEntry);
        if (!lSubtitle.isEmpty()) {
            lHtml.append("<font size='-2' color='#808080'>").append(escapeHtml(lSubtitle)).append("</font><br>");
        }

        if (lFound) {
            lHtml.append("<font size='-2'>Found");
            if (lFinding.getCopies() > 1) {
                lHtml.append(" x").append(lFinding.getCopies());
            }
            String lFiles = joinFileNames(lFinding);
            if (!lFiles.isEmpty()) {
                lHtml.append(" (").append(escapeHtml(lFiles)).append(")");
            }
            lHtml.append("</font>");
        } else {
            lHtml.append("<font size='-2' color='#808080'>Missing</font>");
        }
        lHtml.append("</html>");

        setText(lHtml.toString());
        setForeground(pIsSelected ? pList.getSelectionForeground() : pList.getForeground());
    }

    /**
     * "BaseItemName - Tier" (plan section 3.1, e.g. "Shako - Elite"), tier omitted for a NORMAL or
     * tierless (runeword) entry -- matching the sketch, which only ever shows a tier suffix for
     * Exceptional/Elite bases.
     */
    private static String subtitle(D2GrailEntry pEntry) {
        String lBaseName = nullToEmpty(pEntry.getBaseItemName());
        if (pEntry.getTier() == D2GrailEntry.Tier.EXCEPTIONAL) {
            return lBaseName.isEmpty() ? "Exceptional" : lBaseName + " - Exceptional";
        }
        if (pEntry.getTier() == D2GrailEntry.Tier.ELITE) {
            return lBaseName.isEmpty() ? "Elite" : lBaseName + " - Elite";
        }
        return lBaseName;
    }

    /**
     * The short, display form of every file this entry was found in (plan section 3.3:
     * "Found in: Barbarian.d2s", never the full path -- D2GrailFinding.getFileNames() holds that,
     * but it exists only for D2FileManager.focusFileWindow()'s double-click lookup, never for
     * showing to the user).
     */
    private static String joinFileNames(D2GrailFinding pFinding) {
        StringBuilder lOut = new StringBuilder();
        Iterator<String> lIt = pFinding.getFileDisplayNames().iterator();
        while (lIt.hasNext()) {
            lOut.append(lIt.next());
            if (lIt.hasNext()) {
                lOut.append(", ");
            }
        }
        return lOut.toString();
    }

    private static Color qualityColor(D2GrailEntry pEntry, D2GrailFinding pFinding) {
        D2Item lItem = pFinding.getFirstItem();
        if (lItem != null) {
            try {
                return lItem.getItemColor();
            } catch (RuntimeException pEx) {
                // Fall through to the entry-type default below -- a broken accessor on one real
                // item must not stop the whole row from rendering.
            }
        }
        return defaultQualityColor(pEntry);
    }

    /**
     * Resolves this row's icon, found or missing, never throwing. A found entry's sprite comes
     * from its first real D2Item (colour, exactly as the game would show it); a missing entry has
     * no D2Item at all, only an invfile string, so it goes through
     * {@code D2ImageCache.getDC6Image(String)} directly and is then greyed out
     * (GrayFilter.createDisabledImage) -- an empty invfile or an absent .dc6 both degrade to no
     * icon rather than an exception, matching D2ItemImagePanel.imageFor's contract for the
     * found-entry case.
     */
    private static Icon iconFor(D2GrailEntry pEntry, D2GrailFinding pFinding, boolean pFound) {
        try {
            if (pFound) {
                D2Item lItem = pFinding.getFirstItem();
                if (lItem == null) {
                    return null;
                }
                Image lImage = D2ImageCache.getDC6Image(lItem);
                return lImage == null ? null : new ImageIcon(lImage);
            }
            String lInvfile = pEntry.getInvfile();
            if (lInvfile == null || lInvfile.isEmpty()) {
                return null;
            }
            Image lImage = D2ImageCache.getDC6Image(lInvfile + ".dc6");
            if (lImage == null) {
                return null;
            }
            return new ImageIcon(GrayFilter.createDisabledImage(lImage));
        } catch (RuntimeException | Error pEx) {
            // A .dc6 that fails to decode, or any other icon-lookup surprise, must degrade to "no
            // icon" -- never take the whole list down. Same stance as D2ItemImagePanel.imageFor.
            return null;
        }
    }

    // --------------------------------------------------------------------------------------
    // Hover tooltips (plan section 3.3)
    // --------------------------------------------------------------------------------------

    /**
     * The rich hover tooltip for one list cell: null for a set header (plain text, no tooltip
     * needed) or a found/missing entry's HTML tooltip otherwise. pFirstSeenStore may be null (the
     * "First seen:" line is then simply omitted) -- a missing/unreadable store must never be a
     * reason hovering the list stops working.
     * <p>
     * Never throws: any failure anywhere in property rendering degrades to at least the plain
     * (color-code-stripped) display name, so one odd row can't break hovering the rest of the list.
     */
    public static String tooltipFor(Object pValue, D2GrailFirstSeenStore pFirstSeenStore) {
        if (!(pValue instanceof D2GrailModel.Row)) {
            return null;
        }
        D2GrailModel.Row lRow = (D2GrailModel.Row) pValue;
        D2GrailEntry lEntry = lRow.getEntry();
        try {
            return lRow.isFound()
                    ? foundTooltip(lEntry, lRow.getFinding(), pFirstSeenStore)
                    : missingTooltip(lEntry);
        } catch (RuntimeException pEx) {
            return "<html>" + escapeHtml(D2ItemRenderer.stripColorCodes(nullToEmpty(lEntry.getDisplayName()))) + "</html>";
        }
    }

    /**
     * A found entry: the real item's own tooltip (D2ItemRenderer.itemDumpHtml, "true" for the
     * extended/socketed-items form -- the exact HTML GoMule already shows everywhere else for a
     * real item), with a GoMule-specific footer appended: where it was found, how many copies, and
     * when GoMule first saw it (plan section 3.3's "Found in:"/"Copies:"/"First seen:", replacing
     * the game's own unavailable "Dropped By:"/"First Found:" -- see D2GrailFirstSeenStore's
     * javadoc for why the wording differs from the game's).
     */
    private static String foundTooltip(D2GrailEntry pEntry, D2GrailFinding pFinding,
                                        D2GrailFirstSeenStore pFirstSeenStore) {
        D2Item lItem = pFinding.getFirstItem();
        String lBody;
        if (lItem != null) {
            String lHtml = D2ItemRenderer.itemDumpHtml(lItem, true);
            int lCloseTag = lHtml.lastIndexOf("</html>");
            lBody = lCloseTag >= 0 ? lHtml.substring(0, lCloseTag) : lHtml;
        } else {
            // D2GrailFinding.record() tolerates a null item (see its javadoc); degrade to just the
            // entry's own name rather than fabricating item details that were never captured.
            lBody = "<html><center>"
                    + escapeHtml(D2ItemRenderer.stripColorCodes(nullToEmpty(pEntry.getDisplayName()))) + "<br>";
        }

        StringBuilder lFooter = new StringBuilder("<hr>");
        String lFiles = joinFileNames(pFinding);
        if (!lFiles.isEmpty()) {
            lFooter.append("Found in: ").append(escapeHtml(lFiles)).append("<br>&#10;");
        }
        lFooter.append("Copies: ").append(pFinding.getCopies()).append("<br>&#10;");
        if (pFirstSeenStore != null) {
            Long lMillis = pFirstSeenStore.getFirstSeenMillis(pEntry.getKey());
            if (lMillis != null) {
                lFooter.append("First seen: ").append(D2GrailFirstSeenStore.formatDate(lMillis)).append("<br>&#10;");
            }
        }
        return lBody + lFooter + "</html>";
    }

    /**
     * A missing entry has no D2Item at all -- only its uniqueitems.txt/setitems.txt source row
     * (D2GrailEntry.getSourceRow()) -- so its tooltip is built straight from that row's fixed
     * prop1..N/par1..N/min1..N/max1..N columns via the same D2TxtFile.propToStat() +
     * D2PropCollection pipeline D2ItemRenderer.getItemPropertyString uses for a real item's own
     * properties, just fed columns instead of a parsed item. Uniques carry prop1..prop12,
     * set items only prop1..prop9 (verified against ./d2111's column headers).
     */
    private static String missingTooltip(D2GrailEntry pEntry) {
        String lName = D2ItemRenderer.stripColorCodes(nullToEmpty(pEntry.getDisplayName()));
        Color lColor = defaultQualityColor(pEntry);

        StringBuilder lHtml = new StringBuilder("<html><center>");
        lHtml.append("<font color='#").append(toHex(lColor)).append("'>").append(escapeHtml(lName)).append("</font><br>&#10;");
        String lBaseName = nullToEmpty(pEntry.getBaseItemName());
        if (!lBaseName.isEmpty()) {
            lHtml.append(escapeHtml(lBaseName)).append("<br>&#10;");
        }
        String lTierLabel = tierLabel(pEntry.getTier());
        if (!lTierLabel.isEmpty()) {
            lHtml.append(escapeHtml(lTierLabel)).append("<br>&#10;");
        }

        D2TxtFileItemProperties lRow = pEntry.getSourceRow();
        if (lRow != null) {
            int lMaxPropSlots = pEntry.getKey().getType() == D2GrailKey.Type.SET ? 9 : 12;
            D2PropCollection lProps = new D2PropCollection();
            for (int i = 1; i <= lMaxPropSlots; i++) {
                String lCode = lRow.get("prop" + i);
                if (lCode == null || lCode.isEmpty()) {
                    continue;
                }
                try {
                    // propToStat's own parameter order is (code, min, max, param) -- matching the
                    // "propN, parN, minN, maxN" column order in uniqueitems.txt/setitems.txt is a
                    // one-letter trap here (par vs param) that is easy to get backwards.
                    ArrayList lStats = D2TxtFile.propToStat(
                            lCode, lRow.get("min" + i), lRow.get("max" + i), lRow.get("par" + i), 0);
                    //noinspection unchecked
                    lProps.addAll(lStats);
                } catch (RuntimeException pEx) {
                    // One malformed property slot must not blank out every other real one.
                }
            }
            lProps.tidy();
            // No real character to ask for a level (this entry has never been found), so this
            // uses a generous default (99) for any level-scaled property display rather than a
            // misleadingly low one.
            lHtml.append(lProps.generateDisplay(0, 99));
        }

        if (pEntry.getKey().getType() == D2GrailKey.Type.SET) {
            appendMissingSetBonuses(lHtml, pEntry, lRow);
        }

        lHtml.append("</center></html>");
        return lHtml.toString();
    }

    /**
     * A found set item's tooltip shows its set bonuses below its own properties, but those come
     * from the save's bitstream (readExtend2, quality flags 2..6/12..16/32..36 in D2Item's own
     * iProps -- see getItemPropertyString), not from any table -- a missing item has no bitstream
     * to read, so this synthesizes the same two bonus kinds straight from the .txt tables instead:
     * <ul>
     *   <li>This piece's own per-threshold bonus: setitems.txt "aprop{N-1}a"/"aprop{N-1}b" (each
     *   with its "apar"/"amin"/"amax") -- D2Item.java:1341-1347's rollsAValue check is the existing
     *   precedent confirming both the "a" and "b" slot are real, independent bonus slots.</li>
     *   <li>The set's own bonus at that same threshold: sets.txt (D2TxtFile.FULLSET) "PCode{N}a"
     *   (with "PMin"/"PMax"/"PParam"). Deliberately the "a" slot only, never "b" -- copying
     *   D2Item.addSetProperties (D2Item.java:1137-1147) exactly, which itself never reads
     *   "PCode{N}b" at all. Consistency with that precedent matters more than completeness here:
     *   showing a "b" bonus on a MISSING item's tooltip that a FOUND copy of the very same entry
     *   would never show (once actually picked up) would be a worse, more confusing kind of wrong.</li>
     * </ul>
     * Both kinds share one combined "Set (N items):" line per threshold (2..6) -- they are, to the
     * player, one reward for reaching that many pieces, regardless of which of the two tables
     * happens to define it -- followed by one "Full Set Bonus:" line from sets.txt "FCode1".."FCode8".
     * Every one of qFlag 0 (this tooltip's own scheme -- see the class javadoc's qFlag note; none of
     * D2PropCollection's built-in qFlag routing applies here), so each section builds and renders
     * its own small D2PropCollection independently rather than filtering one shared one.
     * <p>
     * Section labels are red or a fixed color (matching D2ItemRenderer.getItemPropertyString's
     * "<font color=\"red\">Set (N items): "-then-stats style -- the label is red, the stats
     * inside keep D2PropCollection.generateDisplay's own blue, since the red font tag never
     * actually wraps them, only the label text before it); a threshold with nothing to show is
     * skipped entirely rather than printing an empty heading. A missing setitems.txt/sets.txt row,
     * or one bad property slot, degrades that one section away -- never the rest of the tooltip.
     */
    private static void appendMissingSetBonuses(StringBuilder pHtml, D2GrailEntry pEntry, D2TxtFileItemProperties pItemRow) {
        D2TxtFileItemProperties lFullSetRow;
        try {
            lFullSetRow = D2TxtFile.FULLSET.searchColumns("index", pEntry.getSetName());
        } catch (RuntimeException pEx) {
            lFullSetRow = null;
        }

        for (int x = 1; x <= 5; x++) {
            int lThreshold = x + 1; // aprop1x/PCode2x is the "2 items" bonus, ... aprop5x/PCode6x is "6 items"
            D2PropCollection lThresholdProps = new D2PropCollection();
            if (pItemRow != null) {
                addPropIfPresent(lThresholdProps, pItemRow,
                        "aprop" + x + "a", "amin" + x + "a", "amax" + x + "a", "apar" + x + "a");
                addPropIfPresent(lThresholdProps, pItemRow,
                        "aprop" + x + "b", "amin" + x + "b", "amax" + x + "b", "apar" + x + "b");
            }
            if (lFullSetRow != null) {
                addPropIfPresent(lThresholdProps, lFullSetRow,
                        "PCode" + lThreshold + "a", "PMin" + lThreshold + "a", "PMax" + lThreshold + "a",
                        "PParam" + lThreshold + "a");
            }
            if (lThresholdProps.isEmpty()) {
                continue;
            }
            lThresholdProps.tidy();
            pHtml.append("<font color='red'>Set (").append(lThreshold).append(" items): </font>")
                    .append(lThresholdProps.generateDisplay(0, 99));
        }

        if (lFullSetRow != null) {
            D2PropCollection lFullSetProps = new D2PropCollection();
            for (int i = 1; i <= 8; i++) {
                addPropIfPresent(lFullSetProps, lFullSetRow, "FCode" + i, "FMin" + i, "FMax" + i, "FParam" + i);
            }
            if (!lFullSetProps.isEmpty()) {
                lFullSetProps.tidy();
                pHtml.append("<font color='red'>Full Set Bonus: </font>").append(lFullSetProps.generateDisplay(0, 99));
            }
        }
    }

    /**
     * Reads one property slot (a code column plus its matching min/max/param columns) off a .txt
     * row and, if the code is non-empty, converts it through D2TxtFile.propToStat -- the same
     * conversion the item's own base properties above use -- into pInto. A blank code (no bonus in
     * that slot) or a code propToStat can't resolve are both silently skipped rather than either
     * one aborting every other slot in the same section.
     */
    private static void addPropIfPresent(D2PropCollection pInto, D2TxtFileItemProperties pRow,
                                          String pCodeColumn, String pMinColumn, String pMaxColumn, String pParamColumn) {
        String lCode = pRow.get(pCodeColumn);
        if (lCode == null || lCode.isEmpty()) {
            return;
        }
        try {
            ArrayList lStats = D2TxtFile.propToStat(
                    lCode, pRow.get(pMinColumn), pRow.get(pMaxColumn), pRow.get(pParamColumn), 0);
            //noinspection unchecked
            pInto.addAll(lStats);
        } catch (RuntimeException pEx) {
            // One malformed property slot must not blank out every other real one.
        }
    }

    private static String tierLabel(D2GrailEntry.Tier pTier) {
        switch (pTier) {
            case EXCEPTIONAL:
                return "Exceptional";
            case ELITE:
                return "Elite";
            case NORMAL:
                return "Normal";
            default:
                return "";
        }
    }

    private static Color defaultQualityColor(D2GrailEntry pEntry) {
        return pEntry.getKey().getType() == D2GrailKey.Type.SET ? SET_COLOR : UNIQUE_OR_RUNEWORD_COLOR;
    }

    private static String toHex(Color pColor) {
        String lHex = Integer.toHexString(pColor.getRGB() & 0xFFFFFF);
        while (lHex.length() < 6) {
            lHex = "0" + lHex;
        }
        return lHex;
    }

    private static String escapeHtml(String pText) {
        return pText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nullToEmpty(String pText) {
        return pText == null ? "" : pText;
    }
}
