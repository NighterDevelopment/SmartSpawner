package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerLogEntry;
import org.bukkit.Location;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds Discord webhook payloads from log entries.
 *
 * <p>Supports two modes controlled by {@link DiscordWebhookConfig#isJsonEmbedFormat()}:
 * <ul>
 *   <li><b>yaml</b> – Programmatic embed constructed from the structured config keys.</li>
 *   <li><b>json</b> – The raw {@code embed_json} template string with placeholder substitution.</li>
 * </ul>
 */
public class DiscordEmbedBuilder {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Returns the full Discord webhook JSON payload string for the given log entry.
     * Routes to the JSON-template path or the programmatic-embed path based on config.
     */
    public static String buildWebhookPayload(SpawnerLogEntry entry,
                                             DiscordWebhookConfig config,
                                             SmartSpawner plugin) {
        Map<String, String> placeholders = buildPlaceholders(entry, config);

        if (config.isJsonEmbedFormat()) {
            return buildFromJsonTemplate(config.getEmbedJsonTemplate(), placeholders);
        }

        return buildEmbed(entry, config, plugin, placeholders).toJson();
    }

    // -------------------------------------------------------------------------
    // JSON-template path
    // -------------------------------------------------------------------------

    /**
     * Replaces all {@code {key}} placeholders in the template and returns the
     * resulting string ready to POST to Discord.
     */
    private static String buildFromJsonTemplate(String template,
                                                Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            // Fallback: minimal valid payload
            return "{\"content\":\"SmartSpawner event – no embed_json template configured.\"}";
        }
        String result = template;
        for (Map.Entry<String, String> ph : placeholders.entrySet()) {
            result = result.replace("{" + ph.getKey() + "}", ph.getValue());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Programmatic (YAML) embed path – kept for backward-compatibility
    // -------------------------------------------------------------------------

    /** @deprecated Use {@link #buildWebhookPayload} instead. */
    public static DiscordEmbed buildEmbed(SpawnerLogEntry entry,
                                          DiscordWebhookConfig config,
                                          SmartSpawner plugin) {
        return buildEmbed(entry, config, plugin, buildPlaceholders(entry, config));
    }

    private static DiscordEmbed buildEmbed(SpawnerLogEntry entry,
                                           DiscordWebhookConfig config,
                                           SmartSpawner plugin,
                                           Map<String, String> placeholders) {
        DiscordEmbed embed = new DiscordEmbed();

        embed.setColor(config.getColorForEvent(entry.getEventType()));

        String eventIcon = getEventIcon(entry.getEventType());
        embed.setTitle(eventIcon + " " + replacePlaceholders(config.getEmbedTitle(), placeholders));
        embed.setDescription(buildCompactDescription(entry, placeholders, config));
        embed.setFooter(
                replacePlaceholders(config.getEmbedFooter(), placeholders),
                "https://images.minecraft-heads.com/render2d/head/2e/2eaa2d8b7e9a098ebd33fcb6cf1120f4.webp");
        embed.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis()));

        if (config.isShowPlayerHead() && entry.getPlayerName() != null) {
            embed.setThumbnail(getPlayerAvatarUrl(entry.getPlayerName()));
        }

        addCompactFields(embed, entry);

        for (DiscordWebhookConfig.EmbedField custom : config.getCustomFields()) {
            embed.addField(
                    replacePlaceholders(custom.getName(), placeholders),
                    replacePlaceholders(custom.getValue(), placeholders),
                    custom.isInline());
        }

        return embed;
    }

    private static String buildCompactDescription(SpawnerLogEntry entry, Map<String, String> placeholders, DiscordWebhookConfig config) {
        StringBuilder desc = new StringBuilder();

        // Main description
        String mainDesc = replacePlaceholders(config.getEmbedDescription(), placeholders);
        desc.append(mainDesc);
        desc.append("\n\n");

        // Player info (if exists)
        if (entry.getPlayerName() != null) {
            desc.append("👤 `").append(entry.getPlayerName()).append("`");
        }

        // Location info (compact format)
        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            if (entry.getPlayerName() != null) desc.append(" • ");
            desc.append("📍 `").append(loc.getWorld().getName())
                    .append(" (").append(loc.getBlockX())
                    .append(", ").append(loc.getBlockY())
                    .append(", ").append(loc.getBlockZ()).append(")`");
        }

        // Entity type (if exists)
        if (entry.getEntityType() != null) {
            desc.append("\n🐾 `").append(formatEntityName(entry.getEntityType().name())).append("`");
        }

        return desc.toString();
    }

    private static void addCompactFields(DiscordEmbed embed, SpawnerLogEntry entry) {
        Map<String, Object> metadata = entry.getMetadata();

        if (metadata.isEmpty()) {
            return;
        }

        // Only add important metadata (max 6 fields for compact look)
        int fieldCount = 0;
        int maxFields = 6;

        for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
            if (fieldCount >= maxFields) break;

            String key = formatFieldName(metaEntry.getKey());
            String icon = getFieldIcon(metaEntry.getKey());
            Object value = metaEntry.getValue();

            String formattedValue = formatCompactValue(value);
            embed.addField(icon + " " + key, formattedValue, true);
            fieldCount++;
        }
    }

    private static String formatCompactValue(Object value) {
        if (value == null) return "`N/A`";

        if (value instanceof Number) {
            Number num = (Number) value;
            if (value instanceof Double || value instanceof Float) {
                return "`" + String.format("%.2f", num.doubleValue()) + "`";
            }
            return "`" + num.toString() + "`";
        }

        String str = String.valueOf(value);
        if (str.length() > 50) {
            str = str.substring(0, 47) + "...";
        }
        return "`" + str + "`";
    }

    private static Map<String, String> buildPlaceholders(SpawnerLogEntry entry,
                                                          DiscordWebhookConfig config) {
        Map<String, String> placeholders = new HashMap<>();

        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        placeholders.put("description", entry.getEventType().getDescription());
        placeholders.put("event_type",  entry.getEventType().name());
        placeholders.put("time",        TIME_FMT.format(now));
        placeholders.put("timestamp",   ISO_FMT.format(now));
        placeholders.put("color",       String.valueOf(config.getColorForEvent(entry.getEventType())));

        if (entry.getPlayerName() != null) {
            placeholders.put("player", entry.getPlayerName());
        } else {
            placeholders.put("player", "N/A");
        }

        if (entry.getPlayerUuid() != null) {
            placeholders.put("player_uuid", entry.getPlayerUuid().toString());
        }

        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            placeholders.put("world",    loc.getWorld().getName());
            placeholders.put("x",        String.valueOf(loc.getBlockX()));
            placeholders.put("y",        String.valueOf(loc.getBlockY()));
            placeholders.put("z",        String.valueOf(loc.getBlockZ()));
            placeholders.put("location", String.format("%s (%d, %d, %d)",
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }

        if (entry.getEntityType() != null) {
            placeholders.put("entity", formatEntityName(entry.getEntityType().name()));
        }

        // Expose all metadata keys as placeholders
        for (Map.Entry<String, Object> meta : entry.getMetadata().entrySet()) {
            placeholders.put(meta.getKey(), String.valueOf(meta.getValue()));
        }

        return placeholders;
    }

    private static String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static String getPlayerAvatarUrl(String playerName) {
        return "https://mc-heads.net/avatar/" + playerName + "/64.png";
    }

    private static String formatFieldName(String fieldName) {
        String[] words = fieldName.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    private static String formatEntityName(String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            return entityType;
        }
        String[] words = entityType.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    private static String getFieldIcon(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains("command")) return "⚙️";
        if (lower.contains("amount") || lower.contains("count")) return "🔢";
        if (lower.contains("quantity")) return "📊";
        if (lower.contains("price") || lower.contains("cost") || lower.contains("money")) return "💰";
        if (lower.contains("exp") || lower.contains("experience")) return "✨";
        if (lower.contains("stack")) return "📚";
        if (lower.contains("type")) return "🏷️";
        return "•";
    }

    private static String getEventIcon(github.nighter.smartspawner.logging.SpawnerEventType eventType) {
        String eventName = eventType.name();

        // Command events
        if (eventName.startsWith("COMMAND_")) {
            if (eventName.contains("PLAYER")) return "👤";
            if (eventName.contains("CONSOLE")) return "🖥️";
            if (eventName.contains("RCON")) return "🔌";
            return "⚙️";
        }

        // Spawner events
        if (eventName.equals("SPAWNER_PLACE")) return "✅";
        if (eventName.equals("SPAWNER_BREAK")) return "❌";
        if (eventName.equals("SPAWNER_EXPLODE")) return "💥";

        // Stack events
        if (eventName.contains("STACK_HAND")) return "✋";
        if (eventName.contains("STACK_GUI")) return "📦";
        if (eventName.contains("DESTACK")) return "📤";

        // GUI events
        if (eventName.contains("GUI_OPEN")) return "📋";
        if (eventName.contains("STORAGE_OPEN")) return "📦";
        if (eventName.contains("STACKER_OPEN")) return "🔢";

        // Action events
        if (eventName.contains("EXP_CLAIM")) return "✨";
        if (eventName.contains("SELL_ALL")) return "💰";
        if (eventName.contains("ITEM_TAKE_ALL")) return "🎒";
        if (eventName.contains("ITEM_DROP")) return "🗑️";
        if (eventName.contains("ITEMS_SORT")) return "🔃";
        if (eventName.contains("ITEM_FILTER")) return "🔍";
        if (eventName.contains("DROP_PAGE_ITEMS")) return "📄";
        if (eventName.contains("EGG_CHANGE")) return "🥚";

        return "📌";
    }
}