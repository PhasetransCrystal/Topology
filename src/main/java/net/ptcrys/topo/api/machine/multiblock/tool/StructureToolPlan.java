package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.ToIntFunction;

/** 只读放置规划。先统计已满足的位置，再选择候选并检查投影后的完整结构；不读取物品组件、不改世界、不扣物品。 */
public record StructureToolPlan(List<Target> targets, Map<Block, Integer> materials, List<Problem> problems, DataSnapshot metadata) {

    public static final int MAX_CELLS = 16384;

    public StructureToolPlan {
        targets = List.copyOf(targets);
        materials = Map.copyOf(materials);
        problems = List.copyOf(problems);
    }

    public boolean buildable() {
        return problems.isEmpty();
    }

    public enum Status {
        MATCHED,
        MISSING,
        BLOCKED,
        UNKNOWN,
        UNRESOLVED
    }

    public record Target(BlockPos pos, BlockState state, Status status) {

        public Target {
            pos = pos.immutable();
            Objects.requireNonNull(state);
            Objects.requireNonNull(status);
        }
    }

    public record Problem(Status status, BlockPos pos) {

        public Problem {
            pos = pos.immutable();
        }
    }

    /** 未携带调用方配置要求的便捷入口；工具路径使用带元数据快照的重载。任何现有非空气位置均不会进入放置集合。 */
    public static StructureToolPlan create(StructureView world, Blueprint blueprint, StructureToolOptions.Settings settings, ToIntFunction<Block> inventory) {
        DataSnapshot source = StructureEngine.recognize(world, blueprint, settings.orientation(), settings.repeats()).metadata();
        return create(world, blueprint, settings, inventory, source);
    }

