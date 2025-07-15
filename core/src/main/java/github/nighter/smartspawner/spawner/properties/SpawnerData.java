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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpawnerData {
    private static final Logger logger = Logger.getLogger("SmartSpawnerConfig");

    // Core dependencies
    @Getter
    private final SmartSpawner plugin;
    private final EntityLootRegistry lootRegistry;

    // Immutable properties
    @Getter
    private final String spawnerId;
    @Getter
    private final Location spawnerLocation;
    @Getter
    private final ReentrantLock lock = new ReentrantLock();

    // Basic spawner properties
    @Getter
    private Integer spawnerExp;
    @Getter @Setter
    private Boolean spawnerActive;
    @Getter @Setter
    private Integer spawnerRange;
    @Getter @Setter
    private Boolean spawnerStop;
    @Getter @Setter
    private Boolean isAtCapacity;
    @Getter @Setter
    private Long lastSpawnTime;
    @Getter
    private long spawnDelay;

    // Entity and loot configuration
    @Getter
    private EntityType entityType;
    @Getter @Setter
    private EntityLootConfig lootConfig;

    // Capacity limits
    @Getter @Setter
    private int maxStoragePages;
    @Getter @Setter
    private int maxSpawnerLootSlots;
    @Getter @Setter
    private int maxStoredExp;
    @Getter @Setter
    private int minMobs;
    @Getter @Setter
    private int maxMobs;

    // Stack management
    @Getter
    private int stackSize;
    @Getter @Setter
    private int maxStackSize;

    // Storage and filtering
    @Getter @Setter
    private VirtualInventory virtualInventory;
    @Getter
    private final Set<Material> filteredItems = new HashSet<>();

    // Sales management
    @Getter
    private SellResult lastSellResult;
    @Getter
    private boolean lastSellProcessed;

    // Display components
    private SpawnerHologram hologram;
    @Getter @Setter
    private long cachedSpawnDelay = 0;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.lootRegistry = plugin.getEntityLootRegistry();

        initializeDefaults();
        loadConfigurationValues();
        initializeComponents();
    }

    private void initializeDefaults() {
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = true;
        this.isAtCapacity = false;
        this.stackSize = 1;
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.maxStoragePages = plugin.getConfig().getInt("spawner_properties.default.max_storage_pages", 1);
        this.maxSpawnerLootSlots = 45;
        this.lootConfig = lootRegistry.getLootConfig(entityType);
    }

    private void loadConfigurationValues() {
        int baseMaxStoredExp = plugin.getConfig().getInt("spawner_properties.default.max_stored_exp", 1000);
        int baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        int baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        long baseSpawnerDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");

        validateAndSetStoragePages();
        calculateScaledValues(baseMaxStoredExp, baseMinMobs, baseMaxMobs);
        setSpawnDelay(baseSpawnerDelay);
        setSpawnerRange();
    }

    private void validateAndSetStoragePages() {
        if (maxStoragePages <= 0) {
            logger.warning("Invalid max_storage_pages value. Setting to default: 1");
            maxStoragePages = 1;
        }
        this.maxSpawnerLootSlots = Math.max((45 * maxStoragePages) * stackSize, 45);
    }

    private void calculateScaledValues(int baseMaxStoredExp, int baseMinMobs, int baseMaxMobs) {
        this.maxStoredExp = Math.max(baseMaxStoredExp * stackSize, baseMaxStoredExp);
        this.minMobs = Math.max(baseMinMobs * stackSize, baseMinMobs);
        this.maxMobs = Math.max(baseMaxMobs * stackSize, this.minMobs + stackSize);

        if (this.maxMobs <= this.minMobs) {
            logger.warning("Invalid max_mobs value after scaling. Adjusting to: " + (this.minMobs + stackSize));
            this.maxMobs = this.minMobs + stackSize;
        }
    }

    public void setSpawnDelay(long baseSpawnerDelay) {
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 400;
        if (baseSpawnerDelay <= 0) {
            logger.warning("Invalid delay value. Setting to default: 400");
        }
    }

    private void setSpawnerRange() {
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range");
        if (this.spawnerRange <= 0) {
            logger.warning("Invalid range value. Setting to default: 16");
            this.spawnerRange = 16;
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
        lock.lock();
        try {
            updateStackSize(stackSize);
        } finally {
            lock.unlock();
        }
    }

    private void updateStackSize(int newStackSize) {
        if (newStackSize <= 0) {
            this.stackSize = 1;
            logger.warning("Invalid stack size. Setting to 1");
            return;
        }

        if (newStackSize > this.maxStackSize) {
            this.stackSize = this.maxStackSize;
            logger.warning("Stack size exceeds maximum. Setting to " + this.stackSize);
            return;
        }

        Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();
        int newMaxSlots = (45 * this.maxStoragePages) * newStackSize;
        VirtualInventory newInventory = new VirtualInventory(newMaxSlots);

        transferItemsToNewInventory(currentItems, newInventory);

        this.stackSize = newStackSize;
        loadConfigurationValues();
        this.spawnerExp = Math.min(this.spawnerExp, this.maxStoredExp);
        this.lastSpawnTime = System.currentTimeMillis() + this.spawnDelay;
        this.virtualInventory = newInventory;

        updateHologramData();
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

    public void decreaseStackSizeByOne() {
        this.stackSize = Math.max(1, this.stackSize - 1);
        updateHologramData();
    }

    public void setSpawnerExp(int exp) {
        this.spawnerExp = Math.min(Math.max(0, exp), maxStoredExp);
        updateHologramData();
    }

    public void setSpawnerExpData(int exp) {
        this.spawnerExp = exp;
    }

    public Map<Integer, ItemStack> getDisplayInventory() {
        return virtualInventory.getDisplayInventory();
    }

    public void updateHologramData() {
        if (hologram != null) {
            hologram.updateData(
                    stackSize,
                    entityType,
                    spawnerExp,
                    maxStoredExp,
                    virtualInventory.getUsedSlots(),
                    maxSpawnerLootSlots
            );
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

    public void reloadLootConfig() {
        this.lootConfig = lootRegistry.getLootConfig(entityType);
    }

    public void setLastSellResult(SellResult sellResult) {
        this.lastSellResult = sellResult;
        this.lastSellProcessed = false;
    }

    public void markLastSellAsProcessed() {
        this.lastSellProcessed = true;
    }

    /**
     * Check if items should be automatically dropped to the ground instead of stored
     * @return true if auto drop to ground is enabled
     */
    public boolean getAutoDropItemsToGround() {
        return plugin.getConfig().getBoolean("spawner_properties.default.auto_drop_items_to_ground", false);
    }

    /**
     * Check if the storage system is completely disabled
     * @return true if storage is disabled
     */
    public boolean getDisableStorage() {
        return plugin.getConfig().getBoolean("spawner_properties.default.disable_storage", false);
    }
}