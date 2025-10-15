package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing logging messages and Discord embed configurations from language files.
 */
public class LoggingMessageService {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    public LoggingMessageService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * Get localized description for an event type.
     * Falls back to hardcoded description if not found in language files.
     */
    public String getEventDescription(SpawnerEventType eventType) {
        String key = "logging.events." + eventType.name();
        
        try {
            // Try to get from language manager's default locale
            ConfigurationSection messages = languageManager.getCachedDefaultLocaleData().messages();
            String description = messages.getString(key);
            
            if (description != null && !description.isEmpty()) {
                return description;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load logging description for " + eventType.name() + ": " + e.getMessage());
        }
        
        // Fallback to hardcoded description
        return eventType.getDescription();
    }

    /**
     * Get Discord embed configuration for an event type from language files.
     */
    public DiscordEmbedConfig getDiscordEmbedConfig(SpawnerEventType eventType) {
        String key = "logging.discord_embeds." + eventType.name();
        
        try {
            ConfigurationSection messages = languageManager.getCachedDefaultLocaleData().messages();
            ConfigurationSection embedSection = messages.getConfigurationSection(key);
            
            if (embedSection != null) {
                String title = embedSection.getString("title", "{description}");
                String description = embedSection.getString("description", "{description}");
                String colorHex = embedSection.getString("color", "5865F2");
                
                // Parse fields if they exist
                Map<String, DiscordEmbedField> fields = new HashMap<>();
                ConfigurationSection fieldsSection = embedSection.getConfigurationSection("fields");
                if (fieldsSection != null) {
                    for (String fieldKey : fieldsSection.getKeys(false)) {
                        ConfigurationSection fieldSection = fieldsSection.getConfigurationSection(fieldKey);
                        if (fieldSection != null) {
                            String fieldName = fieldSection.getString("name", "");
                            String fieldValue = fieldSection.getString("value", "");
                            boolean inline = fieldSection.getBoolean("inline", false);
                            fields.put(fieldKey, new DiscordEmbedField(fieldName, fieldValue, inline));
                        }
                    }
                }
                
                return new DiscordEmbedConfig(title, description, parseColor(colorHex), fields);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load Discord embed config for " + eventType.name() + ": " + e.getMessage());
        }
        
        // Return default config
        return new DiscordEmbedConfig("{description}", "{description}", 0x5865F2, new HashMap<>());
    }

    private int parseColor(String colorHex) {
        try {
            // Remove # if present
            if (colorHex.startsWith("#")) {
                colorHex = colorHex.substring(1);
            }
            return Integer.parseInt(colorHex, 16);
        } catch (NumberFormatException e) {
            return 0x5865F2; // Default Discord blurple
        }
    }

    /**
     * Container for Discord embed configuration from language files.
     */
    public static class DiscordEmbedConfig {
        private final String title;
        private final String description;
        private final int color;
        private final Map<String, DiscordEmbedField> fields;

        public DiscordEmbedConfig(String title, String description, int color, Map<String, DiscordEmbedField> fields) {
            this.title = title;
            this.description = description;
            this.color = color;
            this.fields = fields;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getColor() {
            return color;
        }

        public Map<String, DiscordEmbedField> getFields() {
            return fields;
        }
    }

    /**
     * Container for Discord embed field configuration.
     */
    public static class DiscordEmbedField {
        private final String name;
        private final String value;
        private final boolean inline;

        public DiscordEmbedField(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public boolean isInline() {
            return inline;
        }
    }
}
