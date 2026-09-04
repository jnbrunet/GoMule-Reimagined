package gomule.grail;

import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins D2GrailCategories against the real ./d2111 data. The whole point of this table living
 * separately from the rest of the grail code (see its class javadoc) is that a future mod update
 * adding a new itemtypes.txt UICategory value should fail a test here loudly, rather than leave a
 * silently-uncategorized node in the eventual grail tree.
 */
public class D2GrailCategoriesTest {

    @Test
    public void everyUiCategoryPresentInItemTypesHasALabelAndARootGroup() {
        D2TxtFile.constructTxtFiles("./d2111");

        Set<String> lFoundInItemTypes = new LinkedHashSet<>();
        int lRows = D2TxtFile.ITEM_TYPES.getRowSize();
        for (int i = 0; i < lRows; i++) {
            D2TxtFileItemProperties lRow = D2TxtFile.ITEM_TYPES.getRow(i);
            String lCategory = lRow.get("UICategory");
            if (lCategory != null && !lCategory.isEmpty()) {
                lFoundInItemTypes.add(lCategory);
            }
        }

        // Measured against ./d2111: exactly 37 distinct UICategory values, spelled out verbatim
        // in PLAN-holy-grail.md section 5.5. If this count changes, the mod added or removed a
        // category and D2GrailCategories needs a matching update.
        assertEquals(37, lFoundInItemTypes.size(),
                "itemtypes.txt's set of distinct UICategory values changed -- update "
                        + "D2GrailCategories to match: " + lFoundInItemTypes);

        for (String lCategory : lFoundInItemTypes) {
            assertNotNull(D2GrailCategories.getLabel(lCategory),
                    "no English label for itemtypes.txt UICategory '" + lCategory + "'");
            assertNotNull(D2GrailCategories.getRootGroup(lCategory),
                    "no root group for itemtypes.txt UICategory '" + lCategory + "'");
        }
    }

    /**
     * The one UICategory value D2GrailIndex can actually produce that does NOT come from
     * itemtypes.txt's own column (see D2GrailCategories's class javadoc): misc.txt's separate
     * UICatOverride column on the "Colossal Jewel" base item ("cjw") is "dns", which the six
     * unique Colossal Jewels resolve to directly, bypassing itemtypes.txt entirely.
     */
    @Test
    public void colossalJewelOverrideCategoryIsKnown() {
        assertNotNull(D2GrailCategories.getLabel("dns"));
        assertEquals(D2GrailCategories.RootGroup.MISC, D2GrailCategories.getRootGroup("dns"));
    }

    @Test
    public void uiClassLabelsMatchThePlanVerbatim() {
        assertEquals("General", D2GrailCategories.getUiClassLabel(""));
        assertEquals("Amazon", D2GrailCategories.getUiClassLabel("ama"));
        assertEquals("Sorceress", D2GrailCategories.getUiClassLabel("sor"));
        assertEquals("Necromancer", D2GrailCategories.getUiClassLabel("nec"));
        assertEquals("Paladin", D2GrailCategories.getUiClassLabel("pal"));
        assertEquals("Barbarian", D2GrailCategories.getUiClassLabel("bar"));
        assertEquals("Druid", D2GrailCategories.getUiClassLabel("dru"));
        assertEquals("Assassin", D2GrailCategories.getUiClassLabel("ass"));
        assertEquals("Warlock", D2GrailCategories.getUiClassLabel("war"));
    }

    @Test
    public void unknownCodesDoNotThrow() {
        assertFalse(D2GrailCategories.getUiClassLabel("zzz").isEmpty());
        assertEquals(null, D2GrailCategories.getLabel("zzz"));
        assertEquals(null, D2GrailCategories.getRootGroup("zzz"));
    }
}
