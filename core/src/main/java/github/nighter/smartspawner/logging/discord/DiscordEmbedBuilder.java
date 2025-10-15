package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.LoggingMessageService;
import github.nighter.smartspawner.logging.SpawnerLogEntry;
import org.bukkit.Location;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds compact Discord embeds from log entries.
 */
public class DiscordEmbedBuilder {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static DiscordEmbed buildEmbed(SpawnerLogEntry entry, DiscordWebhookConfig config, SmartSpawner plugin, LoggingMessageService loggingMessageService) {
        DiscordEmbed embed = new DiscordEmbed();
        
        // Get logging message service for localized descriptions (use provided or create new)
        LoggingMessageService msgService = loggingMessageService != null ? loggingMessageService : new LoggingMessageService(plugin);
        String localizedDescription = msgService.getEventDescription(entry.getEventType());
        
        // Get embed configuration from language files
        LoggingMessageService.DiscordEmbedConfig embedConfig = msgService.getDiscordEmbedConfig(entry.getEventType());

        // Set color from language file or fallback to config
        int color = embedConfig.getColor();
        if (color == 0x5865F2) { // If it's the default, try config
            color = config.getColorForEvent(entry.getEventType());
        }
        embed.setColor(color);

        // Build placeholders
        Map<String, String> placeholders = buildPlaceholders(entry, localizedDescription);

        // Set compact title with icon - use language file config or fallback to config
        String eventIcon = getEventIcon(entry.getEventType());
        String title = eventIcon + " " + replacePlaceholders(embedConfig.getTitle(), placeholders);
        embed.setTitle(title);

        // Set compact description - use language file config
        String description = buildCompactDescription(entry, placeholders, embedConfig.getDescription());
        embed.setDescription(description);

        // Set footer
        String footer = replacePlaceholders(config.getEmbedFooter(), placeholders);
        embed.setFooter(footer, "https://images.minecraft-heads.com/render2d/head/2e/2eaa2d8b7e9a098ebd33fcb6cf1120f4.webp");

        // Set timestamp
        embed.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis()));

        // Add player thumbnail if enabled
        if (config.isShowPlayerHead() && entry.getPlayerName() != null) {
            embed.setThumbnail(getPlayerAvatarUrl(entry.getPlayerName()));
        }

        // Add only important metadata as inline fields
        addCompactFields(embed, entry);
        
        // Add custom fields from language file if any
        for (Map.Entry<String, LoggingMessageService.DiscordEmbedField> fieldEntry : embedConfig.getFields().entrySet()) {
            LoggingMessageService.DiscordEmbedField field = fieldEntry.getValue();
            String fieldName = replacePlaceholders(field.getName(), placeholders);
            String fieldValue = replacePlaceholders(field.getValue(), placeholders);
            embed.addField(fieldName, fieldValue, field.isInline());
        }

        // Add custom fields from config (if any)
        for (DiscordWebhookConfig.EmbedField customField : config.getCustomFields()) {
            String fieldName = replacePlaceholders(customField.getName(), placeholders);
            String fieldValue = replacePlaceholders(customField.getValue(), placeholders);
            embed.addField(fieldName, fieldValue, customField.isInline());
        }

        return embed;
    }

    private static String buildCompactDescription(SpawnerLogEntry entry, Map<String, String> placeholders, String descriptionTemplate) {
        StringBuilder desc = new StringBuilder();

        // Main description from template
        String mainDesc = replacePlaceholders(descriptionTemplate, placeholders);
        desc.append(mainDesc);

        // Player info (if exists)
        if (entry.getPlayerName() != null) {
            desc.append("\n👤 `").append(entry.getPlayerName()).append("`");
        }

        // Location info (compact format)
        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            if (entry.getPlayerName() != null) desc.append(" • ");
            else desc.append("\n");
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

    private static Map<String, String> buildPlaceholders(SpawnerLogEntry entry, String localizedDescription) {
        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("description", localizedDescription);
        placeholders.put("event_type", entry.getEventType().name());
        placeholders.put("time", FORMATTER.format(Instant.ofEpochMilli(System.currentTimeMillis())));

        if (entry.getPlayerName() != null) {
            placeholders.put("player", entry.getPlayerName());
        }

        if (entry.getPlayerUuid() != null) {
            placeholders.put("player_uuid", entry.getPlayerUuid().toString());
        }

        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            placeholders.put("world", loc.getWorld().getName());
            placeholders.put("x", String.valueOf(loc.getBlockX()));
            placeholders.put("y", String.valueOf(loc.getBlockY()));
            placeholders.put("z", String.valueOf(loc.getBlockZ()));
            placeholders.put("location", String.format("%s (%d, %d, %d)",
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }

        if (entry.getEntityType() != null) {
            placeholders.put("entity", formatEntityName(entry.getEntityType().name()));
        }

        // Add metadata as placeholders
        for (Map.Entry<String, Object> metaEntry : entry.getMetadata().entrySet()) {
            placeholders.put(metaEntry.getKey(), String.valueOf(metaEntry.getValue()));
        }

        return placeholders;
    }

    private static String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        // Replace {newline} with actual newline
        result = result.replace("{newline}", "\n");
        
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