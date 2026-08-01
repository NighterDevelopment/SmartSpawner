package github.nighter.smartspawner.hooks.economy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.ConfigMigrations;
import github.nighter.smartspawner.updates.YamlMigrator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ensures {@code sell_integration.yml} exists and is up-to-date. Version-less: delegates to
 * {@link YamlMigrator}, which creates the file if missing and tops up any new keys on startup.
 *
 * <p>Two moves happened in 1.8.0 and are replayed here for servers upgrading: the
 * {@code sell_integration} section of {@code config.yml} became this file, and {@code item_prices.yml}
 * became its {@code custom_prices.prices} section. Both run before the migrator so the user's own
 * values land first and the top-up only fills in what is genuinely missing.</p>
 */
public class SellIntegrationConfigUpdater {

    public static final String FILE_NAME = "sell_integration.yml";

    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String LEGACY_CONFIG_SECTION = "sell_integration";
    private static final String LEGACY_PRICE_FILE_NAME = "item_prices.yml";
    private static final String PRICES_SECTION = "custom_prices.prices";

    private final SmartSpawner plugin;

    public SellIntegrationConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /** Call this before anything reads {@code sell_integration.yml}. */
    public void checkAndUpdate() {
        if (importLegacySources(plugin.getDataFolder(), plugin.getLogger())) {
            // config.yml was rewritten on disk, so Bukkit's cached copy is stale.
            plugin.reloadConfig();
        }

        YamlMigrator.migrate(
                new File(plugin.getDataFolder(), FILE_NAME),
                plugin.getResource(FILE_NAME),
                ConfigMigrations.SELL_INTEGRATION,
                null,
                true,
                ConfigMigrations.SELL_INTEGRATION_PRICES,
                plugin.getLogger());
    }

    /**
     * Moves both 1.8.0 sources into {@code sell_integration.yml}. Takes the data folder rather than
     * the plugin so it can be exercised without a server.
     *
     * @return true when {@code config.yml} was rewritten and the caller must reload it
     */
    static boolean importLegacySources(File dataFolder, Logger log) {
        File file = new File(dataFolder, FILE_NAME);
        boolean configRewritten = importSettingsFromConfig(dataFolder, file, log);
        importLegacyPrices(dataFolder, file, log);
        return configRewritten;
    }

    // config.yml 'sell_integration' -> the top level of this file, then drops the section from config.yml.
    private static boolean importSettingsFromConfig(File dataFolder, File file, Logger log) {
        File configFile = new File(dataFolder, CONFIG_FILE_NAME);
        if (!configFile.exists()) return false;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection legacy = config.getConfigurationSection(LEGACY_CONFIG_SECTION);
        if (legacy == null) return false;

        YamlConfiguration sellIntegration = YamlConfiguration.loadConfiguration(file);
        for (String key : legacy.getKeys(true)) {
            if (legacy.isConfigurationSection(key)) continue;
            if (!sellIntegration.contains(key)) {
                sellIntegration.set(key, legacy.get(key));
            }
        }

        if (!save(sellIntegration, file, "Failed to move the sell settings into " + FILE_NAME, log)) {
            return false;
        }

        config.set(LEGACY_CONFIG_SECTION, null);
        try {
            config.save(configFile);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to remove the old sell_integration section from config.yml", e);
            return false;
        }
        log.info("Moved the sell settings from config.yml into " + FILE_NAME);
        return true;
    }

    /**
     * item_prices.yml, whose material names sat at the top level, becomes the
     * {@code custom_prices.prices} section here. The old file is deleted once every entry has been
     * written, so it cannot be read a second time and resurrect prices the owner removes later.
     */
    private static void importLegacyPrices(File dataFolder, File file, Logger log) {
        File legacyFile = new File(dataFolder, LEGACY_PRICE_FILE_NAME);
        if (!legacyFile.exists()) return;

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        YamlConfiguration sellIntegration = YamlConfiguration.loadConfiguration(file);
        for (String key : legacy.getKeys(false)) {
            if (legacy.isConfigurationSection(key)) continue;
            String path = PRICES_SECTION + "." + key;
            if (!sellIntegration.contains(path)) {
                sellIntegration.set(path, legacy.get(key));
            }
        }

        if (!save(sellIntegration, file, "Failed to move the item prices into " + FILE_NAME, log)) {
            return;
        }

        if (legacyFile.delete()) {
            log.info("Moved the prices from " + LEGACY_PRICE_FILE_NAME + " into " + FILE_NAME);
        } else {
            log.warning("Moved the prices from " + LEGACY_PRICE_FILE_NAME + " into " + FILE_NAME
                    + ", but could not delete the old file. Delete it yourself, it is no longer read.");
        }
    }

    private static boolean save(YamlConfiguration config, File file, String failureMessage, Logger log) {
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, failureMessage, e);
            return false;
        }
    }
}
