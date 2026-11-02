package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.registrylib.util.entry.ItemEntry;
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

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns equipment registration steps and their freeze fences. */
public final class EquipmentRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<EquipmentPlugin, EquipmentDomainRegistration> KINDS = RegistrationPoint.of(IdHelper.id("equipment/kinds"), EquipmentPlugin.class, EquipmentDomainRegistration.DOMAIN, EquipmentPlugin::registerEquipmentKinds);

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, EquipmentRegistrationModule.KINDS, List.of(MaterialRegistrationModule.FOLLOW_UPS.id()), context, () -> {
            EquipmentRegistry.freeze();
            List<EquipmentRegistry.EquipmentItemRecord> records = new ArrayList<>();
            for (Material material : MaterialRegistry.registered()) {
                MaterialContentContext materialContext = new MaterialContentContext(context.registry(material.id().getNamespace()), material);
                for (Equipment equipment : EquipmentRegistry.registered()) {
                    if (!equipment.strategy().appliesTo(material)) {
                        continue;
                    }
                    ItemEntry<Item> entry = Objects.requireNonNull(
                            equipment.strategy().register(materialContext, equipment),
                            () -> "equipment " + equipment.id() + " returned no item for " + material.id());
                    records.add(new EquipmentRegistry.EquipmentItemRecord(equipment, material, entry));
                }
            }
            EquipmentRegistry.publishItems(records);
        });
    }
}
