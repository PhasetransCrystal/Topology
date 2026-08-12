package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePortMetadata;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import com.google.common.primitives.Ints;

public final class AeStockingFluidPort extends AeStockingPort<FluidResource> {

    public static final ComponentKey<AeStockingFluidPort> AE_STOCKING_FLUID_PORT = ComponentKey.id("ae_stocking_fluid_port", AeStockingFluidPort.class);

    /** Preview capacity for JEI slot footprints; live capacity is each slot's configured target. */
    private static final int PREVIEW_TANK_CAPACITY = Ints.saturatedCast(64_000L);

    private AeStockingFluidPort(ComponentContext<AeStockingFluidPort> context, int slots) {
        super(
                context,
                FluidResource.class,
                FluidResource.EMPTY,
                slots,
                BuiltinTopoResourceIntegrations.FLUID.resourceType());
    }

    public static PartRoleMount<AeStockingFluidPort> mount(int slots) {
        FluidResourcePortMetadata metadata = new FluidResourcePortMetadata(
                slots, PREVIEW_TANK_CAPACITY, AeStockingItemPort.stockingPolicy());
        return new PartRoleMount<>(
                AE_STOCKING_FLUID_PORT.mount(context -> new AeStockingFluidPort(context, slots), metadata));
    }
}
