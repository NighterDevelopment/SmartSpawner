package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerSellConfirmListener implements Listener {

    private final SmartSpawner plugin;
    // Tracks which players are currently executing a sell confirmation to prevent
    // duplicate processing when a client replays confirm-click packets within the same tick.
    private final Set<UUID> activeSells = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SpawnerSellConfirmListener(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SpawnerSellConfirmHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SpawnerSellConfirmHolder confirmHolder = (SpawnerSellConfirmHolder) holder;
        SpawnerData spawner = confirmHolder.getSpawnerData();

        if (spawner == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        // OPTIMIZATION: Get layout once and check action based on clicked slot
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentSellConfirmLayout();
        if (layout == null) {
            player.closeInventory();
            return;
        }

        Optional<GuiButton> buttonOpt = layout.getButtonAtSlot(slot);
        if (!buttonOpt.isPresent()) {
            return;
        }

        GuiButton button = buttonOpt.get();
        String action = button.getDefaultAction();

        if (action == null) {
            return;
        }

        switch (action) {
            case "cancel":
                handleCancel(player, spawner, confirmHolder.getPreviousGui());
                break;
            case "confirm":
                handleConfirm(player, spawner, confirmHolder.getPreviousGui(), confirmHolder.isCollectExp());
                break;
            default:
                // Info button or unknown action - do nothing
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SpawnerSellConfirmHolder confirmHolder)) {
            return;
        }

        // Always release the interaction lock so the spawner is not stuck when the
        // player closes the GUI with Escape instead of clicking Cancel.
        confirmHolder.getSpawnerData().clearInteracted();
    }

    private void handleCancel(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Play sound instead of sending message
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Clear interaction state
        spawner.clearInteracted();

        // Reopen previous GUI
        reopenPreviousGui(player, spawner, previousGui);
    }

    private void handleConfirm(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui, boolean collectExp) {
        // Prevent the same player from running two concurrent sell confirmations
        // (e.g. duplicate confirm-click packets sent by a cheat client within the same tick).
        if (!activeSells.add(player.getUniqueId())) {
            return;
        }

        try {
            // Collect exp if requested
            if (collectExp) {
                plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
            }

            // Trigger the actual sell operation
            plugin.getSpawnerSellManager().sellAllItems(player, spawner);

            // Release the interaction lock only AFTER the sell has fully completed so that the
            // spawner cannot be re-opened by another replayed "open" packet mid-transaction.
            spawner.clearInteracted();

            // Schedule GUI reopening after sell completes (1 tick delay to ensure sell process finishes)
            github.nighter.smartspawner.Scheduler.runTask(() -> {
                reopenPreviousGui(player, spawner, previousGui);
            });
        } finally {
            activeSells.remove(player.getUniqueId());
        }
    }

    private void reopenPreviousGui(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Check if player is Bedrock
        boolean isBedrockPlayer = isBedrockPlayer(player);

        switch (previousGui) {
            case MAIN_MENU:
                if (isBedrockPlayer && plugin.getSpawnerMenuFormUI() != null) {
                    // Reopen FormUI for Bedrock players
                    plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
                } else {
                    // Reopen standard GUI for Java players
                    plugin.getSpawnerMenuUI().openSpawnerMenu(player, spawner, true);
                }
                break;
            case STORAGE:
                // Storage GUI works the same for both Java and Bedrock
                org.bukkit.inventory.Inventory storageInventory = plugin.getSpawnerStorageUI()
                        .createStorageInventory(spawner, 1, -1);
                player.openInventory(storageInventory);
                break;
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null ||
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }
}


