package gomule.grail;

import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The character level one grail entry needs -- the number the list is ordered by, and the same one
 * a missing item's tooltip prints as "Required Level".
 * <p>
 * It is NOT simply the base item's own {@code levelreq}: D2Item.java raises it from the unique/set
 * row at parse time (readExtend, cases 7 and 5), and a runeword has no single base item at all. The
 * three shapes are spelled out on {@link #of}.
 * <p>
 * Lives in the grail package, next to the index, because two callers need the identical answer and
 * must not drift: {@link D2GrailModel} sorts by it, and {@code D2GrailListRenderer} prints it.
 * Requires {@link D2TxtFile#constructTxtFiles(String)} to have been called first.
 */
public final class D2GrailRequiredLevel {

    private D2GrailRequiredLevel() {
    }

    // A pure function of the .txt tables, which never change at runtime -- the same reasoning that
    // lets D2GrailIndex cache itself forever. Worth caching because the list re-sorts on every
    // filter change. Null values are cached too (as ABSENT), so an entry with no requirement is not
    // recomputed every time.
    private static final Map<D2GrailKey, Integer> CACHE = new HashMap<D2GrailKey, Integer>();
    private static final Integer ABSENT = Integer.valueOf(Integer.MIN_VALUE);

    /**
     * @return the level this entry requires, or null when the tables state none (a base row that
     * cannot be resolved, or a blank/0/non-numeric column -- D2Item.getReq()'s own semantics, where
     * all three mean "no requirement" rather than a displayed 0).
     * <ul>
     *   <li><b>Unique</b>: the unique row's own "lvl req" always wins over the base's "levelreq"
     *   when it parses to a real requirement (D2Item.java's own precedence).</li>
     *   <li><b>Set item</b>: the set row's own "lvl req" wins over the base's "levelreq" only when
     *   it is HIGHER (D2Item.java: "lSetReq != -1 &amp;&amp; lSetReq > iReqLvl") -- a set piece is
     *   never required at a LOWER level than its base item would otherwise demand.</li>
     *   <li><b>Runeword</b>: the highest level requirement among its runes, since runes.txt has no
     *   level column of its own -- see {@link #runewordLevel}.</li>
     * </ul>
     */
    public static synchronized Integer of(D2GrailEntry pEntry) {
        if (pEntry == null) {
            return null;
        }
        Integer lCached = CACHE.get(pEntry.getKey());
        if (lCached != null) {
            return ABSENT.equals(lCached) ? null : lCached;
        }
        Integer lLevel = compute(pEntry);
        CACHE.put(pEntry.getKey(), lLevel == null ? ABSENT : lLevel);
        return lLevel;
    }

    /**
     * {@link #of} with a value for the entries that have no stated requirement, for a caller that
     * needs a plain number -- an ordering key, say. 0 is the honest stand-in: nothing in ./d2111
     * genuinely requires level 0, so it never collides with a real answer, and it sorts those few
     * oddities (a reserved unique slot with no base item at all) to the top rather than scattering
     * them.
     */
    public static int orZero(D2GrailEntry pEntry) {
        Integer lLevel = of(pEntry);
        return lLevel == null ? 0 : lLevel.intValue();
    }

    private static Integer compute(D2GrailEntry pEntry) {
        D2TxtFileItemProperties lSourceRow = pEntry.getSourceRow();
        if (pEntry.getKey().getType() == D2GrailKey.Type.RUNEWORD) {
            return runewordLevel(lSourceRow);
        }

        String lBaseCode = pEntry.getBaseItemCode();
        D2TxtFileItemProperties lBaseRow;
        try {
            lBaseRow = lBaseCode == null || lBaseCode.isEmpty() ? null : D2TxtFile.search(lBaseCode);
        } catch (RuntimeException pEx) {
            lBaseRow = null;
        }
        Integer lBaseLevel = lBaseRow == null ? null : parseRequirement(lBaseRow.get("levelreq"));
        if (lSourceRow == null) {
            return lBaseLevel;
        }

        Integer lOwnLevel = parseRequirement(lSourceRow.get("lvl req"));
        if (pEntry.getKey().getType() == D2GrailKey.Type.UNIQUE) {
            return lOwnLevel != null ? lOwnLevel : lBaseLevel;
        }
        if (pEntry.getKey().getType() == D2GrailKey.Type.SET) {
            if (lOwnLevel != null && (lBaseLevel == null || lOwnLevel.intValue() > lBaseLevel.intValue())) {
                return lOwnLevel;
            }
            return lBaseLevel;
        }
        return lBaseLevel;
    }

    /**
     * A runeword's level, as its tooltip states it: the highest level requirement among its runes,
     * since runes.txt carries no level column of its own (Bulwark is Shael 29 / Io 35 / Sol 27, and
     * the mod's own page says "Level 35").
     * <p>
     * With one addition, for ordering rather than display: 25 of the 208 words also carry a
     * "levelreq" property of their own, and for exactly one of them -- "Law", made of Hel + Hel,
     * both levelreq 0 -- that property is the ONLY requirement there is. Taking the higher of the
     * two keeps Law with the other level-26 words instead of sorting it first as a level-0 item;
     * for every other word the runes are already the higher of the two, so the two readings agree.
     * The tooltip still prints the two separately -- the rune-derived line and the property's own
     * "Required Level +N" -- because whether the mod ADDS that property to the rune level or merely
     * floors it is not something the tables state, and inventing one number would be a guess.
     */
    private static Integer runewordLevel(D2TxtFileItemProperties pRunesRow) {
        if (pRunesRow == null) {
            return null;
        }
        int lMax = 0;
        List<String> lRuneCodes = D2GrailRunewords.runeCodes(pRunesRow);
        for (String lRuneCode : lRuneCodes) {
            D2TxtFileItemProperties lRuneRow;
            try {
                lRuneRow = D2TxtFile.search(lRuneCode);
            } catch (RuntimeException pEx) {
                continue;
            }
            Integer lRuneLevel = lRuneRow == null ? null : parseRequirement(lRuneRow.get("levelreq"));
            if (lRuneLevel != null && lRuneLevel.intValue() > lMax) {
                lMax = lRuneLevel.intValue();
            }
        }
        for (int i = 1; i <= 7; i++) {
            if (!"levelreq".equals(pRunesRow.get("T1Code" + i))) {
                continue;
            }
            Integer lFromProperty = parseRequirement(pRunesRow.get("T1Min" + i));
            if (lFromProperty != null && lFromProperty.intValue() > lMax) {
                lMax = lFromProperty.intValue();
            }
        }
        return lMax == 0 ? null : Integer.valueOf(lMax);
    }

    /**
     * D2Item.getReq()'s exact semantics: blank, "0" and anything non-numeric all mean "no
     * requirement" (null), never a rendered 0.
     */
    private static Integer parseRequirement(String pValue) {
        if (pValue == null) {
            return null;
        }
        String lTrimmed = pValue.trim();
        if (lTrimmed.isEmpty() || "0".equals(lTrimmed)) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(lTrimmed));
        } catch (NumberFormatException pEx) {
            return null;
        }
    }
}
