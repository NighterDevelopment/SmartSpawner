package github.nighter.smartspawner;

import fr.xyness.SCS.API.SimpleClaimSystemAPI_Provider;
import fr.xyness.SCS.SimpleClaimSystem;
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerPlugin;
import github.nighter.smartspawner.api.SmartSpawnerAPIImpl;
import github.nighter.smartspawner.bstats.Metrics;
import github.nighter.smartspawner.commands.CommandHandler;
import github.nighter.smartspawner.commands.give.GiveCommand;
import github.nighter.smartspawner.commands.hologram.HologramCommand;
import github.nighter.smartspawner.commands.list.ListCommand;
import github.nighter.smartspawner.commands.list.SpawnerListGUI;
import github.nighter.smartspawner.commands.list.UserPreferenceCache;
import github.nighter.smartspawner.commands.reload.ReloadCommand;
import github.nighter.smartspawner.configs.TimeFormatter;
import github.nighter.smartspawner.economy.CustomEconomyManager;
import github.nighter.smartspawner.economy.ItemPriceManager;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.hooks.protections.api.Lands;
import github.nighter.smartspawner.hooks.protections.api.SuperiorSkyblock2;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.hooks.shops.SaleLogger;
import github.nighter.smartspawner.hooks.shops.ShopIntegrationManager;
import github.nighter.smartspawner.hooks.shops.api.shopguiplus.SpawnerHook;
import github.nighter.smartspawner.hooks.shops.api.shopguiplus.SpawnerProvider;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.migration.SpawnerDataMigration;
import github.nighter.smartspawner.spawner.gui.main.ItemCache;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerHandler;
import github.nighter.smartspawner.spawner.gui.storage.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageAction;
import github.nighter.smartspawner.spawner.interactions.SpawnerClickManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerBreakListener;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerExplosionListener;
import github.nighter.smartspawner.spawner.interactions.place.SpawnerPlaceListener;
import github.nighter.smartspawner.spawner.interactions.stack.SpawnerStackHandler;
import github.nighter.smartspawner.spawner.interactions.type.SpawnEggHandler;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.lootgen.SpawnerRangeChecker;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.lootgen.SpawnerLootGenerator;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.updates.ConfigUpdater;
import github.nighter.smartspawner.nms.VersionInitializer;

import github.nighter.smartspawner.updates.LanguageUpdater;
import github.nighter.smartspawner.updates.UpdateChecker;
import github.nighter.smartspawner.utils.SpawnerTypeChecker;
import lombok.Getter;
import lombok.experimental.Accessors;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

@Getter
@Accessors(chain = false)
public class SmartSpawner extends JavaPlugin implements SmartSpawnerPlugin {
    @Getter
    private static SmartSpawner instance;
    public final int DATA_VERSION = 3;
    private final boolean debugMode = getConfig().getBoolean("debug", false);

    // Services
    private TimeFormatter timeFormatter;
    private ConfigUpdater configUpdater;
    private LanguageManager languageManager;
    private LanguageUpdater languageUpdater;
    private MessageService messageService;

    // Factories
    private SpawnerItemFactory spawnerItemFactory;

    // Core UI components
    private final ItemCache itemCache = new ItemCache(500, 30);
    private SpawnerMenuUI spawnerMenuUI;
    private SpawnerStorageUI spawnerStorageUI;
    private FilterConfigUI filterConfigUI;
    private SpawnerStackerUI spawnerStackerUI;

    // Core handlers
    private SpawnEggHandler spawnEggHandler;
    private SpawnerClickManager spawnerClickManager;
    private SpawnerStackHandler spawnerStackHandler;

    // UI actions
    private SpawnerMenuAction spawnerMenuAction;
    private SpawnerStackerHandler spawnerStackerHandler;
    private SpawnerStorageAction spawnerStorageAction;

    // Core managers
    private SpawnerFileHandler spawnerFileHandler;
    private SpawnerManager spawnerManager;
    private ShopIntegrationManager shopIntegrationManager;
    private HopperHandler hopperHandler;

