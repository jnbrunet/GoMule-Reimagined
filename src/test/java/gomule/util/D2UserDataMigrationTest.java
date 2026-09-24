package gomule.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2UserDataMigration.migrateIfNeeded(), the pure copy (plan section 5, step 1) -- deliberately
 * NOT migrateIfNeededOnStartup(), which resolves the real per-OS user-data folder and pops a
 * JOptionPane; a headless test must never call that one. Every test here works under a single
 * {@code @TempDir} root, split by hand into "legacy" and "userdata" subdirectories -- the
 * project is pinned to JUnit Jupiter 5.5.2 (build.gradle), whose @TempDir support resolves every
 * @TempDir-annotated PARAMETER on the same test method to the very same directory (fixed only in
 * 5.9); two separate @TempDir parameters here would silently alias legacy and target into one
 * folder instead of two. Nothing here touches the repository's own {@code ./projects}.
 */
public class D2UserDataMigrationTest {

    private static void write(File pFile, String pContent) throws IOException {
        Files.createDirectories(pFile.getParentFile().toPath());
        try (FileWriter lWriter = new FileWriter(pFile)) {
            lWriter.write(pContent);
        }
    }

    @Test
    public void copiesProjectDirectoriesRecursively(@TempDir File pRoot) throws IOException {
        File lLegacyProjects = new File(pRoot, "legacy/projects");
        File lUserDataDir = new File(pRoot, "userdata");
        write(new File(lLegacyProjects, "GoMule/project.properties"), "bank=100");
        write(new File(lLegacyProjects, "GoMule/Clipboard.d2x"), "fake-clipboard-bytes");
        write(new File(lLegacyProjects, "GoMule/sub/deep.txt"), "nested file");

        D2UserDataMigration.Result lResult = D2UserDataMigration.migrateIfNeeded(lLegacyProjects, lUserDataDir);
        assertTrue(lResult.didMigrate(), "failure=" + lResult.getFailure());

        File lNewProjectDir = new File(lUserDataDir, "projects/GoMule");
        assertEquals("bank=100", new String(Files.readAllBytes(new File(lNewProjectDir, "project.properties").toPath())));
        assertEquals("fake-clipboard-bytes", new String(Files.readAllBytes(new File(lNewProjectDir, "Clipboard.d2x").toPath())));
        assertEquals("nested file", new String(Files.readAllBytes(new File(lNewProjectDir, "sub/deep.txt").toPath())));

        // The original is a backup, never touched -- plan section 6's first listed risk ("perte de
        // données perçue") hinges on this being a copy, not a move.
        assertTrue(new File(lLegacyProjects, "GoMule/project.properties").isFile());
    }

    @Test
    public void promotesGlobalPropertiesOneLevelUp(@TempDir File pRoot) throws IOException {
        File lLegacyProjects = new File(pRoot, "legacy/projects");
        File lUserDataDir = new File(pRoot, "userdata");
        write(new File(lLegacyProjects, "projects.properties"), "current-project=GoMule");
        write(new File(lLegacyProjects, "GoMule/project.properties"), "bank=0");

        D2UserDataMigration.migrateIfNeeded(lLegacyProjects, lUserDataDir);

        // Promoted to a SIBLING of "projects", not inside it (plan section 3's tree) -- so it is
        // never mistaken for a project of its own.
        File lPromoted = new File(lUserDataDir, "projects.properties");
        assertTrue(lPromoted.isFile());
        assertEquals("current-project=GoMule", new String(Files.readAllBytes(lPromoted.toPath())));
        assertFalse(new File(lUserDataDir, "projects/projects.properties").exists());
    }

    @Test
    public void noOpWhenTargetProjectsDirAlreadyExists(@TempDir File pRoot) throws IOException {
        File lLegacyProjects = new File(pRoot, "legacy/projects");
        File lUserDataDir = new File(pRoot, "userdata");
        write(new File(lLegacyProjects, "GoMule/project.properties"), "bank=0");

        File lTargetProjects = new File(lUserDataDir, "projects");
        assertTrue(lTargetProjects.mkdirs()); // simulate "migration already happened".

        D2UserDataMigration.Result lResult = D2UserDataMigration.migrateIfNeeded(lLegacyProjects, lUserDataDir);
        assertFalse(lResult.didMigrate());
        // Nothing was copied into the pre-existing (empty) target -- a no-op must really do
        // nothing, not overwrite with a fresh, possibly-stale copy.
        assertEquals(0, lTargetProjects.list().length);
    }

    @Test
    public void noOpWhenLegacyProjectsDirDoesNotExist(@TempDir File pRoot) {
        // A genuinely new install: nothing under ./projects at all.
        File lLegacyProjectsThatDoesNotExist = new File(pRoot, "legacy/no-such-projects-dir");
        File lUserDataDir = new File(pRoot, "userdata");

        D2UserDataMigration.Result lResult = D2UserDataMigration.migrateIfNeeded(lLegacyProjectsThatDoesNotExist, lUserDataDir);
        assertFalse(lResult.didMigrate());
        assertFalse(new File(lUserDataDir, "projects").exists());
    }

    @Test
    public void secondCallIsANoOpAfterTheFirstMigrated(@TempDir File pRoot) throws IOException {
        File lLegacyProjects = new File(pRoot, "legacy/projects");
        File lUserDataDir = new File(pRoot, "userdata");
        write(new File(lLegacyProjects, "GoMule/project.properties"), "bank=0");

        D2UserDataMigration.Result lFirst = D2UserDataMigration.migrateIfNeeded(lLegacyProjects, lUserDataDir);
        assertTrue(lFirst.didMigrate(), "failure=" + lFirst.getFailure());

        // Simulate the user having since edited the migrated copy -- a second call must leave it
        // alone rather than re-copying the (now stale, from the migration's point of view) legacy
        // original back on top of it.
        File lMigratedProps = new File(lUserDataDir, "projects/GoMule/project.properties");
        write(lMigratedProps, "bank=999");

        D2UserDataMigration.Result lSecond = D2UserDataMigration.migrateIfNeeded(lLegacyProjects, lUserDataDir);
        assertFalse(lSecond.didMigrate());
        assertEquals("bank=999", new String(Files.readAllBytes(lMigratedProps.toPath())));
    }
}
