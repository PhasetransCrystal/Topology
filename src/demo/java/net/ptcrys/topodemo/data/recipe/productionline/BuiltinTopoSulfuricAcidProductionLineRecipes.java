package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.world.level.material.Fluids;

/** 硫酸生产线的三步配方。 */
public final class BuiltinTopoSulfuricAcidProductionLineRecipes {

    private BuiltinTopoSulfuricAcidProductionLineRecipes() {}

    public static void init() {
        var fluids = BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
        BuiltinRecipeTypes.INDUSTRIAL_COMBUSTION_FURNACE.recipe("sulfur_dioxide_from_sulfur")
                .productionLines(BuiltinTopoProductionLines.sulfuricAcidConsumers())
                .input(BuiltinTopoResourceIntegrations.ITEM.recipeCapability()
                        .in(MaterialHelper.materialItemSupplier(BuiltinMaterials.SULFUR, BuiltinMaterialForms.DUST), 1))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.AIR, BuiltinMaterialForms.GAS), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFUR_DIOXIDE, BuiltinMaterialForms.GAS), 1_000))
                .tickInput(BuiltinTopoResourceIntegrations.HEAT.recipeCapability().in(10))
                .duration(120)
                .save();

        BuiltinRecipeTypes.CHEMICAL_REACTOR.recipe("sulfur_trioxide_from_sulfur_dioxide")
                .productionLines(BuiltinTopoProductionLines.sulfuricAcidConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFUR_DIOXIDE, BuiltinMaterialForms.GAS), 1_000))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.AIR, BuiltinMaterialForms.GAS), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFUR_TRIOXIDE, BuiltinMaterialForms.GAS), 1_000))
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(30))
                .duration(160)
                .save();

        BuiltinRecipeTypes.MIXING_TANK.recipe("sulfuric_acid_from_sulfur_trioxide")
                .productionLines(BuiltinTopoProductionLines.sulfuricAcidConsumers())
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFUR_TRIOXIDE, BuiltinMaterialForms.GAS), 1_000))
                .input(fluids.in(Fluids.WATER, 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFURIC_ACID, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(30))
                .duration(120)
                .save();
    }
}
