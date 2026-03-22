package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class LanguageUpdater {
    private final String currentVersion;
    private final SmartSpawner plugin;
    private static final String LANGUAGE_VERSION_KEY = "language_version";
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("en_US", "vi_VN", "de_DE");

    // Track which file types to update
    private final Set<LanguageFileType> activeFileTypes = new HashSet<>();

    public LanguageUpdater(SmartSpawner plugin) {
        this(plugin, LanguageFileType.values());
    }

    public LanguageUpdater(SmartSpawner plugin, LanguageFileType... fileTypes) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        activeFileTypes.addAll(Arrays.asList(fileTypes));
        checkAndUpdateLanguageFiles();
    }

    @Getter
    public enum LanguageFileType {
        MESSAGES("messages.yml"),
        GUI("gui.yml"),
        FORMATTING("formatting.yml"),
        ITEMS("items.yml");

        private final String fileName;

        LanguageFileType(String fileName) {
            this.fileName = fileName;
        }
    }

    /**
     * Check and update all language files for all supported languages
     */
    public void checkAndUpdateLanguageFiles() {
        for (String language : SUPPORTED_LANGUAGES) {
            File langDir = new File(plugin.getDataFolder(), "language/" + language);

            // Create language directory if it doesn't exist
            if (!langDir.exists()) {
                langDir.mkdirs();
            }

            // Check and update each language file type
            for (LanguageFileType fileType : activeFileTypes) {
                File languageFile = new File(langDir, fileType.getFileName());
                updateLanguageFile(language, languageFile, fileType);
            }
        }
    }

    /**
     * Update a specific language file
     *
     * @param language     The language code (e.g., "en_US")
     * @param languageFile The file to update
     * @param fileType     The type of language file
     */
    private void updateLanguageFile(String language, File languageFile, LanguageFileType fileType) {
        try {
            // Create parent directory if it doesn't exist
            if (!languageFile.getParentFile().exists()) {
                languageFile.getParentFile().mkdirs();
            }

            // Create the file if it doesn't exist
            if (!languageFile.exists()) {
                createDefaultLanguageFileWithHeader(language, languageFile, fileType);
                plugin.getLogger().info("Created new " + fileType.getFileName() + " for " + language);
                return;
            }

            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(languageFile);
            String configVersionStr = currentConfig.getString(LANGUAGE_VERSION_KEY, "0.0.0");
            Version configVersion = new Version(configVersionStr);
            Version pluginVersion = new Version(currentVersion);

            if (configVersion.compareTo(pluginVersion) >= 0) {
                return; // No update needed
            }

            if (!configVersionStr.equals("0.0.0")) {
                plugin.debug("Updating " + language + " " + fileType.getFileName() +
                        " from version " + configVersionStr + " to " + currentVersion);
            }

            // Store user's current values
            Map<String, Object> userValues = flattenConfig(currentConfig);

            // Create temp file with new default config
            File tempFile = new File(plugin.getDataFolder(),
                    "language/" + language + "/" + fileType.getFileName().replace(".yml", "_new.yml"));
            createDefaultLanguageFileWithHeader(language, tempFile, fileType);

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(LANGUAGE_VERSION_KEY, currentVersion);

            // Check if there are actual differences before creating backup
            boolean configDiffers = hasConfigDifferences(userValues, newConfig);

            if (configDiffers) {
                File backupFile = new File(plugin.getDataFolder(),
                        "language/" + language + "/" + fileType.getFileName().replace(".yml", "_backup_" + configVersionStr + ".yml"));
                Files.copy(languageFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.debug(language + " " + fileType.getFileName() + " backup created at " + backupFile.getName());
            } else {
                if (!configVersionStr.equals("0.0.0")) {
                    plugin.debug("No significant changes detected in " + language + " " +
                            fileType.getFileName() + ", skipping backup creation");
                }
            }

            // Apply user values and save
            applyUserValues(newConfig, userValues);
            newConfig.save(languageFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update " + language + " " + fileType.getFileName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a default language file with a version header
     */
    private void createDefaultLanguageFileWithHeader(String language, File destinationFile, LanguageFileType fileType) {
        try (InputStream in = plugin.getResource("language/" + language + "/" + fileType.getFileName())) {
            if (in != null) {
                List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines()
                        .toList();

                List<String> newLines = new ArrayList<>();
                newLines.add("# Language file version - Do not modify this value");
                newLines.add(LANGUAGE_VERSION_KEY + ": " + currentVersion);
                newLines.add("");
                newLines.addAll(defaultLines);

                destinationFile.getParentFile().mkdirs();
                Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
            } else {
                plugin.getLogger().warning("Default " + fileType.getFileName() + " for " + language +
                        " not found in the plugin's resources.");

                // Create empty file with just version
                destinationFile.getParentFile().mkdirs();

                // Create basic YAML with just the version
                YamlConfiguration emptyConfig = new YamlConfiguration();
                emptyConfig.set(LANGUAGE_VERSION_KEY, currentVersion);
                emptyConfig.set("_note", "This is an empty " + fileType.getFileName() +
                        " created because no default was found in the plugin resources.");
                emptyConfig.save(destinationFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default language file " + fileType.getFileName() +
                    " for " + language + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determines if there are actual differences between old and new configs
     */
    private boolean hasConfigDifferences(Map<String, Object> userValues, FileConfiguration newConfig) {
        // Get all paths from new config (excluding version key)
        Map<String, Object> newConfigMap = flattenConfig(newConfig);

        // Check for removed or changed keys
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object oldValue = entry.getValue();

            // Skip version key
            if (path.equals(LANGUAGE_VERSION_KEY)) continue;

            // Check if path no longer exists
            if (!newConfig.contains(path)) {
                return true; // Found a removed path
            }

            // Check if default value changed
            Object newDefaultValue = newConfig.get(path);
            if (newDefaultValue != null && !newDefaultValue.equals(oldValue)) {
                return true; // Default value changed
            }
        }

        // Check for new keys
        for (String path : newConfigMap.keySet()) {
            if (!path.equals(LANGUAGE_VERSION_KEY) && !userValues.containsKey(path)) {
                return true; // Found a new path
            }
        }

        return false; // No significant differences
    }

    /**
     * Flattens a configuration section into a map of path -> value
     */
    private Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                result.put(key, config.get(key));
            }
        }
        return result;
    }

    /**
     * Applies the user values to the new config
     */
    private void applyUserValues(FileConfiguration newConfig, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            // Don't override version key
            if (path.equals(LANGUAGE_VERSION_KEY)) continue;

            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().fine("Config path '" + path + "' from old config no longer exists in new config");
            }
        }
    }
}
