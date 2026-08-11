package net.ptcrys.topo.apiv2.ore.engine;

import net.ptcrys.topo.apiv2.ore.OreEnvironment;
import net.ptcrys.topo.apiv2.ore.OreHostRule;
import net.ptcrys.topo.apiv2.ore.OrePlacement;
import net.ptcrys.topo.apiv2.ore.OreVein;
import net.ptcrys.topo.apiv2.ore.OreVeinCollector;
import net.ptcrys.topo.apiv2.ore.OreVeinEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Plans deterministic grid ore veins for a chunk. Pure function of (world seed, dimension, chunk,
 * existing blocks). Only veins whose mode self-collects as grid participate.
 */
public final class OreVeinPlanner {

    private static final long MAX_BLOCK_SCANS_PER_CHUNK = 262_144L;
    private static final long EXPOSURE_SALT = 0x6A09E667F3BCC909L;
    private static final String VEIN_SELECT_SALT = "topo:vein_select";

    private final Map<ResourceKey<Level>, List<GridVein>> gridByDimension;
    private final Map<OreVein, WeightedEntries> entryTables;
    private final OreBlockResolver resolver;

    public OreVeinPlanner(List<OreVein> veins, OreBlockResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        List<OreVein> collected = new ArrayList<>();
        OreVeinCollector collector = collected::add;
        for (OreVein vein : veins) {
            vein.mode().strategy().collectGrid(vein, collector);
        }
        Map<ResourceKey<Level>, List<GridVein>> byDimension = new LinkedHashMap<>();
        Map<OreVein, WeightedEntries> tables = new IdentityHashMap<>();
        for (OreVein vein : collected) {
            if (!(vein.placement() instanceof OrePlacement.Grid grid)) {
                throw new IllegalStateException(
                        "mode collected non-grid vein into planner: " + vein.id());
            }
            GridVein gridVein = new GridVein(vein, grid);
            for (var dimensionRule : vein.environment().dimensions()) {
                byDimension.computeIfAbsent(dimensionRule.dimension(), ignored -> new ArrayList<>()).add(gridVein);
            }
            tables.put(vein, WeightedEntries.compile(vein));
        }
        this.gridByDimension = byDimension;
        this.entryTables = tables;
    }

    public List<OreVeinPlacement> veinsForChunk(long seed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        List<GridVein> veins = gridByDimension.getOrDefault(dimension, List.of());
        Map<OriginCell, List<GridVein>> byCell = new LinkedHashMap<>();
        for (GridVein gridVein : veins) {
            OrePlacement.Grid grid = gridVein.grid();
            int gridSize = grid.gridSizeChunks();
            int searchChunks = Math.ceilDiv(grid.radiusBlocks() + grid.randomOffsetBlocks() + 15, 16);
            int firstX = firstOrigin(chunkPos.x() - searchChunks, gridSize);
            int lastX = lastOrigin(chunkPos.x() + searchChunks, gridSize);
            int firstZ = firstOrigin(chunkPos.z() - searchChunks, gridSize);
            int lastZ = lastOrigin(chunkPos.z() + searchChunks, gridSize);
            for (int cx = firstX; cx <= lastX; cx += gridSize) {
                for (int cz = firstZ; cz <= lastZ; cz += gridSize) {
                    byCell.computeIfAbsent(new OriginCell(cx, cz), ignored -> new ArrayList<>()).add(gridVein);
                }
            }
        }
        List<OreVeinPlacement> result = new ArrayList<>();
        for (Map.Entry<OriginCell, List<GridVein>> entry : byCell.entrySet()) {
            GridVein selected = selectVein(seed, dimension, entry.getKey(), entry.getValue());
            if (selected == null) {
                continue;
            }
            OreVeinPlacement placement = placementForCell(seed, dimension, entry.getKey(), selected);
            if (intersectsChunk(placement, chunkPos)) {
                result.add(placement);
            }
        }
        result.sort(Comparator
                .comparingInt((OreVeinPlacement placement) -> -placement.grid().priority())
                .thenComparing(placement -> placement.vein().id().toString())
                .thenComparingInt(placement -> placement.center().getX())
                .thenComparingInt(placement -> placement.center().getY())
                .thenComparingInt(placement -> placement.center().getZ()));
        return result;
    }

