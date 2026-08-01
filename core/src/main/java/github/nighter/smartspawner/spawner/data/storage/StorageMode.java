package github.nighter.smartspawner.spawner.data.storage;

import java.util.Locale;

/**
 * Enumeration of available storage modes for spawner data.
 *
 * <p>YAML storage was removed in 1.8. Configs still holding {@code YAML} resolve to
 * {@link #SQLITE}, and the leftover {@code spawners_data.yml} is imported once on startup.</p>
 */
public enum StorageMode {
    /**
     * SQLite database storage with HikariCP connection pool. The default.
     * Local file-based database, no external server required.
     */
    SQLITE,

    /**
     * MySQL/MariaDB database storage with HikariCP connection pool.
     * Requires database server configuration in config.yml
     * Supports cross-server spawner management.
     */
    MYSQL;

    /**
     * Resolve a configured {@code database.type} value.
     *
     * @param raw the raw config value, may be null
     * @return the matching mode, or {@link #SQLITE} when the value is unknown or the removed
     *         {@code YAML} mode
     */
    public static StorageMode fromConfig(String raw) {
        if (raw == null) {
            return SQLITE;
        }

        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SQLITE;
        }
    }
}
