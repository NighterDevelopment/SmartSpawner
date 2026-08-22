package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the item spawner settings configuration
 */
public class ItemSpawnerSettingsConfig {
    private static final String RESOURCE = "spawner_items.yml";
    /** Replaced by {@link #RESOURCE} in 1.8.0. Never read, only reported once. */
    private static final String LEGACY_RESOURCE = "item_spawners_settings.yml";

    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;
    
    /**
     * Shown when an entry names no head of its own, or names one that does not exist. Not a config
     * key: it is only ever a fallback, so a server owner has nothing useful to change here.
     */
    private static final Material FALLBACK_HEAD = Material.SPAWNER;

    private final Map<Material, ItemHeadData> itemHeadMap = new EnumMap<>(Material.class);
    private final Set<Material> validItemSpawnerMaterials = new HashSet<>();
    
    // Loot data for item spawners
    private final Map<Material, EntityLootConfig> itemLootConfigs = new ConcurrentHashMap<>();
    private final Map<Material, ItemStack> displayItems = new EnumMap<>(Material.class);
    private final Map<String, ItemDefinition> definitionsByName = new HashMap<>();
    private final Map<String, ItemStack> displayItemsByName = new HashMap<>();
    private final Map<Material, ItemDefinition> defaultDefinitionsByMaterial = new EnumMap<>(Material.class);
    
