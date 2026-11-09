package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.world.level.material.Fluids;

/** 氢氧化钠生产线的盐水、电解和蒸发配方。 */
public final class BuiltinTopoSodiumHydroxideProductionLineRecipes {

    private BuiltinTopoSodiumHydroxideProductionLineRecipes() {}

    public static void init() {
        var fluids = BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
        var items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        BuiltinRecipeTypes.MIXING_TANK.recipe("brine_from_salt")
                .productionLines(BuiltinTopoProductionLines.sodiumHydroxideConsumers())
                .input(items.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.SALT, BuiltinMaterialForms.DUST), 1))
                .input(fluids.in(Fluids.WATER, 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.BRINE, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(20))
                .duration(100)
                .save();

        BuiltinRecipeTypes.ELECTROLYZER.recipe("sodium_hydroxide_solution_from_brine")
                .productionLines(BuiltinTopoProductionLines.sodiumHydroxideConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.BRINE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.CHLORINE, BuiltinMaterialForms.GAS), 500))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.HYDROGEN, BuiltinMaterialForms.GAS), 500))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SODIUM_HYDROXIDE, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability().in(2))
                .duration(180)
                .save();

        BuiltinRecipeTypes.EVAPORATOR.recipe("sodium_hydroxide_dust_from_solution")
                .productionLines(BuiltinTopoProductionLines.sodiumHydroxideConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SODIUM_HYDROXIDE, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(items.out(MaterialHelper.materialItemSupplier(BuiltinMaterials.SODIUM_HYDROXIDE, BuiltinMaterialForms.DUST), 1))
                .tickInput(BuiltinTopoResourceIntegrations.HEAT.recipeCapability().in(10))
                .duration(120)
                .save();
    }
}
