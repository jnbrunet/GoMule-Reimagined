package gomule.grail;

import gomule.item.D2ItemRenderer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The filtering/statistics engine behind the Holy Grail window: takes the fixed
 * {@link D2GrailIndex} entries plus a (replaceable) findings map from {@link D2GrailScanner}, and
 * turns the window's current control state (tab, tier checkboxes, category/class tree selection,
 * status dropdown, search text, "Include non-Chronicle items") into the rows and progress numbers
 * {@code D2ViewGrail} actually renders.
 * <p>
 * Deliberately Swing-free -- no {@code JTree}, no {@code javax.swing.*} import anywhere in this
 * file -- so it is fully unit-testable headless. It does not know how to scan anything either: a
 * rescan is "call {@link D2GrailScanner#scan}, then {@link #setFindings}"; toggling any filter
 * (the Chronicle-scope checkbox in particular, per plan section 6.2) never touches the findings
 * map and therefore can never trigger one.
 */
public final class D2GrailModel {

    /**
     * The `Show:` dropdown (plan section 7).
     */
    public enum Status {
        ALL,
        FOUND,
        MISSING
    }

    // Category/class tree selection is modelled as a plain String id rather than a Swing tree
    // node, precisely so this class stays UI-framework-free. D2ViewGrail builds its JTree nodes
    // using these same ids (via the id(...) factory methods below) so the two sides can never
    // disagree on the string format.
    public static final String ALL_ID = "ALL";

    // Uniques tab only: the 25 real entries with no resolvable UICategory (10 reserved unique
    // slots with a blank base code, 15 "grab"/"stor" utility items -- see D2GrailIndexTest) must
    // stay reachable somewhere in the tree rather than silently vanishing. This is that "somewhere".
    public static final String UNCATEGORIZED_ID = "UNCATEGORIZED";

    private static final String ROOT_PREFIX = "ROOT:";
    private static final String CATEGORY_PREFIX = "CAT:";
    private static final String CLASS_PREFIX = "CLASS:";

    public static String rootId(D2GrailCategories.RootGroup pRoot) {
        return ROOT_PREFIX + pRoot.name();
    }

    public static String categoryId(String pCategoryCode) {
        return CATEGORY_PREFIX + pCategoryCode;
    }

    /**
     * @param pUiClassCode a sets.txt UIClass code, "" for General.
     */
    public static String classId(String pUiClassCode) {
        return CLASS_PREFIX + (pUiClassCode == null ? "" : pUiClassCode);
    }

    private final List<D2GrailEntry> iAllEntries;
    private Map<D2GrailKey, D2GrailFinding> iFindings = Collections.emptyMap();

    private D2GrailKey.Type iTab = D2GrailKey.Type.UNIQUE;
    private final EnumSet<D2GrailEntry.Tier> iEnabledTiers =
            EnumSet.of(D2GrailEntry.Tier.NORMAL, D2GrailEntry.Tier.EXCEPTIONAL, D2GrailEntry.Tier.ELITE);
    private boolean iIncludeNonChronicle = false;
    private String iCategorySelection = ALL_ID;
    private Status iStatus = Status.ALL;
    private String iSearchText = "";

    // Memo for setPassesTier(): set name -> "at least one of this set's pieces is in an enabled
    // tier". Depends only on the tier checkboxes and the Chronicle scope, so it is cleared exactly
    // when either changes and nowhere else -- the entry list itself never changes for the life of a
    // model. Without it, the Sets tab would walk every set item once per row, per repaint.
    private final Map<String, Boolean> iSetTierPass = new HashMap<>();

    /**
     * Production constructor: filters over the real, complete grail index. Requires
     * {@code D2TxtFile.constructTxtFiles(...)} to already have been called, exactly like
     * {@link D2GrailIndex#getEntries()} itself does.
     */
    public D2GrailModel() {
        this(D2GrailIndex.getEntries());
    }

    /**
     * Test/advanced constructor over an explicit entry list, so a test can build a small,
     * hand-picked scenario instead of depending on the exact real counts of ./d2111.
     */
    public D2GrailModel(List<D2GrailEntry> pEntries) {
        iAllEntries = pEntries;
    }

