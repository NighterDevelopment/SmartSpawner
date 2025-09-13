package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class GuiLayoutUpdater {
    private final String currentVersion;
    private final SmartSpawner plugin;
    private static final String GUI_VERSION_KEY = "gui_version";
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String STORAGE_GUI_FILE = "storage_gui.yml";
    private static final List<String> SUPPORTED_LAYOUTS = Arrays.asList("default", "DonutSMP");

    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Check and update all GUI layout files for all supported layouts
     */
    public void checkAndUpdateGuiLayouts() {
        for (String layout : SUPPORTED_LAYOUTS) {
            File layoutDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR + "/" + layout);

            // Create layout directory if it doesn't exist
            if (!layoutDir.exists()) {
                layoutDir.mkdirs();
            }

            // Check and update storage GUI file
            File storageGuiFile = new File(layoutDir, STORAGE_GUI_FILE);
            updateStorageGuiFile(layout, storageGuiFile);
        }
    }

    /**
     * Update a specific storage GUI file
     *
     * @param layout The layout name (e.g., "default", "DonutSMP")
     * @param storageGuiFile The file to update
     */
    private void updateStorageGuiFile(String layout, File storageGuiFile) {
        try {
            // Create parent directory if it doesn't exist
            if (!storageGuiFile.getParentFile().exists()) {
                storageGuiFile.getParentFile().mkdirs();
            }

            // Create the file if it doesn't exist
            if (!storageGuiFile.exists()) {
                createDefaultStorageGuiFileWithHeader(layout, storageGuiFile);
                plugin.getLogger().info("Created new storage_gui.yml for " + layout + " layout");
                return;
            }

            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(storageGuiFile);
            String configVersionStr = currentConfig.getString(GUI_VERSION_KEY, "0.0.0");
            Version configVersion = new Version(configVersionStr);
            Version pluginVersion = new Version(currentVersion);

            if (configVersion.compareTo(pluginVersion) >= 0) {
                return; // No update needed
            }

            if (!configVersionStr.equals("0.0.0")) {
                plugin.debug("Updating " + layout + " storage_gui.yml from version " + configVersionStr + " to " + currentVersion);
            }

            // Store user's current values
            Map<String, Object> userValues = flattenConfig(currentConfig);

            // Create temp file with new default config
            File tempFile = new File(plugin.getDataFolder(),
                    GUI_LAYOUTS_DIR + "/" + layout + "/storage_gui_new.yml");
            createDefaultStorageGuiFileWithHeader(layout, tempFile);

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(GUI_VERSION_KEY, currentVersion);

            // Check if there are actual differences before creating backup
            boolean configDiffers = hasConfigDifferences(userValues, newConfig);

            if (configDiffers) {
                File backupFile = new File(plugin.getDataFolder(),
                        GUI_LAYOUTS_DIR + "/" + layout + "/storage_gui_backup_" + configVersionStr + ".yml");
                Files.copy(storageGuiFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.debug(layout + " storage_gui.yml backup created at " + backupFile.getName());
            } else {
                if (!configVersionStr.equals("0.0.0")) {
                    plugin.debug("No significant changes detected in " + layout + " storage_gui.yml, skipping backup creation");
                }
            }

            // Apply user values and save
            applyUserValues(newConfig, userValues);
            newConfig.save(storageGuiFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update " + layout + " storage_gui.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a default storage GUI file with a version header
     */
    private void createDefaultStorageGuiFileWithHeader(String layout, File destinationFile) {
        try (InputStream in = plugin.getResource(GUI_LAYOUTS_DIR + "/" + layout + "/" + STORAGE_GUI_FILE)) {
            if (in != null) {
                List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines()
                        .toList();

                List<String> newLines = new ArrayList<>();
                newLines.add("# GUI Layout version - Do not modify this value");
                newLines.add(GUI_VERSION_KEY + ": " + currentVersion);
                newLines.add("");
                newLines.addAll(defaultLines);

                destinationFile.getParentFile().mkdirs();
                Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
            } else {
                plugin.getLogger().warning("Default storage_gui.yml for " + layout +
                        " layout not found in the plugin's resources.");

                // Create empty file with just version
                destinationFile.getParentFile().mkdirs();

                // Create basic YAML with just the version
                YamlConfiguration emptyConfig = new YamlConfiguration();
                emptyConfig.set(GUI_VERSION_KEY, currentVersion);
                emptyConfig.set("_note", "This is an empty storage_gui.yml created because no default was found in the plugin resources.");
                emptyConfig.save(destinationFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default storage_gui.yml for " + layout + " layout: " + e.getMessage());
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
            if (path.equals(GUI_VERSION_KEY)) continue;

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
            if (!path.equals(GUI_VERSION_KEY) && !userValues.containsKey(path)) {
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
            if (path.equals(GUI_VERSION_KEY)) continue;

            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().fine("Config path '" + path + "' from old config no longer exists in new config");
            }
        }
    }
}