package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.Version;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Manages creation and version-based migration of discord.yml.
 * Follows the same pattern as ConfigUpdater so user customisations are
 * preserved across plugin updates while new default keys are added.
 */
public class DiscordConfigUpdater {

    private static final String FILE_NAME = "discord.yml";
    private static final String VERSION_KEY = "config_version";

    private final SmartSpawner plugin;
    private final String currentVersion;

    public DiscordConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Ensures discord.yml exists and is up-to-date.
     * Call this before {@link DiscordWebhookConfig} attempts to load the file.
     */
    public void checkAndUpdate() {
        File discordFile = new File(plugin.getDataFolder(), FILE_NAME);

        if (!discordFile.exists()) {
            createDefaultWithHeader(discordFile);
            return;
        }

        FileConfiguration current = YamlConfiguration.loadConfiguration(discordFile);
        String fileVersionStr = current.getString(VERSION_KEY, "0.0.0");
        Version fileVersion = new Version(fileVersionStr);
        Version pluginVersion = new Version(currentVersion);

        if (fileVersion.compareTo(pluginVersion) >= 0) {
            return; // Already up-to-date
        }

        plugin.getLogger().info("Updating discord.yml from version " + fileVersionStr + " to " + currentVersion);

        try {
            Map<String, Object> userValues = flattenConfig(current);

            // Write new defaults to a temp file
            File tempFile = new File(plugin.getDataFolder(), "discord_new.yml");
            createDefaultWithHeader(tempFile);

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(VERSION_KEY, currentVersion);

            if (hasConfigDifferences(userValues, newConfig)) {
                File backup = new File(plugin.getDataFolder(), "discord_backup_" + fileVersionStr + ".yml");
                Files.copy(discordFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("discord.yml backup created: " + backup.getName());
            }

            applyUserValues(newConfig, userValues);
            newConfig.save(discordFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update discord.yml: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers (same pattern as ConfigUpdater)
    // -------------------------------------------------------------------------

    private void createDefaultWithHeader(File dest) {
        try {
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (InputStream in = plugin.getResource(FILE_NAME)) {
                if (in == null) {
                    plugin.getLogger().warning("discord.yml not found in plugin resources.");
                    dest.createNewFile();
                    return;
                }
                List<String> defaultLines = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))
                        .lines()
                        .toList();

                List<String> lines = new ArrayList<>();
                lines.add("# Configuration version - do not modify this value");
                lines.add(VERSION_KEY + ": " + currentVersion);
                lines.add("");
                lines.addAll(defaultLines);

                Files.write(dest.toPath(), lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create discord.yml: " + e.getMessage());
        }
    }

    private boolean hasConfigDifferences(Map<String, Object> userValues, FileConfiguration newConfig) {
        Map<String, Object> newMap = flattenConfig(newConfig);

        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            if (path.equals(VERSION_KEY)) continue;
            if (!newConfig.contains(path)) return true;
            Object newDefault = newConfig.get(path);
            if (newDefault != null && !newDefault.equals(entry.getValue())) return true;
        }

        for (String path : newMap.keySet()) {
            if (!path.equals(VERSION_KEY) && !userValues.containsKey(path)) return true;
        }

        return false;
    }

    private Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                result.put(key, config.get(key));
            }
        }
        return result;
    }

    private void applyUserValues(FileConfiguration newConfig, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            if (path.equals(VERSION_KEY)) continue;
            if (newConfig.contains(path)) {
                newConfig.set(path, entry.getValue());
            } else {
                plugin.debug("discord.yml: path '" + path + "' no longer exists in new defaults, skipping");
            }
        }
    }
}

