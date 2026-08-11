package net.ptcrys.topo.api.pipe;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Per-resource-kind bridge between filter entry strings and concrete {@link Resource} matching.
 * One adapter is declared on the kind's {@link PipeResourceProfile}; kinds without one (scalar
 * energy/heat) cannot mount a non-{@code NONE} {@link PipeFilterSettings} — cross-validated in
 * {@link Pipes.Builder#build()}.
 *
 * <p>
 * Entry grammar (shared by every adapter): a plain registry id ({@code minecraft:coal})
 * matches that exact registry entry regardless of data components; a {@code #}-prefixed id
 * ({@code #c:ingots}) matches by tag. Entries that do not resolve against the registry simply
 * never match — they stay visible in the list so the player can see and remove them.
 */
public interface PipeFilterAdapter<R extends Resource> {

    /**
     * Compile one entry list into a fast predicate (identity set + tag keys; no per-test
     * allocation). Recompiled only when the port's filter config changes.
     */
    Predicate<R> compile(List<String> entries);

    /**
     * Client display stack for a plain-id entry's list-row icon ({@code EMPTY} when the id does
     * not resolve). Fluid adapters return the fluid's bucket so rows stay item-rendered.
     */
    ItemStack displayStack(String idEntry);

    /**
     * Client display stacks for a list-row preview. Plain ids normally produce one stack; tag
     * entries may produce several stacks and the UI may cycle them.
     */
    default List<ItemStack> displayStacks(String entry) {
        ItemStack stack = displayStack(entry);
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    /**
     * Client display fluids for a list-row preview. Non-fluid adapters return an empty list; fluid
     * adapters can expose real fluid textures instead of bucket stand-ins.
     */
    default List<FluidStack> displayFluidStacks(String entry) {
        return List.of();
    }

    /**
     * Translate the stack on the player's cursor (ghost-slot click) into a plain-id entry, or
     * {@code null} when the stack carries nothing of this kind (e.g. an empty bucket on a fluid
     * pipe). Pure read — the cursor stack is never mutated.
     */
    @Nullable
    String entryFromCarried(ItemStack carried);
}
