package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.updates.YamlMigrator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the merged spawner settings configuration that combines mob drops and head textures.
 *
 * <p>The file is kept in sync by the version-less {@link YamlMigrator}: it is created if missing and,
 * on every startup, any keys added by a plugin update are topped up while the user's own edits are
 * preserved.</p>
 */
public class SpawnerSettingsConfig {
    private static final String RESOURCE = "spawners_settings.yml";

    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;

    // Mob head data
    private Material defaultMaterial;
    private final Map<EntityType, MobHeadData> mobHeadMap = new EnumMap<>(EntityType.class);

    // Loot data
    private final Map<String, EntityLootConfig> entityLootConfigs = new ConcurrentHashMap<>();
    private final Set<Material> loadedMaterials = new HashSet<>();

    // Spawner item drop chance when the spawner block is broken
    private final Map<EntityType, Double> spawnerDropChances = new EnumMap<>(EntityType.class);

    public SpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), RESOURCE);
    }

    /**
     * Load or create the spawners settings configuration.
     */
    public void load() {
        // Creates the file if missing and tops up any keys added by a plugin update. A mob's loot
        // section is left alone once the user has one: those entries are a list they curate, so
        // topping it up would resurrect drops they deliberately deleted on every startup.
        YamlMigrator.migrate(configFile, plugin.getResource(RESOURCE), List.of(), null, true,
                YamlMigrator.OwnedSection.curated((defaults, path) -> path.endsWith(".loot")),
                plugin.getLogger());

        config = YamlConfiguration.loadConfiguration(configFile);
        parseConfig();
    }

    /**
     * Parse the configuration and populate both mob head and loot data
     */
    private void parseConfig() {
        mobHeadMap.clear();
        entityLootConfigs.clear();
        loadedMaterials.clear();
        spawnerDropChances.clear();

        // Get default material
        String defaultMaterialName = config.getString("default_material", "SPAWNER");
        try {
            defaultMaterial = Material.valueOf(defaultMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid default_material in spawners_settings.yml: " + defaultMaterialName + ", using SPAWNER");
            defaultMaterial = Material.SPAWNER;
        }

        // Parse each mob's configuration
        for (String entityName : config.getKeys(false)) {
            // Skip special keys
            if (entityName.equals("default_material")) {
                continue;
            }

            // Validate entity type
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Entity type '" + entityName + "' is invalid or not available in server version " + plugin.getServer().getBukkitVersion());
                continue;
            }

            ConfigurationSection entitySection = config.getConfigurationSection(entityName);
            if (entitySection == null) continue;

            // Parse head texture data
            parseHeadTexture(entityType, entitySection);

            // Parse loot data
            parseLootData(entityName, entitySection);

            parseSpawnerDropChance(entityType, entitySection);
        }
    }

    private void parseSpawnerDropChance(EntityType entityType, ConfigurationSection entitySection) {
        if (!entitySection.contains("drop_chance")) {
            return;
        }

        double dropChance = entitySection.getDouble("drop_chance", 100.0);
        if (dropChance < 0.0 || dropChance > 100.0) {
            plugin.getLogger().warning("Invalid drop_chance for " + entityType.name() +
                    " in spawners_settings.yml. Value must be between 0.0 and 100.0; using 100.0");
            dropChance = 100.0;
        }

        spawnerDropChances.put(entityType, dropChance);
    }

    /**
     * Parse head texture configuration for an entity
     */
    private void parseHeadTexture(EntityType entityType, ConfigurationSection entitySection) {
        ConfigurationSection headSection = entitySection.getConfigurationSection("head_texture");
        if (headSection == null) {
            return;
        }

        String materialName = headSection.getString("material", "SPAWNER");
        String customTexture = headSection.getString("custom_texture");

        // Validate material
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
            if (!material.isItem()) {
                plugin.getLogger().warning("Material " + materialName + " for " + entityType + " is not an item, using default");
                material = defaultMaterial;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material " + materialName + " for " + entityType + ", using default");
            material = defaultMaterial;
        }

        // Store mob head data
        mobHeadMap.put(entityType, new MobHeadData(material, customTexture));
    }

    /**
     * Parse loot configuration for an entity
     */
    private void parseLootData(String entityName, ConfigurationSection entitySection) {
        int experience = entitySection.getInt("experience", 0);
        List<LootItem> items = new ArrayList<>();

        // Cache price manager reference for better performance
        ItemPriceManager priceManager = plugin.getItemPriceManager();

        ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
        if (lootSection != null) {
            for (String itemKey : lootSection.getKeys(false)) {
                ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;

                try {
                    // Get the material
                    Material material;
                    try {
                        material = Material.valueOf(itemKey.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = null;
                    }

                    if (material == null) {
                        plugin.getLogger().warning("Material '" + itemKey + "' is not available in server version " +
                                plugin.getServer().getBukkitVersion() + " - skipping for entity " + entityName);
                        continue;
                    }

                    loadedMaterials.add(material);

                    String[] amounts = itemSection.getString("amount", "1-1").split("-");
                    int minAmount = Integer.parseInt(amounts[0]);
                    int maxAmount = Integer.parseInt(amounts.length > 1 ? amounts[1] : amounts[0]);
                    double chance = itemSection.getDouble("chance", 100.0);

                    double sellPrice = 0.0;
                    if (priceManager != null) {
                        sellPrice = priceManager.getPrice(material);
                    }

                    Integer minDurability = null;
                    Integer maxDurability = null;
                    if (itemSection.contains("durability")) {
                        String[] durabilities = itemSection.getString("durability").split("-");
                        minDurability = Integer.parseInt(durabilities[0]);
                        maxDurability = Integer.parseInt(durabilities.length > 1 ? durabilities[1] : durabilities[0]);
                    }

                    PotionType potionType = null;
                    if (material == Material.TIPPED_ARROW && itemSection.contains("potion_type")) {
                        String potionTypeName = itemSection.getString("potion_type");
                        if (potionTypeName != null) {
                            try {
                                potionType = PotionType.valueOf(potionTypeName.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid potion type '" + potionTypeName +
                                        "' for entity " + entityName);
                                continue;
                            }
                        }
                    }

                    items.add(new LootItem(material, minAmount, maxAmount, chance,
                            minDurability, maxDurability, potionType, sellPrice));

                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing material '" + itemKey + "' for entity " + entityName + ": " + e.getMessage());
                }
            }
        }

        entityLootConfigs.put(entityName.toLowerCase(), new EntityLootConfig(experience, items));
    }

    // ===== Mob Head Methods =====

    /**
     * Get the material for a specific entity type
     */
    public Material getMaterial(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.material : defaultMaterial;
    }

    /**
     * Get the custom texture for a specific entity type
     */
    public String getCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.customTexture : null;
    }

    /**
     * Check if an entity type has a custom texture configured
     */
    public boolean hasCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null && data.customTexture != null && !data.customTexture.isEmpty();
    }

    // ===== Loot Methods =====

    /**
     * Get loot configuration for an entity type
     */
    public EntityLootConfig getLootConfig(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return null;
        }
        return entityLootConfigs.get(entityType.name().toLowerCase());
    }

    /**
     * Get the spawner item drop chance for a broken Smart Spawner.
     */
    public double getSpawnerDropChance(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return 100.0;
        }
        return spawnerDropChances.getOrDefault(entityType, 100.0);
    }

    /**
     * Check whether an entity has an explicit spawner item drop chance configured.
     */
    public boolean hasSpawnerDropChance(EntityType entityType) {
        return entityType != null && entityType != EntityType.UNKNOWN && spawnerDropChances.containsKey(entityType);
    }

    /**
     * Get all loaded materials
     */
    public Set<Material> getLoadedMaterials() {
        return new HashSet<>(loadedMaterials);
    }

    /**
     * Reload the configuration
     */
    public void reload() {
        load();
    }

    /**
     * Internal class to store mob head data
     */
    private static class MobHeadData {
        final Material material;
        final String customTexture;

        MobHeadData(Material material, String customTexture) {
            this.material = material;
            this.customTexture = customTexture;
        }
    }
}
