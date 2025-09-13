package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.GuiLayoutUpdater;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class GuiLayoutConfig {
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String STORAGE_GUI_FILE = "storage_gui.yml";
    private static final String DEFAULT_LAYOUT = "default";
    private static final int MIN_SLOT = 1;
    private static final int MAX_SLOT = 9;
    private static final int SLOT_OFFSET = 44;

    private final SmartSpawner plugin;
    private final File layoutsDir;
    private final GuiLayoutUpdater guiLayoutUpdater;
    private String currentLayout;
    private GuiLayout currentGuiLayout;

    public GuiLayoutConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        this.guiLayoutUpdater = new GuiLayoutUpdater(plugin);
        loadLayout();
    }

    public void loadLayout() {
        this.currentLayout = plugin.getConfig().getString("gui_layout", DEFAULT_LAYOUT);
        initializeLayoutsDirectory();
        this.currentGuiLayout = loadCurrentLayout();
    }

    private void initializeLayoutsDirectory() {
        if (!layoutsDir.exists()) {
            layoutsDir.mkdirs();
        }
        autoSaveLayoutFiles();
        
        // Update GUI layouts using the new updater system
        guiLayoutUpdater.checkAndUpdateGuiLayouts();
    }

    private void autoSaveLayoutFiles() {
        try {
            String[] layoutNames = new String[]{DEFAULT_LAYOUT, "DonutSMP"};

            for (String layoutName : layoutNames) {
                File layoutDir = new File(layoutsDir, layoutName);
                if (!layoutDir.exists()) {
                    layoutDir.mkdirs();
                }

                File storageFile = new File(layoutDir, STORAGE_GUI_FILE);
                String resourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + STORAGE_GUI_FILE;

                if (!storageFile.exists()) {
                    try {
                        plugin.saveResource(resourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to auto-save layout resource for " + layoutName + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to auto-save layout files", e);
        }
    }

    private GuiLayout loadCurrentLayout() {
        File layoutDir = new File(layoutsDir, currentLayout);
        File storageFile = new File(layoutDir, STORAGE_GUI_FILE);

        if (storageFile.exists()) {
            GuiLayout layout = loadStorageLayout(storageFile);
            if (layout != null) {
                plugin.getLogger().info("Loaded GUI layout: " + currentLayout);
                return layout;
            }
        }

        if (!currentLayout.equals(DEFAULT_LAYOUT)) {
            plugin.getLogger().warning("Layout '" + currentLayout + "' not found. Attempting to use default layout.");
            File defaultLayoutDir = new File(layoutsDir, DEFAULT_LAYOUT);
            File defaultStorageFile = new File(defaultLayoutDir, STORAGE_GUI_FILE);

            if (defaultStorageFile.exists()) {
                GuiLayout defaultLayout = loadStorageLayout(defaultStorageFile);
                if (defaultLayout != null) {
                    plugin.getLogger().info("Loaded default layout as fallback");
                    return defaultLayout;
                }
            }
        }

        plugin.getLogger().severe("No valid layout found! Creating empty layout as fallback.");
        return new GuiLayout();
    }

    private GuiLayout loadStorageLayout(File file) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            GuiLayout layout = new GuiLayout();

            if (!config.contains("buttons")) {
                plugin.getLogger().warning("No buttons section found in GUI layout: " + file.getName());
                return layout;
            }

            for (String buttonKey : config.getConfigurationSection("buttons").getKeys(false)) {
                if (!loadButton(config, layout, buttonKey)) {
                    continue;
                }
            }

            return layout;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load storage layout from " + file.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    private boolean loadButton(FileConfiguration config, GuiLayout layout, String buttonKey) {
        String path = "buttons." + buttonKey;

        if (!config.getBoolean(path + ".enabled", true)) {
            return false;
        }

        // Check conditions first
        Map<String, Object> conditions = loadConditions(config, path);
        if (!evaluateConditions(conditions)) {
            plugin.debug("Button '" + buttonKey + "' skipped due to failed conditions: " + conditions);
            return false;
        }

        int slot = config.getInt(path + ".slot", -1);
        String materialName = config.getString(path + ".material", "STONE");
        String soundName = config.getString(path + ".sound", null);

        if (!isValidSlot(slot)) {
            plugin.getLogger().warning(String.format(
                    "Invalid slot %d for button %s. Must be between %d and %d.",
                    slot, buttonKey, MIN_SLOT, MAX_SLOT));
            return false;
        }

        Material material = parseMaterial(materialName, buttonKey);
        Sound sound = parseSound(soundName, buttonKey);
        int actualSlot = SLOT_OFFSET + slot;

        GuiButton button = new GuiButton(buttonKey, actualSlot, material, true, sound, conditions);
        layout.addButton(buttonKey, button);
        return true;
    }

    private Map<String, Object> loadConditions(FileConfiguration config, String path) {
        Map<String, Object> conditions = new HashMap<>();
        ConfigurationSection conditionsSection = config.getConfigurationSection(path + ".conditions");
        if (conditionsSection != null) {
            for (String key : conditionsSection.getKeys(false)) {
                conditions.put(key, conditionsSection.get(key));
            }
        }
        return conditions;
    }

    private boolean evaluateConditions(Map<String, Object> conditions) {
        if (conditions.isEmpty()) {
            return true; // No conditions means always show
        }

        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            String conditionKey = condition.getKey();
            Object expectedValue = condition.getValue();

            if (!evaluateCondition(conditionKey, expectedValue)) {
                return false; // If any condition fails, don't show button
            }
        }

        return true; // All conditions passed
    }

    private boolean evaluateCondition(String conditionKey, Object expectedValue) {
        switch (conditionKey.toLowerCase()) {
            case "shopintegration":
                // Check if shop integration is enabled in the plugin
                boolean shopEnabled = plugin.getConfig().getBoolean("custom_economy.shop_integration.enabled", false);
                return shopEnabled == (Boolean) expectedValue;
            
            // Add more condition types here as needed
            default:
                plugin.getLogger().warning("Unknown condition type: " + conditionKey);
                return true; // Unknown conditions default to true to avoid breaking configs
        }
    }

    private boolean isValidSlot(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }

    private Material parseMaterial(String materialName, String buttonKey) {
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format(
                    "Invalid material %s for button %s. Using STONE instead.",
                    materialName, buttonKey));
            return Material.STONE;
        }
    }

    private Sound parseSound(String soundName, String buttonKey) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return null; // No sound specified
        }

        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format(
                    "Invalid sound %s for button %s. No sound will be played.",
                    soundName, buttonKey));
            return null;
        }
    }

    public GuiLayout getCurrentLayout() {
        return currentGuiLayout;
    }

    public void reloadLayouts() {
        loadLayout();
    }
}