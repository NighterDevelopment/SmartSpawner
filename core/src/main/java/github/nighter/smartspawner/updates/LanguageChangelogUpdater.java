package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * Maintains {@code language/CHANGELOG.txt} in the plugin data folder.
 *
 * <p>This file is purely informational – it lists the language keys that were
 * added, changed, or removed in each plugin version so that users with custom
 * translations know exactly what to update after upgrading.
 *
 * <p>The file is extracted from the bundled JAR resource and written to disk
 * only when its content has changed (i.e. after a plugin update). On subsequent
 * restarts with the same plugin version the on-disk file is identical to the
 * bundled resource, so the write is skipped entirely.
 */
public class LanguageChangelogUpdater {

    private static final String RESOURCE_PATH = "language/CHANGELOG.txt";
    private static final String DEST_PATH     = "language/CHANGELOG.txt";

    private final SmartSpawner plugin;

    public LanguageChangelogUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Writes the bundled changelog to the plugin data folder.
     *
     * <p>If an up-to-date copy already exists on disk (byte-identical to the
     * bundled resource) the write is skipped to avoid unnecessary I/O on every
     * server start.
     */
    public void update() {
        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
            if (in == null) {
                plugin.getLogger().warning("language/CHANGELOG.txt not found in plugin JAR.");
                return;
            }

            byte[] bundled = in.readAllBytes();
            File dest = new File(plugin.getDataFolder(), DEST_PATH);

            if (isUpToDate(dest, bundled)) {
                plugin.debug("Language CHANGELOG.txt is already up-to-date – skipping write.");
                return;
            }

            ensureParentExists(dest);
            Files.write(dest.toPath(), bundled);
            plugin.debug("Language CHANGELOG.txt updated.");

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update language/CHANGELOG.txt", e);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code dest} exists and its content is byte-for-byte
     * identical to {@code expected}, meaning no write is necessary.
     */
    private boolean isUpToDate(File dest, byte[] expected) {
        if (!dest.exists()) return false;
        try {
            byte[] current = Files.readAllBytes(dest.toPath());
            return Arrays.equals(current, expected);
        } catch (IOException e) {
            // If we can't read the file, assume it needs updating
            plugin.debug("Could not read existing CHANGELOG.txt for comparison: " + e.getMessage());
            return false;
        }
    }

    private void ensureParentExists(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}

