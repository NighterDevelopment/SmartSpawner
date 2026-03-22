package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.ConfigVersionService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Ensures {@code discord_logging.yml} exists and is up-to-date.
 * Delegates version checking / backup / merge to {@link ConfigVersionService}.
 *
 * <p>Legacy migration: if the server still has a {@code discord.yml} from a
 * previous version, its user settings (enabled, webhook_url, show_player_head,
 * log_all_events, logged_events) are copied into the new file before the old
 * file is renamed to {@code discord.yml.backup}.</p>
 */
public class DiscordConfigUpdater {

    private static final String FILE_NAME   = "discord_logging.yml";
    private static final String LEGACY_NAME = "discord.yml";
    private static final String VERSION_KEY = "config_version";

    private final SmartSpawner plugin;

    public DiscordConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /** Call this before {@link DiscordWebhookConfig} tries to load the file. */
    public void checkAndUpdate() {
        File newFile    = new File(plugin.getDataFolder(), FILE_NAME);
        File legacyFile = new File(plugin.getDataFolder(), LEGACY_NAME);

        // ── Migrate from legacy discord.yml if it exists and new file doesn't ──
        if (!newFile.exists() && legacyFile.exists()) {
            migrateFromLegacy(legacyFile, newFile);
            return; // createFromDefaults + legacy merge already written
        }

        ConfigVersionService.updateFile(plugin, newFile, FILE_NAME, VERSION_KEY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates {@code discord_logging.yml} from bundled defaults, then overlays
     * user values from the legacy {@code discord.yml}, and renames the old
     * file.
     */
    private void migrateFromLegacy(File legacyFile, File newFile) {
        plugin.getLogger().info("[Discord] Migrating " + LEGACY_NAME + " → " + FILE_NAME + " …");
        try {
            // 1. Write fresh defaults
            ConfigVersionService.updateFile(plugin, newFile, FILE_NAME, VERSION_KEY);

            // 2. Overlay user values from legacy file
            FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
            FileConfiguration fresh  = YamlConfiguration.loadConfiguration(newFile);

            for (String key : new String[]{"enabled", "webhook_url", "show_player_head",
                                           "log_all_events"}) {
                if (legacy.contains(key)) fresh.set(key, legacy.get(key));
            }
            if (legacy.contains("logged_events")) {
                fresh.set("logged_events", legacy.get("logged_events"));
            }
            fresh.save(newFile);

            // 3. Rename legacy file so it won't trigger migration again
            File backup = new File(plugin.getDataFolder(), LEGACY_NAME + ".backup");
            if (legacyFile.renameTo(backup)) {
                plugin.getLogger().info("[Discord] Old " + LEGACY_NAME
                        + " renamed to " + LEGACY_NAME + ".backup");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Discord] Migration failed: " + e.getMessage()
                    + " – a fresh " + FILE_NAME + " has been created instead.");
        }
    }
}
