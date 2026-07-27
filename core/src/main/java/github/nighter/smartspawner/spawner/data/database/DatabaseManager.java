package github.nighter.smartspawner.spawner.data.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.legacy.LegacyInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pool.
 * Supports SQLite (default) and MySQL/MariaDB for spawner data storage.
 */
public class DatabaseManager {
    /** Spawner rows. Renamed from {@code smart_spawners} in schema v3. */
    public static final String TABLE_SPAWNERS = "spawner_data";
    /** Plugin-owned schema metadata. Renamed from {@code smartspawner_meta} in schema v3. */
    public static final String TABLE_META = "spawner_meta";

    private static final String LEGACY_TABLE_SPAWNERS = "smart_spawners";
    private static final String LEGACY_TABLE_META = "smartspawner_meta";

    private final SmartSpawner plugin;
    private final Logger logger;
    private final StorageMode storageMode;
    private HikariDataSource dataSource;

    // Configuration values
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String serverName;
    private final String sqliteFile;
    private final int sqlitePoolSize;

    // Pool settings
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long maxLifetime;
    private final long idleTimeout;
    private final long keepaliveTime;
    private final long leakDetectionThreshold;

    // MySQL/MariaDB table creation SQL
    private static final String CREATE_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS spawner_data (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                spawner_id VARCHAR(64) NOT NULL,
                server_name VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing)
                world_name VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,

                -- Chunk coordinates, derived from loc_x/loc_z, indexed for per-chunk lookups
                chunk_x INT NOT NULL DEFAULT 0,
                chunk_z INT NOT NULL DEFAULT 0,

                -- Entity data
                entity_type VARCHAR(64) NOT NULL,
                item_spawner_material VARCHAR(64) DEFAULT NULL,

                -- Settings
                spawner_exp BIGINT NOT NULL DEFAULT 0,
                spawner_active BOOLEAN NOT NULL DEFAULT TRUE,
                spawner_range INT NOT NULL DEFAULT 16,
                spawner_stop BOOLEAN NOT NULL DEFAULT TRUE,
                spawn_delay BIGINT NOT NULL DEFAULT 500,
                max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                max_stored_exp BIGINT NOT NULL DEFAULT 1000,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                is_at_capacity BOOLEAN NOT NULL DEFAULT FALSE,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Virtual inventory, see SpawnerInventoryCodec
                items MEDIUMBLOB DEFAULT NULL,
                total_items BIGINT NOT NULL DEFAULT 0,

                -- Timestamps
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                -- Indexes
                UNIQUE KEY uk_server_spawner (server_name, spawner_id),
                UNIQUE KEY uk_location (server_name, world_name, loc_x, loc_y, loc_z),
                INDEX idx_server (server_name),
                INDEX idx_world (server_name, world_name),
                INDEX idx_chunk (server_name, world_name, chunk_x, chunk_z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    // SQLite table creation SQL (slightly different syntax)
    private static final String CREATE_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS spawner_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                spawner_id VARCHAR(64) NOT NULL,
                server_name VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing)
                world_name VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,

                -- Chunk coordinates, derived from loc_x/loc_z, indexed for per-chunk lookups
                chunk_x INT NOT NULL DEFAULT 0,
                chunk_z INT NOT NULL DEFAULT 0,

                -- Entity data
                entity_type VARCHAR(64) NOT NULL,
                item_spawner_material VARCHAR(64) DEFAULT NULL,

                -- Settings
                spawner_exp BIGINT NOT NULL DEFAULT 0,
                spawner_active BOOLEAN NOT NULL DEFAULT 1,
                spawner_range INT NOT NULL DEFAULT 16,
                spawner_stop BOOLEAN NOT NULL DEFAULT 1,
                spawn_delay BIGINT NOT NULL DEFAULT 500,
                max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                max_stored_exp BIGINT NOT NULL DEFAULT 1000,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                is_at_capacity BOOLEAN NOT NULL DEFAULT 0,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Virtual inventory, see SpawnerInventoryCodec
                items BLOB DEFAULT NULL,
                total_items BIGINT NOT NULL DEFAULT 0,

