package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.utils.BlockPos;
import github.nighter.smartspawner.utils.ChunkUtil;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class HopperRegistry {

    private final Map<UUID, Map<Long, Set<BlockPos>>> data = new ConcurrentHashMap<>();

    public void add(BlockPos pos) {
        UUID worldId = pos.worldId();
        long chunkKey = ChunkUtil.getChunkKey(pos.getChunkX(), pos.getChunkZ());

        data.computeIfAbsent(worldId, w -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, c -> ConcurrentHashMap.newKeySet())
                .add(pos);
    }

    public void remove(BlockPos pos) {
        UUID worldId = pos.worldId();
        Map<Long, Set<BlockPos>> worldMap = data.get(worldId);
        if (worldMap == null) return;

        long chunkKey = ChunkUtil.getChunkKey(pos.getChunkX(), pos.getChunkZ());
        Set<BlockPos> set = worldMap.get(chunkKey);
        if (set == null) return;

        set.remove(pos);

        if (set.isEmpty()) {
            worldMap.remove(chunkKey);
        }

        if (worldMap.isEmpty()) {
            data.remove(worldId);
        }
    }

    public void removeChunk(UUID worldId, int chunkX, int chunkZ) {
        Map<Long, Set<BlockPos>> worldMap = data.get(worldId);
        if (worldMap == null) return;

        long key = ChunkUtil.getChunkKey(chunkX, chunkZ);
        worldMap.remove(key);

        if (worldMap.isEmpty()) {
            data.remove(worldId);
        }
    }

    public void removeWorld(World world) {
        removeWorld(world.getUID());
    }

    public void removeWorld(UUID worldId) {
        data.remove(worldId);
    }

    /**
     * Iterates all active chunks.
     * Does NOT touch blocks (safe for global thread).
     */
    public void forEachChunk(BiConsumer<UUID, Long> consumer) {
        for (var worldEntry : data.entrySet()) {
            UUID worldId = worldEntry.getKey();

            for (Long chunkKey : worldEntry.getValue().keySet()) {
                consumer.accept(worldId, chunkKey);
            }
        }
    }

    /**
     * Must be called inside region thread.
     */
    public Set<BlockPos> getChunkHoppers(UUID worldId, long chunkKey) {
        Map<Long, Set<BlockPos>> worldMap = data.get(worldId);
        if (worldMap == null) return Collections.emptySet();

        return worldMap.getOrDefault(chunkKey, Collections.emptySet());
    }
}
