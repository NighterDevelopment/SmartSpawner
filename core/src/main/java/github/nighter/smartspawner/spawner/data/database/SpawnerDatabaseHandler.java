package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Database-backed storage handler for spawner data.
 * Implements SpawnerStorage interface with MariaDB operations.
 */
public class SpawnerDatabaseHandler implements SpawnerStorage {
    /** Used when {@code database.autosave-interval} is absent or unparseable. */
    private static final String DEFAULT_AUTOSAVE_INTERVAL = "3m";
    private static final long DEFAULT_AUTOSAVE_TICKS = 3L * 60L * 20L;

    /** Floor on the configured interval. Flushing more often than this buys nothing and costs I/O. */
    private static final long MIN_AUTOSAVE_TICKS = 30L * 20L;

    private final SmartSpawner plugin;
    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final String serverName;

    // Dirty tracking for batch saves
    private final Set<String> dirtySpawners = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedSpawners = ConcurrentHashMap.newKeySet();

    private volatile boolean isSaving = false;
    private Scheduler.Task saveTask = null;

    // Cache for raw location strings (used by WorldEventHandler)
    private final Map<String, String> locationCache = new ConcurrentHashMap<>();

    // SQL Statements
    private static final String SELECT_COLUMNS = """
            spawner_id, world_name, loc_x, loc_y, loc_z, entity_type, item_spawner_material,
            spawner_exp, spawner_active, spawner_range, spawner_stop, spawn_delay,
            max_spawner_loot_slots, max_stored_exp, min_mobs, max_mobs, stack_size,
            max_stack_size, last_spawn_time, is_at_capacity, last_interacted_player,
            preferred_sort_item, filtered_items, items
            """;

    // MySQL/MariaDB upsert syntax
    private static final String UPSERT_SQL_MYSQL = """
            INSERT INTO %s (
                spawner_id, server_name, world_name, loc_x, loc_y, loc_z, chunk_x, chunk_z,
                entity_type, item_spawner_material, spawner_exp, spawner_active,
                spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                last_spawn_time, is_at_capacity, last_interacted_player,
                preferred_sort_item, filtered_items, items, total_items
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                world_name = VALUES(world_name),
                loc_x = VALUES(loc_x),
                loc_y = VALUES(loc_y),
                loc_z = VALUES(loc_z),
                chunk_x = VALUES(chunk_x),
                chunk_z = VALUES(chunk_z),
                entity_type = VALUES(entity_type),
                item_spawner_material = VALUES(item_spawner_material),
                spawner_exp = VALUES(spawner_exp),
                spawner_active = VALUES(spawner_active),
                spawner_range = VALUES(spawner_range),
                spawner_stop = VALUES(spawner_stop),
                spawn_delay = VALUES(spawn_delay),
                max_spawner_loot_slots = VALUES(max_spawner_loot_slots),
                max_stored_exp = VALUES(max_stored_exp),
                min_mobs = VALUES(min_mobs),
                max_mobs = VALUES(max_mobs),
                stack_size = VALUES(stack_size),
                max_stack_size = VALUES(max_stack_size),
                last_spawn_time = VALUES(last_spawn_time),
                is_at_capacity = VALUES(is_at_capacity),
                last_interacted_player = VALUES(last_interacted_player),
                preferred_sort_item = VALUES(preferred_sort_item),
                filtered_items = VALUES(filtered_items),
                items = VALUES(items),
                total_items = VALUES(total_items)
            """;

    // SQLite upsert syntax (ON CONFLICT)
    private static final String UPSERT_SQL_SQLITE = """
            INSERT INTO %s (
                spawner_id, server_name, world_name, loc_x, loc_y, loc_z, chunk_x, chunk_z,
                entity_type, item_spawner_material, spawner_exp, spawner_active,
                spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                last_spawn_time, is_at_capacity, last_interacted_player,
                preferred_sort_item, filtered_items, items, total_items
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(server_name, spawner_id) DO UPDATE SET
                world_name = excluded.world_name,
                loc_x = excluded.loc_x,
                loc_y = excluded.loc_y,
                loc_z = excluded.loc_z,
                chunk_x = excluded.chunk_x,
                chunk_z = excluded.chunk_z,
                entity_type = excluded.entity_type,
                item_spawner_material = excluded.item_spawner_material,
                spawner_exp = excluded.spawner_exp,
                spawner_active = excluded.spawner_active,
                spawner_range = excluded.spawner_range,
                spawner_stop = excluded.spawner_stop,
                spawn_delay = excluded.spawn_delay,
                max_spawner_loot_slots = excluded.max_spawner_loot_slots,
                max_stored_exp = excluded.max_stored_exp,
                min_mobs = excluded.min_mobs,
                max_mobs = excluded.max_mobs,
                stack_size = excluded.stack_size,
                max_stack_size = excluded.max_stack_size,
                last_spawn_time = excluded.last_spawn_time,
                is_at_capacity = excluded.is_at_capacity,
                last_interacted_player = excluded.last_interacted_player,
                preferred_sort_item = excluded.preferred_sort_item,
                filtered_items = excluded.filtered_items,
                items = excluded.items,
                total_items = excluded.total_items
            """;

