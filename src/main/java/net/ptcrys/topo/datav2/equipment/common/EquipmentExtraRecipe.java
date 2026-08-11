package net.ptcrys.topo.datav2.equipment.common;

import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.apiv2.material.Material;

import net.minecraft.world.item.Item;

/**
 * Optional assembly recipe for an equipment kind when {@link EquipmentPattern} is not enough (e.g.
 * stick + ingot forge hammers). Emit via {@link net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes} builders;
 * invoked once per registered (kind, material) item.
 */
@FunctionalInterface
public interface EquipmentExtraRecipe {

    void emit(Material material, ItemEntry<Item> entry);
}
