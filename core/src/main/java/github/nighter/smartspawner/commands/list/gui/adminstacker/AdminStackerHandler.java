package github.nighter.smartspawner.commands.list.gui.adminstacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for admin stacker GUI interactions
 */
public class AdminStackerHandler implements Listener {
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int BACK_SLOT = 22;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final SpawnerManagementGUI managementGUI;

    public AdminStackerHandler(SmartSpawner plugin, SpawnerManagementGUI managementGUI) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.managementGUI = managementGUI;
    }

    @EventHandler
    public void onAdminStackerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminStackerHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        SpawnerData spawner = holder.getSpawnerData();
        String worldName = holder.getWorldName();
        int listPage = holder.getListPage();

        if (spawner == null) {
            messageService.sendMessage(player, "spawner_not_found");
            return;
        }

        int slot = event.getSlot();
        handleClick(player, spawner, worldName, listPage, slot);
    }

    private void handleClick(Player player, SpawnerData spawner, String worldName, int listPage, int slot) {
        if (slot == BACK_SLOT) {
            // Return to management GUI
            managementGUI.openManagementMenu(player, spawner.getSpawnerId(), worldName, listPage);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (slot == SPAWNER_INFO_SLOT) {
            // Do nothing for info slot
            return;
        }

        // Check if it's a decrease slot
        for (int i = 0; i < DECREASE_SLOTS.length; i++) {
            if (slot == DECREASE_SLOTS[i]) {
                handleStackChange(player, spawner, worldName, listPage, -STACK_AMOUNTS[i]);
                return;
            }
        }

        // Check if it's an increase slot
        for (int i = 0; i < INCREASE_SLOTS.length; i++) {
            if (slot == INCREASE_SLOTS[i]) {
                handleStackChange(player, spawner, worldName, listPage, STACK_AMOUNTS[i]);
                return;
            }
        }
    }

    private void handleStackChange(Player player, SpawnerData spawner, String worldName, int listPage, int change) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Validate spawner integrity before allowing admin operations
        if (spawnerManager.isGhostSpawner(spawner)) {
            messageService.sendMessage(player, "spawner_invalid");
            return;
        }

        int newStackSize = spawner.getStackSize() + change;
        
        // Ensure stack size is within valid bounds
        if (newStackSize < 1) {
            newStackSize = 1;
        } else if (newStackSize > spawner.getMaxStackSize()) {
            newStackSize = spawner.getMaxStackSize();
        }

        // Update the spawner stack size
        spawner.setStackSize(newStackSize);
        
        // Track interaction
        spawner.updateLastInteractedPlayer(player.getName());

        // Send feedback message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(Math.abs(change)));
        placeholders.put("new_size", String.valueOf(newStackSize));
        
        if (change > 0) {
            messageService.sendMessage(player, "spawner_management.stack_increased", placeholders);
        } else {
            messageService.sendMessage(player, "spawner_management.stack_decreased", placeholders);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Refresh the GUI to show updated values
        AdminStackerUI adminStackerUI = new AdminStackerUI(plugin);
        adminStackerUI.openAdminStackerGui(player, spawner, worldName, listPage);
    }
}