package gomule.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * The "known projects" list the left-pane combo box is fed from (plan section 4, "Registre des
 * projets connus"), backed by {@code project.recent.0..9} in projects.properties -- most recent
 * first, capped at {@link #MAX_RECENT}. Deliberately a plain, Swing-free helper over a
 * {@link Properties} instance the caller already has (D2FileManager owns the real one) rather
 * than a class of its own with file I/O, so D2ProjectRegistryTest can exercise every rule (add,
 * order, cap, purge) against an in-memory Properties with no filesystem beyond a couple of
 * {@code @TempDir} probe directories for isProjectDir() checks.
 */
public final class D2ProjectRegistry {
    public static final int MAX_RECENT = 10;

    private static final String RECENT_PREFIX = "project.recent.";

    private D2ProjectRegistry() {
    }

    /**
     * The recorded recent projects, most recent first, in whatever order they were written --
     * this never re-sorts, so a sparse/hand-edited properties file (e.g. only "project.recent.0"
     * and "project.recent.2" set) simply reads back short rather than throwing or reordering.
     */
    public static List<File> getRecentProjects(Properties pProperties) {
        List<File> lResult = new ArrayList<File>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String lPath = pProperties.getProperty(RECENT_PREFIX + i);
            if (lPath != null && !lPath.trim().isEmpty()) {
                lResult.add(new File(lPath));
            }
        }
        return lResult;
    }

    private static void setRecentProjects(Properties pProperties, List<File> pProjects) {
        // Clear every slot first: a purge can shrink the list, and a stale
        // "project.recent.4" left behind after the list drops from 5 entries to 3 would resurface
        // a dead project the next time getRecentProjects() reads it back.
        for (int i = 0; i < MAX_RECENT; i++) {
            pProperties.remove(RECENT_PREFIX + i);
        }
        int lCount = Math.min(pProjects.size(), MAX_RECENT);
        for (int i = 0; i < lCount; i++) {
            pProperties.setProperty(RECENT_PREFIX + i, pProjects.get(i).getAbsolutePath());
        }
    }

    /**
     * Moves pProjectDir to the front of the recent list, de-duplicating by absolute path (opening
     * the same project twice must not create two entries), then trims to {@link #MAX_RECENT}.
     * This is the only writer of {@code project.recent.N} -- New/Open Project, the combo box and
     * startup's checkProjects() all funnel through here, so "most recent first" and the cap stay
     * enforced in exactly one place.
     */
    public static void recordOpened(Properties pProperties, File pProjectDir) {
        File lTarget = pProjectDir.getAbsoluteFile();
        List<File> lCurrent = getRecentProjects(pProperties);
        Iterator<File> lIt = lCurrent.iterator();
        while (lIt.hasNext()) {
            if (lIt.next().getAbsoluteFile().equals(lTarget)) {
                lIt.remove();
            }
        }
        lCurrent.add(0, lTarget);
        if (lCurrent.size() > MAX_RECENT) {
            lCurrent = lCurrent.subList(0, MAX_RECENT);
        }
        setRecentProjects(pProperties, lCurrent);
    }

    /**
     * Drops every recent entry that is no longer a real, valid project directory -- deleted,
     * renamed, or living on a drive that isn't mounted right now. Called once at startup
     * (D2FileManager.checkProjectsModel()) before the combo box is ever populated, so the user
     * never sees a stale entry that would just fail if picked.
     */
    public static void purgeMissing(Properties pProperties) {
        List<File> lCurrent = getRecentProjects(pProperties);
        List<File> lValid = new ArrayList<File>();
        for (File lDir : lCurrent) {
            if (D2Project.isProjectDir(lDir)) {
                lValid.add(lDir);
            }
        }
        if (lValid.size() != lCurrent.size()) {
            setRecentProjects(pProperties, lValid);
        }
    }

    /**
     * Convenience for callers that only want to know what would survive a purge without actually
     * mutating pProperties (e.g. rebuilding the combo box, which purges for real separately).
     */
    public static List<File> getValidRecentProjects(Properties pProperties) {
        List<File> lResult = new ArrayList<File>();
        for (File lDir : getRecentProjects(pProperties)) {
            if (D2Project.isProjectDir(lDir)) {
                lResult.add(lDir);
            }
        }
        return Collections.unmodifiableList(lResult);
    }
}
