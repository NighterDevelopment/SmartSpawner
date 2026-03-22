package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Maintains {@code language/CHANGELOG.yml} in the plugin data folder.
 *
 * <p>This file is purely informational – it is always overwritten from the
 * bundled resource so it always reflects the latest version's key changes.
 * Users with custom language folders should read it to find out which keys
 * they need to add after a plugin update.</p>
 */
public class LanguageChangelogUpdater {

    private static final String RESOURCE_PATH = "language/CHANGELOG.yml";
    private static final String DEST_PATH     = "language/CHANGELOG.yml";

    private final SmartSpawner plugin;

    public LanguageChangelogUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Copies the bundled changelog to the plugin data folder, always overwriting
     * the previous copy so users see the latest changes.
     */
    public void update() {
        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
            if (in == null) {
                plugin.getLogger().warning("language/CHANGELOG.yml not found in plugin JAR.");
                return;
            }

            File dest = new File(plugin.getDataFolder(), DEST_PATH);
            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();

            byte[] content = in.readAllBytes();
            Files.write(dest.toPath(), content);

            plugin.debug("Language CHANGELOG.yml updated.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update language/CHANGELOG.yml", e);
        }
    }
}

