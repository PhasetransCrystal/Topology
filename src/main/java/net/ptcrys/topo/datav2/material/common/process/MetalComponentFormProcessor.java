package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIProcessDies;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Machine-component conversions: stamped forms in the component processor, dense plates by
 * rolling plates, and frame/rotor assembly on the crafting table via {@link BuiltinOIRecipeTypes#SHAPED}.
 */
public final class MetalComponentFormProcessor extends MaterialPostProcessor {

    private static final int DEFAULT_DURATION = 100;
    private static final int DENSE_DURATION = 200;
    private static final long ENERGY_PER_TICK = 30;

    public MetalComponentFormProcessor(Identifier id) {
        super(id);
    }

    public MetalComponentFormProcessor(Identifier id, RegistryCore core) {
        this(id);
    }

    @Override
    public void validate(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        require(material, BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        registerIngotForm(material, BuiltinOIMaterialForms.PLATE, 1, BuiltinOIProcessDies.TEMPLATE_PLATE::get, 1);
        registerIngotForm(material, BuiltinOIMaterialForms.ROD, 2, BuiltinOIProcessDies.TEMPLATE_ROD::get, 1);
        registerIngotForm(material, BuiltinOIMaterialForms.GEAR, 1, BuiltinOIProcessDies.TEMPLATE_GEAR::get, 2);
        registerIngotForm(material, BuiltinOIMaterialForms.BOLT, 4, BuiltinOIProcessDies.TEMPLATE_BOLT::get, 1);
        registerDensePlate(material);
        registerAssemblyRecipes(material);
    }

    private static void registerIngotForm(
                                          Material material,
                                          MaterialForm outputForm,
                                          int outputCount,
                                          Supplier<? extends ItemLike> template,
                                          int inputCount) {
        Objects.requireNonNull(template, "template");
        if (!declares(material, outputForm)) {
            return;
        }
        ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
        BuiltinOIRecipeTypes.COMPONENT_PROCESSOR.recipe(recipeId(material, outputForm))
                .input(items.in(material, BuiltinOIMaterialForms.INGOT, inputCount))
                .input(items.unconsumedInput(template))
                .output(items.out(material, outputForm, outputCount))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(DEFAULT_DURATION)
                .productionLine(BuiltinOIProductionLines.MACHINE_COMPONENT)
                .save();
    }

    private static void registerDensePlate(Material material) {
        if (!declares(material, BuiltinOIMaterialForms.PLATE_DENSE) || !declares(material, BuiltinOIMaterialForms.PLATE)) {
            return;
        }
        ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
        String materialPath = material.id().getPath();
        BuiltinOIRecipeTypes.COMPONENT_PROCESSOR
                .recipe(itemName(materialPath, BuiltinOIMaterialForms.PLATE_DENSE) + "_from_" + itemName(materialPath, BuiltinOIMaterialForms.PLATE))
                .input(items.in(material, BuiltinOIMaterialForms.PLATE, 9))
                .input(items.unconsumedInput((Supplier<? extends ItemLike>) BuiltinOIProcessDies.TEMPLATE_PLATE))
                .output(items.out(material, BuiltinOIMaterialForms.PLATE_DENSE, 1))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(DENSE_DURATION)
                .productionLine(BuiltinOIProductionLines.MACHINE_COMPONENT)
                .save();
    }

    private void registerAssemblyRecipes(Material material) {
        registerFrameRecipe(material);
        registerRotorRecipe(material);
    }

    private static void registerFrameRecipe(Material material) {
        if (!declares(material, BuiltinOIMaterialForms.FRAME) || !declares(material, BuiltinOIMaterialForms.ROD) || !declares(material, BuiltinOIMaterialForms.BOLT)) {
            return;
        }
        Supplier<Item> rod = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.ROD);
        Supplier<Item> bolt = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.BOLT);
        Supplier<Item> frame = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.FRAME);
        String framePath = MaterialHelper.itemPath(material, BuiltinOIMaterialForms.FRAME);
        BuiltinOIRecipeTypes.CRAFTING_SHAPED
                .recipe("shape/material/" + framePath + "_from_rod_bolt", frame)
                .pattern("RBR", "B B", "RBR")
                .define('R', rod)
                .define('B', bolt)
                .unlockedBy(rod)
                .save();
    }

    private static void registerRotorRecipe(Material material) {
        if (!declares(material, BuiltinOIMaterialForms.ROTOR) || !declares(material, BuiltinOIMaterialForms.PLATE) || !declares(material, BuiltinOIMaterialForms.ROD) || !declares(material, BuiltinOIMaterialForms.GEAR)) {
            return;
        }
        Supplier<Item> plate = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.PLATE);
        Supplier<Item> rod = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.ROD);
        Supplier<Item> gear = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.GEAR);
        Supplier<Item> rotor = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.ROTOR);
        String rotorPath = MaterialHelper.itemPath(material, BuiltinOIMaterialForms.ROTOR);
        BuiltinOIRecipeTypes.CRAFTING_SHAPED
                .recipe("shape/material/" + rotorPath + "_from_plate_rod_gear", rotor)
                .pattern(" P ", "RGR", " P ")
                .define('P', plate)
                .define('R', rod)
                .define('G', gear)
                .unlockedBy(gear)
                .save();
    }

    private static boolean declares(Material material, MaterialForm form) {
        return material.strategy().forms().contains(form);
    }

    private static String recipeId(Material material, MaterialForm outputForm) {
        String materialPath = material.id().getPath();
        return itemName(materialPath, outputForm) + "_from_" + itemName(materialPath, BuiltinOIMaterialForms.INGOT);
    }

    private static String itemName(String materialPath, MaterialForm form) {
        return String.format(form.strategy().registryPath(), materialPath);
    }
}