                -- Timestamps
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                -- Unique constraints
                UNIQUE (server_name, spawner_id),
                UNIQUE (server_name, world_name, loc_x, loc_y, loc_z)
            )
            """;

    // SQLite index creation (separate statements)
    private static final String CREATE_INDEX_SERVER_SQLITE =
            "CREATE INDEX IF NOT EXISTS idx_server ON spawner_data (server_name)";
    private static final String CREATE_INDEX_WORLD_SQLITE =
            "CREATE INDEX IF NOT EXISTS idx_world ON spawner_data (server_name, world_name)";
    private static final String CREATE_INDEX_CHUNK_SQLITE =
            "CREATE INDEX IF NOT EXISTS idx_chunk ON spawner_data (server_name, world_name, chunk_x, chunk_z)";

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final int CURRENT_SCHEMA_VERSION = 3;

    /** Rows converted per transaction while rewriting inventories during the v3 migration. */
    private static final int MIGRATION_BATCH_SIZE = 250;

    private static final String CREATE_META_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS spawner_meta (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(64) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String CREATE_META_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS spawner_meta (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(64) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    public DatabaseManager(SmartSpawner plugin, StorageMode storageMode) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageMode = storageMode;

        // Load configuration
        this.host = plugin.getConfig().getString("database.sql.host", "localhost");
        this.port = plugin.getConfig().getInt("database.sql.port", 3306);
        this.database = plugin.getConfig().getString("database.database", "smartspawner");
        this.username = plugin.getConfig().getString("database.sql.username", "root");
        this.password = plugin.getConfig().getString("database.sql.password", "");
        this.serverName = plugin.getConfig().getString("database.server_name", "server1");
        this.sqliteFile = plugin.getConfig().getString("database.sqlite.file", "spawners.db");
        this.sqlitePoolSize = Math.max(1, plugin.getConfig().getInt("database.sqlite.pool_size", 4));

        // Pool settings
        this.maxPoolSize = plugin.getConfig().getInt("database.sql.pool.maximum-size", 10);
        this.minIdle = plugin.getConfig().getInt("database.sql.pool.minimum-idle", 2);
        this.connectionTimeout = plugin.getConfig().getLong("database.sql.pool.connection-timeout", 10000);
        this.maxLifetime = plugin.getConfig().getLong("database.sql.pool.max-lifetime", 1800000);
        this.idleTimeout = plugin.getConfig().getLong("database.sql.pool.idle-timeout", 600000);
        this.keepaliveTime = plugin.getConfig().getLong("database.sql.pool.keepalive-time", 30000);
        this.leakDetectionThreshold = plugin.getConfig().getLong("database.sql.pool.leak-detection-threshold", 0);
    }

    /**
     * Initialize the database connection pool and create tables.
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            setupDataSource();
            // Renaming has to happen before anything reads the schema version, because the meta
            // table holding that version is itself one of the renamed tables.
            renameLegacyTables();
            createTables();
            createSchemaMetaTable();
            runSchemaMigrations();
            logger.info("Database connection pool initialized successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection pool", e);
            return false;
        }
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        if (storageMode == StorageMode.SQLITE) {
            setupSQLiteDataSource(config);
        } else {
            setupMySQLDataSource(config);
        }

        dataSource = new HikariDataSource(config);
    }

    private void setupMySQLDataSource(HikariConfig config) {
        // JDBC URL for MariaDB/MySQL
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("github.nighter.smartspawner.libs.mariadb.Driver");
        config.setUsername(username);
        config.setPassword(password);

        // Pool settings
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setIdleTimeout(idleTimeout);
        config.setKeepaliveTime(keepaliveTime);
        config.setLeakDetectionThreshold(leakDetectionThreshold);

        // Performance settings for MySQL/MariaDB
        config.setPoolName("SmartSpawner-HikariCP");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    private void setupSQLiteDataSource(HikariConfig config) {
        // Create data folder if it doesn't exist
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Pragmas go in the JDBC URL because the xerial driver builds its SQLiteConfig from the URL
        // query string. WAL lets readers run while the batched flush holds the write lock, and
        // busy_timeout is what stops a concurrent reader from failing outright with SQLITE_BUSY.
        File dbFile = new File(dataFolder, sqliteFile);
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                + "?journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&busy_timeout=5000"
                + "&foreign_keys=true"
                + "&cache_size=-16000"
                + "&temp_store=MEMORY";

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        config.setMaximumPoolSize(sqlitePoolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(0);  // Disable max lifetime for SQLite
        config.setIdleTimeout(0);  // Disable idle timeout for SQLite
        config.setPoolName("SmartSpawner-SQLite-HikariCP");
    }

    /**
     * Move pre-v3 table names onto the {@code spawner_*} prefix. No-op on a fresh install and on
     * databases that were already renamed.
     */
    private void renameLegacyTables() throws SQLException {
        renameTableIfNeeded(LEGACY_TABLE_META, TABLE_META);
        renameTableIfNeeded(LEGACY_TABLE_SPAWNERS, TABLE_SPAWNERS);
    }

    private void renameTableIfNeeded(String from, String to) throws SQLException {
        if (!tableExists(from) || tableExists(to)) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + from + " RENAME TO " + to);
        }
        logger.info("Renamed database table " + from + " to " + to + ".");
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName, null)) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (storageMode == StorageMode.SQLITE) {
                stmt.execute(CREATE_TABLE_SQLITE);
            } else {
                stmt.execute(CREATE_TABLE_MYSQL);
            }

            plugin.debug("Database tables created/verified successfully.");
        }
    }

    /**
     * SQLite indexes are separate statements, and the chunk index can only be created once the
     * chunk columns exist, so this runs after migrations rather than with the table creation.
     */
    private void createSqliteIndexes() throws SQLException {
        if (storageMode != StorageMode.SQLITE) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_INDEX_SERVER_SQLITE);
            stmt.execute(CREATE_INDEX_WORLD_SQLITE);
            stmt.execute(CREATE_INDEX_CHUNK_SQLITE);
        }
    }

    private void createSchemaMetaTable() throws SQLException {
        String createSql = storageMode == StorageMode.SQLITE ? CREATE_META_TABLE_SQLITE : CREATE_META_TABLE_MYSQL;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
        }
    }

    private void runSchemaMigrations() throws SQLException {
        Integer currentVersion = getSchemaVersionFromMeta();
        if (currentVersion == null) {
            currentVersion = detectInitialSchemaVersion();
            setSchemaVersion(currentVersion);
            logger.info("Initialized database schema version at v" + currentVersion + ".");
        }

        if (currentVersion > CURRENT_SCHEMA_VERSION) {
            logger.warning("Database schema version v" + currentVersion + " is newer than plugin-supported v" + CURRENT_SCHEMA_VERSION + ".");
            return;
        }

        while (currentVersion < CURRENT_SCHEMA_VERSION) {
            int targetVersion = currentVersion + 1;
            logger.info("Applying database schema migration v" + currentVersion + " -> v" + targetVersion + "...");
            applyMigrationStep(targetVersion);
            setSchemaVersion(targetVersion);
            currentVersion = targetVersion;
            logger.info("Database schema migration completed to v" + currentVersion + ".");
        }

        createSqliteIndexes();
    }

    private Integer getSchemaVersionFromMeta() throws SQLException {
        String sql = "SELECT meta_value FROM " + TABLE_META + " WHERE meta_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, SCHEMA_VERSION_KEY);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String rawVersion = rs.getString("meta_value");
                try {
                    return Integer.parseInt(rawVersion);
                } catch (NumberFormatException ex) {
                    throw new SQLException("Invalid database schema version value: " + rawVersion, ex);
                }
            }
        }
    }

    private int detectInitialSchemaVersion() throws SQLException {
        if (columnExists(TABLE_SPAWNERS, "items")) {
            return CURRENT_SCHEMA_VERSION;
        }
        return xpColumnsRequireMigration() ? LEGACY_SCHEMA_VERSION : 2;
    }

    private void setSchemaVersion(int version) throws SQLException {
        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO " + TABLE_META + " (meta_key, meta_value) VALUES (?, ?) ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value"
                : "INSERT INTO " + TABLE_META + " (meta_key, meta_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, SCHEMA_VERSION_KEY);
            stmt.setString(2, String.valueOf(version));
            stmt.executeUpdate();
        }
    }

    private void applyMigrationStep(int targetVersion) throws SQLException {
        switch (targetVersion) {
            case 2 -> migrateXpColumnsToBigIntIfNeeded();
            case 3 -> migrateToChunkAndItemBlobColumns();
            default -> throw new SQLException("No database migration handler found for schema version: " + targetVersion);
        }
    }

    // ============== Schema v2: XP columns to BIGINT ==============

    private void migrateXpColumnsToBigIntIfNeeded() throws SQLException {
        if (!xpColumnsRequireMigration()) {
            return;
        }

        String backupName = createPreMigrationBackup("bigint");
        logger.info("Created database backup before XP BIGINT migration: " + backupName);

        if (storageMode == StorageMode.SQLITE) {
            migrateSQLiteXpColumnsToBigInt();
        } else {
            migrateMySqlXpColumnsToBigInt();
        }

        logger.info("Successfully migrated XP columns to BIGINT.");
    }

    private boolean xpColumnsRequireMigration() throws SQLException {
        return storageMode == StorageMode.SQLITE
                ? sqliteXpColumnsRequireMigration()
                : mySqlXpColumnsRequireMigration();
    }

    private boolean mySqlXpColumnsRequireMigration() throws SQLException {
        String sql = """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_name IN ('spawner_exp', 'max_stored_exp')
                """;

        boolean needsMigration = false;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, database);
            stmt.setString(2, TABLE_SPAWNERS);
            try (ResultSet rs = stmt.executeQuery()) {
                int seen = 0;
                while (rs.next()) {
                    seen++;
                    String type = rs.getString("data_type");
                    if (type == null || !"bigint".equalsIgnoreCase(type)) {
                        needsMigration = true;
                    }
                }
                if (seen < 2) {
                    needsMigration = true;
                }
            }
        }
        return needsMigration;
    }

    private boolean sqliteXpColumnsRequireMigration() throws SQLException {
        String sql = "PRAGMA table_info(" + TABLE_SPAWNERS + ")";
        boolean spawnerExpBigInt = false;
        boolean maxStoredExpBigInt = false;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean isBigInt = type != null && type.equalsIgnoreCase("BIGINT");

                if ("spawner_exp".equalsIgnoreCase(name)) {
                    spawnerExpBigInt = isBigInt;
                } else if ("max_stored_exp".equalsIgnoreCase(name)) {
                    maxStoredExpBigInt = isBigInt;
                }
            }
        }

        return !(spawnerExpBigInt && maxStoredExpBigInt);
    }

    private String createPreMigrationBackup(String label) throws SQLException {
        String backupTableName = TABLE_SPAWNERS + "_backup_" + label + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if (storageMode == StorageMode.SQLITE) {
            String backupSql = "CREATE TABLE " + backupTableName + " AS SELECT * FROM " + TABLE_SPAWNERS;
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(backupSql);
            }
        } else {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + backupTableName + " LIKE " + TABLE_SPAWNERS);
                stmt.execute("INSERT INTO " + backupTableName + " SELECT * FROM " + TABLE_SPAWNERS);
            }
        }

        return backupTableName;
    }

    private void migrateMySqlXpColumnsToBigInt() throws SQLException {
        String alterSql = """
                ALTER TABLE spawner_data
                    MODIFY COLUMN spawner_exp BIGINT NOT NULL DEFAULT 0,
                    MODIFY COLUMN max_stored_exp BIGINT NOT NULL DEFAULT 1000
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        }
    }

    private void migrateSQLiteXpColumnsToBigInt() throws SQLException {
        // Recreates the table in its v2 shape. The v3 step below then adds the chunk and item
        // columns on top, so this deliberately keeps the old inventory_data column.
        String[] migrationSql = {
                "BEGIN IMMEDIATE TRANSACTION",
                "ALTER TABLE " + TABLE_SPAWNERS + " RENAME TO spawner_data_pre_bigint",
                """
                CREATE TABLE spawner_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    spawner_id VARCHAR(64) NOT NULL,
                    server_name VARCHAR(64) NOT NULL,
                    world_name VARCHAR(128) NOT NULL,
                    loc_x INT NOT NULL,
                    loc_y INT NOT NULL,
                    loc_z INT NOT NULL,
                    entity_type VARCHAR(64) NOT NULL,
                    item_spawner_material VARCHAR(64) DEFAULT NULL,
                    spawner_exp BIGINT NOT NULL DEFAULT 0,
                    spawner_active BOOLEAN NOT NULL DEFAULT 1,
                    spawner_range INT NOT NULL DEFAULT 16,
                    spawner_stop BOOLEAN NOT NULL DEFAULT 1,
                    spawn_delay BIGINT NOT NULL DEFAULT 500,
                    max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                    max_stored_exp BIGINT NOT NULL DEFAULT 1000,
                    min_mobs INT NOT NULL DEFAULT 1,
                    max_mobs INT NOT NULL DEFAULT 4,
                    stack_size INT NOT NULL DEFAULT 1,
                    max_stack_size INT NOT NULL DEFAULT 1000,
                    last_spawn_time BIGINT NOT NULL DEFAULT 0,
                    is_at_capacity BOOLEAN NOT NULL DEFAULT 0,
                    last_interacted_player VARCHAR(64) DEFAULT NULL,
                    preferred_sort_item VARCHAR(64) DEFAULT NULL,
                    filtered_items TEXT DEFAULT NULL,
                    inventory_data TEXT DEFAULT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (server_name, spawner_id),
                    UNIQUE (server_name, world_name, loc_x, loc_y, loc_z)
                )
                """,
                """
                INSERT INTO spawner_data (
                    id, spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
                    entity_type, item_spawner_material, spawner_exp, spawner_active,
                    spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                    max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                    last_spawn_time, is_at_capacity, last_interacted_player,
                    preferred_sort_item, filtered_items, inventory_data,
                    created_at, updated_at
                )
                SELECT
                    id, spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
                    entity_type, item_spawner_material, spawner_exp, spawner_active,
                    spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                    max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                    last_spawn_time, is_at_capacity, last_interacted_player,
                    preferred_sort_item, filtered_items, inventory_data,
                    created_at, updated_at
                FROM spawner_data_pre_bigint
                """,
                "DROP TABLE spawner_data_pre_bigint",
                CREATE_INDEX_SERVER_SQLITE,
                CREATE_INDEX_WORLD_SQLITE,
                "COMMIT"
        };

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : migrationSql) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            try (Connection rollbackConn = getConnection();
                 Statement rollbackStmt = rollbackConn.createStatement()) {
                rollbackStmt.execute("ROLLBACK");
            } catch (SQLException rollbackEx) {
                logger.log(Level.SEVERE, "Failed to rollback SQLite BIGINT migration", rollbackEx);
            }
            throw e;
        }
    }

    // ============== Schema v3: chunk columns and item blob ==============

    /**
     * Adds the chunk coordinates and the binary inventory columns, then rewrites every stored
     * inventory from the legacy string format into {@link SpawnerInventoryCodec}.
     */
    private void migrateToChunkAndItemBlobColumns() throws SQLException {
        boolean hasLegacyInventory = columnExists(TABLE_SPAWNERS, "inventory_data");

        if (hasLegacyInventory && countRows() > 0) {
            String backupName = createPreMigrationBackup("items");
            logger.info("Created database backup before inventory format migration: " + backupName);
        }

        addColumnIfMissing("chunk_x", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("chunk_z", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("items", storageMode == StorageMode.SQLITE ? "BLOB DEFAULT NULL" : "MEDIUMBLOB DEFAULT NULL");
        addColumnIfMissing("total_items", "BIGINT NOT NULL DEFAULT 0");

        backfillChunkColumns();

        if (hasLegacyInventory) {
            int converted = convertLegacyInventories();
            logger.info("Converted " + converted + " spawner inventories to the binary item format.");
            dropLegacyInventoryColumn();
        }

        if (storageMode != StorageMode.SQLITE) {
            createMySqlChunkIndexIfMissing();
        }
    }

    private long countRows() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_SPAWNERS)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private void addColumnIfMissing(String columnName, String definition) throws SQLException {
        if (columnExists(TABLE_SPAWNERS, columnName)) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + TABLE_SPAWNERS + " ADD COLUMN " + columnName + " " + definition);
        }
        plugin.debug("Added column " + columnName + " to " + TABLE_SPAWNERS);
    }

    /**
     * Derives chunk coordinates from block coordinates. Done in Java rather than SQL because the
     * arithmetic-shift semantics of {@code >>} on negative values differ between SQLite and MySQL.
     */
    private void backfillChunkColumns() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT id, loc_x, loc_z FROM " + TABLE_SPAWNERS);
                 PreparedStatement update = conn.prepareStatement(
                         "UPDATE " + TABLE_SPAWNERS + " SET chunk_x = ?, chunk_z = ? WHERE id = ?")) {

                int pending = 0;
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        update.setInt(1, rs.getInt("loc_x") >> 4);
                        update.setInt(2, rs.getInt("loc_z") >> 4);
                        update.setLong(3, rs.getLong("id"));
                        update.addBatch();

                        if (++pending >= MIGRATION_BATCH_SIZE) {
                            update.executeBatch();
                            pending = 0;
                        }
                    }
                }

                if (pending > 0) {
                    update.executeBatch();
                }
            }

            conn.commit();
        }
    }

    /**
     * Rewrites {@code inventory_data} into the {@code items} blob. Rows whose legacy payload cannot
     * be parsed are left with a null blob and logged, rather than failing the whole migration.
     */
    private int convertLegacyInventories() throws SQLException {
        int converted = 0;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT id, spawner_id, inventory_data FROM " + TABLE_SPAWNERS
                            + " WHERE inventory_data IS NOT NULL AND inventory_data <> ''");
                 PreparedStatement update = conn.prepareStatement(
                         "UPDATE " + TABLE_SPAWNERS + " SET items = ?, total_items = ? WHERE id = ?")) {

                int pending = 0;
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        long rowId = rs.getLong("id");
                        String spawnerId = rs.getString("spawner_id");
                        String legacy = rs.getString("inventory_data");

                        byte[] blob;
                        long total;
                        try {
                            Map<ItemStack, Long> items = LegacyInventoryCodec.deserialize(
                                    LegacyInventoryCodec.parseJsonArray(legacy));
                            blob = encodeLegacyItems(items);
                            total = 0L;
                            for (Long amount : items.values()) {
                                total += amount;
                            }
                        } catch (Exception e) {
                            logger.warning("Could not convert stored inventory for spawner " + spawnerId
                                    + ", it will be empty after migration: " + e.getMessage());
                            blob = null;
                            total = 0L;
                        }

                        update.setBytes(1, blob);
                        update.setLong(2, total);
                        update.setLong(3, rowId);
                        update.addBatch();
                        converted++;

                        if (++pending >= MIGRATION_BATCH_SIZE) {
                            update.executeBatch();
                            pending = 0;
                        }
                    }
                }

                if (pending > 0) {
                    update.executeBatch();
                }
            }

            conn.commit();
        }

        return converted;
    }

    /**
     * Encodes legacy item templates. They carry no signature-relevant metadata, so they are wrapped
     * in the same layout {@link SpawnerInventoryCodec} produces by round-tripping through a
     * consolidated map keyed on fresh signatures.
     */
    private byte[] encodeLegacyItems(Map<ItemStack, Long> items) throws Exception {
        if (items.isEmpty()) {
            return null;
        }

        Map<github.nighter.smartspawner.spawner.properties.ItemSignature, Long> consolidated =
                new LinkedHashMap<>(Math.max(16, items.size() * 2));
        for (Map.Entry<ItemStack, Long> entry : items.entrySet()) {
            consolidated.merge(
                    new github.nighter.smartspawner.spawner.properties.ItemSignature(entry.getKey()),
                    entry.getValue(),
                    Long::sum);
        }

        return SpawnerInventoryCodec.encode(consolidated);
    }

    private void dropLegacyInventoryColumn() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + TABLE_SPAWNERS + " DROP COLUMN inventory_data");
            plugin.debug("Dropped legacy inventory_data column.");
        } catch (SQLException e) {
            // Harmless if it stays: nothing reads or writes it any more.
            logger.warning("Could not drop the legacy inventory_data column, leaving it in place: " + e.getMessage());
        }
    }

    private void createMySqlChunkIndexIfMissing() throws SQLException {
        List<String> existingIndexes = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), null, TABLE_SPAWNERS, false, false)) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    if (name != null) {
                        existingIndexes.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        if (existingIndexes.contains("idx_chunk")) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX idx_chunk ON " + TABLE_SPAWNERS
                    + " (server_name, world_name, chunk_x, chunk_z)");
        }
    }

    /**
     * Get a connection from the pool.
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Get the configured server name for this server.
     * @return The server name used to identify spawners
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Get the storage mode this manager is configured for.
     * @return The storage mode (MYSQL or SQLITE)
     */
    public StorageMode getStorageMode() {
        return storageMode;
    }

    /**
     * Check if the database connection pool is active.
     * @return true if the pool is active and accepting connections
     */
    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Shutdown the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