    // Event handlers and utilities
    private GlobalEventHandlers globalEventHandlers;
    private SpawnerLootGenerator spawnerLootGenerator;
    private SpawnerListGUI spawnerListGUI;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerGuiViewManager spawnerGuiViewManager;
    private SpawnerExplosionListener spawnerExplosionListener;
    private SpawnerBreakListener spawnerBreakListener;
    private SpawnerPlaceListener spawnerPlaceListener;
    private ItemPriceManager itemPriceManager;
    private CustomEconomyManager customEconomyManager;
    private EntityLootRegistry entityLootRegistry;
    private UpdateChecker updateChecker;

    // Set up commands
    private CommandHandler commandHandler;
    private ReloadCommand reloadCommand;
    private GiveCommand giveCommand;
    private UserPreferenceCache userPreferenceCache;
    private ListCommand listCommand;
    private HologramCommand hologramCommand;

    // Integration flags - static for quick access
    public static boolean hasTowny = false;
    public static boolean hasLands = false;
    public static boolean hasWorldGuard = false;
    public static boolean hasGriefPrevention = false;
    public static boolean hasSuperiorSkyblock2 = false;
    public static boolean hasBentoBox = false;
    public static boolean hasSimpleClaimSystem = false;
    public static boolean hasRedProtect = false;

    // API implementation
    private SmartSpawnerAPIImpl apiImpl;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        // Save default config
        saveResourceAsDefault("spawners_data.yml");
        saveResourceAsDefault("mob_drops.yml");

        // Initialize version-specific components
        initializeVersionComponents();

        // Check for data migration needs
        migrateDataIfNeeded();

        // Initialize core components in the same order as before
        initializeComponents();

