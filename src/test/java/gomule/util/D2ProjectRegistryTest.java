package gomule.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2ProjectRegistry, the {@code project.recent.N} list the combo box is fed from (plan section
 * 4/6: add/order/cap-at-10/purge). Plain {@link Properties} in memory plus a handful of
 * {@code @TempDir} probe directories for the isProjectDir() checks purgeMissing()/
 * getValidRecentProjects() rely on -- no Swing, no real projects/ folder.
 */
public class D2ProjectRegistryTest {

    // A minimal, valid project directory for D2Project.isProjectDir() to say yes to -- just the
    // one marker file, no actual D2Project instance needed (this whole test is about the registry
    // bookkeeping, not project content).
    private static File newProjectDir(File pParent, String pName) {
        File lDir = new File(pParent, pName);
        assertTrue(lDir.mkdirs());
        try {
            assertTrue(new File(lDir, "project.properties").createNewFile());
        } catch (Exception pEx) {
            throw new RuntimeException(pEx);
        }
        return lDir;
    }

    @Test
    public void recordOpenedPutsTheNewestProjectFirst(@TempDir File pTempDir) {
        Properties lProps = new Properties();
        File lA = newProjectDir(pTempDir, "A");
        File lB = newProjectDir(pTempDir, "B");

        D2ProjectRegistry.recordOpened(lProps, lA);
        D2ProjectRegistry.recordOpened(lProps, lB);

        List<File> lRecent = D2ProjectRegistry.getRecentProjects(lProps);
        assertEquals(2, lRecent.size());
        assertEquals(lB.getAbsoluteFile(), lRecent.get(0).getAbsoluteFile(), "most recently opened must be first");
        assertEquals(lA.getAbsoluteFile(), lRecent.get(1).getAbsoluteFile());
    }

    @Test
    public void recordOpenedDeduplicatesByPathAndMovesToFront(@TempDir File pTempDir) {
        Properties lProps = new Properties();
        File lA = newProjectDir(pTempDir, "A");
        File lB = newProjectDir(pTempDir, "B");

        D2ProjectRegistry.recordOpened(lProps, lA);
        D2ProjectRegistry.recordOpened(lProps, lB);
        D2ProjectRegistry.recordOpened(lProps, lA); // re-opening A must not create a second entry.

        List<File> lRecent = D2ProjectRegistry.getRecentProjects(lProps);
        assertEquals(2, lRecent.size(), "re-opening an already-known project must not duplicate it");
        assertEquals(lA.getAbsoluteFile(), lRecent.get(0).getAbsoluteFile());
        assertEquals(lB.getAbsoluteFile(), lRecent.get(1).getAbsoluteFile());
    }

    @Test
    public void recordOpenedCapsAtMaxRecent(@TempDir File pTempDir) {
        Properties lProps = new Properties();
        for (int i = 0; i < D2ProjectRegistry.MAX_RECENT + 3; i++) {
            D2ProjectRegistry.recordOpened(lProps, newProjectDir(pTempDir, "P" + i));
        }

        List<File> lRecent = D2ProjectRegistry.getRecentProjects(lProps);
        assertEquals(D2ProjectRegistry.MAX_RECENT, lRecent.size());
        // The most recently opened MAX_RECENT projects survive the cap; the oldest ones are
        // dropped rather than the newest.
        assertEquals("P" + (D2ProjectRegistry.MAX_RECENT + 2), lRecent.get(0).getName());
        assertEquals("P3", lRecent.get(D2ProjectRegistry.MAX_RECENT - 1).getName());
    }

    @Test
    public void purgeMissingDropsDeletedOrRenamedProjectDirectories(@TempDir File pTempDir) {
        Properties lProps = new Properties();
        File lKept = newProjectDir(pTempDir, "kept");
        File lDeleted = newProjectDir(pTempDir, "deleted");
        D2ProjectRegistry.recordOpened(lProps, lKept);
        D2ProjectRegistry.recordOpened(lProps, lDeleted);

        // Simulate the user deleting/renaming the folder on disk between two GoMule sessions --
        // its project.properties is gone, so isProjectDir() must now say no.
        assertTrue(new File(lDeleted, "project.properties").delete());

        D2ProjectRegistry.purgeMissing(lProps);

        List<File> lRecent = D2ProjectRegistry.getRecentProjects(lProps);
        assertEquals(1, lRecent.size());
        assertEquals(lKept.getAbsoluteFile(), lRecent.get(0).getAbsoluteFile());
    }

    @Test
    public void getValidRecentProjectsDoesNotMutateTheBackingProperties(@TempDir File pTempDir) {
        Properties lProps = new Properties();
        File lKept = newProjectDir(pTempDir, "kept");
        File lStale = newProjectDir(pTempDir, "stale");
        D2ProjectRegistry.recordOpened(lProps, lKept);
        D2ProjectRegistry.recordOpened(lProps, lStale);
        assertTrue(new File(lStale, "project.properties").delete());

        // A read-only view must filter out the stale entry for the caller...
        List<File> lValid = D2ProjectRegistry.getValidRecentProjects(lProps);
        assertEquals(1, lValid.size());
        assertEquals(lKept.getAbsoluteFile(), lValid.get(0).getAbsoluteFile());

        // ...without having silently purged pProperties itself (only purgeMissing() may do that).
        assertEquals(2, D2ProjectRegistry.getRecentProjects(lProps).size());
    }
}
