package gomule.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2UserData's path resolution (plan PLAN-projets-ouvrir-fermer.md, sections 3 and 6). Entirely
 * headless -- this class has no Swing dependency of its own -- and never touches a real per-OS
 * user-data folder or the repo's own {@code ./projects}: every branch of
 * {@link D2UserData#resolveUserDataDir} is driven directly through its package-private parameters
 * (os.name/env/user.home as plain arguments, exactly as the plan's test table calls for), and the
 * few tests that go through the public {@link D2UserData#getUserDataDir()} entry point instead use
 * the {@value D2UserData#OVERRIDE_PROPERTY} system property pointed at a JUnit {@code @TempDir}.
 * The override property is always cleared afterward so no other test in the suite ever sees it
 * still set.
 */
public class D2UserDataTest {

    @AfterEach
    public void clearOverrideProperty() {
        System.clearProperty(D2UserData.OVERRIDE_PROPERTY);
    }

    // ------------------------------------------------------------------------------------------
    // resolveUserDataDir() -- the pure, parameterised resolution logic.
    // ------------------------------------------------------------------------------------------

    private static Function<String, String> envOf(Map<String, String> pMap) {
        return pMap::get;
    }

    @Test
    public void overridePropertyWinsOverEveryOsBranch() {
        File lResolved = D2UserData.resolveUserDataDir("D:\\Portable\\GoMuleData", "Windows 11", envOf(new HashMap<>()), "C:\\Users\\u");
        assertEquals(new File("D:\\Portable\\GoMuleData"), lResolved);
    }

    @Test
    public void blankOverrideFallsThroughToOsResolution() {
        // A blank (empty or all-whitespace) override must behave as if it were never set at all --
        // e.g. a launcher script that always passes -Dgomule.userdata.dir="${VAR}" with VAR unset.
        File lResolved = D2UserData.resolveUserDataDir("   ", "Linux", key -> null, "/home/u");
        assertEquals(new File("/home/u/.local/share/GoMule-Reimagined"), lResolved);
    }

    @Test
    public void windowsWithAppDataUsesRoamingProfile() {
        Map<String, String> lEnv = new HashMap<>();
        lEnv.put("APPDATA", "C:\\Users\\u\\AppData\\Roaming");
        File lResolved = D2UserData.resolveUserDataDir(null, "Windows 10", envOf(lEnv), "C:\\Users\\u");
        assertEquals(new File("C:\\Users\\u\\AppData\\Roaming", "GoMule-Reimagined"), lResolved);
    }

    @Test
    public void windowsWithoutAppDataDerivesRoamingPathFromUserHome() {
        // %APPDATA% unset (plan section 3, point 2 -- some service/CI accounts don't have it) must
        // still land on the same Roaming path, derived by hand instead of giving up on Windows.
        File lResolved = D2UserData.resolveUserDataDir(null, "Windows 10", key -> null, "C:\\Users\\u");
        assertEquals(new File("C:\\Users\\u", "AppData" + File.separator + "Roaming" + File.separator + "GoMule-Reimagined"), lResolved);
    }

    @Test
    public void macOsUsesApplicationSupport() {
        File lResolved = D2UserData.resolveUserDataDir(null, "Mac OS X", key -> null, "/Users/u");
        assertEquals(new File("/Users/u", "Library" + File.separator + "Application Support" + File.separator + "GoMule-Reimagined"), lResolved);
    }

    @Test
    public void linuxWithXdgDataHomeUsesIt() {
        Map<String, String> lEnv = new HashMap<>();
        lEnv.put("XDG_DATA_HOME", "/home/u/.data");
        File lResolved = D2UserData.resolveUserDataDir(null, "Linux", envOf(lEnv), "/home/u");
        assertEquals(new File("/home/u/.data", "GoMule-Reimagined"), lResolved);
    }

    @Test
    public void linuxWithoutXdgDataHomeFallsBackToDotLocalShare() {
        File lResolved = D2UserData.resolveUserDataDir(null, "Linux", key -> null, "/home/u");
        assertEquals(new File("/home/u", ".local" + File.separator + "share" + File.separator + "GoMule-Reimagined"), lResolved);
    }

    // ------------------------------------------------------------------------------------------
    // Public entry points, through the override property (the test injection point per the
    // plan) so nothing here ever depends on a real per-OS folder.
    // ------------------------------------------------------------------------------------------

    @Test
    public void getUserDataDirHonorsOverrideAndIsUsable(@TempDir File pTempDir) {
        System.setProperty(D2UserData.OVERRIDE_PROPERTY, pTempDir.getAbsolutePath());
        assertEquals(pTempDir.getAbsoluteFile(), D2UserData.getUserDataDir().getAbsoluteFile());
        assertFalse(D2UserData.isFallbackActive(), "a real, writable @TempDir must never trigger the fallback");
    }

    @Test
    public void getProjectsDirAndDefaultProjectDirNestUnderTheOverride(@TempDir File pTempDir) {
        System.setProperty(D2UserData.OVERRIDE_PROPERTY, pTempDir.getAbsolutePath());
        File lProjectsDir = D2UserData.getProjectsDir();
        assertEquals(new File(pTempDir, "projects").getAbsoluteFile(), lProjectsDir.getAbsoluteFile());
        assertTrue(lProjectsDir.isDirectory(), "getProjectsDir() must create the directory, not just name it");

        File lDefaultProjectDir = D2UserData.getDefaultProjectDir();
        assertEquals(new File(lProjectsDir, "GoMule").getAbsoluteFile(), lDefaultProjectDir.getAbsoluteFile());
    }

    @Test
    public void unusableOverrideTriggersTheLegacyFallback(@TempDir File pTempDir) throws Exception {
        // Point the override at a path whose PARENT is a plain file rather than a directory --
        // mkdirs() cannot create a child of a file, so ensureUsable() must fail and getUserDataDir()
        // must fall back to "./projects" (plan section 3's "mieux vaut ... qu'un GoMule qui refuse
        // de s'ouvrir") instead of throwing.
        File lBlockingFile = new File(pTempDir, "not-a-directory");
        assertTrue(lBlockingFile.createNewFile());
        System.setProperty(D2UserData.OVERRIDE_PROPERTY, new File(lBlockingFile, "sub").getAbsolutePath());

        try {
            File lResolved = D2UserData.getUserDataDir();
            assertEquals(new File(".").getAbsoluteFile(), lResolved.getAbsoluteFile());
            assertTrue(D2UserData.isFallbackActive());
        } finally {
            // sFallbackActive is sticky process-wide state (by design -- see its javadoc); reset it
            // with one more, genuinely usable, call so no later test in the suite observes a stale
            // "fallback active" flag left behind by this one.
            System.setProperty(D2UserData.OVERRIDE_PROPERTY, pTempDir.getAbsolutePath());
            D2UserData.getUserDataDir();
        }
    }
}
