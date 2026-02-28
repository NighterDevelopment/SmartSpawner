package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
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
    @Getter @Setter
    private int baseMaxStoredExp;
    @Getter @Setter
    private int baseMaxStoragePages;
    @Getter @Setter
    private int baseMinMobs;
    @Getter @Setter
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

    // Item spawner support - stores the material being spawned for item spawners
    @Getter @Setter
    private Material spawnedItemMaterial;

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

    @Getter
    private volatile boolean sellValueDirty;

    private SpawnerHologram hologram;
    @Getter @Setter
    private long cachedSpawnDelay;

    // Sort preference for spawner storage
    @Getter @Setter
    private Material preferredSortItem;

    // CRITICAL: Pre-generated loot storage for better UX - access must be synchronized via lootGenerationLock
    private volatile List<ItemStack> preGeneratedItems;
    private volatile int preGeneratedExperience;
    private volatile boolean isPreGenerating;

    // Cache for no-loot detection to avoid repeated expensive checks
    private volatile Boolean cachedHasNoLoot = null;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.spawnedItemMaterial = null;

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    // Constructor for item spawners
    public SpawnerData(String id, Location location, Material itemMaterial, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = EntityType.ITEM;
        this.spawnedItemMaterial = itemMaterial;

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
        this.cachedSpawnDelay = (this.spawnDelay + 20L) * 50L; // Add 1 second buffer for GUI display and convert tick to ms
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range", 16);

        // Load loot config based on spawner type
        if (isItemSpawner() && spawnedItemMaterial != null) {
            this.lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            this.lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
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

    /**
     * Recalculates spawner values after API modifications.
     * Similar to {@link #recalculateAfterConfigReload()} but specifically for API changes.
     */
    public void recalculateAfterAPIModification() {
        calculateStackBasedValues();
        if (virtualInventory != null && virtualInventory.getMaxSlots() != maxSpawnerLootSlots) {
            recreateVirtualInventory();
        }
        updateHologramData();

        // Invalidate GUI cache after API modifications
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
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 500;
        if (baseSpawnerDelay <= 0) {
            plugin.getLogger().warning("Invalid spawner delay value. Setting to default: 500 ticks (25s)");
        }
    }
    public void setSpawnDelayFromConfig() {
        long delay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        if (delay <= 0) {
            plugin.getLogger().warning("Invalid spawner delay value in config. Setting to default: 500 ticks (25s)");
            delay = 500L;
        }
        setSpawnDelay(delay);
    }

    private void initializeComponents() {
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            createHologram();
        }

        if (this.preferredSortItem == null && this.lootConfig != null && this.lootConfig.getAllItems() != null) {
            var lootItems = this.lootConfig.getAllItems();
            if (!lootItems.isEmpty()) {
                var sortedLoot = lootItems.stream()
                        .map(LootItem::material)
                        .distinct()
                        .sorted(Comparator.comparing(Material::name))
                        .toList();

                if (!sortedLoot.isEmpty()) {
                    this.preferredSortItem = sortedLoot.getFirst();
                }
            }
        }
        this.virtualInventory.sortItems(this.preferredSortItem);
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
        calculateStackBasedValues();

        // Resize the existing virtual inventory instead of creating a new one
        virtualInventory.resize(this.maxSpawnerLootSlots);

        // Reset lastSpawnTime to prevent exploit where players break spawners to trigger immediate loot
        this.lastSpawnTime = System.currentTimeMillis();
        updateHologramData();

        // Invalidate GUI cache when stack size changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void recreateVirtualInventory() {
        if (virtualInventory == null) return;
        virtualInventory.resize(maxSpawnerLootSlots);
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
        this.lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(newType);
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
        return lootConfig != null ? lootConfig.experience() : 0;
    }

    /**
     * Checks if this spawner has any configured loot or experience.
     * Used to detect spawners that will never generate anything (like Allay).
     * Result is cached for performance.
     *
     * @return true if spawner has no loot items and no experience configured
     */
    public boolean hasNoLootOrExperience() {
        // Return cached value if available
        if (cachedHasNoLoot != null) {
            return cachedHasNoLoot;
        }

        // Calculate and cache the result
        boolean result = (lootConfig == null ||
                (lootConfig.experience() == 0 && getValidLootItems().isEmpty()));
        cachedHasNoLoot = result;
        return result;
    }

    public void setLootConfig() {
        // Load loot config based on spawner type
        if (isItemSpawner() && spawnedItemMaterial != null) {
            this.lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            this.lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
        // Mark sell value as dirty since prices may have changed
        this.sellValueDirty = true;
        // Invalidate no-loot cache since config changed
        this.cachedHasNoLoot = null;
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
        // Prevent concurrent modification during spawn events to avoid hologram desync
        if (System.currentTimeMillis() - lastSpawnTime < 50) {
            markInteracted();
        }
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
            // Use getTemplateRef() to avoid cloning - we only need to read properties
            ItemStack template = entry.getKey().getTemplateRef();
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
            // Use cached signature to avoid excessive cloning
            VirtualInventory.ItemSignature sig = VirtualInventory.getSignature(item);
            consolidated.merge(sig, (long) item.getAmount(), Long::sum);
        }

        double removedValue = 0.0;
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : consolidated.entrySet()) {
            // Use getTemplateRef() to avoid cloning - we only need to read properties
            ItemStack template = entry.getKey().getTemplateRef();
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
            // Use getTemplateRef() to avoid cloning - we only need to read properties
            ItemStack template = entry.getKey().getTemplateRef();
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
            if (lootItem.sellPrice() > 0.0) {
                ItemStack template = lootItem.createItemStack(new java.util.Random());
                if (template != null) {
                    String key = createItemKey(template);
                    cache.put(key, lootItem.sellPrice());
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
     * THREAD-SAFE: Uses inventoryLock to ensure atomicity
     * @param items Items to add
     */
    public void addItemsAndUpdateSellValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // CRITICAL: Acquire inventoryLock to ensure VirtualInventory remains source of truth
        inventoryLock.lock();
        try {
            // Consolidate items being added for efficient price lookup
            Map<VirtualInventory.ItemSignature, Long> itemsToAdd = new java.util.HashMap<>();
            for (ItemStack item : items) {
                if (item == null || item.getAmount() <= 0) continue;
                // Use cached signature to avoid excessive cloning
                VirtualInventory.ItemSignature sig = VirtualInventory.getSignature(item);
                itemsToAdd.merge(sig, (long) item.getAmount(), Long::sum);
            }

            // Add to VirtualInventory (source of truth) - this operation is atomic within the lock
            virtualInventory.addItems(items);

            // Update sell value atomically
            if (!sellValueDirty) {
                Map<String, Double> priceCache = createPriceCache();
                incrementSellValue(itemsToAdd, priceCache);
            }
        } finally {
            inventoryLock.unlock();
        }
    }

    /**
     * Removes items from virtual inventory and updates accumulated sell value
     * THREAD-SAFE: Uses inventoryLock to ensure atomicity
     * @param items Items to remove
     * @return true if items were removed successfully
     */
    public boolean removeItemsAndUpdateSellValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        // CRITICAL: Acquire inventoryLock to ensure VirtualInventory remains source of truth
        inventoryLock.lock();
        try {
            // Remove from VirtualInventory (source of truth) - atomic operation within lock
            boolean removed = virtualInventory.removeItems(items);

            // Update sell value atomically if removal was successful
            if (removed && !sellValueDirty) {
                Map<String, Double> priceCache = createPriceCache();
                decrementSellValue(items, priceCache);
            }

            return removed;
        } finally {
            inventoryLock.unlock();
        }
    }

    public synchronized void storePreGeneratedLoot(List<ItemStack> items, int experience) {
        this.preGeneratedItems = items;
        this.preGeneratedExperience = experience;
    }

    public synchronized List<ItemStack> getAndClearPreGeneratedItems() {
        List<ItemStack> items = preGeneratedItems;
        preGeneratedItems = null;
        return items;
    }

    public synchronized int getAndClearPreGeneratedExperience() {
        int exp = preGeneratedExperience;
        preGeneratedExperience = 0;
        return exp;
    }

    public synchronized boolean hasPreGeneratedLoot() {
        return (preGeneratedItems != null && !preGeneratedItems.isEmpty()) || preGeneratedExperience > 0;
    }

    public synchronized void setPreGenerating(boolean generating) {
        this.isPreGenerating = generating;
    }

    public synchronized boolean isPreGenerating() {
        return isPreGenerating;
    }

    public synchronized void clearPreGeneratedLoot() {
        preGeneratedItems = null;
        preGeneratedExperience = 0;
        isPreGenerating = false;
    }

    /**
     * Checks if this is an item spawner (spawns items instead of entities)
     * @return true if this spawner spawns items
     */
    public boolean isItemSpawner() {
        return entityType == EntityType.ITEM && spawnedItemMaterial != null;
    }
}
