package net.ptcrys.topo.apiv2.machine.resource;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Long-first transactional operations with a bounded chunked adapter for plain NeoForge handlers.
 * A partial int response stops immediately, and full responses stop at a fixed compatibility
 * budget; high-volume stores must implement {@link LongResourceHandler} rather than making one
 * server operation loop through the entire {@code long} range.
 */
public final class ResourceHandlerLongOps {

    private static final int MAX_COMPATIBILITY_CHUNKS = 16;

    private ResourceHandlerLongOps() {}

    /**
     * Whether two handler slots are views of the same backing storage cell.
     *
     * <p>
     * Recipe planners use this to model an input extraction followed by an output insertion
     * from one immutable snapshot. Combined and directional OI views are unwrapped recursively;
     * an unknown handler remains safely identifiable only by its own instance and slot index.
     */
    public static boolean sameStorageSlot(
                                          ResourceHandler<?> left,
                                          int leftSlot,
                                          ResourceHandler<?> right,
                                          int rightSlot) {
        Objects.requireNonNull(left, "left handler");
        Objects.requireNonNull(right, "right handler");
        if (left instanceof CombinedLongResourceHandler<?> combined) {
            return combined.sameStorageSlot(leftSlot, right, rightSlot);
        }
        if (right instanceof CombinedLongResourceHandler<?> combined) {
            return combined.sameStorageSlot(rightSlot, left, leftSlot);
        }
        if (left instanceof DirectionalResourceHandler<?> directional) {
            return sameStorageSlot(directional.delegate(), leftSlot, right, rightSlot);
        }
        if (right instanceof DirectionalResourceHandler<?> directional) {
            return sameStorageSlot(left, leftSlot, directional.delegate(), rightSlot);
        }
        return left == right && leftSlot == rightSlot;
    }

    public static <R extends Resource> long insert(
                                                   ResourceHandler<R> handler,
                                                   R resource,
                                                   long amount,
                                                   TransactionContext transaction) {
        return transfer(handler, 0, false, resource, amount, transaction, true);
    }

    public static <R extends Resource> long insert(
                                                   ResourceHandler<R> handler,
                                                   int index,
                                                   R resource,
                                                   long amount,
                                                   TransactionContext transaction) {
        return transfer(handler, index, true, resource, amount, transaction, true);
    }

    public static <R extends Resource> long extract(
                                                    ResourceHandler<R> handler,
                                                    R resource,
                                                    long amount,
                                                    TransactionContext transaction) {
        return transfer(handler, 0, false, resource, amount, transaction, false);
    }

    public static <R extends Resource> long extract(
                                                    ResourceHandler<R> handler,
                                                    int index,
                                                    R resource,
                                                    long amount,
                                                    TransactionContext transaction) {
        return transfer(handler, index, true, resource, amount, transaction, false);
    }

    private static <R extends Resource> long transfer(
                                                      ResourceHandler<R> handler,
                                                      int index,
                                                      boolean indexed,
                                                      R resource,
                                                      long amount,
                                                      TransactionContext transaction,
                                                      boolean insert) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmpty(resource);
        if (amount < 0L) {
            throw new IllegalArgumentException("Transfer amount must be non-negative: " + amount);
        }
        if (amount == 0L) {
            return 0L;
        }

        if (handler instanceof LongResourceHandler<?> rawLongHandler) {
            @SuppressWarnings("unchecked")
            LongResourceHandler<R> longHandler = (LongResourceHandler<R>) rawLongHandler;
            long moved = indexed ? insert ? longHandler.insertLong(index, resource, amount, transaction) : longHandler.extractLong(index, resource, amount, transaction) : insert ? longHandler.insertLong(resource, amount, transaction) : longHandler.extractLong(resource, amount, transaction);
            return checkedResult(insert, amount, moved);
        }

        long remaining = amount;
        for (int chunk = 0; chunk < MAX_COMPATIBILITY_CHUNKS && remaining > 0L; chunk++) {
            int requested = (int) Math.min(remaining, Integer.MAX_VALUE);
            int moved = indexed ? insert ? handler.insert(index, resource, requested, transaction) : handler.extract(index, resource, requested, transaction) : insert ? handler.insert(resource, requested, transaction) : handler.extract(resource, requested, transaction);
            checkedResult(insert, requested, moved);
            remaining -= moved;
            if (moved < requested) {
                break;
            }
        }
        return amount - remaining;
    }

    private static long checkedResult(boolean insert, long requested, long actual) {
        if (actual < 0L || actual > requested) {
            throw new IllegalStateException("Resource handler " + (insert ? "insert" : "extract") + " returned " + actual + " for a request of " + requested);
        }
        return actual;
    }
}
