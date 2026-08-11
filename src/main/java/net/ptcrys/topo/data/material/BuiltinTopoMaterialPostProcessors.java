package net.ptcrys.topo.data.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.common.process.ManualForgeHammerProcessor;
import net.ptcrys.topo.data.material.common.process.MetalComponentFormProcessor;
import net.ptcrys.topo.data.material.common.process.MetalFormProcessor;
import net.ptcrys.topo.data.material.common.process.OreProcessing12xProcessor;
import net.ptcrys.topo.data.material.common.process.OreProcessing2xProcessor;
import net.ptcrys.topo.data.material.common.process.OreProcessing3xProcessor;
import net.ptcrys.topo.data.material.common.process.OreProcessing4xProcessor;
import net.ptcrys.topo.data.material.common.process.OreProcessing6xProcessor;
import net.ptcrys.topo.data.material.common.process.WireTierFormProcessor;

/** Builtin material post processors used by wildcard production lines. */
public final class BuiltinTopoMaterialPostProcessors {

    private static final MaterialDomainRegistration MATERIALS = OfficialTopoPlugin.INSTANCE.material();

    public static final MetalFormProcessor METAL_FORM_CONVERSIONS = MATERIALS.postProcessor(
            "metal_forms",
            new MetalFormProcessor(MATERIALS.id("metal_forms"), MATERIALS.registry()));
    public static final MetalComponentFormProcessor METAL_COMPONENT_FORMS = MATERIALS.postProcessor(
            "metal_component_forms",
            new MetalComponentFormProcessor(MATERIALS.id("metal_component_forms")));
    public static final ManualForgeHammerProcessor MANUAL_FORGE_HAMMER = MATERIALS.postProcessor(
            "manual_forge_hammer",
            new ManualForgeHammerProcessor(MATERIALS.id("manual_forge_hammer"), MATERIALS.registry()));
    public static final WireTierFormProcessor WIRE_TIER_FORMS = MATERIALS.postProcessor(
            "wire_tier_forms",
            new WireTierFormProcessor(MATERIALS.id("wire_tier_forms")));
    public static final OreProcessing2xProcessor ORE_PROCESSING_2X = MATERIALS.postProcessor(
            "ore_processing_2x",
            new OreProcessing2xProcessor(MATERIALS.id("ore_processing_2x")));
    public static final OreProcessing3xProcessor ORE_PROCESSING_3X = MATERIALS.postProcessor(
            "ore_processing_3x",
            new OreProcessing3xProcessor(MATERIALS.id("ore_processing_3x")));
    public static final OreProcessing4xProcessor ORE_PROCESSING_4X = MATERIALS.postProcessor(
            "ore_processing_4x",
            new OreProcessing4xProcessor(MATERIALS.id("ore_processing_4x")));
    public static final OreProcessing6xProcessor ORE_PROCESSING_6X = MATERIALS.postProcessor(
            "ore_processing_6x",
            new OreProcessing6xProcessor(MATERIALS.id("ore_processing_6x")));
    public static final OreProcessing12xProcessor ORE_PROCESSING_12X = MATERIALS.postProcessor(
            "ore_processing_12x",
            new OreProcessing12xProcessor(MATERIALS.id("ore_processing_12x")));

    private BuiltinTopoMaterialPostProcessors() {}

    public static void init() {}
}
