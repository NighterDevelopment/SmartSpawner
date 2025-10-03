package github.nighter.smartspawner;

import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerPlugin;
import github.nighter.smartspawner.api.SmartSpawnerAPIImpl;
import github.nighter.smartspawner.bstats.Metrics;
import github.nighter.smartspawner.commands.BrigadierCommandManager;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.list.gui.list.UserPreferenceCache;
import github.nighter.smartspawner.commands.list.gui.list.SpawnerListGUI;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementHandler;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.commands.list.gui.adminstacker.AdminStackerHandler;
import github.nighter.smartspawner.commands.prices.PricesGUI;
import github.nighter.smartspawner.spawner.natural.NaturalSpawnerListener;
import github.nighter.smartspawner.utils.TimeFormatter;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.SpawnerProvider;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.hooks.IntegrationManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.migration.SpawnerDataMigration;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuFormUI;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerHandler;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerFormUI;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageAction;
import github.nighter.smartspawner.spawner.interactions.click.SpawnerClickManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerBreakListener;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerExplosionListener;
import github.nighter.smartspawner.spawner.interactions.place.SpawnerPlaceListener;
import github.nighter.smartspawner.spawner.interactions.stack.SpawnerStackHandler;
import github.nighter.smartspawner.spawner.interactions.type.SpawnEggHandler;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.limits.ChunkSpawnerLimiter;
import github.nighter.smartspawner.spawner.loot.EntityLootRegistry;
import github.nighter.smartspawner.spawner.lootgen.SpawnerRangeChecker;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.lootgen.SpawnerLootGenerator;
import github.nighter.smartspawner.spawner.events.WorldEventHandler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.updates.ConfigUpdater;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.updates.LanguageUpdater;
import github.nighter.smartspawner.updates.UpdateChecker;
import github.nighter.smartspawner.utils.SpawnerTypeChecker;

import lombok.Getter;
import lombok.experimental.Accessors;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

@Getter
@Accessors(chain = false)
public class SmartSpawner extends JavaPlugin implements SmartSpawnerPlugin {
    @Getter
    private static SmartSpawner instance;
    public final int DATA_VERSION = 3;
    private final boolean debugMode = getConfig().getBoolean("debug", false);

    // Integration Manager
    private IntegrationManager integrationManager;

    // Services
    private TimeFormatter timeFormatter;
    private ConfigUpdater configUpdater;
    private LanguageManager languageManager;
    private LanguageUpdater languageUpdater;
    private MessageService messageService;

    // Factories
    private SpawnerItemFactory spawnerItemFactory;

    // Core UI components
    private GuiLayoutConfig guiLayoutConfig;
    private SpawnerMenuUI spawnerMenuUI;
    private SpawnerMenuFormUI spawnerMenuFormUI;
    private SpawnerStorageUI spawnerStorageUI;
    private FilterConfigUI filterConfigUI;
    private SpawnerStackerUI spawnerStackerUI;
    private SpawnerStackerFormUI spawnerStackerFormUI;

    // Core handlers
    private SpawnEggHandler spawnEggHandler;
    private SpawnerClickManager spawnerClickManager;
    private SpawnerStackHandler spawnerStackHandler;

    // UI actions
    private SpawnerMenuAction spawnerMenuAction;
    private SpawnerStackerHandler spawnerStackerHandler;
    private SpawnerStorageAction spawnerStorageAction;
    private SpawnerSellManager spawnerSellManager;

    // Core managers
    private SpawnerFileHandler spawnerFileHandler;
    private SpawnerManager spawnerManager;
    private HopperHandler hopperHandler;

    // Event handlers and utilities
    private NaturalSpawnerListener naturalSpawnerListener;
    private SpawnerLootGenerator spawnerLootGenerator;
    private SpawnerRangeChecker rangeChecker;
    private ChunkSpawnerLimiter chunkSpawnerLimiter;
    private SpawnerGuiViewManager spawnerGuiViewManager;
    private SpawnerExplosionListener spawnerExplosionListener;
    private SpawnerBreakListener spawnerBreakListener;
    private SpawnerPlaceListener spawnerPlaceListener;
    private WorldEventHandler worldEventHandler;
    private ItemPriceManager itemPriceManager;
    private EntityLootRegistry entityLootRegistry;
    private UpdateChecker updateChecker;
    private BrigadierCommandManager brigadierCommandManager;
    private ListSubCommand listSubCommand;
    private UserPreferenceCache userPreferenceCache;
    private SpawnerListGUI spawnerListGUI;
    private SpawnerManagementHandler spawnerManagementHandler;
    private AdminStackerHandler adminStackerHandler;
    private PricesGUI pricesGUI;

    // API implementation
    private SmartSpawnerAPIImpl apiImpl;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        // Initialize version-specific components
        initializeVersionComponents();

        // Initialize plugin integrations
        this.integrationManager = new IntegrationManager(this);
        integrationManager.initializeIntegrations();

        // Check for data migration needs
        migrateDataIfNeeded();

        // Initialize core components
        initializeComponents();

        // Setup plugin infrastructure
        setupCommand();
        setupBtatsMetrics();
        registerListeners();

