package gomule.grail;

import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything about a runeword that is read out of {@code runes.txt} rather than out of a found
 * item: which kinds of base it can be made in, and which runes it is made of.
 * <p>
 * Lives here, next to the rest of the grail index, rather than inside either of its two callers,
 * because both need exactly the same answers and must not drift apart: {@link D2GrailModel} filters
 * the Runewords tab by base type and by which runes the player has, and
 * {@code D2GrailListRenderer} prints both in a missing runeword's tooltip.
 * <p>
 * Requires {@link D2TxtFile#constructTxtFiles(String)} to have been called first, exactly like
 * {@link D2GrailIndex}.
 */
public final class D2GrailRunewords {

    private D2GrailRunewords() {
    }

    /** El (1) through Zod (33) -- runes.txt never references anything outside that range. */
    public static final int RUNE_COUNT = 33;

    /**
     * itemtypes.txt's own "ItemType" column is the label for every runeword base type except these
     * eight, where the mod's wording describes something other than the slot and would read as an
     * error in a filter list: "helm" is labelled "Merc Equip" there (a note about who can wear it,
     * not what it is), "weap"/"h2h"/"orb"/"grim"/"ashd" are labelled without the class or the
     * distinction that makes them useful to browse by, and the Necromancer's two codes -- the
     * umbrella "necr" and its one child "head" ("Voodoo Heads") -- are the same piece of gear under
     * two names and are deliberately given ONE label so they collapse into a single filter entry.
     * Everything else -- Sword, Polearm, Any Shield, Amazon Bow, Circlet and the rest -- is taken
     * verbatim from the table, so a mod update that adds a base type needs no change here.
     */
    private static final Map<String, String> LABEL_OVERRIDES = new HashMap<String, String>();

    static {
        LABEL_OVERRIDES.put("helm", "Helm");
        LABEL_OVERRIDES.put("weap", "Any Weapon");
        LABEL_OVERRIDES.put("h2h", "Assassin Claw");
        LABEL_OVERRIDES.put("necr", "Necromancer Head");
        LABEL_OVERRIDES.put("head", "Necromancer Head");
        LABEL_OVERRIDES.put("ashd", "Paladin Auric Shield");
        LABEL_OVERRIDES.put("orb", "Sorceress Orb");
        LABEL_OVERRIDES.put("grim", "Warlock Grimoire");
    }

    private static List<String> sAllBaseTypeLabels;

    /**
     * The base types one runeword allows (runes.txt itype1..itype6), as display labels, in table
     * order and de-duplicated -- two codes sharing a label (the Necromancer's "necr"/"head") count
     * once.
     */
    public static List<String> baseTypeLabels(D2TxtFileItemProperties pRunesRow) {
        Set<String> lOut = new LinkedHashSet<String>();
        for (String lCode : itypeCodes(pRunesRow)) {
            lOut.add(baseTypeLabel(lCode));
        }
        return new ArrayList<String>(lOut);
    }

    /**
     * Every base-type label any complete runeword actually uses, alphabetically -- the Runewords
     * tab's whole left-hand tree. Derived from the table rather than hardcoded so a mod update that
     * adds (or stops using) a base type changes the tree with no code change. Computed once; the
     * tables never change at runtime, same as {@link D2GrailIndex}'s own cache.
     */
    public static synchronized List<String> allBaseTypeLabels() {
        if (sAllBaseTypeLabels != null) {
            return sAllBaseTypeLabels;
        }
        Set<String> lLabels = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        int lRows = D2TxtFile.RUNES.getRowSize();
        for (int i = 0; i < lRows; i++) {
            D2TxtFileItemProperties lRow = D2TxtFile.RUNES.getRow(i);
            if (!"1".equals(lRow.get("complete"))) {
                continue;
            }
            lLabels.addAll(baseTypeLabels(lRow));
        }
        sAllBaseTypeLabels = Collections.unmodifiableList(new ArrayList<String>(lLabels));
        return sAllBaseTypeLabels;
    }

    /** The raw itype1..itype6 codes of one runeword, in table order, de-duplicated. */
    public static List<String> itypeCodes(D2TxtFileItemProperties pRunesRow) {
        List<String> lOut = new ArrayList<String>();
        if (pRunesRow == null) {
            return lOut;
        }
        for (int i = 1; i <= 6; i++) {
            String lCode = nullToEmpty(pRunesRow.get("itype" + i)).trim();
            if (!lCode.isEmpty() && !lOut.contains(lCode)) {
                lOut.add(lCode);
            }
        }
        return lOut;
    }

    /** One itype code's display label -- see {@link #LABEL_OVERRIDES}. */
    public static String baseTypeLabel(String pItypeCode) {
        String lOverride = LABEL_OVERRIDES.get(pItypeCode);
        if (lOverride != null) {
            return lOverride;
        }
        D2TxtFileItemProperties lRow = D2TxtFile.ITEM_TYPES.searchColumns("Code", pItypeCode);
        String lLabel = lRow == null ? "" : nullToEmpty(lRow.get("ItemType")).trim();
        return lLabel.isEmpty() ? pItypeCode : lLabel;
    }

    /**
     * The runes one word is made of (runes.txt Rune1..Rune6), in order -- deliberately NOT
     * de-duplicated: a word can legitimately use the same rune twice ("Law" is Hel + Hel).
     */
    public static List<String> runeCodes(D2TxtFileItemProperties pRunesRow) {
        List<String> lOut = new ArrayList<String>();
        if (pRunesRow == null) {
            return lOut;
        }
        for (int i = 1; i <= 6; i++) {
            String lCode = nullToEmpty(pRunesRow.get("Rune" + i)).trim();
            if (!lCode.isEmpty()) {
                lOut.add(lCode);
            }
        }
        return lOut;
    }

    /**
     * The same runes as {@link #runeCodes}, as the numbers the game and every runeword guide print
     * ("Shael Rune (#13)"). A code that carries no usable number is dropped rather than turned into
     * a made-up 0, so a caller matching against the player's owned runes never matches on garbage.
     */
    public static List<Integer> runeNumbers(D2TxtFileItemProperties pRunesRow) {
        List<Integer> lOut = new ArrayList<Integer>();
        for (String lCode : runeCodes(pRunesRow)) {
            int lNumber = runeNumber(lCode);
            if (lNumber > 0) {
                lOut.add(Integer.valueOf(lNumber));
            }
        }
        return lOut;
    }

    /**
     * A rune item code's number -- its digits, since the codes run r01..r33 in order. 0 for
     * anything that doesn't parse, rather than a made-up number.
     */
    public static int runeNumber(String pRuneCode) {
        if (pRuneCode == null) {
            return 0;
        }
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

    /** The misc.txt item code for a rune number: 1 -&gt; "r01", 33 -&gt; "r33". */
    public static String runeCode(int pRuneNumber) {
        return pRuneNumber < 10 ? "r0" + pRuneNumber : "r" + pRuneNumber;
    }

    /**
     * A rune's bare name for a compact control -- "El", "Shael", "Zod". Taken from misc.txt's own
     * "name" column ("El Rune") with the trailing " Rune" removed, NOT from the translation table:
     * the mod's translated rune strings are colour-coded markup that already embeds the number and,
     * for the high runes, a second "~Pick Up~" line, none of which belongs on a toggle button. The
     * number is displayed separately by the caller.
     */
    public static String runeShortName(int pRuneNumber) {
        String lCode = runeCode(pRuneNumber);
        D2TxtFileItemProperties lRow = D2TxtFile.search(lCode);
        String lName = lRow == null ? "" : nullToEmpty(lRow.get("name")).trim();
        if (lName.isEmpty()) {
            return lCode;
        }
        return lName.endsWith(" Rune") ? lName.substring(0, lName.length() - " Rune".length()) : lName;
    }

    private static String nullToEmpty(String pText) {
        return pText == null ? "" : pText;
    }
}
