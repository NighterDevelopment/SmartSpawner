package github.nighter.smartspawner.spawner.gui.synchronization.listeners;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener for player-related events.
 * Handles cleanup when players disconnect.
 */
public class PlayerEventListener implements Listener {

    private final ViewerTrackingManager viewerTrackingManager;

    public PlayerEventListener(ViewerTrackingManager viewerTrackingManager) {
        this.viewerTrackingManager = viewerTrackingManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        
        // Clear viewer tracking
        viewerTrackingManager.untrackViewer(playerUUID);
        
        // Clear player-specific caches to prevent memory leaks
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin != null && plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().clearPlayerCache(playerUUID);
        }
        
        // Clear Bedrock player cache
        SpawnerMobHeadTexture.clearBedrockPlayerCache(playerUUID);
    }
}

