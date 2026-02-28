package github.nighter.smartspawner.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BlockPos(int x, int y, int z, UUID worldId) {

    public BlockPos(Location location) {
        this(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getUID());
    }

    public World getWorld() {
        return Bukkit.getWorld(worldId);
    }

    public Location toLocation() {
        World world = getWorld();
        if (world == null) return null;

        return new Location(world, x, y, z);
    }

    public int getChunkX() {
        return x >> 4;
    }

    public int getChunkZ() {
        return z >> 4;
    }

    public long getChunkKey() {
        return ChunkUtil.getChunkKey(getChunkX(), getChunkZ());
    }

    public BlockPos above() {
        return new BlockPos(x, y + 1, z, worldId);
    }

    public BlockPos below() {
        return new BlockPos(x, y - 1, z, worldId);
    }
}
