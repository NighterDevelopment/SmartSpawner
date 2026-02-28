package github.nighter.smartspawner.utils;

public class ChunkUtil {
    public static long getChunkKey(int x, int z) {
        return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
    }

    public static int getChunkX(long key) {
        return (int) (key & 0xffffffffL);
    }

    public static int getChunkZ(long key) {
        return (int) (key >>> 32);
    }
}
