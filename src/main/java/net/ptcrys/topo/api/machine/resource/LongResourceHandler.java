package net.ptcrys.topo.api.machine.resource;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Native transactional transfer protocol for handlers whose logical amounts are {@code long}.
 * Implementations keep the complete request in one transaction and must never narrow it to an
 * {@code int}. Callers that only have a plain {@link ResourceHandler} should use
 * {@link ResourceHandlerLongOps}, which performs the necessary int-sized fallback calls.
 */
public interface LongResourceHandler<R extends Resource> extends ResourceHandler<R> {

    /** Inserts into one specific slot without narrowing {@code amount}. */
    long insertLong(int index, R resource, long amount, TransactionContext transaction);

    /** Extracts from one specific slot without narrowing {@code amount}. */
    long extractLong(int index, R resource, long amount, TransactionContext transaction);

    /**
     * Aggregate insert. Implementations with resource-specific placement rules should override
     * this method; the default preserves the normal slot-order {@link ResourceHandler} behavior.
     */
    default long insertLong(R resource, long amount, TransactionContext transaction) {
        checkTransferRequest(resource, amount, transaction);
        long remaining = amount;
        for (int index = 0, slots = size(); index < slots && remaining > 0L; index++) {
            long inserted = checkedResult(
                    "insert", remaining, insertLong(index, resource, remaining, transaction));
            remaining -= inserted;
        }
        return amount - remaining;
    }

    /**
     * Aggregate extract. Implementations with a resource index should override this method; the
     * default preserves the normal slot-order {@link ResourceHandler} behavior.
     */
    default long extractLong(R resource, long amount, TransactionContext transaction) {
        checkTransferRequest(resource, amount, transaction);
        long remaining = amount;
        for (int index = 0, slots = size(); index < slots && remaining > 0L; index++) {
            long extracted = checkedResult(
                    "extract", remaining, extractLong(index, resource, remaining, transaction));
            remaining -= extracted;
        }
        return amount - remaining;
    }

    @Override
    default int insert(int index, R resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(checkedResult(
                "insert", amount, insertLong(index, resource, amount, transaction)));
    }

    @Override
    default int extract(int index, R resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(checkedResult(
                "extract", amount, extractLong(index, resource, amount, transaction)));
    }

    @Override
    default int insert(R resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(checkedResult(
                "insert", amount, insertLong(resource, amount, transaction)));
    }

    @Override
    default int extract(R resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(checkedResult(
                "extract", amount, extractLong(resource, amount, transaction)));
    }

    /** Shared long-request validation for wrappers and native implementations. */
    static void checkTransferRequest(Resource resource, long amount, TransactionContext transaction) {
        java.util.Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmpty(resource);
        if (amount < 0L) {
            throw new IllegalArgumentException("Transfer amount must be non-negative: " + amount);
        }
    }

    private static long checkedResult(String operation, long requested, long actual) {
        if (actual < 0L || actual > requested) {
            throw new IllegalStateException("Long resource " + operation + " returned " + actual + " for a request of " + requested);
        }
        return actual;
    }
}
