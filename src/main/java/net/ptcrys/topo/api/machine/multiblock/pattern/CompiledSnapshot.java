package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 一个时点的稳定蓝图快照，不持有世界或方块实体。
 * 状态按最大布局中的稳定编号存储，位置通过 BlueprintLayout 反查；角色和成员以稀疏表保存。
 * 发布后只读，可以跨线程检测；构建器移交数据所有权后立即失效。
 */
public final class CompiledSnapshot implements StructureView {

    private final CompiledBlueprint compiled;
    private final BlockPos controllerPos;
    private final BlockState[] states;
    private final Long2ObjectOpenHashMap<Set<PartRole>> capabilities;
    private final Long2ObjectOpenHashMap<StructureView.Member> members;

    private CompiledSnapshot(
                             CompiledBlueprint compiled,
                             BlockPos controllerPos,
                             BlockState[] states,
                             Long2ObjectOpenHashMap<Set<PartRole>> capabilities,
                             Long2ObjectOpenHashMap<StructureView.Member> members) {
        this.compiled = compiled;
        this.controllerPos = controllerPos.immutable();
        this.states = states;
        this.capabilities = capabilities;
        this.members = members;
    }

    public static Builder builder(CompiledBlueprint compiled, BlockPos controllerPos) {
        return new Builder(compiled, controllerPos);
    }

    public CompiledBlueprint compiled() {
        return compiled;
    }

    @Override
    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override
    public StructureView.@Nullable CellInfo cellInfo(BlockPos worldPos) {
        int index = compiled.cellIndexOfWorld(worldPos, controllerPos);
        if (index < 0) {
            return null;
        }
        BlockState state = states[index];
        if (state == null) {
            return null;
        }
        long packed = worldPos.asLong();
        Set<PartRole> roles = capabilities.get(packed);
        return new StructureView.CellInfo(state, roles == null ? Set.of() : roles, members.get(packed));
    }

    @Override
    public @Nullable BlockState blockState(BlockPos worldPos) {
        int index = compiled.cellIndexOfWorld(worldPos, controllerPos);
        return index < 0 ? null : states[index];
    }

    @Override
    public boolean isLoaded(BlockPos worldPos) {
        int index = compiled.cellIndexOfWorld(worldPos, controllerPos);
        return index >= 0 && states[index] != null;
    }

    @Override
    public Set<PartRole> roles(BlockPos worldPos) {
        Set<PartRole> set = capabilities.get(worldPos.asLong());
        return set == null ? Set.of() : set;
    }

    @Override
    public StructureView.@Nullable Member member(BlockPos worldPos) {
        return members.get(worldPos.asLong());
    }

    public static final class Builder {

        private final CompiledBlueprint compiled;
        private final BlockPos controllerPos;
        private final BlockState[] states;
        private boolean built;
        private final Long2ObjectOpenHashMap<Set<PartRole>> capabilities = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<StructureView.Member> members = new Long2ObjectOpenHashMap<>();

        private Builder(CompiledBlueprint compiled, BlockPos controllerPos) {
            this.compiled = Objects.requireNonNull(compiled, "compiled blueprint");
            this.controllerPos = Objects.requireNonNull(controllerPos, "controller position").immutable();
            if (!compiled.blueprint().supportsSnapshot()) {
                throw new IllegalArgumentException("A moving layout must be read through a live structure view");
            }
            this.states = new BlockState[compiled.cellCapacity()];
        }

        /** 写入一个采集位置；state 为 null 表示未加载。位置必须与稳定编号对应，成员必须属于同一已加载位置；覆盖写入会清除原角色和成员。 */
        public Builder put(
                           int cellIndex,
                           long packedWorldPos,
                           @Nullable BlockState state,
                           StructureView.@Nullable Member member) {
            ensureOpen();
            Objects.checkIndex(cellIndex, states.length);
            if (compiled.cellIndexOfWorld(BlockPos.of(packedWorldPos), controllerPos) != cellIndex) {
                throw new IllegalArgumentException("Snapshot position does not match its cell index");
            }
            if (member != null && (state == null || member.pos().asLong() != packedWorldPos)) {
                throw new IllegalArgumentException("Snapshot member does not match the captured cell");
            }
            states[cellIndex] = state;
            capabilities.remove(packedWorldPos);
            if (member == null) members.remove(packedWorldPos);
            else members.put(packedWorldPos, member);
            return this;
        }

        public Builder roles(long packedWorldPos, Set<PartRole> caps) {
            ensureOpen();
            int index = compiled.cellIndexOfWorld(BlockPos.of(packedWorldPos), controllerPos);
            if (index < 0 || states[index] == null) throw new IllegalArgumentException("Roles require a loaded snapshot cell");
            if (caps.isEmpty()) capabilities.remove(packedWorldPos);
            else capabilities.put(packedWorldPos, Set.copyOf(caps));
            return this;
        }

        /** 发布后移交数组和稀疏表的所有权，构建器随即失效，避免复制百万格快照。 */
        public CompiledSnapshot build() {
            ensureOpen();
            built = true;
            return new CompiledSnapshot(compiled, controllerPos, states, capabilities, members);
        }

        private void ensureOpen() {
            if (built) throw new IllegalStateException("Snapshot builder has already published its data");
        }
    }
}
