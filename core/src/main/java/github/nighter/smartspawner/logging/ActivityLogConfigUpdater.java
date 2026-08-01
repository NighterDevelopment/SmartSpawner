package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.ConfigMigrations;
import github.nighter.smartspawner.updates.YamlMigrator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ensures {@code activity_log.yml} exists and is up-to-date. Version-less: delegates to
 * {@link YamlMigrator}, which creates the file if missing and tops up any new keys on startup.
 *
 * <p>Two moves happened in 1.8.0 and are replayed here for servers upgrading:
 * {@code discord_logging.yml} became this file, and the {@code logging} section of
 * {@code config.yml} became its {@code file} section. Both run before the migrator so the user's
 * own values land first and the top-up only fills in what is genuinely missing.</p>
 */
public class ActivityLogConfigUpdater {

    public static final String FILE_NAME = "activity_log.yml";

    private static final String LEGACY_FILE_NAME = "discord_logging.yml";
    private static final String LEGACY_CONFIG_SECTION = "logging";
    private static final String FILE_SECTION = "file";

    private final SmartSpawner plugin;

    public ActivityLogConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /** Call this before anything reads {@code activity_log.yml}. */
    public void checkAndUpdate() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (importLegacySources(plugin.getDataFolder(), plugin.getLogger())) {
            // config.yml was rewritten on disk, so Bukkit's cached copy is stale.
            plugin.reloadConfig();
        }

        boolean legacyLayout = isLegacyLayout(file);
        YamlMigrator.migrate(
                file,
                plugin.getResource(FILE_NAME),
                ConfigMigrations.ACTIVITY_LOG,
                ConfigMigrations.ACTIVITY_LOG_LAYOUT,
                plugin.getLogger());
        if (legacyLayout) {
            rebuildFromDefaults(file, plugin.getResource(FILE_NAME), plugin.getLogger());
        }
    }

    /**
     * Replays both 1.8.0 file moves. Takes the data folder rather than the plugin so it can be
     * exercised without a server.
     *
     * @return true when {@code config.yml} was rewritten and the caller must reload it
     */
    static boolean importLegacySources(File dataFolder, Logger log) {
        File file = new File(dataFolder, FILE_NAME);
        renameLegacyFile(dataFolder, file, log);
        return importFileLoggingFromConfig(dataFolder, file, log);
    }

    // Every key of this file moved in 1.8.0, so a file without a 'discord' section is a pre-1.8.0 one.
    static boolean isLegacyLayout(File file) {
        return file.exists() && !YamlConfiguration.loadConfiguration(file).contains("discord");
    }

    // discord_logging.yml -> activity_log.yml. The keys inside are moved by the migrator afterwards.
    private static void renameLegacyFile(File dataFolder, File file, Logger log) {
        if (file.exists()) return;

        File legacy = new File(dataFolder, LEGACY_FILE_NAME);
        if (!legacy.exists()) return;

        if (legacy.renameTo(file)) {
            log.info("Renamed " + LEGACY_FILE_NAME + " to " + FILE_NAME);
        } else {
            log.warning("Could not rename " + LEGACY_FILE_NAME + " to " + FILE_NAME
                    + ", the Discord settings in it will be ignored.");
        }
    }

    /**
     * Writes the user's values into a fresh copy of the bundled file, once, for a file that still had
     * the pre-1.8.0 layout.
     *
     * <p>The usual migrator contract is to leave the user's file alone and only add to it, which here
     * would leave every comment describing keys that have all moved, under a header naming a file that
     * no longer exists. Since the layout changed wholesale, the shipped documentation and ordering are
     * worth more than the old file's formatting. Values are still the user's, and a key they added
     * that the defaults do not have is carried over.</p>
     */
    static void rebuildFromDefaults(File file, InputStream resource, Logger log) {
        if (resource == null) return;

        YamlConfiguration rebuilt;
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            rebuilt = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not read the bundled " + FILE_NAME, e);
            return;
        }

        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        for (String path : user.getKeys(true)) {
            if (user.isConfigurationSection(path)) continue;
            rebuilt.set(path, user.get(path));
        }

        try {
            rebuilt.save(file);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to rewrite " + FILE_NAME, e);
        }
    }

    // config.yml 'logging' -> activity_log.yml 'file', then drops the section from config.yml.
    private static boolean importFileLoggingFromConfig(File dataFolder, File file, Logger log) {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) return false;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection logging = config.getConfigurationSection(LEGACY_CONFIG_SECTION);
        if (logging == null) return false;

        YamlConfiguration activityLog = YamlConfiguration.loadConfiguration(file);
        for (String key : logging.getKeys(true)) {
            if (logging.isConfigurationSection(key)) continue;
            String path = FILE_SECTION + "." + key;
            if (!activityLog.contains(path)) {
                activityLog.set(path, logging.get(key));
            }
        }

        try {
            activityLog.save(file);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to move the logging settings into " + FILE_NAME, e);
            return false;
        }

        config.set(LEGACY_CONFIG_SECTION, null);
        try {
            config.save(configFile);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to remove the old logging section from config.yml", e);
            return false;
        }
        log.info("Moved the logging settings from config.yml into " + FILE_NAME);
        return true;
    }
}
