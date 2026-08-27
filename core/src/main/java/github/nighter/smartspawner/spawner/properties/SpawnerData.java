package github.nighter.smartspawner.spawner.properties;

import com.google.common.util.concurrent.AtomicDouble;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private final ReentrantLock dataLock = new ReentrantLock();  // For metadata changes (exp, stack size, etc.)

    // Atomic sell state – single CAS guard that replaces the old sellLock + double-lock pattern.
    // All operations that touch virtual inventory must check isSelling() before proceeding.
    private final AtomicBoolean selling = new AtomicBoolean(false);

    // Dirty flag for storage GUI – set when items are moved/dropped inside the storage GUI,
    // cleared (and spawner queued for save) when the GUI is closed or main menu is returned to.
    private final AtomicBoolean storageDirty = new AtomicBoolean(false);

    // Monotonic counter bumped on every virtual-inventory mutation (loot, take, sell, drop).
    // Storage views compare against it to decide whether a redraw is needed (version-based sync).
    @Getter
    private final AtomicLong storageVersion = new AtomicLong(0);

    // Phase 4: when the last storage viewer closed. Used as a lazy grace window so a quick reopen
    // keeps the frozen item order, while a reopen after the grace re-sorts. 0 means never emptied.
    private volatile long storageLastEmptyAt = 0L;

    // Base values from config (immutable after load)
    @Getter
    private long baseMaxStoredExp;
    // Per-single-spawner storage capacity, in slots. Scaled by stackSize into maxSpawnerLootSlots.
    // (Replaced baseMaxStoragePages in 1.9.0; 1 page == 45 slots. API pages accessors below convert.)
    @Getter @Setter
    private int baseMaxStorageSlots;
    @Getter @Setter
    private int baseMinMobs;
    @Getter @Setter
    private int baseMaxMobs;

    @Getter
    private long spawnerExp;
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
    private String configName;
    @Getter @Setter
    private EntityLootConfig lootConfig;

    // Item spawner support - stores the material being spawned for item spawners
    @Getter @Setter
    private Material spawnedItemMaterial;

    // Calculated values based on stackSize
    @Getter
    private int maxStoragePages;
    @Getter
    private int maxSpawnerLootSlots;
    @Getter @Setter
    private long maxStoredExp;
    @Getter @Setter
    private int minMobs;
    @Getter @Setter
    private int maxMobs;

    @Getter
    private int stackSize;
    @Getter @Setter
    private int maxStackSize;

    @Getter
    private VirtualInventory virtualInventory;
    @Getter
    private final Set<Material> filteredItems = new HashSet<>();

    @Getter @Setter
    private String lastInteractedPlayer;

    @Getter
    private SellResult lastSellResult;
    @Getter
    private boolean lastSellProcessed;

    // Accumulated sell value for optimization
    private AtomicDouble accumulatedSellValue;

    @Getter
    private volatile boolean sellValueDirty;

    private SpawnerHologram hologram;
    @Getter @Setter
    private long cachedSpawnDelay;

    // Sort preference for spawner storage
    @Getter @Setter
    private Material preferredSortItem;

    // CRITICAL: Pre-generated loot storage for better UX - access must be synchronized via lootGenerationLock
    private volatile Map<ItemSignature, Long> preGeneratedItems;
    private volatile long preGeneratedExperience;
    private volatile boolean isPreGenerating;

    // Cache for no-loot detection to avoid repeated expensive checks
    private volatile Boolean cachedHasNoLoot = null;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this(id, location, type, defaultMobName(plugin, type), plugin);
    }

    public SpawnerData(String id, Location location, EntityType type, String configName, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.configName = configName == null || configName.isBlank()
                ? defaultMobName(plugin, type) : configName;
        this.spawnedItemMaterial = null;

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    // Constructor for item spawners
    public SpawnerData(String id, Location location, Material itemMaterial, SmartSpawner plugin) {
        this(id, location, itemMaterial, defaultItemName(plugin, itemMaterial), plugin);
    }

    public SpawnerData(String id, Location location, Material itemMaterial, String configName, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = EntityType.ITEM;
        this.spawnedItemMaterial = itemMaterial;
        this.configName = configName == null || configName.isBlank()
                ? defaultItemName(plugin, itemMaterial) : configName;

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
        this.accumulatedSellValue = new AtomicDouble(0);
        this.sellValueDirty = true;
    }

    public void loadConfigurationValues() {
        this.baseMaxStoredExp = plugin.getConfig().getLong("spawner_properties.default.max_stored_exp", 1000L);
        this.baseMaxStorageSlots = plugin.getConfig().getInt("spawner_properties.default.max_storage_slots", 45);
        this.baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        this.baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.spawnDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        this.cachedSpawnDelay = (this.spawnDelay + 20L) * 50L; // Add 1 second buffer for GUI display and convert tick to ms
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range", 16);

        // Load loot config based on spawner type
        if (isItemSpawner() && spawnedItemMaterial != null) {
            var definition = plugin.getItemSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
    }

    private static String defaultMobName(SmartSpawner plugin, EntityType type) {
        var definition = plugin.getSpawnerSettingsConfig().getDefaultDefinition(type);
        return definition != null ? definition.name() : type.name().toLowerCase(Locale.ROOT) + "_spawner";
    }

    private static String defaultItemName(SmartSpawner plugin, Material material) {
        var definition = plugin.getItemSpawnerSettingsConfig().getDefaultDefinition(material);
        return definition != null ? definition.name() : material.name().toLowerCase(Locale.ROOT) + "_spawner";
    }

    public void recalculateAfterConfigReload() {
        calculateStackBasedValues();

        // Mark sell value as dirty after config reload since prices may have changed
        this.sellValueDirty = true;
        updateHologramData();

        // Invalidate GUI cache after config reload
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    /**
     * Recalculates spawner values after API modifications.
     * Similar to {@link #recalculateAfterConfigReload()} but specifically for API changes.
     */
    public void recalculateAfterAPIModification() {
        calculateStackBasedValues();

        updateHologramData();

        // Invalidate GUI cache after API modifications
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void calculateStackBasedValues() {
        this.maxStoredExp = clampToLong(baseMaxStoredExp * stackSize, 0L, Long.MAX_VALUE);
        // Capacity is now slots directly (base slots x stack), no longer pages x 45.
        setMaxSpawnerLootSlots(clampToInt((long) baseMaxStorageSlots * stackSize, 0, Integer.MAX_VALUE));
        // maxStoragePages is derived for display only; the source of truth is the slot count.
        this.maxStoragePages = github.nighter.smartspawner.spawner.gui.storage.PageGeometry.pageCount(this.maxSpawnerLootSlots);
        this.minMobs = clampToInt((long) baseMinMobs * stackSize, 0, Integer.MAX_VALUE);
        this.maxMobs = clampToInt((long) baseMaxMobs * stackSize, 0, Integer.MAX_VALUE);
        this.spawnerExp = clampToLong(this.spawnerExp, 0L, this.maxStoredExp);
    }

    /**
     * API compatibility: storage capacity used to be expressed in pages (45 slots each). Addons still
     * call these; they convert to and from the slot-based {@link #baseMaxStorageSlots}. Returns the
     * page-equivalent, rounded up so a partial page still counts.
     */
    public int getBaseMaxStoragePages() {
        return Math.max(1, (baseMaxStorageSlots + 44) / 45);
    }

    /** API compatibility: sets storage capacity from a page count (1 page = 45 slots). */
    public void setBaseMaxStoragePages(int baseMaxStoragePages) {
        this.baseMaxStorageSlots = Math.max(0, baseMaxStoragePages) * 45;
    }

    public void setMaxSpawnerLootSlots(int maxSpawnerLootSlots) {
        this.maxSpawnerLootSlots = Math.max(0, maxSpawnerLootSlots);
        if (virtualInventory != null) {
            virtualInventory.setMaxSlots(this.maxSpawnerLootSlots);
        }
    }

    public void setVirtualInventory(VirtualInventory virtualInventory) {
        this.virtualInventory = virtualInventory;
        if (this.virtualInventory != null) {
            this.virtualInventory.setMaxSlots(this.maxSpawnerLootSlots);
        }
    }

    public void setSpawnDelay(long baseSpawnerDelay) {
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 500;
        long ticksWithBuffer = this.spawnDelay > Long.MAX_VALUE - 20L ? Long.MAX_VALUE : this.spawnDelay + 20L;
        this.cachedSpawnDelay = ticksWithBuffer > Long.MAX_VALUE / 50L ? Long.MAX_VALUE : ticksWithBuffer * 50L;
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

        // Only prevent INCREASING beyond maxStackSize.
        // If the config limit was lowered after a spawner accumulated a higher stack,
        // we must still allow the count to decrease (e.g. on break) to avoid data loss.
        if (newStackSize > this.maxStackSize && newStackSize > this.stackSize) {
            plugin.getLogger().warning("Stack size " + newStackSize + " exceeds maximum " + this.maxStackSize + ". Ignoring.");
            return;
        }

        this.stackSize = newStackSize;
        calculateStackBasedValues();

        // Reset lastSpawnTime to prevent exploit where players break spawners to trigger immediate loot
        this.lastSpawnTime = System.currentTimeMillis();
        updateHologramData();

        // Invalidate GUI cache when stack size changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    public void setSpawnerExp(long exp) {
        this.spawnerExp = Math.clamp(exp, 0L, maxStoredExp);
        updateHologramData();

        // Invalidate GUI cache when experience changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    public void setSpawnerExpData(long exp) {
        this.spawnerExp = Math.max(0L, exp);
    }

    public void setBaseMaxStoredExp(long baseMaxStoredExp) {
        this.baseMaxStoredExp = Math.max(0L, baseMaxStoredExp);
    }

    private int clampToInt(long value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return (int) value;
    }

    // TODO: this does NOT work :cryo:
    private long clampToLong(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public void updateHologramData() {
        if (hologram != null) {
            hologram.updateData(stackSize, entityType, spawnedItemMaterial, spawnerExp, maxStoredExp,
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
        var definition = plugin.getSpawnerSettingsConfig().getDefaultDefinition(newType);
        this.configName = definition != null ? definition.name() : defaultMobName(plugin, newType);
        this.lootConfig = definition != null ? definition.lootConfig()
                : plugin.getSpawnerSettingsConfig().getLootConfig(newType);
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
        return item.isAvailable() && !filteredItems.contains(item.material());
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
            var definition = plugin.getItemSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
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
        this.lastSellResult = null;
    }

    /** @return true if this spawner is currently executing a sell operation */
    public boolean isSelling() {
        return selling.get();
    }

    /**
     * Atomically transitions the spawner into selling state.
     * @return true if the transition succeeded (caller owns the sell), false if already selling
     */
    public boolean startSelling() {
        return selling.compareAndSet(false, true);
    }

    /** Releases the selling state so other operations may proceed. */
    public void stopSelling() {
        selling.set(false);
    }

    /** @return true if the storage GUI content was modified since last save. */
    public boolean isStorageDirty() {
        return storageDirty.get();
    }

    /** Marks that the storage GUI content has been modified and needs to be saved. */
    public void markStorageDirty() {
        storageDirty.set(true);
    }

    /** Clears the storage dirty flag after the spawner has been queued for saving. */
    public void clearStorageDirty() {
        storageDirty.set(false);
    }

    public void updateLastInteractedPlayer(String playerName) {
        this.lastInteractedPlayer = playerName;
    }

    /**
     * Marks the sell value as dirty, requiring recalculation
     */
    public void markSellValueDirty() {
        this.sellValueDirty = true;
    }

    public double getAccumulatedSellValue() {
        return accumulatedSellValue.get();
    }

    /**
     * Updates the accumulated sell value for specific items being added
     * @param itemsAdded Map of item signatures to quantities added
     * @param priceCache Price cache from loot config
     */
    public void incrementSellValue(Map<ItemSignature, Long> itemsAdded, Map<String, Double> priceCache) {
        if (itemsAdded == null || itemsAdded.isEmpty()) {
            return;
        }

        double addedValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : itemsAdded.entrySet()) {
            double itemPrice = findItemPrice(entry.getKey(), priceCache);
            if (itemPrice > 0.0) {
                addedValue += itemPrice * entry.getValue();
            }
        }

        if (addedValue > 0.0) {
            this.accumulatedSellValue.addAndGet(addedValue);
        }
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

        Map<ItemSignature, Long> consolidated = new java.util.HashMap<>();
        for (ItemStack item : itemsRemoved) {
            if (item == null || item.getAmount() <= 0) continue;
            ItemSignature sig = VirtualInventory.getSignature(item);
            consolidated.merge(sig, (long) item.getAmount(), Long::sum);
        }

        decrementSellValue(consolidated, priceCache);
    }

    /**
     * Decrements the accumulated sell value when already-consolidated items are removed.
     * @param itemsRemoved Map of item signatures to quantities removed
     * @param priceCache Price cache from loot config
     */
    public void decrementSellValue(Map<ItemSignature, Long> itemsRemoved, Map<String, Double> priceCache) {
        if (itemsRemoved == null || itemsRemoved.isEmpty()) {
            return;
        }

        double removedValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : itemsRemoved.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            double itemPrice = findItemPrice(entry.getKey(), priceCache);
            if (itemPrice > 0.0) {
                removedValue += itemPrice * entry.getValue().longValue();
            }
        }

        subtractAccumulatedSellValue(removedValue);
    }

    /**
     * Forces a full recalculation of the accumulated sell value
     * Should be called when the cache is dirty or on spawner load
     */
    public void recalculateSellValue() {
        if (lootConfig == null) {
            this.accumulatedSellValue.set(0.0);
            this.sellValueDirty = false;
            return;
        }

        // Get price cache
        Map<String, Double> priceCache = createPriceCache();

        // Calculate from current inventory
        Map<ItemSignature, Long> items = virtualInventory.getConsolidatedItems();
        double totalValue = 0.0;

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            double itemPrice = findItemPrice(entry.getKey(), priceCache);
            if (itemPrice > 0.0) {
                totalValue += itemPrice * entry.getValue().longValue();
            }
        }

        this.accumulatedSellValue.set(totalValue);
        this.sellValueDirty = false;
    }

    private void subtractAccumulatedSellValue(double removedValue) {
        if (removedValue <= 0.0) {
            return;
        }

        double current;
        double updated;
        do {
            current = accumulatedSellValue.get();
            updated = Math.max(0.0, current - removedValue);
        } while (!accumulatedSellValue.compareAndSet(current, updated));
    }

    /**
     * Gets the price cache from loot config.
     * Prefers live prices from ItemPriceManager to avoid startup timing issues where
     * shop plugin prices aren't yet available when LootItem.sellPrice is baked in.
     */
    public Map<String, Double> createPriceCache() {
        if (lootConfig == null) {
            return new java.util.HashMap<>();
        }

        github.nighter.smartspawner.hooks.economy.ItemPriceManager priceManager = plugin.getItemPriceManager();
        Map<String, Double> cache = new java.util.HashMap<>();
        java.util.List<LootItem> allLootItems = lootConfig.getAllItems();

        for (LootItem lootItem : allLootItems) {
            // Use live price from ItemPriceManager; fall back to baked sellPrice if unavailable
            double price = (priceManager != null) ? priceManager.getPrice(lootItem.material()) : 0.0;
            if (price <= 0.0) {
                price = lootItem.sellPrice();
            }
            if (price > 0.0) {
                ItemStack template = lootItem.createItemStack();
                if (template != null) {
                    String key = createItemKey(template);
                    cache.put(key, price);
                }
            }
        }

        return cache;
    }

    /**
     * Finds item price using the cache
     */
    private double findItemPrice(ItemSignature itemSignature, Map<String, Double> priceCache) {
        if (priceCache == null) {
            return 0.0;
        }
        String itemKey = createItemKey(itemSignature);
        Double price = priceCache.get(itemKey);
        return price != null ? price : 0.0;
    }

    /**
     * Convenience overload
     */
    private String createItemKey(ItemStack itemStack) {
        if (itemStack == null) return "null";

        return createItemKey(new ItemSignature(itemStack));
    }

    /**
     * Creates a unique key for an item (same logic as SpawnerSellManager)
     * TODO: this feels very wrong ngl
     */
    private String createItemKey(ItemSignature itemSignature) {
        StringBuilder key = new StringBuilder();
        key.append(itemSignature.getMaterial().name());

        // Add enchantments if present
        ItemMeta meta = itemSignature.getUnsafeTemplateRef().getItemMeta(); // Read-only
        if (itemSignature.hasItemMeta() && meta.hasEnchants()) {
            key.append("_enchants:");
            meta.getEnchants().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString())))
                    .forEach(entry -> key.append(entry.getKey().getKey()).append(":").append(entry.getValue()).append(","));
        }

        // Add display name if present
        if (itemSignature.hasItemMeta() && meta.hasDisplayName()) {
            key.append("_name:").append(meta.displayName());
        }

        return key.toString();
    }

    /**
     * Adds already-consolidated items to virtual inventory and updates accumulated sell value.
     * THREAD-SAFE: Uses inventoryLock to ensure atomicity.
     * @param items Items to add, keyed by the same signature used by VirtualInventory
     */
    public void addItemsAndUpdateSellValue(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // CRITICAL: Acquire inventoryLock to ensure VirtualInventory remains source of truth
        inventoryLock.lock();
        try {
            virtualInventory.addItems(items);

            // Update sell value atomically
            if (!sellValueDirty) {
                Map<String, Double> priceCache = createPriceCache();
                incrementSellValue(items, priceCache);
            }
            storageVersion.incrementAndGet();
        } finally {
            inventoryLock.unlock();
        }
    }

    /**
     * Transactional take: atomically removes up to {@code desired} of each signature from the
     * virtual inventory (the single source of truth), decrements the accumulated sell value,
     * bumps {@link #storageVersion}, and returns exactly what was removed.
     *
     * <p>This is the dupe-safe primitive for player-driven takes (take-1, take-all, drop-page):
     * callers compute how much they can accept, hand that in as {@code desired}, and give the
     * player back only what this method reports as removed. Two players clicking at once are
     * serialized on {@code inventoryLock}; the second sees a stale view and simply gets less or
     * nothing, so a stale GUI is no longer a dupe path.
     *
     * @param desired signature to requested amount
     * @return signature to amount actually removed; empty when selling or nothing available
     */
    public Map<ItemSignature, Long> takeItems(Map<ItemSignature, Long> desired) {
        if (desired == null || desired.isEmpty() || isSelling()) {
            return Collections.emptyMap();
        }

        inventoryLock.lock();
        try {
            Map<ItemSignature, Long> removed = virtualInventory.removeUpTo(desired);
            if (!removed.isEmpty()) {
                if (!sellValueDirty) {
                    Map<String, Double> priceCache = createPriceCache();
                    decrementSellValue(removed, priceCache);
                }
                storageVersion.incrementAndGet();
            }
            return removed;
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

        Map<ItemSignature, Long> itemsToRemove = new java.util.HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            ItemSignature sig = VirtualInventory.getSignature(item);
            itemsToRemove.merge(sig, (long) item.getAmount(), Long::sum);
        }

        return removeItemsAndUpdateSellValue(itemsToRemove);
    }

    /**
     * Removes already-consolidated items from virtual inventory and updates accumulated sell value.
     * THREAD-SAFE: Uses inventoryLock to ensure atomicity.
     * @param items Items to remove, keyed by the same signature used by VirtualInventory
     * @return true if items were removed successfully
     */
    public boolean removeItemsAndUpdateSellValue(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        inventoryLock.lock();
        try {
            // Remove from VirtualInventory (source of truth) - atomic operation within lock
            boolean removed = virtualInventory.removeItems(items);

            // Update sell value atomically if removal was successful
            if (removed && !sellValueDirty) {
                Map<String, Double> priceCache = createPriceCache();
                decrementSellValue(items, priceCache);
            }
            if (removed) {
                storageVersion.incrementAndGet();
            }

            return removed;
        } finally {
            inventoryLock.unlock();
        }
    }

    // ============== Phase 4: frozen storage display order ==============

    /** @return timestamp (ms) the last storage viewer closed, or 0 if never. */
    public long getStorageLastEmptyAt() {
        return storageLastEmptyAt;
    }

    /** Records that the last storage viewer just closed, starting the reorder grace window. */
    public void markStorageEmptyNow() {
        this.storageLastEmptyAt = System.currentTimeMillis();
    }

    /** @return true if the storage display order is currently pinned. */
    public boolean isStorageOrderFrozen() {
        return virtualInventory != null && virtualInventory.isOrderFrozen();
    }

    /**
     * Pins the current storage order for the first viewer. Re-sorts first when {@code resort} is
     * true (grace elapsed or never frozen), otherwise keeps the previous order for a fast reopen.
     */
    public void freezeStorageOrder(boolean resort) {
        inventoryLock.lock();
        try {
            if (resort) {
                virtualInventory.unfreezeOrder();
            }
            virtualInventory.freezeOrder();
        } finally {
            inventoryLock.unlock();
        }
    }

    /**
     * Applies a new sort preference while a viewer is present: re-sorts and re-pins the order so the
     * change is visible immediately, and bumps the version so other viewers redraw.
     */
    public void applySortPreference(Material sort) {
        this.preferredSortItem = sort;
        inventoryLock.lock();
        try {
            virtualInventory.unfreezeOrder();
            virtualInventory.sortItems(sort);
            virtualInventory.freezeOrder();
        } finally {
            inventoryLock.unlock();
        }
        storageVersion.incrementAndGet();
    }

    public synchronized void storePreGeneratedLoot(Map<ItemSignature, Long> items, long experience) {
        this.preGeneratedItems = items;
        this.preGeneratedExperience = experience;
    }

    public synchronized Map<ItemSignature, Long> getAndClearPreGeneratedItems() {
        Map<ItemSignature, Long> items = preGeneratedItems;
        preGeneratedItems = null;
        return items;
    }

    public synchronized long getAndClearPreGeneratedExperience() {
        long exp = preGeneratedExperience;
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
