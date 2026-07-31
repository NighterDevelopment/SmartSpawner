package github.nighter.smartspawner.logging.discord;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Immutable embed configuration for a single Discord event type.
 * Loaded from one {@code embeds} entry of {@code activity_log.yml} in the plugin data folder.
 */
@Getter
public final class DiscordEventEmbedConfig {

    private final String title;
    private final String description;
    private final int    color;
    private final String footer;
    private final List<DiscordWebhookConfig.EmbedField> fields;

    private DiscordEventEmbedConfig(
            String title,
            String description,
            int color,
            String footer,
            List<DiscordWebhookConfig.EmbedField> fields
    ) {
        this.title       = title;
        this.description = description;
        this.color       = color;
        this.footer      = footer;
        this.fields      = fields;
    }

    public static DiscordEventEmbedConfig fromSection(ConfigurationSection cfg) {
        String title  = cfg.getString("title",       "{description}");
        String desc   = cfg.getString("description", "{description}");
        String footer = cfg.getString("footer",      "SmartSpawner • {time}");
        int    color  = parseColor(cfg.getString("color", "99AAB5"));

        List<DiscordWebhookConfig.EmbedField> fields = new ArrayList<>();
        for (Map<?, ?> fm : cfg.getMapList("fields")) {
            String name    = (String) fm.get("name");
            String value   = (String) fm.get("value");
            boolean inline = Boolean.TRUE.equals(fm.get("inline"));
            if (name != null && value != null) {
                fields.add(new DiscordWebhookConfig.EmbedField(name, value, inline));
            }
        }

        return new DiscordEventEmbedConfig(title, desc, color, footer, fields);
    }

    public static DiscordEventEmbedConfig defaults() {
        return new DiscordEventEmbedConfig(
                "{event_type}",
                "{description}",
                0x5865F2,
                "SmartSpawner • {time}",
                List.of()
        );
    }

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
