package net.ptcrys.topo.api.recipe;

import net.ptcrys.topo.api.machine.MachineBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Vanilla recipe input adapter for machine-scoped Topo recipes.
 *
 * <p>
 * Capability recipes match through {@link #machine()}; vanilla's item-shaped accessors are unsupported so item
 * semantics stay owned by item recipe capabilities.
 */
public final class TopoRecipeInput implements RecipeInput {

    private final MachineBlockEntity machine;

    public TopoRecipeInput(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public MachineBlockEntity machine() {
        return machine;
    }

    @Override
    public ItemStack getItem(int index) {
        throw unsupportedItemView();
    }

    @Override
    public int size() {
        throw unsupportedItemView();
    }

    @Override
    public boolean isEmpty() {
        throw unsupportedItemView();
    }

    private static UnsupportedOperationException unsupportedItemView() {
        return new UnsupportedOperationException(
                "TopoRecipeInput is a machine recipe context, not a vanilla item input view; " + "use RecipeCapability implementations for item matching and transfer.");
    }
}
