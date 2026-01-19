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
 * Chunk-based spawner ticking:
 * - NO player requirement.
 * - Spawners run ONLY if their chunk is loaded.
 *
 * HARD PAUSE behavior:
 * - When chunk is NOT loaded, spawner is stopped and its timer is frozen
 *   (no offline progress / no "catch-up" production).
 * - When chunk becomes loaded again, spawner resumes normally.
 */
public class SpawnerRangeChecker {

    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
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

                // Folia/Leaf: any world/chunk access should happen on the region thread for that location
                Scheduler.runLocationTask(loc, () -> {
                    if (!isSpawnerValid(spawner)) {
                        cleanupRemovedSpawner(spawner.getSpawnerId());
                        return;
                    }

                    final Location liveLoc = spawner.getSpawnerLocation();
                    if (liveLoc == null || liveLoc.getWorld() == null) return;

                    boolean chunkLoaded;
                    try {
                        chunkLoaded = liveLoc.getChunk().isLoaded();
                    } catch (Throwable t) {
                        chunkLoaded = false;
                    }

                    // ONLY rule:
                    // chunk loaded -> shouldStop=false
                    // chunk unloaded -> shouldStop=true (HARD PAUSE)
                    final boolean shouldStop = !chunkLoaded;

                    // Apply stop/resume state transitions
                    boolean previous = spawner.getSpawnerStop().getAndSet(shouldStop);
                    if (previous != shouldStop) {
                        handleSpawnerStateChange(spawner, shouldStop);
                    }

                    // HARD PAUSE:
                    // If chunk is not loaded, freeze timer so no offline progress accumulates.
                    // We also do NOT run any loot logic.
                    if (shouldStop) {
                        // Freeze timer to "now" to prevent catch-up on next load
                        spawner.setLastSpawnTime(System.currentTimeMillis());
                        spawner.clearPreGeneratedLoot();
                        return;
                    }

                    // If active and not stopped, tick loot
                    if (spawner.getSpawnerActive() && !spawner.getSpawnerStop().get()) {
                        checkAndSpawnLoot(spawner);
                    }
                });
            }
        });
    }

    private boolean isSpawnerValid(SpawnerData spawner) {
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        if (current == null) return false;
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

        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        // ensure clean state
        deactivateSpawner(spawner);

        if (!spawner.getSpawnerActive()) {
            return;
        }

        // Start countdown from now
        spawner.setLastSpawnTime(System.currentTimeMillis());

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
            // (ticks + 20 safety) -> ms
            cachedDelay = (spawner.getSpawnDelay() + 20L) * 50L;
            spawner.setCachedSpawnDelay(cachedDelay);
        }

        final long finalCachedDelay = cachedDelay;

        long now = System.currentTimeMillis();
        long last = spawner.getLastSpawnTime();
        long elapsed = now - last;

        if (elapsed < cachedDelay) {
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
                now = System.currentTimeMillis();
                last = spawner.getLastSpawnTime();
                elapsed = now - last;

                if (elapsed < cachedDelay) return;

                if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                    spawner.clearPreGeneratedLoot();
                    return;
                }

                final Location loc = spawner.getSpawnerLocation();
                if (loc == null || loc.getWorld() == null) return;

                // Safety: ensure chunk still loaded at spawn moment
                if (!loc.getChunk().isLoaded()) {
                    // HARD PAUSE freeze here too
                    spawner.setLastSpawnTime(System.currentTimeMillis());
                    spawner.clearPreGeneratedLoot();
                    return;
                }

                // Commit/Generate loot on region thread (already on region thread here)
                long timeSinceLast = System.currentTimeMillis() - spawner.getLastSpawnTime();
                if (timeSinceLast < finalCachedDelay - 100) {
                    if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
                        plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
                    }
                    return;
                }

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
