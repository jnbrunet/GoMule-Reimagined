package gomule.d2i;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Custom shared stash tab names set with the "Pyre Stash Search" D2RLoader plugin (right-click a
// tab in its Side Tab Flyout to rename it). The game's .d2i holds no names at all: the plugin keeps
// them in its own config, <D2R install>/mods/<mod>/d2rloader/config/pyre-stashsearch.toml, as
//     tab_13 = "jewels"
// where N counts the shared tabs from 1 the way D2SharedStash.getTabPaneIndices() does, i.e.
// skipping the stackable tab -- confirmed against a real stash whose "Weapon"/"armor"/"bolts"/
// "jewels"/"charms"/"ring-amu" tabs held exactly that (tab 13 = pane 13, 94 jewels). Its config
// header documents tab_name_shared_N as well, so that spelling is read too.
//
// The save folder is not named after the mod (mod "Reimagined" saves to "ReimaginedThree"), so the
// mod is found through the "savepath" in its modinfo.json.
public final class D2StashTabNames {

    private static final String CONFIG_NAME = "pyre-stashsearch.toml";
    private static final Pattern TAB_LINE =
            Pattern.compile("^\\s*tab_(?:name_shared_)?(\\d+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SAVE_PATH = Pattern.compile("\"savepath\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private D2StashTabNames() {
    }

    // 1-based shared tab number -> name, for the stash at pStashFile; empty when there are none.
    public static Map<Integer, String> forStash(File pStashFile) {
        File lConfig = findConfig(pStashFile, installDirs());
        if (lConfig == null) return Collections.emptyMap();
        try {
            return parse(Files.readAllLines(lConfig.toPath(), StandardCharsets.UTF_8));
        } catch (IOException pEx) {
            return Collections.emptyMap();
        }
    }

    // The plugin config belonging to the mod that saves into pStashFile's folder, or null.
    static File findConfig(File pStashFile, List<File> pInstallDirs) {
        File lSaveDir = pStashFile.getAbsoluteFile().getParentFile();
        if (lSaveDir == null) return null;
        String lSaveDirName = lSaveDir.getName();
        for (File lInstall : pInstallDirs) {
            File[] lMods = new File(lInstall, "mods").listFiles(File::isDirectory);
            if (lMods == null) continue;
            for (File lMod : lMods) {
                if (!lSaveDirName.equalsIgnoreCase(savePath(lMod))) continue;
                File lConfig = new File(lMod, "d2rloader/config/" + CONFIG_NAME);
                if (lConfig.isFile()) return lConfig;
            }
        }
        return null;
    }

    // The folder a mod saves into: modinfo.json's "savepath" (in <mod>/<mod>.mpq/), defaulting to
    // the mod's own name as the game does.
    private static String savePath(File pMod) {
        File lModInfo = new File(pMod, pMod.getName() + ".mpq/modinfo.json");
        if (lModInfo.isFile()) {
            try {
                String lJson = new String(Files.readAllBytes(lModInfo.toPath()), StandardCharsets.UTF_8);
                Matcher lMatcher = SAVE_PATH.matcher(lJson);
                if (lMatcher.find()) {
                    String lPath = lMatcher.group(1).replace("\\\\", "/").replaceAll("[/\\\\]+$", "");
                    if (!lPath.isEmpty()) return lPath;
                }
            } catch (IOException pEx) {
                // fall through to the default
            }
        }
        return pMod.getName();
    }

    static Map<Integer, String> parse(List<String> pLines) {
        Map<Integer, String> lNames = new HashMap<>();
        for (String lLine : pLines) {
            Matcher lMatcher = TAB_LINE.matcher(lLine);
            if (!lMatcher.find()) continue;
            String lName = lMatcher.group(2).replaceAll("\\\\(.)", "$1").trim();
            if (!lName.isEmpty()) lNames.put(Integer.parseInt(lMatcher.group(1)), lName);
        }
        return lNames;
    }

    // Where D2R may be installed: what the Battle.net installer registered, then the default.
    private static List<File> installDirs() {
        List<File> lDirs = new ArrayList<>();
        String lRegistered = registeredInstallDir();
        if (lRegistered != null) lDirs.add(new File(lRegistered));
        lDirs.add(new File("C:/Program Files/Diablo II Resurrected"));
        lDirs.add(new File("C:/Program Files (x86)/Diablo II Resurrected"));
        return lDirs;
    }

    private static String sRegisteredInstallDir;
    private static boolean sRegistryQueried = false;

    private static synchronized String registeredInstallDir() {
        if (sRegistryQueried) return sRegisteredInstallDir;
        sRegistryQueried = true;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
        try {
            Process lProcess = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Diablo II Resurrected",
                    "/v", "InstallLocation").redirectErrorStream(true).start();
            try (BufferedReader lReader = new BufferedReader(new InputStreamReader(lProcess.getInputStream()))) {
                String lLine;
                while ((lLine = lReader.readLine()) != null) {
                    int lAt = lLine.indexOf("REG_SZ");
                    if (lAt >= 0) sRegisteredInstallDir = lLine.substring(lAt + "REG_SZ".length()).trim();
                }
            }
            lProcess.waitFor();
        } catch (Exception pEx) {
            // no registry entry: the default locations still apply
        }
        return sRegisteredInstallDir;
    }
}
