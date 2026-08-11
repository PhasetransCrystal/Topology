package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.apiv2.machine.resource.DirectResourceAccess;

import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

/**
 * Shared multi-slot implementation of {@link net.ptcrys.topo.apiv2.machine.resource.DirectResourceAccess}
 * for our {@link StacksResourceHandler}-backed trait handlers (items, fluids). Uses only the
 * public handler API; mutations go through the non-transactional {@code set(index, resource,
 * amount)}, which fires the same contents-changed listener a committed transaction would.
 */
final class DirectStacksOps {

    private DirectStacksOps() {}

    /** First stored resource in slot order, or {@code empty} when the handler holds nothing. */
    static <R extends Resource> R firstResource(StacksResourceHandler<?, R> handler, R empty) {
        int size = handler.size();
        for (int index = 0; index < size; index++) {
            R resource = handler.getResource(index);
            if (!resource.isEmpty() && handler.getAmountAsLong(index) > 0) {
                return resource;
            }
        }
        return empty;
    }

    /** Total stored amount of the first resource kind (matching {@link #firstResource}). */
    static <R extends Resource> long amountOfFirst(StacksResourceHandler<?, R> handler, R empty) {
        R resource = firstResource(handler, empty);
        if (resource.isEmpty()) {
            return 0;
        }
        long total = 0L;
        int size = handler.size();
        for (int index = 0; index < size; index++) {
            if (resource.equals(handler.getResource(index))) {
                total = saturatingAdd(total, checkedIntAmount(handler, index));
            }
        }
        return total;
    }

    /** Read-only acceptance: free space across slots that are empty-and-valid or already match. */
    static <R extends Resource> long freeFor(StacksResourceHandler<?, R> handler, R resource) {
        DirectResourceAccess.checkTransferRequest(resource, 0L);
        long free = 0L;
        int size = handler.size();
        for (int index = 0; index < size; index++) {
            R slot = handler.getResource(index);
            if (handler.isValid(index, resource) && (slot.isEmpty() || slot.equals(resource))) {
                long stored = checkedIntAmount(handler, index);
                long writableCapacity = Math.min(
                        Integer.MAX_VALUE,
                        Math.max(0L, handler.getCapacityAsLong(index, resource)));
                long room = writableCapacity - stored;
                if (room > 0) {
                    free = saturatingAdd(free, room);
                }
            }
        }
        return free;
    }

    /** Immediately remove up to {@code amount} of {@code resource} across matching slots. */
    static <R extends Resource> long extract(
                                             StacksResourceHandler<?, R> handler, R empty, R resource, long amount) {
        DirectResourceAccess.checkTransferRequest(resource, amount);
        if (amount == 0L) {
            return 0L;
        }
        long taken = 0L;
        int size = handler.size();
        for (int index = 0; index < size && taken < amount; index++) {
            if (!resource.equals(handler.getResource(index))) {
                continue;
            }
            int stored = checkedIntAmount(handler, index);
            int take = Math.toIntExact(Math.min(stored, amount - taken));
            if (take > 0) {
                int remaining = stored - take;
                handler.set(index, remaining > 0 ? resource : empty, remaining);
                taken += take;
            }
        }
        return taken;
    }

    /** Immediately add up to {@code amount}: top up matching slots first, then empty valid slots. */
    static <R extends Resource> long insert(
                                            StacksResourceHandler<?, R> handler, R resource, long amount) {
        DirectResourceAccess.checkTransferRequest(resource, amount);
        if (amount == 0L) {
            return 0L;
        }
        long put = 0L;
        int size = handler.size();
        for (int index = 0; index < size && put < amount; index++) {
            if (resource.equals(handler.getResource(index)) && handler.isValid(index, resource)) {
                put += fill(handler, resource, index, amount - put);
            }
        }
        for (int index = 0; index < size && put < amount; index++) {
            if (handler.getResource(index).isEmpty() && handler.isValid(index, resource)) {
                put += fill(handler, resource, index, amount - put);
            }
        }
        return put;
    }

    private static <R extends Resource> long fill(
                                                  StacksResourceHandler<?, R> handler, R resource, int index, long amount) {
        int stored = checkedIntAmount(handler, index);
        long writableCapacity = Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, handler.getCapacityAsLong(index, resource)));
        long room = writableCapacity - stored;
        int add = (int) Math.min(Math.max(0L, room), amount);
        if (add <= 0) {
            return 0L;
        }
        handler.set(index, resource, stored + add);
        return add;
    }

    private static int checkedIntAmount(StacksResourceHandler<?, ?> handler, int index) {
        long amount = handler.getAmountAsLong(index);
        if (amount < 0L || amount > Integer.MAX_VALUE) {
            throw new IllegalStateException("Int-backed stack slot " + index + " reported an out-of-range amount: " + amount);
        }
        return (int) amount;
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
