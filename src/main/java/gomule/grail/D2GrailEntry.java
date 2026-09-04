package gomule.grail;

import randall.d2files.D2TxtFileItemProperties;

/**
 * One row of the Holy Grail index: everything the (future) grail window needs to know about a
 * single collectible unique, set item or runeword, independent of whether it has actually been
 * found in any open file. Built once by {@link D2GrailIndex} from the {@code .txt} tables;
 * "found" status lives separately, in the {@link D2GrailFinding} map the scanner produces.
 * <p>
 * Immutable: every field is set once, at construction, by {@link D2GrailIndex}.
 */
public final class D2GrailEntry {

    /**
     * Which of the three "base code quality tiers" (misc/armor/weapons {@code normcode}/
     * {@code ubercode}/{@code ultracode}) this entry's base item is. Runewords have no base item
     * in this sense, so they are always {@link #NONE}. NORMAL also covers every base whose three
     * tier columns don't include the item's own code at all (rings, amulets, charms, jewels --
     * see the taxonomy in PLAN-holy-grail.md section 5.4), since it is checked first.
     */
    public enum Tier {
        NORMAL,
        EXCEPTIONAL,
        ELITE,
        NONE
    }

    private final D2GrailKey iKey;
    private final String iDisplayName;
    private final String iBaseItemCode;
    private final String iBaseItemName;
    private final Tier iTier;

    // Null for runewords: a runeword's allowed base types are a list (itype1..itype6), not a
    // single UICategory the way a unique/set item's one base code resolves to one.
    private final String iUiCategoryCode;
    private final String iUiCategoryLabel;
    private final D2GrailCategories.RootGroup iRootGroup;

    // Set items only; null/0 otherwise.
    private final String iSetName;
    private final int iSetSize;
    private final String iSetUiClassCode;
    private final String iSetUiClassLabel;

    private final String iInvfile;
    private final boolean iChronicle;
    private final D2TxtFileItemProperties iSourceRow;

    public D2GrailEntry(D2GrailKey pKey, String pDisplayName, String pBaseItemCode,
                         String pBaseItemName, Tier pTier, String pUiCategoryCode,
                         String pUiCategoryLabel, D2GrailCategories.RootGroup pRootGroup,
                         String pSetName, int pSetSize, String pSetUiClassCode,
                         String pSetUiClassLabel, String pInvfile, boolean pChronicle,
                         D2TxtFileItemProperties pSourceRow) {
        iKey = pKey;
        iDisplayName = pDisplayName;
        iBaseItemCode = pBaseItemCode;
        iBaseItemName = pBaseItemName;
        iTier = pTier;
        iUiCategoryCode = pUiCategoryCode;
        iUiCategoryLabel = pUiCategoryLabel;
        iRootGroup = pRootGroup;
        iSetName = pSetName;
        iSetSize = pSetSize;
        iSetUiClassCode = pSetUiClassCode;
        iSetUiClassLabel = pSetUiClassLabel;
        iInvfile = pInvfile;
        iChronicle = pChronicle;
        iSourceRow = pSourceRow;
    }

    public D2GrailKey getKey() {
        return iKey;
    }

    public String getDisplayName() {
        return iDisplayName;
    }

    public String getBaseItemCode() {
        return iBaseItemCode;
    }

    public String getBaseItemName() {
        return iBaseItemName;
    }

    public Tier getTier() {
        return iTier;
    }

    public String getUiCategoryCode() {
        return iUiCategoryCode;
    }

    public String getUiCategoryLabel() {
        return iUiCategoryLabel;
    }

    public D2GrailCategories.RootGroup getRootGroup() {
        return iRootGroup;
    }

    /**
     * The set's name (setitems.txt "set" column). Null for uniques and runewords.
     */
    public String getSetName() {
        return iSetName;
    }

    /**
     * How many setitems.txt rows share this entry's set name -- i.e. how many pieces the full set
     * has. 0 for uniques and runewords.
     */
    public int getSetSize() {
        return iSetSize;
    }

    /**
     * The set's sets.txt "UIClass" raw code (e.g. "pal", or "" for General). Null for uniques and
     * runewords.
     */
    public String getSetUiClassCode() {
        return iSetUiClassCode;
    }

    /**
     * The set's UIClass resolved to an English label (e.g. "Paladin", "General"). Null for
     * uniques and runewords.
     */
    public String getSetUiClassLabel() {
        return iSetUiClassLabel;
    }

    /**
     * The .dc6 inventory sprite file name (without extension), already resolved through the
     * base-item fallback described in D2GrailIndex. May still be empty if nothing in the chain
     * supplied one.
     */
    public String getInvfile() {
        return iInvfile;
    }

    /**
     * True if this entry is part of the in-game Chronicle (uniqueitems.txt/setitems.txt
     * "disableChronicle" column empty; always true for runewords, which have no such column).
     */
    public boolean isChronicle() {
        return iChronicle;
    }

    /**
     * The uniqueitems.txt/setitems.txt/runes.txt row this entry was built from.
     */
    public D2TxtFileItemProperties getSourceRow() {
        return iSourceRow;
    }

    @Override
    public String toString() {
        return "D2GrailEntry{" + iKey + " '" + iDisplayName + "'}";
    }
}
