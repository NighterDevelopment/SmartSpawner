package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Configuration for Discord webhook logging.
 * Loaded from the standalone {@code discord.yml} file in the plugin data folder.
 */
public class DiscordWebhookConfig {
    private static final String FILE_NAME = "discord.yml";

    private final SmartSpawner plugin;

    @Getter private boolean enabled;
    @Getter private String webhookUrl;
    @Getter private boolean showPlayerHead;
    @Getter private String embedTitle;
    @Getter private String embedDescription;
    @Getter private String embedFooter;
    @Getter private Map<String, Integer> eventColors;
    @Getter private List<EmbedField> customFields;
    @Getter private Set<SpawnerEventType> enabledEvents;
    @Getter private boolean logAllEvents;

    /**
     * Embed format: "yaml" (structured fields) or "json" (raw Discord payload template).
     */
    @Getter private String embedFormat;

    /**
     * Raw Discord webhook JSON payload template.
     * Active when {@link #embedFormat} is {@code "json"}.
     * Placeholders ({player}, {location}, {color}, {timestamp}, …) are replaced
     * at send time by {@link DiscordEmbedBuilder}.
     */
    @Getter private String embedJsonTemplate;

    public DiscordWebhookConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        // Ensure discord.yml exists and is up-to-date before loading
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

        this.enabled          = cfg.getBoolean("enabled", false);
        this.webhookUrl       = cfg.getString("webhook_url", "");
        this.showPlayerHead   = cfg.getBoolean("show_player_head", true);
        this.logAllEvents     = cfg.getBoolean("log_all_events", false);
        this.embedFormat      = cfg.getString("embed_format", "yaml").toLowerCase(Locale.ROOT);
        this.embedJsonTemplate = cfg.getString("embed_json", "");

        // Structured YAML embed settings
        this.embedTitle       = cfg.getString("embed.title", "{description}");
        this.embedDescription = cfg.getString("embed.description", "{description}");
        this.embedFooter      = cfg.getString("embed.footer", "SmartSpawner • {time}");

        // Per-event colours
        this.eventColors = new HashMap<>();
        ConfigurationSection colorsSection = cfg.getConfigurationSection("embed.colors");
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                String hex = colorsSection.getString(key, "99AAB5");
                this.eventColors.put(key.toUpperCase(), parseColor(hex));
            }
        }

        // Custom extra fields
        this.customFields = new ArrayList<>();
        List<Map<?, ?>> fieldsList = cfg.getMapList("embed.fields");
        for (Map<?, ?> fieldMap : fieldsList) {
            String name   = (String) fieldMap.get("name");
            String value  = (String) fieldMap.get("value");
            boolean inline = fieldMap.containsKey("inline") && Boolean.TRUE.equals(fieldMap.get("inline"));
            if (name != null && value != null) {
                customFields.add(new EmbedField(name, value, inline));
            }
        }

        this.enabledEvents = parseEnabledEvents(cfg);
    }

    // -------------------------------------------------------------------------

    private Set<SpawnerEventType> parseEnabledEvents(FileConfiguration cfg) {
        if (logAllEvents) {
            return EnumSet.allOf(SpawnerEventType.class);
        }

        List<String> eventList = cfg.getStringList("logged_events");
        if (eventList.isEmpty()) {
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
        for (String name : eventList) {
            try {
                events.add(SpawnerEventType.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("discord.yml: unknown event type '" + name + "', skipping.");
            }
        }
        return events;
    }

    private int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0x99AAB5; // fallback grey
        }
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    public boolean isEventEnabled(SpawnerEventType eventType) {
        return enabled && enabledEvents.contains(eventType);
    }

    public int getColorForEvent(SpawnerEventType eventType) {
        Integer color = eventColors.get(eventType.name());
        if (color != null) return color;
        return eventColors.getOrDefault("DEFAULT", 0x5865F2);
    }

    /** Returns {@code true} when the raw JSON template mode is active. */
    public boolean isJsonEmbedFormat() {
        return "json".equals(embedFormat);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    public static class EmbedField {
        @Getter private final String name;
        @Getter private final String value;
        @Getter private final boolean inline;

        public EmbedField(String name, String value, boolean inline) {
            this.name   = name;
            this.value  = value;
            this.inline = inline;
        }
    }
}
