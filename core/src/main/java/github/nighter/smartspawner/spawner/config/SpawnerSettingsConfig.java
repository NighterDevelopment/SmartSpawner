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
import org.bukkit.entity.EntitySnapshot;

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
    private final Map<String, Double> namedSpawnerDropChances = new HashMap<>();
    private final Map<EntityType, EntitySnapshot> entityDisplaySnapshots = new EnumMap<>(EntityType.class);
    private final Map<String, MobDefinition> definitionsByName = new HashMap<>();
    private final Map<String, EntitySnapshot> snapshotsByName = new HashMap<>();
    private final Map<EntityType, MobDefinition> defaultDefinitionsByEntity = new EnumMap<>(EntityType.class);

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
        namedSpawnerDropChances.clear();
        entityDisplaySnapshots.clear();
        definitionsByName.clear();
        snapshotsByName.clear();
        defaultDefinitionsByEntity.clear();

        // Parse each mob's configuration
        for (String configName : config.getKeys(false)) {
            // Anything that is not a section is a stray scalar, not an entry.
            ConfigurationSection entitySection = config.getConfigurationSection(configName);
            if (entitySection == null) continue;

            // Validate entity type
            EntityType entityType;
            try {
                String entityName = entitySection.getString("entity", configName);
                entityType = EntityType.valueOf(entityName == null ? "" : entityName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Entity for '" + configName + "' is invalid or missing in " + RESOURCE);
                continue;
            }

            String normalizedName = entitySection.contains("entity")
                    ? SpawnerConfigName.normalize(configName)
                    : SpawnerConfigName.defaultName(entityType.name());
            if (normalizedName.isEmpty() || definitionsByName.containsKey(normalizedName)) {
                plugin.getLogger().warning("Duplicate or invalid spawner name '" + configName + "' in " + RESOURCE);
                continue;
            }

            // Parse head texture data
            parseHeadTexture(entityType, entitySection);
            EntitySnapshot snapshot = parseEntityDisplay(entityType, entitySection);

            // Parse loot data
            parseLootData(normalizedName, entitySection);

            parseSpawnerDropChance(normalizedName, entityType, entitySection);
            MobDefinition definition = new MobDefinition(normalizedName, entityType,
                    entityLootConfigs.get(normalizedName));
            definitionsByName.put(normalizedName, definition);
            if (snapshot != null) {
                snapshotsByName.put(normalizedName, snapshot);
                entityDisplaySnapshots.putIfAbsent(entityType, snapshot);
            }
            defaultDefinitionsByEntity.putIfAbsent(entityType, definition);
        }
    }

    private EntitySnapshot parseEntityDisplay(EntityType entityType, ConfigurationSection entitySection) {
        String nbt = entitySection.getString("nbt_data");
        if (nbt == null || nbt.isBlank()) {
            return null;
        }

        String trimmed = nbt.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}') {
            plugin.getLogger().warning("Invalid nbt_data for " + entityType.name() + ": expected an SNBT compound");
            return null;
        }

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        String fullNbt = "{id:\"" + entityType.getKey().asString() + "\""
                + (body.isEmpty() ? "" : "," + body) + "}";
        try {
            EntitySnapshot snapshot = plugin.getServer().getEntityFactory().createEntitySnapshot(fullNbt);
            if (snapshot.getEntityType() == entityType) {
                return snapshot;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid nbt_data for " + entityType.name() + ": " + e.getMessage());
        }
        return null;
    }

    private void parseSpawnerDropChance(String configName, EntityType entityType,
                                        ConfigurationSection entitySection) {
        if (!entitySection.contains("drop_chance")) {
            return;
        }

        double dropChance = entitySection.getDouble("drop_chance", 100.0);
        if (dropChance < 0.0 || dropChance > 100.0) {
            plugin.getLogger().warning("Invalid drop_chance for " + entityType.name() +
                    " in spawner_mobs.yml. Value must be between 0.0 and 100.0; using 100.0");
            dropChance = 100.0;
        }

        spawnerDropChances.putIfAbsent(entityType, dropChance);
        namedSpawnerDropChances.put(configName, dropChance);
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
        mobHeadMap.putIfAbsent(entityType, new MobHeadData(material, customTexture));
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
        MobDefinition definition = defaultDefinitionsByEntity.get(entityType);
        return definition == null ? null : definition.lootConfig();
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

    public double getSpawnerDropChance(String configName, EntityType fallback) {
        return namedSpawnerDropChances.getOrDefault(SpawnerConfigName.normalize(configName),
                getSpawnerDropChance(fallback));
    }

    public boolean hasSpawnerDropChance(String configName) {
        return namedSpawnerDropChances.containsKey(SpawnerConfigName.normalize(configName));
    }

    public EntitySnapshot getEntityDisplaySnapshot(EntityType entityType) {
        return entityDisplaySnapshots.get(entityType);
    }

    public EntitySnapshot getEntityDisplaySnapshot(String configName, EntityType fallback) {
        String normalized = SpawnerConfigName.normalize(configName);
        if (definitionsByName.containsKey(normalized)) return snapshotsByName.get(normalized);
        return entityDisplaySnapshots.get(fallback);
    }

    public MobDefinition getDefinition(String name) {
        return definitionsByName.get(SpawnerConfigName.normalize(name));
    }

    public MobDefinition getDefaultDefinition(EntityType type) {
        return defaultDefinitionsByEntity.get(type);
    }

    public Set<String> getDefinitionNames() {
        return Collections.unmodifiableSet(definitionsByName.keySet());
    }

    public record MobDefinition(String name, EntityType entityType, EntityLootConfig lootConfig) {}

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
