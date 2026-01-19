package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Custom mode:
 * - Spawners run WITHOUT any player requirement.
 * - The ONLY requirement is: the spawner's chunk must be loaded.
 *   (Chunk loaders like WildLoaders keep chunks loaded -> production continues even with 0 online players.)
 *
 * Notes:
 * - If chunk is not loaded, spawner is stopped.
 * - This can increase production significantly; ensure your spawner caps/limits are configured.
 */
public class SpawnerRangeChecker {

    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;

    // We keep a lightweight async executor to iterate spawners without blocking main thread.
    // All world/chunk access is still done via Scheduler.runLocationTask for Folia/Leaf safety.
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SmartSpawner-ChunkCheck"));
        initializeTask();
    }

    private void initializeTask() {
        Scheduler.runTaskTimer(this::tick, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void tick() {
        this.executor.execute(() -> {
            final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();

            for (SpawnerData spawner : allSpawners) {
                if (spawner == null) continue;

                final Location loc = spawner.getSpawnerLocation();
                if (loc == null) continue;

                // Folia/Leaf: chunk/world checks must happen on the region thread for that location
                Scheduler.runLocationTask(loc, () -> {
                    if (!isSpawnerValid(spawner)) {
                        cleanupRemovedSpawner(spawner.getSpawnerId());
                        return;
                    }

                    Location liveLoc = spawner.getSpawnerLocation();
                    if (liveLoc == null || liveLoc.getWorld() == null) return;

                    // ✅ ONLY condition: chunk must be loaded
                    boolean chunkLoaded;
                    try {
                        chunkLoaded = liveLoc.getChunk().isLoaded();
                    } catch (Throwable t) {
                        // If anything goes wrong, fail-safe to "stopped"
                        chunkLoaded = false;
                    }

                    boolean shouldStop = !chunkLoaded;

                    // Update stop flag only if changed
                    boolean previous = spawner.getSpawnerStop().getAndSet(shouldStop);
                    if (previous != shouldStop) {
                        handleSpawnerStateChange(spawner, shouldStop);
                    }

                    // If active and not stopped, run loot tick
                    if (spawner.getSpawnerActive() && !spawner.getSpawnerStop().get()) {
                        checkAndSpawnLoot(spawner);
                    }
                });
            }
        });
    }

    private boolean isSpawnerValid(SpawnerData spawner) {
        // Still present in manager?
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        if (current == null) return false;

        // Prevent stale copies being processed
        if (current != spawner) return false;

        Location loc = spawner.getSpawnerLocation();
        return loc != null && loc.getWorld() != null;
    }

    private void cleanupRemovedSpawner(String spawnerId) {
        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner != null) {
            spawner.clearPreGeneratedLoot();
        }
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (!shouldStop) {
            activateSpawner(spawner);
        } else {
            deactivateSpawner(spawner);
        }

        // Force GUI update when state changes
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        // Reset any pre-generated loot to avoid stale state
        deactivateSpawner(spawner);

        if (!spawner.getSpawnerActive()) {
            return;
        }

        // Start countdown immediately
        long currentTime = System.currentTimeMillis();
        spawner.setLastSpawnTime(currentTime);

        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        }
    }

    public void deactivateSpawner(SpawnerData spawner) {
        spawner.clearPreGeneratedLoot();
    }

    private void checkAndSpawnLoot(SpawnerData spawner) {
        long cachedDelay = spawner.getCachedSpawnDelay();
        if (cachedDelay == 0) {
            // Convert ticks to milliseconds (+20 ticks safety margin like original code)
            cachedDelay = (spawner.getSpawnDelay() + 20L) * 50L;
            spawner.setCachedSpawnDelay(cachedDelay);
        }

        final long finalCachedDelay = cachedDelay;

        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long timeElapsed = currentTime - lastSpawnTime;

        if (timeElapsed < cachedDelay) {
            // Update open GUIs if needed
            if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
                plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
            }
            return;
        }

        try {
            if (!spawner.getDataLock().tryLock(50, TimeUnit.MILLISECONDS)) {
                return;
            }
            try {
                currentTime = System.currentTimeMillis();
                lastSpawnTime = spawner.getLastSpawnTime();
                timeElapsed = currentTime - lastSpawnTime;

                if (timeElapsed < cachedDelay) return;
                if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                    spawner.clearPreGeneratedLoot();
                    return;
                }

                final Location spawnerLocation = spawner.getSpawnerLocation();
                if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

                // Must run on region thread for safety
                Scheduler.runLocationTask(spawnerLocation, () -> {
                    if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                        spawner.clearPreGeneratedLoot();
                        return;
                    }

                    // ✅ Hard requirement: chunk loaded
                    if (!spawnerLocation.getChunk().isLoaded()) {
                        // Do NOT advance time; we'll retry next tick
                        return;
                    }

                    long timeSinceLastSpawn = System.currentTimeMillis() - spawner.getLastSpawnTime();
                    if (timeSinceLastSpawn < finalCachedDelay - 100) {
                        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
                            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
                        }
                        return;
                    }

                    // If there is pre-generated loot, commit it; otherwise generate fresh loot
                    if (spawner.hasPreGeneratedLoot()) {
                        List<ItemStack> items = spawner.getAndClearPreGeneratedItems();
                        int exp = spawner.getAndClearPreGeneratedExperience();
                        plugin.getSpawnerLootGenerator().addPreGeneratedLoot(spawner, items, exp);
                    } else {
                        plugin.getSpawnerLootGenerator().spawnLootToSpawner(spawner);
                    }

                    if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
                        plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
                    }
                });

            } finally {
                spawner.getDataLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void cleanup() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
