package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.hook.EquipmentPlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.helper.IdHelper;

import java.util.List;

/** Owns equipment registration steps and their freeze fences. */
public final class EquipmentRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<EquipmentPlugin, EquipmentDomainRegistration> KINDS = RegistrationPoint.of(IdHelper.id("equipment/kinds"), EquipmentPlugin.class, EquipmentDomainRegistration.DOMAIN, EquipmentPlugin::registerEquipmentKinds);

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, EquipmentRegistrationModule.KINDS, List.of(MaterialRegistrationModule.FOLLOW_UPS.id()), context, () -> {
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
