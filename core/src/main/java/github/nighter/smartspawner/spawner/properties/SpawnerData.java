package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.loot.LootItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class SpawnerData {
    private static final Logger logger = Logger.getLogger("SmartSpawnerConfig");

    private final SmartSpawner plugin;
    private final EntityLootRegistry lootRegistry;

    @Getter private final String spawnerId;
    @Getter private final Location spawnerLocation;
    @Getter private final ReentrantLock lock = new ReentrantLock();

    @Getter private Integer spawnerExp;
    @Getter @Setter private Boolean spawnerActive;
    @Getter @Setter private Integer spawnerRange;
    @Getter @Setter private Boolean spawnerStop;
    @Getter @Setter private Boolean isAtCapacity;
    @Getter @Setter private Long lastSpawnTime;
    @Getter @Setter private long spawnDelay;
    @Getter private EntityType entityType;
    @Getter @Setter private EntityLootConfig lootConfig;
    @Getter @Setter private int maxStoragePages;
    @Getter @Setter private int maxSpawnerLootSlots;
    @Getter @Setter private int maxStoredExp;
    @Getter @Setter private int minMobs;
    @Getter @Setter private int maxMobs;
    @Getter private int stackSize;
    @Getter @Setter private int maxStackSize;

    @Getter @Setter private VirtualInventory virtualInventory;
    @Getter private final Set<Material> filteredItems = new HashSet<>();

    private SpawnerHologram hologram;
    @Getter @Setter private long cachedSpawnDelay = 0;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = true;
        this.isAtCapacity = false;
        this.stackSize = 1;
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.maxStoragePages = plugin.getConfig().getInt("spawner_properties.default.max_storage_pages", 1);
        this.maxSpawnerLootSlots = 45;
        this.lootRegistry = plugin.getEntityLootRegistry();
        // Initialize loot config based on entity type
        this.lootConfig = lootRegistry.getLootConfig(entityType);
        loadConfigValues();
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            this.hologram = new SpawnerHologram(location);
            this.hologram.createHologram();
            updateHologramData();
        }
    }

    private void loadConfigValues() {
        int baseMaxStoredExp = plugin.getConfig().getInt("spawner_properties.default.max_stored_exp", 1000);
        int baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        int baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        long baseSpawnerDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        int maxStoragePages = plugin.getConfig().getInt("spawner_properties.default.max_storage_pages", 1);

        if (maxStoragePages <= 0) {
            logger.warning("Invalid max_storage_pages value. Setting to default: 1");
            maxStoragePages = 1;
        }

        this.maxSpawnerLootSlots = (45 * maxStoragePages) * stackSize;
        if (this.maxSpawnerLootSlots < 0) this.maxSpawnerLootSlots = 45;

        this.maxStoredExp = baseMaxStoredExp * stackSize;
        if (this.maxStoredExp <= 0) {
            logger.warning("Invalid max_stored_exp value after scaling. Setting to base value: " + baseMaxStoredExp);
            this.maxStoredExp = baseMaxStoredExp;
        }

        this.minMobs = baseMinMobs * stackSize;
        if (this.minMobs <= 0) {
            logger.warning("Invalid min_mobs value after scaling. Setting to base value: " + baseMinMobs);
            this.minMobs = baseMinMobs;
        }

        this.maxMobs = baseMaxMobs * stackSize;
        if (this.maxMobs <= 0 || this.maxMobs <= this.minMobs) {
            logger.warning("Invalid max_mobs value after scaling. Setting to: " + (this.minMobs + stackSize));
            this.maxMobs = this.minMobs + stackSize;
        }

        this.spawnDelay = baseSpawnerDelay;
        if (this.spawnDelay <= 0) {
            logger.warning("Invalid delay value. Setting to default: 20");
            this.spawnDelay = 400;
        }

        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range");
        if (this.spawnerRange <= 0) {
            logger.warning("Invalid range value. Setting to default: 16");
            this.spawnerRange = 16;
        }
    }

    public void setStackSize(int stackSize) {
        lock.lock();
        try {
            updateStackSize(stackSize);
        } finally {
            lock.unlock();
        }
    }

    public void setStackSize(int stackSize, Player player) {
        lock.lock();
        try {
            updateStackSize(stackSize);
        } finally {
            lock.unlock();
        }
    }

    private void updateStackSize(int stackSize) {
        if (stackSize <= 0) {
            this.stackSize = 1;
            plugin.getLogger().warning("Invalid stack size. Setting to 1");
            return;
        }

        if (stackSize > this.maxStackSize) {
            this.stackSize = this.maxStackSize;
            plugin.getLogger().warning("Stack size exceeds maximum. Setting to " + this.stackSize);
            return;
        }

        // Get current consolidated items
        Map<VirtualInventory.ItemSignature, Long> currentItems = virtualInventory.getConsolidatedItems();

        // Calculate new max slots
        int newMaxSlots = (45 * this.maxStoragePages) * stackSize;

        // Create new inventory with new size
        VirtualInventory newInventory = new VirtualInventory(newMaxSlots);

        // Convert consolidated items to ItemStack list for adding to new inventory
        List<ItemStack> itemsToTransfer = new ArrayList<>();
        currentItems.forEach((signature, amount) -> {
            ItemStack template = signature.getTemplate();
            while (amount > 0) {
                int batchSize = (int) Math.min(amount, Integer.MAX_VALUE);
                ItemStack batch = template.clone();
                batch.setAmount(batchSize);
                itemsToTransfer.add(batch);
                amount -= batchSize;
            }
        });

        // Update stack size and config values
        this.stackSize = stackSize;
        loadConfigValues();
        this.lastSpawnTime = System.currentTimeMillis() + this.spawnDelay;

        // Add items to new inventory
        newInventory.addItems(itemsToTransfer);
        this.virtualInventory = newInventory;
        updateHologramData();
    }

    public void decreaseStackSizeByOne() {
        this.stackSize -= 1;
        updateHologramData();
    }

    public void setSpawnerExp(int exp) {
        this.spawnerExp = Math.min(exp, maxStoredExp);
        updateHologramData();
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
            this.hologram = new SpawnerHologram(spawnerLocation);
            this.hologram.createHologram();
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

    public void refreshHologram() {
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            if (hologram == null) {
                this.hologram = new SpawnerHologram(spawnerLocation);
                this.hologram.createHologram();
                updateHologramData();
            }
        } else {
            if (hologram != null) {
                hologram.remove();
                hologram = null;
            }
        }
    }

    public void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    public boolean isCompletelyFull() {
        return virtualInventory.getUsedSlots() >= maxSpawnerLootSlots
                && spawnerExp >= maxStoredExp;
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

        return !wasFiltered; // Return new state
    }

    // Modified method to get valid loot items that respects the filter
    public List<LootItem> getValidLootItems() {
        if (lootConfig == null) {
            return Collections.emptyList();
        }

        // Get all items first
        List<LootItem> allItems = lootConfig.getAllItems();

        // Filter based on equipment setting and filter list
        return allItems.stream()
                .filter(item -> {
                    // Check if it's in the filtered list
                    ItemStack example = item.createItemStack(new Random());
                    return example != null && !filteredItems.contains(example.getType());
                })
                .collect(Collectors.toList());
    }

    public int getEntityExperienceValue() {
        return lootConfig != null ? lootConfig.getExperience() : 0;
    }

    public void reloadLootConfig() {
        this.lootConfig = lootRegistry.getLootConfig(entityType);
    }
}