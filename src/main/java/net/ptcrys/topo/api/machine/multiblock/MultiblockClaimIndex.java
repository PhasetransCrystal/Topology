package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-level runtime ownership index and snapshot producer — <b>region form</b>, million-cell
 * capable: nothing is stored per cell.
 *
 * <p>
 * A watch or claim is a bounding box plus the blueprint's arithmetic membership test
 * ({@link CompiledBlueprint#cellIndexOfWorld}); routing a changed cell to its controllers is a scan
 * over the level's live controllers (a handful) with one box check and one integer-math membership
 * test each, instead of a per-cell hash map that would hold 10⁶ entries per megastructure.
 *
 * <p>
 * Claim lifetime contract: claims and watches are memory-only runtime state — never persisted.
 * A controller releases them when its block entity unloads (unclaim + unwatch) and re-acquires them
 * through the recheck its on-load dirty mark triggers. Overlap windows between a stale claim and a
 * new claimant converge through the unclaim wake-up: {@link #unclaim} returns the freed region so
 * the caller can dirty the watchers it intersects ({@link #watchersIntersecting}).
 */
public final class MultiblockClaimIndex {

    private final ServerLevel level;
    private final Map<BlockPos, Watch> watchesByController = new LinkedHashMap<>();
    private final Map<BlockPos, Claim> claimsByController = new LinkedHashMap<>();

    MultiblockClaimIndex(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    /** Chunk-ordered dense capture of the maximal footprint; see {@link StructureCaptures}. */
    public CompiledSnapshot snapshot(BlockPos controllerPos, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(controllerPos, "controller position");
        return StructureCaptures.capture(level, controllerPos, blueprint, orientation);
    }

    /**
     * Registers the watched region: one box + membership test per orientation. Watching is
     * O(orientations), regardless of footprint size.
     */
    public void watch(BlockPos controllerPos, Blueprint blueprint, Orientation... orientations) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientations, "orientations");
        if (orientations.length == 0) {
            throw new IllegalArgumentException("At least one orientation is required");
        }
        BlockPos controllerKey = controllerPos.immutable();
        CompiledBlueprint[] compiled = new CompiledBlueprint[orientations.length];
        Region[] regions = new Region[orientations.length];
        for (int i = 0; i < orientations.length; i++) {
            compiled[i] = blueprint.compiled(Objects.requireNonNull(orientations[i], "orientation"));
            regions[i] = Region.of(compiled[i], controllerKey);
        }
        watchesByController.put(controllerKey, new Watch(compiled, regions));
    }

    public void unwatch(BlockPos controllerPos) {
        watchesByController.remove(Objects.requireNonNull(controllerPos, "controller position").immutable());
    }

    /**
     * Claims the recognized footprint (blueprint × orientation × repeats anchored at the
     * controller). Cell ownership is exclusive: conflicts are pre-screened by box intersection and
     * decided by exact arithmetic membership over the smaller of the two footprints.
     */
    public boolean tryClaim(
                            BlockPos controllerPos,
                            Blueprint blueprint,
                            Orientation orientation,
                            RepeatResolution repeats) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(repeats, "repeats");
        BlockPos controllerKey = controllerPos.immutable();
        Claim claim = new Claim(
                blueprint.compiled(orientation), controllerKey, repeats,
                Region.of(blueprint.compiled(orientation), controllerKey));
        for (Map.Entry<BlockPos, Claim> entry : claimsByController.entrySet()) {
            if (entry.getKey().equals(controllerKey)) {
                continue;
            }
            Claim other = entry.getValue();
            if (!claim.region().intersects(other.region())) {
                continue;
            }
            if (claimsOverlap(claim, other)) {
                return false;
            }
        }
        claimsByController.put(controllerKey, claim);
        return true;
    }

    /**
     * Releases the controller's claim and returns its region (null when nothing was claimed).
     * Callers use the region to wake the remaining watchers it intersects.
     */
    public @Nullable Region unclaim(BlockPos controllerPos) {
        Claim removed = claimsByController.remove(Objects.requireNonNull(controllerPos, "controller position").immutable());
        return removed == null ? null : removed.region();
    }

    /** Controllers (other than {@code except}) whose watched region intersects {@code region}. */
    public Set<BlockPos> watchersIntersecting(Region region, @Nullable BlockPos except) {
        Objects.requireNonNull(region, "region");
        Set<BlockPos> watchers = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, Watch> entry : watchesByController.entrySet()) {
            if (entry.getKey().equals(except)) {
                continue;
            }
            for (Region watched : entry.getValue().regions()) {
                if (watched.intersects(region)) {
                    watchers.add(entry.getKey());
                    break;
                }
            }
        }
        return watchers;
    }

    /**
     * Controllers watching or claiming {@code cell}: a scan over the level's live controllers with
     * one box check + one O(1) membership test per orientation — the event-ingress hot path.
     */
    public Set<BlockPos> controllersForCell(BlockPos cell) {
        Objects.requireNonNull(cell, "cell");
        Set<BlockPos> controllers = null;
        for (Map.Entry<BlockPos, Watch> entry : watchesByController.entrySet()) {
            Watch watch = entry.getValue();
            for (int i = 0; i < watch.regions().length; i++) {
                if (watch.regions()[i].contains(cell) && watch.compiled()[i].containsWorldAnyLayout(cell, entry.getKey())) {
                    if (controllers == null) {
                        controllers = new LinkedHashSet<>();
                    }
                    controllers.add(entry.getKey());
                    break;
                }
            }
        }
        for (Map.Entry<BlockPos, Claim> entry : claimsByController.entrySet()) {
            Claim claim = entry.getValue();
            if (claim.region().contains(cell) && claim.compiled().containsWorld(cell, entry.getKey(), claim.repeats())) {
                if (controllers == null) {
                    controllers = new LinkedHashSet<>();
                }
                controllers.add(entry.getKey());
            }
        }
        // Zero-allocation fast path for quiet cells: the event ingress probes every change here.
        return controllers == null ? Set.of() : controllers;
    }

    /** Exact overlap: walk the smaller footprint's cells, test the other's membership arithmetically. */
    private static boolean claimsOverlap(Claim a, Claim b) {
        Claim smaller = a.compiled().cellCapacity() <= b.compiled().cellCapacity() ? a : b;
        Claim larger = smaller == a ? b : a;
        boolean[] overlap = { false };
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        smaller.compiled().forEachWorldPos(smaller.controllerPos(), smaller.repeats(), packed -> {
            if (overlap[0]) {
                return;
            }
            cursor.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
            if (larger.region().contains(cursor) && larger.compiled().containsWorld(cursor, larger.controllerPos(), larger.repeats())) {
                overlap[0] = true;
            }
        });
        return overlap[0];
    }

    /** Axis-aligned world-space box of a compiled footprint anchored at a controller. */
    public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        static Region of(CompiledBlueprint compiled, BlockPos controller) {
            int[] bounds = compiled.worldBounds(controller);
            return new Region(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        public boolean intersects(Region other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }

    private record Watch(CompiledBlueprint[] compiled, Region[] regions) {}

    private record Claim(
                         CompiledBlueprint compiled,
                         BlockPos controllerPos,
                         RepeatResolution repeats,
                         Region region) {}
}
