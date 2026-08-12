package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePortMetadata;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import com.google.common.primitives.Ints;

public final class AeFluidBufferPort extends AeBufferPort<FluidResource> {

    public static final ComponentKey<AeFluidBufferPort> AE_FLUID_BUFFER_PORT = ComponentKey.id("ae_fluid_buffer_port", AeFluidBufferPort.class);

    /** Preview capacity for JEI slot footprints; live capacity is saturating per kind. */
    private static final int PREVIEW_TANK_CAPACITY = Ints.saturatedCast(64_000L);

    private AeFluidBufferPort(ComponentContext<AeFluidBufferPort> context, int maxKinds) {
        super(
                context,
                FluidResource.class,
                FluidResource.EMPTY,
                maxKinds,
                BuiltinTopoResourceIntegrations.FLUID.resourceType());
    }

    public static PartRoleMount<AeFluidBufferPort> mount(int maxKinds) {
        FluidResourcePortMetadata metadata = new FluidResourcePortMetadata(
                maxKinds, PREVIEW_TANK_CAPACITY, AeItemBufferPort.bufferPolicy());
        return new PartRoleMount<>(
                AE_FLUID_BUFFER_PORT.mount(context -> new AeFluidBufferPort(context, maxKinds), metadata));
    }
}
