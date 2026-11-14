package net.ptcrys.topodemo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.machine.resource.ResourcePort;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.machine.ui.MachineUiContribution;
import net.ptcrys.topo.api.machine.ui.PortUiHighlight;
import net.ptcrys.topo.api.machine.ui.ResourceBar;
import net.ptcrys.topo.integration.jade.ResourceBarProvider;
import net.ptcrys.topodemo.api.machine.DemoComponentKeys;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topodemo.data.recipe.common.ScalarRecipeCapability;

import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.function.Consumer;

/**
 * Creative scalar buffer: a {@link CreativeScalarResourceHandler} ({@link Long#MAX_VALUE} capacity,
 * starts empty, tracks a real long amount) wrapped in the standard {@link ResourcePort}
 * machinery, so it exposes the scalar capability, the configurable side-IO card and a resource bar
 * exactly like a real {@link ScalarResourcePort} — only the capacity is effectively unbounded.
 */
public final class CreativeScalarResourcePort
                                              extends ResourcePort<ResourceStack<ScalarResource>, ScalarResource> implements ResourceBarProvider {

    public static final ComponentKey<CreativeScalarResourcePort> CREATIVE_ENERGY_STORAGE = DemoComponentKeys.id("creative_energy_storage", CreativeScalarResourcePort.class).service(ResourceBarProvider.KEY, (port, ignored) -> port);

    private CreativeScalarResourcePort(
                                       ComponentContext<CreativeScalarResourcePort> context,
                                       ScalarResourcePortMetadata metadata) {
        this(context, new CreativeScalarResourceHandler(metadata.resource()), metadata);
    }

    private CreativeScalarResourcePort(
                                       ComponentContext<CreativeScalarResourcePort> context,
                                       CreativeScalarResourceHandler handler,
                                       ScalarResourcePortMetadata metadata) {
        super(context, metadata.integration().resourceType(), handler, metadata);
        handler.setOnChanged(this::onStorageChanged);
    }

    /** 将本端口的实时存量提供给框架资源条视图。 */
    @Override
    public void collect(Consumer<ResourceBarProvider.Bar> output) {
        output.accept(new ResourceBarProvider.Bar(
                resource().nameLang(), resource().color(), storedAmount(), capacityAmount()));
    }

    public ScalarResource resource() {
        return ((ScalarResourcePortMetadata) metadata()).resource();
    }

    public long storedAmount() {
        return handler().getAmountAsLong(0);
    }

    public long capacityAmount() {
        return handler().getCapacityAsLong(0, resource());
    }

    /** Horizontal resource bar showing the (always full) buffer; mirrors {@link ScalarResourcePort}. */
    @Override
    public void collectMachineUi(MachineUiContribution contribution) {
        super.collectMachineUi(contribution);
        ScalarResource resource = resource();
        ResourceBar bar = MachineUiComponentTemplate.INSTANCE
                .createResourceBar(resource.displayName(), resource.color(), ResourceBar.Orientation.HORIZONTAL)
                .bindStorage(this::storedAmount, this::capacityAmount);
        PortUiHighlight.tag(bar, id());
        contribution.bottomStrip(
                "topo_resource_bar_" + id().getPath(),
                resource.displayName(),
                null,
                bar);
    }

    public static PartRoleMount<CreativeScalarResourcePort> mount(
                                                                  ComponentKey<CreativeScalarResourcePort> key,
                                                                  BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                                  PortAccess policy) {
        // Metadata capacity is unused (the handler reports Long.MAX); pass a positive sentinel for validity.
        ScalarResourcePortMetadata metadata = new ScalarResourcePortMetadata(integration, Integer.MAX_VALUE, policy);
        return new PartRoleMount<>(
                key.mount(context -> new CreativeScalarResourcePort(context, metadata), metadata));
    }
}
