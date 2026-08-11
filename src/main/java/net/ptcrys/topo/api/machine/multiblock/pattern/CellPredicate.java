package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Leaf element (L1) deciding whether the block placed at a blueprint cell satisfies that cell.
 *
 * <p>
 * The primary path is read-only and works from a {@link StructureView}: capability predicates read
 * the snapshot's precomputed {@link PartRole} set, controller predicates check the controller
 * origin, and plain block predicates can still compare states.
 *
 * <p>
 * Built-in factory kinds live in {@link CellPredicates}; predicates are plain values referenced
 * from blueprint declarations, never registry entries.
 */
public interface CellPredicate {

    /**
     * Returns {@code true} if {@code worldPos} satisfies this cell inside the immutable recognition
     * view.
     */
    default boolean test(StructureView view, BlockPos worldPos, Cell expected) {
        return test(view.blockState(worldPos), expected.expectedState());
    }

    /**
     * State-only fallback used by block predicates and simple unit tests. Predicate kinds that
     * need controller/member/capability context should override the {@link StructureView} overload.
     */
    boolean test(@Nullable BlockState actual, @Nullable BlockState expected);

    /** Structural part capabilities this predicate can match, for blueprint count validation. */
    default List<PartRole> partRoles() {
        return List.of();
    }

    /**
     * Example item stacks that satisfy this cell, for JEI/recipe display. Empty by default.
     */
    default List<ItemStack> candidates() {
        return List.of();
    }
}
