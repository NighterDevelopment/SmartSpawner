package github.nighter.smartspawner.updates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static github.nighter.smartspawner.ConfigFixtures.BUNDLED_DATABASE_SECTION;
import static github.nighter.smartspawner.ConfigFixtures.LEGACY_DATABASE_SECTION;
import static github.nighter.smartspawner.ConfigFixtures.LEGACY_SELL_SECTION;
import static github.nighter.smartspawner.ConfigFixtures.read;
import static github.nighter.smartspawner.ConfigFixtures.resource;
import static github.nighter.smartspawner.ConfigFixtures.silentLogger;
import static github.nighter.smartspawner.ConfigFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.8.0 {@code config.yml} migrations. Every case here is a value a 1.7.1.2 server has set that
 * must survive the upgrade; a regression is silent, because the plugin starts happily on the shipped
 * default it would put in the missing key's place.
 */
class ConfigMigrationsTest {

    @TempDir
    Path folder;

    /** Runs the real config.yml migration over {@code userYaml}, against {@code bundled} defaults. */
    private YamlConfiguration migrate(String userYaml, String bundled) {
        write(folder, "config.yml", userYaml);
        YamlMigrator.migrate(
                folder.resolve("config.yml").toFile(),
                bundled == null ? null : resource(bundled),
                ConfigMigrations.CONFIG,
                ConfigMigrations.CONFIG_VALUES,
                silentLogger());
        return read(folder, "config.yml");
    }

    @Nested
    @DisplayName("database section, flattened and kebab-cased in 1.8.0")
    class Database {

        @Test
        @DisplayName("every 1.7.1.2 key reaches its 1.8.0 name")
        void everyLegacyKeyIsRenamed() {
            YamlConfiguration result = migrate(LEGACY_DATABASE_SECTION, BUNDLED_DATABASE_SECTION);

            assertEquals("SQLITE", result.getString("database.type"));
            assertEquals("sspawner_", result.getString("database.table-prefix"));
            assertEquals("server1", result.getString("database.server-name"));
            assertFalse(result.getBoolean("database.sync-across-servers"));
            assertTrue(result.getBoolean("database.migrate-from-local"));
            assertEquals("spawners.db", result.getString("database.sqlite-file"));
            assertEquals("localhost", result.getString("database.host"));
            assertEquals(3306, result.getInt("database.port"));
            assertEquals("root", result.getString("database.username"));
            assertEquals(10, result.getInt("database.pool-size"));
            assertEquals("smartspawner", result.getString("database.database"));
        }

        @Test
        @DisplayName("customised values survive the rename rather than reverting to the default")
        void customisedValuesSurvive() {
            String user = LEGACY_DATABASE_SECTION
                    .replace("mode: SQLITE", "mode: MYSQL")
                    .replace("host: localhost", "host: db.example.net")
                    .replace("port: 3306", "port: 3307")
                    .replace("table_prefix: sspawner_", "table_prefix: ss_prod_")
                    .replace("maximum-size: 10", "maximum-size: 25");

            YamlConfiguration result = migrate(user, BUNDLED_DATABASE_SECTION);

            assertEquals("MYSQL", result.getString("database.type"));
            assertEquals("db.example.net", result.getString("database.host"));
            assertEquals(3307, result.getInt("database.port"));
            assertEquals("ss_prod_", result.getString("database.table-prefix"));
            assertEquals(25, result.getInt("database.pool-size"));
        }

        @Test
        @DisplayName("the old nested sections are removed once their keys have moved")
        void nestedSectionsAreRemoved() {
            YamlConfiguration result = migrate(LEGACY_DATABASE_SECTION, BUNDLED_DATABASE_SECTION);

            assertFalse(result.contains("database.sql"),
                    "leaving it would keep two spellings of host, port and the pool size");
            assertFalse(result.contains("database.sqlite"));
        }

        @Test
        @DisplayName("autosave-interval is added with the shipped default")
        void autosaveIntervalIsAdded() {
            assertEquals("3m", migrate(LEGACY_DATABASE_SECTION, BUNDLED_DATABASE_SECTION)
                    .getString("database.autosave-interval"));
        }

        @Test
        @DisplayName("the rebuilt section carries the shipped comments and key order")
        void theRebuiltSectionIsDocumented() {
            YamlConfiguration result = migrate(LEGACY_DATABASE_SECTION, BUNDLED_DATABASE_SECTION);

            assertEquals(List.of(
                            "RESTART: every setting here except autosave-interval.",
                            "Backend: SQLITE or MYSQL."),
                    result.getComments("database.type"),
                    "an upgraded section with no comments is as good as undocumented");
            assertEquals(List.of("type", "table-prefix", "autosave-interval", "sqlite-file", "host",
                            "port", "database", "username", "password", "pool-size", "server-name",
                            "sync-across-servers", "migrate-from-local"),
                    List.copyOf(result.getConfigurationSection("database").getKeys(false)));
        }

        @Test
        @DisplayName("an already-converted section is left exactly as it is")
        void anAlreadyConvertedSectionIsNotRebuilt() {
            String converted = BUNDLED_DATABASE_SECTION.replace("pool-size: 10", "pool-size: 42");

            YamlConfiguration first = migrate(converted, BUNDLED_DATABASE_SECTION);
            assertEquals(42, first.getInt("database.pool-size"));

            // Running the whole migration a second time must be a no-op, or every start rewrites the file.
            assertFalse(YamlMigrator.migrate(folder.resolve("config.yml").toFile(),
                            resource(BUNDLED_DATABASE_SECTION), ConfigMigrations.CONFIG,
                            ConfigMigrations.CONFIG_VALUES, silentLogger()),
                    "the migration must be idempotent");
        }

