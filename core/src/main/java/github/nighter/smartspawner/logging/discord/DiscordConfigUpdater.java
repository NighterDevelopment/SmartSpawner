package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.YamlMigrator;

import java.io.File;
import java.util.List;

/**
 * Ensures {@code discord_logging.yml} exists and is up-to-date. Version-less: delegates to
 * {@link YamlMigrator}, which creates the file if missing and tops up any new keys on startup.
 */
public class DiscordConfigUpdater {

    private static final String FILE_NAME = "discord_logging.yml";

    private final SmartSpawner plugin;

    public DiscordConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /** Call this before {@link DiscordWebhookConfig} tries to load the file. */
    public void checkAndUpdate() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        YamlMigrator.migrate(file, plugin.getResource(FILE_NAME), List.of(), plugin.getLogger());
    }
}
