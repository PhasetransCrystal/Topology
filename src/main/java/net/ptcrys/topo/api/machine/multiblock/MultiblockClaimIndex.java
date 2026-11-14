package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.BlueprintLayout;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;

import net.minecraft.core.BlockPos;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一个世界内的占地监听与独占部件索引，不读取或持有世界对象。
 * 普通占地按蓝图布局和保守边界保存；仅独占部件逐位置登记所有者，不展开普通结构的全部方块。
 * 所有操作只能在所属世界线程执行。预留提交先校验全部冲突，失败时保留旧预留，成功时整体替换。
 */
public final class MultiblockClaimIndex {

    private final Map<BlockPos, Watch> watchesByController = new LinkedHashMap<>();
    private final Map<BlockPos, Claim> claimsByController = new LinkedHashMap<>();
    private final Map<BlockPos, BlockPos> exclusiveOwners = new LinkedHashMap<>();

    MultiblockClaimIndex() {}

    /** 登记主体在各朝向下的保守监听区域，覆盖所有重复次数的可能位置，不逐格展开占地。 */
    public void watch(BlockPos controllerPos, Blueprint blueprint, Orientation... orientations) {
        watch(controllerPos, blueprint, List.of(), orientations);
    }

    /** 同时监听主体与所有模块，缺失模块的搭建变更也会唤醒控制器。 */
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

    /** 提交可共享的成型占地，不声明任何独占部件。返回提交结果及应唤醒的释放范围。 */
    public ClaimUpdate tryClaim(
                                BlockPos controllerPos,
                                Blueprint blueprint,
                                Orientation orientation,
                                RepeatResolution repeats) {
        return tryClaim(controllerPos, blueprint, orientation, repeats, Set.of());
    }

    /** 校验并整体替换成型占地及独占预留。冲突返回 accepted=false，旧预留保持不变；坐标按不可变值保存。released 非空时调用者必须唤醒相交监听者。 */
    public ClaimUpdate tryClaim(
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
        Set<BlockPos> immutableExclusive = new LinkedHashSet<>();
        for (BlockPos cell : exclusiveCells) immutableExclusive.add(cell.immutable());
        // 冲突检查和坐标校验都在修改索引之前完成，失败不会释放或覆盖原有预留。
        if (!conflictingExclusive(controllerKey, immutableExclusive).isEmpty()) return ClaimUpdate.REJECTED;
        BlueprintLayout layout = blueprint.layout(repeats);
        Region region = Region.of(compiled, controllerKey);
        for (BlockPos cell : immutableExclusive) region = region.include(cell);
        Claim claim = new Claim(compiled, layout, region, Set.copyOf(immutableExclusive));
        Claim previous = claimsByController.put(controllerKey, claim);
        if (previous != null) releaseExclusive(controllerKey, previous);
        for (BlockPos cell : immutableExclusive) exclusiveOwners.put(cell, controllerKey);
        boolean released = previous != null && !immutableExclusive.containsAll(previous.exclusiveCells());
        return new ClaimUpdate(true, released ? previous.region() : null);
    }

    /** 查询被其他控制器占用的独占部件；重新提交自己的预留不会与自身冲突。 */
    public Set<BlockPos> conflictingExclusive(BlockPos controllerPos, Set<BlockPos> exclusiveCells) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(exclusiveCells, "exclusive cells");
        Set<BlockPos> conflicts = null;
        for (BlockPos cell : exclusiveCells) {
            BlockPos owner = exclusiveOwners.get(Objects.requireNonNull(cell, "exclusive position"));
            if (owner != null && !owner.equals(controllerPos)) {
                if (conflicts == null) conflicts = new LinkedHashSet<>();
                conflicts.add(cell.immutable());
            }
        }
        return conflicts == null ? Set.of() : Set.copyOf(conflicts);
    }

    private void releaseExclusive(BlockPos controller, Claim claim) {
        for (BlockPos cell : claim.exclusiveCells()) exclusiveOwners.remove(cell, controller);
    }

    /** 释放控制器的占地及所有独占预留，返回包含模块独占部件的唤醒范围；没有旧预留时返回 null。 */
    public @Nullable Region unclaim(BlockPos controllerPos) {
        BlockPos controller = Objects.requireNonNull(controllerPos, "controller position").immutable();
        Claim removed = claimsByController.remove(controller);
        if (removed == null) return null;
        releaseExclusive(controller, removed);
        return removed.region();
    }

    /** 查询监听范围与释放区域相交的其他控制器，排除 except。 */
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

    /** 将变更路由给监听、占地或独占部件所有者。普通占地通过缓存布局反查，独占位置直接查询所有者表。 */
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
            if (claim.region().contains(cell) && claim.compiled().cellIndexOfWorld(cell, entry.getKey(), claim.layout()) >= 0) {
                if (controllers == null) {
                    controllers = new LinkedHashSet<>();
                }
                controllers.add(entry.getKey());
            }
        }
        BlockPos owner = exclusiveOwners.get(cell);
        if (owner != null) {
            if (controllers == null) controllers = new LinkedHashSet<>();
            controllers.add(owner);
        }
        // Zero-allocation fast path for quiet cells: the event ingress probes every change here.
        return controllers == null ? Set.of() : controllers;
    }

    /**
     * 预留提交结果。失败时原预留完全保留；released 非空表示本次替换释放了旧的独占位置，
     * 调用者须唤醒与该范围相交的监听者，即使主结构仍保持成型。
     */
    public record ClaimUpdate(boolean accepted, @Nullable Region released) {

        private static final ClaimUpdate REJECTED = new ClaimUpdate(false, null);
    }

    /** 世界轴对齐边界，用于监听筛选和释放唤醒。 */
    public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        static Region of(CompiledBlueprint compiled, BlockPos controller) {
            int[] bounds = compiled.worldBounds(controller);
            return new Region(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
        }

        /** 将主占地外的模块独占部件也纳入释放后的唤醒范围。 */
        Region include(BlockPos pos) {
            return new Region(Math.min(minX, pos.getX()), Math.min(minY, pos.getY()), Math.min(minZ, pos.getZ()),
                    Math.max(maxX, pos.getX()), Math.max(maxY, pos.getY()), Math.max(maxZ, pos.getZ()));
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
                         BlueprintLayout layout,
                         Region region,
                         Set<BlockPos> exclusiveCells) {}
}
