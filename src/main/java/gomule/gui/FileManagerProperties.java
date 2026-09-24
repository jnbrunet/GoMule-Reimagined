package gomule.gui;

import com.google.common.io.Closeables;
import gomule.util.D2UserData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class FileManagerProperties {
    /**
     * Lives directly under the user-data root now, next to (not inside) "projects" -- plan
     * section 3's tree -- rather than under the legacy {@code projects/} folder, so the global
     * settings file is never mistaken for a project of its own. The file NAME is unchanged
     * ("projects.properties"): renaming it at the same time as moving it would make both the
     * migration and any future support diagnosis needlessly confusing (plan section 5, step 1).
     */
    public static File getFileManagerPropertiesFile() throws IOException {
        File lUserDataDir = D2UserData.getUserDataDir();
        if (!lUserDataDir.exists() && !lUserDataDir.mkdirs()) {
            throw new IOException("Could not create user data directory: "
                    + lUserDataDir.getAbsolutePath()
                    + " (check that GoMule has write permission there)");
        }

        File lProps = new File(lUserDataDir, "projects.properties");
        if (!lProps.exists() && !lProps.createNewFile()) {
            throw new IOException("Could not create properties file: "
                    + lProps.getAbsolutePath()
                    + " (check that GoMule has write permission there)");
        }
        return lProps;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static Properties loadFileManagerProperties() throws IOException {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(getFileManagerPropertiesFile());
            Properties properties = new Properties();
            properties.load(fileInputStream);
            return properties;
        } finally {
            Closeables.closeQuietly(fileInputStream);
        }
    }

    public static void saveFileManagerProperties(Properties properties) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(getFileManagerPropertiesFile());
            properties.store(out, null);
        } catch (IOException ex) {
            D2FileManager.displayErrorDialog(ex);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
