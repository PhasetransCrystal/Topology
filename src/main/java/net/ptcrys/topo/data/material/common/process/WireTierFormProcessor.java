package net.ptcrys.topo.data.material.common.process;

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

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/** Wire-tier conversions for the wire mill: ingot draw to 1x, then adjacent 2:1 bundling. */
public final class WireTierFormProcessor extends MaterialPostProcessor {

    private static final int DEFAULT_DURATION = 100;
    private static final long ENERGY_PER_TICK = 30;

    public WireTierFormProcessor(Identifier id) {
        super(
                id,
                BuiltinTopoMaterialForms.INGOT,
                BuiltinTopoMaterialForms.WIRE_1X,
                BuiltinTopoMaterialForms.WIRE_2X,
                BuiltinTopoMaterialForms.WIRE_4X,
                BuiltinTopoMaterialForms.WIRE_8X,
                BuiltinTopoMaterialForms.WIRE_16X);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        registerDraw(material);
        registerBundle(material, BuiltinTopoMaterialForms.WIRE_1X, BuiltinTopoMaterialForms.WIRE_2X);
        registerBundle(material, BuiltinTopoMaterialForms.WIRE_2X, BuiltinTopoMaterialForms.WIRE_4X);
        registerBundle(material, BuiltinTopoMaterialForms.WIRE_4X, BuiltinTopoMaterialForms.WIRE_8X);
        registerBundle(material, BuiltinTopoMaterialForms.WIRE_8X, BuiltinTopoMaterialForms.WIRE_16X);
    }

    private static void registerDraw(Material material) {
        register(
                material,
                BuiltinTopoMaterialForms.INGOT,
                1,
                BuiltinTopoMaterialForms.WIRE_1X,
                2,
                BuiltinTopoProcessDies.TEMPLATE_WIRE::get);
    }

    private static void registerBundle(Material material, MaterialForm inputForm, MaterialForm outputForm) {
        register(material, inputForm, 2, outputForm, 1, null);
    }

    private static void register(
                                 Material material,
                                 MaterialForm inputForm,
                                 int inputCount,
                                 MaterialForm outputForm,
                                 int outputCount,
                                 Supplier<? extends ItemLike> template) {
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        var recipe = BuiltinTopoRecipeTypes.WIRE_MILL.recipe(recipeId(material, outputForm, inputForm))
                .input(items.in(material, inputForm, inputCount));
        if (template != null) {
            recipe.input(items.unconsumedInput(template));
        }
        recipe.output(items.out(material, outputForm, outputCount))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(DEFAULT_DURATION)
                .productionLine(BuiltinTopoProductionLines.WIRE_TIER)
                .save();
    }

    private static String recipeId(Material material, MaterialForm outputForm, MaterialForm inputForm) {
        String materialPath = material.id().getPath();
        return itemName(materialPath, outputForm) + "_from_" + itemName(materialPath, inputForm);
    }

    private static String itemName(String materialPath, MaterialForm form) {
        return String.format(form.strategy().registryPath(), materialPath);
    }
}
