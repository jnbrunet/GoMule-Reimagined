package gomule.util;

import java.io.File;
import java.util.function.Function;

/**
 * Where GoMule keeps a user's own data: the projects registry (projects.properties) and, inside
 * it, the projects themselves (plan PLAN-projets-ouvrir-fermer.md, section 3). Deliberately
 * Swing-free -- the one-shot "your projects moved" dialog lives in D2UserDataMigration instead,
 * so this class stays unit-testable headless with no HeadlessException risk.
 */
public final class D2UserData {
    /**
     * The escape hatch: set this system property (e.g. {@code -Dgomule.userdata.dir=...} on the
     * command line, or a portable install's own launcher script) to use that exact folder instead
     * of the OS-specific location below. This is also the test injection point -- see
     * D2UserDataTest -- so a test never has to touch a real per-OS user-data folder.
     */
    public static final String OVERRIDE_PROPERTY = "gomule.userdata.dir";

    private static final String APP_DIR_NAME = "GoMule-Reimagined";

    // Sticky for the life of the process, deliberately: the one-time startup warning this backs
    // is "showed it once, never again this run", not "recompute on every call" -- see
    // isFallbackActive()'s javadoc.
    private static volatile boolean sFallbackActive = false;

    private D2UserData() {
    }

    /**
     * True if the most recent call to {@link #getUserDataDir()} (or one of the methods below,
     * which all go through it) had to fall back to {@code ./projects} because the real per-OS
     * location could not be created or written to. GoMule.main() reads this once, right after
     * migrating, to decide whether to show the one-time fallback warning -- plan section 3:
     * "mieux vaut un GoMule qui démarre comme avant qu'un GoMule qui refuse de s'ouvrir".
     */
    public static boolean isFallbackActive() {
        return sFallbackActive;
    }

    /**
     * The root of GoMule's user data, created if it doesn't exist yet. Resolution order (plan
     * section 3):
     * <ol>
     *   <li>{@value #OVERRIDE_PROPERTY} system property, if set -- portable installs and tests.</li>
     *   <li>Windows: {@code %APPDATA%\GoMule-Reimagined}, or the same path derived from
     *   {@code user.home} if the environment variable itself is unset (some service/CI
     *   accounts).</li>
     *   <li>macOS: {@code ~/Library/Application Support/GoMule-Reimagined}.</li>
     *   <li>Anything else (Linux/BSD/...): {@code $XDG_DATA_HOME/GoMule-Reimagined}, else
     *   {@code ~/.local/share/GoMule-Reimagined}.</li>
     * </ol>
     * If the resolved directory can't be created or isn't writable, this falls back to the
     * pre-3.x layout (the process's working directory, which {@link #getProjectsDir()} then
     * turns into {@code ./projects} exactly like the old {@code D2Project.PROJECTS_DIR}) rather
     * than refusing to start at all.
     */
    public static File getUserDataDir() {
        File lResolved = resolveUserDataDir(
                System.getProperty(OVERRIDE_PROPERTY),
                System.getProperty("os.name", ""),
                System::getenv,
                System.getProperty("user.home", "."));
        if (ensureUsable(lResolved)) {
            sFallbackActive = false;
            return lResolved;
        }
        sFallbackActive = true;
        File lFallback = new File(".");
        System.err.println("GoMule: could not use " + lResolved.getAbsolutePath()
                + " for user data (not creatable or not writable); falling back to "
                + lFallback.getAbsolutePath());
        return lFallback;
    }

    /**
     * {@code <user data root>/projects} -- the registry of every project GoMule knows how to open
     * lives one level above this (projects.properties, next to it, not inside it -- see the plan
     * tree in section 3), but the projects themselves default here.
     */
    public static File getProjectsDir() {
        File lDir = new File(getUserDataDir(), "projects");
        lDir.mkdirs();
        return lDir;
    }

    /**
     * {@code <projects dir>/GoMule} -- the project GoMule opens on a genuinely first run, and the
     * one the "Del Proj" button (D2FileManager) refuses to ever delete.
     */
    public static File getDefaultProjectDir() {
        return new File(getProjectsDir(), "GoMule");
    }

    /**
     * The actual resolution logic, factored out as a package-private overload purely so
     * D2UserDataTest can drive every branch (override set/unset, each OS family, {@code %APPDATA%}
     * present/absent, {@code XDG_DATA_HOME} present/absent) by passing plain arguments, instead of
     * needing to mutate real system properties/environment variables (the latter isn't even
     * possible from within the JVM) -- see the plan's section 6 test table.
     *
     * @param pOverride the {@value #OVERRIDE_PROPERTY} system property value, or null/blank.
     * @param pOsName   what {@code os.name} would report (e.g. "Windows 11", "Mac OS X", "Linux").
     * @param pEnv      an environment-variable lookup (production passes {@code System::getenv}).
     * @param pUserHome what {@code user.home} would report.
     */
    static File resolveUserDataDir(String pOverride, String pOsName, Function<String, String> pEnv, String pUserHome) {
        if (pOverride != null && !pOverride.trim().isEmpty()) {
            return new File(pOverride);
        }

        String lOsName = pOsName == null ? "" : pOsName.toLowerCase();
        if (lOsName.startsWith("windows")) {
            String lAppData = pEnv.apply("APPDATA");
            if (lAppData != null && !lAppData.trim().isEmpty()) {
                return new File(lAppData, APP_DIR_NAME);
            }
            // %APPDATA% unset (plan section 3, point 2) -- derive the same Roaming path by hand
            // rather than giving up on the Windows branch entirely.
            return new File(pUserHome, "AppData" + File.separator + "Roaming" + File.separator + APP_DIR_NAME);
        }
        if (lOsName.startsWith("mac") || lOsName.contains("darwin")) {
            return new File(pUserHome, "Library" + File.separator + "Application Support" + File.separator + APP_DIR_NAME);
        }
        String lXdgDataHome = pEnv.apply("XDG_DATA_HOME");
        if (lXdgDataHome != null && !lXdgDataHome.trim().isEmpty()) {
            return new File(lXdgDataHome, APP_DIR_NAME);
        }
        return new File(pUserHome, ".local" + File.separator + "share" + File.separator + APP_DIR_NAME);
    }

    /**
     * True if pDir already is, or could be made into, a directory GoMule can actually write to --
     * the single check {@link #getUserDataDir()}'s fallback hinges on.
     */
    private static boolean ensureUsable(File pDir) {
        if (!pDir.exists() && !pDir.mkdirs()) {
            return false;
        }
        return pDir.isDirectory() && pDir.canWrite();
    }
}
