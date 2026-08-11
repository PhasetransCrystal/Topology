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

public final class AeItemBufferPort extends AeBufferPort<ItemResource> {

    public static final ComponentKey<AeItemBufferPort> AE_ITEM_BUFFER_PORT = ComponentKey.oi("ae_item_buffer_port", AeItemBufferPort.class);

    private AeItemBufferPort(ComponentContext<AeItemBufferPort> context, int maxKinds) {
        super(
                context,
                ItemResource.class,
                ItemResource.EMPTY,
                maxKinds,
                BuiltinOIResourceIntegrations.ITEM.resourceType());
    }

    public static PartRoleMount<AeItemBufferPort> mount(int maxKinds) {
        ItemResourcePortMetadata metadata = new ItemResourcePortMetadata(maxKinds, bufferPolicy());
        return new PartRoleMount<>(
                AE_ITEM_BUFFER_PORT.mount(context -> new AeItemBufferPort(context, maxKinds), metadata));
    }

    /** Recipe OUTPUT sink only: AE push is the sole egress, no automation capability. */
    static PortAccess bufferPolicy() {
        return PortAccess.output()
                .withAutomationIo(AutomationIo.NONE)
                .withPlayerSlotAccess(PlayerAccess.HIDDEN);
    }
}
