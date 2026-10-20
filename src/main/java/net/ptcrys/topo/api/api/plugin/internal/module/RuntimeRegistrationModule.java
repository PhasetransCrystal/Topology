package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.topo.api.api.builtin.RuntimeDomainRegistration;
import net.ptcrys.topo.api.api.plugin.hook.RuntimePlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.pipe.ui.PipeSpecTooltips;

import java.util.List;

/** Resolve bound game objects in deferred work, then close shared runtime tables at load complete. */
public final class RuntimeRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<RuntimePlugin, RuntimeDomainRegistration> BINDINGS = RegistrationPoint.of("topo:runtime/bindings", RuntimePlugin.class, RuntimeDomainRegistration.DOMAIN, RuntimePlugin::registerRuntime);

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.step(Phase.RUNTIME, "topo:runtime/builtins", List.of(), () -> {
            PipeSpecTooltips.registerProviders();
            for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
                MachineItemBehavior behavior = record.equipment().strategy().machineBehavior();
                if (behavior != null) MachineItemBehaviors.register(record.entry().get(), behavior);
            }
        });
        plan.point(Phase.RUNTIME, RuntimeRegistrationModule.BINDINGS, List.of("topo:runtime/builtins"), context, () -> {});
        plan.step(Phase.READY, "topo:runtime/freeze", List.of(RuntimeRegistrationModule.BINDINGS.id()), () -> {
            ItemTooltipUis.freeze();
            MachineItemBehaviors.freeze();
        });
    }
}
