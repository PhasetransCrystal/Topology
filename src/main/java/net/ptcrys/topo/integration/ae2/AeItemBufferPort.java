package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.api.machine.resource.AutomationIo;
import net.ptcrys.topo.api.machine.resource.PlayerAccess;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.neoforged.neoforge.transfer.item.ItemResource;

public final class AeItemBufferPort extends AeBufferPort<ItemResource> {

    public static final ComponentKey<AeItemBufferPort> AE_ITEM_BUFFER_PORT = ComponentKey.id("ae_item_buffer_port", AeItemBufferPort.class);

    private AeItemBufferPort(ComponentContext<AeItemBufferPort> context, int maxKinds) {
        super(
                context,
                ItemResource.class,
                ItemResource.EMPTY,
                maxKinds,
                BuiltinTopoResourceIntegrations.ITEM.resourceType());
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
