package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.spawner.sell.SellResult;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class SpawnerData {
    @Getter
    private final SmartSpawner plugin;
    private final EntityLootRegistry lootRegistry;

    @Getter @Setter
    private String spawnerId;
    @Getter
    private final Location spawnerLocation;
    
    // Fine-grained locks for different operations (Lock Striping Pattern)
    @Getter
    private final ReentrantLock inventoryLock = new ReentrantLock();  // For storage operations
    @Getter
    private final ReentrantLock lootGenerationLock = new ReentrantLock();  // For loot spawning
    @Getter
    private final ReentrantLock sellLock = new ReentrantLock();  // For selling operations
    @Getter
    private final ReentrantLock dataLock = new ReentrantLock();  // For metadata changes (exp, stack size, etc.)

    // Base values from config (immutable after load)
    private int baseMaxStoredExp;
    private int baseMaxStoragePages;
    private int baseMinMobs;
    private int baseMaxMobs;

    @Getter
    private Integer spawnerExp;
    @Getter @Setter
    private Boolean spawnerActive;
    @Getter @Setter
    private Integer spawnerRange;
    @Getter
    private AtomicBoolean spawnerStop;
    @Getter @Setter
    private Boolean isAtCapacity;
    @Getter @Setter
    private Long lastSpawnTime;
    @Getter
    private long spawnDelay;

    @Getter
    private EntityType entityType;
    @Getter @Setter
    private EntityLootConfig lootConfig;

    // Calculated values based on stackSize
    @Getter
    private int maxStoragePages;
    @Getter @Setter
    private int maxSpawnerLootSlots;
    @Getter @Setter
    private int maxStoredExp;
    @Getter @Setter
    private int minMobs;
    @Getter @Setter
    private int maxMobs;

    @Getter
    private int stackSize;
    @Getter @Setter
    private int maxStackSize;

    @Getter @Setter
    private VirtualInventory virtualInventory;
    @Getter
    private final Set<Material> filteredItems = new HashSet<>();

    private final AtomicBoolean interacted = new AtomicBoolean(false);
    @Getter @Setter
    private String lastInteractedPlayer;

    @Getter
    private SellResult lastSellResult;
    @Getter
    private boolean lastSellProcessed;

    // Accumulated sell value for optimization
    @Getter
    private volatile double accumulatedSellValue;
    /**
     * -- GETTER --
     *  Checks if sell value needs recalculation
     */
    @Getter
    private volatile boolean sellValueDirty;

    private SpawnerHologram hologram;
    @Getter @Setter
    private long cachedSpawnDelay = 0;

    // Sort preference for spawner storage
    @Getter @Setter
    private Material preferredSortItem;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.lootRegistry = plugin.getEntityLootRegistry();

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    private void initializeDefaults() {
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = new AtomicBoolean(true);
        this.isAtCapacity = false;
        this.stackSize = 1;
        this.lastSpawnTime = System.currentTimeMillis();
        this.preferredSortItem = null; // Initialize sort preference as null
        this.accumulatedSellValue = 0.0;
        this.sellValueDirty = true;
    }

    public void loadConfigurationValues() {
        this.baseMaxStoredExp = plugin.getConfig().getInt("spawner_properties.default.max_stored_exp", 1000);
        this.baseMaxStoragePages = plugin.getConfig().getInt("spawner_properties.default.max_storage_pages", 1);
        this.baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        this.baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.spawnDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range", 16);
        this.lootConfig = lootRegistry.getLootConfig(entityType);
    }

    public void recalculateAfterConfigReload() {
        calculateStackBasedValues();
        if (virtualInventory != null && virtualInventory.getMaxSlots() != maxSpawnerLootSlots) {
            recreateVirtualInventory();
        }
        // Mark sell value as dirty after config reload since prices may have changed
        this.sellValueDirty = true;
        updateHologramData();

        // Invalidate GUI cache after config reload
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void calculateStackBasedValues() {
        this.maxStoredExp = baseMaxStoredExp * stackSize;
        this.maxStoragePages = baseMaxStoragePages * stackSize;
        this.maxSpawnerLootSlots = maxStoragePages * 45;
        this.minMobs = baseMinMobs * stackSize;
        this.maxMobs = baseMaxMobs * stackSize;
        this.spawnerExp = Math.min(this.spawnerExp, this.maxStoredExp);
    }

    public void setSpawnDelay(long baseSpawnerDelay) {
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 400;
        if (baseSpawnerDelay <= 0) {
            plugin.getLogger().warning("Invalid delay value. Setting to default: 400");
        }
    }

    private void initializeComponents() {
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            createHologram();
        }
    }

    private void createHologram() {
        this.hologram = new SpawnerHologram(spawnerLocation);
        this.hologram.createHologram();
        updateHologramData();
    }

    public void setStackSize(int stackSize) {
        setStackSize(stackSize, true);
    }

    public void setStackSize(int stackSize, boolean restartHopper) {
        // Acquire locks in consistent order to prevent deadlocks:
        // 1. dataLock - for metadata changes
        // 2. inventoryLock - to prevent inventory operations during virtual inventory replacement
        // Note: We don't acquire lootGenerationLock here to avoid blocking loot generation cycles
        dataLock.lock();
        try {
            inventoryLock.lock();
            try {
                updateStackSize(stackSize, restartHopper);
            } finally {
                inventoryLock.unlock();
            }
        } finally {
            dataLock.unlock();
        }
    }

    private void updateStackSize(int newStackSize, boolean restartHopper) {
        if (newStackSize <= 0) {
            this.stackSize = 1;
            plugin.getLogger().warning("Invalid stack size. Setting to 1");
            return;
        }

        if (newStackSize > this.maxStackSize) {
            this.stackSize = this.maxStackSize;
            plugin.getLogger().warning("Stack size exceeds maximum. Setting to " + this.stackSize);
            return;
        }

        this.stackSize = newStackSize;
        Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

        calculateStackBasedValues();

        VirtualInventory newInventory = new VirtualInventory(this.maxSpawnerLootSlots);
        transferItemsToNewInventory(currentItems, newInventory);
        this.virtualInventory = newInventory;

        updateHologramData();

        // Invalidate GUI cache when stack size changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }

        // Restart hopper task if hopper integration is enabled
        // This ensures hopper continues to work after stack size changes
        // Skip during batch loading to avoid performance bottleneck
        if (restartHopper && plugin.getHopperHandler() != null) {
            plugin.getHopperHandler().restartHopperForSpawner(this.spawnerLocation);
        }
    }

    private void recreateVirtualInventory() {
        if (virtualInventory == null) return;

        Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();
        VirtualInventory newInventory = new VirtualInventory(maxSpawnerLootSlots);
        transferItemsToNewInventory(currentItems, newInventory);
        this.virtualInventory = newInventory;
    }

    private void transferItemsToNewInventory(Map<VirtualInventory.ItemSignature, Long> items,
                                             VirtualInventory newInventory) {
        List<ItemStack> itemsToTransfer = new ArrayList<>();

        items.forEach((signature, amount) -> {
            ItemStack template = signature.getTemplate();
            while (amount > 0) {
                int batchSize = (int) Math.min(amount, Integer.MAX_VALUE);
                ItemStack batch = template.clone();
                batch.setAmount(batchSize);
                itemsToTransfer.add(batch);
                amount -= batchSize;
            }
        });

        newInventory.addItems(itemsToTransfer);
    }

    public void setSpawnerExp(int exp) {
        this.spawnerExp = Math.min(Math.max(0, exp), maxStoredExp);
        updateHologramData();

        // Invalidate GUI cache when experience changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    public void setSpawnerExpData(int exp) {
        this.spawnerExp = exp;
    }

    public void updateHologramData() {
        if (hologram != null) {
            hologram.updateData(stackSize, entityType, spawnerExp, maxStoredExp,
                    virtualInventory.getUsedSlots(), maxSpawnerLootSlots);
        }
    }

    public void reloadHologramData() {
        if (hologram != null) {
            hologram.remove();
            createHologram();
        }
    }

    public void refreshHologram() {
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            if (hologram == null) {
                createHologram();
            }
        } else if (hologram != null) {
            removeHologram();
        }
    }

    public void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    public boolean isCompletelyFull() {
        return virtualInventory.getUsedSlots() >= maxSpawnerLootSlots && spawnerExp >= maxStoredExp;
    }

    public boolean updateCapacityStatus() {
        boolean newStatus = isCompletelyFull();
        if (newStatus != isAtCapacity) {
            isAtCapacity = newStatus;
            return true;
        }
        return false;
    }

    public void setEntityType(EntityType newType) {
        this.entityType = newType;
        this.lootConfig = lootRegistry.getLootConfig(newType);
        // Mark sell value as dirty since entity type and prices changed
        this.sellValueDirty = true;
        updateHologramData();
    }

    public boolean toggleItemFilter(Material material) {
        boolean wasFiltered = filteredItems.contains(material);
        if (wasFiltered) {
            filteredItems.remove(material);
        } else {
            filteredItems.add(material);
        }
        return !wasFiltered;
    }

    public List<LootItem> getValidLootItems() {
        if (lootConfig == null) {
            return Collections.emptyList();
        }
        return lootConfig.getAllItems().stream()
                .filter(this::isLootItemValid)
                .collect(Collectors.toList());
    }

    private boolean isLootItemValid(LootItem item) {
        ItemStack example = item.createItemStack(new Random());
        return example != null && !filteredItems.contains(example.getType());
    }

    public int getEntityExperienceValue() {
        return lootConfig != null ? lootConfig.getExperience() : 0;
    }

    public void setLootConfig() {
        this.lootConfig = lootRegistry.getLootConfig(entityType);
        // Mark sell value as dirty since prices may have changed
        this.sellValueDirty = true;
    }

    public void setLastSellResult(SellResult sellResult) {
        this.lastSellResult = sellResult;
        this.lastSellProcessed = false;
    }

    public void markLastSellAsProcessed() {
        this.lastSellProcessed = true;
    }

    public boolean isInteracted() {
        return interacted.get();
    }

    public void markInteracted() {
        interacted.compareAndSet(false, true);
    }

    public void clearInteracted() {
        interacted.compareAndSet(true, false);
    }

    public void updateLastInteractedPlayer(String playerName) {
        this.lastInteractedPlayer = playerName;
        markInteracted();
    }

    /**
     * Marks the sell value as dirty, requiring recalculation
     */
    public void markSellValueDirty() {
        this.sellValueDirty = true;
    }

    /**
     * Updates the accumulated sell value for specific items being added
     * @param itemsAdded Map of item signatures to quantities added
     * @param priceCache Price cache from loot config
     */
    public void incrementSellValue(Map<VirtualInventory.ItemSignature, Long> itemsAdded,
                                   Map<String, Double> priceCache) {
        if (itemsAdded == null || itemsAdded.isEmpty()) {
            return;
        }

        double addedValue = 0.0;
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : itemsAdded.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();
            double itemPrice = findItemPrice(template, priceCache);
            if (itemPrice > 0.0) {
                addedValue += itemPrice * amount;
            }
        }

        this.accumulatedSellValue += addedValue;
        this.sellValueDirty = false;
    }

    /**
     * Decrements the accumulated sell value when items are removed
     * @param itemsRemoved List of items removed
     * @param priceCache Price cache from loot config
     */
    public void decrementSellValue(List<ItemStack> itemsRemoved, Map<String, Double> priceCache) {
        if (itemsRemoved == null || itemsRemoved.isEmpty()) {
            return;
        }

        // Consolidate removed items
        Map<VirtualInventory.ItemSignature, Long> consolidated = new java.util.HashMap<>();
        for (ItemStack item : itemsRemoved) {
            if (item == null || item.getAmount() <= 0) continue;
            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            consolidated.merge(sig, (long) item.getAmount(), Long::sum);
        }

        double removedValue = 0.0;
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : consolidated.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();
            double itemPrice = findItemPrice(template, priceCache);
            if (itemPrice > 0.0) {
                removedValue += itemPrice * amount;
            }
        }

        this.accumulatedSellValue = Math.max(0.0, this.accumulatedSellValue - removedValue);
    }

    /**
     * Forces a full recalculation of the accumulated sell value
     * Should be called when the cache is dirty or on spawner load
     */
    public void recalculateSellValue() {
        if (lootConfig == null) {
            this.accumulatedSellValue = 0.0;
            this.sellValueDirty = false;
            return;
        }

        // Get price cache
        Map<String, Double> priceCache = createPriceCache();

        // Calculate from current inventory
        Map<VirtualInventory.ItemSignature, Long> items = virtualInventory.getConsolidatedItems();
        double totalValue = 0.0;

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();
            double itemPrice = findItemPrice(template, priceCache);
            if (itemPrice > 0.0) {
                totalValue += itemPrice * amount;
            }
        }

        this.accumulatedSellValue = totalValue;
        this.sellValueDirty = false;
    }

    /**
     * Gets the price cache from loot config
     */
    public Map<String, Double> createPriceCache() {
        if (lootConfig == null) {
            return new java.util.HashMap<>();
        }

        Map<String, Double> cache = new java.util.HashMap<>();
        java.util.List<LootItem> allLootItems = lootConfig.getAllItems();

        for (LootItem lootItem : allLootItems) {
            if (lootItem.getSellPrice() > 0.0) {
                ItemStack template = lootItem.createItemStack(new java.util.Random());
                if (template != null) {
                    String key = createItemKey(template);
                    cache.put(key, lootItem.getSellPrice());
                }
            }
        }

        return cache;
    }

    /**
     * Finds item price using the cache
     */
    private double findItemPrice(ItemStack item, Map<String, Double> priceCache) {
        if (item == null || priceCache == null) {
            return 0.0;
        }
        String itemKey = createItemKey(item);
        Double price = priceCache.get(itemKey);
        return price != null ? price : 0.0;
    }

    /**
     * Creates a unique key for an item (same logic as SpawnerSellManager)
     */
    private String createItemKey(ItemStack item) {
        if (item == null) {
            return "null";
        }

        StringBuilder key = new StringBuilder();
        key.append(item.getType().name());

        // Add enchantments if present
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            key.append("_enchants:");
            item.getItemMeta().getEnchants().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString())))
                    .forEach(entry -> key.append(entry.getKey().getKey()).append(":").append(entry.getValue()).append(","));
        }

        // Add custom model data if present
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            key.append("_cmd:").append(item.getItemMeta().getCustomModelData());
        }

        // Add display name if present
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            key.append("_name:").append(item.getItemMeta().getDisplayName());
        }

        return key.toString();
    }

    /**
     * Adds items to virtual inventory and updates accumulated sell value
     * This is the preferred method to add items to maintain accurate sell value cache
     * @param items Items to add
     */
    public void addItemsAndUpdateSellValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // Consolidate items being added for efficient price lookup
        Map<VirtualInventory.ItemSignature, Long> itemsToAdd = new java.util.HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            itemsToAdd.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Add to inventory
        virtualInventory.addItems(items);

        // Update sell value
        if (!sellValueDirty) {
            Map<String, Double> priceCache = createPriceCache();
            incrementSellValue(itemsToAdd, priceCache);
        }
    }

    /**
     * Removes items from virtual inventory and updates accumulated sell value
     * @param items Items to remove
     * @return true if items were removed successfully
     */
    public boolean removeItemsAndUpdateSellValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        // Remove from inventory
        boolean removed = virtualInventory.removeItems(items);

        // Update sell value if removal was successful
        if (removed && !sellValueDirty) {
            Map<String, Double> priceCache = createPriceCache();
            decrementSellValue(items, priceCache);
        }

        return removed;
    }
}
