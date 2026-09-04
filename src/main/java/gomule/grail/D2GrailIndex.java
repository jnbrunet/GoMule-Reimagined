package gomule.grail;

import gomule.D2Files;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the complete Holy Grail index -- every unique, set item and complete runeword the
 * Reimagined mod's own data tables define -- exactly once, and caches it in a static field for
 * the lifetime of the JVM. The mod's tables never change at runtime (they are read once by
 * {@link D2TxtFile#constructTxtFiles(String)} before anything else happens), so there is nothing
 * to invalidate.
 * <p>
 * Deliberately independent of Swing and of any save file: this class only reads {@code .txt}
 * tables. What has actually been *found* lives separately, in the
 * {@code Map<D2GrailKey, D2GrailFinding>} that {@link D2GrailScanner} produces from open item
 * lists; this index has no notion of "found" at all.
 * <p>
 * Callers MUST call {@link D2TxtFile#constructTxtFiles(String)} before the first call to
 * {@link #getEntries()} / {@link #getByKey(D2GrailKey)} -- see the "Gotchas" section of
 * CLAUDE.md. Building is not thread-safe against a concurrent {@code constructTxtFiles} call for
 * the same reason the rest of GoMule isn't: the txt tables themselves are plain static fields.
 */
public final class D2GrailIndex {

    private D2GrailIndex() {
    }

    private static List<D2GrailEntry> sEntries;
    private static Map<D2GrailKey, D2GrailEntry> sByKey;

    /**
     * The complete grail index -- Chronicle and non-Chronicle entries alike (see
     * {@link D2GrailEntry#isChronicle()}). Built on first call, then cached forever.
     */
    public static synchronized List<D2GrailEntry> getEntries() {
        build();
        return sEntries;
    }

    /**
     * @return the entry for this key, or null if the key is not part of the grail (e.g. a
     * {@code disabled} row, or an id/name from a mod version newer than the loaded {@code .txt}
     * tables). Never throws -- an unknown key is exactly the case callers like
     * {@link D2GrailScanner} need to be able to ignore safely.
     */
    public static synchronized D2GrailEntry getByKey(D2GrailKey pKey) {
        build();
        return sByKey.get(pKey);
    }

    private static void build() {
        if (sEntries != null) {
            return;
        }
        List<D2GrailEntry> lEntries = new ArrayList<>();
        buildUniques(lEntries);
        buildSetItems(lEntries);
        buildRunewords(lEntries);

        Map<D2GrailKey, D2GrailEntry> lByKey = new HashMap<>();
        for (D2GrailEntry lEntry : lEntries) {
            lByKey.put(lEntry.getKey(), lEntry);
        }

        sEntries = Collections.unmodifiableList(lEntries);
        sByKey = Collections.unmodifiableMap(lByKey);
    }

    // ------------------------------------------------------------------------------------------
    // Uniques
    // ------------------------------------------------------------------------------------------

    private static void buildUniques(List<D2GrailEntry> pOut) {
        D2TxtFile lTable = D2TxtFile.UNIQUES;
        int lRows = lTable.getRowSize();

        // The id a real save file actually embeds for a unique item is whatever
        // D2TxtFile.searchByID() would need to be given to land back on this row -- that is the
        // ONLY id D2GrailScanner will ever see (via D2Item.getUniqueID()), so it is the only id
        // that can be used as this entry's key.
        //
        // searchByID() does NOT read the "*ID" column at all: it walks iData by physical position
        // and returns row[id], shifted by exactly one once past the single row whose "index"
        // column is literally "Expansion" -- see D2TxtFile.searchByID(). The "*ID" column looks at
        // first like it already encodes that same offset (CLAUDE.md's note, and row 128
        // "SuperKhalimFlail"/*ID 128 vs row 130 "Coldkill"/*ID 129 immediately around the
        // Expansion row), but that agreement is coincidental to being early in the table, not a
        // general guarantee: uniqueitems.txt turns out to carry five more blank-*ID "section
        // header" comment rows further down ("Armor", "Elite Uniques", "Rings", "Class Specific",
        // "Warlock Class Pack"), which searchByID's single-Expansion-only compensation does not
        // know about at all. Past those, "*ID" drifts out of sync with what searchByID actually
        // returns -- confirmed against the real pally3/4/5 fixtures: the "Heaven Facet" jewel
        // socketed in "Hand of Blessed Light" carries unique_id=1400 in every one of them (exactly
        // searchByID(1400)), but that same row's own "*ID" column reads 1398. Keying this index by
        // the raw "*ID" column text, as originally planned, would put "Heaven Facet" under the
        // wrong key and let key 1400 silently resolve to an unrelated row one physical position
        // later instead -- misattributing every real find. So this recomputes the same physical-
        // index/Expansion-offset id searchByID itself uses, rather than trusting the "*ID" column.
        //
        // Set items are not affected by any of this: D2Item.java resolves a set item via
        // SETITEMS.searchColumns("*ID", ...) -- a literal text match against the "*ID" column
        // itself, not a positional lookup -- so buildSetItems() below, which keys off that same
        // column, is already exactly consistent with the real parser.
        int lExpansionPhysicalIndex = -1;
        for (int i = 0; i < lRows; i++) {
            if ("Expansion".equals(lTable.getRow(i).get("index"))) {
                lExpansionPhysicalIndex = i;
                break;
            }
        }

        for (int i = 0; i < lRows; i++) {
            if (i == lExpansionPhysicalIndex) {
                // The separator row itself -- not addressable by any id, exactly like
                // searchByID() special-cases it.
                continue;
            }
            D2TxtFileItemProperties lRow = lTable.getRow(i);

            // The same five section-header comment rows the id computation above has to reason
            // about are also not real items and must not become grail entries: "Armor", "Elite
            // Uniques", "Rings", "Class Specific" and "Warlock Class Pack" (Talonrage, the sixth
            // blank-*ID row, is also caught by the "disabled" check just below, redundantly).
            // Blank "*ID" is what actually distinguishes them: a handful of real, non-disabled
            // rows have no base item code either (e.g. "Gore Ripper", *ID 295 -- apparently a
            // reserved/never-finished unique slot) but DO carry a real "*ID", so filtering on a
            // blank base "code" instead would wrongly exclude those too.
            if (lRow.get("*ID").isEmpty()) {
                continue;
            }
            // 7 rows the mod has permanently removed from the game; not findable under any
            // circumstance, unlike a disableChronicle=1 row which is merely hidden by default.
            if (!lRow.get("disabled").isEmpty()) {
                continue;
            }

            int lId = (lExpansionPhysicalIndex == -1 || i < lExpansionPhysicalIndex) ? i : i - 1;

            boolean lChronicle = !"1".equals(lRow.get("disableChronicle"));
            String lBaseCode = lRow.get("code");
            String lBaseName = lRow.get("*ItemName");
            String lDisplayName = translateOrFallback(lRow.get("index"), lRow.get("index"));

            D2TxtFileItemProperties lBaseRow = findBaseRow(lBaseCode);

            // invfile: the unique's own column first: when it's empty (most rows), fall back to
            // the base item's own invfile -- the same fallback D2Item.java applies at parse time
            // (readExtend, "case 7").
            String lInvfile = lRow.get("invfile");
            if (lInvfile.isEmpty() && lBaseRow != null) {
                lInvfile = lBaseRow.get("invfile");
            }

            D2GrailEntry.Tier lTier = resolveTier(lBaseRow, lBaseCode);
            CategoryResolution lCategory = resolveCategory(lBaseRow);

            pOut.add(new D2GrailEntry(
                    D2GrailKey.unique(lId), lDisplayName, lBaseCode, lBaseName, lTier,
                    lCategory == null ? null : lCategory.code,
                    lCategory == null ? null : lCategory.label,
                    lCategory == null ? null : lCategory.root,
                    null, 0, null, null,
                    lInvfile, lChronicle, lRow));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Set items
    // ------------------------------------------------------------------------------------------

    private static void buildSetItems(List<D2GrailEntry> pOut) {
        D2TxtFile lTable = D2TxtFile.SETITEMS;
        int lRows = lTable.getRowSize();
        for (int i = 0; i < lRows; i++) {
            D2TxtFileItemProperties lRow = lTable.getRow(i);

            // Same untouched-separator-row situation as uniqueitems.txt (readInData()'s check
            // tests for "SetItems", the real file name is lowercase "setitems").
            if ("Expansion".equals(lRow.get("index"))) {
                continue;
            }
            // Measured: 0 rows are disabled in setitems.txt, but the check costs nothing and
            // keeps this branch symmetrical with uniques if that ever changes.
            if (!lRow.get("disabled").isEmpty()) {
                continue;
            }

            int lId = parseIntSafe(lRow.get("*ID"));
            if (lId < 0) {
                continue;
            }

            boolean lChronicle = !"1".equals(lRow.get("disableChronicle"));
            String lBaseCode = lRow.get("item");
            String lBaseName = lRow.get("*ItemName");
            String lSetName = lRow.get("set");
            String lDisplayName = translateOrFallback(lRow.get("index"), lRow.get("index"));

            // Set size: how many setitems.txt rows share this set name -- exactly the count
            // D2Item.java (readExtend, "case 5") uses for its own bookkeeping.
            int lSetSize = D2TxtFile.SETITEMS.searchColumnsMultipleHits("set", lSetName).size();

            // Set UI class: sets.txt "UIClass" column of the FULLSET row for this set name; empty
            // (unthemed) sets mean "General", per D2GrailCategories.getUiClassLabel.
            D2TxtFileItemProperties lFullSetRow = D2TxtFile.FULLSET.searchColumns("index", lSetName);
            String lUiClassCode = lFullSetRow == null ? "" : lFullSetRow.get("UIClass");
            String lUiClassLabel = D2GrailCategories.getUiClassLabel(lUiClassCode);

            D2TxtFileItemProperties lBaseRow = findBaseRow(lBaseCode);

            // invfile: setitems.txt's own column is empty for all but 5 of its 455 rows, so this
            // almost always falls through to the base item's invfile -- same fallback as uniques.
            String lInvfile = lRow.get("invfile");
            if (lInvfile.isEmpty() && lBaseRow != null) {
                lInvfile = lBaseRow.get("invfile");
            }

            D2GrailEntry.Tier lTier = resolveTier(lBaseRow, lBaseCode);
            CategoryResolution lCategory = resolveCategory(lBaseRow);

            pOut.add(new D2GrailEntry(
                    D2GrailKey.set(lId), lDisplayName, lBaseCode, lBaseName, lTier,
                    lCategory == null ? null : lCategory.code,
                    lCategory == null ? null : lCategory.label,
                    lCategory == null ? null : lCategory.root,
                    lSetName, lSetSize, lUiClassCode, lUiClassLabel,
                    lInvfile, lChronicle, lRow));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Runewords
    // ------------------------------------------------------------------------------------------

    private static void buildRunewords(List<D2GrailEntry> pOut) {
        D2TxtFile lTable = D2TxtFile.RUNES;
        int lRows = lTable.getRowSize();

        // One runeword ("Doom", Name="Doom1") is legitimately split across two complete=1 rows --
        // one restricted to staves, the other to axes/polearms/clubs/hammers/maces -- both with
        // the identical Name, *Rune Name and rune sequence (verified against ./d2111; it is the
        // only such pair among the 209 complete rows). Without this guard it would produce two
        // D2GrailEntry objects sharing one D2GrailKey, silently breaking the "keys are unique"
        // invariant the rest of the index (and its HashMap-based lookup) depends on. First
        // occurrence wins; the two rows carry identical display data anyway, so which one is kept
        // makes no observable difference. This is why the real runeword count is 208, not 209.
        Set<String> lSeenNames = new HashSet<>();

        for (int i = 0; i < lRows; i++) {
            D2TxtFileItemProperties lRow = lTable.getRow(i);

            // 209 of runes.txt's 211 rows are "complete" (fully defined); the other 2 are
            // work-in-progress/unused entries the game itself never recognizes.
            if (!"1".equals(lRow.get("complete"))) {
                continue;
            }

            String lName = lRow.get("Name");
            if (lName == null || lName.isEmpty()) {
                // Defensive only: every complete=1 row observed in ./d2111 has a Name.
                continue;
            }
            if (!lSeenNames.add(lName)) {
                continue;
            }

            String lDisplayName = translateOrFallback(lName, lRow.get("*Rune Name"));

            // Icon: the first rune of the word (Rune1), looked up in misc.txt like any other base
            // item code. Runewords have no invfile of their own and no tier -- see D2GrailEntry.
            String lRune1 = lRow.get("Rune1");
            D2TxtFileItemProperties lRuneRow = lRune1.isEmpty() ? null : D2TxtFile.search(lRune1);
            String lInvfile = lRuneRow == null ? "" : lRuneRow.get("invfile");

            // runes.txt has no disableChronicle column at all: every runeword is part of the
            // Chronicle unconditionally.
            pOut.add(new D2GrailEntry(
                    D2GrailKey.runeword(lName), lDisplayName, null, null, D2GrailEntry.Tier.NONE,
                    null, null, null,
                    null, 0, null, null,
                    lInvfile, true, lRow));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Looks up a base item's own misc.txt/armor.txt/weapons.txt row (the row for the code that
     * sits in a unique's "code" column, or a set item's "item" column). Null for a blank/unknown
     * code -- callers must tolerate that instead of crashing, the same defensive stance CLAUDE.md
     * asks for everywhere else in this codebase.
     */
    private static D2TxtFileItemProperties findBaseRow(String pCode) {
        if (pCode == null || pCode.isEmpty()) {
            return null;
        }
        return D2TxtFile.search(pCode);
    }

    /**
     * Normal/Exceptional/Elite resolution (plan section 5.4): compare the entry's own base code
     * against its base row's normcode/ubercode/ultracode columns, checked in that order. Every
     * base row in misc/armor/weapons.txt carries all three columns, but for rings, amulets,
     * charms and jewels none of the three ever equals the item's own code (some, like "Ring",
     * simply repeat their own code in all three columns; others, like "Colossal Jewel", point at
     * an unrelated code such as "jew") -- either way the checks below fall through to NORMAL,
     * which is the correct answer for both shapes of "no real tier".
     */
    private static D2GrailEntry.Tier resolveTier(D2TxtFileItemProperties pBaseRow, String pCode) {
        if (pBaseRow == null || pCode == null) {
            // Base row not found at all -- should not happen for a real entry (verified against
            // ./d2111), but a missing base is closer to "no tier information" than to any of the
            // three real tiers, so treat it the same as the ring/amulet/charm/jewel case: NORMAL.
            return D2GrailEntry.Tier.NORMAL;
        }
        if (pCode.equals(pBaseRow.get("normcode"))) {
            return D2GrailEntry.Tier.NORMAL;
        }
        if (pCode.equals(pBaseRow.get("ubercode"))) {
            return D2GrailEntry.Tier.EXCEPTIONAL;
        }
        if (pCode.equals(pBaseRow.get("ultracode"))) {
            return D2GrailEntry.Tier.ELITE;
        }
        return D2GrailEntry.Tier.NORMAL;
    }

    /**
     * The result of resolving a base item's UICategory: the raw code plus its already-resolved
     * label/root group, or null altogether if nothing in the chain produced a usable category.
     */
    private static final class CategoryResolution {
        final String code;
        final String label;
        final D2GrailCategories.RootGroup root;

        CategoryResolution(String pCode, String pLabel, D2GrailCategories.RootGroup pRoot) {
            code = pCode;
            label = pLabel;
            root = pRoot;
        }
    }

    /**
     * UI category resolution (plan section 5.5):
     * <pre>
     * base row's UICatOverride non-empty?  -> that code, directly
     * otherwise                            -> base row's "type" column -> itemtypes.txt Code
     *                                         -> that row's UICategory column; if empty, retry
     *                                         with that row's Equiv1, then Equiv2, as the "type"
     *                                         to look up again (same fallback chain D2Item.java
     *                                         uses at lines 427-433, applied to a different
     *                                         "missing" condition: an empty UICategory value
     *                                         rather than no itemtypes.txt row at all).
     * </pre>
     * Returns null if nothing above ultimately resolves to a code {@link D2GrailCategories} knows
     * about -- verified against ./d2111 that this never actually happens once "dns" (the six
     * unique Colossal Jewels' UICatOverride) is added to that table, but a future mod update
     * could still introduce a new one, and an entry with no category must not crash the index.
     */
    private static CategoryResolution resolveCategory(D2TxtFileItemProperties pBaseRow) {
        if (pBaseRow == null) {
            return null;
        }

        String lCode;
        String lOverride = pBaseRow.get("UICatOverride");
        if (!lOverride.isEmpty()) {
            lCode = lOverride;
        } else {
            String lType = pBaseRow.get("type");
            D2TxtFileItemProperties lTypeRow =
                    lType.isEmpty() ? null : D2TxtFile.ITEM_TYPES.searchColumns("Code", lType);
            lCode = lTypeRow == null ? "" : lTypeRow.get("UICategory");

            if (lCode.isEmpty() && lTypeRow != null) {
                lCode = uiCategoryOf(lTypeRow.get("Equiv1"));
            }
            if (lCode.isEmpty() && lTypeRow != null) {
                lCode = uiCategoryOf(lTypeRow.get("Equiv2"));
            }
        }

        if (lCode.isEmpty()) {
            return null;
        }
        String lLabel = D2GrailCategories.getLabel(lCode);
        D2GrailCategories.RootGroup lRoot = D2GrailCategories.getRootGroup(lCode);
        if (lLabel == null || lRoot == null) {
            // A code neither the plan's 37 itemtypes.txt values nor the "dns" UICatOverride
            // exception covers -- see the class javadoc above. Rather than surface a half-built
            // category (a label with no root, or vice versa), treat this entry as uncategorized.
            return null;
        }
        return new CategoryResolution(lCode, lLabel, lRoot);
    }

    private static String uiCategoryOf(String pItemTypeCode) {
        if (pItemTypeCode == null || pItemTypeCode.isEmpty()) {
            return "";
        }
        D2TxtFileItemProperties lRow = D2TxtFile.ITEM_TYPES.searchColumns("Code", pItemTypeCode);
        return lRow == null ? "" : lRow.get("UICategory");
    }

    /**
     * getTranslation(key) (used verbatim by D2Item.java's own unique/set-item name resolution at
     * readExtend lines 909/935) throws IllegalArgumentException when nothing translates the key,
     * instead of the null the surrounding code in both call sites actually checks for. Building
     * one grail entry with no translation must not abort the whole index the way that would, so
     * this deliberately uses getTranslationOrNull instead -- the same choice, and the same
     * reasoning, as the documented fix at D2Item.java lines 361-372.
     */
    private static String translateOrFallback(String pKey, String pFallback) {
        String lTranslated = D2Files.getInstance().getTranslations().getTranslationOrNull(pKey, "");
        return lTranslated != null ? lTranslated : pFallback;
    }

    private static int parseIntSafe(String pValue) {
        try {
            return Integer.parseInt(pValue.trim());
        } catch (Exception pEx) {
            return -1;
        }
    }
}
