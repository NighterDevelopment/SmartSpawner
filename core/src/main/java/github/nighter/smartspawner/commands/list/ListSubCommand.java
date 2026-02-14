package github.nighter.smartspawner.commands.list;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import github.nighter.smartspawner.commands.list.gui.list.SpawnerListHolder;
import github.nighter.smartspawner.commands.list.gui.list.UserPreferenceCache;
import github.nighter.smartspawner.commands.list.gui.worldselection.WorldSelectionHolder;
import github.nighter.smartspawner.commands.list.gui.serverselection.ServerSelectionHolder;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class ListSubCommand extends BaseSubCommand {
    private final SpawnerManager spawnerManager;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final UserPreferenceCache userPreferenceCache;
    private final SpawnerManagementGUI spawnerManagementGUI;
    private static final int SPAWNERS_PER_PAGE = 45;

    public ListSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.userPreferenceCache = plugin.getUserPreferenceCache();
        this.spawnerManagementGUI = new SpawnerManagementGUI(plugin);
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.list";
    }

    @Override
    public String getDescription() {
        return "Open the spawner list GUI";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        if (!isPlayer(context.getSource().getSender())) {
            return 0;
        }

        Player player = getPlayer(context.getSource().getSender());

        // Check if cross-server mode is enabled
        if (isCrossServerEnabled()) {
            openServerSelectionGUI(player);
        } else {
            openWorldSelectionGUI(player);
        }
        return 1;
    }

    /**
     * Check if cross-server sync is enabled.
     * Requires MYSQL mode AND sync_across_servers = true
     * (SQLite is local-only and does not support cross-server sync)
     */
    public boolean isCrossServerEnabled() {
        String modeStr = plugin.getConfig().getString("database.mode", "YAML").toUpperCase();
        try {
            StorageMode mode = StorageMode.valueOf(modeStr);
            if (mode != StorageMode.MYSQL) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return plugin.getConfig().getBoolean("database.sync_across_servers", false);
    }

    /**
     * Get the current server name from config.
     */
    public String getCurrentServerName() {
        return plugin.getConfig().getString("database.server_name", "server1");
    }

    /**
     * Open the server selection GUI (async database query).
     */
    public void openServerSelectionGUI(Player player) {
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            // Fallback to local world selection
            openWorldSelectionGUI(player);
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Async query for server names
        dbHandler.getDistinctServerNamesAsync(servers -> {
            if (servers.isEmpty()) {
                messageService.sendMessage(player, "no_spawners_found");
                return;
            }

            // Calculate inventory size
            int size = Math.max(9, (int) Math.ceil(servers.size() / 7.0) * 9);
            size = Math.min(54, size); // Max 54 slots

            String title = languageManager.getGuiTitle("gui_title_server_selection");
            if (title == null || title.isEmpty()) {
                title = ChatColor.DARK_GRAY + "Select Server";
            }

            Inventory inv = Bukkit.createInventory(new ServerSelectionHolder(), size, title);

            String currentServer = getCurrentServerName();
            int slot = 0;

            for (String serverName : servers) {
                if (slot >= size) break;

                // Skip border slots for nicer layout
                while (slot < size && (slot % 9 == 0 || slot % 9 == 8)) {
                    slot++;
                }
                if (slot >= size) break;

                Material material = serverName.equals(currentServer) ? Material.EMERALD_BLOCK : Material.IRON_BLOCK;
                ItemStack serverItem = createServerButton(serverName, material, serverName.equals(currentServer));
                inv.setItem(slot, serverItem);
                slot++;
            }

            player.openInventory(inv);
        });
    }

    private ItemStack createServerButton(String serverName, Material material, boolean isCurrentServer) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = (isCurrentServer ? ChatColor.GREEN : ChatColor.GOLD) + serverName;
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>();
            if (isCurrentServer) {
                lore.add(ChatColor.GRAY + "Current Server");
            }
            lore.add(ChatColor.YELLOW + "Click to view spawners");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Open world selection for a specific server (async for remote servers).
     */
    public void openWorldSelectionGUIForServer(Player player, String targetServer) {
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        String currentServer = getCurrentServerName();

        // If it's the current server, use local data
        if (targetServer.equals(currentServer)) {
            openWorldSelectionGUI(player);
            return;
        }

        // For remote servers, query async
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "action_failed");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        dbHandler.getWorldsForServerAsync(targetServer, worldCounts -> {
            if (worldCounts.isEmpty()) {
                messageService.sendMessage(player, "no_spawners_found");
                return;
            }

            int size = Math.max(27, (int) Math.ceil((worldCounts.size() + 2) / 7.0) * 9);
            size = Math.min(54, size);

            Map<String, String> titlePlaceholders = new HashMap<>();
            titlePlaceholders.put("server", targetServer);
            String title = languageManager.getGuiTitle("gui_title_world_selection_server", titlePlaceholders);
            if (title == null || title.isEmpty()) {
                title = ChatColor.DARK_GRAY + "Worlds - " + targetServer;
            }

            Inventory inv = Bukkit.createInventory(
                    new WorldSelectionHolder(targetServer),
                    size, title
            );

            int slot = 10;
            for (Map.Entry<String, Integer> entry : worldCounts.entrySet()) {
                if (slot >= size - 9) break;

                // Skip border slots
                if (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                    continue;
                }

                String worldName = entry.getKey();
                int count = entry.getValue();

                Material material = getMaterialForWorldName(worldName);
                ItemStack worldItem = createRemoteWorldButton(worldName, material, count, targetServer);
                inv.setItem(slot, worldItem);
                slot++;
            }

            // Back button
            ItemStack backButton = createNavigationButton(Material.RED_STAINED_GLASS_PANE, "navigation.back");
            inv.setItem(size - 5, backButton);

            player.openInventory(inv);
        });
    }

    private ItemStack createRemoteWorldButton(String worldName, Material material, int spawnerCount, String serverName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + formatWorldName(worldName));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Server: " + ChatColor.WHITE + serverName);
            lore.add(ChatColor.GRAY + "Spawners: " + ChatColor.WHITE + spawnerCount);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to view spawners");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getMaterialForWorldName(String worldName) {
        if (worldName.contains("nether")) {
            return Material.NETHERRACK;
        } else if (worldName.contains("end")) {
            return Material.END_STONE;
        }
        return Material.GRASS_BLOCK;
    }

    private SpawnerDatabaseHandler getDbHandler() {
        if (plugin.getSpawnerStorage() instanceof SpawnerDatabaseHandler dbHandler) {
            return dbHandler;
        }
        return null;
    }

    // World selection GUI logic (unchanged)
    public void openWorldSelectionGUI(Player player) {
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Get all loaded worlds with spawners
        List<World> worlds = Bukkit.getWorlds().stream()
                .filter(world -> spawnerManager.countSpawnersInWorld(world.getName()) > 0)
                .collect(Collectors.toList());

        // Check if there are any custom worlds with spawners
        List<World> customWorlds = worlds.stream()
                .filter(world -> !isDefaultWorld(world.getName()))
                .collect(Collectors.toList());

        boolean hasCustomWorlds = !customWorlds.isEmpty();

        // Calculate inventory size - use original 27 size if only default worlds, otherwise adapt
        int size = hasCustomWorlds ? Math.max(27, (int) Math.ceil((worlds.size() + 2) / 7.0) * 9) : 27;

        Inventory inv = Bukkit.createInventory(new WorldSelectionHolder(),
                size, languageManager.getGuiTitle("gui_title_world_selection"));

        // If we only have default worlds, use the original layout
        if (!hasCustomWorlds) {
            // Create buttons for default worlds
            ItemStack overworldButton = createWorldButtonIfWorldExists("world", Material.GRASS_BLOCK,
                    languageManager.getGuiTitle("world_buttons.overworld.name"));

            ItemStack netherButton = createWorldButtonIfWorldExists("world_nether", Material.NETHERRACK,
                    languageManager.getGuiTitle("world_buttons.nether.name"));

            ItemStack endButton = createWorldButtonIfWorldExists("world_the_end", Material.END_STONE,
                    languageManager.getGuiTitle("world_buttons.end.name"));

            // Set buttons in the original layout
            if (overworldButton != null) inv.setItem(11, overworldButton);
            if (netherButton != null) inv.setItem(13, netherButton);
            if (endButton != null) inv.setItem(15, endButton);
        }
        // If we have custom worlds, use a more flexible layout
        else {
            int slot = 10; // Start at second row, second column
            int row = 1;

            // Add default worlds first (if they exist)
            if (addWorldButtonIfExists(inv, "world", Material.GRASS_BLOCK,
                    languageManager.getGuiTitle("world_buttons.overworld.name"), slot)) {
                slot++;
            }

            if (addWorldButtonIfExists(inv, "world_nether", Material.NETHERRACK,
                    languageManager.getGuiTitle("world_buttons.nether.name"), slot)) {
                slot++;
            }

            if (addWorldButtonIfExists(inv, "world_the_end", Material.END_STONE,
                    languageManager.getGuiTitle("world_buttons.end.name"), slot)) {
                slot++;
            }

            // Add custom worlds
            for (World world : customWorlds) {
                // Move to next row if we've reached the end of this one
                if (slot % 9 == 8) {
                    row++;
                    slot = 9 * row + 1; // First slot in the next row (skipping the border)
                }

                // Stop if we've filled the inventory
                if (slot >= size) {
                    break;
                }

                // Add the world button
                Material material = getMaterialForWorldType(world.getEnvironment());
                addWorldButton(inv, world.getName(), material, formatWorldName(world.getName()), slot++);
            }
        }

        // Add back button if cross-server mode is enabled
        if (isCrossServerEnabled()) {
            ItemStack backButton = createNavigationButton(Material.RED_STAINED_GLASS_PANE, "navigation.back");
            inv.setItem(size - 5, backButton); // Bottom center
        }

        player.openInventory(inv);
    }

    private boolean isDefaultWorld(String worldName) {
        return worldName.equals("world") || worldName.equals("world_nether") || worldName.equals("world_the_end");
    }

    private ItemStack createWorldButtonIfWorldExists(String worldName, Material material, String displayName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null && spawnerManager.countSpawnersInWorld(worldName) > 0) {
            return createWorldButton(material, displayName, getWorldDescription(worldName));
        }
        return null;
    }

    private boolean addWorldButtonIfExists(Inventory inv, String worldName, Material material, String displayName, int slot) {
        World world = Bukkit.getWorld(worldName);
        if (world != null && spawnerManager.countSpawnersInWorld(worldName) > 0) {
            addWorldButton(inv, worldName, material, displayName, slot);
            return true;
        }
        return false;
    }

    private void addWorldButton(Inventory inv, String worldName, Material material, String displayName, int slot) {
        // For custom worlds, get formatted name with color based on environment
        if (!isDefaultWorld(worldName)) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                World.Environment environment = world.getEnvironment();
                String namePath;
                switch (environment) {
                    case NORMAL -> namePath = "world_buttons.custom_overworld.name";
                    case NETHER -> namePath = "world_buttons.custom_nether.name";
                    case THE_END -> namePath = "world_buttons.custom_end.name";
                    default -> namePath = "world_buttons.custom_default.name";
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("world_name", displayName);
                displayName = languageManager.getGuiTitle(namePath, placeholders);
            }
        }

        ItemStack button = createWorldButton(material, displayName, getWorldDescription(worldName));
        inv.setItem(slot, button);
    }

    private Material getMaterialForWorldType(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> Material.GRASS_BLOCK;
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.ENDER_PEARL; // For custom environments
        };
    }

    private String formatWorldName(String worldName) {
        // Convert something like "my_custom_world" to "My Custom World"
        return Arrays.stream(worldName.replace('_', ' ').split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private List<String> getWorldDescription(String worldName) {
        List<String> description = new ArrayList<>();
        int physicalSpawners = spawnerManager.countSpawnersInWorld(worldName);
        int totalWithStacks = spawnerManager.countTotalSpawnersWithStacks(worldName);

        // Use path for default worlds if available, otherwise use custom world description based on environment
        String path;
        if (worldName.equals("world")) {
            path = "world_buttons.overworld.lore";
        } else if (worldName.equals("world_nether")) {
            path = "world_buttons.nether.lore";
        } else if (worldName.equals("world_the_end")) {
            path = "world_buttons.end.lore";
        } else {
            // Get environment for custom world
            World world = Bukkit.getWorld(worldName);
            World.Environment environment = world != null ? world.getEnvironment() : World.Environment.NORMAL;

            // Select appropriate lore based on environment
            switch (environment) {
                case NORMAL -> path = "world_buttons.overworld.lore";
                case NETHER -> path = "world_buttons.nether.lore";
                case THE_END -> path = "world_buttons.end.lore";
                default -> path = "world_buttons.custom_default.lore";
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("total", String.valueOf(physicalSpawners));
        placeholders.put("total_stacked", languageManager.formatNumber(totalWithStacks));

        // Get the lore as string array and convert to List
        String[] loreArray = languageManager.getGuiItemLore(path, placeholders);
        return Arrays.asList(loreArray);
    }

    private ItemStack createWorldButton(Material material, String name, List<String> lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        button.setItemMeta(meta);
        return button;
    }

    // Basic spawner list GUI opener with default filter and sort
    public void openSpawnerListGUI(Player player, String worldName, int page) {
        // Check for saved preferences
        UserPreferenceCache.UserPreference preference = userPreferenceCache.getPreference(player.getUniqueId(), worldName);

        if (preference != null) {
            // Use saved preferences if available
            openSpawnerListGUI(player, worldName, page, preference.getFilterOption(), preference.getSortOption());
        } else {
            // Use default preferences
            openSpawnerListGUI(player, worldName, page, FilterOption.ALL, SortOption.DEFAULT);
        }
    }

    public void saveUserPreference(Player player, String worldName, FilterOption filter, SortOption sort) {
        userPreferenceCache.savePreference(player.getUniqueId(), worldName, filter, sort);
    }

    // Main spawner list GUI method with filter and sort options
    public void openSpawnerListGUI(Player player, String worldName, int page, FilterOption filter, SortOption sortType) {
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Get all spawners in the world
        List<SpawnerData> worldSpawners = spawnerManager.getAllSpawners().stream()
                .filter(spawner -> spawner.getSpawnerLocation().getWorld().getName().equals(worldName))
                .collect(Collectors.toList());

        // Apply filtering
        if (filter == FilterOption.ACTIVE) {
            worldSpawners = worldSpawners.stream()
                    .filter(spawner -> !spawner.getSpawnerStop().get())
                    .collect(Collectors.toList());
        } else if (filter == FilterOption.INACTIVE) {
            worldSpawners = worldSpawners.stream()
                    .filter(spawner -> spawner.getSpawnerStop().get())
                    .collect(Collectors.toList());
        }

        // Apply sorting
        switch (sortType) {
            case STACK_SIZE_ASC -> worldSpawners.sort(Comparator.comparingInt(SpawnerData::getStackSize));
            case STACK_SIZE_DESC -> worldSpawners.sort(Comparator.comparingInt(SpawnerData::getStackSize).reversed());
            default -> {} // Default sorting (by ID) - no additional sorting needed
        }

        int totalPages = (int) Math.ceil((double) worldSpawners.size() / SPAWNERS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        String worldTitle;
        switch (worldName) {
            case "world" -> worldTitle = languageManager.getGuiTitle("world_buttons.overworld.name");
            case "world_nether" -> worldTitle = languageManager.getGuiTitle("world_buttons.nether.name");
            case "world_the_end" -> worldTitle = languageManager.getGuiTitle("world_buttons.end.name");
            default -> {
                // For custom worlds, format the name nicely
                worldTitle = formatWorldName(worldName);
            }
        }

        Map<String, String> titlePlaceholders = new HashMap<>();
        worldTitle = ChatColor.stripColor(worldTitle);
        titlePlaceholders.put("world", worldTitle);
        titlePlaceholders.put("current", String.valueOf(page));
        titlePlaceholders.put("total", String.valueOf(totalPages));

        String title = languageManager.getGuiTitle("gui_title_spawner_list", titlePlaceholders);

        Inventory inv = Bukkit.createInventory(new SpawnerListHolder(page, totalPages, worldName, filter, sortType),
                54, title);

        // Calculate start and end indices for current page
        int startIndex = (page - 1) * SPAWNERS_PER_PAGE;
        int endIndex = Math.min(startIndex + SPAWNERS_PER_PAGE, worldSpawners.size());

        // Populate inventory with spawners
        for (int i = startIndex; i < endIndex; i++) {
            SpawnerData spawner = worldSpawners.get(i);
            inv.addItem(createSpawnerInfoItem(spawner));
        }

        // Add filter and sort controls
        addControlButtons(inv, filter, sortType);

        // Add navigation buttons
        if (page > 1) {
            inv.setItem(45, createNavigationButton(Material.SPECTRAL_ARROW, "navigation.previous_page"));
        }

        // Back button
        inv.setItem(49, createNavigationButton(Material.RED_STAINED_GLASS_PANE, "navigation.back"));

        if (page < totalPages) {
            inv.setItem(53, createNavigationButton(Material.SPECTRAL_ARROW, "navigation.next_page"));
        }

        player.openInventory(inv);
    }

    // Create the new consolidated filter and sort buttons
    private void addControlButtons(Inventory inv, FilterOption currentFilter, SortOption currentSort) {
        // Filter button (updated material and position) - moved to slot 46
        ItemStack filterButton = createEnhancedControlButton(
                Material.CAULDRON,
                "filter",
                currentFilter
        );

        // Sort button (updated material and position) - moved to slot 52
        ItemStack sortButton = createEnhancedControlButton(
                Material.HOPPER,
                "sort",
                currentSort
        );

        // Updated positions for better symmetry
        inv.setItem(48, filterButton);
        inv.setItem(50, sortButton);
    }

    private ItemStack createEnhancedControlButton(Material material, String controlType, Enum<?> currentOption) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta == null) return button;

        Map<String, String> placeholders = new HashMap<>();

        // Get format strings from configuration
        String selectedFormat = languageManager.getGuiItemName(controlType + ".selected_option");
        String unselectedFormat = languageManager.getGuiItemName(controlType + ".unselected_option");

        // Build available options list
        StringBuilder availableOptions = new StringBuilder();
        boolean first = true;

        if (controlType.equals("filter")) {
            FilterOption currentFilter = (FilterOption) currentOption;

            for (FilterOption option : FilterOption.values()) {
                if (!first) availableOptions.append("\n");
                String optionName = languageManager.getGuiItemName("filter." + option.getName());
                String format = option == currentFilter ? selectedFormat : unselectedFormat;
                String formattedOption = format.replace("{option_name}", optionName);
                availableOptions.append(formattedOption);
                first = false;
            }

            meta.setDisplayName(languageManager.getGuiItemName("filter.button.name"));

        } else if (controlType.equals("sort")) {
            SortOption currentSort = (SortOption) currentOption;

            for (SortOption option : SortOption.values()) {
                if (!first) availableOptions.append("\n");
                String optionName = languageManager.getGuiItemName("sort." + option.getName());
                String format = option == currentSort ? selectedFormat : unselectedFormat;
                String formattedOption = format.replace("{option_name}", optionName);
                availableOptions.append(formattedOption);
                first = false;
            }

            meta.setDisplayName(languageManager.getGuiItemName("sort.button.name"));
        }

        placeholders.put("available_options", availableOptions.toString());

        // Set the lore using the appropriate button lore path and the placeholders
        String lorePath = controlType + ".button.lore";
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders(lorePath, placeholders);
        meta.setLore(lore);

        button.setItemMeta(meta);
        return button;
    }

    private ItemStack createNavigationButton(Material material, String namePath) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(languageManager.getGuiItemName(namePath));
        button.setItemMeta(meta);
        return button;
    }


    private ItemStack createSpawnerInfoItem(SpawnerData spawner) {
        EntityType entityType = spawner.getEntityType();
        Location loc = spawner.getSpawnerLocation();

        // Prepare all placeholders upfront
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", String.valueOf(spawner.getSpawnerId()));
        placeholders.put("entity", languageManager.getFormattedMobName(entityType));
        placeholders.put("size", String.valueOf(spawner.getStackSize()));
        if (spawner.getSpawnerStop().get()) {
            placeholders.put("status_color", "&#ff6b6b");
            placeholders.put("status_text", "Inactive");
        } else {
            placeholders.put("status_color", "&#00E689");
            placeholders.put("status_text", "Active");
        }
        placeholders.put("x", String.valueOf(loc.getBlockX()));
        placeholders.put("y", String.valueOf(loc.getBlockY()));
        placeholders.put("z", String.valueOf(loc.getBlockZ()));
        String lastPlayer = spawner.getLastInteractedPlayer();
        placeholders.put("last_player", lastPlayer != null ? lastPlayer : "None");

        ItemStack spawnerItem;

        if (entityType == null) {
            spawnerItem = new ItemStack(Material.SPAWNER);
            spawnerItem.editMeta(meta -> {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                meta.setDisplayName(languageManager.getGuiItemName("spawner_item_list.name", placeholders));
                List<String> lore = Arrays.asList(languageManager.getGuiItemLore("spawner_item_list.lore", placeholders));
                meta.setLore(lore);
            });
        } else {
            // Use optimized method with consumer to avoid extra getItemMeta/setItemMeta
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, meta -> {
                meta.setDisplayName(languageManager.getGuiItemName("spawner_item_list.name", placeholders));
                List<String> lore = Arrays.asList(languageManager.getGuiItemLore("spawner_item_list.lore", placeholders));
                meta.setLore(lore);
            });
        }

        VersionInitializer.hideTooltip(spawnerItem);
        return spawnerItem;
    }

    public void openSpawnerManagementGUI(Player player, String spawnerId, String worldName, int listPage) {
        spawnerManagementGUI.openManagementMenu(player, spawnerId, worldName, listPage);
    }

    public void openSpawnerManagementGUI(Player player, String spawnerId, String worldName, int listPage, String targetServer) {
        spawnerManagementGUI.openManagementMenu(player, spawnerId, worldName, listPage, targetServer);
    }

    /**
     * Open spawner list GUI for a remote server (async database query).
     */
    public void openSpawnerListGUIForServer(Player player, String targetServer, String worldName, int page) {
        // Use default filter and sort
        openSpawnerListGUIForServer(player, targetServer, worldName, page, FilterOption.ALL, SortOption.DEFAULT);
    }

    /**
     * Open spawner list GUI for a remote server with filter and sort options.
     */
    public void openSpawnerListGUIForServer(Player player, String targetServer, String worldName, int page,
                                            FilterOption filter, SortOption sort) {
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        String currentServer = getCurrentServerName();

        // If it's the current server, use local data
        if (targetServer.equals(currentServer)) {
            openSpawnerListGUI(player, worldName, page, filter, sort);
            return;
        }

        // For remote servers, query async
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "action_failed");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        final int requestedPage = page;
        final FilterOption finalFilter = filter;
        final SortOption finalSort = sort;
        dbHandler.getCrossServerSpawnersAsync(targetServer, worldName, filter.name(), sort.name(), spawners -> {
            if (spawners.isEmpty()) {
                messageService.sendMessage(player, "no_spawners_found");
                return;
            }

            int totalPages = (int) Math.ceil((double) spawners.size() / SPAWNERS_PER_PAGE);
            int currentPage = Math.max(1, Math.min(requestedPage, totalPages));

            String worldTitle = formatWorldName(worldName);

            Map<String, String> titlePlaceholders = new HashMap<>();
            titlePlaceholders.put("world", worldTitle);
            titlePlaceholders.put("current", String.valueOf(currentPage));
            titlePlaceholders.put("total", String.valueOf(totalPages));

            String title = languageManager.getGuiTitle("gui_title_spawner_list", titlePlaceholders);

            Inventory inv = Bukkit.createInventory(
                new SpawnerListHolder(currentPage, totalPages, worldName, finalFilter, finalSort, targetServer),
                54, title
            );

            // Calculate start and end indices for current page
            int startIndex = (currentPage - 1) * SPAWNERS_PER_PAGE;
            int endIndex = Math.min(startIndex + SPAWNERS_PER_PAGE, spawners.size());

            // Populate inventory with spawners
            for (int i = startIndex; i < endIndex; i++) {
                CrossServerSpawnerData spawner = spawners.get(i);
                inv.addItem(createCrossServerSpawnerItem(spawner, targetServer));
            }

            // Add navigation buttons
            // Previous page
            if (currentPage > 1) {
                inv.setItem(45, createNavigationButton(Material.SPECTRAL_ARROW, "navigation.previous_page"));
            }

            // Filter button (slot 48)
            addControlButtons(inv, finalFilter, finalSort);

            // Back button
            inv.setItem(49, createNavigationButton(Material.RED_STAINED_GLASS_PANE, "navigation.back"));

            // Next page
            if (currentPage < totalPages) {
                inv.setItem(53, createNavigationButton(Material.SPECTRAL_ARROW, "navigation.next_page"));
            }

            player.openInventory(inv);
        });
    }

    private ItemStack createCrossServerSpawnerItem(CrossServerSpawnerData spawner, String serverName) {
        EntityType entityType = spawner.getEntityType();

        // Prepare all placeholders
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", spawner.getSpawnerId());
        placeholders.put("entity", languageManager.getFormattedMobName(entityType));
        placeholders.put("size", String.valueOf(spawner.getStackSize()));
        if (!spawner.isActive()) {
            placeholders.put("status_color", "&#ff6b6b");
            placeholders.put("status_text", "Inactive");
        } else {
            placeholders.put("status_color", "&#00E689");
            placeholders.put("status_text", "Active");
        }
        placeholders.put("x", String.valueOf(spawner.getLocX()));
        placeholders.put("y", String.valueOf(spawner.getLocY()));
        placeholders.put("z", String.valueOf(spawner.getLocZ()));
        String lastPlayer = spawner.getLastInteractedPlayer();
        placeholders.put("last_player", lastPlayer != null ? lastPlayer : "None");

        ItemStack spawnerItem;

        if (entityType == null) {
            spawnerItem = new ItemStack(Material.SPAWNER);
            spawnerItem.editMeta(meta -> {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                meta.setDisplayName(languageManager.getGuiItemName("spawner_item_list.name", placeholders));
                List<String> lore = new ArrayList<>(Arrays.asList(languageManager.getGuiItemLore("spawner_item_list.lore", placeholders)));
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "Server: " + ChatColor.WHITE + serverName);
                meta.setLore(lore);
            });
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, meta -> {
                meta.setDisplayName(languageManager.getGuiItemName("spawner_item_list.name", placeholders));
                List<String> lore = new ArrayList<>(Arrays.asList(languageManager.getGuiItemLore("spawner_item_list.lore", placeholders)));
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "Server: " + ChatColor.WHITE + serverName);
                meta.setLore(lore);
            });
        }

        VersionInitializer.hideTooltip(spawnerItem);
        return spawnerItem;
    }
    public FilterOption getUserFilter(Player player, String worldName) {
        return userPreferenceCache.getUserFilter(player, worldName);
    }

    /**
     * Gets the user's current sort preference for a world
     */
    public SortOption getUserSort(Player player, String worldName) {
        return userPreferenceCache.getUserSort(player, worldName);
    }
}
