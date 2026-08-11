package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Dense, block-entity-free point-in-time view of one compiled footprint — the million-cell
 * snapshot. States live in a flat array indexed by the blueprint's canonical cell index; position
 * lookups go through {@link CompiledBlueprint#cellIndexOfWorld} (pure integer math), so neither
 * capture nor recognition touches a per-cell hash map. Capabilities and machine-membership are
 * sparse side tables — only part cells (a handful of hatches in a million casings) pay an entry.
 */
public final class CompiledSnapshot implements StructureView {

    private final CompiledBlueprint compiled;
    private final BlockPos controllerPos;
    private final BlockState[] states;
    private final Long2ObjectOpenHashMap<Set<PartRole>> capabilities;
    private final LongOpenHashSet members;

    private CompiledSnapshot(
                             CompiledBlueprint compiled,
                             BlockPos controllerPos,
                             BlockState[] states,
                             Long2ObjectOpenHashMap<Set<PartRole>> capabilities,
                             LongOpenHashSet members) {
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
    public @Nullable BlockState blockState(BlockPos worldPos) {
        int index = compiled.cellIndexOfWorld(worldPos, controllerPos);
        return index < 0 ? null : states[index];
    }

    @Override
    public Set<PartRole> roles(BlockPos worldPos) {
        Set<PartRole> set = capabilities.get(worldPos.asLong());
        return set == null ? Set.of() : set;
    }

    @Override
    public boolean isMember(BlockPos worldPos) {
        return members.contains(worldPos.asLong());
    }

    public static final class Builder {

        private final CompiledBlueprint compiled;
        private final BlockPos controllerPos;
        private final BlockState[] states;
        private final Long2ObjectOpenHashMap<Set<PartRole>> capabilities = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet members = new LongOpenHashSet();

        private Builder(CompiledBlueprint compiled, BlockPos controllerPos) {
            this.compiled = Objects.requireNonNull(compiled, "compiled blueprint");
            this.controllerPos = Objects.requireNonNull(controllerPos, "controller position").immutable();
            this.states = new BlockState[compiled.cellCapacity()];
        }

        /** Writes one captured cell; {@code state} null = unloaded chunk (recognized as not formed). */
        public Builder put(int cellIndex, long packedWorldPos, @Nullable BlockState state, boolean machineMember) {
            states[cellIndex] = state;
            if (machineMember) {
                members.add(packedWorldPos);
            }
            return this;
        }

        public Builder roles(long packedWorldPos, Set<PartRole> caps) {
            if (!caps.isEmpty()) {
                capabilities.put(packedWorldPos, Set.copyOf(caps));
            }
            return this;
        }

        public CompiledSnapshot build() {
            return new CompiledSnapshot(compiled, controllerPos, states, capabilities, members);
        }
    }
}
