package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialRegistry;

import java.util.List;

/** Owns equipment registration steps and their freeze fences. */
final class EquipmentRegistrationModule implements RegistrationModule {

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, EquipmentDomainRegistration.KINDS, List.of(MaterialDomainRegistration.FOLLOW_UPS.id()), context, () -> {
            EquipmentRegistry.freeze();
            for (Material material : MaterialRegistry.registered()) {
                MaterialContentContext materialContext = new MaterialContentContext(context.registry(material.id().getNamespace()), material);
                for (Equipment equipment : EquipmentRegistry.registered()) {
                    if (equipment.strategy().appliesTo(material)) equipment.strategy().register(materialContext, equipment);
                }
            }
        });
    }
}
