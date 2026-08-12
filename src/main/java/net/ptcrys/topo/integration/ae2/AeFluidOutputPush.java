package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

public final class AeFluidOutputPush extends AeResourceOutputPush<FluidResource> {

    public static final ComponentKey<AeFluidOutputPush> AE_FLUID_OUTPUT_PUSH = ComponentKey.id("ae_fluid_output_push", AeFluidOutputPush.class);

    private AeFluidOutputPush(ComponentContext<AeFluidOutputPush> context) {
        super(context, AeFluidBufferPort.AE_FLUID_BUFFER_PORT);
    }

    public static ComponentMount<AeFluidOutputPush> mount() {
        return AE_FLUID_OUTPUT_PUSH.mount(AeFluidOutputPush::new);
    }
}
