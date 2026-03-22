package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.ConfigVersionService;

import java.io.File;

/**
 * Ensures {@code discord_logging.yml} exists and is up-to-date.
 * Delegates version checking / backup / merge to {@link ConfigVersionService}.
 */
public class DiscordConfigUpdater {

    private static final String FILE_NAME   = "discord_logging.yml";
    private static final String VERSION_KEY = "config_version";

    private final SmartSpawner plugin;

    public DiscordConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /** Call this before {@link DiscordWebhookConfig} tries to load the file. */
    public void checkAndUpdate() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        ConfigVersionService.updateFile(plugin, file, FILE_NAME, VERSION_KEY);
    }
}
