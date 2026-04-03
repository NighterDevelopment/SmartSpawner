package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all click/close events for the sell-confirm GUI.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SELL FLOW  (sell confirmation ENABLED – default)
 * ─────────────────────────────────────────────────────────────────────────────
 * Entry: SpawnerMenuAction / SpawnerStorageAction  →  SpawnerSellConfirmUI.openSellConfirmGui()
 *
 *  1. openSellConfirmGui():
 *       • spawner.isSelling() – abort if a sell is already running for this spawner.
 *       • Creates SpawnerSellConfirmHolder inventory, opens it for the player.
 *
 *  2. Player clicks CONFIRM  →  handleConfirm():
 *       • activeSells.add(uuid) – guards against duplicate confirm-click packets.
 *         [LEAK RISK: removed in onComplete callback AND onPlayerQuit]
 *       • If collectExp: handleExpBottleClick() runs synchronously here.
 *       • SpawnerSellManager.sellAllItems(player, spawner, onComplete) called.
 *         sellAllItems() owns the async chain from this point:
 *           a. spawner.startSelling() CAS false→true – real dupe guard.
 *              [LEAK RISK: always released in finally inside the location task]
 *           b. closeAllViewersInventory(spawner) – evicts ALL current viewers.
 *           c. Snapshot virtual inventory (safe while isSelling blocks mutations).
 *           d. Async thread: calculate SellResult (pure CPU, no Bukkit API).
 *           e. Location/main thread: applySellResult() – deposit money, remove items,
 *              update hologram, notify player.
 *           f. onComplete.run():
 *                • activeSells.remove(uuid)
 *                • Scheduler.runTask → reopenPreviousGui() for the selling player only.
 *           g. spawner.stopSelling() released in finally.
 *
 *  3. Player clicks CANCEL  →  handleCancel():
 *       • reopenPreviousGui() immediately (sync).
 *
 *  4. Player presses ESCAPE (GUI close without clicking)  →  onInventoryClose():
 *       • No extra work needed; the sell never started.
 *
 *  5. Player disconnects while sell is in flight  →  onPlayerQuit():
 *       • activeSells.remove(uuid) – prevents permanent leak.
 *       (spawner.isSelling is released by its own finally chain.)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SELL FLOW  (sell confirmation DISABLED – skip_sell_confirmation: true)
 * ─────────────────────────────────────────────────────────────────────────────
 * Entry: same as above  →  SpawnerSellConfirmUI.openSellConfirmGui()  (skip path)
 *
 *  1. openSellConfirmGui():
 *       • spawner.isSelling() guard (same as above).
 *       • If collectExp: handleExpBottleClick() runs synchronously here.
 *       • player.closeInventory() – close any currently open GUI first.
 *       • SpawnerSellManager.sellAllItems(player, spawner, null) called.
 *
 *  No confirm GUI is ever created, so:
 *       • SpawnerSellConfirmHolder is never instantiated.
 *       • activeSells is never touched (no confirm click to guard).
 *       • onInventoryClose / handleCancel / handleConfirm are never invoked.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * STATE OBJECTS AND LEAK CHECKLIST
 * ─────────────────────────────────────────────────────────────────────────────
 *  activeSells (ConcurrentHashSet<UUID>)
 *    Set by   : activeSells.add() at the top of handleConfirm()
 *    Cleared by: onComplete callback (normal completion)
 *               onPlayerQuit() (disconnect during sell)
 *    Leak check: both exit paths covered; skip path never touches this set.
 *
 *  spawner.selling (AtomicBoolean, owned by SpawnerSellManager)
 *    Set by   : spawner.startSelling() inside sellAllItems()
 *    Cleared by: spawner.stopSelling() in the location-thread finally block
 *               (runs even if player is offline or sell fails mid-way).
 *    Leak check: finally block guarantees release.
 */
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

    private void handleCancel(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Play sound instead of sending message
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Reopen previous GUI
        reopenPreviousGui(player, spawner, previousGui);
    }

    private void handleConfirm(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui, boolean collectExp) {
        // Prevent the same player from running two concurrent sell confirmations
        // (e.g. duplicate confirm-click packets sent by a cheat client within the same tick).
        if (!activeSells.add(player.getUniqueId())) {
            return;
        }

        // Collect exp if requested (sync, safe on this thread)
        if (collectExp) {
            plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
        }

        // Callback runs on the spawner's region/main thread after the sell fully completes.
        // Defers the GUI reopen until the inventory is actually emptied, closing the race
        // window where a storage GUI could be reopened with stale (pre-removal) items.
        final UUID playerId = player.getUniqueId();
        Runnable onComplete = () -> {
            activeSells.remove(playerId);
            // player.openInventory must run on the global/main thread; schedule with runTask
            // so it is always dispatched correctly on both Paper and Folia.
            Scheduler.runTask(() -> reopenPreviousGui(player, spawner, previousGui));
        };

        plugin.getSpawnerSellManager().sellAllItems(player, spawner, onComplete);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // If a player disconnects while a sell is in progress, ensure the activeSells set
        // is cleaned up so the entry doesn't leak (the spawner's isSelling flag will be
        // cleared by the normal async chain completing even without the player online).
        activeSells.remove(event.getPlayer().getUniqueId());
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


