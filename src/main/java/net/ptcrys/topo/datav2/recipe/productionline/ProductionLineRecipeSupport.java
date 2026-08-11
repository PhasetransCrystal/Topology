package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

final class ProductionLineRecipeSupport {

    private ProductionLineRecipeSupport() {}

    static ItemRecipeCapability items() {
        return BuiltinOIResourceIntegrations.ITEM.recipeCapability();
    }

    static FluidRecipeCapability fluids() {
        return BuiltinOIResourceIntegrations.FLUID.recipeCapability();
    }

    static ScalarRecipeCapability energy() {
        return BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
    }

    static ScalarRecipeCapability advancedEnergy() {
        return BuiltinOIResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
    }

    static ScalarRecipeCapability heat() {
        return BuiltinOIResourceIntegrations.HEAT.recipeCapability();
    }

    static Supplier<Fluid> fluid(Material material, MaterialForm form) {
        return MaterialHelper.materialFluidSupplier(material, form);
    }
}
