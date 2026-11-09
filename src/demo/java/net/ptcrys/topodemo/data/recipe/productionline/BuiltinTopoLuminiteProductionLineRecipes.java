package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 辉光铜镍晶生产线中的研磨、化学和晶化配方。 */
public final class BuiltinTopoLuminiteProductionLineRecipes {

    private BuiltinTopoLuminiteProductionLineRecipes() {}

    public static void init() {
        fineGrind(BuiltinMaterials.COPPER);
        fineGrind(BuiltinMaterials.NICKEL);
        var items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        var fluids = BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
        var energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        var heat = BuiltinTopoResourceIntegrations.HEAT.recipeCapability();
        var advanced = BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability();

        BuiltinRecipeTypes.CHEMICAL_REACTOR.recipe("copper_sulfate_solution_from_copper_dust")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.COPPER, BuiltinMaterialForms.DUST), 2))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFURIC_ACID, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.COPPER_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.WASTE_GAS, BuiltinMaterialForms.GAS), 500))
                .tickInput(heat.in(10)).duration(160).save();

        BuiltinRecipeTypes.CHEMICAL_REACTOR.recipe("nickel_sulfate_solution_from_nickel_dust")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.NICKEL, BuiltinMaterialForms.DUST), 1))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFURIC_ACID, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.NICKEL_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.WASTE_GAS, BuiltinMaterialForms.GAS), 500))
                .tickInput(heat.in(10)).duration(160).save();

        BuiltinRecipeTypes.MIXING_TANK.recipe("copper_nickel_sulfate_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.COPPER_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.NICKEL_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.COPPER_NICKEL_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(energy.in(30)).duration(120).save();

        BuiltinRecipeTypes.CHEMICAL_REACTOR.recipe("copper_nickel_hydroxide_from_sulfate_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.COPPER_NICKEL_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .input(items.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.SODIUM_HYDROXIDE, BuiltinMaterialForms.DUST), 1))
                .output(items.out(MaterialHelper.materialItemSupplier(BuiltinMaterials.COPPER_NICKEL_HYDROXIDE, BuiltinMaterialForms.DUST), 1))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SODIUM_SULFATE, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(energy.in(30)).duration(160).save();

        BuiltinRecipeTypes.EVAPORATOR.recipe("copper_nickel_oxide_solution_from_oxide")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.COPPER_NICKEL_OXIDE, BuiltinMaterialForms.DUST), 1))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.ELECTROLYTE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.COPPER_NICKEL_OXIDE, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(heat.in(10)).duration(160).save();

        BuiltinRecipeTypes.ELECTRIC_FURNACE.recipe("copper_nickel_oxide_from_hydroxide")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.COPPER_NICKEL_HYDROXIDE, BuiltinMaterialForms.DUST), 1))
                .output(items.out(MaterialHelper.materialItemSupplier(BuiltinMaterials.COPPER_NICKEL_OXIDE, BuiltinMaterialForms.DUST), 1))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.STEAM, BuiltinMaterialForms.GAS), 500))
                .tickInput(energy.in(30)).duration(120).save();

        BuiltinRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER.recipe("luminite_solution_from_copper_nickel_oxide_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.COPPER_NICKEL_OXIDE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.LUMINITE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(advanced.in(2)).duration(200).save();

        BuiltinRecipeTypes.ELECTROLYZER.recipe("luminite_gem_from_luminite_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.LUMINITE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(items.out(MaterialHelper.materialItemSupplier(BuiltinMaterials.LUMINITE, BuiltinMaterialForms.GEM), 4))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(advanced.in(2)).duration(200).save();
    }

    private static void fineGrind(Material material) {
        var items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        BuiltinRecipeTypes.FINE_GRINDER.recipe(material.id().getPath() + "_dust_from_ingot")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items.in(MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.INGOT), 1))
                .output(items.out(MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST), 1))
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(30))
                .duration(100).save();
    }
}