    /** 接收检测或调用方配置提供的元数据，候选试探不能覆盖已经确认的选择。放置扩展契约仍单独演进。 */
    public static StructureToolPlan create(StructureView world, Blueprint blueprint, StructureToolOptions.Settings settings, ToIntFunction<Block> inventory, DataSnapshot source) {
        var metadata = source.verify();
        if (blueprint.cellCount(settings.repeats()) > MAX_CELLS) throw new IllegalArgumentException("Structure exceeds tool cell limit");
        if (!blueprint.supports(settings.orientation())) throw new IllegalArgumentException("Unsupported structure orientation");
        var layout = blueprint.layout(settings.repeats());
        var compiled = blueprint.compiled(settings.orientation());
        Orientation effective = blueprint.effectiveOrientation(settings.orientation());
        Map<BlockPos, BlockState> planned = new LinkedHashMap<>();
        Map<Block, Integer> materials = new LinkedHashMap<>();
        Map<PartRole, Integer> roles = new LinkedHashMap<>();
        List<Target> targets = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        List<Cell> pending = new ArrayList<>();
        ProjectedView projected = new ProjectedView(world, planned);
        for (Cell cell : blueprint.cells(settings.repeats())) {
            if (cell.localPos().equals(BlockPos.ZERO)) continue;
            BlockPos pos = effective.apply(cell.localPos(), world.controllerPos());
            var definition = compiled.definitionAt(layout.cellIndexAt(cell.localPos().getX(), cell.localPos().getY(), cell.localPos().getZ()));
            if (world.isLoaded(pos) && StructureEngine.evaluateCompiled(world, pos, definition, metadata).passed()) {
                BlockState actual = world.blockState(pos);
                targets.add(new Target(pos, Objects.requireNonNull(actual), Status.MATCHED));
                for (PartRole role : world.roles(pos)) roles.merge(role, 1, Integer::sum);
            } else pending.add(cell);
        }
        for (Cell cell : pending) {
            BlockPos pos = effective.apply(cell.localPos(), world.controllerPos());
            int index = layout.cellIndexAt(cell.localPos().getX(), cell.localPos().getY(), cell.localPos().getZ());
            var definition = compiled.definitionAt(index);
            List<BlockState> candidates = new ArrayList<>(settings.extraCandidates(cell));
            if (definition.orientedExpected() != null) candidates.add(definition.orientedExpected());
            for (Block block : cell.binding().candidateBlocks()) candidates.add(block.defaultBlockState());
            BlockState selected = null;
            int bestScore = Integer.MIN_VALUE;
            for (BlockState state : candidates) {
                if (state.isAir()) continue;
                planned.put(pos, state);
                if (!StructureEngine.evaluateCompiled(projected, pos, definition, metadata.fork()).passed()) continue;
                Set<PartRole> offered = rolesFor(state);
                int score = inventory.applyAsInt(state.getBlock()) > materials.getOrDefault(state.getBlock(), 0) ? 1 : 0;
                boolean allowed = true;
                for (PartRole role : offered) {
                    Blueprint.CountRequirement requirement = blueprint.countRequirements().get(role);
                    if (requirement == null) continue;
                    int count = roles.getOrDefault(role, 0);
                    if (count >= requirement.max()) allowed = false;
                    if (count < requirement.min()) score += 100;
                }
                if (allowed && score > bestScore) {
                    bestScore = score;
                    selected = state;
                }
            }
            planned.remove(pos);
            Status status;
            if (!world.isLoaded(pos) || world.blockState(pos) == null) status = Status.UNKNOWN;
            else if (world.blockState(pos) != null && !world.blockState(pos).isAir()) status = Status.BLOCKED;
            else if (selected == null) status = Status.UNRESOLVED;
            else status = Status.MISSING;
            BlockState shown = selected != null ? selected : definition.orientedExpected() != null ? definition.orientedExpected() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            targets.add(new Target(pos, shown, status));
            if (status != Status.MISSING) {
                problems.add(new Problem(status, pos));
                continue;
            }
            planned.put(pos, selected);
            StructureEngine.evaluateCompiled(projected, pos, definition, metadata);
            materials.merge(selected.getBlock(), 1, Integer::sum);
            for (PartRole role : rolesFor(selected)) roles.merge(role, 1, Integer::sum);
        }
        // 自定义谓词和数量约束必须在完整投影中重新通过，不把候选列表误当成匹配证明。
        if (problems.isEmpty()) {
            var result = StructureEngine.recognize(projected, blueprint, settings.orientation(), settings.repeats(), metadata.fork(), "body");
            if (!result.formed() || !result.repeats().equals(settings.repeats())) problems.add(new Problem(Status.UNRESOLVED, world.controllerPos()));
        }
        return new StructureToolPlan(targets, materials, problems, metadata.snapshot());
    }

    private static Set<PartRole> rolesFor(BlockState state) {
        if (!(state.getBlock() instanceof MachineBlock machine)) return Set.of();
        Set<PartRole> result = new LinkedHashSet<>();
        for (PartRoleAttachment role : machine.definition().metadata(PartRoleAttachment.TYPE)) result.add(role.role());
        return Set.copyOf(result);
    }

    private record ProjectedView(StructureView delegate, Map<BlockPos, BlockState> planned) implements StructureView {

        public BlockPos controllerPos() {
            return delegate.controllerPos();
        }

        public boolean isLoaded(BlockPos pos) {
            return planned.containsKey(pos) || delegate.isLoaded(pos);
        }

        public @Nullable BlockState blockState(BlockPos pos) {
            return planned.containsKey(pos) ? planned.get(pos) : delegate.blockState(pos);
        }

        public Set<PartRole> roles(BlockPos pos) {
            return planned.containsKey(pos) ? rolesFor(planned.get(pos)) : delegate.roles(pos);
        }

        public @Nullable Member member(BlockPos pos) {
            if (!planned.containsKey(pos)) return delegate.member(pos);
            BlockState state = planned.get(pos);
            return state.getBlock() instanceof MachineBlock machine ? new Member(pos, !machine.definition().metadata(net.ptcrys.topo.api.machine.multiblock.ability.ExclusivePartAttachment.TYPE).isEmpty()) : null;
        }
    }
}
