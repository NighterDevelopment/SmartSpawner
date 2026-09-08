package github.nighter.smartspawner.spawner.gui.storage.session;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active storage sessions for spawners.
 * Coordinates session lifecycle: creates sessions on open, tracks viewers, and triggers compacting on 0 viewers.
 */
public class StorageSessionManager {
    private final SmartSpawner plugin;
    private final Map<String, StorageSession> sessions = new ConcurrentHashMap<>();

    public StorageSessionManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public StorageSession getOrCreateSession(SpawnerData spawner) {
        if (spawner == null) return null;
        return sessions.computeIfAbsent(spawner.getSpawnerId(), id -> new StorageSession(plugin, spawner));
    }

    public StorageSession getSession(String spawnerId) {
        if (spawnerId == null) return null;
        return sessions.get(spawnerId);
    }

    public boolean hasSession(String spawnerId) {
        return spawnerId != null && sessions.containsKey(spawnerId);
    }

    public void removeSession(String spawnerId) {
        if (spawnerId != null) {
            sessions.remove(spawnerId);
        }
    }

    /**
     * Ends all active sessions and clears cache.
     * Called during plugin disable or server shutdown.
     */
    public void cleanup() {
        for (StorageSession session : sessions.values()) {
            try {
                session.endSession();
            } catch (Exception e) {
                plugin.getLogger().warning("Error ending storage session for spawner "
                        + session.getSpawner().getSpawnerId() + ": " + e.getMessage());
            }
        }
        sessions.clear();
    }
}
