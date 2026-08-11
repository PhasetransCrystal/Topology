package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Supplier;

abstract class OreProcessingLineProcessor extends MaterialPostProcessor {

    private static final int MACERATE_TIME = 100;
    private static final int WASH_TIME = 120;
    private static final int CHEMICAL_TIME = 160;
    private static final int SMELT_TIME = 100;
    private static final int CRYSTALLIZE_TIME = 200;

    private static final long ENERGY_PER_TICK = 30;
    private static final long HEAT_PER_TICK = 10;
    private static final long ADVANCED_ENERGY_PER_TICK = 2;

    private static final int WATER_MB = 250;
    private static final int REAGENT_MB = 250;
    private static final int LUMINITE_MB = 100;
    private static final int SPENT_LIQUOR_MB = 100;

    protected OreProcessingLineProcessor(Identifier id, MaterialForm... requiredForms) {
        super(id, requiredForms);
    }

    protected final boolean shouldSkip(Material material) {
        return notDeclaredOn(material);
    }

    protected final void registerOreToCrudeDust(Material material, ProductionLine... productionLines) {
        registerMaceration(material, BuiltinOIMaterialForms.ORE, productionLines);
        registerMaceration(material, BuiltinOIMaterialForms.DEEPSLATE_ORE, productionLines);
        if (material.strategy().forms().contains(BuiltinOIMaterialForms.NETHERRACK_ORE)) {
            registerMaceration(material, BuiltinOIMaterialForms.NETHERRACK_ORE, productionLines);
        }
    }

    protected final void registerTwoTimesSmelt(Material material, ProductionLine... productionLines) {
        // 1:1 on SMELTING (export → vanilla furnace); electric furnace imports powered projection.
        registerUnitSmelt(material, BuiltinOIMaterialForms.CRUDE_DUST, productionLines);
    }

    protected final void registerCrudeDustWash(Material material, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.ORE_WASHER.recipe(recipeId(material, "purified_dust_from_crude_dust"))
                .input(items().in(material, BuiltinOIMaterialForms.CRUDE_DUST, 2))
                .input(fluids().in(Fluids.WATER, WATER_MB))
                .output(items().out(material, BuiltinOIMaterialForms.PURIFIED_DUST, 3))
                .output(items().out(material, BuiltinOIMaterialForms.TINY_DUST, 1))
                .tickInput(energy().in(ENERGY_PER_TICK))
                .duration(WASH_TIME)
                .productionLines(productionLines)
                .save();
    }

    protected final void registerThreeTimesSmelt(Material material, ProductionLine... productionLines) {
        registerUnitSmelt(material, BuiltinOIMaterialForms.PURIFIED_DUST, productionLines);
    }

