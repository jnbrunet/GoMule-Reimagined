package gomule.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2Project addressed by directory (plan section 4/6). Every test here passes a null
 * D2FileManager to the constructor: that field is only ever dereferenced by addChar()/addStash()/
 * addSharedStash()/deleteCharStash()/setBankValue() (all of which reach into
 * D2FileManager or the D2ViewClipboard/D2ViewProject singletons -- exactly the Swing machinery
 * this suite must never load), and none of those are exercised below. The constructor itself and
 * saveProject() never touch it.
 */
public class D2ProjectTest {

    @Test
    public void isProjectDirIsTrueOnlyWithAProjectPropertiesFile(@TempDir File pTempDir) throws Exception {
        assertFalse(D2Project.isProjectDir(pTempDir), "an empty directory is not a project yet");
        assertFalse(D2Project.isProjectDir(new File(pTempDir, "does-not-exist")));
        assertFalse(D2Project.isProjectDir(null));

        // Constructing a D2Project over pTempDir is itself what creates project.properties (a
        // "new" project, per the constructor's own lNew logic) -- exercised separately below by
        // the round-trip test, so this uses a plain touch instead to keep this test about
        // isProjectDir() alone.
        File lPropsFile = new File(pTempDir, "project.properties");
        assertTrue(lPropsFile.createNewFile());
        assertTrue(D2Project.isProjectDir(pTempDir));
    }

    @Test
    public void projectNameIsTheDirectoryName(@TempDir File pTempDir) {
        File lProjectDir = new File(pTempDir, "MyLadderProject");
        D2Project lProject = new D2Project(null, lProjectDir);
        assertEquals("MyLadderProject", lProject.getProjectName());
        assertEquals(lProjectDir.getAbsolutePath(), lProject.getProjectDir());
        assertEquals(lProjectDir.getAbsoluteFile(), lProject.getProjectDirFile().getAbsoluteFile());
    }

    @Test
    public void constructorCreatesTheDirectoryButNotProjectPropertiesYet(@TempDir File pTempDir) {
        File lProjectDir = new File(pTempDir, "brand-new");
        assertFalse(lProjectDir.exists());

        D2Project lProject = new D2Project(null, lProjectDir);
        assertTrue(lProjectDir.isDirectory(), "the constructor must create the project directory itself");
        // project.properties is only written by an explicit saveProject() call (below) -- the
        // constructor alone must not have created it, so a project that is opened but never
        // touched leaves no trace on disk.
        assertFalse(D2Project.isProjectDir(lProjectDir));

        // Defaults for a genuinely new project (the "lNew" branch of the constructor).
        assertEquals(0, lProject.getBankValue());
        assertEquals(D2Project.TYPE_BOTH, lProject.getType());
        assertTrue(lProject.getAllowDelete());
    }

    @Test
    public void saveProjectThenReconstructingRoundTripsEverySetting(@TempDir File pTempDir) {
        // addChar()/addStash()/addSharedStash() and setBankValue() each also poke the (here null)
        // D2FileManager / the D2ViewClipboard singleton to refresh the UI, so this round-trips only
        // the settings that are plain field assignments -- type/backup/allowDelete -- rather than
        // bank or the file lists, which would need a real D2FileManager (i.e. the live Swing app)
        // to exercise via their public setters.
        File lProjectDir = new File(pTempDir, "roundtrip");
        D2Project lProject = new D2Project(null, lProjectDir);
        lProject.setType(D2Project.TYPE_HC);
        lProject.setBackup(D2Project.BACKUP_MONTH);
        lProject.setAllowDelete(false);
        lProject.saveProject();

        assertTrue(D2Project.isProjectDir(lProjectDir), "saveProject() must have written project.properties");

        D2Project lReloaded = new D2Project(null, lProjectDir);
        assertEquals(D2Project.TYPE_HC, lReloaded.getType());
        assertEquals(D2Project.BACKUP_MONTH, lReloaded.getBackup());
        assertFalse(lReloaded.getAllowDelete());
        // Bank defaults to 0 for a new project and is never touched by this test -- still worth
        // pinning that saveProject()/reload round-trips it too, without going through the
        // Swing-coupled setBankValue() setter.
        assertEquals(0, lReloaded.getBankValue());
    }
}
