package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.apiv2.machine.resource.AutomationIo;
import net.ptcrys.topo.apiv2.machine.resource.PlayerAccess;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import net.neoforged.neoforge.transfer.item.ItemResource;

public final class AeStockingItemPort extends AeStockingPort<ItemResource> {

    public static final ComponentKey<AeStockingItemPort> AE_STOCKING_ITEM_PORT = ComponentKey.oi("ae_stocking_item_port", AeStockingItemPort.class);

    private AeStockingItemPort(ComponentContext<AeStockingItemPort> context, int slots) {
        super(
                context,
                ItemResource.class,
                ItemResource.EMPTY,
                slots,
                BuiltinOIResourceIntegrations.ITEM.resourceType());
    }

    public static PartRoleMount<AeStockingItemPort> mount(int slots) {
        ItemResourcePortMetadata metadata = new ItemResourcePortMetadata(slots, stockingPolicy());
        return new PartRoleMount<>(
                AE_STOCKING_ITEM_PORT.mount(context -> new AeStockingItemPort(context, slots), metadata));
    }

    /** Recipe INPUT visibility only: no automation capability, ghost slots are view-only. */
    static PortAccess stockingPolicy() {
        return PortAccess.input()
                .withAutomationIo(AutomationIo.NONE)
                .withPlayerSlotAccess(PlayerAccess.VIEW_ONLY);
    }
}