    // --------------------------------------------------------------------------------------
    // Findings
    // --------------------------------------------------------------------------------------

    /**
     * Replaces the findings map wholesale -- what a rescan (D2GrailScanner.scan(...) call) is.
     * Never called by any filter setter below: filtering and scanning are deliberately
     * independent, so flipping a checkbox is always instantaneous and never re-walks a single item.
     */
    public void setFindings(Map<D2GrailKey, D2GrailFinding> pFindings) {
        iFindings = pFindings == null ? Collections.<D2GrailKey, D2GrailFinding>emptyMap() : pFindings;
    }

    public Map<D2GrailKey, D2GrailFinding> getFindings() {
        return iFindings;
    }

    private D2GrailFinding findingFor(D2GrailEntry pEntry) {
        return iFindings.get(pEntry.getKey());
    }

    // --------------------------------------------------------------------------------------
    // Filter state
    // --------------------------------------------------------------------------------------

    public void setTab(D2GrailKey.Type pTab) {
        iTab = pTab;
    }

    public D2GrailKey.Type getTab() {
        return iTab;
    }

    /**
     * Normal/Exceptional/Elite checkboxes. Meaningless (and inert -- see {@link #passesTier}) on
     * the Runewords tab, where every entry is {@link D2GrailEntry.Tier#NONE}; the window disables
     * the checkboxes there rather than this model refusing to apply them, so this setter itself
     * has no tab-awareness.
     * <p>
     * Whether at least one tier stays enabled is a UI concern (the window refuses to let the user
     * uncheck the last one) -- this model tolerates an empty set just fine, it simply matches no
     * unique/set entry.
     */
    public void setTierEnabled(D2GrailEntry.Tier pTier, boolean pEnabled) {
        if (pEnabled) {
            iEnabledTiers.add(pTier);
        } else {
            iEnabledTiers.remove(pTier);
        }
        iSetTierPass.clear();
    }

    public boolean isTierEnabled(D2GrailEntry.Tier pTier) {
        return iEnabledTiers.contains(pTier);
    }

    /**
     * Plan section 7: unchecked (default) shows only chronicle=true entries -- the in-game grail;
     * checked shows every entry in the table. Deliberately does not touch {@link #iFindings}: a
     * discovery made before this was ever checked must appear immediately once it is, with no
     * rescan involved (plan section 6.2).
     */
    public void setIncludeNonChronicle(boolean pInclude) {
        iIncludeNonChronicle = pInclude;
        iSetTierPass.clear();
    }

    public boolean isIncludeNonChronicle() {
        return iIncludeNonChronicle;
    }

    /**
     * One of {@link #ALL_ID}, {@link #UNCATEGORIZED_ID}, or an id built by {@link #rootId},
     * {@link #categoryId} or {@link #classId}. An id that doesn't apply to the current tab (e.g. a
     * CLASS: id while on the Uniques tab) is simply never matched by {@link #passesCategory} --
     * see that method -- rather than throwing, since a tab switch while a tree selection survives
     * is exactly the "no exception, no stale selection" case the plan's manual checklist calls out.
     */
    public void setCategorySelection(String pSelection) {
        iCategorySelection = pSelection == null ? ALL_ID : pSelection;
    }

    public String getCategorySelection() {
        return iCategorySelection;
    }

    public void setStatus(Status pStatus) {
        iStatus = pStatus == null ? Status.ALL : pStatus;
    }

    public Status getStatus() {
        return iStatus;
    }

    /**
     * Case- and accent-insensitive, and tolerant of raw ÿc. color codes in either the search text
     * or the entry names it is compared against (plan section 7 / section 12) -- see
     * {@link #foldForSearch}.
     */
    public void setSearchText(String pText) {
        iSearchText = pText == null ? "" : pText;
    }

    public String getSearchText() {
        return iSearchText;
    }

    // --------------------------------------------------------------------------------------
    // Rows
    // --------------------------------------------------------------------------------------

    /**
     * One displayable line: a grail entry plus whatever was found for it (null = missing).
     */
    public static final class Row {
        private final D2GrailEntry iEntry;
        private final D2GrailFinding iFinding;

