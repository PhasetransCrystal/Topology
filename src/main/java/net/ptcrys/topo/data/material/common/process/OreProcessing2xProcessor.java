package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.resources.Identifier;

/** 2x ore processing: ore to crude dust, then crude dust to ingots. */
public final class OreProcessing2xProcessor extends OreProcessingLineProcessor {

    public OreProcessing2xProcessor(Identifier id) {
        super(
                id,
                BuiltinTopoMaterialForms.ORE,
                BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                BuiltinTopoMaterialForms.CRUDE_DUST,
                BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinTopoProductionLines.registerOreProcessing2x(material);
        registerOreToCrudeDust(material, line);
        registerTwoTimesSmelt(material, line);
    }
}
