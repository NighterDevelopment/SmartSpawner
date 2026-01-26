package github.nighter.smartspawner.spawner.data;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        plugin.debug("World initialized: " + world.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        plugin.debug("World loaded: " + worldName);

        processedWorlds.add(worldName);

        // Load pending spawners that belong to this world
        loadPendingSpawnersForWorld(worldName);

        // Startup initial load (once)
        if (!initialLoadAttempted) {
            Scheduler.runTaskLater(this::attemptInitialSpawnerLoad, 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        World world = event.getWorld();
        plugin.debug("World saving: " + world.getName());
        plugin.getSpawnerFileHandler().flushChanges();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        plugin.debug("World unloading: " + worldName);

        processedWorlds.remove(worldName);

        unloadSpawnersFromWorld(worldName);

        plugin.getSpawnerFileHandler().flushChanges();
    }

    public void attemptInitialSpawnerLoad() {
        if (initialLoadAttempted) return;
        initialLoadAttempted = true;

        plugin.debug("Attempting initial spawner load...");

        Map<String, SpawnerData> allSpawnerData = plugin.getSpawnerFileHandler().loadAllSpawnersRaw();

        int loadedCount = 0;
        int pendingCount = 0;

        for (Map.Entry<String, SpawnerData> entry : allSpawnerData.entrySet()) {
            String spawnerId = entry.getKey();
            SpawnerData spawner = entry.getValue();

            if (spawner != null) {
                plugin.getSpawnerManager().addSpawnerToIndexes(spawnerId, spawner);
                loadedCount++;
            } else {
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

    private void loadPendingSpawnersForWorld(String worldName) {
        if (pendingSpawners.isEmpty()) return;

        int loadedCount = 0;

        Set<String> spawnerIds = new HashSet<>(pendingSpawners.keySet());
        for (String spawnerId : spawnerIds) {
            PendingSpawnerData pending = pendingSpawners.get(spawnerId);
            if (pending == null) continue;

            if (worldName.equals(pending.worldName)) {
                // NOTE: your project uses loadSpecificSpawner(...)
                SpawnerData spawner = plugin.getSpawnerFileHandler().loadSpecificSpawner(spawnerId);
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

            // Restart hoppers for the spawners in this world
            if (plugin.getHopperHandler() != null) {
                plugin.getHopperHandler().restartAllHoppers();
            }
        }
    }

    /**
     * Key fix:
     * On world unload, remove spawners from runtime indexes (prevents stale World instance),
     * and mark as pending so they are re-loaded from disk on world load.
     */
    private void unloadSpawnersFromWorld(String worldName) {
        Set<SpawnerData> worldSpawners = plugin.getSpawnerManager().getSpawnersInWorld(worldName);

        if (worldSpawners == null || worldSpawners.isEmpty()) return;

        int unloadedCount = 0;

        for (SpawnerData spawner : new HashSet<>(worldSpawners)) {
            if (spawner == null) continue;

            String spawnerId = spawner.getSpawnerId();
            if (spawnerId != null) {
                pendingSpawners.put(spawnerId, new PendingSpawnerData(spawnerId, worldName));
                plugin.getSpawnerManager().removeSpawner(spawnerId);
            } else {
                spawner.removeHologram();
            }

            unloadedCount++;
        }

        logger.info("Unloaded " + unloadedCount + " spawners from world: " + worldName + " (marked pending for reload)");
    }

    private PendingSpawnerData loadPendingSpawnerFromFile(String spawnerId) {
        try {
            String locationString = plugin.getSpawnerFileHandler().getRawLocationString(spawnerId);
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

    public boolean isWorldLoaded(String worldName) {
        return processedWorlds.contains(worldName) && Bukkit.getWorld(worldName) != null;
    }

    public int getPendingSpawnerCount() {
        return pendingSpawners.size();
    }

    private static class PendingSpawnerData {
        final String spawnerId;
        final String worldName;

        PendingSpawnerData(String spawnerId, String worldName) {
            this.spawnerId = spawnerId;
            this.worldName = worldName;
        }
    }
}