        Row(D2GrailEntry pEntry, D2GrailFinding pFinding) {
            iEntry = pEntry;
            iFinding = pFinding;
        }

        public D2GrailEntry getEntry() {
            return iEntry;
        }

        /**
         * Null if this entry has not been found in any open file.
         */
        public D2GrailFinding getFinding() {
            return iFinding;
        }

        public boolean isFound() {
            return iFinding != null;
        }
    }

    /**
     * Every row for the current tab, with every filter applied (tier, Chronicle scope, tree
     * selection, status, search), sorted by display name (color codes stripped first, so a
     * ÿc4-prefixed name doesn't sort by its color code). This is the "everything applied" view --
     * used for the actual list and, grouped, for {@link #getSetGroups()} -- as opposed to
     * {@link #getTabProgress()}/{@link #getCategoryProgress()}, which deliberately leave status and
     * search out (plan section 7: otherwise the Missing filter would always show 0%).
     * <p>
     * One deliberate exception to "status applies per row": on the Sets tab, {@link Status#FOUND}
     * shows every piece (found AND missing) of any set with at least one structurally-passing
     * found piece, instead of only the owned ones -- see
     * {@link #setNamesWithAStructurallyFoundPiece()}. {@link Status#MISSING} and {@link Status#ALL}
     * are unaffected, on every tab, including Sets.
     */
    public List<Row> getRows() {
        // Sets tab + Show: Found only (a request from real use: a 2-of-4 set under Show: Found
        // used to show only the 2 owned pieces, hiding what completes it): a set with at least one
        // STRUCTURALLY-passing found piece shows every one of its pieces, found and missing alike,
        // instead of filtering each row by status individually. Null everywhere else, meaning
        // "apply passesStatus() per row exactly as before" -- see setNamesWithAStructurallyFoundPiece().
        Set<String> lSetsToShowInFull = (iTab == D2GrailKey.Type.SET && iStatus == Status.FOUND)
                ? setNamesWithAStructurallyFoundPiece() : null;

        List<Row> lOut = new ArrayList<>();
        for (D2GrailEntry lEntry : iAllEntries) {
            if (lEntry.getKey().getType() != iTab) {
                continue;
            }
            if (!passesChronicleScope(lEntry) || !passesTier(lEntry) || !passesCategory(lEntry)) {
                continue;
            }
            D2GrailFinding lFinding = findingFor(lEntry);

            if (lSetsToShowInFull != null) {
                // A set not in this set never had a found piece under the current structural
                // filters -- stays hidden entirely, same as Show: Found always did for it.
                if (!lSetsToShowInFull.contains(lEntry.getSetName())) {
                    continue;
                }
                // Found or missing, this piece belongs to a set the player has started: show it
                // (status is deliberately not checked here -- that is the whole point).
            } else if (!passesStatus(lFinding)) {
                continue;
            }

            if (!passesSearch(lEntry)) {
                continue;
            }
            lOut.add(new Row(lEntry, lFinding));
        }
        lOut.sort(Comparator.comparing(pRow -> D2ItemRenderer.stripColorCodes(pRow.getEntry().getDisplayName())
                .toLowerCase(Locale.ROOT)));
        return lOut;
    }

    /**
     * The set names (Sets tab only) with at least one piece that is both found and passes the
     * STRUCTURAL filters -- Chronicle scope, tier, and the tree/class selection -- but deliberately
     * NOT search: a set must not appear or disappear as the user types, and search only ever
     * narrows which of the resulting rows are displayed (see {@link #passesSearch}'s call site in
     * {@link #getRows()}, applied after this decision, not as part of it). Tier in particular means
     * a set whose only owned piece is a tier the player has unchecked does not resurrect the set --
     * that piece would not itself be visible, so it should not be able to reveal the rest either.
     */
    private Set<String> setNamesWithAStructurallyFoundPiece() {
        Set<String> lNames = new HashSet<>();
        for (D2GrailEntry lEntry : iAllEntries) {
            if (lEntry.getKey().getType() != D2GrailKey.Type.SET) {
                continue;
            }
            if (!passesChronicleScope(lEntry) || !passesTier(lEntry) || !passesCategory(lEntry)) {
                continue;
            }
            if (findingFor(lEntry) != null) {
                lNames.add(lEntry.getSetName());
            }
        }
        return lNames;
    }

