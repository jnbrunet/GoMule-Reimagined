package gomule.grail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hardcoded English labels for the taxonomy the Reimagined mod's own data drives the Holy Grail
 * screen with: {@code itemtypes.txt}'s {@code UICategory} column (which base-item category a
 * unique/set item's tree node falls under) and {@code sets.txt}'s {@code UIClass} column (which
 * character class a set is themed around, for the Sets tab's left tree).
 * <p>
 * Neither column carries a translation of its own (verified: none of these short codes appear in
 * {@code d2Files/D2R_1.0/translations/ui-controller.json}), so the mapping to a displayable label
 * has to live somewhere in GoMule instead of being read out of the mod's data. It is kept in this
 * one small, easily-inspected table so that a future mod update adding a category is a one-line
 * fix here rather than a hunt through the rest of the grail code -- see
 * {@code D2GrailCategoriesTest}, which fails loudly the moment {@code itemtypes.txt} grows a
 * {@code UICategory} value this class doesn't know about.
 */
public final class D2GrailCategories {

    private D2GrailCategories() {
    }

    /**
     * The three top-level nodes of the Uniques/Sets category tree (plan section 5.5). Every
     * {@code UICategory} code below is assigned to exactly one of these.
     */
    public enum RootGroup {
        ARMOR,
        WEAPONS,
        MISC
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, RootGroup> ROOTS = new LinkedHashMap<>();

    private static void add(String pCode, String pLabel, RootGroup pRoot) {
        LABELS.put(pCode, pLabel);
        ROOTS.put(pCode, pRoot);
    }

    static {
        // Armor -- itemtypes.txt UICategory values, labels and grouping taken verbatim from
        // PLAN-holy-grail.md section 5.5.
        add("armor", "Body Armor", RootGroup.ARMOR);
        add("helms", "Helms", RootGroup.ARMOR);
        add("shlds", "Shields", RootGroup.ARMOR);
        add("boots", "Boots", RootGroup.ARMOR);
        add("glove", "Gloves", RootGroup.ARMOR);
        add("belts", "Belts", RootGroup.ARMOR);
        add("circl", "Circlets", RootGroup.ARMOR);
        add("barbh", "Barbarian Helms", RootGroup.ARMOR);
        add("druid", "Druid Pelts", RootGroup.ARMOR);
        add("necro", "Necromancer Shrunken Heads", RootGroup.ARMOR);
        add("palad", "Paladin Shields", RootGroup.ARMOR);
        add("sorce", "Sorceress Orbs", RootGroup.ARMOR);
        add("assas", "Assassin Katars", RootGroup.ARMOR);
        add("warlo", "Warlock Grimoires", RootGroup.ARMOR);

        // Weapons
        add("axes", "Axes", RootGroup.WEAPONS);
        add("bows", "Bows", RootGroup.WEAPONS);
        add("xbows", "Crossbows", RootGroup.WEAPONS);
        add("daggs", "Daggers", RootGroup.WEAPONS);
        add("javel", "Javelins", RootGroup.WEAPONS);
        add("maces", "Maces", RootGroup.WEAPONS);
        add("poles", "Polearms", RootGroup.WEAPONS);
        add("scept", "Scepters", RootGroup.WEAPONS);
        add("spear", "Spears", RootGroup.WEAPONS);
        add("stave", "Staves", RootGroup.WEAPONS);
        add("sword", "Swords", RootGroup.WEAPONS);
        add("throw", "Throwing Weapons", RootGroup.WEAPONS);
        add("wands", "Wands", RootGroup.WEAPONS);
        add("amazo", "Amazon Weapons", RootGroup.WEAPONS);
        add("ammo", "Quivers", RootGroup.WEAPONS);

        // Misc
        add("amule", "Amulets", RootGroup.MISC);
        add("rings", "Rings", RootGroup.MISC);
        add("charm", "Charms", RootGroup.MISC);
        add("jewel", "Jewels", RootGroup.MISC);
        add("gems", "Gems", RootGroup.MISC);
        add("runes", "Runes", RootGroup.MISC);
        add("potis", "Potions", RootGroup.MISC);
        add("scrlt", "Scrolls & Books", RootGroup.MISC);

        // Not one of itemtypes.txt's 37 UICategory values -- misc.txt has its own, separate
        // UICatOverride column that can point straight at a handful of other short codes for
        // items that don't fit the type-based bucketing (potions, keys, quest/uber materials,
        // terror-zone drops...). Verified against ./d2111 that of all those override codes, "dns"
        // is the only one actually reachable by a grail-eligible item: the six unique Colossal
        // Jewels (base code "cjw", e.g. "Guardian's Light", *ID 424) carry UICatOverride = "dns"
        // on their misc.txt row, which D2GrailIndex's category resolution takes before ever
        // consulting itemtypes.txt. Added here, next to the regular "jewel" bucket, purely so
        // those six entries don't end up with no category at all.
        add("dns", "Colossal Jewels", RootGroup.MISC);
    }

    /**
     * @return the English label for a UICategory (or UICatOverride) code, or null if the code is
     * not one this table knows about.
     */
    public static String getLabel(String pCode) {
        return LABELS.get(pCode);
    }

    /**
     * @return which root tree node a UICategory (or UICatOverride) code belongs under, or null if
     * the code is not one this table knows about.
     */
    public static RootGroup getRootGroup(String pCode) {
        return ROOTS.get(pCode);
    }

    /**
     * All UICategory/UICatOverride codes this table has a label and root group for. Used by
     * D2GrailCategoriesTest to assert nothing in itemtypes.txt is missing from this table.
     */
    public static java.util.Set<String> knownCodes() {
        return LABELS.keySet();
    }

    // sets.txt UIClass -> English class label, verbatim from PLAN-holy-grail.md's task
    // description. Empty string is the real value sets.txt uses for "no particular class"
    // (89 of the 98 non-blank rows checked), so it is a real, deliberate map key here, not a
    // placeholder.
    private static final Map<String, String> UI_CLASS_LABELS = new LinkedHashMap<>();

    static {
        UI_CLASS_LABELS.put("", "General");
        UI_CLASS_LABELS.put("ama", "Amazon");
        UI_CLASS_LABELS.put("sor", "Sorceress");
        UI_CLASS_LABELS.put("nec", "Necromancer");
        UI_CLASS_LABELS.put("pal", "Paladin");
        UI_CLASS_LABELS.put("bar", "Barbarian");
        UI_CLASS_LABELS.put("dru", "Druid");
        UI_CLASS_LABELS.put("ass", "Assassin");
        UI_CLASS_LABELS.put("war", "Warlock");
    }

    /**
     * @return the English label for a sets.txt UIClass code, or the raw code itself if this table
     * doesn't recognize it (defensive: a future mod update adding a class should degrade to an
     * ugly-but-present label rather than a null one).
     */
    public static String getUiClassLabel(String pCode) {
        String lLabel = UI_CLASS_LABELS.get(pCode == null ? "" : pCode);
        return lLabel != null ? lLabel : pCode;
    }

    /**
     * The UIClass codes this table knows about, in the order the Sets tab's left tree should list
     * them (General first, then one entry per class) -- a LinkedHashMap's key set, so callers get
     * that order for free rather than having to re-derive it.
     */
    public static java.util.Set<String> knownUiClassCodes() {
        return UI_CLASS_LABELS.keySet();
    }
}
