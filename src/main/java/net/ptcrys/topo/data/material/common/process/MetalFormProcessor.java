package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Common metal conversions: storage blocks, pinch dust packing, fine grinding, and vanilla cooking.
 *
 * <p>
 * Runs during RegisterEvent HIGHEST — must not resolve {@code BuiltInRegistries.ITEM} here;
 * use deferred suppliers and {@link MaterialHelper#itemPath}.
 */
public final class MetalFormProcessor extends MaterialPostProcessor {

    private static final int GRID_3X3_COUNT = 9;
    private static final int FINE_GRIND_TIME = 100;
    private static final long ENERGY_PER_TICK = 30;
    private static final float COOKING_EXPERIENCE = 0.7F;
    private static final int SMELTING_TIME = 200;
    private static final int BLASTING_TIME = 100;

    public MetalFormProcessor(Identifier id, RegistryCore core) {
        super(
                id,
                BuiltinTopoMaterialForms.INGOT,
                BuiltinTopoMaterialForms.DUST,
                BuiltinTopoMaterialForms.BLOCK,
                BuiltinTopoMaterialForms.NUGGET,
                BuiltinTopoMaterialForms.TINY_DUST);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        registerCraftingAndCooking(material);
        registerIngotFineGrinding(material);
    }

    private static void registerCraftingAndCooking(Material material) {
        registerStorageBlockConversion(material);
        registerNineToOne(
                material,
                BuiltinTopoMaterialForms.TINY_DUST,
                BuiltinTopoMaterialForms.DUST,
                "shape/material/",
                "shapeless/material/");
        registerCooking(material);
    }

    private static void registerStorageBlockConversion(Material material) {
        if (MaterialHelper.isMinecraftNamespace(material, BuiltinTopoMaterialForms.INGOT) && MaterialHelper.isMinecraftNamespace(material, BuiltinTopoMaterialForms.BLOCK)) {
            return;
        }
        registerNineToOne(
                material,
                BuiltinTopoMaterialForms.INGOT,
                BuiltinTopoMaterialForms.BLOCK,
                "shape/material/",
                "shapeless/material/");
    }

    private static void registerNineToOne(
                                          Material material,
                                          MaterialForm unitForm,
                                          MaterialForm packedForm,
                                          String packedPrefix,
                                          String unitPrefix) {
        Supplier<Item> unit = MaterialHelper.materialItemSupplier(material, unitForm);
        Supplier<Item> packed = MaterialHelper.materialItemSupplier(material, packedForm);
        String unitPath = MaterialHelper.itemPath(material, unitForm);
        String packedPath = MaterialHelper.itemPath(material, packedForm);
        BuiltinTopoRecipeTypes.CRAFTING_SHAPED
                .recipe(packedPrefix + packedPath + "_from_" + unitPath, packed)
                .pattern("XXX", "XXX", "XXX")
                .define('X', unit)
                .unlockedBy(unit)
                .save();
        BuiltinTopoRecipeTypes.CRAFTING_SHAPELESS
                .recipe(unitPrefix + unitPath + "_from_" + packedPath, unit, GRID_3X3_COUNT)
                .requires(packed)
                .unlockedBy(packed)
                .save();
    }

    private static void registerCooking(Material material) {
        Supplier<Item> dust = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.DUST);
        Supplier<Item> ingot = MaterialHelper.materialItemSupplier(material, BuiltinTopoMaterialForms.INGOT);
        String dustPath = MaterialHelper.itemPath(material, BuiltinTopoMaterialForms.DUST);
        String ingotPath = MaterialHelper.itemPath(material, BuiltinTopoMaterialForms.INGOT);
        BuiltinTopoRecipeTypes.SMELTING
                .recipe("smelting/material/" + ingotPath + "_from_" + dustPath, ingot, dust)
                .experience(COOKING_EXPERIENCE)
                .cookingTime(SMELTING_TIME)
                .unlockedBy(dust)
                .save();
        BuiltinTopoRecipeTypes.BLASTING
                .recipe("blasting/material/" + ingotPath + "_from_" + dustPath, ingot, dust)
                .experience(COOKING_EXPERIENCE)
                .cookingTime(BLASTING_TIME)
                .unlockedBy(dust)
                .save();
    }

    private void registerIngotFineGrinding(Material material) {
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        BuiltinTopoRecipeTypes.FINE_GRINDER.recipe(material.id().getPath() + "_dust_from_ingot")
                .input(items.in(material, BuiltinTopoMaterialForms.INGOT, 1))
                .output(items.out(material, BuiltinTopoMaterialForms.DUST, 1))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(FINE_GRIND_TIME)
                .productionLine(BuiltinTopoProductionLines.FINE_GRINDING)
                .save();
    }
}