    /**
     * One set's header (plan section 3.2: "== Angelic Raiment == 2 / 4") plus its rows, for the
     * Sets tab. Empty (not an error) on any other tab.
     */
    public static final class SetGroup {
        private final String iSetName;
        private final int iFound;
        private final int iTotal;
        private final List<Row> iRows;

        SetGroup(String pSetName, int pFound, int pTotal, List<Row> pRows) {
            iSetName = pSetName;
            iFound = pFound;
            iTotal = pTotal;
            iRows = pRows;
        }

        public String getSetName() {
            return iSetName;
        }

        /**
         * How many of this set's pieces are found, under the tab's tier + Chronicle-scope filters
         * (the same scope {@link #getTabProgress()} uses) -- NOT the status/search filters, so a
         * set filtered down to its Missing pieces alone still shows its true "n / size", not
         * "0 / (visible pieces)".
         */
        public int getFound() {
            return iFound;
        }

        /**
         * How many pieces this set has under the current Chronicle scope + tier filter (plan
         * section 5.2: a set with 2 of 6 pieces in the Chronicle shows "x / 2" normally and
         * "x / 6" once "Include non-Chronicle items" is checked). This is deliberately NOT
         * {@link D2GrailEntry#getSetSize()} (which always counts every piece regardless of scope)
         * -- see {@link #getSetGroups()}.
         */
        public int getTotal() {
            return iTotal;
        }

        /**
         * This set's rows that also pass the current status/search filters -- e.g. under
         * Show: Missing, only the still-missing pieces, even though {@link #getTotal()} still
         * reports the true set size.
         */
        public List<Row> getRows() {
            return iRows;
        }
    }

    public List<SetGroup> getSetGroups() {
        if (iTab != D2GrailKey.Type.SET) {
            return Collections.emptyList();
        }

        // Which sets to show headers for: only ones with at least one row surviving status/search
        // -- so e.g. filtering to Show: Found hides a set nobody has found any piece of yet,
        // rather than showing an empty "0 / 4" header for it.
        Map<String, List<Row>> lRowsBySet = new LinkedHashMap<>();
        for (Row lRow : getRows()) {
            String lSetName = lRow.getEntry().getSetName();
            List<Row> lRows = lRowsBySet.get(lSetName);
            if (lRows == null) {
                lRows = new ArrayList<>();
                lRowsBySet.put(lSetName, lRows);
            }
            lRows.add(lRow);
        }

        List<String> lSetNames = new ArrayList<>(lRowsBySet.keySet());
        Collections.sort(lSetNames, String.CASE_INSENSITIVE_ORDER);

        List<SetGroup> lOut = new ArrayList<>();
        for (String lSetName : lSetNames) {
            int lFound = 0;
            int lTotal = 0;
            for (D2GrailEntry lEntry : iAllEntries) {
                if (lEntry.getKey().getType() != D2GrailKey.Type.SET) {
                    continue;
                }
                if (!lSetName.equals(lEntry.getSetName())) {
                    continue;
                }
                if (!passesChronicleScope(lEntry) || !passesTier(lEntry)) {
                    continue;
                }
                lTotal++;
                if (findingFor(lEntry) != null) {
                    lFound++;
                }
            }
            lOut.add(new SetGroup(lSetName, lFound, lTotal, lRowsBySet.get(lSetName)));
        }
        return lOut;
    }

    // --------------------------------------------------------------------------------------
    // Progress
    // --------------------------------------------------------------------------------------

    public static final class Progress {
        private final int iFound;
        private final int iTotal;

        Progress(int pFound, int pTotal) {
            iFound = pFound;
            iTotal = pTotal;
        }

        public int getFound() {
            return iFound;
        }

        public int getTotal() {
            return iTotal;
        }

        /**
         * 0.0 when {@link #getTotal()} is 0 (an empty selection is "no progress", not a divide-by-
         * zero crash).
         */
        public double getRatio() {
            return iTotal == 0 ? 0.0 : (double) iFound / iTotal;
        }
    }