    /** Columns the cross-server list GUI needs. Deliberately excludes the item blob. */
    private static final String CROSS_SERVER_COLUMNS = """
            spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
            entity_type, stack_size, spawner_stop, last_interacted_player,
            spawner_exp, total_items
            """;

    // Statements carrying the table name, which is only known once database.table_prefix is read
    private final String tableSpawners;
    private final String selectAllSql;
    private final String selectOneSql;
    private final String selectLocationSql;
    private final String deleteSql;

    public SpawnerDatabaseHandler(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseManager = databaseManager;
        this.serverName = databaseManager.getServerName();
        this.tableSpawners = databaseManager.getTableSpawners();

        this.selectAllSql = "SELECT " + SELECT_COLUMNS + " FROM " + tableSpawners + " WHERE server_name = ?";
        this.selectOneSql = "SELECT " + SELECT_COLUMNS + " FROM " + tableSpawners
                + " WHERE server_name = ? AND spawner_id = ?";
        this.selectLocationSql = "SELECT world_name, loc_x, loc_y, loc_z FROM " + tableSpawners
                + " WHERE server_name = ? AND spawner_id = ?";
        this.deleteSql = "DELETE FROM " + tableSpawners + " WHERE server_name = ? AND spawner_id = ?";
    }

    @Override
    public boolean initialize() {
        if (!databaseManager.isActive()) {
            logger.severe("Database manager is not active, cannot initialize SpawnerDatabaseHandler");
            return false;
        }

        // Start the periodic save task
        startSaveTask();
        return true;
    }

    /**
     * (Re)starts the batched save timer from {@code database.autosave-interval}.
     *
     * <p>Called again by {@link #reloadSettings()}, so a changed interval takes effect on
     * {@code /ss reload} rather than needing a restart like the rest of the database section.</p>
     */
    private void startSaveTask() {
        long intervalTicks = Math.max(MIN_AUTOSAVE_TICKS, configuredAutosaveTicks());

        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        saveTask = Scheduler.runTaskTimerAsync(() -> {
            plugin.debug("Running scheduled database save task");
            flushChanges();
        }, intervalTicks, intervalTicks);
        plugin.debug("Database autosave runs every " + (intervalTicks / 20L) + "s");
    }

    /**
     * Parses {@code database.autosave-interval} directly rather than through
     * {@code plugin.getTimeFromConfig}, whose fallback for an unreadable value is one hour. An hour
     * of unsaved spawner data is the wrong answer to a typo; the shipped default is.
     */
    private long configuredAutosaveTicks() {
        String configured = plugin.getConfig().getString("database.autosave-interval", DEFAULT_AUTOSAVE_INTERVAL);
        long ticks = plugin.getTimeFormatter().parseTimeToTicks(configured, -1L);
        if (ticks > 0) {
            return ticks;
        }

        logger.warning("Could not read database.autosave-interval ('" + configured + "'), using "
                + DEFAULT_AUTOSAVE_INTERVAL + " instead.");
        return DEFAULT_AUTOSAVE_TICKS;
    }

    @Override
    public void reloadSettings() {
        startSaveTask();
    }

    @Override
    public void markSpawnerModified(String spawnerId) {
        if (spawnerId != null) {
            dirtySpawners.add(spawnerId);
            deletedSpawners.remove(spawnerId);
        }
    }

    @Override
    public void markSpawnerDeleted(String spawnerId) {
        if (spawnerId != null) {
            deletedSpawners.add(spawnerId);
            dirtySpawners.remove(spawnerId);
            locationCache.remove(spawnerId);
        }
    }

