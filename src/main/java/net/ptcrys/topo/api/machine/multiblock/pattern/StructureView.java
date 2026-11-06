package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable read view consumed by the structure engine. It is <b>block-entity-free</b>: it exposes
 * each cell's {@link BlockState}, its precomputed {@link PartRole} set, and the machine member (a
 * part) occupying the cell. The live {@code MachineBlockEntity} is never held here, so a recognition
 * pass can run off the server thread without touching a live {@code Level} or block entity. Member
 * positions flow out through {@link RecognitionResult}; the controller re-resolves the live block
 * entities by position on the server thread.
 */
public interface StructureView {

    /** The world anchor blueprint-local offsets are placed against (the controller cell). */
    BlockPos controllerPos();

    /**
     * One read of a footprint cell: its block state, part roles, and member comment, or {@code null}
     * when the cell lies in an unloaded chunk (unknown, not missing). The engine prefers this over
     * the individual accessors so a live view performs a single world/block-entity lookup per cell;
     * {@link #blockState()}, {@link #roles()} and {@link #member()} remain for predicates and other
     * consumers that need only one facet.
     */
    default @Nullable CellInfo cellInfo(BlockPos worldPos) {
        if (!isLoaded(worldPos)) {
            return null;
        }
        return new CellInfo(blockState(worldPos), roles(worldPos), member(worldPos));
    }

    @Nullable
    BlockState blockState(BlockPos worldPos);

    /** Whether {@code worldPos} could be read from a loaded chunk; false = unknown, not missing. */
    default boolean isLoaded(BlockPos worldPos) {
        return true;
    }

    Set<PartRole> roles(BlockPos worldPos);

    /**
     * The machine member (part) occupying {@code worldPos}, or {@code null}. A member is a member
     * first; its declaration comments (currently {@link Member#exclusive()}) travel with it instead
     * of forming a separate collection.
     */
    default @Nullable Member member(BlockPos worldPos) {
        return null;
    }

    /** Whether a machine member (part) occupies {@code worldPos}, recorded BE-free at snapshot time. */
    default boolean isMember(BlockPos worldPos) {
        return member(worldPos) != null;
    }

    default boolean hasCapability(BlockPos worldPos, PartRole capability) {
        return roles(worldPos).contains(capability);
    }

    default boolean isController(BlockPos worldPos) {
        return controllerPos().equals(worldPos);
    }

    /** One member's full declaration data: block state, part roles, and member comment. */
    record CellInfo(@Nullable BlockState state, Set<PartRole> roles, @Nullable Member member) {

        public CellInfo {
            roles = Set.copyOf(Objects.requireNonNull(roles, "cell roles"));
        }

        /** Whether this cell is a machine member. */
        public boolean isMember() {
            return member != null;
        }
    }

    /**
     * One machine member: its world position plus declaration comments.
     *
     * @param pos       the member's world position
     * @param exclusive whether the member's definition carries {@code ExclusivePartAttachment};
     *                  exclusive parts are reserved by one formed machine, ordinary members are shareable
     */
    record Member(BlockPos pos, boolean exclusive) {

        public Member {
            pos = Objects.requireNonNull(pos, "member position").immutable();
        }
    }
}
