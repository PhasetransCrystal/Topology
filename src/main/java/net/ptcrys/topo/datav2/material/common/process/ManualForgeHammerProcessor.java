package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.datav2.equipment.BuiltinOIEquipment;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIProcessDies;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/**
 * Cold-start forge-hammer workbench recipes (half machine yield) via {@link BuiltinOIRecipeTypes#SHAPELESS}.
 */
public final class ManualForgeHammerProcessor extends MaterialPostProcessor {

    public ManualForgeHammerProcessor(Identifier id, RegistryCore core) {
        super(id, BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        register(
                material,
                BuiltinOIMaterialForms.PLATE,
                1,
                BuiltinOIProcessDies.TEMPLATE_PLATE,
                2);
        register(material, BuiltinOIMaterialForms.ROD, 1, BuiltinOIProcessDies.TEMPLATE_ROD, 1);
        register(material, BuiltinOIMaterialForms.GEAR, 1, BuiltinOIProcessDies.TEMPLATE_GEAR, 4);
        register(material, BuiltinOIMaterialForms.WIRE_1X, 1, BuiltinOIProcessDies.TEMPLATE_WIRE, 1);
        register(material, BuiltinOIMaterialForms.BOLT, 2, BuiltinOIProcessDies.TEMPLATE_BOLT, 1);
    }

    private static void register(
                                 Material material,
                                 MaterialForm outputForm,
                                 int outputCount,
                                 ItemEntry<? extends ItemLike> template,
                                 int ingotCount) {
        if (!material.strategy().forms().contains(outputForm)) {
            return;
        }
        Supplier<Item> ingot = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.INGOT);
        Supplier<Item> output = MaterialHelper.materialItemSupplier(material, outputForm);
        String path = "manual/material/" + MaterialHelper.itemPath(material, outputForm) + "_from_" + MaterialHelper.itemPath(material, BuiltinOIMaterialForms.INGOT);
        BuiltinOIRecipeTypes.CRAFTING_SHAPELESS
                .recipe(path, output, outputCount)
                .requires(ingot, ingotCount)
                .requires(BuiltinOIEquipment.FORGE_HAMMERS)
                .requires((Supplier<? extends ItemLike>) template)
                .unlockedBy(ingot)
                .save();
    }
}
