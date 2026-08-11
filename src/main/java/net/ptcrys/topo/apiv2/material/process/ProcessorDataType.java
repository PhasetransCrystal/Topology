package net.ptcrys.topo.apiv2.material.process;

import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

/**
 * The material data type paired 1:1 with a registered {@link MaterialPostProcessor}. The data value
 * is the processor itself: declaring {@link #activation()} on a material activates the processor,
 * absence deactivates it. Instances are created only by {@link MaterialPostProcessorRegistry}, so a
 * processor and its data type can never exist independently.
 */
public final class ProcessorDataType extends MaterialDataType<MaterialPostProcessor> {

    private final MaterialPostProcessor processor;

    ProcessorDataType(Identifier id, MaterialPostProcessor processor) {
        super(id);
        this.processor = processor;
    }

    public MaterialPostProcessor processor() {
        return processor;
    }

    public MaterialDataUse<MaterialPostProcessor> activation() {
        return use(processor);
    }
}