        // Setup plugin infrastructure
        checkProtectionPlugins();
        setupCommand();
        setupBtatsMetrics();
        registerListeners();
        initializeSaleLogging();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("SmartSpawner has been enabled! (Loaded in " + loadTime + "ms)");
    }

    @Override
    public SmartSpawnerAPI getAPI() {
        return apiImpl;
    }

    private void saveResourceAsDefault(String resourcePath) {
        File resourceFile = new File(getDataFolder(), resourcePath);
        if (!resourceFile.exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void initializeVersionComponents() {
        try {
            new VersionInitializer(this).initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize version-specific components", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void migrateDataIfNeeded() {
        SpawnerDataMigration migration = new SpawnerDataMigration(this);
        if (migration.checkAndMigrateData()) {
            getLogger().info("Data migration completed. Loading with new format...");
        }
    }

    private void initializeComponents() {
        // Initialize services
        initializeServices();

        // Initialize factories and economy
        initializeEconomyComponents();

        // Initialize core components in order
        initializeCoreComponents();

        // Initialize handlers
        initializeHandlers();

        // Initialize UI and actions
        initializeUIAndActions();

        // Initialize hopper handler if enabled in config
        setUpHopperHandler();

        // Initialize listeners
        initializeListeners();

        // Initialize API implementation
        this.apiImpl = new SmartSpawnerAPIImpl(this);
        this.updateChecker = new UpdateChecker(this);
    }

    private void initializeServices() {
        SpawnerTypeChecker.init(this);
        this.timeFormatter = new TimeFormatter(this);
        this.configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        this.languageManager = new LanguageManager(this);
        this.languageUpdater = new LanguageUpdater(this);
        this.messageService = new MessageService(this, languageManager);
    }

    private void initializeEconomyComponents() {
        this.shopIntegrationManager = new ShopIntegrationManager(this);
        this.itemPriceManager = new ItemPriceManager(this);
        this.itemPriceManager.init();

        // Only initialize if custom sell prices should be used
        if (getConfig().getBoolean("custom_sell_prices.enabled", false)) {
            this.customEconomyManager = new CustomEconomyManager(this, itemPriceManager);
        } else {
            this.customEconomyManager = null;
        }

        shopIntegrationManager.initialize();
        this.entityLootRegistry = new EntityLootRegistry(this, itemPriceManager);
        this.spawnerItemFactory = new SpawnerItemFactory(this);
    }

    private void initializeCoreComponents() {
        this.spawnerFileHandler = new SpawnerFileHandler(this);
        this.spawnerManager = new SpawnerManager(this);
        this.spawnerManager.reloadAllHolograms();
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.filterConfigUI = new FilterConfigUI(this);
        this.spawnerMenuUI = new SpawnerMenuUI(this);
        this.spawnerGuiViewManager = new SpawnerGuiViewManager(this);
        this.spawnerLootGenerator = new SpawnerLootGenerator(this);
        this.rangeChecker = new SpawnerRangeChecker(this);
    }

    private void initializeHandlers() {
        this.spawnerStackerUI = new SpawnerStackerUI(this);

        this.spawnEggHandler = new SpawnEggHandler(this);
        this.spawnerStackHandler = new SpawnerStackHandler(this);
        this.spawnerClickManager = new SpawnerClickManager(this);
    }

    private void initializeUIAndActions() {
        this.spawnerMenuAction = new SpawnerMenuAction(this);
        this.spawnerStackerHandler = new SpawnerStackerHandler(this);
        this.spawnerStorageAction = new SpawnerStorageAction(this);
    }

    private void initializeListeners() {
        this.globalEventHandlers = new GlobalEventHandlers(this);
        this.spawnerExplosionListener = new SpawnerExplosionListener(this);
        this.spawnerBreakListener = new SpawnerBreakListener(this);
        this.spawnerPlaceListener = new SpawnerPlaceListener(this);
    }

    public void setUpHopperHandler() {
        this.hopperHandler = getConfig().getBoolean("hopper.enabled", false) ? new HopperHandler(this) : null;
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Register core listeners
        pm.registerEvents(globalEventHandlers, this);
        pm.registerEvents(spawnerListGUI, this);
        pm.registerEvents(spawnerBreakListener, this);
        pm.registerEvents(spawnerPlaceListener, this);
        pm.registerEvents(spawnerStorageAction, this);
        pm.registerEvents(spawnerExplosionListener, this);
        pm.registerEvents(spawnerGuiViewManager, this);
        pm.registerEvents(spawnerClickManager, this);
        pm.registerEvents(spawnerMenuAction, this);
        pm.registerEvents(spawnerStackerHandler, this);

        // Register shop integration listeners if available
        if (shopIntegrationManager.isShopGUIPlusEnabled()) {
            pm.registerEvents(new SpawnerHook(this), this);
        }
    }

    private void setupCommand() {
        this.reloadCommand = new ReloadCommand(this);
        this.giveCommand = new GiveCommand(this);
        this.userPreferenceCache = new UserPreferenceCache(this);
        this.listCommand = new ListCommand(this);
        this.spawnerListGUI = new SpawnerListGUI(this);
        this.hologramCommand = new HologramCommand(this);
        this.commandHandler = new CommandHandler(this);
        Objects.requireNonNull(getCommand("smartspawner")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("smartspawner")).setTabCompleter(commandHandler);
    }

    private void setupBtatsMetrics() {
        Metrics metrics = new Metrics(this, 24822);
        metrics.addCustomChart(new Metrics.SimplePie("holograms", () ->
                String.valueOf(getConfig().getBoolean("hologram.enabled", false)))
        );
        metrics.addCustomChart(new Metrics.SimplePie("hoppers", () ->
                String.valueOf(getConfig().getBoolean("hopper.enabled", false)))
        );
        metrics.addCustomChart(new Metrics.SimplePie("spawners", () ->
                String.valueOf(this.spawnerManager.getTotalSpawners() / 1000 * 1000))
        );
    }

    private void initializeSaleLogging() {
        if (getConfig().getBoolean("log_transactions.enabled", false)) {
            SaleLogger.getInstance();
        }
    }

    private void checkProtectionPlugins() {
        hasWorldGuard = checkPlugin("WorldGuard", () -> {
            Plugin worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            return worldGuardPlugin != null && worldGuardPlugin.isEnabled();
        }, true);

        hasGriefPrevention = checkPlugin("GriefPrevention", () -> {
            Plugin griefPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            return griefPlugin instanceof GriefPrevention;
        }, true);

        hasLands = checkPlugin("Lands", () -> {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                new Lands(this);
                return true;
            }
            return false;
        }, true);

        hasTowny = checkPlugin("Towny", () -> {
            try {
                Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                return com.palmergames.bukkit.towny.TownyAPI.getInstance() != null;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }, true);

        hasSuperiorSkyblock2 = checkPlugin("SuperiorSkyblock2", () -> {
            Plugin superiorSkyblock2 = Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2");
            if(superiorSkyblock2 != null) {
                SuperiorSkyblock2 ssb2 = new SuperiorSkyblock2();
                Bukkit.getPluginManager().registerEvents(ssb2, this);
                return true;
            }
            return false;
        }, true);

        hasBentoBox = checkPlugin("BentoBox", () -> {
            Plugin bentoPlugin = Bukkit.getPluginManager().getPlugin("BentoBox");
            if (bentoPlugin != null) {
                return true;
            }
            return false;
        }, true);
        hasSimpleClaimSystem = checkPlugin("SimpleClaimSystem", () -> {
            Plugin simpleClaimPlugin = Bukkit.getPluginManager().getPlugin("SimpleClaimSystem");
            SimpleClaimSystemAPI_Provider.initialize((SimpleClaimSystem) simpleClaimPlugin);
            return simpleClaimPlugin != null;
        }, true);
        hasRedProtect = checkPlugin("RedProtect", () -> {
            Plugin pRP = Bukkit.getPluginManager().getPlugin("RedProtect");
            if (pRP != null && pRP.isEnabled()){
                return true;
            }
            return false;
        }, true);
    }

    private boolean checkPlugin(String pluginName, PluginCheck checker, boolean logSuccess) {
        try {
            if (checker.check()) {
                if (logSuccess) {
                    getLogger().info(pluginName + " integration enabled successfully!");
                }
                return true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            // Silent fail - plugin not available
        }
        return false;
    }

    public void reload() {
        shopIntegrationManager.reload();
        itemPriceManager.reload();
        clearItemCache();

        // Only initialize CustomEconomyManager if custom sell prices are enabled
        boolean shouldUseCustomSellPrices = getConfig().getBoolean("custom_sell_prices.enabled", false);

        // Handle CustomEconomyManager based on current configuration
        if (shouldUseCustomSellPrices) {
            if (customEconomyManager == null) {
                // Initialize if it wasn't active before
                customEconomyManager = new CustomEconomyManager(this, itemPriceManager);
            } else {
                // Just reload if it already exists
                customEconomyManager.reload();
            }
        } else if (customEconomyManager != null) {
            // Shut down if it's no longer needed
            customEconomyManager.shutdown();
            customEconomyManager = null;
        }
    }

    public void reloadStaticUI() {
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.filterConfigUI = new FilterConfigUI(this);
    }

    @Override
    public void onDisable() {
        saveAndCleanup();
        SpawnerMobHeadTexture.clearCache();
        shutdownSaleLogger();
        getLogger().info("SmartSpawner has been disabled!");
    }

    private void saveAndCleanup() {
        if (spawnerManager != null) {
            try {
                // First shutdown the file handler to flush any pending changes
                // but avoid starting new tasks
                if (spawnerFileHandler != null) {
                    spawnerFileHandler.shutdown();
                }

                // Clean up the spawner manager
                spawnerManager.cleanupAllSpawners();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving spawner data during shutdown", e);
            }
        }

        // Clean up resources
        cleanupResources();
    }

    private void cleanupResources() {
        if (rangeChecker != null) rangeChecker.cleanup();
        if (spawnerGuiViewManager != null) spawnerGuiViewManager.cleanup();
        if (hopperHandler != null) hopperHandler.cleanup();
        if (spawnerClickManager != null) spawnerClickManager.cleanup();
        if (spawnerStackerHandler != null) spawnerStackerHandler.cleanupAll();
        if (spawnerStorageUI != null) spawnerStorageUI.cleanup();
        if (customEconomyManager != null) customEconomyManager.shutdown();
    }

    private void shutdownSaleLogger() {
        if (getConfig().getBoolean("log_transactions.enabled", true)) {
            SaleLogger.getInstance().shutdown();
        }
    }

    @FunctionalInterface
    private interface PluginCheck {
        boolean check();
    }

    public boolean hasShopIntegration() {
        return shopIntegrationManager.hasShopIntegration();
    }

    public IShopIntegration getShopIntegration() {
        return shopIntegrationManager.getShopIntegration();
    }

    // Spawner Provider for ShopGUI+ integration
    public SpawnerProvider getSpawnerProvider() {
        return new SpawnerProvider(this);
    }

    public long getTimeFromConfig(String path, String defaultValue) {
        return timeFormatter.getTimeFromConfig(path, defaultValue);
    }

    public void refreshTimeCache() {
        if (timeFormatter != null) {
            timeFormatter.clearCache();
        }
    }

    public void clearItemCache() {
        itemCache.clear();
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}