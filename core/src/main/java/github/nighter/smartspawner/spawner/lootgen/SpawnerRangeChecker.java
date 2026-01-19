package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Chunk-based spawner ticking with optional "any online player" gate.
 *
 * - If REQUIRE_ANY_ONLINE_PLAYER = false:
 *     Spawners run with 0 online players as long as their chunk is loaded.
 *
 * - If REQUIRE_ANY_ONLINE_PLAYER = true:
 *     Spawners will NOT run when there are 0 online players, even if chunks are loaded.
 *
 * HARD PAUSE:
 * - When production is not allowed (chunk unloaded OR gate blocks), timer freezes (no catch-up).
 */
public class SpawnerRangeChecker {

    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks

    // ✅ Toggle THIS depending on what you actually want:
    // true  = if 0 players online -> STOP production
    // false = allow production even with 0 players online (fully offline if chunk loaded)
    private static final boolean REQUIRE_ANY_ONLINE_PLAYER = true;

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SmartSpawner-ChunkCheck"));
        Scheduler.runTaskTimer(this::tick, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void tick() {
        this.executor.execute(() -> {
            final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();

            // "Any online player" gate
            final boolean anyOnline = !Bukkit.getOnlinePlayers().isEmpty();
            final boolean gateAllows = !REQUIRE_ANY_ONLINE_PLAYER || anyOnline;

            for (SpawnerData spawner : allSpawners) {
                if (spawner == null) continue;

                final Location loc = spawner.getSpawnerLocation();
                if (loc == null) continue;

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

                    // Allowed only if chunk is loaded AND gate allows
                    final boolean allowed = chunkLoaded && gateAllows;

                    final boolean shouldStop = !allowed;

                    boolean previous = spawner.getSpawnerStop().getAndSet(shouldStop);
                    if (previous != shouldStop) {
                        handleSpawnerStateChange(spawner, shouldStop);
                    }

                    // HARD PAUSE: freeze timer & clear any pre-gen when not allowed
                    if (shouldStop) {
                        spawner.setLastSpawnTime(System.currentTimeMillis());
                        spawner.clearPreGeneratedLoot();
                        return;
                    }

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
        if (spawner != null) spawner.clearPreGeneratedLoot();
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (!shouldStop) activateSpawner(spawner);
        else deactivateSpawner(spawner);

        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        deactivateSpawner(spawner);
        if (!spawner.getSpawnerActive()) return;

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
            if (!spawner.getDataLock().tryLock(50, TimeUnit.MILLISECONDS)) return;
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

                // Final safety: chunk must still be loaded
                if (!loc.getChunk().isLoaded()) {
                    spawner.setLastSpawnTime(System.currentTimeMillis());
                    spawner.clearPreGeneratedLoot();
                    return;
                }

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
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
