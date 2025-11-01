package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SmartSpawner-RangeCheck"));
        initializeRangeCheckTask();
    }

    private void initializeRangeCheckTask() {
        // Using the global scheduler, but only for coordinating region-specific checks
        Scheduler.runTaskTimer(this::scheduleRegionSpecificCheck, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void scheduleRegionSpecificCheck() {
        PlayerRangeWrapper[] rangePlayers = getRangePlayers();

        this.executor.execute(() -> {
            final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();

            final RangeMath rangeCheck = new RangeMath(rangePlayers, allSpawners);
            final boolean[] spawnersPlayerFound = rangeCheck.getActiveSpawners();

            for (int i = 0; i < spawnersPlayerFound.length; i++) {
                final boolean expectedStop = !spawnersPlayerFound[i];
                final SpawnerData sd = allSpawners.get(i);
                final String spawnerId = sd.getSpawnerId();

                // Atomically update spawner stop flag only if it has changed
                if (sd.getSpawnerStop().compareAndSet(!expectedStop, expectedStop)) {
                    // Schedule main-thread task for actual state change
                    Scheduler.runLocationTask(sd.getSpawnerLocation(), () -> {
                        if (!isSpawnerValid(sd)) {
                            cleanupRemovedSpawner(spawnerId);
                            return;
                        }

                        // Double-check atomic boolean before applying
                        if (sd.getSpawnerStop().get() == expectedStop) {
                            handleSpawnerStateChange(sd, expectedStop);
                        }
                    });
                }
            }
        });
    }

    private PlayerRangeWrapper[] getRangePlayers() {
        final Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        final PlayerRangeWrapper[] rangePlayers = new PlayerRangeWrapper[onlinePlayers.size()];
        int i = 0;

        for (Player p : onlinePlayers) {

            boolean conditions = p.isConnected() && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;

            // Store data in wrapper for faster access
            rangePlayers[i++] = new PlayerRangeWrapper(p.getWorld().getUID(),
                    p.getX(), p.getY(), p.getZ(),
                    conditions
            );
        }

        return rangePlayers;
    }

    private boolean isSpawnerValid(SpawnerData spawner) {
        // Check 1: Still in manager?
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        if (current == null) {
            return false;
        }

        // Check 2: Same instance? (prevents processing stale copies)
        if (current != spawner) {
            return false;
        }

        // Check 3: Location still valid?
        Location loc = spawner.getSpawnerLocation();
        return loc != null && loc.getWorld() != null;
    }

    private void cleanupRemovedSpawner(String spawnerId) {
        // Clear any pre-generated loot when spawner is removed
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

        // Force GUI update when spawner state changes
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        deactivateSpawner(spawner);

        // Check if spawner is actually active before starting
        if (!spawner.getSpawnerActive()) {
            return;
        }

        // Set lastSpawnTime to current time to start countdown immediately
        long currentTime = System.currentTimeMillis();
        spawner.setLastSpawnTime(currentTime);

        // Immediately update any open GUIs to show the countdown
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        }
    }

    public void deactivateSpawner(SpawnerData spawner) {
        // Clear any pre-generated loot when deactivating
        spawner.clearPreGeneratedLoot();
    }

    public void cleanup() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

