package gomule.grail;

import gomule.util.D2Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * "First seen" persistence (plan section 8, option A): the date a save file never actually
 * records, so GoMule keeps its own record of the first time it ever scanned a given grail entry
 * into existence. Deliberately Swing-free (only {@link D2Project#PROJECTS_DIR}, a plain constant,
 * is pulled from the gui-adjacent util package) so it stays unit-testable headless.
 * <p>
 * Backed by a flat {@link Properties} file, the exact shape {@code D2Project.saveProject()}
 * already uses elsewhere: one line per key, {@code UNIQUE:412=1756900000000} (a
 * {@link D2GrailKey#toString()} to epoch-millis mapping) -- there is no need to parse the key back
 * into a {@link D2GrailKey}, only to look one up by an already-known key, so its {@code toString()}
 * is used as-is rather than inventing a second encoding.
 * <p>
 * A date, once written, is never overwritten or removed -- not even if the item is later sold,
 * the file that had it gets closed, or a rescan no longer finds it. "First seen" means exactly
 * that: the first time, ever, this installation of GoMule saw it.
 */
public final class D2GrailFirstSeenStore {

    private static final String FILE_SUFFIX = "-grail.properties";

    private final File iFile;
    private final Properties iProperties;

    private D2GrailFirstSeenStore(File pFile, Properties pProperties) {
        iFile = pFile;
        iProperties = pProperties;
    }

    /**
     * The real production location: a flat file directly under {@code projects/} (NOT inside the
     * project's own subdirectory), named after the project -- exactly what plan section 8
     * specifies.
     */
    public static File fileFor(String pProjectName) {
        return new File(D2Project.PROJECTS_DIR, pProjectName + FILE_SUFFIX);
    }

    /**
     * Loads the store for the given file, or starts a fresh, empty one if the file doesn't exist
     * yet, can't be read, or is corrupt. A first-seen record is a nice-to-have, not something
     * that should ever be able to stop the grail window from opening -- this deliberately never
     * throws.
     */
    public static D2GrailFirstSeenStore load(File pFile) {
        Properties lProperties = new Properties();
        if (pFile.exists()) {
            FileInputStream lIn = null;
            try {
                lIn = new FileInputStream(pFile);
                lProperties.load(lIn);
            } catch (Exception pEx) {
                // Missing, unreadable, or genuinely corrupt (not valid .properties syntax) all
                // degrade the same way: start empty rather than losing every recorded date, or
                // worse, throwing and taking the grail window down with it.
                lProperties = new Properties();
            } finally {
                if (lIn != null) {
                    try {
                        lIn.close();
                    } catch (IOException pIgnored) {
                        // nothing sensible to do about a close failure on a file we only read.
                    }
                }
            }
        }
        return new D2GrailFirstSeenStore(pFile, lProperties);
    }

    /**
     * Records {@code pTimestampMillis} for every key in {@code pKeys} that has no date yet.
     * Existing dates are left untouched -- this is an "if absent" write, never an overwrite.
     *
     * @return true if at least one new date was actually recorded (so the caller knows whether
     * {@link #save()} is worth calling).
     */
    public boolean recordFirstSeenIfAbsent(Iterable<D2GrailKey> pKeys, long pTimestampMillis) {
        boolean lChanged = false;
        for (D2GrailKey lKey : pKeys) {
            String lPropertyKey = lKey.toString();
            if (!iProperties.containsKey(lPropertyKey)) {
                iProperties.setProperty(lPropertyKey, String.valueOf(pTimestampMillis));
                lChanged = true;
            }
        }
        return lChanged;
    }

    /**
     * @return the epoch-millis this key was first seen at, or null if it never has been.
     */
    public Long getFirstSeenMillis(D2GrailKey pKey) {
        String lValue = iProperties.getProperty(pKey.toString());
        if (lValue == null) {
            return null;
        }
        try {
            return Long.parseLong(lValue.trim());
        } catch (NumberFormatException pEx) {
            // A hand-edited or corrupted single line must not poison the date for every other
            // (perfectly fine) key in the same file.
            return null;
        }
    }

    /**
     * Writes the store back to disk, creating the projects/ directory if it doesn't exist yet.
     * Best-effort: a failure here is swallowed rather than thrown, since this class has no Swing
     * dependency to pop an error dialog with, and losing one save of a nice-to-have timestamp is
     * far preferable to that failure propagating into the middle of a rescan.
     */
    public void save() {
        FileOutputStream lOut = null;
        try {
            File lParent = iFile.getParentFile();
            if (lParent != null && !lParent.exists()) {
                lParent.mkdirs();
            }
            lOut = new FileOutputStream(iFile);
            iProperties.store(lOut, null);
        } catch (IOException pEx) {
            // Best-effort persistence -- see the class/method javadoc above.
        } finally {
            if (lOut != null) {
                try {
                    lOut.close();
                } catch (IOException pIgnored) {
                    // nothing sensible to do about a close failure after the write already ran.
                }
            }
        }
    }

    /**
     * "First seen: 08/21/2026 20:56" (plan section 3.3) -- deliberately not "First Found:", the
     * game's own label: this is when GoMule's own scan first noticed the entry, not when the item
     * actually dropped, and the wording says so honestly rather than implying otherwise.
     */
    public static String formatDate(long pEpochMillis) {
        return new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date(pEpochMillis));
    }
}
