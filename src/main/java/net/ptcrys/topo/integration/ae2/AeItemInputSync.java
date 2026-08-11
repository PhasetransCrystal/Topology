package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;

import net.neoforged.neoforge.transfer.item.ItemResource;

public final class AeItemInputSync extends AeResourceInputSync<ItemResource> {

    public static final ComponentKey<AeItemInputSync> AE_ITEM_INPUT_SYNC = ComponentKey.oi("ae_item_input_sync", AeItemInputSync.class);

    private AeItemInputSync(
                            ComponentContext<AeItemInputSync> context,
                            int slots,
                            ComponentKey<ItemResourcePort> portKey) {
        super(context, ItemResource.class, slots, portKey);
    }

    /** Syncs {@code slots} configured slots of the local item port at {@code portKey} from the AE grid. */
    public static ComponentMount<AeItemInputSync> mount(int slots, ComponentKey<ItemResourcePort> portKey) {
        return AE_ITEM_INPUT_SYNC.mount(context -> new AeItemInputSync(context, slots, portKey));
    }
}
