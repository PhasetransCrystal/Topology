package net.ptcrys.topo.api.material.process;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Material post-processor table (query + freeze). Product writes only via
 * {@link MaterialDomainRegistration#postProcessor}.
 */
public final class MaterialPostProcessorRegistry {

    private static final FreezableStrategyRegistry<Identifier, MaterialPostProcessor, MaterialPostProcessor> REGISTRY = FreezableStrategyRegistry.create("material post-processors");

    private MaterialPostProcessorRegistry() {}

    /**
     * Single write entry: registers the processor and its activation {@link ProcessorDataType}.
     */
    public static <P extends MaterialPostProcessor> P begin(Identifier id, P processor) {
        if (!id.equals(processor.id())) {
            throw new IllegalArgumentException(
                    "material post-processor id mismatch: registry key " + id + " != processor id " + processor.id());
        }
        ProcessorDataType dataType = new ProcessorDataType(id, processor);
        MaterialDataRegistry.begin(id, dataType, (material, data) -> processor.validate(material));
        processor.bindDataType(dataType);
        REGISTRY.register(id, processor, processor);
        return processor;
    }

    public static MaterialPostProcessor require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<MaterialPostProcessor> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
