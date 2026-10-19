package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;
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
final class MachineRegistrationModule implements RegistrationModule {

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, MachineDomainRegistration.FOUNDATION, List.of(EquipmentDomainRegistration.KINDS.id()), context, () -> {
            MachineResourceTypes.freeze();
            MachineUiIcons.freeze();
            MachineRenderRegistry.freeze();
            PartRoleRegistry.freeze();
            PropertyDisplayRegistry.freeze();
            AttachmentTypes.freeze();
        });
        plan.point(Phase.CONTENT, MachineDomainRegistration.MACHINES, List.of(MachineDomainRegistration.FOUNDATION.id()), context, () -> {
            Machines.freeze();
            Machines.registerBlocksAndBlockEntity(Topology.REGISTRY);
            TopoRecipeTypes.installPreviewPlans(RecipePreviewPlans::canonical);
        });
        plan.point(Phase.CONTENT, MachineDomainRegistration.FOLLOW_UPS, List.of(MachineDomainRegistration.MACHINES.id()), context, () -> {
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
