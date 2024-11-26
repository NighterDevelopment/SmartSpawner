package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private final SmartSpawner plugin;
    private FileConfiguration config;
    private FileConfiguration lootConfig;
    private File configFile;
    private File lootConfigFile;
    private Map<String, Object> configCache;

    public ConfigManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configCache = new ConcurrentHashMap<>();
        loadConfigs();
    }

    // ===============================================================
    //                      Utility Methods
    // ===============================================================

    private void loadConfigs() {
        loadMainConfig();
        loadLootConfig();
        initializeCache();
    }
    public void saveConfigs() {
        saveMainConfig();
        saveLootConfig();
    }
    public void reloadConfigs() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
        mergeLootDefaults();
        initializeCache();
    }

    private void setDefaultIfNotExists(String path, Object defaultValue) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            plugin.saveConfig();
        }
    }

    public void debug(String message) {
        if (isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    // ===============================================================
    //                      Main Configs
    // ===============================================================

    public FileConfiguration getMainConfig() {
        return config;
    }

    private void loadMainConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Set default values if not present
        addDefaultConfigs();
    }

    private void saveMainConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save main config: " + e.getMessage());
        }
    }

    private void addDefaultConfigs() {
        // Global Settings
        if (!config.contains("settings")) {
            config.set("settings.language", "en");
            config.set("settings.debug", false);
            config.set("settings.save-interval", 6000);
        }

        // Spawner Core Mechanics
        if (!config.contains("spawner")) {
            config.set("spawner.default-entity", "PIG");
            config.set("spawner.min-mobs", 1);
            config.set("spawner.max-mobs", 4);
            config.set("spawner.range", 16);
            config.set("spawner.delay", 600);
            config.set("spawner.max-storage-pages", 1);
            config.set("spawner.max-stored-exp", 1000);
            config.set("spawner.max-stack-size", 1000);
            config.set("spawner.allow-exp-mending", true);
            config.set("spawner.allow-toggle-equipment-drops", true);
            config.set("spawner.allow-grief", false);
            config.set("spawner.activate-on-place", true);
        }

        // Spawner Breaking Mechanics
        if (!config.contains("spawner-break")) {
            config.set("spawner-break.enabled", true);
            config.set("spawner-break.required-tools",
                    Arrays.asList("IRON_PICKAXE", "GOLDEN_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE"));
            config.set("spawner-break.durability-loss-per-spawner", 1);
            config.set("spawner-break.silk-touch.required", true);
            config.set("spawner-break.silk-touch.level", 1);
            config.set("spawner-break.drop-stack.amount", 64);
        }

        // Economic Integration
        if (!config.contains("shop-integration")) {
            config.set("shop-integration", "EconomyShopGUI");
        }
        if (!config.contains("tax")) {
            config.set("tax.enabled", false);
            config.set("tax.rate", 10.0);
        }

        // Hopper Mechanics
        if (!config.contains("hopper")) {
            config.set("hopper.enabled", false);
            config.set("hopper.items-per-transfer", 1);
            config.set("hopper.check-interval", 20);
        }

        // Performance Optimizations
        if (!config.contains("performance")) {
            config.set("performance.batch-size", 3);
        }

        // Update checker configs
        if (!config.contains("update-checker")) {
            config.set("update-checker.enabled", true);
            config.set("update-checker.check-interval", 24);
            config.set("update-checker.notify-ops", true);
            config.set("update-checker.notify-on-join", true);
        }

        saveMainConfig();
    }

    private void initializeCache() {
        configCache.clear();

        // Cache global settings
        configCache.put("settings.language", config.getString("settings.language"));
        configCache.put("settings.debug", config.getBoolean("settings.debug"));
        configCache.put("settings.save-interval", config.getInt("settings.save-interval"));

        // Cache spawner settings
        configCache.put("spawner.default-entity", config.getString("spawner.default-entity"));
        configCache.put("spawner.min-mobs", config.getInt("spawner.min-mobs"));
        configCache.put("spawner.max-mobs", config.getInt("spawner.max-mobs"));
        configCache.put("spawner.range", config.getInt("spawner.range"));
        configCache.put("spawner.delay", config.getInt("spawner.delay"));
        configCache.put("spawner.max-storage-pages", config.getInt("spawner.max-storage-pages"));
        configCache.put("spawner.max-stack-size", config.getInt("spawner.max-stack-size"));
        configCache.put("spawner.max-stored-exp", config.getInt("spawner.max-stored-exp"));
        configCache.put("spawner.allow-exp-mending", config.getBoolean("spawner.allow-exp-mending"));
        configCache.put("spawner.allow-toggle-equipment-drops", config.getBoolean("spawner.allow-toggle-equipment-drops"));
        configCache.put("spawner.allow-grief", config.getBoolean("spawner.allow-grief"));
        configCache.put("spawner.activate-on-place", config.getBoolean("spawner.activate-on-place"));

        // Cache break settings
        configCache.put("spawner-break.enabled", config.getBoolean("spawner-break.enabled"));
        configCache.put("spawner-break.durability-loss", config.getInt("spawner-break.durability-loss-per-spawner"));
        configCache.put("spawner-break.silk-touch.required", config.getBoolean("spawner-break.silk-touch.required"));
        configCache.put("spawner-break.silk-touch.level", config.getInt("spawner-break.silk-touch.level"));
        configCache.put("spawner-break.drop-stack.amount", config.getInt("spawner-break.drop-stack.amount"));

        // Cache shop config
        configCache.put("shop-integration", config.getString("shop-integration"));
        configCache.put("tax.enabled", config.getBoolean("tax.enabled"));
        configCache.put("tax.rate", config.getDouble("tax.rate"));

        // Cache hopper settings
        configCache.put("hopper.enabled", config.getBoolean("hopper.enabled"));
        configCache.put("hopper.transfer-cooldown", config.getInt("hopper.transfer-cooldown"));
        configCache.put("hopper.check-interval", config.getInt("hopper.check-interval"));

        // Cache performance settings
        configCache.put("performance.batch-size", config.getInt("performance.batch-size"));

        // Cache update checker settings
        configCache.put("update-checker.enabled", config.getBoolean("update-checker.enabled"));
        configCache.put("update-checker.check-interval", config.getInt("update-checker.check-interval"));
        configCache.put("update-checker.notify-ops", config.getBoolean("update-checker.notify-ops"));
        configCache.put("update-checker.notify-on-join", config.getBoolean("update-checker.notify-on-join"));
    }

    // ===============================================================
    //                      Loot Configs
    // ===============================================================

    public FileConfiguration getLootConfig() {
        return lootConfig;
    }

    public void loadLootConfig() {
        lootConfigFile = new File(plugin.getDataFolder(), "mob_drops.yml");
        if (!lootConfigFile.exists()) {
            debug("Creating default mob_drops.yml");
            plugin.saveResource("mob_drops.yml", false);
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
        mergeLootDefaults();
    }

    private void mergeLootDefaults() {
        try (InputStream defaultLootConfigStream = plugin.getResource("mob_drops.yml")) {
            if (defaultLootConfigStream != null) {
                YamlConfiguration defaultLootConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultLootConfigStream, StandardCharsets.UTF_8)
                );

                for (String key : defaultLootConfig.getKeys(true)) {
                    if (!lootConfig.contains(key)) {
                        debug("Adding missing default config value: " + key);
                        lootConfig.set(key, defaultLootConfig.get(key));
                    }
                }
                saveLootConfig();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading default loot config: " + e.getMessage());
        }
    }

    private void saveLootConfig() {
        try {
            lootConfig.save(lootConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save loot config: " + e.getMessage());
        }
    }

    // ===============================================================
    //                      Global Settings
    // ===============================================================

    public boolean isDebugMode() {
        return (boolean) configCache.computeIfAbsent("settings.debug", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getSaveInterval() {
        return (int) configCache.computeIfAbsent("settings.save-interval", key -> {
            int defaultValue = 6000;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    // ===============================================================
    //                Spawner Core Mechanics Configs
    // ===============================================================

    public EntityType getDefaultEntityType() {
        Object cachedValue = configCache.get("spawner.default-entity");

        if (cachedValue instanceof EntityType) {
            return (EntityType) cachedValue;
        }

        String defaultValue = "PIG";
        String entityName;

        if (cachedValue instanceof String) {
            entityName = ((String) cachedValue).toUpperCase();
        } else {
            setDefaultIfNotExists("spawner.default-entity", defaultValue);
            entityName = config.getString("spawner.default-entity", defaultValue).toUpperCase();
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid default entity type: " + entityName);
            entityType = EntityType.PIG;
        }
        configCache.put("spawner.default-entity", entityType);
        return entityType;
    }

    public int getMinMobs() {
        return (int) configCache.computeIfAbsent("spawner.min-mobs", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxMobs() {
        return (int) configCache.computeIfAbsent("spawner.max-mobs", key -> {
            int defaultValue = 4;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getSpawnerRange() {
        return (int) configCache.computeIfAbsent("spawner.range", key -> {
            int defaultValue = 16;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getSpawnerDelay() {
        return (int) configCache.computeIfAbsent("spawner.delay", key -> {
            int defaultValue = 16;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxStoragePages() {
        return (int) configCache.computeIfAbsent("spawner.max-storage-pages", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxStackSize() {
        return (int) configCache.computeIfAbsent("spawner.max-stack-size", key -> {
            int defaultValue = 100;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getMaxStoredExp() {
        return (int) configCache.computeIfAbsent("spawner.max-stored-exp", key -> {
            int defaultValue = 1000;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean isAllowExpMending() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-exp-mending", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isAllowToggleEquipmentItems() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-toggle-equipment-drops", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isAllowGrief() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-grief", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean getActivateOnPlace() {
        return (boolean) configCache.computeIfAbsent("spawner.activate-on-place", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    // ===============================================================
    //                   Spawner Breaking Mechanics
    // ===============================================================

    public boolean isSpawnerBreakEnabled() {
        return (boolean) configCache.computeIfAbsent("spawner-break.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    @SuppressWarnings("unchecked")
    public List<String> getRequiredTools() {
        return (List<String>) configCache.computeIfAbsent("spawner-break.required-tools", key -> {
            List<String> defaultValue = Arrays.asList(
                    "IRON_PICKAXE",
                    "GOLDEN_PICKAXE",
                    "DIAMOND_PICKAXE",
                    "NETHERITE_PICKAXE"
            );
            setDefaultIfNotExists(key, defaultValue);
            return config.getStringList(key);
        });
    }

    public int getDurabilityLossPerSpawner() {
        return (int) configCache.computeIfAbsent("spawner-break.durability-loss-per-spawner", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean isSilkTouchRequired() {
        return (boolean) configCache.computeIfAbsent("spawner-break.silk-touch.required", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getSilkTouchLevel() {
        return (int) configCache.computeIfAbsent("spawner-break.silk-touch.level", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getDropStackAmount() {
        return (int) configCache.computeIfAbsent("spawner-break.drop-stack.amount", key -> {
            int defaultValue = 64;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    // ===============================================================
    //                   Economic Integration
    // ===============================================================

    public enum ShopType {
        DISABLED,
        ECONOMY_SHOP_GUI,
        ECONOMY_SHOP_GUI_PREMIUM;

        public static ShopType fromString(String value) {
            if (value == null) return DISABLED;

            switch (value.toLowerCase()) {
                case "economyshopgui":
                    return ECONOMY_SHOP_GUI;
                case "economyshopgui-premium":
                    return ECONOMY_SHOP_GUI_PREMIUM;
                case "disabled":
                default:
                    return DISABLED;
            }
        }
    }

    public ShopType getShopType() {
        String shopType = (String) configCache.computeIfAbsent("shop-integration",
                key -> config.getString(key, "EconomyShopGUI"));
        return ShopType.fromString(shopType);
    }

    public boolean isTaxEnabled() {
        return (boolean) configCache.computeIfAbsent("tax.enabled", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public double getTaxPercentage() {
        if (!isTaxEnabled()) return 0.0;
        return (double) configCache.computeIfAbsent("tax.rate", key -> {
            double defaultValue = 10.0;
            setDefaultIfNotExists(key, defaultValue);
            return config.getDouble(key, defaultValue);
        });
    }

    // ===============================================================
    //                Hopper Mechanics (Experimental)
    // ===============================================================

    public boolean isHopperEnabled() {
        return (boolean) configCache.computeIfAbsent("hopper.enabled", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getHopperItemsPerTransfer() {
        return (int) configCache.computeIfAbsent("hopper.items-per-transfer", key -> {
            int defaultValue = 64;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getHopperCheckInterval() {
        return (int) configCache.computeIfAbsent("hopper.check-interval", key -> {
            int defaultValue = 20;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    // ===============================================================
    //                  Performance Optimizations
    // ===============================================================

    public int getBatchSize() {
        return (int) configCache.computeIfAbsent("performance.batch-size", key -> {
            int defaultValue = 3;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    // ===============================================================
    //                    Plugin Update Checker
    // ===============================================================

    public boolean isUpdateCheckerEnabled() {
        return (boolean) configCache.computeIfAbsent("update-checker.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getUpdateCheckInterval() {
        return (int) configCache.computeIfAbsent("update-checker.check-interval", key -> {
            int defaultValue = 24;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean shouldNotifyOps() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-ops", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean shouldNotifyOnJoin() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-on-join", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }
}