    @Override
    public void queueSpawnerForSaving(String spawnerId) {
        markSpawnerModified(spawnerId);
    }

    @Override
    public void flushChanges() {
        if (dirtySpawners.isEmpty() && deletedSpawners.isEmpty()) {
            plugin.debug("No database changes to flush");
            return;
        }

        if (isSaving) {
            plugin.debug("Database flush operation already in progress");
            return;
        }

        isSaving = true;
        plugin.debug("Flushing " + dirtySpawners.size() + " modified and " + deletedSpawners.size() + " deleted spawners to database");

        Scheduler.runTaskAsync(() -> {
            try {
                // Handle updates
                if (!dirtySpawners.isEmpty()) {
                    Set<String> toUpdate = new HashSet<>(dirtySpawners);
                    dirtySpawners.removeAll(toUpdate);

                    saveSpawnerBatch(toUpdate);
                }

                // Handle deletes
                if (!deletedSpawners.isEmpty()) {
                    Set<String> toDelete = new HashSet<>(deletedSpawners);
                    deletedSpawners.removeAll(toDelete);

                    deleteSpawnerBatch(toDelete);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during database flush", e);
                // Re-add failed items back to dirty lists
                // Note: In production, might want more sophisticated retry logic
            } finally {
                isSaving = false;
            }
        });
    }

    private void saveSpawnerBatch(Set<String> spawnerIds) {
        if (spawnerIds.isEmpty()) return;

        // Select appropriate SQL based on storage mode
        String upsertSql = (databaseManager.getStorageMode() == StorageMode.SQLITE
                ? UPSERT_SQL_SQLITE
                : UPSERT_SQL_MYSQL).formatted(tableSpawners);

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            conn.setAutoCommit(false);

            for (String spawnerId : spawnerIds) {
                SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
                if (spawner == null) continue;

                if (setSpawnerParameters(stmt, spawner)) {
                    stmt.addBatch();
                }
            }

            stmt.executeBatch();
            conn.commit();
            plugin.debug("Saved " + spawnerIds.size() + " spawners to database");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error saving spawner batch to database", e);
            // Re-add to dirty list for retry
            dirtySpawners.addAll(spawnerIds);
        }
    }

