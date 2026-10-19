package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.hook.MachinePlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.component.AttachmentTypes;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleRegistry;
import net.ptcrys.topo.api.machine.multiblock.ui.PropertyDisplayRegistry;
import net.ptcrys.topo.api.machine.render.MachineRenderRegistry;
import net.ptcrys.topo.api.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.api.machine.ui.MachineUiIcons;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategies;
import net.ptcrys.topo.api.pipe.Pipes;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;
import net.ptcrys.topo.api.recipe.common.RecipePreviewPlans;

import java.util.List;

/** Owns machine registration steps and their freeze fences. */
public final class MachineRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<MachinePlugin, MachineDomainRegistration> FOUNDATION = RegistrationPoint.of("topo:machine/foundation", MachinePlugin.class, MachineDomainRegistration.DOMAIN, MachinePlugin::registerMachineFoundation);
    static final RegistrationPoint<MachinePlugin, MachineDomainRegistration> MACHINES = RegistrationPoint.of("topo:machine/machines", MachinePlugin.class, MachineDomainRegistration.DOMAIN, MachinePlugin::registerMachines);
    static final RegistrationPoint<MachinePlugin, MachineDomainRegistration> FOLLOW_UPS = RegistrationPoint.of("topo:machine/follow_ups", MachinePlugin.class, MachineDomainRegistration.DOMAIN, MachinePlugin::registerMachineFollowUps);

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, MachineRegistrationModule.FOUNDATION, List.of(EquipmentRegistrationModule.KINDS.id()), context, () -> {
            MachineResourceTypes.freeze();
            MachineUiIcons.freeze();
            MachineRenderRegistry.freeze();
            PartRoleRegistry.freeze();
            PropertyDisplayRegistry.freeze();
            AttachmentTypes.freeze();
        });
        plan.point(Phase.CONTENT, MachineRegistrationModule.MACHINES, List.of(MachineRegistrationModule.FOUNDATION.id()), context, () -> {
            Machines.freeze();
            Machines.registerBlocksAndBlockEntity(Topology.REGISTRY);
            TopoRecipeTypes.installPreviewPlans(RecipePreviewPlans::canonical);
        });
        plan.point(Phase.CONTENT, MachineRegistrationModule.FOLLOW_UPS, List.of(MachineRegistrationModule.MACHINES.id()), context, () -> {
            PipeDistributionStrategies.freeze();
            Pipes.freeze();
            Pipes.registerBlocks(Topology.REGISTRY);
        });
    }

    @Override
    public void attach(net.neoforged.bus.api.IEventBus bus) {
        Machines.registerResourceCapabilities(bus);
    }
}