        @Test
        @DisplayName("a key the owner added themselves is kept")
        void unknownKeysAreKept() {
            YamlConfiguration result = migrate(
                    LEGACY_DATABASE_SECTION + "  my-own-note: keep me\n", BUNDLED_DATABASE_SECTION);

            assertEquals("keep me", result.getString("database.my-own-note"));
        }

        @Test
        @DisplayName("the pre-1.7.1 standalone keys still reach the current names")
        void twoHopRenameFromStandalone() {
            String ancient = """
                    database:
                      mode: MYSQL
                      standalone:
                        host: old.example.net
                        port: 3310
                        username: legacy
                        password: secret
                        pool:
                          maximum-size: 15
                    """;

            YamlConfiguration result = migrate(ancient, BUNDLED_DATABASE_SECTION);

            assertEquals("old.example.net", result.getString("database.host"));
            assertEquals(3310, result.getInt("database.port"));
            assertEquals("legacy", result.getString("database.username"));
            assertEquals("secret", result.getString("database.password"));
            assertEquals(15, result.getInt("database.pool-size"));
        }
    }

    @Nested
    @DisplayName("value migrations")
    class Values {

        @Test
        @DisplayName("the removed YAML storage mode resolves to SQLITE")
        void yamlModeBecomesSqlite() {
            assertEquals("SQLITE",
                    migrate("database:\n  mode: YAML\n", null).getString("database.type"));
        }

        @Test
        @DisplayName("the old DATABASE mode name becomes MYSQL")
        void databaseModeBecomesMysql() {
            assertEquals("MYSQL",
                    migrate("database:\n  mode: DATABASE\n", null).getString("database.type"));
        }

        @Test
        @DisplayName("COINSENGINE becomes EXCELLENTECONOMY")
        void coinsEngineIsRenamed() {
            assertEquals("EXCELLENTECONOMY",
                    migrate("sell_integration:\n  currency: COINSENGINE\n", null)
                            .getString("sell_integration.currency"));
        }

        @Test
        @DisplayName("the custom_economy section becomes sell_integration")
        void customEconomyIsRenamed() {
            String user = """
                    custom_economy:
                      enabled: false
                      currency: COINSENGINE
                      coinsengine_currency: gems
                      price_source_mode: CUSTOM_ONLY
                      shop_integration:
                        enabled: false
                        preferred_plugin: zShop
                      custom_prices:
                        enabled: true
                        default_price: 3.5
                    """;

            YamlConfiguration result = migrate(user, null);

            assertFalse(result.contains("custom_economy"));
            assertFalse(result.getBoolean("sell_integration.enabled"));
            assertEquals("EXCELLENTECONOMY", result.getString("sell_integration.currency"));
            assertEquals("gems", result.getString("sell_integration.excellenteconomy_currency"));
            assertEquals("CUSTOM_ONLY", result.getString("sell_integration.price_source_mode"));
            assertEquals("zShop", result.getString("sell_integration.shop_integration.preferred_plugin"));
            assertEquals(3.5, result.getDouble("sell_integration.custom_prices.default_price"));
        }

        @Test
        @DisplayName("the renamed break-on-sell key keeps its value")
        void sellAndXpBreakIsRenamed() {
            assertFalse(migrate("spawner_break:\n  auto_sell_and_claim_exp_on_break: false\n", null)
                    .getBoolean("spawner_break.sell_and_xp_break"));
        }
    }

    @Nested
    @DisplayName("sell_integration.yml prices are the owner's list")
    class SellIntegrationPrices {

        private static final String BUNDLED = """
                enabled: true
                custom_prices:
                  enabled: true
                  default_price: 1.0
                  prices:
                    LEATHER: 4.0
                    BONE: 1.0
                """;

        @Test
        @DisplayName("an edited price is kept and a deleted material is not added back")
        void editedAndDeletedPricesStick() {
            write(folder, "sell.yml", """
                    enabled: true
                    custom_prices:
                      enabled: true
                      default_price: 1.0
                      prices:
                        LEATHER: 99.0
                    """);

            YamlMigrator.migrate(folder.resolve("sell.yml").toFile(), resource(BUNDLED),
                    ConfigMigrations.SELL_INTEGRATION, null, true,
                    ConfigMigrations.SELL_INTEGRATION_PRICES, silentLogger());

            YamlConfiguration result = read(folder, "sell.yml");
            assertEquals(99.0, result.getDouble("custom_prices.prices.LEATHER"));
            assertFalse(result.contains("custom_prices.prices.BONE"),
                    "a material the owner removed must not come back on the next start");
        }

        @Test
        @DisplayName("a new setting outside the price list is still added")
        void settingsOutsideThePriceListAreStillToppedUp() {
            write(folder, "sell.yml", "custom_prices:\n  prices:\n    LEATHER: 4.0\n");

            YamlMigrator.migrate(folder.resolve("sell.yml").toFile(), resource(BUNDLED),
                    ConfigMigrations.SELL_INTEGRATION, null, true,
                    ConfigMigrations.SELL_INTEGRATION_PRICES, silentLogger());

            YamlConfiguration result = read(folder, "sell.yml");
            assertTrue(result.getBoolean("enabled"));
            assertEquals(1.0, result.getDouble("custom_prices.default_price"));
        }
    }

    @Test
    @DisplayName("a 1.7.1.2 config keeps every sell value for the file move that follows")
    void sellSectionSurvivesUntilTheFileMove() {
        String user = LEGACY_SELL_SECTION.replace("default_price: 1.0", "default_price: 7.5");

        YamlConfiguration result = migrate(user, "language: en_US\n");

        assertEquals(7.5, result.getDouble("sell_integration.custom_prices.default_price"),
                "SellIntegrationConfigUpdater reads these straight after, so they must still be here");
    }
}
