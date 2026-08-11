package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.resources.Identifier;

/** 4x ore processing: acid leaching into slurry and slurry smelting. */
public final class OreProcessing4xProcessor extends OreProcessingLineProcessor {

    public OreProcessing4xProcessor(Identifier id) {
        super(
                id,
                BuiltinTopoMaterialForms.ORE,
                BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                BuiltinTopoMaterialForms.CRUDE_DUST,
                BuiltinTopoMaterialForms.PURIFIED_DUST,
                BuiltinTopoMaterialForms.TINY_DUST,
                BuiltinTopoMaterialForms.SLURRY,
                BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinTopoProductionLines.registerOreProcessing4x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerAcidLeach(material, line);
        registerFourTimesSmelt(material, line);
    }
}
