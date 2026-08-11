package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.data.equipment.BuiltinTopoEquipment;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoProcessDies;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/**
 * Cold-start forge-hammer workbench recipes (half machine yield) via {@link BuiltinTopoRecipeTypes#SHAPELESS}.
 */
public final class ManualForgeHammerProcessor extends MaterialPostProcessor {

    public ManualForgeHammerProcessor(Identifier id, RegistryCore core) {
        super(id, BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        register(
                material,
                BuiltinTopoMaterialForms.PLATE,
                1,
                BuiltinTopoProcessDies.TEMPLATE_PLATE,
                2);
        register(material, BuiltinTopoMaterialForms.ROD, 1, BuiltinTopoProcessDies.TEMPLATE_ROD, 1);
        register(material, BuiltinTopoMaterialForms.GEAR, 1, BuiltinTopoProcessDies.TEMPLATE_GEAR, 4);
        register(material, BuiltinTopoMaterialForms.WIRE_1X, 1, BuiltinTopoProcessDies.TEMPLATE_WIRE, 1);
        register(material, BuiltinTopoMaterialForms.BOLT, 2, BuiltinTopoProcessDies.TEMPLATE_BOLT, 1);
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
        Supplier<Item> ingot = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.INGOT);
        Supplier<Item> output = MaterialHelper.materialItemSupplier(material, outputForm);
        String path = "manual/material/" + MaterialHelper.itemPath(material, outputForm) + "_from_" + MaterialHelper.itemPath(material, BuiltinTopoMaterialForms.INGOT);
        BuiltinTopoRecipeTypes.CRAFTING_SHAPELESS
                .recipe(path, output, outputCount)
                .requires(ingot, ingotCount)
                .requires(BuiltinTopoEquipment.FORGE_HAMMERS)
                .requires((Supplier<? extends ItemLike>) template)
                .unlockedBy(ingot)
                .save();
    }
}
