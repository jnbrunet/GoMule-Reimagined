package gomule.grail;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2GrailFirstSeenStore, entirely headless: no Swing, and this class's only outside dependency
 * (gomule.util.D2Project, for the PROJECTS_DIR constant) is a plain constants/data class with no
 * static side effects, unlike D2FileManager. Every test uses its own temp file rather than the
 * real production location (D2Project.PROJECTS_DIR), so nothing here touches the repo's own
 * projects/ directory.
 */
public class D2GrailFirstSeenStoreTest {

    @Test
    public void recordsAndReloadsADate() throws IOException {
        File lFile = File.createTempFile("grail-first-seen", ".properties");
        lFile.deleteOnExit();

        D2GrailFirstSeenStore lStore = D2GrailFirstSeenStore.load(lFile);
        D2GrailKey lKey = D2GrailKey.unique(146);
        assertNull(lStore.getFirstSeenMillis(lKey), "nothing recorded yet");

        boolean lChanged = lStore.recordFirstSeenIfAbsent(Collections.singletonList(lKey), 1_700_000_000_000L);
        assertTrue(lChanged);
        lStore.save();

        D2GrailFirstSeenStore lReloaded = D2GrailFirstSeenStore.load(lFile);
        assertEquals(1_700_000_000_000L, lReloaded.getFirstSeenMillis(lKey));
    }

    @Test
    public void neverOverwritesAnExistingDate() throws IOException {
        File lFile = File.createTempFile("grail-first-seen", ".properties");
        lFile.deleteOnExit();

        D2GrailFirstSeenStore lStore = D2GrailFirstSeenStore.load(lFile);
        D2GrailKey lKey = D2GrailKey.runeword("Insight");
        lStore.recordFirstSeenIfAbsent(Collections.singletonList(lKey), 1_000L);

        // A later "rescan" seeing the same key again must not move its date forward -- "first
        // seen" means the FIRST time, not the most recent.
        boolean lChangedOnSecondCall = lStore.recordFirstSeenIfAbsent(Collections.singletonList(lKey), 9_999_999L);
        assertFalse(lChangedOnSecondCall, "recording an already-known key must report no change");
        assertEquals(1_000L, lStore.getFirstSeenMillis(lKey));
    }

    @Test
    public void recordsEveryKeyRegardlessOfChronicleStatus() throws IOException {
        // D2GrailFirstSeenStore has no notion of "chronicle" at all -- that is the point: plan
        // section 8 requires every discovery to be timestamped, not just the in-game-grail ones,
        // so this simply proves the store never filters what it is given.
        File lFile = File.createTempFile("grail-first-seen", ".properties");
        lFile.deleteOnExit();
        D2GrailFirstSeenStore lStore = D2GrailFirstSeenStore.load(lFile);

        D2GrailKey lChronicleKey = D2GrailKey.unique(1);
        D2GrailKey lNonChronicleKey = D2GrailKey.unique(431); // e.g. a "Crafted ..." style entry
        lStore.recordFirstSeenIfAbsent(Arrays.asList(lChronicleKey, lNonChronicleKey), 42L);

        assertEquals(42L, lStore.getFirstSeenMillis(lChronicleKey));
        assertEquals(42L, lStore.getFirstSeenMillis(lNonChronicleKey));
    }

    @Test
    public void toleratesAMissingFile() {
        File lFile = new File(System.getProperty("java.io.tmpdir"), "definitely-does-not-exist-grail.properties");
        assertFalse(lFile.exists());

        D2GrailFirstSeenStore lStore = D2GrailFirstSeenStore.load(lFile);
        assertNull(lStore.getFirstSeenMillis(D2GrailKey.unique(1)), "a missing file must start empty, not throw");
    }

    @Test
    public void toleratesACorruptFile() throws IOException {
        File lFile = File.createTempFile("grail-first-seen-corrupt", ".properties");
        lFile.deleteOnExit();
        // Not valid Properties syntax at all (a raw non-UTF/garbled byte sequence via a backslash
        // that escapes into nothing meaningful) -- Properties.load() is fairly tolerant, so this
        // uses an explicit malformed unicode escape, which it does reject.
        FileWriter lWriter = new FileWriter(lFile);
        lWriter.write("UNIQUE:1=\\uZZZZ this is not a valid properties file at all");
        lWriter.close();

        D2GrailFirstSeenStore lStore = D2GrailFirstSeenStore.load(lFile);
        assertNull(lStore.getFirstSeenMillis(D2GrailKey.unique(1)), "a corrupt file must degrade to empty, not throw");

        // The store must still be fully usable afterward -- a corrupt on-disk file is not a
        // reason to also refuse writing a fresh one.
        lStore.recordFirstSeenIfAbsent(Collections.singletonList(D2GrailKey.unique(1)), 5L);
        lStore.save();
        assertEquals(5L, D2GrailFirstSeenStore.load(lFile).getFirstSeenMillis(D2GrailKey.unique(1)));
    }

    @Test
    public void formatsDatesAsFirstSeenNotFirstFound() {
        // Only pinning that formatDate() produces *something* stable and doesn't throw --
        // the exact wording distinction ("First seen:" vs the game's "First Found:") is a label
        // D2ViewGrail/D2GrailListRenderer own, not this class.
        String lFormatted = D2GrailFirstSeenStore.formatDate(1_700_000_000_000L);
        assertTrue(lFormatted.matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"), "unexpected format: " + lFormatted);
    }
}
