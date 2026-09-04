package gomule.grail;

import gomule.d2s.D2Character;
import gomule.d2x.D2Stash;
import gomule.gui.D2ItemList;
import gomule.item.D2Item;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a set of open item lists (.d2s characters, .d2x mule stashes, .d2i shared stashes) and
 * reports every grail-eligible item found in them, independent of the D2GrailIndex's Chronicle
 * flag -- see the class-level note on that below.
 * <p>
 * Deliberately takes {@code Collection<? extends D2ItemList>} rather than
 * {@code gomule.gui.D2ItemListAll}: D2ItemListAll's constructor only aggregates
 * {@code getCharList()} + {@code getStashList()} (D2ItemListAll.java:37-38), so .d2i shared
 * stashes are never part of it. Callers must pass {@code D2FileManager}'s raw open-list
 * collection (all three file kinds) instead.
 */
public final class D2GrailScanner {

    private D2GrailScanner() {
    }

    /**
     * Scans every item in every given list -- including items socketed into another item, at any
     * depth -- and returns what was found, keyed by {@link D2GrailKey}.
     * <p>
     * This scan is intentionally independent of {@link D2GrailEntry#isChronicle()}: every
     * discovery is recorded, Chronicle or not, because whether to display/count a non-Chronicle
     * find is a filtering decision for later (the "Include non-Chronicle items" checkbox), not
     * something the scan itself should decide. A key with no matching {@link D2GrailIndex} entry
     * (a disabled row, or an id/name newer than the loaded {@code .txt} tables) is silently
     * ignored rather than reported -- there is nothing sensible to show for it anyway.
     * <p>
     * Never throws: a null collection, a null list inside it, a list that throws while producing
     * its items, a null item, or one item whose identification throws, are all tolerated so that
     * one bad file or one bad item can never take the whole scan down (the same defensive stance
     * CLAUDE.md documents for translation lookups).
     */
    public static Map<D2GrailKey, D2GrailFinding> scan(Collection<? extends D2ItemList> pItemLists) {
        Map<D2GrailKey, D2GrailFinding> lFindings = new HashMap<>();
        if (pItemLists == null) {
            return lFindings;
        }

        for (D2ItemList lList : pItemLists) {
            if (lList == null) {
                continue;
            }
            String lFileName = safeFileName(lList);
            // The concrete D2ItemList type is only ever known here -- by the time an item is being
            // identified below, all that's left is a bare file-name string, so the short display
            // form has to be derived up front and threaded through alongside the full path.
            String lDisplayFileName = displayFileName(lList, lFileName);

            List lItems;
            try {
                lItems = lList.getItemList();
            } catch (Throwable pEx) {
                continue;
            }
            if (lItems == null) {
                continue;
            }

            for (Object lObj : lItems) {
                if (lObj instanceof D2Item) {
                    scanItemRecursively((D2Item) lObj, lFileName, lDisplayFileName, lFindings);
                }
            }
        }

        return lFindings;
    }

    /**
     * Identifies one item, then recurses into whatever is socketed into it. The recursion is not
     * optional: a unique jewel or a rune socketed into a piece of armor never appears in that
     * list's own getItemList() -- only in the carrying item's getiSocketedItems() -- so without
     * this walk, an entire class of grail entries (anything findable only while socketed) would
     * be invisible. pally3.d2s's "Hand of Blessed Light" holding two unique "Heaven Facet" jewels
     * is the fixture case D2GrailScannerTest pins this against.
     */
    private static void scanItemRecursively(D2Item pItem, String pFileName, String pDisplayFileName,
                                             Map<D2GrailKey, D2GrailFinding> pFindings) {
        if (pItem == null) {
            return;
        }

        try {
            identify(pItem, pFileName, pDisplayFileName, pFindings);
        } catch (Throwable pEx) {
            // One item's identification blowing up (a mocked/malformed item in a test, or some
            // future accessor throwing) must not abort the rest of the scan.
        }

        List<D2Item> lSockets;
        try {
            lSockets = pItem.getiSocketedItems();
        } catch (Throwable pEx) {
            return;
        }
        if (lSockets == null) {
            return;
        }
        for (D2Item lSocket : lSockets) {
            scanItemRecursively(lSocket, pFileName, pDisplayFileName, pFindings);
        }
    }

    private static void identify(D2Item pItem, String pFileName, String pDisplayFileName,
                                  Map<D2GrailKey, D2GrailFinding> pFindings) {
        D2GrailKey lKey = null;

        if (pItem.isUnique()) {
            // unique_id defaults to -1 and is only ever overwritten once the item's unique row is
            // actually read (D2Item.java, readExtend "case 7") -- see CLAUDE.md/the plan on this.
            short lId = pItem.getUniqueID();
            if (lId >= 0) {
                lKey = D2GrailKey.unique(lId);
            }
        } else if (pItem.isSet()) {
            short lId = pItem.getSetID();
            if (lId >= 0) {
                lKey = D2GrailKey.set(lId);
            }
        } else if (pItem.isRuneWord()) {
            String lName = pItem.getRuneWordIndex();
            if (lName != null && !lName.isEmpty()) {
                lKey = D2GrailKey.runeword(lName);
            }
        }

        if (lKey == null) {
            return;
        }

        // A key absent from the index (a disabled uniqueitems.txt row, or a mod-added id/name the
        // loaded ./d2111 tables don't know about yet) must never throw here -- it is simply not
        // part of the grail this GoMule build can display. See D2GrailIndex.getByKey's javadoc.
        if (D2GrailIndex.getByKey(lKey) == null) {
            return;
        }

        D2GrailFinding lFinding = pFindings.get(lKey);
        if (lFinding == null) {
            lFinding = new D2GrailFinding();
            pFindings.put(lKey, lFinding);
        }
        lFinding.record(pItem, pFileName, pDisplayFileName);
    }

    private static String safeFileName(D2ItemList pList) {
        try {
            return pList.getFilename();
        } catch (Throwable pEx) {
            return null;
        }
    }

    /**
     * The short, human-readable form of a file (plan section 3.3: "Found in: Barbarian.d2s", not
     * the full path getFilename() actually returns) -- precisely what D2ItemListAll.getFilename
     * (D2Item) already does for the "all" pseudo-view, followed here for the same two concrete
     * types plus a third: D2Character -> getCharName() + ".d2s", D2Stash -> getFileNameEnd(). A
     * D2SharedStash (.d2i) has no equivalent short-name accessor, and anything else is unknown
     * outright, so both fall back to the path's own basename (java.io.File.getName()) -- never the
     * full path itself, and never an exception over what is only ever a display nicety.
     */
    // Package-private rather than private: D2GrailScannerTest pins each concrete D2ItemList type's
    // shortening rule directly, rather than only indirectly through a full scan() (which would
    // need a real grail item planted in a synthetic .d2x fixture just to observe this).
    static String displayFileName(D2ItemList pList, String pFullPath) {
        try {
            if (pList instanceof D2Character) {
                return ((D2Character) pList).getCharName() + ".d2s";
            }
            if (pList instanceof D2Stash) {
                return ((D2Stash) pList).getFileNameEnd();
            }
            // D2SharedStash (.d2i) and anything else (a future D2ItemList implementation this
            // class doesn't know about yet): the basename is always at least as short as the full
            // path and never wrong, unlike guessing at a type-specific short form.
            if (pFullPath != null) {
                return new File(pFullPath).getName();
            }
        } catch (Throwable pEx) {
            // Falls through to the pFullPath-or-null default below -- a broken accessor on the
            // display-name path must not be allowed to lose the (already safely captured) full
            // path's own recording.
        }
        return pFullPath;
    }
}
