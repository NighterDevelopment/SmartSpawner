package github.nighter.smartspawner;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared helpers for the config migration tests: writing YAML into a temp data folder, reading it
 * back, and a logger that stays quiet so a passing run prints nothing.
 */
public final class ConfigFixtures {

    private ConfigFixtures() {}

    /** A logger that swallows everything, so expected warnings do not clutter the test output. */
    public static Logger silentLogger() {
        Logger logger = Logger.getLogger("SmartSpawnerTest");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return logger;
    }

    public static File write(Path folder, String fileName, String content) {
        File file = folder.resolve(fileName).toFile();
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + fileName, e);
        }
        return file;
    }

    public static YamlConfiguration read(Path folder, String fileName) {
        return YamlConfiguration.loadConfiguration(folder.resolve(fileName).toFile());
    }

    public static String readRaw(Path folder, String fileName) {
        try {
            return Files.readString(folder.resolve(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + fileName, e);
        }
    }

    /** Stands in for {@code plugin.getResource(name)}, which needs a loaded plugin jar. */
    public static InputStream resource(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The {@code database} section as 1.7.1.2 shipped it: nested, snake_case, with the HikariCP pool
     * knobs that 1.8.0 stopped exposing.
     */
    public static final String LEGACY_DATABASE_SECTION = """
            database:
              mode: SQLITE
              table_prefix: sspawner_
              server_name: server1
              sync_across_servers: false
              migrate_from_local: true
              database: smartspawner
              sqlite:
                file: spawners.db
                pool_size: 4
              sql:
                host: localhost
                port: 3306
                username: root
                password: ''
                pool:
                  maximum-size: 10
                  minimum-idle: 2
                  connection-timeout: 10000
                  max-lifetime: 1800000
                  idle-timeout: 600000
                  keepalive-time: 30000
                  leak-detection-threshold: 0
            """;

    /** The {@code sell_integration} section as it sat in 1.7.1.2's {@code config.yml}. */
    public static final String LEGACY_SELL_SECTION = """
            sell_integration:
              enabled: true
              currency: VAULT
              excellenteconomy_currency: money
              price_source_mode: SHOP_PRIORITY
              shop_integration:
                enabled: true
                preferred_plugin: auto
              custom_prices:
                enabled: true
                default_price: 1.0
            """;

    /** The 1.8.0 bundled {@code database} section, trimmed to what the tests assert on. */
    public static final String BUNDLED_DATABASE_SECTION = """
            database:
              # RESTART: every setting here except autosave-interval.
              # Backend: SQLITE or MYSQL.
              type: SQLITE

              # Prefix for this plugin's tables.
              table-prefix: sspawner_

              # How often unsaved spawner changes are written to the database. Minimum 30s.
              autosave-interval: 3m

              # SQLite: file name inside the plugin folder.
              sqlite-file: spawners.db

              # MySQL/MariaDB only.
              host: localhost
              port: 3306
              database: smartspawner
              username: root
              password: ""
              pool-size: 10

              server-name: server1
              sync-across-servers: false
              migrate-from-local: true
            """;
}
