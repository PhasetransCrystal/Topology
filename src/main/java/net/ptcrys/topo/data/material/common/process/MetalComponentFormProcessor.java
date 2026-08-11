package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoProcessDies;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Machine-component conversions: stamped forms in the component processor, dense plates by
 * rolling plates, and frame/rotor assembly on the crafting table via {@link BuiltinTopoRecipeTypes#SHAPED}.
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
        require(material, BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        registerIngotForm(material, BuiltinTopoMaterialForms.PLATE, 1, BuiltinTopoProcessDies.TEMPLATE_PLATE::get, 1);
        registerIngotForm(material, BuiltinTopoMaterialForms.ROD, 2, BuiltinTopoProcessDies.TEMPLATE_ROD::get, 1);
        registerIngotForm(material, BuiltinTopoMaterialForms.GEAR, 1, BuiltinTopoProcessDies.TEMPLATE_GEAR::get, 2);
        registerIngotForm(material, BuiltinTopoMaterialForms.BOLT, 4, BuiltinTopoProcessDies.TEMPLATE_BOLT::get, 1);
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
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        BuiltinTopoRecipeTypes.COMPONENT_PROCESSOR.recipe(recipeId(material, outputForm))
                .input(items.in(material, BuiltinTopoMaterialForms.INGOT, inputCount))
                .input(items.unconsumedInput(template))
                .output(items.out(material, outputForm, outputCount))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(DEFAULT_DURATION)
                .productionLine(BuiltinTopoProductionLines.MACHINE_COMPONENT)
                .save();
    }

    private static void registerDensePlate(Material material) {
        if (!declares(material, BuiltinTopoMaterialForms.PLATE_DENSE) || !declares(material, BuiltinTopoMaterialForms.PLATE)) {
            return;
        }
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        String materialPath = material.id().getPath();
        BuiltinTopoRecipeTypes.COMPONENT_PROCESSOR
                .recipe(itemName(materialPath, BuiltinTopoMaterialForms.PLATE_DENSE) + "_from_" + itemName(materialPath, BuiltinTopoMaterialForms.PLATE))
                .input(items.in(material, BuiltinTopoMaterialForms.PLATE, 9))
                .input(items.unconsumedInput((Supplier<? extends ItemLike>) BuiltinTopoProcessDies.TEMPLATE_PLATE))
                .output(items.out(material, BuiltinTopoMaterialForms.PLATE_DENSE, 1))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(DENSE_DURATION)
                .productionLine(BuiltinTopoProductionLines.MACHINE_COMPONENT)
                .save();
    }

    private void registerAssemblyRecipes(Material material) {
        registerFrameRecipe(material);
        registerRotorRecipe(material);
    }

    private static void registerFrameRecipe(Material material) {
        if (!declares(material, BuiltinTopoMaterialForms.FRAME) || !declares(material, BuiltinTopoMaterialForms.ROD) || !declares(material, BuiltinTopoMaterialForms.BOLT)) {
            return;
        }
        Supplier<Item> rod = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.ROD);
        Supplier<Item> bolt = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.BOLT);
        Supplier<Item> frame = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.FRAME);
        String framePath = MaterialHelper.itemPath(material, BuiltinTopoMaterialForms.FRAME);
        BuiltinTopoRecipeTypes.CRAFTING_SHAPED
                .recipe("shape/material/" + framePath + "_from_rod_bolt", frame)
                .pattern("RBR", "B B", "RBR")
                .define('R', rod)
                .define('B', bolt)
                .unlockedBy(rod)
                .save();
    }

    private static void registerRotorRecipe(Material material) {
        if (!declares(material, BuiltinTopoMaterialForms.ROTOR) || !declares(material, BuiltinTopoMaterialForms.PLATE) || !declares(material, BuiltinTopoMaterialForms.ROD) || !declares(material, BuiltinTopoMaterialForms.GEAR)) {
            return;
        }
        Supplier<Item> plate = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.PLATE);
        Supplier<Item> rod = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.ROD);
        Supplier<Item> gear = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.GEAR);
        Supplier<Item> rotor = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.ROTOR);
        String rotorPath = MaterialHelper.itemPath(material, BuiltinTopoMaterialForms.ROTOR);
        BuiltinTopoRecipeTypes.CRAFTING_SHAPED
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
        return itemName(materialPath, outputForm) + "_from_" + itemName(materialPath, BuiltinTopoMaterialForms.INGOT);
    }

    private static String itemName(String materialPath, MaterialForm form) {
        return String.format(form.strategy().registryPath(), materialPath);
    }
}
