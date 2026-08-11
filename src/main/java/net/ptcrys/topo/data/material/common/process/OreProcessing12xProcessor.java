package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.resources.Identifier;

/** 12x ore processing: luminite-assisted resonant dust crystallization. */
public final class OreProcessing12xProcessor extends OreProcessingLineProcessor {

    public OreProcessing12xProcessor(Identifier id) {
        super(
                id,
                BuiltinTopoMaterialForms.ORE,
                BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                BuiltinTopoMaterialForms.CRUDE_DUST,
                BuiltinTopoMaterialForms.PURIFIED_DUST,
                BuiltinTopoMaterialForms.TINY_DUST,
                BuiltinTopoMaterialForms.SLURRY,
                BuiltinTopoMaterialForms.NEUTRALIZED_SLURRY,
                BuiltinTopoMaterialForms.HIGH_PURITY_DUST,
                BuiltinTopoMaterialForms.ACTIVATED_DUST,
                BuiltinTopoMaterialForms.RESONANT_DUST,
                BuiltinTopoMaterialForms.RARE_ELEMENT,
                BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinTopoProductionLines.registerOreProcessing12x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerAcidLeach(material, line);
        registerNeutralize(material, line);
        registerElectrolyze(material, line);
        registerActivate(material, line);
        registerCrystallize(material, line);
        registerTwelveTimesSmelt(material, line);
    }
}
