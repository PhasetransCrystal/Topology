package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Immutable read view consumed by the structure engine. It is <b>block-entity-free</b>: it exposes
 * each cell's {@link BlockState}, its precomputed {@link PartRole} set, and whether the cell is a
 * machine member (a part). The live {@code MachineBlockEntity} is never held here, so a recognition
 * pass can run off the server thread without touching a live {@code Level} or block entity. Member
 * positions flow out through {@link RecognitionResult}; the controller re-resolves the live block
 * entities by position on the server thread.
 */
public interface StructureView {

    /** The world anchor blueprint-local offsets are placed against (the controller cell). */
    BlockPos controllerPos();

    @Nullable
    BlockState blockState(BlockPos worldPos);

    Set<PartRole> roles(BlockPos worldPos);

    /** Whether a machine member (part) occupies {@code worldPos}, recorded BE-free at snapshot time. */
    boolean isMember(BlockPos worldPos);

    default boolean hasCapability(BlockPos worldPos, PartRole capability) {
        return roles(worldPos).contains(capability);
    }

    default boolean isController(BlockPos worldPos) {
        return controllerPos().equals(worldPos);
    }
}