    public List<PlannedOreBlock> planChunk(
                                           long seed, ResourceKey<Level> dimension, ChunkPos chunkPos, ExistingBlockProvider existing) {
        Objects.requireNonNull(existing, "existing");
        Map<BlockPos, PlannedOreBlock> result = new LinkedHashMap<>();
        Map<BlockPos, Integer> priorities = new HashMap<>();
        long scans = 0L;
        for (OreVeinPlacement placement : veinsForChunk(seed, dimension, chunkPos)) {
            OreVein vein = placement.vein();
            OrePlacement.Grid grid = placement.grid();
            OreEnvironment environment = vein.environment();
            BlockPos center = placement.center();
            int radius = placement.radiusBlocks();
            int minX = Math.max(chunkPos.getMinBlockX(), center.getX() - radius);
            int maxX = Math.min(chunkPos.getMaxBlockX(), center.getX() + radius);
            int minY = Math.max(center.getY() - radius, grid.minY());
            int maxY = Math.min(center.getY() + radius, grid.maxY());
            int minZ = Math.max(chunkPos.getMinBlockZ(), center.getZ() - radius);
            int maxZ = Math.min(chunkPos.getMaxBlockZ(), center.getZ() + radius);
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                continue;
            }
            scans += (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (scans > MAX_BLOCK_SCANS_PER_CHUNK) {
                throw new IllegalStateException("ore planning exceeded the per-chunk scan budget at " + chunkPos);
            }
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        Optional<PlannedOreBlock> planned = planBlock(placement, pos, existing);
                        if (planned.isEmpty()) {
                            continue;
                        }
                        Integer previous = priorities.get(pos);
                        if (previous == null || grid.conflictPolicy().canReplace(grid.priority(), previous)) {
                            result.put(pos, planned.get());
                            priorities.put(pos, grid.priority());
                        }
                    }
                }
            }
        }
        return List.copyOf(result.values());
    }

    private Optional<PlannedOreBlock> planBlock(
                                                OreVeinPlacement placement, BlockPos pos, ExistingBlockProvider existing) {
        OreVein vein = placement.vein();
        OrePlacement.Grid grid = placement.grid();
        if (pos.getY() < grid.minY() || pos.getY() > grid.maxY()) {
            return Optional.empty();
        }
        BlockPos center = placement.center();
        if (!grid.shape().contains(
                pos.getX() - center.getX(),
                pos.getY() - center.getY(),
                pos.getZ() - center.getZ(),
                placement.radiusBlocks())) {
            return Optional.empty();
        }
        if (!passesDensity(grid, placement, pos)) {
            return Optional.empty();
        }
        BlockState existingState = existing.blockState(pos);
        Optional<OreHostRule> host = vein.environment().hostFor(existingState);
        if (host.isEmpty()) {
            return Optional.empty();
        }
        OreVeinEntry entry = entryTables.get(vein).select(entrySeed(placement, vein, pos));
        Optional<Block> block = resolver.block(entry.material(), host.get().generatedForm());
        if (block.isEmpty()) {
            return Optional.empty();
        }
        if (isExposedToAir(pos, existing) && shouldDiscardExposed(vein, placement, pos)) {
            return Optional.empty();
        }
        return Optional.of(new PlannedOreBlock(
                pos, vein.id(), entry.material(), host.get().generatedForm(), block.get().defaultBlockState()));
    }

    private boolean passesDensity(OrePlacement.Grid grid, OreVeinPlacement placement, BlockPos pos) {
        if (grid.density() >= 1.0) {
            return true;
        }
        if (grid.density() <= 0.0) {
            return false;
        }
        long mixed = blockSeed(placement, placement.vein(), pos.getX(), pos.getZ());
        double value = (double) Long.remainderUnsigned(OreGridMath.mix(mixed ^ pos.getY()), 1_000_000L) / 1_000_000.0;
        return value < grid.density();
    }

    private boolean shouldDiscardExposed(OreVein vein, OreVeinPlacement placement, BlockPos pos) {
        long mixed = blockSeed(placement, vein, pos.getX(), pos.getZ());
        double value = (double) Long.remainderUnsigned(
                OreGridMath.mix(mixed ^ pos.getY() ^ EXPOSURE_SALT), 1_000_000L) / 1_000_000.0;
        return vein.airExposurePolicy().discardExposed(vein.airExposureDiscardChance(), value);
    }

    private GridVein selectVein(
                                long seed, ResourceKey<Level> dimension, OriginCell cell, List<GridVein> candidates) {
        int totalWeight = 0;
        for (GridVein candidate : candidates) {
            totalWeight += candidate.grid().weight();
        }
        if (totalWeight <= 0) {
            return null;
        }
        long mixed = OreGridMath.seed(seed, dimension.identifier().toString(), VEIN_SELECT_SALT, cell.x(), cell.z());
        int selected = OreGridMath.randomInt(mixed, 5, 0, totalWeight - 1);
        int cursor = 0;
        for (GridVein candidate : candidates) {
            cursor += candidate.grid().weight();
            if (selected < cursor) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private OreVeinPlacement placementForCell(
                                              long seed, ResourceKey<Level> dimension, OriginCell cell, GridVein gridVein) {
        OreVein vein = gridVein.vein();
        OrePlacement.Grid grid = gridVein.grid();
        long mixed = OreGridMath.seed(seed, dimension.identifier().toString(), vein.id().toString(), cell.x(), cell.z());
        int baseX = cell.x() * 16 + grid.gridSizeChunks() * 8;
        int baseZ = cell.z() * 16 + grid.gridSizeChunks() * 8;
        int offsetX = OreGridMath.randomInt(mixed, 11, -grid.randomOffsetBlocks(), grid.randomOffsetBlocks());
        int offsetZ = OreGridMath.randomInt(mixed, 17, -grid.randomOffsetBlocks(), grid.randomOffsetBlocks());
        int y = OreGridMath.randomInt(mixed, 23, grid.minY(), grid.maxY());
        return new OreVeinPlacement(
                vein, grid, dimension, new BlockPos(baseX + offsetX, y, baseZ + offsetZ), grid.radiusBlocks());
    }

    private static long blockSeed(OreVeinPlacement placement, OreVein vein, int x, int z) {
        return OreGridMath.seed(
                placement.center().asLong(), placement.dimension().identifier().toString(), vein.id().toString(), x, z);
    }

    private static long entrySeed(OreVeinPlacement placement, OreVein vein, BlockPos pos) {
        return OreGridMath.mix(
                blockSeed(placement, vein, pos.getX(), pos.getZ()) ^ ((long) pos.getY() * 0x94D049BB133111EBL));
    }

    private static boolean intersectsChunk(OreVeinPlacement placement, ChunkPos chunkPos) {
        BlockPos center = placement.center();
        int radius = placement.radiusBlocks();
        return center.getX() + radius >= chunkPos.getMinBlockX() && center.getX() - radius <= chunkPos.getMaxBlockX() && center.getZ() + radius >= chunkPos.getMinBlockZ() && center.getZ() - radius <= chunkPos.getMaxBlockZ();
    }

    private static boolean isExposedToAir(BlockPos pos, ExistingBlockProvider existing) {
        return existing.blockState(pos.above()).isAir() || existing.blockState(pos.below()).isAir() || existing.blockState(pos.north()).isAir() || existing.blockState(pos.south()).isAir() || existing.blockState(pos.east()).isAir() || existing.blockState(pos.west()).isAir();
    }

    private static int firstOrigin(int chunk, int grid) {
        return Math.floorDiv(chunk + grid - 1, grid) * grid;
    }

    private static int lastOrigin(int chunk, int grid) {
        return Math.floorDiv(chunk, grid) * grid;
    }

    private record OriginCell(int x, int z) {}

    private record GridVein(OreVein vein, OrePlacement.Grid grid) {}

    private record WeightedEntries(OreVeinEntry[] entries, int[] cumulative, int total) {

        static WeightedEntries compile(OreVein vein) {
            OreVeinEntry[] entries = vein.entries().toArray(OreVeinEntry[]::new);
            int[] cumulative = new int[entries.length];
            int total = 0;
            for (int i = 0; i < entries.length; i++) {
                total += entries[i].weight();
                cumulative[i] = total;
            }
            if (total <= 0) {
                throw new IllegalArgumentException("ore vein has no positive entry weight: " + vein.id());
            }
            return new WeightedEntries(entries, cumulative, total);
        }

        OreVeinEntry select(long mixed) {
            int selected = OreGridMath.randomInt(mixed, 31, 0, total - 1);
            int index = Arrays.binarySearch(cumulative, selected + 1);
            if (index < 0) {
                index = -index - 1;
            }
            return entries[index];
        }
    }
}
