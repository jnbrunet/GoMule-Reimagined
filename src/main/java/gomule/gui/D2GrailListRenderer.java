package gomule.gui;

import gomule.D2Files;
import gomule.grail.D2GrailEntry;
import gomule.grail.D2GrailFinding;
import gomule.grail.D2GrailFirstSeenStore;
import gomule.grail.D2GrailKey;
import gomule.grail.D2GrailModel;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import gomule.item.D2Prop;
import gomule.item.D2PropCollection;
import gomule.item.RequirementModifierAccumulator;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // No real character to ask for a level (a missing entry has never been found), so every
    // per-level property in this tooltip is displayed as if a level-99 character owned it -- a
    // generous default rather than a misleadingly low one. Used for BOTH D2PropCollection.applyOp()
    // (below) and generateDisplay()'s own cLvl parameter, even though generateDisplay() does not
    // actually use it (D2Prop.generateDisplay's cLvl argument is dead -- the real scaling happens
    // in applyOp(), called separately -- see the field's use sites for the full story); kept in
    // sync anyway since a future change to generateDisplay might start using it.
    private static final int ASSUMED_CHARACTER_LEVEL = 99;

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
     * <p>
     * A missing RUNEWORD is a different shape entirely and takes its own path
     * (appendMissingRuneword): its source row is a runes.txt row, which has no prop1..N columns at
     * all (its properties live in T1Code1..T1Code7) and no single base item to take base stats
     * from. Before that path existed, every one of those columns read back as "" and the tooltip
     * came out as nothing but the runeword's name.
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

        if (pEntry.getKey().getType() == D2GrailKey.Type.RUNEWORD) {
            D2TxtFileItemProperties lRunesRow = pEntry.getSourceRow();
            if (lRunesRow != null) {
                appendMissingRuneword(lHtml, lRunesRow);
            }
            lHtml.append("</center></html>");
            return lHtml.toString();
        }

        // Built BEFORE appendMissingBaseStats (even though the base-stat LINES it feeds print
        // first -- see below) because appendMissingBaseStats now needs these same two flavoured,
        // tidy()'d, applyOp()'d collections to compute the item's OWN modified damage/defense/
        // durability/requirements (D2Item.applyItemMods()'s job for a found item -- see
        // appendMissingBaseStats's own javadoc), not just to print the property lines afterward.
        // Deliberately the item's OWN prop1..N collections only, never the set-bonus sections
        // built later in this method: a missing set piece has no ACTIVATED set, exactly like a
        // found set item whose set isn't assembled -- D2Item.applyItemMods() only ever reads
        // qFlag 0 (an item's own props) or 12..16 (an activated set bonus) from iProps, and every
        // prop this tooltip ever builds is qFlag 0, so the set-bonus sections (qFlag has no
        // meaning there either way) simply never belong in this accumulation.
        D2TxtFileItemProperties lRow = pEntry.getSourceRow();
        FlavouredProps lOwnProps = new FlavouredProps(new D2PropCollection(), new D2PropCollection());
        if (lRow != null) {
            int lMaxPropSlots = pEntry.getKey().getType() == D2GrailKey.Type.SET ? 9 : 12;
            List<PropSlot> lSlots = new ArrayList<PropSlot>();
            for (int i = 1; i <= lMaxPropSlots; i++) {
                // propToStat's own parameter order is (code, min, max, param) -- matching the
                // "propN, parN, minN, maxN" column order in uniqueitems.txt/setitems.txt is a
                // one-letter trap here (par vs param) that is easy to get backwards.
                addSlotIfPresent(lSlots, lRow, "prop" + i, "min" + i, "max" + i, "par" + i);
            }
            lOwnProps = buildFlavouredProps(lSlots);
        }

        appendMissingBaseStats(lHtml, pEntry, lOwnProps);

        if (lRow != null) {
            // renderFlavouredProps runs generateDisplay() on each of the two already-built
            // collections and merges the two results into one "min-max" fragment -- see
            // mergeRangeFragments's javadoc. The per-level scaling comment that used to live here
            // (uniqueitems.txt "hp/lvl" par=12 -> "+12 to Life (Based on Character Level)" becoming
            // a real "+127" at level 85, confirmed against charFiles/pally9.d2s's "Cleglaw's
            // Pincers") still applies verbatim to BOTH passes -- a per-level slot has no min/max at
            // all, so both passes compute the identical applyOp() scaling and merge back to
            // themselves, exactly as before this change.
            lHtml.append(renderFlavouredProps(lOwnProps));
        }

        if (pEntry.getKey().getType() == D2GrailKey.Type.SET) {
            appendMissingSetBonuses(lHtml, pEntry, lRow);
        }

        lHtml.append("</center></html>");
        return lHtml.toString();
    }

    // ------------------------------------------------------------------------------------------
    // Missing runewords
    // ------------------------------------------------------------------------------------------

    /**
     * Which of gems.txt's three sets of mod columns a rune contributes through once it is socketed
     * into a base: a weapon reads weaponMod1..3, a helm OR a body armor reads helmMod1..3, a shield
     * reads shieldMod1..3. Exactly the same three-way split D2Item's own socket loop applies to a
     * real found item (where it shows up as qFlags 7/8/9), and the same one D2Item.
     * readPropertiesGems() walks -- this is the table-sourced counterpart of that method, for a
     * runeword nobody has made yet.
     */
    private enum RuneSocketClass {
        WEAPON("weaponMod", "In a Weapon"),
        ARMOR("helmMod", "In Armor"),
        SHIELD("shieldMod", "In a Shield");

        private final String iColumnPrefix;
        private final String iLabel;

        RuneSocketClass(String pColumnPrefix, String pLabel) {
            iColumnPrefix = pColumnPrefix;
            iLabel = pLabel;
        }
    }

    // itemtypes.txt's own "ItemType" column is the label for every runeword base type except these
    // two, where the mod's wording describes something other than the slot and would read as an
    // error in a tooltip: "helm" is labelled "Merc Equip" there (a note about who can wear it, not
    // what it is), and "tors" is labelled plain "Armor", indistinguishable from the separate,
    // broader "armo" type whose own label is "Any Armor". Everything else -- Sword, Polearm, Any
    // Shield, Amazon Bow, Auric Shields, Voodoo Heads and the rest -- is taken verbatim from the
    // table, so a mod update that adds a base type needs no change here.
    private static final Map<String, String> ITYPE_LABEL_OVERRIDES = new HashMap<String, String>();

    static {
        ITYPE_LABEL_OVERRIDES.put("helm", "Helm");
        ITYPE_LABEL_OVERRIDES.put("tors", "Body Armor");
    }

    /**
     * A missing runeword's tooltip body: which bases it can be made in, the rune sequence, the
     * level it needs, and what it grants -- everything the mod's own item page shows, since a
     * runeword that has never been made has no D2Item to ask.
     * <p>
     * "What it grants" is two things added together, exactly as the game itself adds them: the
     * runeword's OWN properties (runes.txt T1Code1..T1Code7 -- there are only seven slots) plus
     * each rune's own gems.txt bonus for the kind of base it is socketed into. Confirmed against
     * the mod's page for "Bulwark" (Shael + Io + Sol in a helm): three of its lines -- "+20% Faster
     * Hit Recovery", "+10 to Vitality" and "Physical Damage Reduced by 7" -- appear nowhere in its
     * runes.txt row at all, and are precisely the helmMod entries of those three runes.
     * <p>
     * The rune half depends on what the runeword is made in, and most runewords allow exactly one
     * kind of base, so their rune bonuses are merged straight into the one property list the mod's
     * page shows. 17 of the 208 runewords in ./d2111 span more than one kind (Spirit is swords OR
     * shields; Fortitude is weapons, body armor OR shields) and the runes contribute differently to
     * each, so those get one clearly-labelled block per kind after their own properties rather than
     * a single merged list that would be right for at most one of them.
     */
    private static void appendMissingRuneword(StringBuilder pHtml, D2TxtFileItemProperties pRow) {
        List<String> lItypes = runewordItypes(pRow);
        if (!lItypes.isEmpty()) {
            StringBuilder lBases = new StringBuilder();
            for (String lItype : lItypes) {
                if (lBases.length() > 0) {
                    lBases.append(" / ");
                }
                lBases.append(itypeLabel(lItype));
            }
            pHtml.append(escapeHtml(lBases.toString())).append("<br>&#10;");
        }

        List<String> lRuneCodes = runewordRuneCodes(pRow);
        if (!lRuneCodes.isEmpty()) {
            StringBuilder lSequence = new StringBuilder();
            for (String lRuneCode : lRuneCodes) {
                if (lSequence.length() > 0) {
                    lSequence.append(" + ");
                }
                String lName = runeName(lRuneCode);
                lSequence.append(lName);
                // The mod's own rune names already carry "(#13)"; only add it for a name that
                // doesn't (an untranslated rune falling back to misc.txt's plain "Shael Rune").
                int lNumber = runeNumber(lRuneCode);
                if (lNumber > 0 && lName.indexOf('#') < 0) {
                    lSequence.append(" (#").append(lNumber).append(")");
                }
            }
            pHtml.append(escapeHtml(lSequence.toString())).append("<br>&#10;");

            int lRequiredLevel = runewordRequiredLevel(lRuneCodes);
            if (lRequiredLevel > 0) {
                pHtml.append("Required Level: ").append(lRequiredLevel).append("<br>&#10;");
            }
        }

        List<PropSlot> lSlots = new ArrayList<PropSlot>();
        for (int i = 1; i <= 7; i++) {
            // runes.txt spells the four columns T1Code/T1Param/T1Min/T1Max -- note that
            // addSlotIfPresent, like propToStat itself, takes them in (code, min, max, param)
            // order, not the order the file lists them in.
            addSlotIfPresent(lSlots, pRow, "T1Code" + i, "T1Min" + i, "T1Max" + i, "T1Param" + i);
        }

        Set<RuneSocketClass> lClasses = runeSocketClasses(lItypes);
        if (lClasses.size() == 1) {
            addRuneModSlots(lSlots, lRuneCodes, lClasses.iterator().next());
        }
        pHtml.append(renderFlavouredProps(buildFlavouredProps(lSlots)));

        if (lClasses.size() > 1) {
            for (RuneSocketClass lClass : lClasses) {
                List<PropSlot> lRuneSlots = new ArrayList<PropSlot>();
                addRuneModSlots(lRuneSlots, lRuneCodes, lClass);
                if (!lRuneSlots.isEmpty()) {
                    pHtml.append("<font color='red'>").append(escapeHtml(lClass.iLabel)).append(": </font>")
                            .append(renderFlavouredProps(buildFlavouredProps(lRuneSlots)));
                }
            }
        }
    }

    /**
     * The runeword's allowed base types (runes.txt itype1..itype6), in table order, de-duplicated.
     */
    private static List<String> runewordItypes(D2TxtFileItemProperties pRow) {
        List<String> lOut = new ArrayList<String>();
        for (int i = 1; i <= 6; i++) {
            String lCode = nullToEmpty(pRow.get("itype" + i)).trim();
            if (!lCode.isEmpty() && !lOut.contains(lCode)) {
                lOut.add(lCode);
            }
        }
        return lOut;
    }

    /**
     * The runes the word is made of (runes.txt Rune1..Rune6), in order -- deliberately NOT
     * de-duplicated: a word can legitimately use the same rune twice.
     */
    private static List<String> runewordRuneCodes(D2TxtFileItemProperties pRow) {
        List<String> lOut = new ArrayList<String>();
        for (int i = 1; i <= 6; i++) {
            String lCode = nullToEmpty(pRow.get("Rune" + i)).trim();
            if (!lCode.isEmpty()) {
                lOut.add(lCode);
            }
        }
        return lOut;
    }

    private static String itypeLabel(String pItypeCode) {
        String lOverride = ITYPE_LABEL_OVERRIDES.get(pItypeCode);
        if (lOverride != null) {
            return lOverride;
        }
        D2TxtFileItemProperties lRow = D2TxtFile.ITEM_TYPES.searchColumns("Code", pItypeCode);
        String lLabel = lRow == null ? "" : nullToEmpty(lRow.get("ItemType")).trim();
        return lLabel.isEmpty() ? pItypeCode : lLabel;
    }

    /**
     * A rune's display name, resolved the same way D2Item.readExtend resolves any base item's:
     * the translation for its code, falling back to misc.txt's own "name" column when the tables
     * carry no translation for it (and to the raw code if there is no row at all).
     * <p>
     * The mod's own rune strings are markup, not plain names: every one is colour-coded and
     * already carries the rune number the guides print ("ÿc1Jah Rune ÿc9(ÿc0#31ÿc9)"), and the
     * high runes add a second line, "ÿc;~Pick Up~ÿc0", that only makes sense on the ground. So the
     * colour codes are stripped and everything after the first line break dropped, leaving
     * "Jah Rune (#31)" -- which is why runeNumber() is only ever appended when the resulting name
     * has no "#" of its own.
     */
    private static String runeName(String pRuneCode) {
        D2TxtFileItemProperties lRow = D2TxtFile.search(pRuneCode);
        if (lRow == null) {
            return pRuneCode;
        }
        String lTableName = nullToEmpty(lRow.get("name"));
        String lTranslated = D2Files.getInstance().getTranslations()
                .getTranslationOrNull(pRuneCode, lTableName);
        String lName = D2ItemRenderer.stripColorCodes(lTranslated != null ? lTranslated : lTableName);
        int lBreak = lName.length();
        for (int i = 0; i < lName.length(); i++) {
            char lChar = lName.charAt(i);
            if (lChar == '\n' || lChar == '\r') {
                lBreak = i;
                break;
            }
        }
        lName = lName.substring(0, lBreak).trim();
        return lName.isEmpty() ? pRuneCode : lName;
    }

    /**
     * The rune's number as the game and every runeword guide print it ("Shael Rune (#13)") -- the
     * digits of its item code, which run r01..r33 in order. 0 (not printed) for anything that
     * doesn't parse, rather than a made-up number.
     */
    private static int runeNumber(String pRuneCode) {
        StringBuilder lDigits = new StringBuilder();
        for (int i = 0; i < pRuneCode.length(); i++) {
            char lChar = pRuneCode.charAt(i);
            if (lChar >= '0' && lChar <= '9') {
                lDigits.append(lChar);
            }
        }
        if (lDigits.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(lDigits.toString());
        } catch (NumberFormatException pEx) {
            return 0;
        }
    }

    /**
     * A runeword's level requirement is the highest level requirement among its runes -- runes.txt
     * carries no level column of its own. Verified against the mod's own page for "Bulwark"
     * (Shael 29 / Io 35 / Sol 27, shown as "Level 35"). 0 -- nothing printed -- if no rune has a
     * usable levelreq, e.g. a word made only of Hel (levelreq 0).
     */
    private static int runewordRequiredLevel(List<String> pRuneCodes) {
        int lMax = 0;
        for (String lRuneCode : pRuneCodes) {
            D2TxtFileItemProperties lRow = D2TxtFile.search(lRuneCode);
            if (lRow == null) {
                continue;
            }
            try {
                lMax = Math.max(lMax, Integer.parseInt(nullToEmpty(lRow.get("levelreq")).trim()));
            } catch (NumberFormatException pEx) {
                // A blank or non-numeric levelreq contributes nothing, exactly like a 0 one.
            }
        }
        return lMax;
    }

    /**
     * Adds each rune's own gems.txt bonuses for one kind of base (up to three mod slots per rune)
     * as ordinary property slots, so they run through the same range/render pipeline as the
     * runeword's own properties and end up sorted and combined with them by D2PropCollection --
     * two runes each granting +10 Vitality show as one "+20 to Vitality" line, exactly as a real
     * socketed item does.
     */
    private static void addRuneModSlots(List<PropSlot> pInto, List<String> pRuneCodes, RuneSocketClass pClass) {
        for (String lRuneCode : pRuneCodes) {
            D2TxtFileItemProperties lGemRow = D2TxtFile.GEMS.searchColumns("code", lRuneCode);
            if (lGemRow == null) {
                continue;
            }
            for (int i = 1; i <= 3; i++) {
                String lPrefix = pClass.iColumnPrefix + i;
                addSlotIfPresent(pInto, lGemRow, lPrefix + "Code", lPrefix + "Min", lPrefix + "Max",
                        lPrefix + "Param");
            }
        }
    }

    /**
     * Which kinds of base this runeword can be made in, as far as the runes' own bonuses are
     * concerned -- an itype that cannot be classified contributes nothing rather than a guess.
     */
    private static Set<RuneSocketClass> runeSocketClasses(List<String> pItypes) {
        Set<RuneSocketClass> lOut = new LinkedHashSet<RuneSocketClass>();
        for (String lItype : pItypes) {
            RuneSocketClass lClass = runeSocketClassOf(lItype);
            if (lClass != null) {
                lOut.add(lClass);
            }
        }
        return lOut;
    }

    /**
     * Resolves one runes.txt itype code to the kind of base it names, from itemtypes.txt's own
     * Equiv1/Equiv2 hierarchy rather than a hardcoded list, so a mod-added type is classified for
     * free: walk up to the "shld"/"armo"/"weap" roots, checking shields FIRST because "shld" is
     * itself an "armo" underneath.
     * <p>
     * The four class-item umbrella types runes.txt uses -- "pala", "barb", "drui", "necr", all of
     * whose ancestry stops at the abstract "clas" -- name no kind of gear on their own, so they are
     * resolved from their children instead: each has exactly one ("ashd" Auric Shields, "phlm"
     * Barbarian Helms, "pelt" Druid Pelts, "head" Voodoo Heads), and each of those does resolve.
     * Null when even that is ambiguous or absent, which no ./d2111 runeword hits today.
     */
    private static RuneSocketClass runeSocketClassOf(String pItypeCode) {
        RuneSocketClass lByAncestry = runeSocketClassByAncestry(pItypeCode);
        if (lByAncestry != null) {
            return lByAncestry;
        }
        RuneSocketClass lFromChildren = null;
        int lRows = D2TxtFile.ITEM_TYPES.getRowSize();
        for (int i = 0; i < lRows; i++) {
            D2TxtFileItemProperties lRow = D2TxtFile.ITEM_TYPES.getRow(i);
            if (!pItypeCode.equals(lRow.get("Equiv1")) && !pItypeCode.equals(lRow.get("Equiv2"))) {
                continue;
            }
            RuneSocketClass lChild = runeSocketClassByAncestry(lRow.get("Code"));
            if (lChild == null || (lFromChildren != null && lFromChildren != lChild)) {
                return null;
            }
            lFromChildren = lChild;
        }
        return lFromChildren;
    }

    private static RuneSocketClass runeSocketClassByAncestry(String pItypeCode) {
        Set<String> lSeen = new HashSet<String>();
        List<String> lPending = new ArrayList<String>();
        lPending.add(pItypeCode);
        boolean lShield = false;
        boolean lArmor = false;
        boolean lWeapon = false;
        while (!lPending.isEmpty()) {
            String lCode = lPending.remove(lPending.size() - 1);
            if (lCode == null || lCode.isEmpty() || !lSeen.add(lCode)) {
                continue;
            }
            if ("shld".equals(lCode)) {
                lShield = true;
            } else if ("armo".equals(lCode)) {
                lArmor = true;
            } else if ("weap".equals(lCode)) {
                lWeapon = true;
            }
            D2TxtFileItemProperties lRow = D2TxtFile.ITEM_TYPES.searchColumns("Code", lCode);
            if (lRow == null) {
                continue;
            }
            lPending.add(nullToEmpty(lRow.get("Equiv1")).trim());
            lPending.add(nullToEmpty(lRow.get("Equiv2")).trim());
        }
        // Shields first: every shield is an "armo" too, so testing armor first would send every
        // shield down the helm/body-armor column set.
        if (lShield) {
            return RuneSocketClass.SHIELD;
        }
        if (lArmor) {
            return RuneSocketClass.ARMOR;
        }
        if (lWeapon) {
            return RuneSocketClass.WEAPON;
        }
        return null;
    }

    /**
     * A found item's tooltip shows its own base statistics (defense, damage, durability,
     * requirements) straight from the real, rolled D2Item, AFTER D2Item.applyItemMods() (D2Item.
     * java, roughly lines 1509-1682) has folded the item's own +damage%/+defense/+durability/
     * -requirement properties into the base numbers -- see D2ItemRenderer.
     * generatePropStringNoHtmlTags. A missing entry has no rolled instance, only its base item's
     * misc.txt/armor.txt/weapons.txt row (D2TxtFile.search(baseCode)) and its own two flavoured
     * property collections (pOwnProps -- built by missingTooltip from prop1..N, BEFORE this
     * method runs, so both the base-stat lines here and the property lines printed afterward share
     * the exact same accumulation), so this mirrors applyItemMods()'s own arithmetic by hand
     * against those, showing the base RANGE ACROSS BOTH the base row's own roll variance AND the
     * item's own modifier roll variance (e.g. "One Hand Damage: 148-185 to 357-500" on Wrath of the
     * Seraphim -- verified by hand against D2Item.applyItemMods(), see accumulateItemMods()'s
     * javadoc) -- exactly what there IS to show for something never found.
     * <p>
     * A runeword has no single base item (its allowed bases are itype1..itype6, a list, not one
     * row) and is skipped entirely rather than guessing at one. Every line degrades independently:
     * a missing base row, or one column that is empty/non-numeric, drops only that line, never the
     * rest of the tooltip -- the same stance as every other piece of this tooltip.
     */
    private static void appendMissingBaseStats(StringBuilder pHtml, D2GrailEntry pEntry, FlavouredProps pOwnProps) {
        if (pEntry.getKey().getType() == D2GrailKey.Type.RUNEWORD) {
            return;
        }

        String lBaseCode = pEntry.getBaseItemCode();
        if (lBaseCode == null || lBaseCode.isEmpty()) {
            // The ~10 real "reserved unique slot" entries with no base code at all (e.g. "Gore
            // Ripper") -- D2TxtFile.search("") could in principle match some unrelated row with a
            // blank "code" column, so this is skipped explicitly rather than risking that.
            return;
        }

        D2TxtFileItemProperties lBaseRow;
        try {
            lBaseRow = D2TxtFile.search(lBaseCode);
        } catch (RuntimeException pEx) {
            lBaseRow = null;
        }
        if (lBaseRow == null) {
            return;
        }

        // Which of armor.txt/weapons.txt/misc.txt the row actually came from -- D2Item.java's own
        // isTypeArmor()/isTypeWeapon() are set the exact same way (readExtend: "armor".equals(
        // iItemType.getFileName())/"weapons".equals(...)). This is NOT the same as checking whether
        // "minac"/"mindam" happen to be non-empty: armor.txt carries its own always-zero "mindam"/
        // "maxdam" pair on every row (confirmed against "Shako"/uap: mindam=0, maxdam=0), which
        // would otherwise render a bogus "One Hand Damage: 0 - 0" on every piece of armor.
        String lTable = lBaseRow.getFileName();
        boolean lIsArmor = "armor".equals(lTable);
        boolean lIsWeapon = "weapons".equals(lTable);

        // One accumulation per flavour, covering every kind of modifier at once -- exactly mirrors
        // D2Item.applyItemMods()'s own single pass over iProps, which likewise accumulates
        // dmgTriple/armourTriple/durTriple/the requirement accumulator together before applying any
        // of them. Harmless (never read) for whichever of dmgTriple/armourTriple doesn't apply to
        // this base's own type -- e.g. an armor unique's own prop list realistically never carries
        // an item_maxdamage_percent (pNum 17) entry to begin with, so accumulateItemMods filling in
        // dmgTriple[0] for it anyway is inert, never consulted by appendArmorStats below. This is a
        // deliberate simplification versus D2Item.applyItemMods()'s own isTypeArmor()/isTypeWeapon()
        // gating INSIDE the accumulation loop: the same segregation still holds here, just enforced
        // by which triple the caller chooses to read, not by which the accumulator chooses to fill.
        ItemModifiers lMinMods = accumulateItemMods(pOwnProps.iMin);
        ItemModifiers lMaxMods = accumulateItemMods(pOwnProps.iMax);

        if (lIsArmor) {
            try {
                appendArmorStats(pHtml, lBaseRow, lMinMods, lMaxMods);
            } catch (RuntimeException pEx) {
                // A broken armor column must not block durability/requirements below.
            }
        }
        if (lIsWeapon) {
            try {
                appendWeaponStats(pHtml, lBaseRow, lMinMods, lMaxMods);
            } catch (RuntimeException pEx) {
                // Ditto for a broken weapon column.
            }
        }
        try {
            appendDurability(pHtml, lBaseRow, lMinMods, lMaxMods);
        } catch (RuntimeException pEx) {
            // Ditto for durability.
        }
        try {
            Integer lReqLevel = requiredLevel(pEntry, lBaseRow);
            if (lReqLevel != null) {
                // D2Item.applyItemMods()'s own precedence: iReqLvl is first set from the unique/set
                // row (D2Item.readExtend, mirrored here by requiredLevel() itself), THEN
                // applyItemMods() ADDS the item's own pNum-92 ("+LvlReq") accumulation on top -- so
                // the accumulator's contribution is added to requiredLevel()'s result, never
                // replacing it.
                int lFromMinFlavour = lReqLevel + lMinMods.iRequirementModifierAccumulator.getLevelRequirement();
                int lFromMaxFlavour = lReqLevel + lMaxMods.iRequirementModifierAccumulator.getLevelRequirement();
                pHtml.append("Required Level: ").append(formatModifiedValue(lFromMinFlavour, lFromMaxFlavour)).append("<br>&#10;");
            }
        } catch (RuntimeException pEx) {
            // Ditto for the required-level precedence lookup below.
        }
        try {
            Integer lReqStr = getReq(lBaseRow.get("reqstr"));
            if (lReqStr != null) {
                int lFromMinFlavour = lReqStr + applyPercentRequirement(lReqStr, lMinMods);
                int lFromMaxFlavour = lReqStr + applyPercentRequirement(lReqStr, lMaxMods);
                pHtml.append("Required Strength: ").append(formatModifiedValue(lFromMinFlavour, lFromMaxFlavour)).append("<br>&#10;");
            }
        } catch (RuntimeException pEx) {
            // Ditto.
        }
        try {
            Integer lReqDex = getReq(lBaseRow.get("reqdex"));
            if (lReqDex != null) {
                int lFromMinFlavour = lReqDex + applyPercentRequirement(lReqDex, lMinMods);
                int lFromMaxFlavour = lReqDex + applyPercentRequirement(lReqDex, lMaxMods);
                pHtml.append("Required Dexterity: ").append(formatModifiedValue(lFromMinFlavour, lFromMaxFlavour)).append("<br>&#10;");
            }
        } catch (RuntimeException pEx) {
            // Ditto.
        }
    }

    /**
     * D2Item.applyItemMods()'s own "-Req" arithmetic (pNum 91, "item_req_percent"): "iReqDex +
     * (int)(iReqDex * percentReqirementsModifier)" -- reproduced here as the ADDEND only (the
     * caller still adds the base requirement itself), so the same expression can feed both
     * Required Strength and Required Dexterity without repeating the cast/division.
     */
    private static int applyPercentRequirement(int pBaseRequirement, ItemModifiers pMods) {
        return (int) (pBaseRequirement * (pMods.iRequirementModifierAccumulator.getPercentRequirements() / 100.0));
    }

    /**
     * Formats one base-stat value that may differ between the min- and max-flavoured
     * accumulations: a single number when the two agree (the common case -- most items don't
     * modify every one of damage/defense/durability/requirements), otherwise mergeNumberToken's
     * own "low-high" text -- reusing mergeNumberToken rather than re-implementing its equal/
     * ascending/negative/backwards rules a second time.
     * <p>
     * Sorts the two inputs itself (Math.min/Math.max) rather than trusting the caller to already
     * pass them low-then-high: for every base stat this is currently used for (damage, defense,
     * durability, and requirements -- see accumulateItemMods()'s own pNum-91 paragraph for why
     * requirements are NOT sign-corrected, so today's real data always happens to keep the
     * min-flavoured accumulation the smaller of the two) the min-flavoured pass already produces
     * the lower number, making this a no-op in practice -- but a REDUCING percent modifier (a
     * bigger negative magnitude cutting a base value down further) would flip that relationship
     * for whichever flavour applies the bigger cut, so sorting defensively here, once, in the one
     * formatter every base-stat call site shares, is cheap insurance against a future real item
     * exercising that shape: mergeNumberToken's own "min &gt; max -> emit the max token alone"
     * fallback would otherwise silently swallow a genuinely descending pair down to one wrong
     * number instead of showing the real range.
     */
    private static String formatModifiedValue(int pFromMinFlavour, int pFromMaxFlavour) {
        int lLow = Math.min(pFromMinFlavour, pFromMaxFlavour);
        int lHigh = Math.max(pFromMinFlavour, pFromMaxFlavour);
        return mergeNumberToken(String.valueOf(lLow), String.valueOf(lHigh));
    }

    /**
     * One flavour's accumulated item modifiers: D2Item.applyItemMods()'s own dmgTriple/
     * armourTriple/durTriple int arrays plus its RequirementModifierAccumulator (D2Item.java,
     * roughly lines 1511-1631), filled here from ONE flavoured D2PropCollection (min or max) via
     * accumulateItemMods() instead of a real item's iProps. See accumulateItemMods()'s javadoc for
     * the field-by-field mirroring and what is deliberately left out.
     */
    private static final class ItemModifiers {
        private final int[] iDmgTriple = new int[5];
        private final int[] iArmourTriple = new int[3];
        private final int[] iDurTriple = new int[2];
        private final RequirementModifierAccumulator iRequirementModifierAccumulator = new RequirementModifierAccumulator();
    }

    /**
     * Mirrors D2Item.applyItemMods()'s own accumulation loop (D2Item.java, roughly lines 1518-1631)
     * field-for-field, run here against ONE flavoured, already tidy()'d and applyOp()'d
     * D2PropCollection instead of a real item's post-applyOp() iProps:
     * <ul>
     *   <li>pNum 73/75 (+Dur / Dur%) -> durTriple[0]/[1], pNum 92/91 (+LvlReq / -Req) -> the
     *   RequirementModifierAccumulator -- all four unconditional on item type, exactly as
     *   D2Item.applyItemMods() itself accumulates them BEFORE its isTypeArmor()/isTypeWeapon()
     *   branch.</li>
     *   <li>pNum 16/31/214 (EDef / +Def / +Def/lvl) -> armourTriple[0]/[1]/[2]; pNum 17/21/22/218/
     *   219 (EDmg / MinDmg / MaxDmg / MaxDmg/Lvl / MaxDmg%/lvl) -> dmgTriple[0..4], including pNum
     *   21's own funcN==31 special case (some min-damage stats carry a second value in pVals[1]
     *   that D2Item.applyItemMods() folds into dmgTriple[2], the MAX side, not the min side --
     *   copied here verbatim, not something this feature invented).</li>
     * </ul>
     * Deliberately NOT attempted, exactly per the parent task's own scope: applyItemMods()'
     * ethereal branch (a missing entry has no ethereal flag to read) and its pNum 97/107
     * skill-granted-level-requirement raise (a separate skills.txt lookup keyed off a specific
     * granted skill, out of scope here).
     * <p>
     * The qFlag filter (0 or 12..16) is copied verbatim from D2Item.applyItemMods() too, even
     * though every D2Prop this tooltip ever builds is qFlag 0 (see the class javadoc's qFlag
     * note) -- defensive, in case a future caller ever feeds this a collection built some other
     * way, at zero cost to the real, current callers.
     * <p>
     * pNum 91 (item_req_percent, properties.txt code "ease") is passed through with its ORIGINAL
     * sign, NOT negated -- investigated and deliberately rejected, so a future reader doesn't
     * re-attempt the same "obviously missing minus sign" fix without seeing why it was rejected.
     * The temptation: "ease"'s *Tooltip is the literal string "Requirements -#%" (the minus lives
     * in that template text, substituted with a raw magnitude), so a row like "The Grandfather"'s
     * real prop7 "ease" (min=25/max=50, positive) LOOKS like it should be negated to match its own
     * property line's implied "-25 to -50%" reduction, and itemstatcost.txt's item_req_percent row
     * (*ID 91, Signed=1, Save Add=100) looks like independent evidence for it -- a "Save Add" is
     * commonly (mis)read as "the true value must be negative, that's why it needs a bias to fit an
     * unsigned field". But "Save Add" is just a bidirectional-signed-value encoding trick (shift by
     * a constant so an unsigned bit field can hold negative numbers); it does not, by itself, mean
     * the real gameplay value is always negative, and checking every real "ease" usage in
     * ./d2111's uniqueitems.txt + setitems.txt disproves the "always negate" theory outright: 49
     * rows already store a NEGATIVE min/max directly (e.g. "Steeldriver" min=-50/max=-50) against
     * 40 that store POSITIVE (including "The Grandfather") -- a near-even split, not a rare
     * exception. Confirmed the "already negative" rows are the genuine, correctly-authored ones by
     * their own visible tooltip glitch: "Steeldriver"'s property line renders as "Requirements
     * --50%" (a double-minus, from the SAME fixed "-#%" template substituting an already-negative
     * number) -- exactly the artifact expected if -50 truly is Steeldriver's intended,
     * already-correctly-signed value, and proof the template's "-" is unconditional decoration, not
     * a reliable signal of which sign convention a given row used. Negating pNum 91 unconditionally
     * would "fix" the ~40 positive rows (including The Grandfather, apparently a genuine ./d2111
     * data error, not a GoMule bug -- vanilla Diablo II's own real "The Grandfather" is well known
     * for REDUCING requirements) at the cost of silently inverting the other 49 already-correct
     * rows (turning Steeldriver's real reduction into an increase). With the underlying mod data
     * itself this evenly, irreconcilably inconsistent for a single stat, passing pVals[0] through
     * unchanged -- trusting the table at face value, exactly as every other stat here does -- is
     * the only choice that does not knowingly break a large, easily-counted set of real items to
     * fix a different one.
     */
    private static ItemModifiers accumulateItemMods(D2PropCollection pProps) {
        ItemModifiers lMods = new ItemModifiers();
        for (int x = 0; x < pProps.size(); x++) {
            D2Prop lProp = (D2Prop) pProps.get(x);
            int lQFlag = lProp.getQFlag();
            if (lQFlag != 0 && lQFlag != 12 && lQFlag != 13 && lQFlag != 14 && lQFlag != 15 && lQFlag != 16) {
                continue;
            }
            int lPNum = lProp.getPNum();
            int[] lPVals = lProp.getPVals();
            if (lPNum == 73) {
                lMods.iDurTriple[0] += lPVals[0];
            } else if (lPNum == 75) {
                lMods.iDurTriple[1] += lPVals[0];
            } else if (lPNum == 92) {
                lMods.iRequirementModifierAccumulator.accumulateLevelRequirement(lPVals[0]);
            } else if (lPNum == 91) {
                // Deliberately NOT sign-corrected -- investigated and rejected; see this method's
                // own javadoc (the pNum 91 paragraph) for why a blanket negation here would be a
                // net regression, not a fix. pVals[0] is passed straight through, exactly as every
                // other stat in this method is.
                lMods.iRequirementModifierAccumulator.accumulatePercentRequirements(lPVals[0]);
            } else if (lPNum == 16) {
                lMods.iArmourTriple[0] += lPVals[0];
            } else if (lPNum == 31) {
                lMods.iArmourTriple[1] += lPVals[0];
            } else if (lPNum == 214) {
                lMods.iArmourTriple[2] += lPVals[0];
            } else if (lPNum == 17) {
                lMods.iDmgTriple[0] += lPVals[0];
            } else if (lPNum == 21) {
                lMods.iDmgTriple[1] += lPVals[0];
                if (lProp.getFuncN() == 31) {
                    lMods.iDmgTriple[2] += lPVals[1];
                }
            } else if (lPNum == 22) {
                lMods.iDmgTriple[2] += lPVals[0];
            } else if (lPNum == 218) {
                lMods.iDmgTriple[3] += lPVals[0];
            } else if (lPNum == 219) {
                lMods.iDmgTriple[4] += lPVals[0];
            }
        }
        return lMods;
    }

    /**
     * D2Item.applyItemMods()'s own "percent-of-base plus flat" formula, copied verbatim (floor()
     * and all -- NOT interchangeable with a plain int division/multiplication, per the parent
     * task's own instruction): {@code floor(base/100.0*percent + (base+flat))}. Covers THREE of
     * applyItemMods()'s four uses of this exact shape (weapon min-damage, armor defense, max
     * durability); weapon max-damage needs a different percent/flat PAIR (dmgTriple[0]+dmgTriple[4]
     * and dmgTriple[2]+dmgTriple[3] respectively, not a single triple slot each) but the same
     * underlying arithmetic, so computeWeaponDamage() below calls this same helper twice with those
     * combined arguments rather than duplicating the formula a second time.
     */
    private static int applyPercentPlusFlat(int pBase, int pPercent, int pFlat) {
        return (int) Math.floor((pBase / 100.0) * pPercent + (pBase + pFlat));
    }

    /**
     * Defense range ("Defense: 10 - 14", or a single number when armor.txt's "minac" equals
     * "maxac"), each end now run through D2Item.applyItemMods()'s own armor formula against its
     * OWN matching flavour -- the low end (armor.txt "minac") through the min-flavoured
     * armourTriple, the high end ("maxac") through the max-flavoured one, per the parent task's own
     * spec (simpler than the weapon-damage treatment below: armor's formula produces only ONE
     * number per call, so there is no second "which flavour computed which side" cross-product to
     * build) -- plus "Chance to Block: " when armor.txt's own "block" column is populated (shields
     * only, in practice; NOT modified here -- iBlock's own modifier, D2Item.applyItemMods()'s pNum
     * 20 shield-block bonus, is out of scope for this change, unmentioned by the parent task). A
     * weapon/misc base simply has neither Defense column, so this contributes nothing for those --
     * no table lookup needed to tell them apart.
     */
    private static void appendArmorStats(StringBuilder pHtml, D2TxtFileItemProperties pRow,
                                          ItemModifiers pMinMods, ItemModifiers pMaxMods) {
        Integer lMinAc = parseIntOrNull(pRow.get("minac"));
        Integer lMaxAc = parseIntOrNull(pRow.get("maxac"));
        if (lMinAc != null || lMaxAc != null) {
            pHtml.append("Defense: ");
            if (lMinAc != null && lMaxAc != null) {
                int lA = applyArmorFormula(lMinAc, pMinMods);
                int lB = applyArmorFormula(lMaxAc, pMaxMods);
                if (lA != lB) {
                    pHtml.append(lA).append(" - ").append(lB);
                } else {
                    pHtml.append(lA);
                }
            } else {
                // A one-ended base row (not observed in practice) has only one flavour's worth of
                // modifiers to apply to it; apply that same single flavour to the one value that
                // exists rather than guessing which of min/max it was meant to pair with.
                Integer lOnlyBase = lMinAc != null ? lMinAc : lMaxAc;
                pHtml.append(applyArmorFormula(lOnlyBase, pMinMods));
            }
            pHtml.append("<br>&#10;");
        }

        // getReq()'s "blank or 0 means absent" semantics, not a plain non-empty check: every
        // non-shield armor row (e.g. helms) still has a real "block" column, just always "0" --
        // D2ItemRenderer only ever shows this line for an actual shield (isShield()), and "0" is
        // exactly how a non-shield row spells "not a shield" here.
        Integer lBlock = getReq(pRow.get("block"));
        if (lBlock != null) {
            pHtml.append("Chance to Block: ").append(lBlock).append("<br>&#10;");
        }
    }

    /**
     * D2Item.applyItemMods()'s own armor formula: {@code floor(baseDef/100.0*armourTriple[0] +
     * (baseDef+armourTriple[1]+armourTriple[2]))} (D2Item.java's iDef assignment) -- EDef percent
     * against the base, plus flat +Def and +Def/lvl (already level-scaled by applyOp(), not scaled
     * again here).
     */
    private static int applyArmorFormula(int pBaseDef, ItemModifiers pMods) {
        return applyPercentPlusFlat(pBaseDef, pMods.iArmourTriple[0], pMods.iArmourTriple[1] + pMods.iArmourTriple[2]);
    }

    /**
     * Damage ranges, mirroring D2ItemRenderer's own hand logic (generatePropStringNoHtmlTags) but
     * driven by weapons.txt column presence instead of a parsed item's iWhichHand/isiThrow(): a
     * javelin's "minmisdam"/"maxmisdam" are simply populated alongside plain "mindam"/"maxdam" (and
     * its "1or2handed"/"2handed" are both blank), so checking "are these columns present" instead
     * of first classifying the weapon by type code reproduces the same "Throw Damage: " + "One Hand
     * Damage: " pairing for a throwable without needing to know it is one.
     * <p>
     * "2handed"=1 (two-hand ONLY) weapons store their real numbers in "2handmindam"/"2handmaxdam",
     * not "mindam"/"maxdam" -- confirmed against D2Item.java's own readExtend2, which loads exactly
     * that column pair into the value it renders as "Two Hand Damage: " for this case.
     */
    private static void appendWeaponStats(StringBuilder pHtml, D2TxtFileItemProperties pRow,
                                           ItemModifiers pMinMods, ItemModifiers pMaxMods) {
        appendDamageRange(pHtml, "Throw Damage: ", parseIntOrNull(pRow.get("minmisdam")), parseIntOrNull(pRow.get("maxmisdam")), pMinMods, pMaxMods);

        if ("1".equals(pRow.get("1or2handed"))) {
            appendDamageRange(pHtml, "One Hand Damage: ", parseIntOrNull(pRow.get("mindam")), parseIntOrNull(pRow.get("maxdam")), pMinMods, pMaxMods);
            appendDamageRange(pHtml, "Two Hand Damage: ", parseIntOrNull(pRow.get("2handmindam")), parseIntOrNull(pRow.get("2handmaxdam")), pMinMods, pMaxMods);
        } else if ("1".equals(pRow.get("2handed"))) {
            appendDamageRange(pHtml, "Two Hand Damage: ", parseIntOrNull(pRow.get("2handmindam")), parseIntOrNull(pRow.get("2handmaxdam")), pMinMods, pMaxMods);
        } else {
            appendDamageRange(pHtml, "One Hand Damage: ", parseIntOrNull(pRow.get("mindam")), parseIntOrNull(pRow.get("maxdam")), pMinMods, pMaxMods);
        }
    }

    /**
     * One weapon damage line, e.g. "One Hand Damage: 148-185 to 357-500" (Wrath of the Seraphim,
     * verified by hand against D2Item.applyItemMods() -- see computeWeaponDamage()'s javadoc). Runs
     * the base (pMin, pMax) pair through D2Item.applyItemMods()'s own weapon-damage formula TWICE
     * -- once entirely under the min-flavoured modifiers, once entirely under the max-flavoured
     * ones -- because that formula itself produces BOTH a low and a high damage number from ONE set
     * of modifiers (exactly as it does for a real item's i1Dmg[1]/i1Dmg[3]). The four resulting
     * numbers then pair up as TWO sides, each formatted independently via formatModifiedValue:
     * "[low damage under min-flavour]-[low damage under max-flavour]" is the min-damage side,
     * "[high damage under min-flavour]-[high damage under max-flavour]" is the max-damage side.
     * <p>
     * When BOTH sides collapse to a single number (an item with no damage-modifying property at
     * all -- dmgTriple all zero in both flavours, so both flavours' formula outputs equal the
     * plain base numbers) this reproduces today's exact "pMin - pMax" format, " - " and all, so an
     * unaffected item's line is byte-identical to before this change. Otherwise the two sides are
     * joined with " to ", e.g. "148-185 to 357-500" -- each side may itself be a single number or a
     * "low-high" range independently (an item might modify only the min or only the max end).
     */
    private static void appendDamageRange(StringBuilder pHtml, String pLabel, Integer pMin, Integer pMax,
                                           ItemModifiers pMinMods, ItemModifiers pMaxMods) {
        if (pMin == null && pMax == null) {
            return;
        }
        pHtml.append(pLabel);
        if (pMin != null && pMax != null) {
            int[] lFromMinFlavour = computeWeaponDamage(pMin, pMax, pMinMods);
            int[] lFromMaxFlavour = computeWeaponDamage(pMin, pMax, pMaxMods);
            boolean lBothSidesSingle = lFromMinFlavour[0] == lFromMaxFlavour[0] && lFromMinFlavour[1] == lFromMaxFlavour[1];
            if (lBothSidesSingle) {
                pHtml.append(lFromMinFlavour[0]).append(" - ").append(lFromMinFlavour[1]);
            } else {
                String lMinSide = formatModifiedValue(lFromMinFlavour[0], lFromMaxFlavour[0]);
                String lMaxSide = formatModifiedValue(lFromMinFlavour[1], lFromMaxFlavour[1]);
                pHtml.append(lMinSide).append(" to ").append(lMaxSide);
            }
        } else {
            // A one-ended base row (not observed in practice, but the pre-existing fallback this
            // replaces already handled it defensively) -- D2Item.applyItemMods()'s own formula
            // needs BOTH ends of the base range to run at all, so a lone end is shown unmodified,
            // exactly as it always was.
            pHtml.append(pMin != null ? pMin : pMax);
        }
        pHtml.append("<br>&#10;");
    }

    /**
     * D2Item.applyItemMods()'s own weapon-damage formula (D2Item.java's i1Dmg[1]/i1Dmg[3]
     * assignment), run against ONE flavour's modifiers:
     * <pre>
     *   min = floor(baseMin/100.0 * dmgTriple[0] + (baseMin + dmgTriple[1]))
     *   max = floor(baseMax/100.0 * (dmgTriple[0] + dmgTriple[4]) + (baseMax + dmgTriple[2] + dmgTriple[3]))
     *   if (min > max) { max = min + 1; }
     * </pre>
     * Verified by hand against Wrath of the Seraphim (base 7ws, weapons.txt mindam=37, maxdam=43)
     * at ASSUMED_CHARACTER_LEVEL 99, whose prop1 "dmg%" (300-400), prop2 "dmg-max" (100-200) and
     * prop5 "dmg%/lvl" (par=16, -> 198 in BOTH flavours, already level-scaled by applyOp() before
     * this method ever runs) resolve to dmgTriple[0]=300/400 and dmgTriple[2]=100/200
     * (min-/max-flavoured respectively) and dmgTriple[4]=198 (both flavours): min-flavoured ->
     * (148, 357), max-flavoured -> (185, 500) -- matching the mod's own website's "148-185" low end
     * exactly (the website's "345-501" high end differs only because it assumes a different
     * character level for the per-level stat, not a defect in this formula).
     */
    private static int[] computeWeaponDamage(int pBaseMin, int pBaseMax, ItemModifiers pMods) {
        int lMin = applyPercentPlusFlat(pBaseMin, pMods.iDmgTriple[0], pMods.iDmgTriple[1]);
        int lMax = applyPercentPlusFlat(pBaseMax, pMods.iDmgTriple[0] + pMods.iDmgTriple[4], pMods.iDmgTriple[2] + pMods.iDmgTriple[3]);
        if (lMin > lMax) {
            lMax = lMin + 1;
        }
        return new int[]{lMin, lMax};
    }

    /**
     * The base maximum durability, or "Indestructible" when "nodurability" is set (never modified
     * -- an indestructible item has no maximum to raise) -- never a "current of max" figure, since
     * a missing entry has no instance to have taken wear on. Otherwise D2Item.applyItemMods()'s own
     * durability formula (D2Item.java's iMaxDur assignment, {@code floor(maxDur/100.0*durTriple[1]
     * + (maxDur+durTriple[0]))}) is run once per flavour against the base "durability" column, then
     * formatted the same "single value, or low-high when the flavours differ" way as every other
     * single-base-value stat below (Required Level/Strength/Dexterity).
     */
    private static void appendDurability(StringBuilder pHtml, D2TxtFileItemProperties pRow,
                                          ItemModifiers pMinMods, ItemModifiers pMaxMods) {
        if ("1".equals(pRow.get("nodurability"))) {
            pHtml.append("Indestructible<br>&#10;");
            return;
        }
        Integer lDurability = parseIntOrNull(pRow.get("durability"));
        if (lDurability != null) {
            int lLow = applyPercentPlusFlat(lDurability, pMinMods.iDurTriple[1], pMinMods.iDurTriple[0]);
            int lHigh = applyPercentPlusFlat(lDurability, pMaxMods.iDurTriple[1], pMaxMods.iDurTriple[0]);
            pHtml.append("Durability: ").append(formatModifiedValue(lLow, lHigh)).append("<br>&#10;");
        }
    }

    /**
     * Required Level is NOT simply the base row's own "levelreq" -- D2Item.java raises it from the
     * unique/set row at parse time (readExtend, cases 7 and 5 respectively), and a missing entry's
     * tooltip must mirror that precedence or it understates what the item actually requires:
     * <ul>
     *   <li>Unique: the unique's own "lvl req" column always wins over the base's "levelreq" when
     *   it parses to a real requirement (D2Item.java's own check, "lUnique.get(code).equals(
     *   item_type)", is trivially true here -- pEntry's base code IS that unique row's own "code"
     *   column, by construction).</li>
     *   <li>Set item: the set item's own "lvl req" wins over the base's "levelreq" only when it is
     *   HIGHER (D2Item.java: "lSetReq != -1 && lSetReq > iReqLvl") -- a set piece is never required
     *   at a LOWER level than its own base item would otherwise demand.</li>
     * </ul>
     */
    private static Integer requiredLevel(D2GrailEntry pEntry, D2TxtFileItemProperties pBaseRow) {
        Integer lBaseLevel = getReq(pBaseRow.get("levelreq"));
        D2TxtFileItemProperties lSourceRow = pEntry.getSourceRow();
        if (lSourceRow == null) {
            return lBaseLevel;
        }

        if (pEntry.getKey().getType() == D2GrailKey.Type.UNIQUE) {
            Integer lUniqueLevel = getReq(lSourceRow.get("lvl req"));
            return lUniqueLevel != null ? lUniqueLevel : lBaseLevel;
        }
        if (pEntry.getKey().getType() == D2GrailKey.Type.SET) {
            Integer lSetLevel = getReq(lSourceRow.get("lvl req"));
            if (lSetLevel != null && (lBaseLevel == null || lSetLevel > lBaseLevel)) {
                return lSetLevel;
            }
            return lBaseLevel;
        }
        return lBaseLevel;
    }

    /**
     * D2Item.getReq()'s exact semantics, reproduced here for a missing entry with no D2Item to ask:
     * blank, "0" and anything non-numeric all mean "no requirement" (null), never a rendered "0".
     */
    private static Integer getReq(String pValue) {
        if (pValue == null) {
            return null;
        }
        String lTrimmed = pValue.trim();
        if (lTrimmed.isEmpty() || "0".equals(lTrimmed)) {
            return null;
        }
        try {
            return Integer.parseInt(lTrimmed);
        } catch (NumberFormatException pEx) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String pValue) {
        if (pValue == null) {
            return null;
        }
        String lTrimmed = pValue.trim();
        if (lTrimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(lTrimmed);
        } catch (NumberFormatException pEx) {
            return null;
        }
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
            List<PropSlot> lSlots = new ArrayList<PropSlot>();
            if (pItemRow != null) {
                addSlotIfPresent(lSlots, pItemRow,
                        "aprop" + x + "a", "amin" + x + "a", "amax" + x + "a", "apar" + x + "a");
                addSlotIfPresent(lSlots, pItemRow,
                        "aprop" + x + "b", "amin" + x + "b", "amax" + x + "b", "apar" + x + "b");
            }
            if (lFullSetRow != null) {
                addSlotIfPresent(lSlots, lFullSetRow,
                        "PCode" + lThreshold + "a", "PMin" + lThreshold + "a", "PMax" + lThreshold + "a",
                        "PParam" + lThreshold + "a");
            }
            if (lSlots.isEmpty()) {
                continue;
            }
            // See ASSUMED_CHARACTER_LEVEL's javadoc: applyOp() (run inside renderSlotsWithRanges,
            // for both the min- and max-flavoured pass) is what actually does the per-level math
            // (e.g. Cleglaw's Pincers' aprop1a "att/lvl" par=20 -> "+850" at level 85) --
            // generateDisplay() alone never scales anything by level.
            pHtml.append("<font color='red'>Set (").append(lThreshold).append(" items): </font>")
                    .append(renderSlotsWithRanges(lSlots));
        }

        if (lFullSetRow != null) {
            List<PropSlot> lSlots = new ArrayList<PropSlot>();
            for (int i = 1; i <= 8; i++) {
                addSlotIfPresent(lSlots, lFullSetRow, "FCode" + i, "FMin" + i, "FMax" + i, "FParam" + i);
            }
            if (!lSlots.isEmpty()) {
                pHtml.append("<font color='red'>Full Set Bonus: </font>")
                        .append(renderSlotsWithRanges(lSlots));
            }
        }
    }

    /**
     * One property slot's four raw .txt columns (code, min, max, param), captured BEFORE any
     * conversion through D2TxtFile.propToStat(). A missing entry's tooltip needs to run
     * propToStat() twice per slot -- once "min-flavoured", once "max-flavoured" (see
     * renderSlotsWithRanges) -- so the raw columns are kept around as plain strings here rather
     * than immediately resolved into a single D2Prop the way the item-parsing path
     * (D2Item/D2PropCollection, both left untouched by this change) does.
     */
    private static final class PropSlot {
        private final String iCode;
        private final String iMin;
        private final String iMax;
        private final String iParam;

        private PropSlot(String pCode, String pMin, String pMax, String pParam) {
            iCode = pCode;
            iMin = pMin;
            iMax = pMax;
            iParam = pParam;
        }
    }

    /**
     * Reads one property slot (a code column plus its matching min/max/param columns) off a .txt
     * row and, if the code is non-empty, adds its RAW columns to pInto as a PropSlot -- unmodified,
     * exactly as they appear in the .txt row. A blank code (no bonus in that slot) is silently
     * skipped, exactly as the old single-pass addPropIfPresent this replaces did.
     * <p>
     * Deliberately no normalization here: whether (and how) a slot's min/max columns get adjusted
     * before propToStat() sees them depends on whether this code's columns are even a real
     * min/max PAIR in the first place (properties.txt's own "uiRangeType" column) -- a decision
     * renderSlotsWithRanges makes per-slot, using these untouched raw strings, not this method.
     */
    private static void addSlotIfPresent(List<PropSlot> pInto, D2TxtFileItemProperties pRow,
                                          String pCodeColumn, String pMinColumn, String pMaxColumn, String pParamColumn) {
        String lCode = pRow.get(pCodeColumn);
        if (lCode == null || lCode.isEmpty()) {
            return;
        }
        pInto.add(new PropSlot(lCode, nullToEmpty(pRow.get(pMinColumn)), nullToEmpty(pRow.get(pMaxColumn)),
                pRow.get(pParamColumn)));
    }

    /**
     * True when properties.txt's own "uiRangeType" column for this code is blank -- the mod's own
     * table saying its "min"/"max" columns really are the two ends of ONE ranged number, safe to
     * feed through the two-pass min-flavoured/max-flavoured machinery below. Confirmed blank (and
     * therefore eligible) for every code this feature actually needs to range: dmg%, dmg-max,
     * dmg-min, res-cold, lifesteal, cheap, and so on.
     * <p>
     * A non-blank uiRangeType documents the two columns as something else entirely -- confirmed
     * against real properties.txt rows: 7 (hit-skill, death-skill) is "% Chance" + "Skill Level",
     * 6 (charged) is "# of Max Charges" + "Skill Level", 2 (skill-rand) is "Min Skill ID" + "Max
     * Skill ID", 5 (skill, oskill) is a skill LEVEL range whose skill id lives in a separate "par"
     * column. None of those pairs are "the low end and the high end of one number" -- merging them
     * as if they were invents nonsense: uniqueitems.txt's real "Fallen Hero's Disgrace" prop8
     * "death-skill" (min=100 "% Chance", max=15 "Skill Level") is not a "15-100" or "100-15" range
     * of anything, and a hit-skill row whose chance happens to be numerically lower than its skill
     * level (e.g. chance 5, level 10) would otherwise merge into an equally bogus invented "5-10".
     * So a non-blank uiRangeType is NOT eligible; renderSlotsWithRanges falls back to feeding that
     * slot's original, unmodified columns to both passes instead, reproducing today's single
     * collapsed value exactly.
     * <p>
     * uiRangeType 5 (skill/oskill, the level-range-plus-separate-skill-id shape) stays excluded:
     * its "min"/"max" are a skill LEVEL range whose skill id lives in the separate "par" column, so
     * they are not one number's two ends any more than the other non-blank uiRangeTypes are.
     * D2TxtFile.propToStat now resolves that par into the skill id itself (see its parNamesASkill /
     * addSkillParamProps pair, added for the runeword tooltip's auras and granted skills), so these
     * slots do render the right skill today -- at the top of their level range, which is what the
     * single-pass path showed before. Ranging the level as well would be a separate change; this
     * method's job is only to decide what is safe to RANGE, and a pair that is not one number is
     * not.
     * <p>
     * A SECOND, independent exclusion, on top of uiRangeType: properties.txt's own func1/func2
     * columns can encode "min feeds one stat, max feeds a DIFFERENT stat" -- func1=15 paired with
     * func2=16 -- even when uiRangeType is blank. Confirmed against every ./d2111 code used with
     * min&lt;max and a non-empty stat2, with no overlap between the two families:
     * <ul>
     *   <li>func1=15/func2=16 -- min and max are NEVER a range, always two distinct stats: real
     *   codes "dmg-norm" (stat1=mindamage, stat2=maxdamage), "dmg-fire"/"dmg-cold"/"dmg-ltng"/
     *   "dmg-mag"/"dmg-elem"/"dmg-pois" (the same min-stat/max-stat shape, one pair per element).
     *   Confirmed live: uniqueitems.txt's real "Hand of Blessed Light" prop7 "dmg-norm" (min=20,
     *   max=45) is NOT "the item adds a randomly-rolled 20-45 damage bonus" -- it is "+20 to
     *   Minimum Damage AND +45 to Maximum Damage, always both, every time" -- so ranging it merged
     *   the min-flavoured pass's "Adds 20 - 20 Damage" and the max-flavoured pass's "Adds 45 - 45
     *   Damage" into the doubled garbage "Adds 20-45 - 20-45 Damage"; excluding it instead feeds
     *   both passes the SAME original (20, 45) pair, restoring today's correct, pre-existing
     *   "Adds 20 - 45 Damage" (D2Prop's own combined-damage rendering, not this class's range
     *   merge) -- and, in turn, correctly feeds dmgTriple[1]=20/dmgTriple[2]=45 identically into
     *   BOTH flavours' weapon-damage base-stat computation (accumulateItemMods), exactly what a
     *   found copy of the same item yields from its bitstream.</li>
     *   <li>func1=1 or 21, func2=3 -- a genuine range: the SAME single rolled value is copied to
     *   several stats at once (e.g. one resistance roll applied to all four elements). Real codes:
     *   "res-all"/"res-all-max"/"all-stats" (a plain func1=1 stat1, then func2=3/func3=3/func4=3
     *   copying the identical value to sibling stats), "fireskill"/"lightningskill"/"magicskill"
     *   (func1=21 stat1=item_elemskill, func2=3 stat2=item_elemskill&lt;element&gt;), "pierce-elem"/
     *   "extra-elem" (func1=1, func2/3/4=3). These stay eligible -- deliberately NOT excluded by
     *   this second check, since their min/max genuinely are one ranged roll.</li>
     * </ul>
     * Keyed on func1/func2 directly, NOT on stat1/stat2's NAMES containing "min"/"max": a
     * name-based rule would misclassify "res-all-max" (a genuine range, family B above) purely
     * because its stat1 happens to be named "maxfireresist".
     * <p>
     * A code with no properties.txt row at all -- should not happen for anything that already
     * survived a real propToStat() call, but defended against here anyway, the same way
     * D2TxtFile.propToStat() itself treats a null PROPS row as "give up, don't guess" -- is treated
     * as NOT eligible: an unknown shape must never be assumed safe to merge.
     */
    private static boolean isRangeEligible(String pCode) {
        D2TxtFileItemProperties lPropsRow = D2TxtFile.PROPS.searchColumns("code", pCode);
        if (lPropsRow == null) {
            return false;
        }
        String lUiRangeType = lPropsRow.get("uiRangeType");
        if (lUiRangeType != null && !lUiRangeType.isEmpty()) {
            return false;
        }
        // "15"/"16" are properties.txt's own func-column CODES for "write the min column into
        // stat1" / "write the max column into stat2" -- i.e. two DIFFERENT stats, never a range.
        return !("15".equals(lPropsRow.get("func1")) && "16".equals(lPropsRow.get("func2")));
    }

    // The literal separator D2PropCollection.generateDisplay() puts after every rendered line --
    // see its source: "arrOut.append(val).append(\"<br>&#10;\")" -- reused here, rather than
    // re-deriving it, as both the split delimiter and the re-join glue in mergeRangeFragments.
    private static final String LINE_SEPARATOR = "<br>&#10;";

    // Matches one signed integer token -- the unit mergeRangeFragments merges independently within
    // an otherwise-identical line (e.g. the "300" and "400" in "+300-400% Enhanced Damage").
    private static final Pattern NUMBER_TOKEN = Pattern.compile("-?\\d+");

    /**
     * Renders a list of raw property slots as a RANGE where the underlying .txt row actually has
     * one ("+300-400% Enhanced Damage", the mod's own website convention), rather than
     * D2TxtFile.propToStat()'s single collapsed value -- without changing propToStat() or
     * D2PropCollection themselves, since both are shared with the real-item parsing path (a found
     * item has one real rolled value, not a range, and must keep rendering exactly as it does
     * today).
     * <p>
     * Runs the whole propToStat -> tidy() -> applyOp() -> generateDisplay() pipeline TWICE for
     * every slot whose properties.txt code is actually range-eligible (isRangeEligible -- a blank
     * "uiRangeType" column, meaning its min/max columns really are the two ends of one number):
     * once feeding the slot's MIN column as both the "min" and "max" argument (so propToStat's own
     * "pVals[0] = pVals[1]" max-collapsing branch becomes a no-op and every stat resolves to its
     * low end, regardless of which branch propToStat takes for that particular stat), once feeding
     * the slot's MAX column the same way. Before either call, a lone blank end is normalized to
     * match its non-blank sibling -- confirmed real: Gheed's Wager prop8 "cheap" (min=10, max
     * blank) would otherwise have its max-flavoured pass call propToStat(code, "", "", param, 0),
     * both arguments blank, resolving to a bare 0 (propToStat's own "pVals[0]=0 default, nothing
     * assigns it" path) rather than "the top of the real range" -- and then merge min=10/max=0 into
     * a backwards-range fallback that regresses the real, correct "10% Reduced Vendor Prices" down
     * to "0%". A slot with BOTH columns blank (a genuine per-level property -- e.g. Wrath of the
     * Seraphim's own "dmg%/lvl" par=16, min/max both absent, the value carried in "par" instead) is
     * left untouched by this normalization: propToStat's per-level branch requires min AND max to
     * both still be "" to fire at all, so normalizing either one away would break every per-level
     * property's own display instead. Either way, a range-eligible slot with equal ends (the
     * overwhelmingly common case) simply produces the same value from both passes.
     * <p>
     * A slot whose code is NOT range-eligible (isRangeEligible false -- its min/max columns are
     * documented as something other than one ranged number, e.g. hit-skill's "% Chance" + "Skill
     * Level") instead feeds its ORIGINAL, unmodified min AND max columns to BOTH passes -- the
     * exact single-pass propToStat(code, min, max, param, 0) call this whole feature's
     * predecessor, addPropIfPresent, always made -- so it renders byte-for-byte as it always has,
     * never a range, never a changed value.
     * <p>
     * The two resulting HTML fragments are then merged line-by-line, number-by-number (see
     * mergeRangeFragments): identical fragments (every ineligible slot, plus every eligible slot
     * whose ends happen to be equal -- together the overwhelming majority) come back unchanged,
     * and only the numbers that actually differ turn into "min-max"/"min to max" text, never
     * touching the surrounding markup or label text.
     * <p>
     * Split into buildFlavouredProps() (this method delegates to it) and renderFlavouredProps()
     * so a caller that also needs the base-stat modifier accumulation (missingTooltip, for its
     * item's own prop1..N -- see appendMissingBaseStats) can build the two collections ONCE and
     * use them for both jobs, rather than this method's own callers (the set-bonus sections, which
     * never feed into base-stat modifiers -- see missingTooltip's own javadoc note on why) needing
     * to care about that split at all.
     */
    private static String renderSlotsWithRanges(List<PropSlot> pSlots) {
        return renderFlavouredProps(buildFlavouredProps(pSlots));
    }

    /**
     * One property section's two flavoured D2PropCollections -- the min-flavoured and
     * max-flavoured passes described on renderSlotsWithRanges, already tidy()'d and applyOp()'d,
     * but not yet rendered to HTML. Exists so a caller can reuse the SAME two collections for more
     * than one job (missingTooltip does: once for its base-stat lines' modifier accumulation via
     * accumulateItemMods(), once for the property lines themselves via renderFlavouredProps())
     * without running propToStat/tidy/applyOp twice over.
     */
    private static final class FlavouredProps {
        private final D2PropCollection iMin;
        private final D2PropCollection iMax;

        private FlavouredProps(D2PropCollection pMin, D2PropCollection pMax) {
            iMin = pMin;
            iMax = pMax;
        }
    }

    /**
     * Builds the two flavoured D2PropCollections described on renderSlotsWithRanges's javadoc
     * (min-flavoured and max-flavoured, each range-eligibility-checked, blank-column-normalized,
     * tidy()'d and applyOp()'d) without rendering them to HTML yet -- see FlavouredProps and
     * renderFlavouredProps.
     */
    private static FlavouredProps buildFlavouredProps(List<PropSlot> pSlots) {
        D2PropCollection lMinProps = new D2PropCollection();
        D2PropCollection lMaxProps = new D2PropCollection();
        for (int i = 0; i < pSlots.size(); i++) {
            PropSlot lSlot = pSlots.get(i);
            try {
                // Both calls must succeed, or neither is kept: keeping only one pass's resolution
                // of a slot that throws on the other pass would desync the min/max fragments' line
                // counts, sending mergeRangeFragments down its "different piece counts" whole-
                // fragment fallback for every OTHER slot in the same section too, not just the one
                // that actually failed.
                ArrayList lMinStats;
                ArrayList lMaxStats;
                if (isRangeEligible(lSlot.iCode)) {
                    String lMinArg = lSlot.iMin;
                    String lMaxArg = lSlot.iMax;
                    if (lMinArg.isEmpty() && !lMaxArg.isEmpty()) {
                        lMinArg = lMaxArg;
                    } else if (lMaxArg.isEmpty() && !lMinArg.isEmpty()) {
                        lMaxArg = lMinArg;
                    }
                    lMinStats = D2TxtFile.propToStat(lSlot.iCode, lMinArg, lMinArg, lSlot.iParam, 0);
                    lMaxStats = D2TxtFile.propToStat(lSlot.iCode, lMaxArg, lMaxArg, lSlot.iParam, 0);
                } else {
                    // Not a real min/max pair (see isRangeEligible) -- both passes get the row's
                    // own original columns, unmodified, so they resolve to identical D2Props and
                    // this slot renders exactly as the old single collapsed-value pass always did.
                    lMinStats = D2TxtFile.propToStat(lSlot.iCode, lSlot.iMin, lSlot.iMax, lSlot.iParam, 0);
                    lMaxStats = D2TxtFile.propToStat(lSlot.iCode, lSlot.iMin, lSlot.iMax, lSlot.iParam, 0);
                }
                //noinspection unchecked
                lMinProps.addAll(lMinStats);
                //noinspection unchecked
                lMaxProps.addAll(lMaxStats);
            } catch (RuntimeException pEx) {
                // One malformed property slot must not blank out every other real one.
            }
        }
        lMinProps.tidy();
        lMinProps.applyOp(ASSUMED_CHARACTER_LEVEL);
        lMaxProps.tidy();
        lMaxProps.applyOp(ASSUMED_CHARACTER_LEVEL);
        return new FlavouredProps(lMinProps, lMaxProps);
    }

    /**
     * Renders an already-built FlavouredProps to HTML: generateDisplay() on each of the two
     * collections (read-only -- safe to call more than once on the same FlavouredProps, unlike
     * tidy()/applyOp(), which buildFlavouredProps already ran exactly once each), then merged via
     * mergeRangeFragments.
     */
    private static String renderFlavouredProps(FlavouredProps pProps) {
        String lMinHtml = pProps.iMin.generateDisplay(0, ASSUMED_CHARACTER_LEVEL).toString();
        String lMaxHtml = pProps.iMax.generateDisplay(0, ASSUMED_CHARACTER_LEVEL).toString();
        return mergeRangeFragments(lMinHtml, lMaxHtml);
    }

    /**
     * Merges a "min-flavoured" and a "max-flavoured" D2PropCollection.generateDisplay() fragment
     * (each shaped {@code <font color="...">line<br>&#10;line<br>&#10;</font>}) into one fragment
     * that shows a real range wherever the two disagree. Package-private (not private) so
     * D2GrailListRendererRangeTest can exercise it directly, alongside the higher-level
     * D2GrailListRenderer.tooltipFor() coverage.
     * <p>
     * The common case -- most stats have no min/max spread at all -- short-circuits on equality.
     * Otherwise the two fragments are split into lines and merged line-by-line (mergeLinePiece);
     * a mismatched line COUNT between the two passes (should not happen given the identical
     * propToStat/tidy/applyOp pipeline run on each side, but a future propToStat change could in
     * principle make min and max resolve to a different number of D2Props) falls back to the max
     * fragment whole, rather than risk zipping unrelated lines together.
     */
    static String mergeRangeFragments(String pMinFragment, String pMaxFragment) {
        if (pMinFragment.equals(pMaxFragment)) {
            return pMaxFragment;
        }
        String[] lMinPieces = pMinFragment.split(Pattern.quote(LINE_SEPARATOR), -1);
        String[] lMaxPieces = pMaxFragment.split(Pattern.quote(LINE_SEPARATOR), -1);
        if (lMinPieces.length != lMaxPieces.length) {
            return pMaxFragment;
        }
        StringBuilder lOut = new StringBuilder();
        for (int i = 0; i < lMinPieces.length; i++) {
            if (i > 0) {
                lOut.append(LINE_SEPARATOR);
            }
            lOut.append(mergeLinePiece(lMinPieces[i], lMaxPieces[i]));
        }
        return lOut.toString();
    }

    /**
     * Merges one min/max pair of same-position pieces (one rendered line, or the leading
     * "&lt;font...&gt;"-plus-first-line / trailing "&lt;/font&gt;" pieces the split in
     * mergeRangeFragments produces at the ends of the fragment). Tokenizes both sides into
     * alternating literal/number tokens (tokenize()); if the token counts differ, or ANY literal
     * token differs between the two sides (different property resolved, different wording, a
     * property that appeared in one pass but not the other -- any of which means these two pieces
     * are no longer "the same line at its two ends"), the max piece is returned unchanged rather
     * than splicing mismatched text together. Otherwise every literal passes through verbatim and
     * every number position is merged via mergeNumberToken.
     */
    static String mergeLinePiece(String pMinPiece, String pMaxPiece) {
        List<String> lMinTokens = tokenize(pMinPiece);
        List<String> lMaxTokens = tokenize(pMaxPiece);
        if (lMinTokens.size() != lMaxTokens.size()) {
            return pMaxPiece;
        }
        // Token lists alternate literal, number, literal, ..., literal (always odd length, per
        // tokenize()'s own contract) -- so every EVEN index is a literal; check all of them before
        // building anything, since one mismatched literal anywhere in the piece disqualifies the
        // whole piece, not just the number tokens around it.
        for (int i = 0; i < lMinTokens.size(); i += 2) {
            if (!lMinTokens.get(i).equals(lMaxTokens.get(i))) {
                return pMaxPiece;
            }
        }
        StringBuilder lOut = new StringBuilder();
        for (int i = 0; i < lMinTokens.size(); i++) {
            if (i % 2 == 0) {
                lOut.append(lMinTokens.get(i)); // literal, already confirmed identical above
            } else {
                lOut.append(mergeNumberToken(lMinTokens.get(i), lMaxTokens.get(i)));
            }
        }
        return lOut.toString();
    }

    /**
     * Splits pText into alternating literal/number tokens around every signed-integer match (e.g.
     * "+300-400% Enhanced Damage" against NUMBER_TOKEN's own "-?\d+" -- deliberately the same
     * pattern text, not reused as a shared constant with anything in D2Prop, since the two never
     * need to agree on the regex source, only on matching what a rendered number actually looks
     * like). The result always has odd length -- literal, number, literal, ..., literal -- even
     * when pText has no digits at all (a single-element list, the whole text as one literal) or
     * starts/ends with a digit (an empty leading/trailing literal).
     */
    private static List<String> tokenize(String pText) {
        List<String> lTokens = new ArrayList<String>();
        Matcher lMatcher = NUMBER_TOKEN.matcher(pText);
        int lLastEnd = 0;
        while (lMatcher.find()) {
            lTokens.add(pText.substring(lLastEnd, lMatcher.start()));
            lTokens.add(lMatcher.group());
            lLastEnd = lMatcher.end();
        }
        lTokens.add(pText.substring(lLastEnd));
        return lTokens;
    }

    /**
     * Merges one min/max pair of number tokens into the text that should appear at that position:
     * <ul>
     *   <li>Equal strings (the overwhelmingly common case: most stats have no range at all) ->
     *   that value, unchanged.</li>
     *   <li>Both parse as non-negative ints with min &lt; max -> "min-max" (e.g. "300-400"),
     *   matching the mod's own website convention (confirmed against Wrath of the Seraphim's real
     *   dmg% 300/400 -> "+300-400% Enhanced Damage").</li>
     *   <li>Either value is negative -> "min to max" (e.g. Cold Rupture's real res-cold -90/-70 ->
     *   "-90 to -70"), never the literal min+"-"+max concatenation: "-10"+"-"+"-20" would read as
     *   the unreadable, ambiguous "-10--20".</li>
     *   <li>min &gt; max (a non-negative pair that resolved backwards -- e.g. one side's slot
     *   normalization or propToStat's own quirks produced a decreasing pair) or either token fails
     *   to parse as an int at all -> the max token alone, never an invented backwards range.</li>
     * </ul>
     */
    static String mergeNumberToken(String pMinToken, String pMaxToken) {
        if (pMinToken.equals(pMaxToken)) {
            return pMinToken;
        }
        int lMin;
        int lMax;
        try {
            lMin = Integer.parseInt(pMinToken);
            lMax = Integer.parseInt(pMaxToken);
        } catch (NumberFormatException pEx) {
            return pMaxToken;
        }
        if (lMin < 0 || lMax < 0) {
            return pMinToken + " to " + pMaxToken;
        }
        if (lMin < lMax) {
            return pMinToken + "-" + pMaxToken;
        }
        return pMaxToken;
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
