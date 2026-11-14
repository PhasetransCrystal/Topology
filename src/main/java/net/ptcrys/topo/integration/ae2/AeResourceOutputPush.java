package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.Nullable;

/**
 * Drains an {@link AeBufferPort}'s buffer into the AE network on a fixed cadence. Anything
 * the network refuses stays in the buffer until the next tick. The cadence (40 game ticks)
 * matches {@link AeResourceInputSync} so the push and pull sides of an ME node have
 * symmetric timing characteristics.
 */
public abstract class AeResourceOutputPush<R extends Resource> extends MachineTicker {

    private final ComponentKey<? extends AeBufferPort<R>> portKey;
    private final AeResourceKeyCache<R> keyCache = new AeResourceKeyCache<>(AeResourceKeyResolver.defaultResolver());
    private @Nullable AeBufferPort<R> port;
    private @Nullable AeGridNode grid;

    protected AeResourceOutputPush(
                                   ComponentContext<? extends AeResourceOutputPush<R>> context,
                                   ComponentKey<? extends AeBufferPort<R>> portKey) {
        super(context);
        this.portKey = portKey;
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        port = traits.require(portKey);
        grid = traits.require(AeGridNode.AE_GRID);
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        AeBufferPort<R> bufferPort = port;
        AeGridNode gridTrait = grid;
        if (bufferPort == null || gridTrait == null) {
            return;
        }
        AeKeyResourceBuffer<R> buffer = bufferPort.buffer();
        // Fast path: an empty buffer means zero work and zero transaction overhead.
        int kinds = buffer.kindsInUse();
        if (kinds == 0) {
            return;
        }
        AeNetworkAccessor network = gridTrait.network();
        if (!network.isOnline()) {
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            boolean changed = false;
            // Iterate the sparse non-empty list — O(kinds) instead of O(maxSlots). A drain may
            // swap-remove the slot it empties, shrinking the list; re-check the same position so
            // the swapped-in tail slot is not skipped.
            for (int p = 0; p < kinds; p++) {
                int slot = buffer.nthNonEmptySlot(p);
                if (slot < 0) {
                    break;
                }
                long pushed = buffer.drainSlotIntoNetwork(slot, network, keyCache, tx);
                if (pushed > 0L) {
                    changed = true;
                    if (buffer.kindsInUse() < kinds) {
                        kinds = buffer.kindsInUse();
                        p--;
                    }
                }
            }
            if (changed) {
                tx.commit();
            }
        }
    }
}
