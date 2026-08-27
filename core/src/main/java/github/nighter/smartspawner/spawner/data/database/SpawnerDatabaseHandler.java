package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
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

    // Last rev this server believes each spawner is at (schema v5). Seeded on load, advanced on every
    // successful flush. The compare-and-set uses it as the expected value; a mismatch means a foreign
    // writer got in between.
    private final Map<String, Long> knownRev = new ConcurrentHashMap<>();

    private volatile boolean isSaving = false;
    private Scheduler.Task saveTask = null;

    // Cache for raw location strings (used by WorldEventHandler)
    private final Map<String, String> locationCache = new ConcurrentHashMap<>();

    // SQL Statements
    private static final String SELECT_COLUMNS = """
            spawner_id, world, loc_x, loc_y, loc_z,
            entity_type, itemspawner_type, config_name, stack_size, max_stack_size,
            active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
            max_loot_slots, is_at_capacity, exp, max_stored_exp,
            last_interacted_player, preferred_sort_item, filtered_items, storage_items, rev
            """;

    // Schema v5 optimistic concurrency. Writes go through a compare-and-set on the rev column rather
    // than a blind upsert, so a foreign write (another server, a manual edit) that slips in between
    // this server's flushes is detected instead of silently lost. The policy is last-writer-wins: on
    // a conflict this server re-applies its in-memory state at the current rev. Both statements are
    // identical on MySQL and SQLite, so no per-backend variant is needed.

    // Positions match bindMutableColumns: 27 value columns at 1..27, new rev at 28, spawner_id at 29,
    // expected rev at 30.
    private static final String UPDATE_SQL = """
            UPDATE %s SET
                world = ?, loc_x = ?, loc_y = ?, loc_z = ?, chunk_x = ?, chunk_z = ?,
                entity_type = ?, itemspawner_type = ?, stack_size = ?, max_stack_size = ?,
                active = ?, stop = ?, activation_range = ?, delay = ?, last_spawn_time = ?, min_mobs = ?, max_mobs = ?,
                max_loot_slots = ?, is_at_capacity = ?, total_items = ?, exp = ?, max_stored_exp = ?,
                last_interacted_player = ?, preferred_sort_item = ?, filtered_items = ?, storage_items = ?, config_name = ?,
                rev = ?
            WHERE spawner_id = ? AND rev = ?
            """;

    // Positions: spawner_id at 1, 27 value columns at 2..28, rev at 29.
    private static final String INSERT_SQL = """
            INSERT INTO %s (
                spawner_id, world, loc_x, loc_y, loc_z, chunk_x, chunk_z,
                entity_type, itemspawner_type, stack_size, max_stack_size,
                active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
                max_loot_slots, is_at_capacity, total_items, exp, max_stored_exp,
                last_interacted_player, preferred_sort_item, filtered_items, storage_items, config_name, rev
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Columns the cross-server list GUI needs. Deliberately excludes the item blob. */
    private static final String CROSS_SERVER_COLUMNS = """
            spawner_id, world, loc_x, loc_y, loc_z,
            entity_type, stack_size, stop, last_interacted_player,
            exp, total_items
            """;

    // Statements carrying the table name, which is only known once database.table_prefix is read
    private final String tableSpawners;
    private final String selectAllSql;
    private final String selectOneSql;
    private final String selectLocationSql;
    private final String deleteSql;
    private final String deleteLocationConflictSql;
    private final String selectRevSql;

    public SpawnerDatabaseHandler(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseManager = databaseManager;
        this.serverName = databaseManager.getServerName();
        this.tableSpawners = databaseManager.getTableSpawners();

        this.selectAllSql = "SELECT " + SELECT_COLUMNS + " FROM " + tableSpawners + ";";
        this.selectOneSql = "SELECT " + SELECT_COLUMNS + " FROM " + tableSpawners
                + " WHERE spawner_id = ?";
        this.selectLocationSql = "SELECT world, loc_x, loc_y, loc_z FROM " + tableSpawners
                + " WHERE spawner_id = ?";
        this.deleteSql = "DELETE FROM " + tableSpawners + " WHERE spawner_id = ?";
        this.deleteLocationConflictSql = "DELETE FROM " + tableSpawners
                + " WHERE world = ? AND loc_x = ? AND loc_y = ? AND loc_z = ? AND spawner_id <> ?";
        this.selectRevSql = "SELECT rev FROM " + tableSpawners + " WHERE spawner_id = ?";
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
            flushChanges();
        }, intervalTicks, intervalTicks);
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
            // Drop the CAS baseline: if the id is ever re-placed it must be treated as a new row.
            knownRev.remove(spawnerId);
        }
    }

    @Override
    public void queueSpawnerForSaving(String spawnerId) {
        markSpawnerModified(spawnerId);
    }

    @Override
    public void flushChanges() {
        if (dirtySpawners.isEmpty() && deletedSpawners.isEmpty()) {
            return;
        }

        if (isSaving) {
            return;
        }

        isSaving = true;

        Scheduler.runTaskAsync(() -> {
            try {
                // Handle deletes first: a spawner broken and re-placed at the same location within one
                // flush window leaves the old row occupying that location. Removing it before the new
                // row is inserted avoids a UNIQUE(world, loc_x, loc_y, loc_z) violation.
                if (!deletedSpawners.isEmpty()) {
                    Set<String> toDelete = new HashSet<>(deletedSpawners);
                    deletedSpawners.removeAll(toDelete);

                    deleteSpawnerBatch(toDelete);
                }

                // Handle updates
                if (!dirtySpawners.isEmpty()) {
                    Set<String> toUpdate = new HashSet<>(dirtySpawners);
                    dirtySpawners.removeAll(toUpdate);

                    saveSpawnerBatch(toUpdate);
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

    /**
     * Flushes the dirty spawners with schema-v5 optimistic concurrency. Each spawner is written in
     * its own transaction via {@link #writeSpawnerWithCas}, so one bad row cannot abort the others and
     * a per-row conflict can be resolved on its own. A per-row commit is deliberate: SQLite runs in
     * WAL/NORMAL where a commit is cheap, and it keeps the CAS retry logic simple and isolated.
     */
    private void saveSpawnerBatch(Set<String> spawnerIds) {
        if (spawnerIds.isEmpty()) return;

        String updateSql = UPDATE_SQL.formatted(tableSpawners);
        String insertSql = INSERT_SQL.formatted(tableSpawners);

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement update = conn.prepareStatement(updateSql);
             PreparedStatement insert = conn.prepareStatement(insertSql);
             PreparedStatement selectRev = conn.prepareStatement(selectRevSql);
             PreparedStatement clear = conn.prepareStatement(deleteLocationConflictSql)) {

            conn.setAutoCommit(false);

            for (String spawnerId : spawnerIds) {
                SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
                if (spawner == null) continue;

                try {
                    writeSpawnerWithCas(spawner, update, insert, selectRev, clear);
                    conn.commit();
                } catch (SQLException rowError) {
                    logger.log(Level.SEVERE, "Failed to save spawner " + spawnerId
                            + ", re-queuing for the next flush", rowError);
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                        // rollback best-effort; the row is re-queued regardless
                    }
                    dirtySpawners.add(spawnerId);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error saving spawner batch to database", e);
            // Re-add to dirty list for retry
            dirtySpawners.addAll(spawnerIds);
        }
    }

    /**
     * Writes one spawner with a compare-and-set on its {@code rev} column.
     *
     * <ol>
     *   <li>Optimistic {@code UPDATE ... WHERE spawner_id = ? AND rev = expected}. One row updated
     *       means we owned the latest state; advance the known rev and return.</li>
     *   <li>Zero rows and no such spawner_id: a brand-new (or re-placed) spawner. Clear any stale row
     *       still occupying this location, then {@code INSERT} at rev 0.</li>
     *   <li>Zero rows but the row exists at a different rev: a foreign writer got in. Last-writer-wins
     *       &ndash; re-apply this server's in-memory state at the current rev. If even that races
     *       again, re-queue for the next flush rather than spin.</li>
     * </ol>
     *
     * <p>Encoding failures bind nothing and re-queue the spawner (handled inside
     * {@link #bindMutableColumns}), so the stored items are never overwritten with an empty blob.</p>
     */
    private void writeSpawnerWithCas(SpawnerData spawner, PreparedStatement update,
                                     PreparedStatement insert, PreparedStatement selectRev,
                                     PreparedStatement clear) throws SQLException {
        String spawnerId = spawner.getSpawnerId();
        long expected = knownRev.getOrDefault(spawnerId, 0L);

        int n = bindMutableColumns(update, spawner, 1);
        if (n < 0) return; // encode failure, already re-queued
        update.setLong(28, expected + 1);
        update.setString(29, spawnerId);
        update.setLong(30, expected);
        if (update.executeUpdate() >= 1) {
            knownRev.put(spawnerId, expected + 1);
            return;
        }

        Long current = readCurrentRev(selectRev, spawnerId);
        if (current == null) {
            // New or re-placed spawner: heal any stale row at this location first, then insert.
            Location loc = spawner.getSpawnerLocation();
            clear.setString(1, loc.getWorld().getName());
            clear.setInt(2, loc.getBlockX());
            clear.setInt(3, loc.getBlockY());
            clear.setInt(4, loc.getBlockZ());
            clear.setString(5, spawnerId);
            clear.executeUpdate();

            insert.setString(1, spawnerId);
            int m = bindMutableColumns(insert, spawner, 2);
            if (m < 0) return;
            insert.setLong(29, 0L);
            insert.executeUpdate();
            knownRev.put(spawnerId, 0L);
            return;
        }

        // Conflict: another writer advanced rev. Last-writer-wins re-apply at the current rev.
        int n2 = bindMutableColumns(update, spawner, 1);
        if (n2 < 0) return;
        update.setLong(28, current + 1);
        update.setString(29, spawnerId);
        update.setLong(30, current);
        if (update.executeUpdate() >= 1) {
            knownRev.put(spawnerId, current + 1);
            logger.warning("Spawner " + spawnerId + " was modified in the database by another writer; "
                    + "kept this server's state (last-writer-wins), rev " + expected + " -> " + (current + 1) + ".");
        } else {
            logger.warning("Spawner " + spawnerId + " lost a database write race twice; "
                    + "re-queuing it for the next flush.");
            dirtySpawners.add(spawnerId);
        }
    }

    private Long readCurrentRev(PreparedStatement selectRev, String spawnerId) throws SQLException {
        selectRev.setString(1, spawnerId);
        try (ResultSet rs = selectRev.executeQuery()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private void deleteSpawnerBatch(Set<String> spawnerIds) {
        if (spawnerIds.isEmpty()) return;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSql)) {

            conn.setAutoCommit(false);

            for (String spawnerId : spawnerIds) {
                stmt.setString(1, spawnerId);
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting spawner batch from database", e);
            // Re-add to deleted list for retry
            deletedSpawners.addAll(spawnerIds);
        }
    }

    /**
     * Binds the 27 mutable value columns (world through config_name, in the order both the UPDATE and
     * INSERT statements list them) starting at {@code startIdx}.
     *
     * @return the next free parameter index ({@code startIdx + 27}), or {@code -1} when the inventory
     *         could not be encoded. On {@code -1} the caller must skip the row; the spawner is
     *         re-queued here so its stored items are never overwritten with an empty blob.
     */
    private int bindMutableColumns(PreparedStatement stmt, SpawnerData spawner, int startIdx) throws SQLException {
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
                return -1;
            }
            totalItems = SpawnerInventoryCodec.totalItems(consolidated);
        }

        int i = startIdx;
        stmt.setString(i++, loc.getWorld().getName());
        stmt.setInt(i++, loc.getBlockX());
        stmt.setInt(i++, loc.getBlockY());
        stmt.setInt(i++, loc.getBlockZ());
        stmt.setInt(i++, loc.getBlockX() >> 4);
        stmt.setInt(i++, loc.getBlockZ() >> 4);
        stmt.setString(i++, spawner.getEntityType().name());
        stmt.setString(i++, spawner.isItemSpawner() ? spawner.getSpawnedItemMaterial().name() : null);
        stmt.setInt(i++, spawner.getStackSize());
        stmt.setInt(i++, spawner.getMaxStackSize());
        stmt.setBoolean(i++, spawner.getSpawnerActive());
        stmt.setBoolean(i++, spawner.getSpawnerStop().get());
        stmt.setInt(i++, spawner.getSpawnerRange());
        stmt.setLong(i++, spawner.getSpawnDelay());
        stmt.setLong(i++, spawner.getLastSpawnTime());
        stmt.setInt(i++, spawner.getMinMobs());
        stmt.setInt(i++, spawner.getMaxMobs());
        stmt.setInt(i++, spawner.getMaxSpawnerLootSlots());
        stmt.setBoolean(i++, spawner.getIsAtCapacity());
        stmt.setLong(i++, totalItems);
        stmt.setLong(i++, Math.max(0L, spawner.getSpawnerExp()));
        stmt.setLong(i++, spawner.getMaxStoredExp());
        stmt.setString(i++, spawner.getLastInteractedPlayer());
        stmt.setString(i++, spawner.getPreferredSortItem() != null ? spawner.getPreferredSortItem().name() : null);
        stmt.setString(i++, serializeFilteredItems(spawner.getFilteredItems()));
        stmt.setBytes(i++, items);
        stmt.setString(i++, spawner.getConfigName());
        return i;
    }

    @Override
    public Map<String, SpawnerData> loadAllSpawnersRaw() {
        Map<String, SpawnerData> loadedSpawners = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectAllSql)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String spawnerId = rs.getString("spawner_id");
                    try {
                        SpawnerData spawner = loadSpawnerFromResultSet(rs);
                        loadedSpawners.put(spawnerId, spawner);

                        // Cache location for WorldEventHandler
                        if (spawner == null) {
                            String worldName = rs.getString("world");
                            int x = rs.getInt("loc_x");
                            int y = rs.getInt("loc_y");
                            int z = rs.getInt("loc_z");
                            locationCache.put(spawnerId, String.format("%s,%d,%d,%d", worldName, x, y, z));
                        }
                    } catch (Exception e) {
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

            stmt.setString(1, spawnerId);

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

            stmt.setString(1, spawnerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
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

        // Seed the CAS baseline even for spawners whose world is not loaded yet: they materialize
        // later through loadSpecificSpawner (which re-seeds), but recording it here keeps the first
        // flush after a normal load from treating an existing row as brand new.
        knownRev.put(spawnerId, rs.getLong("rev"));

        String worldName = rs.getString("world");
        int x = rs.getInt("loc_x");
        int y = rs.getInt("loc_y");
        int z = rs.getInt("loc_z");

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
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
        String itemMaterialStr = rs.getString("itemspawner_type");
        String configName = rs.getString("config_name");
        if (entityType == EntityType.ITEM && itemMaterialStr != null) {
            try {
                Material itemMaterial = Material.valueOf(itemMaterialStr);
                spawner = new SpawnerData(spawnerId, location, itemMaterial, configName, plugin);
            } catch (IllegalArgumentException e) {
                logger.severe("Invalid item spawner material for spawner " + spawnerId + ": " + itemMaterialStr);
                return null;
            }
        } else {
            spawner = new SpawnerData(spawnerId, location, entityType, configName, plugin);
        }

        // Load settings
        spawner.setSpawnerExpData(rs.getLong("exp"));
        spawner.setSpawnerActive(rs.getBoolean("active"));
        spawner.setSpawnerRange(rs.getInt("activation_range"));
        spawner.getSpawnerStop().set(rs.getBoolean("stop"));
        spawner.setSpawnDelay(Math.max(1L, rs.getLong("delay")));
        spawner.setMaxSpawnerLootSlots(rs.getInt("max_loot_slots"));
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
        byte[] itemsBlob = rs.getBytes("storage_items");
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

        // Restore the complete in-cage model from config when the physical block is loaded.
        Scheduler.runLocationTask(location, () -> {
            org.bukkit.block.Block block = location.getBlock();
            if (block.getType() == Material.SPAWNER) {
                org.bukkit.block.BlockState state = block.getState(false);
                if (state instanceof org.bukkit.block.CreatureSpawner cs) {
                    if (spawner.isItemSpawner()) {
                        github.nighter.smartspawner.spawner.config.SpawnerDisplayConfigurator.applyItem(
                                plugin, cs, spawner.getConfigName(), spawner.getSpawnedItemMaterial());
                    } else {
                        github.nighter.smartspawner.spawner.config.SpawnerDisplayConfigurator.applyMob(
                                plugin, cs, spawner.getConfigName(), spawner.getEntityType());
                    }
                    cs.update(true, false);
                }
            }
        });

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

                // Deletes before updates, for the same reason as flushChanges().
                if (!deletedSpawners.isEmpty()) {
                    deleteSpawnerBatch(new HashSet<>(deletedSpawners));
                }

                if (!dirtySpawners.isEmpty()) {
                    saveSpawnerBatch(new HashSet<>(dirtySpawners));
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
     * The table holding {@code targetServer}'s spawners.
     *
     * <p>Since 1.8.0 a table belongs to exactly one server and says so in its name, so picking a
     * server means picking a table rather than filtering rows. An unknown server falls back to this
     * server's own table, which is what the single-server case always resolves to anyway.</p>
     */
    private String tableFor(String targetServer) {
        try {
            return databaseManager.listServerTables().getOrDefault(targetServer, tableSpawners);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not list the spawner tables, using this server's", e);
            return tableSpawners;
        }
    }

    /**
     * Asynchronously get all distinct server names from the database.
     * @param callback Consumer to receive the list of server names on the main thread
     */
    public void getDistinctServerNamesAsync(Consumer<List<String>> callback) {
        Scheduler.runTaskAsync(() -> {
            List<String> servers = new ArrayList<>();
            try {
                servers.addAll(databaseManager.listServerTables().keySet());
                Collections.sort(servers);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error listing the spawner tables in the database", e);
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
            String table = tableFor(targetServer);
            String sql = "SELECT world, COUNT(*) AS total, COALESCE(SUM(stack_size), 0) AS total_stacked " +
                    "FROM " + table + " GROUP BY world ORDER BY world";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        worlds.put(
                                rs.getString("world"),
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
            String sql = "SELECT SUM(stack_size) as total FROM " + tableFor(targetServer) + " WHERE world = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, worldName);

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
                    + " FROM " + tableFor(targetServer)
                    + " WHERE world = ?"
                    + " ORDER BY stack_size DESC";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, worldName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String spawnerId = rs.getString("spawner_id");
                        String server = targetServer;
                        String world = rs.getString("world");
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
                        boolean active = !rs.getBoolean("stop");
                        String lastPlayer = rs.getString("last_interacted_player");
                        long storedExp = rs.getLong("exp");

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
            String sql = "SELECT COUNT(*) as count FROM " + tableFor(targetServer);

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

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
                    + " FROM " + tableFor(targetServer)
                    + " WHERE world = ?");

            // Add filter condition
            if ("ACTIVE".equalsIgnoreCase(filter)) {
                sql.append(" AND stop = FALSE");
            } else if ("INACTIVE".equalsIgnoreCase(filter)) {
                sql.append(" AND stop = TRUE");
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

                stmt.setString(1, worldName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String spawnerId = rs.getString("spawner_id");
                        String server = targetServer;
                        String world = rs.getString("world");
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
                        boolean active = !rs.getBoolean("stop");
                        String lastPlayer = rs.getString("last_interacted_player");
                        long storedExp = rs.getLong("exp");
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
