package gomule.grail;

import gomule.item.D2Item;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What is known so far about one grail entry that has been found in at least one open file.
 * Accumulates as {@link D2GrailScanner} walks item lists: every occurrence increments the copy
 * count and remembers which file it came from, in the order files were scanned.
 * <p>
 * Deliberately mutable -- {@link D2GrailScanner} builds one of these per key, up-to-date after a
 * single pass, rather than merging immutable snapshots.
 */
public final class D2GrailFinding {

    private int iCopies;

    // LinkedHashSet: a set (a file opened twice must not double-count as two "found in" entries)
    // that still preserves the order files were first seen in. The FULL path -- D2ItemList.
    // getFilename() -- because D2FileManager.focusFileWindow() (the double-click-to-source-file
    // path) looks a file up by exactly that string: D2FileManager's own iItemLists map is keyed
    // by the full path, not by any shortened display form. Never shown to the user directly --
    // see getFileDisplayNames() for that.
    private final Set<String> iFileNames = new LinkedHashSet<>();

    // The short, human-readable form of the same files, in the same order (plan section 3.3:
    // "Found in: Barbarian.d2s", not a full Windows/Unix path). Populated in lockstep with
    // iFileNames by record() -- see D2GrailScanner.displayFileName() for how each is derived from
    // the concrete D2ItemList type (the only place that still has it in hand).
    private final Set<String> iFileDisplayNames = new LinkedHashSet<>();

    private D2Item iFirstItem;

    /**
     * Records one occurrence of this entry.
     *
     * @param pItem            the item that was found; kept only the first time (for a future
     *                         tooltip), ignored (but still counted) on every later call.
     * @param pFileName        the full path of the file it was found in, exactly as
     *                         D2ItemList.getFilename() returns it (this is what
     *                         D2FileManager.focusFileWindow() needs); null is tolerated and simply
     *                         not added to {@link #getFileNames()}, so a list whose filename can't
     *                         be determined still contributes to the copy count.
     * @param pDisplayFileName the short, human-readable form of the same file (e.g.
     *                         "Barbarian.d2s" rather than its full path); null is tolerated the
     *                         same way pFileName is.
     */
    public void record(D2Item pItem, String pFileName, String pDisplayFileName) {
        iCopies++;
        if (pFileName != null) {
            iFileNames.add(pFileName);
        }
        if (pDisplayFileName != null) {
            iFileDisplayNames.add(pDisplayFileName);
        }
        if (iFirstItem == null) {
            iFirstItem = pItem;
        }
    }

    public int getCopies() {
        return iCopies;
    }

    /**
     * The full path of every file this entry was found in, in the order first seen. This is the
     * value D2FileManager.focusFileWindow() (double-click) looks a window up by -- never show this
     * to the user directly; use {@link #getFileDisplayNames()} for that.
     */
    public Set<String> getFileNames() {
        return Collections.unmodifiableSet(iFileNames);
    }

    /**
     * The short, human-readable form of every file this entry was found in (e.g. "Barbarian.d2s"),
     * in the same order as {@link #getFileNames()} -- what the list row, the hover tooltip and
     * Export... should all actually display (plan section 3.3).
     */
    public Set<String> getFileDisplayNames() {
        return Collections.unmodifiableSet(iFileDisplayNames);
    }

    /**
     * The first D2Item encountered for this key, kept around for a future tooltip showing the
     * item's actual properties. May be null if every recording call passed a null item.
     */
    public D2Item getFirstItem() {
        return iFirstItem;
    }
}