    private void deleteSpawnerBatch(Set<String> spawnerIds) {
        if (spawnerIds.isEmpty()) return;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSql)) {

            conn.setAutoCommit(false);

            for (String spawnerId : spawnerIds) {
                stmt.setString(1, serverName);
                stmt.setString(2, spawnerId);
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            plugin.debug("Deleted " + spawnerIds.size() + " spawners from database");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting spawner batch from database", e);
            // Re-add to deleted list for retry
            deletedSpawners.addAll(spawnerIds);
        }
    }

    /**
     * Binds one spawner onto the upsert statement.
     *
     * @return false when the inventory could not be encoded, in which case the caller must skip the
     *         row. Writing it anyway would replace the stored items with an empty blob.
     */
    private boolean setSpawnerParameters(PreparedStatement stmt, SpawnerData spawner) throws SQLException {
        Location loc = spawner.getSpawnerLocation();

        byte[] items;
        long totalItems;
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        if (virtualInv == null) {
            items = null;
            totalItems = 0L;
        } else {
            Map<ItemSignature, Long> consolidated = virtualInv.getConsolidatedItems();
            try {
                items = SpawnerInventoryCodec.encode(consolidated);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not encode inventory for spawner " + spawner.getSpawnerId()
                        + ", skipping this save so the stored items are not lost", e);
                dirtySpawners.add(spawner.getSpawnerId());
                return false;
            }
            totalItems = SpawnerInventoryCodec.totalItems(consolidated);
        }

        stmt.setString(1, spawner.getSpawnerId());
        stmt.setString(2, serverName);
        stmt.setString(3, loc.getWorld().getName());
        stmt.setInt(4, loc.getBlockX());
        stmt.setInt(5, loc.getBlockY());
        stmt.setInt(6, loc.getBlockZ());
        stmt.setInt(7, loc.getBlockX() >> 4);
        stmt.setInt(8, loc.getBlockZ() >> 4);
        stmt.setString(9, spawner.getEntityType().name());
        stmt.setString(10, spawner.isItemSpawner() ? spawner.getSpawnedItemMaterial().name() : null);
        stmt.setLong(11, Math.max(0L, spawner.getSpawnerExp()));
        stmt.setBoolean(12, spawner.getSpawnerActive());
        stmt.setInt(13, spawner.getSpawnerRange());
        stmt.setBoolean(14, spawner.getSpawnerStop().get());
        stmt.setLong(15, spawner.getSpawnDelay());
        stmt.setInt(16, spawner.getMaxSpawnerLootSlots());
        stmt.setLong(17, spawner.getMaxStoredExp());
        stmt.setInt(18, spawner.getMinMobs());
        stmt.setInt(19, spawner.getMaxMobs());
        stmt.setInt(20, spawner.getStackSize());
        stmt.setInt(21, spawner.getMaxStackSize());
        stmt.setLong(22, spawner.getLastSpawnTime());
        stmt.setBoolean(23, spawner.getIsAtCapacity());
        stmt.setString(24, spawner.getLastInteractedPlayer());
        stmt.setString(25, spawner.getPreferredSortItem() != null ? spawner.getPreferredSortItem().name() : null);
        stmt.setString(26, serializeFilteredItems(spawner.getFilteredItems()));
        stmt.setBytes(27, items);
        stmt.setLong(28, totalItems);
        return true;
    }

    @Override
    public Map<String, SpawnerData> loadAllSpawnersRaw() {
        Map<String, SpawnerData> loadedSpawners = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectAllSql)) {

            stmt.setString(1, serverName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String spawnerId = rs.getString("spawner_id");
                    try {
                        SpawnerData spawner = loadSpawnerFromResultSet(rs);
                        loadedSpawners.put(spawnerId, spawner);

                        // Cache location for WorldEventHandler
                        if (spawner == null) {
                            String worldName = rs.getString("world_name");
                            int x = rs.getInt("loc_x");
                            int y = rs.getInt("loc_y");
                            int z = rs.getInt("loc_z");
                            locationCache.put(spawnerId, String.format("%s,%d,%d,%d", worldName, x, y, z));
                        }
                    } catch (Exception e) {
                        plugin.debug("Error loading spawner " + spawnerId + ": " + e.getMessage());
                        loadedSpawners.put(spawnerId, null);
                    }
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading spawners from database", e);
        }

        return loadedSpawners;
    }

    @Override
    public SpawnerData loadSpecificSpawner(String spawnerId) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectOneSql)) {

            stmt.setString(1, serverName);
            stmt.setString(2, spawnerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return loadSpawnerFromResultSet(rs);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading spawner " + spawnerId + " from database", e);
        }

        return null;
    }

    @Override
    public String getRawLocationString(String spawnerId) {
        // Check cache first
        String cached = locationCache.get(spawnerId);
        if (cached != null) {
            return cached;
        }

        // Query database
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectLocationSql)) {

            stmt.setString(1, serverName);
            stmt.setString(2, spawnerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world_name");
                    int x = rs.getInt("loc_x");
                    int y = rs.getInt("loc_y");
                    int z = rs.getInt("loc_z");
                    String location = String.format("%s,%d,%d,%d", worldName, x, y, z);
                    locationCache.put(spawnerId, location);
                    return location;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting location for spawner " + spawnerId, e);
        }

        return null;
    }

    private SpawnerData loadSpawnerFromResultSet(ResultSet rs) throws SQLException {
        String spawnerId = rs.getString("spawner_id");
        String worldName = rs.getString("world_name");
        int x = rs.getInt("loc_x");
        int y = rs.getInt("loc_y");
        int z = rs.getInt("loc_z");

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.debug("World not yet loaded for spawner " + spawnerId + ": " + worldName);
            return null;
        }

        Location location = new Location(world, x, y, z);
        String entityTypeStr = rs.getString("entity_type");
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeStr);
        } catch (IllegalArgumentException e) {
            logger.severe("Invalid entity type for spawner " + spawnerId + ": " + entityTypeStr);
            return null;
        }

        // Create spawner based on type
        SpawnerData spawner;
        String itemMaterialStr = rs.getString("item_spawner_material");
        if (entityType == EntityType.ITEM && itemMaterialStr != null) {
            try {
                Material itemMaterial = Material.valueOf(itemMaterialStr);
                spawner = new SpawnerData(spawnerId, location, itemMaterial, plugin);
            } catch (IllegalArgumentException e) {
                logger.severe("Invalid item spawner material for spawner " + spawnerId + ": " + itemMaterialStr);
                return null;
            }
        } else {
            spawner = new SpawnerData(spawnerId, location, entityType, plugin);
        }

        // Load settings
        spawner.setSpawnerExpData(rs.getLong("spawner_exp"));
        spawner.setSpawnerActive(rs.getBoolean("spawner_active"));
        spawner.setSpawnerRange(rs.getInt("spawner_range"));
        spawner.getSpawnerStop().set(rs.getBoolean("spawner_stop"));
        spawner.setSpawnDelay(Math.max(1L, rs.getLong("spawn_delay")));
        spawner.setMaxSpawnerLootSlots(rs.getInt("max_spawner_loot_slots"));
        spawner.setMaxStoredExp(rs.getLong("max_stored_exp"));
        spawner.setMinMobs(rs.getInt("min_mobs"));
        spawner.setMaxMobs(rs.getInt("max_mobs"));
        spawner.setMaxStackSize(rs.getInt("max_stack_size"));
        spawner.setStackSize(rs.getInt("stack_size"), false); // Don't restart hopper during batch load
        spawner.setLastSpawnTime(rs.getLong("last_spawn_time"));
        spawner.setIsAtCapacity(rs.getBoolean("is_at_capacity"));

        // Load player interaction data
        spawner.setLastInteractedPlayer(rs.getString("last_interacted_player"));

        // Load preferred sort item
        String preferredSortItemStr = rs.getString("preferred_sort_item");
        if (preferredSortItemStr != null && !preferredSortItemStr.isEmpty()) {
            try {
                Material preferredSortItem = Material.valueOf(preferredSortItemStr);
                spawner.setPreferredSortItem(preferredSortItem);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid preferred sort item for spawner " + spawnerId + ": " + preferredSortItemStr);
            }
        }

        // Load filtered items
        String filteredItemsStr = rs.getString("filtered_items");
        if (filteredItemsStr != null && !filteredItemsStr.isEmpty()) {
            deserializeFilteredItems(filteredItemsStr, spawner.getFilteredItems());
        }

        // Load inventory
        byte[] itemsBlob = rs.getBytes("items");
        VirtualInventory virtualInv = new VirtualInventory(spawner.getMaxSpawnerLootSlots());
        if (itemsBlob != null && itemsBlob.length > 0) {
            try {
                Map<ItemStack, Long> items = SpawnerInventoryCodec.decode(itemsBlob);
                for (Map.Entry<ItemStack, Long> entry : items.entrySet()) {
                    virtualInv.addConsolidatedItem(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                logger.warning("Error loading inventory for spawner " + spawnerId + ": " + e.getMessage());
            }
        }
        spawner.setVirtualInventory(virtualInv);
        spawner.markSellValueDirty();

        // Apply sort preference to virtual inventory
        if (spawner.getPreferredSortItem() != null) {
            virtualInv.sortItems(spawner.getPreferredSortItem());
        }

        // Restore the physical spawner block state for item spawners
        if (spawner.isItemSpawner()) {
            Scheduler.runLocationTask(location, () -> {
                org.bukkit.block.Block block = location.getBlock();
                if (block.getType() == Material.SPAWNER) {
                    org.bukkit.block.BlockState state = block.getState(false);
                    if (state instanceof org.bukkit.block.CreatureSpawner cs) {
                        cs.setSpawnedType(EntityType.ITEM);
                        ItemStack spawnedItem = new ItemStack(spawner.getSpawnedItemMaterial(), 1);
                        cs.setSpawnedItem(spawnedItem);
                        cs.update(true, false);
                    }
                }
            });
        }

        return spawner;
    }

    @Override
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        // Perform synchronous flush on shutdown
        if (!dirtySpawners.isEmpty() || !deletedSpawners.isEmpty()) {
            try {
                isSaving = true;
                logger.info("Saving " + dirtySpawners.size() + " spawners to database on shutdown...");

                if (!dirtySpawners.isEmpty()) {
                    saveSpawnerBatch(new HashSet<>(dirtySpawners));
                }

                if (!deletedSpawners.isEmpty()) {
                    deleteSpawnerBatch(new HashSet<>(deletedSpawners));
                }

                dirtySpawners.clear();
                deletedSpawners.clear();
                logger.info("Database shutdown save completed.");

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during database shutdown flush", e);
            } finally {
                isSaving = false;
            }
        }

        locationCache.clear();
    }

    // ============== Serialization Helpers ==============

    private String serializeFilteredItems(Set<Material> filteredItems) {
        if (filteredItems == null || filteredItems.isEmpty()) {
            return null;
        }
        return filteredItems.stream()
                .map(Material::name)
                .collect(Collectors.joining(","));
    }

    private void deserializeFilteredItems(String data, Set<Material> filteredItems) {
        if (data == null || data.isEmpty()) return;

        String[] materialNames = data.split(",");
        for (String materialName : materialNames) {
            try {
                Material material = Material.valueOf(materialName.trim());
                filteredItems.add(material);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material in filtered items: " + materialName);
            }
        }
    }

    // ============== Cross-Server Query Methods ==============

    /**
     * Get the current server name.
     * @return The server name from config
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Asynchronously get all distinct server names from the database.
     * @param callback Consumer to receive the list of server names on the main thread
     */
    public void getDistinctServerNamesAsync(Consumer<List<String>> callback) {
        Scheduler.runTaskAsync(() -> {
            List<String> servers = new ArrayList<>();
            String sql = "SELECT DISTINCT server_name FROM " + tableSpawners + " ORDER BY server_name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    servers.add(rs.getString("server_name"));
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching server names from database", e);
            }

            // Return to main thread
            Scheduler.runTask(() -> callback.accept(servers));
        });
    }

    /**
     * Asynchronously get world names with spawner counts for a specific server.
     * @param targetServer The server name to query
     * @param callback Consumer to receive map of world name -> spawner statistics
     */
    public void getWorldsForServerAsync(String targetServer, Consumer<Map<String, WorldSpawnerStats>> callback) {
        Scheduler.runTaskAsync(() -> {
            Map<String, WorldSpawnerStats> worlds = new LinkedHashMap<>();
            String sql = "SELECT world_name, COUNT(*) AS total, COALESCE(SUM(stack_size), 0) AS total_stacked " +
                    "FROM " + tableSpawners + " WHERE server_name = ? GROUP BY world_name ORDER BY world_name";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, targetServer);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.put(
                                rs.getString("world_name"),
                                new WorldSpawnerStats(rs.getInt("total"), rs.getInt("total_stacked"))
                        );
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching worlds for server " + targetServer, e);
            }

            Scheduler.runTask(() -> callback.accept(worlds));
        });
    }

    public record WorldSpawnerStats(int total, int totalStacked) {}

    /**
     * Asynchronously get total stacked spawner count for a server/world.
     * @param targetServer The server name
     * @param worldName The world name
     * @param callback Consumer to receive total stack count
     */
    public void getTotalStacksForWorldAsync(String targetServer, String worldName, Consumer<Integer> callback) {
        Scheduler.runTaskAsync(() -> {
            int total = 0;
            String sql = "SELECT SUM(stack_size) as total FROM " + tableSpawners + " WHERE server_name = ? AND world_name = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, targetServer);
                stmt.setString(2, worldName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt("total");
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching stack total for " + targetServer + "/" + worldName, e);
            }

            final int finalTotal = total;
            Scheduler.runTask(() -> callback.accept(finalTotal));
        });
    }

    /**
     * Asynchronously get spawner data for a specific server and world.
     * Returns CrossServerSpawnerData objects that don't require Bukkit Location objects.
     * @param targetServer The server name to query
     * @param worldName The world name to query
     * @param callback Consumer to receive list of spawner data
     */
    public void getCrossServerSpawnersAsync(String targetServer, String worldName, Consumer<List<CrossServerSpawnerData>> callback) {
        Scheduler.runTaskAsync(() -> {
            List<CrossServerSpawnerData> spawners = new ArrayList<>();
            String sql = "SELECT " + CROSS_SERVER_COLUMNS
                    + " FROM " + tableSpawners
                    + " WHERE server_name = ? AND world_name = ?"
                    + " ORDER BY stack_size DESC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, targetServer);
                stmt.setString(2, worldName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String spawnerId = rs.getString("spawner_id");
                        String server = rs.getString("server_name");
                        String world = rs.getString("world_name");
                        int x = rs.getInt("loc_x");
                        int y = rs.getInt("loc_y");
                        int z = rs.getInt("loc_z");

                        EntityType entityType;
                        try {
                            entityType = EntityType.valueOf(rs.getString("entity_type"));
                        } catch (IllegalArgumentException e) {
                            entityType = EntityType.PIG; // Fallback
                        }

                        int stackSize = rs.getInt("stack_size");
                        boolean active = !rs.getBoolean("spawner_stop");
                        String lastPlayer = rs.getString("last_interacted_player");
                        long storedExp = rs.getLong("spawner_exp");

                        long totalItems = rs.getLong("total_items");

                        spawners.add(new CrossServerSpawnerData(
                                spawnerId, server, world, x, y, z,
                                entityType, stackSize, active, lastPlayer,
                                storedExp, totalItems
                        ));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching spawners for " + targetServer + "/" + worldName, e);
            }

            Scheduler.runTask(() -> callback.accept(spawners));
        });
    }

    /**
     * Get spawner count for a specific server.
     * @param targetServer The server name
     * @param callback Consumer to receive the count
     */
    public void getSpawnerCountForServerAsync(String targetServer, Consumer<Integer> callback) {
        Scheduler.runTaskAsync(() -> {
            int count = 0;
            String sql = "SELECT COUNT(*) as count FROM " + tableSpawners + " WHERE server_name = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, targetServer);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        count = rs.getInt("count");
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching spawner count for " + targetServer, e);
            }

            final int finalCount = count;
            Scheduler.runTask(() -> callback.accept(finalCount));
        });
    }

    /**
     * Asynchronously get spawner data for a specific server and world with filter and sort.
     * @param targetServer The server name to query
     * @param worldName The world name to query
     * @param filter Filter option (ALL, ACTIVE, INACTIVE)
     * @param sort Sort option (DEFAULT, STACK_SIZE_DESC, STACK_SIZE_ASC)
     * @param callback Consumer to receive list of spawner data
     */
    public void getCrossServerSpawnersAsync(String targetServer, String worldName,
                                            String filter, String sort,
                                            Consumer<List<CrossServerSpawnerData>> callback) {
        Scheduler.runTaskAsync(() -> {
            List<CrossServerSpawnerData> spawners = new ArrayList<>();

            // Build dynamic SQL based on filter and sort
            StringBuilder sql = new StringBuilder("SELECT " + CROSS_SERVER_COLUMNS
                    + " FROM " + tableSpawners
                    + " WHERE server_name = ? AND world_name = ?");

            // Add filter condition
            if ("ACTIVE".equalsIgnoreCase(filter)) {
                sql.append(" AND spawner_stop = FALSE");
            } else if ("INACTIVE".equalsIgnoreCase(filter)) {
                sql.append(" AND spawner_stop = TRUE");
            }

            // Add sort order
            if ("STACK_SIZE_ASC".equalsIgnoreCase(sort)) {
                sql.append(" ORDER BY stack_size ASC");
            } else if ("STACK_SIZE_DESC".equalsIgnoreCase(sort)) {
                sql.append(" ORDER BY stack_size DESC");
            } else {
                sql.append(" ORDER BY spawner_id ASC"); // DEFAULT sort
            }

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

                stmt.setString(1, targetServer);
                stmt.setString(2, worldName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String spawnerId = rs.getString("spawner_id");
                        String server = rs.getString("server_name");
                        String world = rs.getString("world_name");
                        int x = rs.getInt("loc_x");
                        int y = rs.getInt("loc_y");
                        int z = rs.getInt("loc_z");

                        EntityType entityType;
                        try {
                            entityType = EntityType.valueOf(rs.getString("entity_type"));
                        } catch (IllegalArgumentException e) {
                            entityType = EntityType.PIG; // Fallback
                        }

                        int stackSize = rs.getInt("stack_size");
                        boolean active = !rs.getBoolean("spawner_stop");
                        String lastPlayer = rs.getString("last_interacted_player");
                        long storedExp = rs.getLong("spawner_exp");
                        long totalItems = rs.getLong("total_items");

                        spawners.add(new CrossServerSpawnerData(
                                spawnerId, server, world, x, y, z,
                                entityType, stackSize, active, lastPlayer,
                                storedExp, totalItems
                        ));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching spawners for " + targetServer + "/" + worldName, e);
            }

            Scheduler.runTask(() -> callback.accept(spawners));
        });
    }

}
