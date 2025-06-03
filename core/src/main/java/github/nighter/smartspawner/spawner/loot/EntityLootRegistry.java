package github.nighter.smartspawner.spawner.loot;

import github.nighter.smartspawner.economy.ItemPriceManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityLootRegistry {
    private final JavaPlugin plugin;
    private FileConfiguration lootConfig;
    private final Map<String, EntityLootConfig> entityLootConfigs;
    private final ItemPriceManager priceManager;
    private final Map<Material, Double> cachedPrices;

    public EntityLootRegistry(JavaPlugin plugin, ItemPriceManager priceManager) {
        this.plugin = plugin;
        this.lootConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mob_drops.yml"));
        this.entityLootConfigs = new ConcurrentHashMap<>();
        this.priceManager = priceManager;
        this.cachedPrices = new ConcurrentHashMap<>();
        loadConfigurations();
    }

    private void loadConfigurations() {
        // Clear caches before reloading
        entityLootConfigs.clear();
        cachedPrices.clear();

        // Iterate through all top-level keys (entity names)
        for (String entityName : lootConfig.getKeys(false)) {
            // Skip any sections that might be comments or other non-entity configs
            if (entityName.startsWith("#") || entityName.equals("per_mob_drop")) {
                continue;
            }

            ConfigurationSection entitySection = lootConfig.getConfigurationSection(entityName);
            if (entitySection == null) continue;

            int experience = entitySection.getInt("experience", 0);
            List<LootItem> items = new ArrayList<>();

            ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
            if (lootSection != null) {
                for (String itemKey : lootSection.getKeys(false)) {
                    ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    try {
                        Material material = Material.valueOf(itemKey.toUpperCase());
                        String[] amounts = itemSection.getString("amount", "1-1").split("-");
                        int minAmount = Integer.parseInt(amounts[0]);
                        int maxAmount = Integer.parseInt(amounts.length > 1 ? amounts[1] : amounts[0]);
                        double chance = itemSection.getDouble("chance", 100.0);

                        // Get the sell price from the price manager, using cache for performance
                        double sellPrice = getSellPrice(material);

                        Integer minDurability = null;
                        Integer maxDurability = null;
                        if (itemSection.contains("durability")) {
                            String[] durabilities = itemSection.getString("durability").split("-");
                            minDurability = Integer.parseInt(durabilities[0]);
                            maxDurability = Integer.parseInt(durabilities.length > 1 ? durabilities[1] : durabilities[0]);
                        }

                        String potionEffectType = null;
                        Integer potionDuration = null;
                        Integer potionAmplifier = null;

                        if (material == Material.TIPPED_ARROW && itemSection.contains("potion_effect")) {
                            ConfigurationSection potionSection = itemSection.getConfigurationSection("potion_effect");
                            if (potionSection != null) {
                                potionEffectType = potionSection.getString("effect");

                                // Convert seconds to ticks (20 ticks = 1 second)
                                int seconds = potionSection.getInt("duration", 5);
                                potionDuration = seconds * 20;

                                potionAmplifier = potionSection.getInt("level", 0);
                            }
                        }

                        items.add(new LootItem(material, minAmount, maxAmount, chance,
                                minDurability, maxDurability, potionEffectType,
                                potionDuration, potionAmplifier, sellPrice));

                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material name: " + itemKey + " in entity loot config for " + entityName);
                    }
                }
            }

            entityLootConfigs.put(entityName.toLowerCase(), new EntityLootConfig(experience, items));
        }
    }

    // Cached method to get sell price
    private double getSellPrice(Material material) {
        return cachedPrices.computeIfAbsent(material, priceManager::getPrice);
    }

    public EntityLootConfig getLootConfig(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return null;
        }
        return entityLootConfigs.get(entityType.name().toLowerCase());
    }

    public void reload() {
        entityLootConfigs.clear();
        cachedPrices.clear();
        this.lootConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mob_drops.yml"));
        loadConfigurations();
    }
}