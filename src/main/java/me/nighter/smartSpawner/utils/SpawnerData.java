package me.nighter.smartSpawner.utils;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class SpawnerData {
    private final SmartSpawner plugin;
    private final String spawnerId;
    private Location spawnerLocation;
    private Integer spawnerExp;
    private Boolean spawnerActive;
    private Integer spawnerRange;
    private Boolean spawnerStop;
    private Long lastSpawnTime;
    private Integer spawnDelay;
    private EntityType entityType;
    private int maxSpawnerLootSlots;
    private int maxStoredExp;
    private int minMobs;
    private int maxMobs;
    private int stackSize;
    private VirtualInventory virtualInventory;
    private boolean allowEquipmentItems;
    private static final Logger logger = Logger.getLogger("SmartSpawnerConfig");
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = true;
        this.stackSize = 1;
        this.maxSpawnerLootSlots = 45;
        this.allowEquipmentItems = true;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        loadConfigValues();
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
    }

    public VirtualInventory getVirtualInventory() {
        return virtualInventory;
    }

    public void setVirtualInventory(VirtualInventory inventory) {
        this.virtualInventory = inventory;
    }

    // Add method to get items for display in real inventory
    public ItemStack getItemAt(int slot) {
        return virtualInventory.getItem(slot);
    }

    // Add method to set items from real inventory
    public void setItemAt(int slot, ItemStack item) {
        virtualInventory.setItem(slot, item);
    }

    // Add method to get total slots
    public int getTotalSlots() {
        return virtualInventory.getSize();
    }

    private void loadConfigValues() {
        FileConfiguration config = configManager.getMainConfig();

        int maxAllowedStack = config.getInt("spawner.max-stack-size", 100);
        int baseMaxStoredExp = config.getInt("spawner.max-stored-exp", 1000);
        int baseMinMobs = config.getInt("spawner.min-mobs", 1);
        int baseMaxMobs = config.getInt("spawner.max-mobs", 4);
        int baseSpawnerDelay = config.getInt("spawner.delay", 600);

        // Calculate scaled values based on stack size
        this.maxSpawnerLootSlots = Math.min(45 * stackSize, 54 * maxAllowedStack);
        if (this.maxSpawnerLootSlots < 9) {
            logger.warning("Invalid maxSpawnerLootSlots value after scaling. Setting to minimum: 9");
            this.maxSpawnerLootSlots = 9;
        }

        this.maxStoredExp = baseMaxStoredExp * stackSize;
        if (this.maxStoredExp <= 0) {
            logger.warning("Invalid maxStoredExp value after scaling. Setting to base value: " + baseMaxStoredExp);
            this.maxStoredExp = baseMaxStoredExp;
        }

        this.minMobs = baseMinMobs * stackSize;
        if (this.minMobs <= 0) {
            logger.warning("Invalid minMobs value after scaling. Setting to base value: " + baseMinMobs);
            this.minMobs = baseMinMobs;
        }

        this.maxMobs = baseMaxMobs * stackSize;
        if (this.maxMobs <= 0 || this.maxMobs <= this.minMobs) {
            logger.warning("Invalid maxMobs value after scaling. Setting to: " + (this.minMobs + stackSize));
            this.maxMobs = this.minMobs + stackSize;
        }

        this.spawnDelay = baseSpawnerDelay;
        if (this.spawnDelay <= 0) {
            logger.warning("Invalid spawnDelay value. Setting to default: 600");
            this.spawnDelay = 600;
        }
        this.spawnerRange = configManager.getSpawnerRange();
        if (this.spawnerRange <= 0) {
            logger.warning("Invalid spawnerRange value. Setting to default: 16");
            this.spawnerRange = 16;
        }

        this.lastSpawnTime = System.currentTimeMillis() + (long) spawnDelay;
    }

    public int getStackSize() {
        return stackSize;
    }

    public void setStackSize(int stackSize) {
        int maxAllowedStack = configManager.getMaxStackSize();
        if (stackSize <= 0) {
            this.stackSize = 1;
            logger.warning("Invalid stack size. Setting to 1");
        } else if (stackSize > maxAllowedStack) {
            this.stackSize = maxAllowedStack;
            logger.warning("Stack size exceeds maximum. Setting to " + maxAllowedStack);
        } else {
            // Lưu lại inventory cũ
            Map<Integer, ItemStack> oldInventory = this.virtualInventory.getAllItems();

            // Tính toán số slot mới dựa trên stackSize mới
            int newMaxSlots = Math.min(45 * stackSize, 54 * maxAllowedStack);
            if (newMaxSlots < 9) newMaxSlots = 9;

            // Kiểm tra xem có items nào sẽ bị mất không
            boolean hasItemsInExcessSlots = false;
            List<ItemStack> excessItems = new ArrayList<>();

            for (Map.Entry<Integer, ItemStack> entry : oldInventory.entrySet()) {
                if (entry.getKey() >= newMaxSlots && entry.getValue() != null) {
                    hasItemsInExcessSlots = true;
                    excessItems.add(entry.getValue());
                }
            }

            // Nếu có items sẽ bị mất, thử merge chúng vào các slot còn trống
            if (hasItemsInExcessSlots) {
                Map<Integer, ItemStack> safeInventory = new HashMap<>();

                // Copy các items trong phạm vi slot mới
                for (int i = 0; i < newMaxSlots; i++) {
                    if (oldInventory.containsKey(i)) {
                        safeInventory.put(i, oldInventory.get(i));
                    }
                }

                // Thử merge excess items vào các slot trống
                for (ItemStack excessItem : excessItems) {
                    boolean merged = false;
                    for (int i = 0; i < newMaxSlots; i++) {
                        ItemStack existingItem = safeInventory.get(i);
                        if (existingItem == null) {
                            safeInventory.put(i, excessItem);
                            merged = true;
                            break;
                        } else if (existingItem.isSimilar(excessItem) &&
                                existingItem.getAmount() + excessItem.getAmount() <= existingItem.getMaxStackSize()) {
                            existingItem.setAmount(existingItem.getAmount() + excessItem.getAmount());
                            merged = true;
                            break;
                        }
                    }

                    if (!merged) {
                        // Nếu không merge được, thêm vào cuối
                    }
                }

                // Cập nhật stackSize và tạo inventory mới
                this.stackSize = stackSize;
                loadConfigValues();
                this.virtualInventory = new VirtualInventory(this.maxSpawnerLootSlots);
                this.virtualInventory.setItems(safeInventory);

            } else {
                // Nếu không có items bị ảnh hưởng, cập nhật bình thường
                this.stackSize = stackSize;
                loadConfigValues();
                this.virtualInventory = new VirtualInventory(this.maxSpawnerLootSlots);
                this.virtualInventory.setItems(oldInventory);
            }
        }
    }

    public void setStackSize(int stackSize, Player player) {
        boolean stop = false;
        int maxAllowedStack = configManager.getMaxStackSize();
        if (stackSize <= 0) {
            this.stackSize = 1;
            configManager.debug("Invalid stack size. Setting to 1");
        } else if (stackSize > maxAllowedStack) {
            this.stackSize = maxAllowedStack;
            configManager.debug("Stack size exceeds maximum. Setting to " + maxAllowedStack);
        } else {
            // Lưu lại inventory cũ
            Map<Integer, ItemStack> oldInventory = this.virtualInventory.getAllItems();

            // Tính toán số slot mới dựa trên stackSize mới
            int newMaxSlots = Math.min(45 * stackSize, 54 * maxAllowedStack);
            if (newMaxSlots < 9) newMaxSlots = 9;

            // Kiểm tra xem có items nào sẽ bị mất không
            boolean hasItemsInExcessSlots = false;
            List<ItemStack> excessItems = new ArrayList<>();

            for (Map.Entry<Integer, ItemStack> entry : oldInventory.entrySet()) {
                if (entry.getKey() >= newMaxSlots && entry.getValue() != null) {
                    hasItemsInExcessSlots = true;
                    excessItems.add(entry.getValue());
                }
            }

            // Nếu có items sẽ bị mất, thử merge chúng vào các slot còn trống
            if (hasItemsInExcessSlots) {
                Map<Integer, ItemStack> safeInventory = new HashMap<>();

                // Copy các items trong phạm vi slot mới
                for (int i = 0; i < newMaxSlots; i++) {
                    if (oldInventory.containsKey(i)) {
                        safeInventory.put(i, oldInventory.get(i));
                    }
                }
                // Thử merge excess items vào các slot trống
                for (ItemStack excessItem : excessItems) {
                    boolean merged = false;
                    for (int i = 0; i < newMaxSlots; i++) {
                        ItemStack existingItem = safeInventory.get(i);
                        if (existingItem == null) {
                            safeInventory.put(i, excessItem);
                            merged = true;
                            break;
                        } else if (existingItem.isSimilar(excessItem) &&
                                existingItem.getAmount() + excessItem.getAmount() <= existingItem.getMaxStackSize()) {
                            existingItem.setAmount(existingItem.getAmount() + excessItem.getAmount());
                            merged = true;
                            break;
                        }
                    }

                    if (!merged && !stop) {
                        languageManager.sendMessage(player, "messages.items-lost");
                        stop = true;
                    }
                }

                // Cập nhật stackSize và tạo inventory mới
                this.stackSize = stackSize;
                loadConfigValues();
                this.virtualInventory = new VirtualInventory(this.maxSpawnerLootSlots);
                this.virtualInventory.setItems(safeInventory);

            } else {
                // Nếu không có items bị ảnh hưởng, cập nhật bình thường
                this.stackSize = stackSize;
                loadConfigValues();
                this.virtualInventory = new VirtualInventory(this.maxSpawnerLootSlots);
                this.virtualInventory.setItems(oldInventory);
            }
        }
    }

    public void decreaseStackSizeByOne() {
        this.stackSize -= 1;
    }

    // Getters and Setters

    public String getSpawnerId() {
        return spawnerId;
    }

    public Location getSpawnerLocation() {
        return spawnerLocation;
    }

    public Integer getSpawnerExp() {
        return spawnerExp;
    }

    public void setSpawnerExp(int exp) {
        this.spawnerExp = Math.min(exp, maxStoredExp);
    }

    public Integer getMaxStoredExp(){
        return maxStoredExp;
    }
    public void setMaxStoredExp(int maxStoredExp) {
        this.maxStoredExp = maxStoredExp;
    }

    public Boolean isSpawnerActive() {
        return spawnerActive;
    }

    public void setSpawnerActive(Boolean spawnerActive) {
        this.spawnerActive = spawnerActive;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public int getMaxSpawnerLootSlots() {
        return maxSpawnerLootSlots;
    }

    public void setMaxSpawnerLootSlots(int maxSpawnerLootSlots) {
        this.maxSpawnerLootSlots = maxSpawnerLootSlots;
    }

    public int getMinMobs() {
        return minMobs;
    }

    public void setMinMobs(int minMobs) {
        this.minMobs = minMobs;
    }

    public int getMaxMobs() {
        return maxMobs;
    }

    public void setMaxMobs(int maxMobs) {
        this.maxMobs = maxMobs;
    }

    public Integer getSpawnerRange() {
        return spawnerRange;
    }
    
    public void setSpawnerRange(Integer spawnerRange) {
        this.spawnerRange = spawnerRange;
    }

    public Boolean getSpawnerActive() {
        return spawnerActive;
    }

    public Boolean getSpawnerStop() {
        return spawnerStop;
    }

    public void setSpawnerStop(Boolean spawnerStop) {
        this.spawnerStop = spawnerStop;
    }

    public Long getLastSpawnTime() {
        return lastSpawnTime;
    }

    public void setLastSpawnTime(Long lastSpawnTime) {
        this.lastSpawnTime = lastSpawnTime;
    }

    public Integer getSpawnDelay() {
        return spawnDelay;
    }

    public void setSpawnDelay(Integer spawnDelay) {
        this.spawnDelay = spawnDelay;
    }

    public boolean isAllowEquipmentItems() {
        return allowEquipmentItems;
    }

    public void setAllowEquipmentItems(boolean allowEquipmentItems) {
        this.allowEquipmentItems = allowEquipmentItems;
    }
}