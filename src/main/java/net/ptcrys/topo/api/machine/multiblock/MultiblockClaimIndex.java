package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
     * Registers the watched region: one box + membership test per blueprint and orientation.
     * Modules are independent blueprints anchored at the same controller and are watched alongside
     * the main blueprint, so changes to module cells reach this controller without waiting for the
     * backstop. Watching is O(blueprints × orientations), regardless of footprint size.
     */
    public void watch(BlockPos controllerPos, Blueprint blueprint, Orientation... orientations) {
        watch(controllerPos, blueprint, List.of(), orientations);
    }

    /** Registers the main blueprint plus every module blueprint as watched regions. */
    public void watch(
                      BlockPos controllerPos,
                      Blueprint blueprint,
                      List<Blueprint> modules,
                      Orientation... orientations) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(modules, "module blueprints");
        Objects.requireNonNull(orientations, "orientations");
        if (orientations.length == 0) {
            throw new IllegalArgumentException("At least one orientation is required");
        }
        BlockPos controllerKey = controllerPos.immutable();
        List<Part> parts = new ArrayList<>();
        for (Orientation orientation : orientations) {
            Objects.requireNonNull(orientation, "orientation");
            CompiledBlueprint main = blueprint.compiled(orientation);
            parts.add(new Part(main, Region.of(main, controllerKey)));
            for (Blueprint module : modules) {
                Objects.requireNonNull(module, "module blueprint");
                CompiledBlueprint compiledModule = module.compiled(orientation);
                parts.add(new Part(compiledModule, Region.of(compiledModule, controllerKey)));
            }
        }
        watchesByController.put(controllerKey, new Watch(List.copyOf(parts)));
    }

    public void unwatch(BlockPos controllerPos) {
        watchesByController.remove(Objects.requireNonNull(controllerPos, "controller position").immutable());
    }

    /**
     * Registers the recognized footprint for change routing. Claims are <b>not</b> exclusive by
     * default: several machines may reuse the same structure or shared part. The controller's
     * exclusive-part reservations are tracked separately; this call only records the region and the
     * arithmetic membership test used to route a changed cell back to this controller.
     */
    public boolean tryClaim(
                            BlockPos controllerPos,
                            Blueprint blueprint,
                            Orientation orientation,
                            RepeatResolution repeats) {
        return tryClaim(controllerPos, blueprint, orientation, repeats, Set.of());
    }

    /**
     * Registers the recognized footprint and the exclusive part positions it holds. Claims remain
     * shareable except for {@code exclusiveCells}: a cell already reserved by another formed machine
     * is a conflict. Callers pre-check with {@link #conflictingExclusive} to decide whether the main
     * structure or only a module must be dropped.
     */
    public boolean tryClaim(
                            BlockPos controllerPos,
                            Blueprint blueprint,
                            Orientation orientation,
                            RepeatResolution repeats,
                            Set<BlockPos> exclusiveCells) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(repeats, "repeats");
        Objects.requireNonNull(exclusiveCells, "exclusive cells");
        BlockPos controllerKey = controllerPos.immutable();
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        Claim claim = new Claim(
                compiled,
                controllerKey,
                repeats,
                Region.of(compiled, controllerKey),
                Set.copyOf(exclusiveCells));
        claimsByController.put(controllerKey, claim);
        return true;
    }

    /**
     * Exclusive cells already reserved by another formed machine. The query excludes
     * {@code controllerPos}, so a controller re-checking its own claim never conflicts with itself.
     */
    public Set<BlockPos> conflictingExclusive(BlockPos controllerPos, Set<BlockPos> exclusiveCells) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(exclusiveCells, "exclusive cells");
        if (exclusiveCells.isEmpty()) {
            return Set.of();
        }
        BlockPos controllerKey = controllerPos.immutable();
        Set<BlockPos> conflicts = null;
        for (Map.Entry<BlockPos, Claim> entry : claimsByController.entrySet()) {
            if (entry.getKey().equals(controllerKey)) {
                continue;
            }
            Set<BlockPos> reserved = entry.getValue().exclusiveCells();
            if (reserved.isEmpty()) {
                continue;
            }
            for (BlockPos cell : exclusiveCells) {
                if (reserved.contains(cell)) {
                    if (conflicts == null) {
                        conflicts = new LinkedHashSet<>();
                    }
                    conflicts.add(cell);
                }
            }
        }
        return conflicts == null ? Set.of() : Set.copyOf(conflicts);
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
            for (Part part : entry.getValue().parts()) {
                if (part.region().intersects(region)) {
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
            for (Part part : entry.getValue().parts()) {
                if (part.region().contains(cell) && part.compiled().containsWorldAnyLayout(cell, entry.getKey())) {
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

    private record Part(CompiledBlueprint compiled, Region region) {}

    private record Watch(List<Part> parts) {}

    private record Claim(
                         CompiledBlueprint compiled,
                         BlockPos controllerPos,
                         RepeatResolution repeats,
                         Region region,
                         Set<BlockPos> exclusiveCells) {}
}
