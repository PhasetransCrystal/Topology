package net.ptcrys.topo.apiv2.ore.engine;

/**
 * Deterministic grid/cell math for vein placement. Pure functions of the world seed and stable ids,
 * so a given world reproduces the same veins every time.
 */
public final class OreGridMath {

    private OreGridMath() {}

    public static int cellMinChunk(int chunkCoordinate, int gridSizeChunks) {
        if (gridSizeChunks <= 0) {
            throw new IllegalArgumentException("gridSizeChunks must be positive");
        }
        return Math.floorDiv(chunkCoordinate, gridSizeChunks) * gridSizeChunks;
    }

    public static long seed(long worldSeed, String dimensionId, String id, int x, int z) {
        long value = worldSeed;
        value ^= ((long) dimensionId.hashCode()) * 0x9E3779B97F4A7C15L;
        value ^= ((long) id.hashCode()) * 0xBF58476D1CE4E5B9L;
        value ^= ((long) x) * 0x94D049BB133111EBL;
        value ^= ((long) z) * 0xD6E8FEB86659FD93L;
        return mix(value);
    }

    public static int randomInt(long seed, int salt, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        if (min == max) {
            return min;
        }
        long range = (long) max - (long) min + 1L;
        long value = Long.remainderUnsigned(mix(seed + salt * 0x9E3779B97F4A7C15L), range);
        return (int) (min + value);
    }

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
