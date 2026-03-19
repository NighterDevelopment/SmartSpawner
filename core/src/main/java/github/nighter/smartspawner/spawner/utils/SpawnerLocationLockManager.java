package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import org.bukkit.Location;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Centralized manager for location-based locks to prevent race conditions
 * when multiple operations (GUI destack, pickaxe break, etc.) affect the same spawner.
 *
 * This prevents duplication exploits where players could:
 * - Click GUI to remove spawners while breaking with pickaxe
 * - Have multiple players break the same spawner in the same tick
 * - Trigger simultaneous operations that modify stack size
 *
 * Thread-safe and memory-efficient with automatic cleanup.
 */
public class SpawnerLocationLockManager {

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;

    // Location-based locks - uses striping for better concurrency
    private final ConcurrentHashMap<Location, ReentrantLock> locationLocks =
            new ConcurrentHashMap<>(128, 0.75f, 4);

    public SpawnerLocationLockManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        startCleanupTask();
    }

    /**
     * Acquires a lock for the given location.
     * Creates a new lock if one doesn't exist.
     *
     * @param location The spawner location
     * @return The lock for this location
     */
    public ReentrantLock getLock(Location location) {
        return locationLocks.computeIfAbsent(location, k -> new ReentrantLock());
    }

    /**
     * Tries to acquire the lock for a location without blocking.
     *
     * @param location The spawner location
     * @return true if lock was acquired, false if already locked
     */
    public boolean tryLock(Location location) {
        ReentrantLock lock = getLock(location);
        return lock.tryLock();
    }

    /**
     * Unlocks the lock for a location.
     *
     * @param location The spawner location
     */
    public void unlock(Location location) {
        ReentrantLock lock = locationLocks.get(location);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Removes the lock for a location (called when spawner is deleted).
     *
     * @param location The spawner location
     */
    public void removeLock(Location location) {
        locationLocks.remove(location);
    }

    /**
     * Checks if a location is currently locked.
     *
     * @param location The spawner location
     * @return true if locked by any thread
     */
    public boolean isLocked(Location location) {
        ReentrantLock lock = locationLocks.get(location);
        return lock != null && lock.isLocked();
    }

    /**
     * Gets the number of active locks (for monitoring/debugging).
     *
     * @return Number of location locks in memory
     */
    public int getActiveLockCount() {
        return locationLocks.size();
    }

    /**
     * Periodically cleans up unused location locks to prevent memory leaks.
     * Runs every 5 minutes and removes locks for non-existent spawners.
     */
    private void startCleanupTask() {
        Scheduler.runTaskTimerAsync(() -> {
            Iterator<Map.Entry<Location, ReentrantLock>> iterator = locationLocks.entrySet().iterator();
            int removed = 0;

            while (iterator.hasNext()) {
                Map.Entry<Location, ReentrantLock> entry = iterator.next();
                ReentrantLock lock = entry.getValue();
                Location location = entry.getKey();

                // Only remove if lock is not currently held
                if (!lock.isLocked() && lock.tryLock()) {
                    try {
                        // Double-check spawner doesn't exist and block is not a spawner
                        if (spawnerManager.getSpawnerByLocation(location) == null) {
                            iterator.remove();
                            removed++;
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }

            if (removed > 0) {
                plugin.debug("SpawnerLocationLockManager: Cleaned up " + removed + " unused locks. Active locks: " + locationLocks.size());
            }
        }, 6000L, 6000L); // Run every 5 minutes (6000 ticks)
    }

    /**
     * Shuts down the lock manager and clears all locks.
     * Should be called on plugin disable.
     */
    public void shutdown() {
        locationLocks.clear();
    }
}

