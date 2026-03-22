package github.nighter.smartspawner.commands.list.gui.management;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.commands.list.gui.adminstacker.AdminStackerUI;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SpawnerManagementHandler implements Listener {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final SpawnerStorage spawnerStorage;
    private final ListSubCommand listSubCommand;
    private final SpawnerMenuUI spawnerMenuUI;
    private final AdminStackerUI adminStackerUI;

    public SpawnerManagementHandler(SmartSpawner plugin, ListSubCommand listSubCommand) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerStorage = plugin.getSpawnerStorage();
        this.listSubCommand = listSubCommand;
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.adminStackerUI = new AdminStackerUI(plugin);
    }

    @EventHandler
    public void onSpawnerManagementClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerManagementHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String spawnerId = holder.getSpawnerId();
        String worldName = holder.getWorldName();
        int listPage = holder.getListPage();
        String targetServer = holder.getTargetServer();
        boolean isRemote = holder.isRemoteServer();

        int slot = event.getSlot();

        // Handle back button - works for both local and remote
        if (slot == 26) {
            handleBack(player, worldName, listPage, targetServer);
            return;
        }

        // For remote servers, handle specific actions
        if (isRemote) {
            switch (slot) {
                case 10 -> {
                    // Teleport disabled for remote
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                case 12 -> handleRemoteOpenSpawnerInfo(player, spawnerId, targetServer, worldName, listPage);
                case 14 -> handleRemoteStackManagement(player, spawnerId, targetServer, worldName, listPage);
                case 16 -> handleRemoteRemoveSpawner(player, spawnerId, targetServer, worldName, listPage);
            }
            return;
        }

        // Local spawner actions
        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner == null) {
            messageService.sendMessage(player, "spawner_not_found");
            return;
        }

        switch (slot) {
            case 10 -> handleTeleport(player, spawner);
            case 12 -> handleOpenSpawner(player, spawner);
            case 14 -> handleStackManagement(player, spawner, worldName, listPage);
            case 16 -> handleRemoveSpawner(player, spawner, worldName, listPage);
        }
    }

    private void handleTeleport(Player player, SpawnerData spawner) {
        Location loc = spawner.getSpawnerLocation().clone().add(0.5, 1, 0.5);
        player.teleportAsync(loc);
        messageService.sendMessage(player, "teleported_to_spawner");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.closeInventory();
    }

    private void handleOpenSpawner(Player player, SpawnerData spawner) {
        // Check if skip_main_gui is enabled
        if (plugin.getGuiLayoutConfig().isSkipMainGui()) {
            // Open storage GUI directly
            org.bukkit.inventory.Inventory storageInventory = plugin.getSpawnerStorageUI()
                    .createStorageInventory(spawner, 1, -1);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            player.openInventory(storageInventory);
            return;
        }

        // Check if player is Bedrock and use appropriate menu
        if (isBedrockPlayer(player)) {
            if (plugin.getSpawnerMenuFormUI() != null) {
                plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
            } else {
                // Fallback to standard GUI if FormUI not available
                spawnerMenuUI.openSpawnerMenu(player, spawner, false);
            }
        } else {
            spawnerMenuUI.openSpawnerMenu(player, spawner, false);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleStackManagement(Player player, SpawnerData spawner, String worldName, int listPage) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        adminStackerUI.openAdminStackerGui(player, spawner, worldName, listPage);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleRemoveSpawner(Player player, SpawnerData spawner, String worldName, int listPage) {
        // Remove the spawner block and data
        Location loc = spawner.getSpawnerLocation();
        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawner);
        String spawnerId = spawner.getSpawnerId();
        spawner.getSpawnerStop().set(true);
        if (loc.getBlock().getType() == Material.SPAWNER) {
            loc.getBlock().setType(Material.AIR);
        }

        // Remove from manager and save
        spawnerManager.removeSpawner(spawnerId);
        spawnerStorage.markSpawnerDeleted(spawnerId);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", spawner.getSpawnerId());
        messageService.sendMessage(player, "management.removed", placeholders);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

        // Return to spawner list
        handleBack(player, worldName, listPage, null);
    }

    private void handleBack(Player player, String worldName, int listPage, String targetServer) {
        // Get the user's current preferences for filter and sort
        FilterOption filter = FilterOption.ALL; // Default
        SortOption sort = SortOption.DEFAULT; // Default

        // Try to get saved preferences
        try {
            filter = listSubCommand.getUserFilter(player, worldName);
            sort = listSubCommand.getUserSort(player, worldName);
        } catch (Exception ignored) {
            // Use defaults if loading fails
        }

        // Check if going back to a remote server's spawner list
        if (targetServer != null && !targetServer.equals(listSubCommand.getCurrentServerName())) {
            listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, listPage);
        } else {
            listSubCommand.openSpawnerListGUI(player, worldName, listPage, filter, sort);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null ||
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    // ===== Remote Spawner Handlers =====

    private void handleRemoteOpenSpawnerInfo(Player player, String spawnerId, String targetServer,
                                              String worldName, int listPage) {
        // Fetch spawner data from database and display info
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "action_failed");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        dbHandler.getRemoteSpawnerByIdAsync(targetServer, spawnerId, spawnerData -> {
            if (spawnerData == null) {
                messageService.sendMessage(player, "spawner_not_found");
                return;
            }

            // Send spawner info as chat message since we can't open the actual spawner menu
            player.sendMessage("");
            player.sendMessage("§6§l=== Remote Spawner Info ===");
            player.sendMessage("§7Server: §f" + spawnerData.getServerName());
            player.sendMessage("§7ID: §f#" + spawnerData.getSpawnerId());
            player.sendMessage("§7Type: §f" + formatEntityName(spawnerData.getEntityType().name()));
            player.sendMessage("§7Location: §f" + spawnerData.getWorldName() + " (" +
                    spawnerData.getLocX() + ", " + spawnerData.getLocY() + ", " + spawnerData.getLocZ() + ")");
            player.sendMessage("§7Stack Size: §f" + spawnerData.getStackSize());
            player.sendMessage("§7Status: " + (spawnerData.isActive() ? "§aActive" : "§cInactive"));
            player.sendMessage("§7Stored XP: §f" + spawnerData.getStoredExp());
            player.sendMessage("§7Total Items: §f" + spawnerData.getTotalItems());
            player.sendMessage("§6§l==========================");
            player.sendMessage("");
        });
    }

    private void handleRemoteStackManagement(Player player, String spawnerId, String targetServer,
                                              String worldName, int listPage) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Open the admin stacker UI for remote spawner
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "action_failed");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Fetch current stack size and open editor
        dbHandler.getRemoteSpawnerByIdAsync(targetServer, spawnerId, spawnerData -> {
            if (spawnerData == null) {
                messageService.sendMessage(player, "spawner_not_found");
                return;
            }

            // Open remote admin stacker UI
            adminStackerUI.openRemoteAdminStackerGui(player, spawnerData, targetServer, worldName, listPage);
        });
    }

    private void handleRemoteRemoveSpawner(Player player, String spawnerId, String targetServer,
                                            String worldName, int listPage) {
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "action_failed");
            return;
        }

        // Delete from database
        dbHandler.deleteRemoteSpawnerAsync(targetServer, spawnerId, success -> {
            if (success) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("id", spawnerId);
                messageService.sendMessage(player, "management.removed", placeholders);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

                // Note: The physical block on the remote server will remain until that server syncs
                player.sendMessage("§e[Note] The spawner block on " + targetServer + " will be removed when that server syncs.");
            } else {
                messageService.sendMessage(player, "spawner_not_found");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            // Return to spawner list
            handleBack(player, worldName, listPage, targetServer);
        });
    }

    private SpawnerDatabaseHandler getDbHandler() {
        if (spawnerStorage instanceof SpawnerDatabaseHandler) {
            return (SpawnerDatabaseHandler) spawnerStorage;
        }
        return null;
    }

    private String formatEntityName(String name) {
        return Arrays.stream(name.toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(name);
    }
}
