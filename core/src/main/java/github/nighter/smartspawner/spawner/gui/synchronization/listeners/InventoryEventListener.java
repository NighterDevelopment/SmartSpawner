package github.nighter.smartspawner.spawner.gui.synchronization.listeners;

import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;
import java.util.UUID;

/**
 * Listener for inventory-related events.
 * Tracks when players open and close spawner GUIs.
 */
public class InventoryEventListener implements Listener {

    private final ViewerTrackingManager viewerTrackingManager;
    private final Runnable onViewerAdded;
    private final Set<Class<? extends InventoryHolder>> validHolderTypes;

    public InventoryEventListener(ViewerTrackingManager viewerTrackingManager, Runnable onViewerAdded) {
        this.viewerTrackingManager = viewerTrackingManager;
        this.onViewerAdded = onViewerAdded;
        this.validHolderTypes = Set.of(
                SpawnerMenuHolder.class,
                StoragePageHolder.class,
                FilterConfigHolder.class
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!isValidHolder(holder)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        SpawnerData spawnerData = null;
        ViewerTrackingManager.ViewerType viewerType = null;

        if (holder instanceof SpawnerMenuHolder spawnerHolder) {
            spawnerData = spawnerHolder.getSpawnerData();
            viewerType = ViewerTrackingManager.ViewerType.MAIN_MENU;
        } else if (holder instanceof StoragePageHolder storageHolder) {
            spawnerData = storageHolder.getSpawnerData();
            viewerType = ViewerTrackingManager.ViewerType.STORAGE;
        } else if (holder instanceof FilterConfigHolder filterHolder) {
            spawnerData = filterHolder.getSpawnerData();
            viewerType = ViewerTrackingManager.ViewerType.FILTER;
        }

        if (spawnerData != null && viewerType != null) {
            // Record the interacting player immediately on open, not on close.
            // This avoids data loss when a player disconnects while the GUI is open,
            // since InventoryCloseEvent is not guaranteed to fire before PlayerQuitEvent.
            spawnerData.updateLastInteractedPlayer(player.getName());

            viewerTrackingManager.trackViewer(playerId, spawnerData, viewerType);
            onViewerAdded.run(); // Trigger update task start if needed
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        viewerTrackingManager.untrackViewer(player.getUniqueId());
    }

    /**
     * Validates if the inventory holder is a supported spawner GUI type.
     */
    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }
}
