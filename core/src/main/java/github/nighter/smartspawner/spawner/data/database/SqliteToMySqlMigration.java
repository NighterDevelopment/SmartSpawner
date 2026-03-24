package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one-time migration from SQLite database to MySQL/MariaDB.
 * After successful migration, the SQLite file is renamed to spawners.db.migrated
 * to prevent re-migration.
 */
public class SqliteToMySqlMigration {
    private final SmartSpawner plugin;
    private final Logger logger;
    private final DatabaseManager mysqlManager;

    private static final String MIGRATED_FILE_SUFFIX = ".migrated";

    // MySQL insert syntax (target)
    private static final String INSERT_SQL_MYSQL = """
            INSERT INTO smart_spawners (
                spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
                entity_type, item_spawner_material, spawner_exp, spawner_active,
                spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                last_spawn_time, is_at_capacity, last_interacted_player,
                preferred_sort_item, filtered_items, inventory_data
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                world_name = VALUES(world_name),
                loc_x = VALUES(loc_x),
                loc_y = VALUES(loc_y),
                loc_z = VALUES(loc_z),
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
                inventory_data = VALUES(inventory_data)
            """;

    private static final String SELECT_ALL_SQLITE = """
            SELECT spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
                   entity_type, item_spawner_material, spawner_exp, spawner_active,
                   spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                   max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                   last_spawn_time, is_at_capacity, last_interacted_player,
                   preferred_sort_item, filtered_items, inventory_data
            FROM smart_spawners
            """;

    public SqliteToMySqlMigration(SmartSpawner plugin, DatabaseManager mysqlManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.mysqlManager = mysqlManager;
    }

    /**
     * Check if migration is needed.
     * Migration is needed if SQLite database file exists and hasn't been migrated.
     * @return true if migration is needed
     */
    public boolean needsMigration() {
        // Only migrate when target is MySQL
        if (mysqlManager.getStorageMode() != StorageMode.MYSQL) {
            return false;
        }

        String sqliteFileName = plugin.getConfig().getString("database.sqlite.file", "spawners.db");
        File sqliteFile = new File(plugin.getDataFolder(), sqliteFileName);

        if (!sqliteFile.exists()) {
            return false;
        }

        // Check if already migrated
        File migratedFile = new File(plugin.getDataFolder(), sqliteFileName + MIGRATED_FILE_SUFFIX);
        if (migratedFile.exists()) {
            return false;
        }

        // Check if SQLite has any data
        return hasSqliteData(sqliteFile);
    }

    private boolean hasSqliteData(File sqliteFile) {
        String jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM smart_spawners")) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Table might not exist or other error
            plugin.debug("SQLite check failed: " + e.getMessage());
        }

        return false;
    }

    /**
     * Perform the migration from SQLite to MySQL.
     * @return true if migration was successful
     */
    public boolean migrate() {
        logger.info("Starting SQLite to MySQL migration...");

        String sqliteFileName = plugin.getConfig().getString("database.sqlite.file", "spawners.db");
        File sqliteFile = new File(plugin.getDataFolder(), sqliteFileName);

        if (!sqliteFile.exists()) {
            logger.info("No SQLite file found, skipping migration.");
            return true;
        }

        String sqliteJdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();

        int totalSpawners = 0;
        int migratedCount = 0;
        int failedCount = 0;

        try (Connection sqliteConn = DriverManager.getConnection(sqliteJdbcUrl);
             Connection mysqlConn = mysqlManager.getConnection();
             PreparedStatement selectStmt = sqliteConn.prepareStatement(SELECT_ALL_SQLITE);
             PreparedStatement insertStmt = mysqlConn.prepareStatement(INSERT_SQL_MYSQL)) {

            mysqlConn.setAutoCommit(false);

            try (ResultSet rs = selectStmt.executeQuery()) {
                int batchCount = 0;
                final int BATCH_SIZE = 100;

                while (rs.next()) {
                    totalSpawners++;

                    try {
                        // Transfer all columns
                        insertStmt.setString(1, rs.getString("spawner_id"));
                        insertStmt.setString(2, rs.getString("server_name"));
                        insertStmt.setString(3, rs.getString("world_name"));
                        insertStmt.setInt(4, rs.getInt("loc_x"));
                        insertStmt.setInt(5, rs.getInt("loc_y"));
                        insertStmt.setInt(6, rs.getInt("loc_z"));
                        insertStmt.setString(7, rs.getString("entity_type"));
                        insertStmt.setString(8, rs.getString("item_spawner_material"));
                        insertStmt.setInt(9, rs.getInt("spawner_exp"));
                        insertStmt.setBoolean(10, rs.getBoolean("spawner_active"));
                        insertStmt.setInt(11, rs.getInt("spawner_range"));
                        insertStmt.setBoolean(12, rs.getBoolean("spawner_stop"));
                        insertStmt.setLong(13, rs.getLong("spawn_delay"));
                        insertStmt.setInt(14, rs.getInt("max_spawner_loot_slots"));
                        insertStmt.setInt(15, rs.getInt("max_stored_exp"));
                        insertStmt.setInt(16, rs.getInt("min_mobs"));
                        insertStmt.setInt(17, rs.getInt("max_mobs"));
                        insertStmt.setInt(18, rs.getInt("stack_size"));
                        insertStmt.setInt(19, rs.getInt("max_stack_size"));
                        insertStmt.setLong(20, rs.getLong("last_spawn_time"));
                        insertStmt.setBoolean(21, rs.getBoolean("is_at_capacity"));
                        insertStmt.setString(22, rs.getString("last_interacted_player"));
                        insertStmt.setString(23, rs.getString("preferred_sort_item"));
                        insertStmt.setString(24, rs.getString("filtered_items"));
                        insertStmt.setString(25, rs.getString("inventory_data"));

                        insertStmt.addBatch();
                        batchCount++;
                        migratedCount++;

                        // Execute batch every BATCH_SIZE records
                        if (batchCount >= BATCH_SIZE) {
                            insertStmt.executeBatch();
                            mysqlConn.commit();
                            batchCount = 0;
                            logger.info("Migrated " + migratedCount + " spawners...");
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to migrate spawner: " + rs.getString("spawner_id"), e);
                        failedCount++;
                    }
                }

                // Execute remaining batch
                if (batchCount > 0) {
                    insertStmt.executeBatch();
                    mysqlConn.commit();
                }
            }

            logger.info("Migration completed. Total: " + totalSpawners + ", Migrated: " + migratedCount + ", Failed: " + failedCount);

            // Rename the SQLite file to prevent re-migration
            if (failedCount == 0) {
                File migratedFile = new File(plugin.getDataFolder(), sqliteFileName + MIGRATED_FILE_SUFFIX);
                if (sqliteFile.renameTo(migratedFile)) {
                    logger.info("SQLite file renamed to " + sqliteFileName + MIGRATED_FILE_SUFFIX);
                } else {
                    logger.warning("Failed to rename SQLite file. Manual cleanup may be required.");
                }
            }

            return failedCount == 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during SQLite to MySQL migration", e);
            return false;
        }
    }
}
