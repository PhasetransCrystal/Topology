package net.ptcrys.topodemo.data.material;

import net.ptcrys.registrylib.util.DataIngredient;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.List;

/** Forms exist here; suppliers remain lazy until their tag/recipe datagen callbacks execute. */
public final class BuiltinMaterialFollowUps {

    public static void init() {
        for (var material : List.of(BuiltinMaterials.COPPER, BuiltinMaterials.TIN, BuiltinMaterials.BRONZE)) {
            var ingot = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.INGOT);
            var dust = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST);
            TopoDemo.REGISTRY.itemTags().addSuppliers(TagKey.create(Registries.ITEM, Identifier.parse("c:ingots")), ingot);
            TopoDemo.REGISTRY.itemTags().addSuppliers(TagKey.create(Registries.ITEM, Identifier.parse("c:dusts")), dust);
            TopoDemo.REGISTRY.itemTags().addSuppliers(TagKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("c", "ingots/" + material.id().getPath())), ingot);
            TopoDemo.REGISTRY.itemTags().addSuppliers(TagKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("c", "dusts/" + material.id().getPath())), dust);
            TopoDemo.REGISTRY.addRecipeData(provider -> provider.smelting(
                    DataIngredient.items(dust), RecipeCategory.MISC, ingot, 0.1f));
        }
    }

    private BuiltinMaterialFollowUps() {}
}
