package github.nighter.smartspawner.spawner.data;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles world-related events to manage spawner loading and unloading
 */
public class WorldEventHandler implements Listener {
    private final SmartSpawner plugin;
    private final Logger logger;

    // Track which worlds have been processed for spawner loading
    private final Set<String> processedWorlds = ConcurrentHashMap.newKeySet();

    // Store spawner data that couldn't be loaded due to missing worlds
    private final Map<String, PendingSpawnerData> pendingSpawners = new ConcurrentHashMap<>();

    // Flag to track if initial loading has been attempted
    private volatile boolean initialLoadAttempted = false;

    public WorldEventHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Called when a world is initialized (before it's fully loaded)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        plugin.debug("World initialized: " + world.getName());
    }

    /**
     * Called when a world is fully loaded and ready for use
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        plugin.debug("World loaded: " + worldName);

        // Mark world as processed
        processedWorlds.add(worldName);

        // Try to load any pending spawners for this world
        loadPendingSpawnersForWorld(worldName);

        // If this is during server startup, also attempt initial load
        if (!initialLoadAttempted) {
            // Delay slightly to ensure world is fully ready
            Scheduler.runTaskLater(() -> attemptInitialSpawnerLoad(), 20L);
        }
    }

    /**
     * Called when a world is being saved
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        World world = event.getWorld();
        plugin.debug("World saving: " + world.getName());

        // Flush any pending spawner changes for this world
        plugin.getSpawnerStorage().flushChanges();
    }

    /**
     * Called when a world is being unloaded
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        plugin.debug("World unloading: " + worldName);

        // Remove world from processed set
        processedWorlds.remove(worldName);

        // Unload spawners from this world
        unloadSpawnersFromWorld(worldName);

        // Save any pending changes before unloading
        plugin.getSpawnerStorage().flushChanges();
    }

    /**
     * Attempt to perform initial spawner loading, checking for available worlds
     */
    public void attemptInitialSpawnerLoad() {
        if (initialLoadAttempted) {
            return;
        }

        initialLoadAttempted = true;
        plugin.debug("Attempting initial spawner load...");

        // Load spawner data from file
        Map<String, SpawnerData> allSpawnerData = plugin.getSpawnerStorage().loadAllSpawnersRaw();

        int loadedCount = 0;
        int pendingCount = 0;

        for (Map.Entry<String, SpawnerData> entry : allSpawnerData.entrySet()) {
            String spawnerId = entry.getKey();
            SpawnerData spawner = entry.getValue();

            if (spawner != null) {
                // Successfully loaded spawner
                plugin.getSpawnerManager().addSpawnerToIndexes(spawnerId, spawner);
                loadedCount++;
            } else {
                // Spawner couldn't be loaded, likely due to missing world
                // Store as pending for later loading
                PendingSpawnerData pending = loadPendingSpawnerFromFile(spawnerId);
                if (pending != null) {
                    pendingSpawners.put(spawnerId, pending);
                    pendingCount++;
                }
            }
        }

        logger.info("Initial spawner load complete. Loaded: " + loadedCount +
                   ", Pending (missing worlds): " + pendingCount);

        if (pendingCount > 0) {
            logger.info("Pending spawners will be loaded when their worlds become available.");
        }
    }

    /**
     * Load pending spawners for a specific world that just became available
     */
    private void loadPendingSpawnersForWorld(String worldName) {
        if (pendingSpawners.isEmpty()) {
            return;
        }

        int loadedCount = 0;

        // Create a copy of the keys to avoid concurrent modification
        Set<String> spawnerIds = new HashSet<>(pendingSpawners.keySet());

        for (String spawnerId : spawnerIds) {
            PendingSpawnerData pending = pendingSpawners.get(spawnerId);

            if (pending != null && worldName.equals(pending.worldName)) {
                // Try to load this spawner now that its world is available
                SpawnerData spawner = plugin.getSpawnerStorage().loadSpecificSpawner(spawnerId);

                if (spawner != null) {
                    plugin.getSpawnerManager().addSpawnerToIndexes(spawnerId, spawner);
                    pendingSpawners.remove(spawnerId);
                    loadedCount++;
                    plugin.debug("Loaded pending spawner " + spawnerId + " for world " + worldName);
                }
            }
        }

        if (loadedCount > 0) {
            logger.info("Loaded " + loadedCount + " pending spawners for world: " + worldName);
        }
    }

    /**
     * Unload all spawners from a specific world
     */
    private void unloadSpawnersFromWorld(String worldName) {
        Set<SpawnerData> worldSpawners = plugin.getSpawnerManager().getSpawnersInWorld(worldName);

        if (worldSpawners != null && !worldSpawners.isEmpty()) {
            int unloadedCount = 0;

            for (SpawnerData spawner : new HashSet<>(worldSpawners)) {
                // Remove hologram and cleanup
                spawner.removeHologram();
                unloadedCount++;
            }

            logger.info("Unloaded " + unloadedCount + " spawners from world: " + worldName);
        }
    }

    /**
     * Load basic spawner information without creating the full SpawnerData object
     */
    private PendingSpawnerData loadPendingSpawnerFromFile(String spawnerId) {
        try {
            String locationString = plugin.getSpawnerStorage().getRawLocationString(spawnerId);
            if (locationString != null) {
                String[] locParts = locationString.split(",");
                if (locParts.length >= 1) {
                    return new PendingSpawnerData(spawnerId, locParts[0]);
                }
            }
        } catch (Exception e) {
            plugin.debug("Error loading pending spawner data for " + spawnerId + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * Check if a world is currently loaded and available
     */
    public boolean isWorldLoaded(String worldName) {
        return processedWorlds.contains(worldName) && Bukkit.getWorld(worldName) != null;
    }

    /**
     * Get the count of pending spawners waiting for worlds to load
     */
    public int getPendingSpawnerCount() {
        return pendingSpawners.size();
    }

    /**
     * Simple data class to store basic spawner information for pending loading
     */
    private static class PendingSpawnerData {
        final String spawnerId;
        final String worldName;

        PendingSpawnerData(String spawnerId, String worldName) {
            this.spawnerId = spawnerId;
            this.worldName = worldName;
        }
    }
}
