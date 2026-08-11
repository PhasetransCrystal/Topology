package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;

import net.minecraft.resources.Identifier;

/** 4x ore processing: acid leaching into slurry and slurry smelting. */
public final class OreProcessing4xProcessor extends OreProcessingLineProcessor {

    public OreProcessing4xProcessor(Identifier id) {
        super(
                id,
                BuiltinOIMaterialForms.ORE,
                BuiltinOIMaterialForms.DEEPSLATE_ORE,
                BuiltinOIMaterialForms.CRUDE_DUST,
                BuiltinOIMaterialForms.PURIFIED_DUST,
                BuiltinOIMaterialForms.TINY_DUST,
                BuiltinOIMaterialForms.SLURRY,
                BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinOIProductionLines.registerOreProcessing4x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerAcidLeach(material, line);
        registerFourTimesSmelt(material, line);
    }
}
