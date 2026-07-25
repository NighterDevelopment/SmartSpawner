package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;

/**
 * Keeps {@code config.yml} in sync with the bundled defaults. Version-less: on every startup it applies
 * key renames / value migrations and tops up any missing keys, preserving the user's own values and
 * comments. See {@link YamlMigrator} and {@link ConfigMigrations}.
 */
public class ConfigUpdater {
    private static final String FILE_NAME = "config.yml";
    private final SmartSpawner plugin;

    public ConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void checkAndUpdateConfig() {
        File configFile = new File(plugin.getDataFolder(), FILE_NAME);
        YamlMigrator.migrate(
                configFile,
                plugin.getResource(FILE_NAME),
                ConfigMigrations.CONFIG,
                ConfigMigrations.CONFIG_VALUES,
                plugin.getLogger());

        // Reload Bukkit's cached config after any write
        plugin.reloadConfig();
    }
}
