package gomule.util;

import javax.swing.JOptionPane;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One-shot migration of the pre-3.x {@code ./projects} layout into the new user-data location
 * (PLAN-projets-ouvrir-fermer.md, sections 3 and 5 step 1). The actual copy
 * ({@link #migrateIfNeeded(File, File)}) is deliberately Swing-free and takes both directories as
 * plain parameters instead of resolving them itself, so D2UserDataMigrationTest can drive it
 * against {@code @TempDir} fakes without ever touching the repository's real {@code ./projects}
 * or a real per-OS user-data folder. Only the production entry point,
 * {@link #migrateIfNeededOnStartup()}, resolves the real locations and pops the one-time "your
 * projects moved" dialog -- and is therefore the one method here a headless test must never call.
 */
public final class D2UserDataMigration {

    private D2UserDataMigration() {
    }

    /**
     * Called once, at the very top of {@code GoMule.main()} -- specifically before
     * {@code FileManagerProperties.loadFileManagerProperties()}, which would otherwise create an
     * empty destination {@code projects.properties} itself and make {@link #migrateIfNeeded}
     * think the migration had already happened (its no-op check is "does the destination already
     * exist", not "is it non-empty").
     */
    public static void migrateIfNeededOnStartup() {
        File lLegacyProjectsDir = new File(D2Project.PROJECTS_DIR);
        File lUserDataDir = D2UserData.getUserDataDir();
        Result lResult = migrateIfNeeded(lLegacyProjectsDir, lUserDataDir);
        if (!lResult.didMigrate()) {
            return;
        }

        System.out.println("GoMule: migrated user data from " + lLegacyProjectsDir.getAbsolutePath()
                + " to " + lUserDataDir.getAbsolutePath() + ":");
        for (String lEntry : lResult.getCopiedEntries()) {
            System.out.println("  " + lEntry);
        }

        // The user must be told where their data went in the same breath as it happening --
        // otherwise a suddenly-empty-looking install (its old ./projects is still THERE on disk,
        // but GoMule no longer reads it) reads exactly like data loss (plan section 6, first
        // listed risk). Shown with a null parent: this runs before D2FileManager's own window
        // exists at all.
        JOptionPane.showMessageDialog(
                null,
                "Your existing GoMule projects have been copied to:\n"
                        + new File(lUserDataDir, "projects").getAbsolutePath()
                        + "\n\nThe original files under \"" + lLegacyProjectsDir.getAbsolutePath()
                        + "\" were left untouched, as a backup.",
                "GoMule projects moved",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * The pure copy. No-ops (plan section 5, step 1):
     * <ul>
     *   <li>if {@code <userdata>/projects} already exists (migration already done, or a fresh
     *   install that has already created its own default project there);</li>
     *   <li>if pLegacyProjectsDir doesn't exist at all (a genuinely new install).</li>
     * </ul>
     * Otherwise copies pLegacyProjectsDir recursively to {@code <userdata>/projects}, EXCEPT its
     * top-level {@code projects.properties}, which moves up one level instead, to
     * {@code <userdata>/projects.properties} directly (plan section 3's tree: the global settings
     * file sits next to "projects", not inside it, so it's never confused for a project of its
     * own). The original is always left in place -- this method only ever reads from
     * pLegacyProjectsDir, never deletes or moves anything out of it.
     */
    public static Result migrateIfNeeded(File pLegacyProjectsDir, File pUserDataDir) {
        File lTargetProjectsDir = new File(pUserDataDir, "projects");
        if (lTargetProjectsDir.exists()) {
            return Result.noOp();
        }
        if (!pLegacyProjectsDir.isDirectory()) {
            return Result.noOp();
        }

        List<String> lCopied = new ArrayList<String>();
        try {
            Files.createDirectories(lTargetProjectsDir.toPath());
            File[] lTopLevelEntries = pLegacyProjectsDir.listFiles();
            if (lTopLevelEntries != null) {
                for (File lChild : lTopLevelEntries) {
                    if (lChild.getName().equals("projects.properties")) {
                        continue; // moves up a level -- handled separately below.
                    }
                    copyRecursively(lChild, new File(lTargetProjectsDir, lChild.getName()), lCopied);
                }
            }

            File lLegacyGlobalProps = new File(pLegacyProjectsDir, "projects.properties");
            if (lLegacyGlobalProps.isFile()) {
                File lNewGlobalProps = new File(pUserDataDir, "projects.properties");
                Files.copy(lLegacyGlobalProps.toPath(), lNewGlobalProps.toPath(), StandardCopyOption.REPLACE_EXISTING);
                lCopied.add(lLegacyGlobalProps.getAbsolutePath() + " -> " + lNewGlobalProps.getAbsolutePath());
            }
        } catch (IOException pEx) {
            return Result.failed(pEx);
        }
        return Result.migrated(lCopied);
    }

    private static void copyRecursively(File pSource, File pTarget, List<String> pCopiedLog) throws IOException {
        if (pSource.isDirectory()) {
            Files.createDirectories(pTarget.toPath());
            File[] lChildren = pSource.listFiles();
            if (lChildren != null) {
                for (File lChild : lChildren) {
                    copyRecursively(lChild, new File(pTarget, lChild.getName()), pCopiedLog);
                }
            }
        } else {
            Files.copy(pSource.toPath(), pTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pCopiedLog.add(pSource.getAbsolutePath() + " -> " + pTarget.getAbsolutePath());
        }
    }

    /**
     * What {@link #migrateIfNeeded} actually did -- a plain value so both the startup wrapper and
     * tests can distinguish "copied N things" from "nothing to do" from "hit an I/O error"
     * without re-inspecting the filesystem themselves.
     */
    public static final class Result {
        private final boolean iMigrated;
        private final List<String> iCopiedEntries;
        private final IOException iFailure;

        private Result(boolean pMigrated, List<String> pCopiedEntries, IOException pFailure) {
            iMigrated = pMigrated;
            iCopiedEntries = pCopiedEntries;
            iFailure = pFailure;
        }

        static Result noOp() {
            return new Result(false, Collections.<String>emptyList(), null);
        }

        static Result migrated(List<String> pCopiedEntries) {
            return new Result(true, pCopiedEntries, null);
        }

        static Result failed(IOException pFailure) {
            return new Result(false, Collections.<String>emptyList(), pFailure);
        }

        public boolean didMigrate() {
            return iMigrated;
        }

        public List<String> getCopiedEntries() {
            return iCopiedEntries;
        }

        public IOException getFailure() {
            return iFailure;
        }
    }
}
