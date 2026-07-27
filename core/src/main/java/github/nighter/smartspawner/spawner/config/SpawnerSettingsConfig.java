package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.updates.YamlMigrator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

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
    private static final String RESOURCE = "spawner_mobs.yml";
    /** Replaced by {@link #RESOURCE} in 1.8.0. Never read, only reported once. */
    private static final String LEGACY_RESOURCE = "spawners_settings.yml";

    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;

    /**
     * Shown when a mob names no head of its own, or names one that does not exist. Not a config key:
     * it is only ever a fallback, so a server owner has nothing useful to change here.
     */
    private static final Material FALLBACK_HEAD = Material.SPAWNER;

    // Mob head data
    private final Map<EntityType, MobHeadData> mobHeadMap = new EnumMap<>(EntityType.class);

    // Loot data
    private final Map<String, EntityLootConfig> entityLootConfigs = new ConcurrentHashMap<>();

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
        boolean firstRun = !configFile.exists();

        // Creates the file if missing and tops up any keys added by a plugin update. A mob's loot
        // section is left alone once the user has one: those entries are a list they curate, so
        // topping it up would resurrect drops they deleted and duplicate any entry the shipped file
        // has since renamed.
        YamlMigrator.migrate(configFile, plugin.getResource(RESOURCE), List.of(), null, true,
                YamlMigrator.OwnedSection.curated((defaults, path) -> path.endsWith(".loot")),
                plugin.getLogger());

        if (firstRun) {
            SupersededConfigNotice.warn(plugin, RESOURCE, LEGACY_RESOURCE);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        parseConfig();
    }

    /**
     * Parse the configuration and populate both mob head and loot data
     */
    private void parseConfig() {
        mobHeadMap.clear();
        entityLootConfigs.clear();
        spawnerDropChances.clear();

        // Parse each mob's configuration
        for (String entityName : config.getKeys(false)) {
            // Anything that is not a section is a stray scalar, not an entry.
            ConfigurationSection entitySection = config.getConfigurationSection(entityName);
            if (entitySection == null) continue;

            // Validate entity type
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Entity type '" + entityName + "' is invalid or not available in server version " + plugin.getServer().getBukkitVersion());
                continue;
            }

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
                    " in spawner_mobs.yml. Value must be between 0.0 and 100.0; using 100.0");
            dropChance = 100.0;
        }

        spawnerDropChances.put(entityType, dropChance);
    }

    /**
     * Parse head texture configuration for an entity
     */
    private void parseHeadTexture(EntityType entityType, ConfigurationSection entitySection) {
        ConfigurationSection headSection = entitySection.getConfigurationSection("mob_head");
        if (headSection == null) {
            return;
        }

        String materialName = headSection.getString("item", "SPAWNER");
        String customTexture = headSection.getString("hash_texture");

        // Validate material
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
            if (!material.isItem()) {
                plugin.getLogger().warning("Material " + materialName + " for " + entityType + " is not an item, using default");
                material = FALLBACK_HEAD;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material " + materialName + " for " + entityType + ", using default");
            material = FALLBACK_HEAD;
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

        ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
        if (lootSection != null) {
            for (String itemKey : lootSection.getKeys(false)) {
                ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;

                LootItem lootItem = LootEntryParser.parse(
                        itemSection, itemKey, plugin.getItemPriceManager(), plugin.getLogger(),
                        "entity " + entityName);
                if (lootItem != null) {
                    items.add(lootItem);
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
        return data != null ? data.material : FALLBACK_HEAD;
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