    /**
     * The bottom progress bar (plan section 7): the whole tab, tier + Chronicle-scope filters
     * applied, status/search deliberately NOT applied (else Show: Missing would always read 0%).
     */
    public Progress getTabProgress() {
        return progressFor(ALL_ID);
    }

    /**
     * The top progress bar: same as {@link #getTabProgress()} but further narrowed to the
     * currently selected tree node (plan section 7: "progression du nœud d'arbre sélectionné").
     */
    public Progress getCategoryProgress() {
        return progressFor(iCategorySelection);
    }

    /**
     * Progress for an arbitrary tree-node id, independent of the currently selected one -- used
     * for a tree node's hover tooltip (plan section 3.3's "where it makes sense" nodes), which
     * needs the count for whichever node the mouse is over, not just the selected one.
     */
    public Progress getProgressFor(String pCategorySelection) {
        return progressFor(pCategorySelection);
    }

    private Progress progressFor(String pCategorySelection) {
        int lFound = 0;
        int lTotal = 0;
        for (D2GrailEntry lEntry : iAllEntries) {
            if (lEntry.getKey().getType() != iTab) {
                continue;
            }
            if (!passesChronicleScope(lEntry) || !passesTier(lEntry)) {
                continue;
            }
            if (!matchesCategory(lEntry, pCategorySelection)) {
                continue;
            }
            lTotal++;
            if (findingFor(lEntry) != null) {
                lFound++;
            }
        }
        return new Progress(lFound, lTotal);
    }

    // --------------------------------------------------------------------------------------
    // Individual filter predicates
    // --------------------------------------------------------------------------------------

    private boolean passesChronicleScope(D2GrailEntry pEntry) {
        return iIncludeNonChronicle || pEntry.isChronicle();
    }

    /**
     * Tier checkboxes apply on the Uniques and Sets tabs (both have base items with a real tier)
     * and are inert on Runewords, where every entry is {@link D2GrailEntry.Tier#NONE} and the
     * window disables the checkboxes rather than relying on this always returning true.
     * <p>
     * On the Sets tab the filter is applied to the SET, not to the individual piece: a piece
     * passes when ANY piece of its set does. A set's pieces routinely span tiers -- Immortal King
     * is built on a Normal Avenger Guard, three Exceptional War Belt/Gauntlets/Boots and an Elite
     * Sacred Armor + Ogre Maul -- so filtering piece-by-piece tore sets apart: with Elite alone
     * checked, Immortal King listed 2 of its 6 pieces under a "0 / 2" header, hiding the four
     * pieces still needed to complete it and understating the set's real size. That is the same
     * complaint, and the same answer, as the Sets + Show: Found rule in {@link #getRows()}: on this
     * tab the set is the unit the player tracks, so a filter decides whether a SET is shown, never
     * which of its pieces are. The checkboxes still narrow the list -- a set with no piece in any
     * enabled tier disappears entirely, so "Elite only" still means "sets with an Elite piece" --
     * they just no longer show half a set.
     * <p>
     * Applied here, in the shared predicate, rather than in getRows() alone, so every consumer
     * agrees: the set-group header's "n / total" ({@link #getSetGroups()}), the two progress bars
     * ({@link #progressFor}) and the Show: Found set detection
     * ({@link #setNamesWithAStructurallyFoundPiece()}) all count the same whole sets the list shows.
     * The Uniques tab is untouched -- a unique is its own unit and its tier is its own.
     */
    private boolean passesTier(D2GrailEntry pEntry) {
        if (iTab == D2GrailKey.Type.RUNEWORD) {
            return true;
        }
        if (iTab == D2GrailKey.Type.SET) {
            return setPassesTier(nullToEmpty(pEntry.getSetName()));
        }
        return iEnabledTiers.contains(pEntry.getTier());
    }