    public ItemSpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), RESOURCE);
    }
    
    /**
     * Load or create the item spawners settings configuration
     */
    public void load() {
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            saveDefaultConfig();
            SupersededConfigNotice.warn(plugin, RESOURCE, LEGACY_RESOURCE);
        }
        
        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Parse configuration
        parseConfig();
    }
    
    /**
     * Save the default configuration from resources
     */
    private void saveDefaultConfig() {
        try {
            InputStream inputStream = plugin.getResource(RESOURCE);
            if (inputStream == null) {
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default spawner_items.yml: " + e.getMessage());
        }
    }
    
    /**
     * Parse the configuration and populate item head data
     */
    private void parseConfig() {
        itemHeadMap.clear();
        validItemSpawnerMaterials.clear();
        itemLootConfigs.clear();
        displayItems.clear();
        definitionsByName.clear();
        displayItemsByName.clear();
        defaultDefinitionsByMaterial.clear();
        
        // Parse each item's configuration
        for (String configName : config.getKeys(false)) {
            // Anything that is not a section is a stray scalar, not an entry.
            ConfigurationSection itemSection = config.getConfigurationSection(configName);
            if (itemSection == null) continue;

            // Validate material type
            Material material;
            try {
                String materialName = itemSection.getString("item", configName);
                material = Material.valueOf(materialName == null ? "" : materialName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Item for '" + configName + "' is invalid or missing in " + RESOURCE);
                continue;
            }

            String normalizedName = itemSection.contains("item")
                    ? SpawnerConfigName.normalize(configName)
                    : SpawnerConfigName.defaultName(material.name());
            if (normalizedName.isEmpty() || definitionsByName.containsKey(normalizedName)) {
                plugin.getLogger().warning("Duplicate or invalid spawner name '" + configName + "' in " + RESOURCE);
                continue;
            }

            // Parse head texture data
            parseHeadTexture(material, itemSection);
            ItemStack displayItem = parseDisplayItem(material, itemSection);
            
            // Parse loot data
            parseLootData(material, itemSection);
            
            // Add to valid materials set
            validItemSpawnerMaterials.add(material);
            ItemDefinition definition = new ItemDefinition(normalizedName, material, itemLootConfigs.get(material));
            definitionsByName.put(normalizedName, definition);
            if (displayItem != null) {
                displayItemsByName.put(normalizedName, displayItem);
                displayItems.putIfAbsent(material, displayItem);
            }
            defaultDefinitionsByMaterial.putIfAbsent(material, definition);
        }
    }

    private ItemStack parseDisplayItem(Material material, ConfigurationSection itemSection) {
        String rawItem = itemSection.getString("nbt_data");
        if (rawItem == null || rawItem.isBlank()) {
            return null;
        }
        try {
            return ConfiguredItemParser.parse(rawItem).asQuantity(1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid nbt_data for " + material.name() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse loot configuration for an item spawner
     */
    private void parseLootData(Material material, ConfigurationSection itemSection) {
        int experience = itemSection.getInt("experience", 0);
        List<LootItem> items = new ArrayList<>();

        ConfigurationSection lootSection = itemSection.getConfigurationSection("loot");
        if (lootSection != null) {
            for (String itemKey : lootSection.getKeys(false)) {
                ConfigurationSection lootItemSection = lootSection.getConfigurationSection(itemKey);
                if (lootItemSection == null) continue;

                LootItem lootItem = LootEntryParser.parse(
                        lootItemSection, itemKey, plugin.getItemPriceManager(), plugin.getLogger(),
                        "item spawner " + material.name());
                if (lootItem != null) {
                    items.add(lootItem);
                }
            }
        }

        // Create and store EntityLootConfig
        EntityLootConfig lootConfig = new EntityLootConfig(experience, items);
        itemLootConfigs.put(material, lootConfig);
    }
    
    /**
     * Parse head texture configuration for an item
     */
    private void parseHeadTexture(Material material, ConfigurationSection itemSection) {
        ConfigurationSection headSection = itemSection.getConfigurationSection("mob_head");
        if (headSection == null) {
            return;
        }
        
        String headMaterialName = headSection.getString("item", material.name());
        String customTexture = headSection.getString("hash_texture");
        
        // Validate material
        Material headMaterial;
        try {
            headMaterial = Material.valueOf(headMaterialName.toUpperCase());
            if (!headMaterial.isItem()) {
                plugin.getLogger().warning("Material " + headMaterialName + " for " + material + " is not an item, using the item itself");
                headMaterial = material;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid head material " + headMaterialName + " for " + material + ", using the item itself");
            headMaterial = material;
        }
        
        // Store item head data
        itemHeadMap.putIfAbsent(material, new ItemHeadData(headMaterial, customTexture));
    }
    
    /**
     * Get the head texture data for an item material
     */
    public ItemHeadData getHeadData(Material material) {
        return itemHeadMap.getOrDefault(material, new ItemHeadData(FALLBACK_HEAD, null));
    }
    
    /**
     * Get the loot configuration for an item spawner material
     */
    public EntityLootConfig getLootConfig(Material material) {
        return itemLootConfigs.get(material);
    }

    /** Uses the first configured loot template as the model rendered inside the spawner cage. */
    public ItemStack getDisplayItem(Material material) {
        ItemStack configuredDisplay = displayItems.get(material);
        if (configuredDisplay != null) {
            return configuredDisplay.clone();
        }
        EntityLootConfig lootConfig = itemLootConfigs.get(material);
        if (lootConfig != null) {
            for (LootItem lootItem : lootConfig.getAllItems()) {
                if (lootItem.template() != null) {
                    return lootItem.template().asQuantity(1);
                }
            }
        }
        return new ItemStack(material, 1);
    }

    public ItemStack getDisplayItem(String configName, Material fallback) {
        String normalized = SpawnerConfigName.normalize(configName);
        ItemStack configured = displayItemsByName.get(normalized);
        if (configured != null) return configured.clone();
        ItemDefinition definition = definitionsByName.get(normalized);
        if (definition != null && definition.lootConfig() != null) {
            for (LootItem lootItem : definition.lootConfig().getAllItems()) {
                if (lootItem.template() != null) return lootItem.template().asQuantity(1);
            }
            return new ItemStack(definition.material(), 1);
        }
        return getDisplayItem(fallback);
    }

    public ItemDefinition getDefinition(String name) {
        return definitionsByName.get(SpawnerConfigName.normalize(name));
    }

    public ItemDefinition getDefaultDefinition(Material material) {
        return defaultDefinitionsByMaterial.get(material);
    }

    public Set<String> getDefinitionNames() {
        return Collections.unmodifiableSet(definitionsByName.keySet());
    }

    public record ItemDefinition(String name, Material material, EntityLootConfig lootConfig) {}
    
    /**
     * Check if a material is a valid item spawner type
     */
    public boolean isValidItemSpawner(Material material) {
        return validItemSpawnerMaterials.contains(material);
    }
    
    /**
     * Get all valid item spawner materials
     */
    public Set<Material> getValidItemSpawnerMaterials() {
        return Collections.unmodifiableSet(validItemSpawnerMaterials);
    }
    
    /**
     * Reload the configuration
     */
    public void reload() {
        load();
    }
    
    /**
     * Data class for item head information
     */
    public static class ItemHeadData {
        private final Material material;
        private final String customTexture;
        
        public ItemHeadData(Material material, String customTexture) {
            this.material = material;
            this.customTexture = customTexture;
        }
        
        public Material getMaterial() {
            return material;
        }
        
        public String getCustomTexture() {
            return customTexture;
        }
        
        public boolean hasCustomTexture() {
            return customTexture != null && !customTexture.isEmpty() && !customTexture.equalsIgnoreCase("null");
        }
    }
}