        // Trigger world event handler to attempt initial spawner loading
        // This is done after all components are initialized
        if (worldEventHandler != null) {
            worldEventHandler.attemptInitialSpawnerLoad();
        }

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("SmartSpawner has been enabled! (Loaded in " + loadTime + "ms)");
    }

    @Override
    public SmartSpawnerAPI getAPI() {
        return apiImpl;
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
        this.itemPriceManager = new ItemPriceManager(this);
        this.itemPriceManager.init();
        this.entityLootRegistry = new EntityLootRegistry(this, itemPriceManager);
        this.spawnerItemFactory = new SpawnerItemFactory(this);
    }

    private void initializeCoreComponents() {
        this.spawnerFileHandler = new SpawnerFileHandler(this);
        this.spawnerManager = new SpawnerManager(this);
        this.spawnerManager.reloadAllHolograms();
        this.guiLayoutConfig = new GuiLayoutConfig(this);
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.filterConfigUI = new FilterConfigUI(this);
        this.spawnerMenuUI = new SpawnerMenuUI(this);
        this.spawnerGuiViewManager = new SpawnerGuiViewManager(this);
        this.spawnerLootGenerator = new SpawnerLootGenerator(this);
        this.spawnerSellManager = new SpawnerSellManager(this);
        this.rangeChecker = new SpawnerRangeChecker(this);
        
        // Initialize FormUI components only if Floodgate is available
        initializeFormUIComponents();
    }

    private void initializeFormUIComponents() {
        if (integrationManager != null && integrationManager.getFloodgateHook() != null 
            && integrationManager.getFloodgateHook().isEnabled()) {
            try {
                this.spawnerMenuFormUI = new SpawnerMenuFormUI(this);
                this.spawnerStackerFormUI = new SpawnerStackerFormUI(this, spawnerStackerHandler);
                getLogger().info("FormUI components initialized successfully for Bedrock player support");
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize FormUI components: " + e.getMessage());
                this.spawnerMenuFormUI = null;
                this.spawnerStackerFormUI = null;
            }
        } else {
            this.spawnerMenuFormUI = null;
            this.spawnerStackerFormUI = null;
            debug("FormUI components not initialized - Floodgate integration not available");
        }
    }

    private void initializeHandlers() {
        this.chunkSpawnerLimiter = new ChunkSpawnerLimiter(this);
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
        this.naturalSpawnerListener = new NaturalSpawnerListener(this);
        this.spawnerExplosionListener = new SpawnerExplosionListener(this);
        this.spawnerBreakListener = new SpawnerBreakListener(this);
        this.spawnerPlaceListener = new SpawnerPlaceListener(this);
        this.worldEventHandler = new WorldEventHandler(this);
    }

    public void setUpHopperHandler() {
        this.hopperHandler = getConfig().getBoolean("hopper.enabled", false) ? new HopperHandler(this) : null;
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Register core listeners
        pm.registerEvents(naturalSpawnerListener, this);
        pm.registerEvents(spawnerBreakListener, this);
        pm.registerEvents(spawnerPlaceListener, this);
        pm.registerEvents(spawnerStorageAction, this);
        pm.registerEvents(spawnerExplosionListener, this);
        pm.registerEvents(spawnerGuiViewManager, this);
        pm.registerEvents(spawnerClickManager, this);
        pm.registerEvents(spawnerMenuAction, this);
        pm.registerEvents(spawnerStackerHandler, this);
        pm.registerEvents(worldEventHandler, this);
        pm.registerEvents(spawnerListGUI, this);
        pm.registerEvents(spawnerManagementHandler, this);
        pm.registerEvents(adminStackerHandler, this);
        pm.registerEvents(pricesGUI, this);
    }

    private void setupCommand() {
        this.brigadierCommandManager = new BrigadierCommandManager(this);
        brigadierCommandManager.registerCommands();
        this.userPreferenceCache = new UserPreferenceCache(this);
        this.listSubCommand = new ListSubCommand(this);
        this.spawnerListGUI = new SpawnerListGUI(this);
        this.spawnerManagementHandler = new SpawnerManagementHandler(this, listSubCommand);
        this.adminStackerHandler = new AdminStackerHandler(this, new SpawnerManagementGUI(this));
        this.pricesGUI = new PricesGUI(this);
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

    public void reload() {
        // reload gui components
        guiLayoutConfig.reloadLayouts();
        
        // Clear spawner info slot cache since layout may have changed
        spawnerGuiViewManager.clearSlotCache();
        
        // Clear GUI item cache since layout/config may have changed
        if (spawnerMenuUI != null) {
            spawnerMenuUI.clearCache();
        }
        
        spawnerStorageAction.reload();
        spawnerStorageUI.reload();
        filterConfigUI.reload();

        // reload services
        integrationManager.reload();
        spawnerMenuAction.reload();
        timeFormatter.clearCache();
    }

    @Override
    public void onDisable() {
        saveAndCleanup();
        SpawnerMobHeadTexture.clearCache();
        getLogger().info("SmartSpawner has been disabled!");
    }

    private void saveAndCleanup() {
        if (spawnerManager != null) {
            try {
                if (spawnerFileHandler != null) {
                    spawnerFileHandler.shutdown();
                }

                // Clean up the spawner manager
                spawnerManager.cleanupAllSpawners();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving spawner data during shutdown", e);
            }
        }

        if (itemPriceManager != null) {
            itemPriceManager.cleanup();
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
    }

    // Spawner Provider for ShopGUI+ integration
    public SpawnerProvider getSpawnerProvider() {
        return new SpawnerProvider(this);
    }

    public boolean hasSellIntegration() {
        if (itemPriceManager == null) {
            return false;
        }
        return itemPriceManager.hasSellIntegration();
    }

    public boolean hasShopIntegration() {
        if (itemPriceManager == null) {
            return false;
        }

        return itemPriceManager.getShopIntegrationManager() != null &&
                itemPriceManager.getShopIntegrationManager().hasActiveProvider();
    }

    public long getTimeFromConfig(String path, String defaultValue) {
        return timeFormatter.getTimeFromConfig(path, defaultValue);
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}
