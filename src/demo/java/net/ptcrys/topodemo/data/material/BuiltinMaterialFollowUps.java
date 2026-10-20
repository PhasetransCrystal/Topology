package net.ptcrys.topodemo.data.material;

import net.ptcrys.registrylib.util.DataIngredient;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;

import net.minecraft.data.recipes.RecipeCategory;

/** Recipe callbacks resolve items after registry binding; each form owns its tags. */
public final class BuiltinMaterialFollowUps {

    public static void init() {
        for (var material : BuiltinMaterials.METALS) {
            var ingot = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.INGOT);
            var dust = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST);
            TopoDemo.REGISTRY.addRecipeData(provider -> provider.smelting(
                    DataIngredient.items(dust), RecipeCategory.MISC, ingot, 0.1f));
        }
    }

    private BuiltinMaterialFollowUps() {}
}
