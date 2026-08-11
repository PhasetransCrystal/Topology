package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

public final class AeFluidInputSync extends AeResourceInputSync<FluidResource> {

    public static final ComponentKey<AeFluidInputSync> AE_FLUID_INPUT_SYNC = ComponentKey.oi("ae_fluid_input_sync", AeFluidInputSync.class);

    private AeFluidInputSync(
                             ComponentContext<AeFluidInputSync> context,
                             int slots,
                             ComponentKey<FluidResourcePort> portKey) {
        super(context, FluidResource.class, slots, portKey);
    }

    /** Syncs {@code slots} configured tanks of the local fluid port at {@code portKey} from the AE grid. */
    public static ComponentMount<AeFluidInputSync> mount(int slots, ComponentKey<FluidResourcePort> portKey) {
        return AE_FLUID_INPUT_SYNC.mount(context -> new AeFluidInputSync(context, slots, portKey));
    }
}
