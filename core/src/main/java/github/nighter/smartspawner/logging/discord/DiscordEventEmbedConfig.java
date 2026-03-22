package github.nighter.smartspawner.logging.discord;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable embed configuration for a single Discord event type.
 *
 * <p>Loaded from {@code plugins/SmartSpawner/discord/events/<EVENT_NAME>.yml}.
 * If the file or a key is absent the class falls back to sensible defaults.</p>
 */
@Getter
public final class DiscordEventEmbedConfig {

    // ── Fields ───────────────────────────────────────────────────────────────

    /** {@code yaml} or {@code json}. */
    private final String embedFormat;

    // YAML-mode fields
    private final String title;
    private final String description;
    private final int    color;
    private final String footer;
    private final List<DiscordWebhookConfig.EmbedField> fields;

    // JSON-mode field
    private final String embedJsonTemplate;

    // ── Constructor ──────────────────────────────────────────────────────────

    private DiscordEventEmbedConfig(
            String embedFormat,
            String title,
            String description,
            int color,
            String footer,
            List<DiscordWebhookConfig.EmbedField> fields,
            String embedJsonTemplate
    ) {
        this.embedFormat       = embedFormat;
        this.title             = title;
        this.description       = description;
        this.color             = color;
        this.footer            = footer;
        this.fields            = fields;
        this.embedJsonTemplate = embedJsonTemplate;
    }

    // ── Factories ────────────────────────────────────────────────────────────

    /**
     * Load embed config from a {@link File} on disk.
     * Returns a sensible default if the file cannot be read.
     */
    public static DiscordEventEmbedConfig fromFile(File file) {
        if (!file.exists()) return defaults();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        return fromSection(cfg);
    }

    /**
     * Load embed config from a {@link ConfigurationSection}
     * (used when extracting from {@code event_defaults.yml}).
     */
    public static DiscordEventEmbedConfig fromSection(ConfigurationSection cfg) {
        String format = cfg.getString("embed_format", "yaml").toLowerCase(Locale.ROOT);
        String jsonTpl = cfg.getString("embed_json", "");

        String title  = cfg.getString("embed.title",       "{description}");
        String desc   = cfg.getString("embed.description", "{description}");
        String footer = cfg.getString("embed.footer",      "SmartSpawner • {time}");
        int    color  = parseColor(cfg.getString("embed.color", "99AAB5"));

        List<DiscordWebhookConfig.EmbedField> fields = new ArrayList<>();
        for (Map<?, ?> fm : cfg.getMapList("embed.fields")) {
            String name   = (String) fm.get("name");
            String value  = (String) fm.get("value");
            boolean inline = Boolean.TRUE.equals(fm.get("inline"));
            if (name != null && value != null) {
                fields.add(new DiscordWebhookConfig.EmbedField(name, value, inline));
            }
        }

        return new DiscordEventEmbedConfig(format, title, desc, color, footer, fields, jsonTpl);
    }

    /** Minimal hard-coded fallback used when no file is available. */
    public static DiscordEventEmbedConfig defaults() {
        return new DiscordEventEmbedConfig(
                "yaml",
                "{event_type}",
                "{description}",
                0x5865F2,
                "SmartSpawner • {time}",
                List.of(),
                ""
        );
    }

    // ── Query helpers ────────────────────────────────────────────────────────

    /** Returns {@code true} when the raw JSON template mode is active. */
    public boolean isJsonFormat() {
        return "json".equals(embedFormat);
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private static int parseColor(String hex) {
        if (hex == null) return 0x5865F2;
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0x5865F2;
        }
    }
}