    /**
     * True when at least one piece of this set is in an enabled tier -- see {@link #passesTier}.
     * The qualifying piece must also be inside the current Chronicle scope, for the same reason
     * {@link #setNamesWithAStructurallyFoundPiece()} checks scope: a piece the scope hides is not
     * a piece the player is being shown, so it must not pull the rest of its set into view either.
     * Memoized per set name in {@link #iSetTierPass}, which {@link #setTierEnabled} and
     * {@link #setIncludeNonChronicle} clear.
     */
    private boolean setPassesTier(String pSetName) {
        Boolean lCached = iSetTierPass.get(pSetName);
        if (lCached != null) {
            return lCached;
        }
        boolean lPasses = false;
        for (D2GrailEntry lEntry : iAllEntries) {
            if (lEntry.getKey().getType() != D2GrailKey.Type.SET) {
                continue;
            }
            if (!pSetName.equals(nullToEmpty(lEntry.getSetName()))) {
                continue;
            }
            if (passesChronicleScope(lEntry) && iEnabledTiers.contains(lEntry.getTier())) {
                lPasses = true;
                break;
            }
        }
        iSetTierPass.put(pSetName, lPasses);
        return lPasses;
    }

    private boolean passesCategory(D2GrailEntry pEntry) {
        return matchesCategory(pEntry, iCategorySelection);
    }

    /**
     * The tree-selection match, shared by row filtering and both progress computations.
     * Runewords have no tree at all (plan section 5.3 -- no single base category applies), so any
     * selection is a no-op there. A selection id that belongs to the OTHER tab's tree (e.g. a
     * CLASS: id while iTab is UNIQUE, left over from switching tabs) matches nothing on purpose --
     * see setCategorySelection's javadoc -- except ALL_ID, which always matches everywhere.
     */
    private boolean matchesCategory(D2GrailEntry pEntry, String pSelection) {
        if (pSelection == null || ALL_ID.equals(pSelection) || iTab == D2GrailKey.Type.RUNEWORD) {
            return true;
        }
        if (iTab == D2GrailKey.Type.SET) {
            if (!pSelection.startsWith(CLASS_PREFIX)) {
                return false;
            }
            String lCode = pSelection.substring(CLASS_PREFIX.length());
            String lEntryCode = pEntry.getSetUiClassCode() == null ? "" : pEntry.getSetUiClassCode();
            return lEntryCode.equals(lCode);
        }
        // Uniques tab.
        if (UNCATEGORIZED_ID.equals(pSelection)) {
            return pEntry.getUiCategoryCode() == null;
        }
        if (pSelection.startsWith(ROOT_PREFIX)) {
            String lRootName = pSelection.substring(ROOT_PREFIX.length());
            return pEntry.getRootGroup() != null && pEntry.getRootGroup().name().equals(lRootName);
        }
        if (pSelection.startsWith(CATEGORY_PREFIX)) {
            String lCode = pSelection.substring(CATEGORY_PREFIX.length());
            return lCode.equals(pEntry.getUiCategoryCode());
        }
        return false;
    }

    private boolean passesStatus(D2GrailFinding pFinding) {
        switch (iStatus) {
            case FOUND:
                return pFinding != null;
            case MISSING:
                return pFinding == null;
            default:
                return true;
        }
    }

    private boolean passesSearch(D2GrailEntry pEntry) {
        if (iSearchText.trim().isEmpty()) {
            return true;
        }
        String lNeedle = foldForSearch(iSearchText);
        if (foldForSearch(nullToEmpty(pEntry.getDisplayName())).contains(lNeedle)) {
            return true;
        }
        return foldForSearch(nullToEmpty(pEntry.getBaseItemName())).contains(lNeedle);
    }

    // Diacritics (the "accents" of plan section 7): decompose to base-letter + combining mark
    // (NFD), then drop every combining mark -- e.g. both "e" and "é" fold to "e". Applied to BOTH
    // the search text and the entry names, so a search for "sur" still finds "Sûr...", not just
    // the other way around.
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static String foldForSearch(String pText) {
        // Real display names carry raw ÿc. color-code markup (e.g. "ÿc4Heaven Facet") that must be
        // stripped before comparison -- plan section 7/12 -- or a search would have to know to type
        // the color code to match, and every such name would visually mismatch a plain query.
        String lStripped = D2ItemRenderer.stripColorCodes(pText);
        String lDecomposed = Normalizer.normalize(lStripped, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(lDecomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String pText) {
        return pText == null ? "" : pText;
    }
}
