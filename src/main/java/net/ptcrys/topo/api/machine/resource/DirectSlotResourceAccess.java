package net.ptcrys.topo.api.machine.resource;

import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * Explicit opt-in for Topo-owned slot handlers whose contents may be replaced without a
 * transaction.
 *
 * <p>
 * The caller must validate resource, amount, and capacity from one same-thread snapshot before
 * applying a batch. Implementations must fire the same change notification as a committed
 * transactional write. This capability is deliberately narrower than checking a handler base
 * class: third-party handlers may override insertion semantics even when they are stack-backed.
 */
public interface DirectSlotResourceAccess<R extends Resource> {

    /** Compatibility implementation point for NeoForge's int-backed stack handlers. */
    void directSet(int index, R resource, int amount);

    /** Whether this implementation can exactly represent {@code amount} in one slot. */
    default boolean supportsDirectSetAmount(long amount) {
        return amount >= 0L && amount <= Integer.MAX_VALUE;
    }

    /** Long-first exact replacement with checked narrowing for int-backed implementations. */
    default void directSet(int index, R resource, long amount) {
        if (!supportsDirectSetAmount(amount)) {
            throw new IllegalArgumentException("Direct slot amount is not supported: " + amount);
        }
        directSet(index, resource, checkedIntAmount(amount));
    }

    /** Checked narrowing helper for implementations backed by NeoForge int stacks. */
    static int checkedIntAmount(long amount) {
        if (amount < 0L || amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Direct slot amount is outside int-backed range: " + amount);
        }
        return (int) amount;
    }
}
