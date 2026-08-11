package net.ptcrys.topo.api.machine.resource;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * Opt-in fast lane for resource handlers owned by this mod: immediate, on-thread mutations with
 * the exact same side effects as a committed transaction (contents-changed listeners, dirty
 * marking, sync hooks fire inline).
 *
 * <p>
 * The pipe engine uses this to move between two of our own buffers without touching the
 * NeoForge transaction machinery at all — {@code Transaction.open} performs a native stack walk
 * per call, which dominates a small steady-state network's whole tick. Implementations must only
 * be mutated through this interface while no transaction is open on the current thread (callers
 * check {@code Transaction.getLifecycle() == NONE}); a journaled rollback racing a direct write
 * would corrupt state otherwise.
 *
 * <p>
 * Contract: giving back an amount just removed by {@link #directExtract} must always fully
 * succeed (capacity cannot shrink in between on the same thread), which is what makes the
 * engine's extract → insert → give-back sequence atomic-equivalent.
 */
public interface DirectResourceAccess<R extends Resource> {

    /**
     * Whether direct operations actually reach a capable backing store. Directional views
     * implement the interface unconditionally and report readiness based on their delegate;
     * callers must check this before using any other method.
     */
    default boolean directReady() {
        return true;
    }

    /**
     * Stronger opt-in for recipe-wide preflight/apply kernels. Returning {@code true} promises
     * that, while the caller remains on the same thread and no transaction is open, a request
     * proven available by {@link #directAmount()} or {@link #directFreeFor(Resource)} is applied in
     * full. Third-party implementations stay on the transactional recipe path by default.
     */
    default boolean directExactTransfers() {
        return false;
    }

    /** The first stored resource (slot order); empty when nothing is stored. */
    R directResource();

    /** The currently stored amount of the {@link #directResource} kind. */
    long directAmount();

    /** How much of non-empty {@code resource} this handler would accept right now; 0 when rejected. */
    long directFreeFor(R resource);

    /**
     * Immediately remove up to {@code amount} of non-empty {@code resource}; returns the amount
     * removed. Negative amounts are invalid; zero is a no-op.
     */
    long directExtract(R resource, long amount);

    /**
     * Immediately add up to {@code amount} of non-empty {@code resource}; returns the amount added.
     * Negative amounts are invalid; zero is a no-op.
     */
    long directInsert(R resource, long amount);

    /**
     * Compensation path: re-insert what {@link #directExtract} just removed. Identical to
     * {@link #directInsert} on plain handlers, but directional views forward it ungated — an
     * extract-only port must still take its own resources back when the receiving end shorts.
     */
    default long directGiveBack(R resource, long amount) {
        return directInsert(resource, amount);
    }

    /** Shared request validation for direct wrappers and backing stores. */
    static void checkTransferRequest(Resource resource, long amount) {
        TransferPreconditions.checkNonEmpty(resource);
        if (amount < 0L) {
            throw new IllegalArgumentException("Transfer amount must be non-negative: " + amount);
        }
    }
}
