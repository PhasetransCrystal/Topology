package net.ptcrys.topodemo.data.material;

import net.ptcrys.registrylib.util.DataIngredient;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;

import net.minecraft.data.recipes.RecipeCategory;

import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.INGOT;

/** Recipe callbacks resolve items after registry binding; each form owns its tags. */
public final class BuiltinMaterialFollowUps {

    public static void init() {
        for (var material : MaterialRegistry.registered()) {
            if (!INGOT.present().test(material) || !material.forms().contains(BuiltinMaterialForms.INGOT) || !material.forms().contains(BuiltinMaterialForms.DUST)) continue;
            var ingot = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.INGOT);
            var dust = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST);
            TopoDemo.REGISTRY.addRecipeData(provider -> provider.smelting(
                    DataIngredient.items(dust), RecipeCategory.MISC, ingot, 0.1f));
        }
    }

    private BuiltinMaterialFollowUps() {}
}
