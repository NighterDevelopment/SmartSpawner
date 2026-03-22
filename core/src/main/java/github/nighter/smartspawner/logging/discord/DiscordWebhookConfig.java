package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Global Discord webhook settings loaded from {@code discord_logging.yml}.
 *
 * <p>This file merges the former {@code discord.yml} (connection settings) and
 * {@code discord/event_defaults.yml} (embed templates) into a single source of truth.
 * Embed appearance per event is still extracted to
 * {@code discord/events/<EVENT>.yml} and managed by {@link DiscordEmbedConfigManager}.</p>
 */
public class DiscordWebhookConfig {
    private static final String FILE_NAME = "discord_logging.yml";

    private final SmartSpawner plugin;

    @Getter private boolean enabled;
    @Getter private String  webhookUrl;
    @Getter private boolean showPlayerHead;
    @Getter private boolean logAllEvents;
    @Getter private Set<SpawnerEventType> enabledEvents;

    public DiscordWebhookConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        new DiscordConfigUpdater(plugin).checkAndUpdate();
        loadConfig();
    }

    public void loadConfig() {
        File discordFile = new File(plugin.getDataFolder(), FILE_NAME);
        if (!discordFile.exists()) {
            this.enabled = false;
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(discordFile);

        this.enabled        = cfg.getBoolean("enabled", false);
        this.webhookUrl     = cfg.getString("webhook_url", "");
        this.showPlayerHead = cfg.getBoolean("show_player_head", true);
        this.logAllEvents   = cfg.getBoolean("log_all_events", false);
        this.enabledEvents  = parseEnabledEvents(cfg);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Set<SpawnerEventType> parseEnabledEvents(FileConfiguration cfg) {
        if (logAllEvents) return EnumSet.allOf(SpawnerEventType.class);

        List<String> list = cfg.getStringList("logged_events");
        if (list.isEmpty()) {
            Set<SpawnerEventType> defaults = EnumSet.noneOf(SpawnerEventType.class);
            defaults.add(SpawnerEventType.SPAWNER_PLACE);
            defaults.add(SpawnerEventType.SPAWNER_BREAK);
            defaults.add(SpawnerEventType.SPAWNER_EXPLODE);
            defaults.add(SpawnerEventType.SPAWNER_STACK_HAND);
            defaults.add(SpawnerEventType.SPAWNER_STACK_GUI);
            defaults.add(SpawnerEventType.SPAWNER_DESTACK_GUI);
            defaults.add(SpawnerEventType.COMMAND_EXECUTE_PLAYER);
            defaults.add(SpawnerEventType.COMMAND_EXECUTE_CONSOLE);
            defaults.add(SpawnerEventType.COMMAND_EXECUTE_RCON);
            return defaults;
        }

        Set<SpawnerEventType> events = EnumSet.noneOf(SpawnerEventType.class);
        for (String name : list) {
            try {
                events.add(SpawnerEventType.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("discord_logging.yml: unknown event type '" + name + "', skipping.");
            }
        }
        return events;
    }

    public boolean isEventEnabled(SpawnerEventType eventType) {
        return enabled && enabledEvents.contains(eventType);
    }

    // ── Inner type (shared with embed config) ─────────────────────────────────

    public static class EmbedField {
        @Getter private final String  name;
        @Getter private final String  value;
        @Getter private final boolean inline;

        public EmbedField(String name, String value, boolean inline) {
            this.name   = name;
            this.value  = value;
            this.inline = inline;
        }
    }
}
