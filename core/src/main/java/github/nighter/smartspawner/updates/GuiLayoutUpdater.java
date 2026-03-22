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
    private static final String GUI_LAYOUT_VERSION_KEY = "gui_layout_version";
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String[] LAYOUT_FILES = {"storage_gui.yml", "main_gui.yml", "sell_confirm_gui.yml"};
    private static final String[] LAYOUT_NAMES = {"default", "DonutSMP"};


    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Check if GUI layouts need to be updated and update them if necessary
     */
    public void checkAndUpdateLayouts() {
        File layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        
        // Ensure layouts directory exists
        if (!layoutsDir.exists()) {
            layoutsDir.mkdirs();
        }

        // Check and update each layout
        for (String layoutName : LAYOUT_NAMES) {
            File layoutDir = new File(layoutsDir, layoutName);
            if (!layoutDir.exists()) {
                layoutDir.mkdirs();
            }

            // Check and update each layout file
            for (String fileName : LAYOUT_FILES) {
                checkAndUpdateLayoutFile(layoutDir, layoutName, fileName);
            }
        }
    }

    /**
     * Check and update a specific layout file
     */
    private void checkAndUpdateLayoutFile(File layoutDir, String layoutName, String fileName) {
        File layoutFile = new File(layoutDir, fileName);

        // If layout file doesn't exist, create it with the version header
        if (!layoutFile.exists()) {
            createDefaultLayoutWithHeader(layoutDir, layoutName, fileName);
            return;
        }

        FileConfiguration currentLayout = YamlConfiguration.loadConfiguration(layoutFile);
        String layoutVersionStr = currentLayout.getString(GUI_LAYOUT_VERSION_KEY, "0.0.0");
        Version layoutVersion = new Version(layoutVersionStr);
        Version pluginVersion = new Version(currentVersion);

        if (layoutVersion.compareTo(pluginVersion) >= 0) {
            return;
        }

        boolean isNewFile = layoutVersionStr.equals("0.0.0");

        if (!isNewFile) {
            plugin.getLogger().info("Updating GUI layout " + layoutName + "/" + fileName + " from version " + layoutVersionStr + " to " + currentVersion);
        }

        try {
            Map<String, Object> userValues = flattenConfig(currentLayout);

            // Create temp file with new default layout
            File tempFile = new File(layoutDir, fileName + ".new");
            createDefaultLayoutWithHeader(layoutDir, layoutName, fileName, tempFile);

            FileConfiguration newLayout = YamlConfiguration.loadConfiguration(tempFile);
            newLayout.set(GUI_LAYOUT_VERSION_KEY, currentVersion);

            // Only check for differences and create backups if not a new file
            if (!isNewFile) {
                // Check if there are actual differences before creating backup
                boolean layoutDiffers = hasLayoutDifferences(userValues, newLayout);

                if (layoutDiffers) {
                    File backupFile = new File(layoutDir, fileName.replace(".yml", "_backup_" + layoutVersionStr + ".yml"));
                    Files.copy(layoutFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("GUI layout backup created at " + layoutName + "/" + backupFile.getName());
                } else {
                    plugin.debug("No significant GUI layout changes detected for " + layoutName + "/" + fileName + ", skipping backup creation");
                }
            }

            // Apply user values and save
            applyUserValues(newLayout, userValues);
            newLayout.save(layoutFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update GUI layout " + layoutName + "/" + fileName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determines if there are actual differences between old and new layout
     */
    private boolean hasLayoutDifferences(Map<String, Object> userValues, FileConfiguration newLayout) {
        // Get all paths from new layout (excluding gui_layout_version)
        Map<String, Object> newLayoutMap = flattenConfig(newLayout);

        // Check for removed or changed keys
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object oldValue = entry.getValue();

            // Skip gui_layout_version key
            if (path.equals(GUI_LAYOUT_VERSION_KEY)) continue;

            // Check if path no longer exists
            if (!newLayout.contains(path)) {
                return true; // Found a removed path
            }

            // Check if default value changed
            Object newDefaultValue = newLayout.get(path);
            if (newDefaultValue != null && !newDefaultValue.equals(oldValue)) {
                return true; // Default value changed
            }
        }

        // Check for new keys
        for (String path : newLayoutMap.keySet()) {
            if (!path.equals(GUI_LAYOUT_VERSION_KEY) && !userValues.containsKey(path)) {
                return true; // Found a new path
            }
        }

        return false; // No significant differences
    }

    /**
     * Create a default layout file with version header
     */
    private void createDefaultLayoutWithHeader(File layoutDir, String layoutName, String fileName) {
        createDefaultLayoutWithHeader(layoutDir, layoutName, fileName, new File(layoutDir, fileName));
    }

    /**
     * Create a default layout file with version header at specific destination
     */
    private void createDefaultLayoutWithHeader(File layoutDir, String layoutName, String fileName, File destinationFile) {
        try {
            // Ensure parent directory exists
            File parentDir = destinationFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String resourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + fileName;
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .toList();

                    List<String> newLines = new ArrayList<>();
                    newLines.add("# GUI Layout version - Do not modify this value");
                    newLines.add(GUI_LAYOUT_VERSION_KEY + ": " + currentVersion);
                    newLines.add("");
                    newLines.addAll(defaultLines);

                    Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
                } else {
                    plugin.getLogger().warning("Default GUI layout " + resourcePath + " not found in the plugin's resources.");
                    destinationFile.createNewFile();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default GUI layout with header for " + layoutName + "/" + fileName + ": " + e.getMessage());
            e.printStackTrace();
        }
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
     * Applies the user values to the new layout
     */
    private void applyUserValues(FileConfiguration newLayout, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            // Don't override gui_layout_version
            if (path.equals(GUI_LAYOUT_VERSION_KEY)) continue;

            if (newLayout.contains(path)) {
                newLayout.set(path, value);
            } else {
                plugin.getLogger().warning("GUI layout path '" + path + "' from old layout no longer exists in new layout");
            }
        }
    }
}