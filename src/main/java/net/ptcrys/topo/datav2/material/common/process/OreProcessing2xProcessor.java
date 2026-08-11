package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;

import net.minecraft.resources.Identifier;

/** 2x ore processing: ore to crude dust, then crude dust to ingots. */
public final class OreProcessing2xProcessor extends OreProcessingLineProcessor {

    public OreProcessing2xProcessor(Identifier id) {
        super(
                id,
                BuiltinOIMaterialForms.ORE,
                BuiltinOIMaterialForms.DEEPSLATE_ORE,
                BuiltinOIMaterialForms.CRUDE_DUST,
                BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinOIProductionLines.registerOreProcessing2x(material);
        registerOreToCrudeDust(material, line);
        registerTwoTimesSmelt(material, line);
    }
}
