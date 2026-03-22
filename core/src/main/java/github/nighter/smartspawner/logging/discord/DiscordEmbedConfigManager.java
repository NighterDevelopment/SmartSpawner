package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;

/**
 * Reads per-event Discord embed configurations directly from
 * {@code discord_logging.yml} in the plugin data folder.
 *
 * <p>Configs are loaded lazily on first use and cached in an {@link EnumMap}.</p>
 */
public class DiscordEmbedConfigManager {

    private static final String CONFIG_FILE = "discord_logging.yml";

    private final SmartSpawner plugin;

    private final EnumMap<SpawnerEventType, DiscordEventEmbedConfig> cache =
            new EnumMap<>(SpawnerEventType.class);

    public DiscordEmbedConfigManager(SmartSpawner plugin, DiscordWebhookConfig config) {
        this.plugin = plugin;
    }

    // ── Public API ───────────────────────────────────────────────────────────────────

    /**
     * Returns the {@link DiscordEventEmbedConfig} for the given event type.
     * The config is loaded on first call and cached for all subsequent calls.
     */
    public DiscordEventEmbedConfig getEmbedConfig(SpawnerEventType eventType) {
        return cache.computeIfAbsent(eventType, this::loadEventConfig);
    }

    /** Clears the cache so configs are reloaded from disk on next access. */
    public void invalidateCache() {
        cache.clear();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private DiscordEventEmbedConfig loadEventConfig(SpawnerEventType eventType) {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) return DiscordEventEmbedConfig.defaults();

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = cfg.getConfigurationSection(eventType.name());
        if (section == null) {
            plugin.debug("No embed config found for event: " + eventType.name());
            return DiscordEventEmbedConfig.defaults();
        }
        return DiscordEventEmbedConfig.fromSection(section);
    }
}

