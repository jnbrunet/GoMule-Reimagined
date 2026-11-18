package gomule.d2i;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class D2StashTabNamesTest {

    // The real pyre-stashsearch.toml the names were confirmed against (see D2StashTabNames).
    private static final String CONFIG = "# Pyre Stash Search configuration\n"
            + "[pyre-stashsearch]\n"
            + "# Keep the side tab flyout expanded across stash opens and game launches.\n"
            + "tab_flyout_expanded = true\n"
            + "\n"
            + "# Optional custom tab names (or right-click any tab in the Side Tab Flyout in-game to rename):\n"
            + "# tab_name_personal = \"Personal\"\n"
            + "# tab_name_shared_1 = \"Runes\"\n"
            + "# tab_name_shared_2 = \"Bases\"\n"
            + "tab_1 = \"Weapon\"\n"
            + "tab_2 = \"Weapon\"\n"
            + "tab_3 = \"weapon\"\n"
            + "tab_4 = \"armor\"\n"
            + "tab_5 = \"armor\"\n"
            + "tab_6 = \"armor\"\n"
            + "tab_7 = \"bolts\"\n"
            + "tab_13 = \"jewels\"\n"
            + "tab_14 = \"charms\"\n"
            + "tab_15 = \"ring-amu\"\n";

    @Test
    void readsTheNamesAndIgnoresTheCommentedExamples() {
        Map<Integer, String> names = D2StashTabNames.parse(Arrays.asList(CONFIG.split("\n")));
        assertEquals(10, names.size());
        assertEquals("Weapon", names.get(1));
        assertEquals("bolts", names.get(7));
        assertEquals("jewels", names.get(13));
        assertEquals("ring-amu", names.get(15));
        assertNull(names.get(8));
    }

    @Test
    void readsTheDocumentedSpellingAndEscapes() {
        Map<Integer, String> names = D2StashTabNames.parse(Arrays.asList(
                "tab_name_shared_2 = \"Bases\"", "tab_3 = \"say \\\"hi\\\"\"", "tab_4 = \"  \""));
        assertEquals("Bases", names.get(2));
        assertEquals("say \"hi\"", names.get(3));
        assertNull(names.get(4), "a blank name is no name");
    }

    // The save folder is the mod's savepath, not its name: "Reimagined" saves to "ReimaginedThree".
    @Test
    void findsTheConfigOfTheModThatSavesIntoTheStashFolder(@TempDir Path tmp) throws Exception {
        File install = tmp.resolve("Diablo II Resurrected").toFile();
        File reimagined = mod(install, "Reimagined", "{\"name\": \"Reimagined\", \"savepath\": \"ReimaginedThree/\"}");
        File other = mod(install, "Other", null);
        File stash = tmp.resolve("Saved Games/mods/ReimaginedThree/ModernSharedStashSoftCoreV2.d2i").toFile();

        assertEquals(new File(reimagined, "d2rloader/config/pyre-stashsearch.toml"),
                D2StashTabNames.findConfig(stash, Collections.singletonList(install)));
        assertEquals(new File(other, "d2rloader/config/pyre-stashsearch.toml"),
                D2StashTabNames.findConfig(tmp.resolve("Saved Games/mods/Other/x.d2i").toFile(),
                        Collections.singletonList(install)), "no savepath: the mod's own name");
        assertNull(D2StashTabNames.findConfig(tmp.resolve("Saved Games/mods/Nope/x.d2i").toFile(),
                Collections.singletonList(install)));
        assertTrue(D2StashTabNames.forStash(tmp.resolve("nowhere/x.d2i").toFile()).isEmpty(),
                "no mod saves there: no names, no failure");
    }

    private static File mod(File install, String name, String modInfo) throws Exception {
        File mod = new File(install, "mods/" + name);
        if (modInfo != null) {
            File mpq = new File(mod, name + ".mpq");
            mpq.mkdirs();
            Files.write(new File(mpq, "modinfo.json").toPath(), modInfo.getBytes(StandardCharsets.UTF_8));
        }
        File config = new File(mod, "d2rloader/config");
        config.mkdirs();
        Files.write(new File(config, "pyre-stashsearch.toml").toPath(), CONFIG.getBytes(StandardCharsets.UTF_8));
        return mod;
    }
}
