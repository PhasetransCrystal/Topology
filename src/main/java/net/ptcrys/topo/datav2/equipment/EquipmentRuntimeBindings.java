package net.ptcrys.topo.datav2.equipment;

import net.ptcrys.topo.apiv2.equipment.EquipmentRegistry;
import net.ptcrys.topo.apiv2.machine.MachineItemBehavior;
import net.ptcrys.topo.apiv2.machine.MachineItemBehaviors;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Runtime wiring of registered equipment items: machine behaviors into the machine domain's
 * {@code MachineItemBehaviors} socket and tooltip panels into {@code ItemTooltipUis}. Items are
 * deferred entries, so both bindings run in FMLCommonSetup's enqueueWork (items bound).
 *
 * <p>
 * Ordering: this must register on the mod bus BEFORE {@code PipeSpecTooltips} — that listener's
 * task calls {@code ItemTooltipUis.freeze()}, and enqueueWork tasks run in submission order.
 */
public final class EquipmentRuntimeBindings {

    private EquipmentRuntimeBindings() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
                MachineItemBehavior behavior = record.equipment().strategy().machineBehavior();
                if (behavior != null) {
                    MachineItemBehaviors.register(record.entry().get(), behavior);
                }
            }
            MachineItemBehaviors.freeze();
            EquipmentTooltips.registerAll();
        }));
    }
}
