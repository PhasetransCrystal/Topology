package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public final class FluidResourcePort extends ResourcePort<FluidStack, FluidResource> {

    public static final ComponentKey<FluidResourcePort> FLUID_INPUT_1 = ComponentKey.oi("fluid_input_1", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_INPUT_2 = ComponentKey.oi("fluid_input_2", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_INPUT_3 = ComponentKey.oi("fluid_input_3", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_INPUT_4 = ComponentKey.oi("fluid_input_4", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_OUTPUT_1 = ComponentKey.oi("fluid_output_1", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_OUTPUT_2 = ComponentKey.oi("fluid_output_2", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_OUTPUT_3 = ComponentKey.oi("fluid_output_3", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_OUTPUT_4 = ComponentKey.oi("fluid_output_4", FluidResourcePort.class);
    public static final ComponentKey<FluidResourcePort> FLUID_STORAGE = ComponentKey.oi("fluid_storage", FluidResourcePort.class);

    private FluidResourcePort(
                              ComponentContext<FluidResourcePort> context,
                              FluidResourcePortMetadata metadata) {
        this(
                context,
                new ObservableFluidResourceHandler(
                        metadata.tanks(),
                        metadata.capacity(),
                        metadata::accepts),
                metadata);
    }

    private FluidResourcePort(
                              ComponentContext<FluidResourcePort> context,
                              ObservableFluidResourceHandler handler,
                              FluidResourcePortMetadata metadata) {
        super(
                context,
                BuiltinOIResourceIntegrations.FLUID.resourceType(),
                handler,
                metadata);
        handler.setOnChanged(this::onStorageChanged);
    }

    /**
     * Port with a caller-supplied {@link PortAccess} — side-restricted or otherwise
     * customized ports compose the policy here (e.g. {@code PortAccess.input(UP)}); chain
     * {@link PartRoleMount#role} to additionally expose it as a multiblock part port.
     */
    public static PartRoleMount<FluidResourcePort> mount(
                                                         ComponentKey<FluidResourcePort> key,
                                                         int tanks,
                                                         int capacity,
                                                         PortAccess policy) {
        return mount(key, tanks, capacity, policy, null);
    }

    public static PartRoleMount<FluidResourcePort> mount(
                                                         ComponentKey<FluidResourcePort> key,
                                                         int tanks,
                                                         int capacity,
                                                         PortAccess policy,
                                                         @Nullable Predicate<Resource> resourceFilter) {
        FluidResourcePortMetadata metadata = new FluidResourcePortMetadata(tanks, capacity, policy, resourceFilter);
        return new PartRoleMount<>(
                key.mount(context -> new FluidResourcePort(context, metadata), metadata));
    }

    public static PartRoleMount<FluidResourcePort> input(
                                                         ComponentKey<FluidResourcePort> key,
                                                         int tanks,
                                                         int capacity) {
        return mount(key, tanks, capacity, PortAccess.input());
    }

    public static PartRoleMount<FluidResourcePort> output(
                                                          ComponentKey<FluidResourcePort> key,
                                                          int tanks,
                                                          int capacity) {
        return mount(key, tanks, capacity, PortAccess.output());
    }

    public static PartRoleMount<FluidResourcePort> storage(
                                                           ComponentKey<FluidResourcePort> key,
                                                           int tanks,
                                                           int capacity) {
        return mount(key, tanks, capacity, PortAccess.storage());
    }
}
