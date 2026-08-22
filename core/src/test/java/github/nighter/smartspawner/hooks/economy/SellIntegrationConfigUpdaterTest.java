package github.nighter.smartspawner.hooks.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static github.nighter.smartspawner.ConfigFixtures.LEGACY_SELL_SECTION;
import static github.nighter.smartspawner.ConfigFixtures.read;
import static github.nighter.smartspawner.ConfigFixtures.silentLogger;
import static github.nighter.smartspawner.ConfigFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.8.0 move of the sell settings out of {@code config.yml} and of {@code item_prices.yml} into
 * {@code sell_integration.yml}. A key moving between files cannot be a {@code Rename}, so none of
 * this is covered by the migrator's own tests.
 */
class SellIntegrationConfigUpdaterTest {

    @TempDir
    Path folder;

    private static final String LEGACY_PRICES = """
            # Animal drops
            LEATHER: 4.0
            STRING: 0.75
            BONE: 1.0
            """;

    @Test
    @DisplayName("the sell settings move out of config.yml with their values")
    void settingsMoveOutOfConfig() {
        write(folder, "config.yml", "language: en_US\n"
                + LEGACY_SELL_SECTION.replace("currency: VAULT", "currency: EXCELLENTECONOMY")
                        .replace("default_price: 1.0", "default_price: 7.5"));

        assertTrue(SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger()),
                "config.yml was rewritten, so the caller has to reload it");

        YamlConfiguration sell = read(folder, "sell_integration.yml");
        assertTrue(sell.getBoolean("enabled"));
        assertEquals("EXCELLENTECONOMY", sell.getString("currency"));
        assertEquals("money", sell.getString("excellenteconomy_currency"));
        assertEquals("SHOP_PRIORITY", sell.getString("price_source_mode"));
        assertEquals("auto", sell.getString("shop_integration.preferred_plugin"));
        assertEquals(7.5, sell.getDouble("custom_prices.default_price"));
    }

    @Test
    @DisplayName("the old section is removed from config.yml, and the rest of the file is left alone")
    void oldSectionIsRemovedFromConfig() {
        write(folder, "config.yml", "language: vi_VN\n" + LEGACY_SELL_SECTION + "gui_layout: DonutSMP\n");

        SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger());

        YamlConfiguration config = read(folder, "config.yml");
        assertFalse(config.contains("sell_integration"));
        assertEquals("vi_VN", config.getString("language"));
        assertEquals("DonutSMP", config.getString("gui_layout"));
    }

    @Test
    @DisplayName("item_prices.yml becomes custom_prices.prices and is deleted")
    void pricesMoveAndTheOldFileGoes() {
        write(folder, "config.yml", "language: en_US\n");
        write(folder, "item_prices.yml", LEGACY_PRICES);

        SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger());

        YamlConfiguration sell = read(folder, "sell_integration.yml");
        assertEquals(4.0, sell.getDouble("custom_prices.prices.LEATHER"));
        assertEquals(0.75, sell.getDouble("custom_prices.prices.STRING"));
        assertEquals(1.0, sell.getDouble("custom_prices.prices.BONE"));
        assertFalse(Files.exists(folder.resolve("item_prices.yml")),
                "leaving it would re-import prices the owner removes later");
    }

    @Test
    @DisplayName("a customised price survives the move")
    void customisedPricesSurvive() {
        write(folder, "config.yml", "language: en_US\n");
        write(folder, "item_prices.yml", "LEATHER: 999.0\nDIAMOND: 500.0\n");

        SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger());

        YamlConfiguration sell = read(folder, "sell_integration.yml");
        assertEquals(999.0, sell.getDouble("custom_prices.prices.LEATHER"));
        assertEquals(500.0, sell.getDouble("custom_prices.prices.DIAMOND"));
    }

    @Test
    @DisplayName("both sources land in one file when both are present")
    void bothSourcesLandTogether() {
        write(folder, "config.yml", "language: en_US\n"
                + LEGACY_SELL_SECTION.replace("price_source_mode: SHOP_PRIORITY", "price_source_mode: CUSTOM_ONLY"));
        write(folder, "item_prices.yml", LEGACY_PRICES);

        SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger());

        YamlConfiguration sell = read(folder, "sell_integration.yml");
        assertEquals("CUSTOM_ONLY", sell.getString("price_source_mode"));
        assertEquals(4.0, sell.getDouble("custom_prices.prices.LEATHER"));
    }

    @Test
    @DisplayName("running it again changes nothing")
    void isIdempotent() {
        write(folder, "config.yml", "language: en_US\n" + LEGACY_SELL_SECTION);
        write(folder, "item_prices.yml", LEGACY_PRICES);

        SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger());
        String afterFirst = readSell();

        assertFalse(SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger()),
                "with both sources gone there is nothing left to move");
        assertEquals(afterFirst, readSell());
    }

    @Test
    @DisplayName("a value the owner already set in the new file wins over the legacy one")
    void existingValuesInTheNewFileWin() {
        write(folder, "config.yml", "language: en_US\n" + LEGACY_SELL_SECTION);
        write(folder, "sell_integration.yml", "currency: EXCELLENTECONOMY\n");

        SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger());

        assertEquals("EXCELLENTECONOMY", read(folder, "sell_integration.yml").getString("currency"));
    }

    @Test
    @DisplayName("a fresh install with neither source present does nothing")
    void freshInstallDoesNothing() {
        write(folder, "config.yml", "language: en_US\n");

        assertFalse(SellIntegrationConfigUpdater.importLegacySources(folder.toFile(), silentLogger()));
        assertFalse(Files.exists(folder.resolve("sell_integration.yml")),
                "the migrator extracts the bundled file afterwards, this step must not pre-empt it");
    }

    private String readSell() {
        try {
            return Files.readString(folder.resolve("sell_integration.yml"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