    protected final void registerAcidLeach(Material material, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe(recipeId(material, "slurry_from_purified_dust"))
                .input(items().in(material, BuiltinOIMaterialForms.PURIFIED_DUST, 3))
                .input(fluids().in(fluid(BuiltinOIMaterials.SULFURIC_ACID, BuiltinOIMaterialForms.SOLUTION),
                        REAGENT_MB))
                .output(items().out(material, BuiltinOIMaterialForms.SLURRY, 4))
                .output(items().out(BuiltinOIMaterials.WASTE_SLAG, BuiltinOIMaterialForms.SLAG_CHUNK, 1))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.WASTE_ACID_SOLUTION, BuiltinOIMaterialForms.SOLUTION),
                        REAGENT_MB))
                .tickInput(heat().in(HEAT_PER_TICK))
                .duration(CHEMICAL_TIME)
                .productionLines(productionLines)
                .save();
    }

    protected final void registerFourTimesSmelt(Material material, ProductionLine... productionLines) {
        registerUnitSmelt(material, BuiltinOIMaterialForms.SLURRY, productionLines);
    }

    protected final void registerNeutralize(Material material, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe(recipeId(material, "neutralized_slurry_from_slurry"))
                .input(items().in(material, BuiltinOIMaterialForms.SLURRY, 4))
                .input(items().in(BuiltinOIMaterials.SODIUM_HYDROXIDE, BuiltinOIMaterialForms.DUST, 1))
                .output(items().out(material, BuiltinOIMaterialForms.NEUTRALIZED_SLURRY, 4))
                .output(items().out(BuiltinOIMaterials.SOLUBLE_SALT, BuiltinOIMaterialForms.DUST, 1))
                .tickInput(energy().in(ENERGY_PER_TICK))
                .duration(CHEMICAL_TIME)
                .productionLines(productionLines)
                .save();
    }

    protected final void registerElectrolyze(Material material, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.ELECTROLYZER.recipe(recipeId(material, "high_purity_dust_from_neutralized_slurry"))
                .input(items().in(material, BuiltinOIMaterialForms.NEUTRALIZED_SLURRY, 4))
                .input(fluids().in(fluid(BuiltinOIMaterials.ELECTROLYTE, BuiltinOIMaterialForms.SOLUTION), REAGENT_MB))
                .output(items().out(material, BuiltinOIMaterialForms.HIGH_PURITY_DUST, 6))
                .output(items().out(material, BuiltinOIMaterialForms.RARE_ELEMENT, 1))
                .tickInput(advancedEnergy().in(ADVANCED_ENERGY_PER_TICK))
                .duration(CHEMICAL_TIME)
                .productionLines(productionLines)
                .save();
    }

    protected final void registerSixTimesSmelt(Material material, ProductionLine... productionLines) {
        registerUnitSmelt(material, BuiltinOIMaterialForms.HIGH_PURITY_DUST, productionLines);
    }

    protected final void registerActivate(Material material, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.INDUSTRIAL_COMBUSTION_FURNACE.recipe(
                recipeId(material, "activated_dust_from_high_purity_dust"))
                .input(items().in(material, BuiltinOIMaterialForms.HIGH_PURITY_DUST, 6))
                .output(items().out(material, BuiltinOIMaterialForms.ACTIVATED_DUST, 6))
                .tickInput(heat().in(HEAT_PER_TICK))
                .duration(CHEMICAL_TIME)
                .productionLines(productionLines)
                .save();
    }

    protected final void registerCrystallize(Material material, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER.recipe(
                recipeId(material, "resonant_dust_from_activated_dust"))
                .input(items().in(material, BuiltinOIMaterialForms.ACTIVATED_DUST, 6))
                .input(fluids().in(fluid(BuiltinOIMaterials.LUMINITE, BuiltinOIMaterialForms.SOLUTION), LUMINITE_MB))
                .output(items().out(material, BuiltinOIMaterialForms.RESONANT_DUST, 12))
                .output(fluids().out(fluid(BuiltinOIMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinOIMaterialForms.SOLUTION),
                        SPENT_LIQUOR_MB))
                .tickInput(advancedEnergy().in(ADVANCED_ENERGY_PER_TICK))
                .duration(CRYSTALLIZE_TIME)
                .productionLines(productionLines)
                .save();
    }

    protected final void registerTwelveTimesSmelt(Material material, ProductionLine... productionLines) {
        registerUnitSmelt(material, BuiltinOIMaterialForms.RESONANT_DUST, productionLines);
    }

    /**
     * Pure item smelting SoT on {@link BuiltinOIRecipeTypes#SMELTING} (1 in → 1 out). Electric
     * furnace sees it via import; vanilla furnace gets the exported cooking JSON.
     */
    private static void registerUnitSmelt(
                                          Material material, MaterialForm inputForm, ProductionLine... productionLines) {
        Supplier<net.minecraft.world.item.Item> input = MaterialHelper.materialItemSupplier(material, inputForm);
        Supplier<net.minecraft.world.item.Item> ingot = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.INGOT);
        String ingotPath = MaterialHelper.itemPath(material, BuiltinOIMaterialForms.INGOT);
        String inputPath = MaterialHelper.itemPath(material, inputForm);
        BuiltinOIRecipeTypes.SMELTING
                .recipe("smelting/material/" + ingotPath + "_from_" + inputPath, ingot, input)
                .cookingTime(SMELT_TIME)
                .productionLines(productionLines)
                .unlockedBy(input)
                .save();
    }

    private static void registerMaceration(Material material, MaterialForm oreForm, ProductionLine... productionLines) {
        BuiltinOIRecipeTypes.MACERATOR.recipe(recipeId(material, "crude_dust_from", oreForm))
                .input(items().in(material, oreForm, 1))
                .output(items().out(material, BuiltinOIMaterialForms.CRUDE_DUST, 2))
                .tickInput(energy().in(ENERGY_PER_TICK))
                .duration(MACERATE_TIME)
                .productionLines(productionLines)
                .save();
    }

    private static ItemRecipeCapability items() {
        return BuiltinOIResourceIntegrations.ITEM.recipeCapability();
    }

    private static FluidRecipeCapability fluids() {
        return BuiltinOIResourceIntegrations.FLUID.recipeCapability();
    }

    private static ScalarRecipeCapability energy() {
        return BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
    }

    private static ScalarRecipeCapability advancedEnergy() {
        return BuiltinOIResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
    }

    private static ScalarRecipeCapability heat() {
        return BuiltinOIResourceIntegrations.HEAT.recipeCapability();
    }

    private static Supplier<Fluid> fluid(Material material, MaterialForm form) {
        return MaterialHelper.materialFluidSupplier(material, form);
    }

    private static String recipeId(Material material, String action) {
        return material.id().getPath() + "_" + action;
    }

    private static String recipeId(Material material, String action, MaterialForm inputForm) {
        return material.id().getPath() + "_" + action + "_" + inputForm.id().getPath();
    }
}
