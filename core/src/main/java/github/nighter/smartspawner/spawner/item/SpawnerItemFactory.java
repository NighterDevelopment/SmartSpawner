package github.nighter.smartspawner.spawner.item;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.loot.LootItem;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.DataComponentTypeKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SpawnerItemFactory {

    private static final long CACHE_EXPIRY_TIME_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int MAX_CACHE_SIZE = 100;
    private static final Set<DataComponentType> HIDDEN_TOOLTIP_COMPONENTS = Set.of(
        RegistryAccess.registryAccess().getRegistry(RegistryKey.DATA_COMPONENT_TYPE).get(DataComponentTypeKeys.BLOCK_ENTITY_DATA)
    );

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private EntityLootRegistry entityLootRegistry;
    private static NamespacedKey VANILLA_SPAWNER_KEY;
    private final Map<EntityType, ItemStack> spawnerItemCache = new HashMap<>();
    private final Map<EntityType, Long> cacheTimestamps = new HashMap<>();
    private long lastCacheCleanup = System.currentTimeMillis();

    public SpawnerItemFactory(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.entityLootRegistry = plugin.getEntityLootRegistry();
        VANILLA_SPAWNER_KEY = new NamespacedKey(plugin, "vanilla_spawner");
    }

    public void reload() {
        this.entityLootRegistry = plugin.getEntityLootRegistry();
        clearAllCaches();
    }

    public void clearAllCaches() {
        spawnerItemCache.clear();
        cacheTimestamps.clear();
        lastCacheCleanup = System.currentTimeMillis();
    }

    private void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup < TimeUnit.MINUTES.toMillis(1)) {
            return;
        }
        lastCacheCleanup = currentTime;
        Iterator<Map.Entry<EntityType, Long>> iterator = cacheTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityType, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_EXPIRY_TIME_MS) {
                EntityType type = entry.getKey();
                spawnerItemCache.remove(type);
                iterator.remove();
            }
        }
    }

    private static void hideTooltip(ItemStack item) {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, 
            TooltipDisplay.tooltipDisplay().hiddenComponents(HIDDEN_TOOLTIP_COMPONENTS).build());
    }

    public ItemStack createSmartSpawnerItem(EntityType entityType) {
        return createSmartSpawnerItem(entityType, 1);
    }

    public ItemStack createSmartSpawnerItem(EntityType entityType, int amount) {
        cleanupCacheIfNeeded();
        if (amount == 1) {
            ItemStack cachedItem = spawnerItemCache.get(entityType);
            if (cachedItem != null) {
                return cachedItem.clone();
            }
        }

        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            String entityTypeNameSmallCaps = languageManager.getSmallCaps(entityTypeName);
            EntityLootConfig lootConfig = entityLootRegistry.getLootConfig(entityType);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", entityTypeNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.getExperience() : 0));
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            sortedLootItems.sort(Comparator.comparing(item -> item.getMaterial().name()));
            if (!sortedLootItems.isEmpty()) {
                String lootFormat = languageManager.getItemName("custom_item.spawner.loot_items", placeholders);
                StringBuilder lootItemsBuilder = new StringBuilder();
                for (LootItem item : sortedLootItems) {
                    String itemName = languageManager.getVanillaItemName(item.getMaterial());
                    String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
                    String amountRange = item.getMinAmount() == item.getMaxAmount() ?
                            String.valueOf(item.getMinAmount()) :
                            item.getMinAmount() + "-" + item.getMaxAmount();
                    String chance = String.format("%.1f", item.getChance());
                    Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
                    itemPlaceholders.put("item_name", itemName);
                    itemPlaceholders.put("ɪᴛᴇᴍ_ɴᴀᴍᴇ", itemNameSmallCaps);
                    itemPlaceholders.put("amount", amountRange);
                    itemPlaceholders.put("chance", chance);
                    String formattedItem = languageManager.applyPlaceholdersAndColors(lootFormat, itemPlaceholders);
                    lootItemsBuilder.append(formattedItem).append("\n");
                }
                if (!lootItemsBuilder.isEmpty()) {
                    lootItemsBuilder.setLength(lootItemsBuilder.length() - 1);
                }
                placeholders.put("loot_items", lootItemsBuilder.toString());
            } else {
                placeholders.put("loot_items", languageManager.getItemName("custom_item.spawner.loot_items_empty", placeholders));
            }
            String displayName = languageManager.getItemName("custom_item.spawner.name", placeholders);
            meta.setDisplayName(displayName);
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.spawner.lore", placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            spawner.setItemMeta(meta);
        }
        if (amount == 1) {
            spawnerItemCache.put(entityType, spawner.clone());
            cacheTimestamps.put(entityType, System.currentTimeMillis());
            if (spawnerItemCache.size() > MAX_CACHE_SIZE) {
                EntityType oldestEntity = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<EntityType, Long> entry : cacheTimestamps.entrySet()) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestEntity = entry.getKey();
                    }
                }
                if (oldestEntity != null) {
                    spawnerItemCache.remove(oldestEntity);
                    cacheTimestamps.remove(oldestEntity);
                }
            }
        }
        hideTooltip(spawner);
        return spawner;
    }

    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return createVanillaSpawnerItem(entityType, 1);
    }

    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityTypeName));
            String displayName = languageManager.getItemName("custom_item.vanilla_spawner.name", placeholders);
            if (displayName != null && !displayName.isEmpty() && !displayName.equals("custom_item.vanilla_spawner.name")) {
                meta.setDisplayName(displayName);
            }
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders("custom_item.vanilla_spawner.lore", placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            }
            meta.getPersistentDataContainer().set(
                    VANILLA_SPAWNER_KEY,
                    PersistentDataType.BOOLEAN,
                    true
            );
            spawner.setItemMeta(meta);
        }
        hideTooltip(spawner);
        return spawner;
    }
}