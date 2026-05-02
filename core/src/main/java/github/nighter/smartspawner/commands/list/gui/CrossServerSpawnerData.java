package github.nighter.smartspawner.commands.list.gui;

import org.bukkit.entity.EntityType;

/**
 * Lightweight data class for spawner information from remote servers.
 * Used when viewing spawners across servers in the list GUI.
 * Does not require actual Bukkit Location/World objects since
 * the spawner exists on a different server.
 */
public class CrossServerSpawnerData {
    private final String spawnerId;
    private final String serverName;
    private final String worldName;
    private final int locX;
    private final int locY;
    private final int locZ;
    private final EntityType entityType;
    private final int stackSize;
    private final boolean active;
    private final String lastInteractedPlayer;
    private final long storedExp;
    private final long totalItems;

    public CrossServerSpawnerData(String spawnerId, String serverName, String worldName,
                                   int locX, int locY, int locZ, EntityType entityType,
                                   int stackSize, boolean active, String lastInteractedPlayer,
                                   long storedExp, long totalItems) {
        this.spawnerId = spawnerId;
        this.serverName = serverName;
        this.worldName = worldName;
        this.locX = locX;
        this.locY = locY;
        this.locZ = locZ;
        this.entityType = entityType;
        this.stackSize = stackSize;
        this.active = active;
        this.lastInteractedPlayer = lastInteractedPlayer;
        this.storedExp = storedExp;
        this.totalItems = totalItems;
    }

    public String getSpawnerId() {
        return spawnerId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getLocX() {
        return locX;
    }

    public int getLocY() {
        return locY;
    }

    public int getLocZ() {
        return locZ;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public int getStackSize() {
        return stackSize;
    }

    public boolean isActive() {
        return active;
    }

    public String getLastInteractedPlayer() {
        return lastInteractedPlayer;
    }

    public long getStoredExp() {
        return storedExp;
    }

    public long getTotalItems() {
        return totalItems;
    }

    /**
     * Check if this spawner is on the current server.
     * @param currentServerName The name of the current server
     * @return true if this spawner is on the current server
     */
    public boolean isLocalServer(String currentServerName) {
        return serverName.equals(currentServerName);
    }
}
