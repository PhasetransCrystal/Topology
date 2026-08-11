package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;

import net.neoforged.neoforge.transfer.item.ItemResource;

public final class AeItemOutputPush extends AeResourceOutputPush<ItemResource> {

    public static final ComponentKey<AeItemOutputPush> AE_ITEM_OUTPUT_PUSH = ComponentKey.oi("ae_item_output_push", AeItemOutputPush.class);

    private AeItemOutputPush(ComponentContext<AeItemOutputPush> context) {
        super(context, AeItemBufferPort.AE_ITEM_BUFFER_PORT);
    }

    public static ComponentMount<AeItemOutputPush> mount() {
        return AE_ITEM_OUTPUT_PUSH.mount(AeItemOutputPush::new);
    }
}
