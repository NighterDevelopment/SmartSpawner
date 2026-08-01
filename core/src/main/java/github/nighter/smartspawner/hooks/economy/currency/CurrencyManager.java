package github.nighter.smartspawner.hooks.economy.currency;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.currency.operation.NotificationTarget;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;

import java.util.logging.Level;

public class CurrencyManager {
    private final SmartSpawner plugin;

    @Getter
    private boolean currencyAvailable = false;

    @Getter
    private String activeCurrencyProvider = "None";

    private Economy vaultEconomy;

    private ExcellentEconomyAPI excellentEconomyApi;

    private ExcellentCurrency excellentEconomyCurrency;

    private OperationContext excellentEconomyContext;

    @Getter
    private String configuredCurrencyType;

    @Getter
    private String configuredExcellentEconomyCurrency;

    public CurrencyManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfiguration();
        setupCurrency();
    }

    private void loadConfiguration() {
        // sell_integration.yml, held by ItemPriceManager, which builds this class.
        FileConfiguration config = plugin.getItemPriceManager().getSellConfig();
        this.configuredCurrencyType = config.getString("currency", "VAULT");
        this.configuredExcellentEconomyCurrency = config.getString("excellenteconomy_currency", "coins");
    }

    private void setupCurrency() {
        currencyAvailable = false;
        activeCurrencyProvider = "None";

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            currencyAvailable = setupVaultEconomy();

        } else if (configuredCurrencyType.equalsIgnoreCase("EXCELLENTECONOMY")) {
            currencyAvailable = setupExcellentEconomy();

        } else {
            plugin.getLogger().warning("Unsupported currency type: " + configuredCurrencyType + ". Supported types: VAULT, EXCELLENTECONOMY.");
            plugin.getLogger().warning("Economy features will be disabled.");
        }
    }

    private boolean setupVaultEconomy() {
        // Check if Vault plugin is available
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Selling items from spawner will be disabled.");
            return false;
        }

        try {
            // Get economy service provider
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                plugin.getLogger().warning("No economy provider found for Vault! Selling items from spawner will be disabled.");
                return false;
            }

            vaultEconomy = rsp.getProvider();
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Failed to get economy provider from Vault! Selling items from spawner will be disabled.");
                return false;
            }

            activeCurrencyProvider = "Vault (" + vaultEconomy.getName() + ")";
            plugin.getLogger().info("Successfully connected to Vault & Economy provider: " + vaultEconomy.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up Vault economy integration", e);
            return false;
        }
    }

    private boolean setupExcellentEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("ExcellentEconomy") == null) {
            plugin.getLogger().warning("ExcellentEconomy not found! Selling items from spawner will be disabled.");
            return false;
        }

        try {
            RegisteredServiceProvider<ExcellentEconomyAPI> provider = plugin.getServer()
                    .getServicesManager()
                    .getRegistration(ExcellentEconomyAPI.class);
            if (provider == null) {
                plugin.getLogger().warning("No ExcellentEconomy API provider found! Selling items from spawner will be disabled.");
                return false;
            }

            excellentEconomyApi = provider.getProvider();
            if (excellentEconomyApi == null) {
                plugin.getLogger().warning("Failed to get ExcellentEconomy API provider! Selling items from spawner will be disabled.");
                return false;
            }

            excellentEconomyCurrency = excellentEconomyApi.currencyById(configuredExcellentEconomyCurrency).orElse(null);
            if (excellentEconomyCurrency == null) {
                plugin.getLogger().warning("Could not find ExcellentEconomy currency '" + configuredExcellentEconomyCurrency + "'. Selling items from spawner will be disabled.");
                return false;
            }

            excellentEconomyContext = OperationContext.custom(plugin.getName())
                    .silentFor(NotificationTarget.USER, NotificationTarget.EXECUTOR, NotificationTarget.CONSOLE_LOGGER);

            activeCurrencyProvider = "ExcellentEconomy (" + excellentEconomyCurrency.getName() + ")";
            plugin.getLogger().info("Successfully connected to ExcellentEconomy with currency: " + excellentEconomyCurrency.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up ExcellentEconomy integration", e);
            return false;
        }
    }

    public boolean deposit(double amount, Player player) {
        if (!currencyAvailable) {
            plugin.getLogger().warning("Currency not available for deposit operation.");
            return false;
        }

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Vault economy is not initialized.");
                return false;
            }
            return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
        }

        if (configuredCurrencyType.equalsIgnoreCase("EXCELLENTECONOMY")) {
            if (excellentEconomyApi == null || excellentEconomyCurrency == null || excellentEconomyContext == null) {
                plugin.getLogger().warning("ExcellentEconomy is not initialized.");
                return false;
            }

            if (!excellentEconomyApi.canPerformOperations()) {
                plugin.getLogger().warning("ExcellentEconomy currency operations are currently disabled.");
                return false;
            }

            return excellentEconomyApi.deposit(player, excellentEconomyCurrency, amount, excellentEconomyContext);
        }

        plugin.getLogger().warning("Unsupported currency type during deposit: " + configuredCurrencyType);
        return false;
    }

    public boolean withdraw(double amount, Player player) {
        if (!currencyAvailable) {
            plugin.getLogger().warning("Currency not available for withdraw operation.");
            return false;
        }

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Vault economy is not initialized.");
                return false;
            }
            return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        }

        if (configuredCurrencyType.equalsIgnoreCase("EXCELLENTECONOMY")) {
            if (excellentEconomyApi == null || excellentEconomyCurrency == null || excellentEconomyContext == null) {
                plugin.getLogger().warning("ExcellentEconomy is not initialized.");
                return false;
            }

            if (!excellentEconomyApi.canPerformOperations()) {
                plugin.getLogger().warning("ExcellentEconomy currency operations are currently disabled.");
                return false;
            }

            return excellentEconomyApi.withdraw(player, excellentEconomyCurrency, amount, excellentEconomyContext);
        }

        plugin.getLogger().warning("Unsupported currency type during withdraw: " + configuredCurrencyType);
        return false;
    }

    public void reload() {
        // Clean up existing connections
        cleanup();

        // Reload configuration and reinitialize
        loadConfiguration();
        setupCurrency();
    }

    public void cleanup() {
        vaultEconomy = null;
        excellentEconomyApi = null;
        excellentEconomyCurrency = null;
        excellentEconomyContext = null;
        currencyAvailable = false;
        activeCurrencyProvider = "None";
    }
}
