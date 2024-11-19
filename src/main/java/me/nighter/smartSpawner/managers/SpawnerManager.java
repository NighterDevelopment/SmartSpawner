package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.serializers.ItemStackSerializer;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.SpawnerMenuHolder;
import me.nighter.smartSpawner.utils.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.utils.VirtualInventory;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {
    private final SmartSpawner plugin;
    private Map<String, SpawnerData> spawners = new HashMap<>();
    private Map<LocationKey, SpawnerData> locationIndex = new HashMap<>();
    private long previousExpValue = 0;
    private File spawnerDataFile;
    private FileConfiguration spawnerData;
    private final SpawnerLootGenerator lootGenerator;
    private final SpawnerLootManager lootManager;
    private final Map<UUID, SpawnerData> openSpawnerGuis = new ConcurrentHashMap<>();
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public SpawnerManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.lootGenerator = plugin.getLootGenerator();
        this.lootManager = plugin.getLootManager();
        setupSpawnerDataFile();
        startSaveTask();
    }

    private static class LocationKey {
        private final String world;
        private final int x, y, z;

        public LocationKey(Location location) {
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LocationKey)) return false;
            LocationKey that = (LocationKey) o;
            return x == that.x &&
                    y == that.y &&
                    z == that.z &&
                    world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, y, z);
        }
    }

    // Add methods to maintain the location index
    public void addSpawner(String id, SpawnerData spawner) {
        spawners.put(id, spawner);
        locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);
    }

    public void removeSpawner(String id) {
        SpawnerData spawner = spawners.get(id);
        if (spawner != null) {
            locationIndex.remove(new LocationKey(spawner.getSpawnerLocation()));
            spawners.remove(id);

        }
        deleteSpawnerFromFile(id);
    }

    public void deleteSpawnerFromFile(String spawnerId) {
        try {
            // Xóa toàn bộ section của spawner đó
            String path = "spawners." + spawnerId;
            spawnerData.set(path, null);

            // Lưu file
            spawnerData.save(spawnerDataFile);

            // Xóa khỏi cache maps
            SpawnerData removedSpawner = spawners.remove(spawnerId);
            if (removedSpawner != null) {
                locationIndex.remove(new LocationKey(removedSpawner.getSpawnerLocation()));
            }

            configManager.debug("Successfully deleted spawner " + spawnerId + " from data file");

        } catch (IOException e) {
            plugin.getLogger().severe("Could not delete spawner " + spawnerId + " from spawners_data.yml!");
            e.printStackTrace();
        }
    }

    // Add efficient location lookup method
    public SpawnerData getSpawnerByLocation(Location location) {
        return locationIndex.get(new LocationKey(location));
    }

    private void setupSpawnerDataFile() {
        // Tạo file spawners_data.yml nếu chưa tồn tại
        spawnerDataFile = new File(plugin.getDataFolder(), "spawners_data.yml");
        if (!spawnerDataFile.exists()) {
            plugin.saveResource("spawners_data.yml", false);
        }
        spawnerData = YamlConfiguration.loadConfiguration(spawnerDataFile);
    }

    private void startSaveTask() {
        configManager.debug("Starting spawner data save task");
        int intervalSeconds = configManager.getSaveInterval();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveSpawnerData, 0, intervalSeconds); // 5 phút
    }

    public void backupSpawnerData() {
        try {
            File backupFile = new File(plugin.getDataFolder(), "spawners_data_backup_" +
                    System.currentTimeMillis() + ".yml");
            spawnerData.save(backupFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create backup of spawners_data.yml!");
            e.printStackTrace();
        }
    }

    public boolean saveSingleSpawner(String spawnerId) {
        try {
            SpawnerData spawner = spawners.get(spawnerId);
            if (spawner == null) {
                plugin.getLogger().warning("Could not save spawner " + spawnerId + ": spawner not found");
                return false;
            }

            String path = "spawners." + spawnerId;
            Location loc = spawner.getSpawnerLocation();

            // Lưu thông tin location
            spawnerData.set(path + ".world", loc.getWorld().getName());
            spawnerData.set(path + ".x", loc.getBlockX());
            spawnerData.set(path + ".y", loc.getBlockY());
            spawnerData.set(path + ".z", loc.getBlockZ());

            // Lưu các thuộc tính cơ bản
            spawnerData.set(path + ".entityType", spawner.getEntityType().name());
            spawnerData.set(path + ".spawnerExp", spawner.getSpawnerExp());
            spawnerData.set(path + ".spawnerActive", spawner.getSpawnerActive());
            spawnerData.set(path + ".spawnerRange", spawner.getSpawnerRange());
            spawnerData.set(path + ".spawnerStop", spawner.getSpawnerStop());
            spawnerData.set(path + ".lastSpawnTime", spawner.getLastSpawnTime());
            spawnerData.set(path + ".spawnDelay", spawner.getSpawnDelay());
            spawnerData.set(path + ".maxSpawnerLootSlots", spawner.getMaxSpawnerLootSlots());
            spawnerData.set(path + ".maxStoredExp", spawner.getMaxStoredExp());
            spawnerData.set(path + ".minMobs", spawner.getMinMobs());
            spawnerData.set(path + ".maxMobs", spawner.getMaxMobs());
            spawnerData.set(path + ".stackSize", spawner.getStackSize());

            // Lưu virtual inventory nếu có
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv != null) {
                List<String> serializedItems = new ArrayList<>();

                for (int slot = 0; slot < virtualInv.getSize(); slot++) {
                    ItemStack item = virtualInv.getItem(slot);
                    if (item != null) {
                        String serialized = slot + ":" + ItemStackSerializer.itemStackToJson(item);
                        serializedItems.add(serialized);
                    }
                }

                spawnerData.set(path + ".virtualInventory.size", virtualInv.getSize());
                spawnerData.set(path + ".virtualInventory.items", serializedItems);
            }

            // Lưu file
            spawnerData.save(spawnerDataFile);
            return true;

        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawner " + spawnerId + " to spawners_data.yml!");
            e.printStackTrace();
            return false;
        }
    }

    public void saveSpawnerData() {
        try {
            // Clear old data
            for (String key : spawnerData.getKeys(false)) {
                spawnerData.set(key, null);
            }

            // Save each spawner
            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                SpawnerData spawner = entry.getValue();
                Location loc = spawner.getSpawnerLocation();

                String path = "spawners." + spawnerId;

                // Save location
                spawnerData.set(path + ".world", loc.getWorld().getName());
                spawnerData.set(path + ".x", loc.getBlockX());
                spawnerData.set(path + ".y", loc.getBlockY());
                spawnerData.set(path + ".z", loc.getBlockZ());

                // Save basic properties
                spawnerData.set(path + ".entityType", spawner.getEntityType().name());
                spawnerData.set(path + ".spawnerExp", spawner.getSpawnerExp());
                spawnerData.set(path + ".spawnerActive", spawner.getSpawnerActive());
                spawnerData.set(path + ".spawnerRange", spawner.getSpawnerRange());
                spawnerData.set(path + ".spawnerStop", spawner.getSpawnerStop());
                spawnerData.set(path + ".lastSpawnTime", spawner.getLastSpawnTime());
                spawnerData.set(path + ".spawnDelay", spawner.getSpawnDelay());
                spawnerData.set(path + ".maxSpawnerLootSlots", spawner.getMaxSpawnerLootSlots());
                spawnerData.set(path + ".maxStoredExp", spawner.getMaxStoredExp());
                spawnerData.set(path + ".minMobs", spawner.getMinMobs());
                spawnerData.set(path + ".maxMobs", spawner.getMaxMobs());
                spawnerData.set(path + ".stackSize", spawner.getStackSize());

                // Save virtual inventory
                VirtualInventory virtualInv = spawner.getVirtualInventory();
                if (virtualInv != null) {
                    List<String> serializedItems = new ArrayList<>();

                    // Iterate through all slots up to the inventory size
                    for (int slot = 0; slot < virtualInv.getSize(); slot++) {
                        ItemStack item = virtualInv.getItem(slot);
                        if (item != null) {
                            String serialized = slot + ":" + ItemStackSerializer.itemStackToJson(item);
                            serializedItems.add(serialized);
                        }
                    }

                    spawnerData.set(path + ".virtualInventory.size", virtualInv.getSize());
                    spawnerData.set(path + ".virtualInventory.items", serializedItems);
                }
            }

            spawnerData.save(spawnerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawners_data.yml!");
            e.printStackTrace();
        }
    }

    public void loadSpawnerData() {
        spawners.clear();
        locationIndex.clear();

        ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
        if (spawnersSection == null) return;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                String path = "spawners." + spawnerId;

                // Load location
                String worldName = spawnerData.getString(path + ".world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Could not load spawner " + spawnerId + ": world not found");
                    continue;
                }

                Location location = new Location(world,
                        spawnerData.getInt(path + ".x"),
                        spawnerData.getInt(path + ".y"),
                        spawnerData.getInt(path + ".z"));

                // Load entity type
                EntityType entityType = EntityType.valueOf(spawnerData.getString(path + ".entityType"));

                // Create new spawner
                SpawnerData spawner = new SpawnerData(spawnerId, location, entityType, plugin);

                // Load basic properties
                spawner.setSpawnerExp(spawnerData.getInt(path + ".spawnerExp"));
                spawner.setSpawnerActive(spawnerData.getBoolean(path + ".spawnerActive"));
                spawner.setSpawnerRange(spawnerData.getInt(path + ".spawnerRange"));
                spawner.setSpawnerStop(spawnerData.getBoolean(path + ".spawnerStop"));
                spawner.setLastSpawnTime(spawnerData.getLong(path + ".lastSpawnTime"));
                spawner.setSpawnDelay(spawnerData.getInt(path + ".spawnDelay"));
                spawner.setMaxSpawnerLootSlots(spawnerData.getInt(path + ".maxSpawnerLootSlots"));
                spawner.setMaxStoredExp(spawnerData.getInt(path + ".maxStoredExp"));
                spawner.setMinMobs(spawnerData.getInt(path + ".minMobs"));
                spawner.setMaxMobs(spawnerData.getInt(path + ".maxMobs"));
                spawner.setStackSize(spawnerData.getInt(path + ".stackSize"));

                // Load virtual inventory
                int invSize = spawnerData.getInt(path + ".virtualInventory.size", spawner.getMaxSpawnerLootSlots());
                VirtualInventory virtualInv = new VirtualInventory(invSize);

                List<String> serializedItems = spawnerData.getStringList(path + ".virtualInventory.items");
                if (serializedItems != null) {
                    for (String serialized : serializedItems) {
                        try {
                            String[] parts = serialized.split(":", 2);
                            if (parts.length == 2) {
                                int slot = Integer.parseInt(parts[0]);
                                ItemStack item = ItemStackSerializer.itemStackFromJson(parts[1]);
                                virtualInv.setItem(slot, item);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to load item for spawner " + spawnerId + ": " + e.getMessage());
                        }
                    }
                }
                spawner.setVirtualInventory(virtualInv);

                // Add to maps
                spawners.put(spawnerId, spawner);
                locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);

            } catch (Exception e) {
                plugin.getLogger().severe("Error loading spawner " + spawnerId);
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + spawners.size() + " spawners from spawners_data.yml");
    }

    public Map<String, SpawnerData> getSpawners() {
        return this.spawners;
    }

    public SpawnerData getSpawner(String id) {
        return spawners.get(id);
    }

    public List<SpawnerData> getAllSpawners() {
        return new ArrayList<>(spawners.values());
    }

    public void spawnLoot(SpawnerData spawner) {
        if (System.currentTimeMillis() - spawner.getLastSpawnTime() >= spawner.getSpawnDelay()) {
            LootResult loot = lootGenerator.generateLoot(
                    spawner.getEntityType(),
                    spawner.getMinMobs(),
                    spawner.getMaxMobs(),
                    spawner
            );

            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();
            world.spawnParticle(Particle.VILLAGER_HAPPY,
                    loc.clone().add(0.5, 0.5, 0.5),
                    10, 0.3, 0.3, 0.3, 0);

            // Thêm loot vào virtual inventory
            lootGenerator.addLootToSpawner(spawner, loot);
            spawner.setLastSpawnTime(System.currentTimeMillis());

            // Cập nhật cho tất cả người chơi đang xem
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (HumanEntity viewer : getViewersForSpawner(spawner)) {
                    if (viewer instanceof Player) {
                        Player player = (Player) viewer;
                        Inventory currentInv = player.getOpenInventory().getTopInventory();
                        if (currentInv.getHolder() instanceof PagedSpawnerLootHolder) {
                            PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) currentInv.getHolder();
                            int currentPage = holder.getCurrentPage();
                            // Tạo inventory mới với data mới nhất
                            Inventory newInv = lootManager.createLootInventory(spawner, languageManager.getGuiTitle("gui-title.loot-menu"), currentPage);
                            // Copy items từ inventory mới sang inventory cũ
                            for (int i = 0; i < newInv.getSize(); i++) {
                                currentInv.setItem(i, newInv.getItem(i));
                            }
                            player.updateInventory();
                        }
                    }
                }

                for (Map.Entry<UUID, SpawnerData> entry : openSpawnerGuis.entrySet()) {
                    if (entry.getValue().getSpawnerId().equals(spawner.getSpawnerId())) {
                        Player viewer = Bukkit.getPlayer(entry.getKey());
                        if (viewer != null && viewer.isOnline()) {
                            updateSpawnerGui(viewer, spawner, true);
                        }
                    }
                }
            });
        }
    }

    private List<HumanEntity> getViewersForSpawner(SpawnerData spawner) {
        List<HumanEntity> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv.getHolder() instanceof PagedSpawnerLootHolder) {
                PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) openInv.getHolder();
                if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                    viewers.add(player);
                }
            }
        }
        return viewers;
    }


    // Thêm method để track GUI đang mở
    public void trackOpenGui(UUID playerId, SpawnerData spawner) {
        openSpawnerGuis.put(playerId, spawner);
    }

    public void untrackOpenGui(UUID playerId) {
        openSpawnerGuis.remove(playerId);
    }

    // Method để update GUI cho người chơi đang xem
    public void updateSpawnerGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateSpawnerInfoItem(openInv, spawner);
                updateExpItem(openInv, spawner);
                updateChestItem(openInv, spawner);
            }
        }
    }

    private void updateChestItem(Inventory inventory, SpawnerData spawner) {
        ItemStack chestItem = inventory.getItem(11);
        if (chestItem == null || !chestItem.hasItemMeta()) return;

        ItemMeta chestMeta = chestItem.getItemMeta();
        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        List<String> chestLore = new ArrayList<>();
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getAllItems().size();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest")
                .replace("%max_slots%", String.valueOf(maxSlots))
                .replace("%current_items%", String.valueOf(currentItems))
                .replace("%percent_storage%", String.valueOf(percentStorage));

        chestLore.addAll(Arrays.asList(loreMessageChest.split("\n")));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);
        inventory.setItem(11, chestItem);
    }

    private void updateExpItem(Inventory inventory, SpawnerData spawner) {
        ItemStack expItem = inventory.getItem(15);
        if (expItem == null || !expItem.hasItemMeta()) return;

        long currentExp = spawner.getSpawnerExp();
        if (currentExp != previousExpValue) {

            ItemMeta expMeta = expItem.getItemMeta();
            Map<String, String> nameReplacements = new HashMap<>();
            String formattedExp = languageManager.formatNumber(currentExp);
            String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
            int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

            nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
            expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name", nameReplacements));
            Map<String, String> loreReplacements = new HashMap<>();
            loreReplacements.put("%current_exp%", formattedExp);
            loreReplacements.put("%max_exp%", formattedMaxExp);
            loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
            loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));
            String lorePathExp = "exp-info-item.lore.exp-bottle";
            String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
            List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
            expMeta.setLore(loreEx);
            expItem.setItemMeta(expMeta);
            inventory.setItem(15, expItem);
            previousExpValue = currentExp;
        }
    }

    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(13);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay = getTimeDisplay(timeUntilNextSpawn);

        // Lấy template cho next spawn line
        String nextSpawnTemplate = languageManager.getMessage("spawner-info-item.lore-change");

        // Strip màu để so sánh
        String strippedTemplate = ChatColor.stripColor(nextSpawnTemplate);

        // Optimize lore update
        boolean updated = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            // Strip màu của line hiện tại để so sánh
            String strippedLine = ChatColor.stripColor(line);

            // So sánh nội dung đã strip màu
            if (strippedLine.startsWith(ChatColor.stripColor(strippedTemplate))) {
                String newLine = nextSpawnTemplate + timeDisplay;
                if (!line.equals(newLine)) {
                    lore.set(i, newLine);
                    updated = true;
                }
                break;
            }
        }

        // Chỉ update ItemMeta nếu có thay đổi
        if (updated || !lore.stream()
                .map(ChatColor::stripColor)
                .anyMatch(line -> line.startsWith(ChatColor.stripColor(strippedTemplate)))) {
            if (!updated) {
                lore.add(nextSpawnTemplate + timeDisplay);
            }
            meta.setLore(lore);
            spawnerItem.setItemMeta(meta);
        }
    }

    private String getTimeDisplay(long timeUntilNextSpawn) {
        if (timeUntilNextSpawn == -1) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn == 0) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn < 0) {
            return languageManager.getMessage("spawner-info-item.lore-error");
        }
        return formatTime(timeUntilNextSpawn);
    }

    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop()) {
            return -1;
        }
        final long currentTime = System.currentTimeMillis();
        final long lastSpawnTime = spawner.getLastSpawnTime();
        final long spawnDelay = spawner.getSpawnDelay() * 50L;
        return lastSpawnTime + spawnDelay - currentTime;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    public Map<UUID, SpawnerData> getOpenSpawnerGuis() {
        return openSpawnerGuis;
    }
}