package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HARD PAUSE gate:
 * Spawner runs ONLY if:
 *   1) its chunk is loaded AND
 *   2) there is at least 1 eligible player within spawner range (same world)
 */
public class SpawnerRangeChecker {

    private static final long CHECK_INTERVAL = 20L;

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SmartSpawner-RangeGate"));
        Scheduler.runTaskTimer(this::tick, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void tick() {
        executor.execute(() -> {
            final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();

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

                    boolean hasPlayerInRange = hasEligiblePlayerInRange(liveLoc, spawner.getSpawnerRange());

                    final boolean allowed = chunkLoaded && hasPlayerInRange;
                    final boolean shouldStop = !allowed;

                    boolean previous = spawner.getSpawnerStop().getAndSet(shouldStop);
                    if (previous != shouldStop) {
                        handleSpawnerStateChange(spawner, shouldStop);
                    }

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

    private boolean hasEligiblePlayerInRange(Location spawnerLoc, int rangeBlocks) {
        if (spawnerLoc == null || spawnerLoc.getWorld() == null) return false;

        final var spawnerWorldUid = spawnerLoc.getWorld().getUID();
        final double r = Math.max(0, rangeBlocks);
        final double rangeSq = r * r;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null) continue;
            if (!p.isConnected() || p.isDead()) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getWorld() == null) continue;
            if (!spawnerWorldUid.equals(p.getWorld().getUID())) continue;

            Location pl = p.getLocation();
            if (pl == null) continue;

            if (pl.distanceSquared(spawnerLoc) <= rangeSq) {
                return true;
            }
        }
        return false;
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

                if (!loc.getChunk().isLoaded()) {
                    spawner.setLastSpawnTime(System.currentTimeMillis());
                    spawner.clearPreGeneratedLoot();
                    return;
                }

                if (!hasEligiblePlayerInRange(loc, spawner.getSpawnerRange())) {
                    spawner.setLastSpawnTime(System.currentTimeMillis());
                    spawner.clearPreGeneratedLoot();
                    spawner.getSpawnerStop().set(true);
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
