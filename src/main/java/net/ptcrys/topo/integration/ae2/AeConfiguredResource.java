package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * Common configuration surface of AE-backed input traits (drawing sync and stocking ports): a row
 * of ghost slots, each pairing a resource with a target amount. Configuration is server-side API
 * for now; the operator UI page is a follow-up (see the migration design ).
 */
public interface AeConfiguredResource<R extends Resource> {

    Class<R> aeConfigResourceType();

    int aeConfigSlotCount();

    AeConfigSlot<R> aeConfigSlot(int slot);

    void setAeConfigSlot(int slot, AeConfigSlot<R> config);

    /**
     * Live stock amount visible at this slot — what the recipe side sees.
     * <ul>
     * <li>For a drawing sync trait this is the local cache count after the most recent AE pull.</li>
     * <li>For a stocking port trait this is the gated AE-network availability under the slot's
     * configured target.</li>
     * </ul>
     */
    default long aeStockedAmount(int slot) {
        return 0L;
    }
}
