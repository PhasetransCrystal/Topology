package net.ptcrys.topo.apiv2.machine.resource;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Objects;

/**
 * Immutable concatenated view used by recipe pools.
 *
 * <p>
 * Unlike NeoForge's combined handler, slot lookup is binary rather than linear and aggregate
 * transfers keep {@code long} amounts across OI-owned handlers. Plain NeoForge handlers are
 * adapted only at the final boundary by {@link ResourceHandlerLongOps}.
 */
final class CombinedLongResourceHandler<R extends Resource>
                                       implements LongResourceHandler<R>, DirectResourceAccess<R> {

    private final ResourceHandler<R>[] handlers;
    private final int[] endSlots;
    private final int size;

    @SuppressWarnings("unchecked")
    CombinedLongResourceHandler(List<? extends ResourceHandler<R>> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        if (handlers.size() < 2) {
            throw new IllegalArgumentException("A combined handler requires at least two delegates");
        }
        this.handlers = handlers.toArray(ResourceHandler[]::new);
        this.endSlots = new int[this.handlers.length];
        int total = 0;
        for (int index = 0; index < this.handlers.length; index++) {
            ResourceHandler<R> handler = Objects.requireNonNull(this.handlers[index], "handler");
            int handlerSize = handler.size();
            if (handlerSize < 0) {
                throw new IllegalArgumentException(
                        "Resource handler " + index + " reported a negative size: " + handlerSize);
            }
            try {
                total = Math.addExact(total, handlerSize);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Combined resource handler size exceeds int range", overflow);
            }
            endSlots[index] = total;
        }
        this.size = total;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public R getResource(int index) {
        int handlerIndex = handlerIndex(index);
        return handlers[handlerIndex].getResource(localIndex(index, handlerIndex));
    }

    @Override
    public long getAmountAsLong(int index) {
        int handlerIndex = handlerIndex(index);
        return handlers[handlerIndex].getAmountAsLong(localIndex(index, handlerIndex));
    }

    @Override
    public long getCapacityAsLong(int index, R resource) {
        int handlerIndex = handlerIndex(index);
        return handlers[handlerIndex].getCapacityAsLong(localIndex(index, handlerIndex), resource);
    }

    @Override
    public boolean isValid(int index, R resource) {
        int handlerIndex = handlerIndex(index);
        return resource.isEmpty() || handlers[handlerIndex].isValid(localIndex(index, handlerIndex), resource);
    }

    @Override
    public long insertLong(int index, R resource, long amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        int handlerIndex = handlerIndex(index);
        return ResourceHandlerLongOps.insert(
                handlers[handlerIndex], localIndex(index, handlerIndex), resource, amount, transaction);
    }

    @Override
    public long extractLong(int index, R resource, long amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        int handlerIndex = handlerIndex(index);
        return ResourceHandlerLongOps.extract(
                handlers[handlerIndex], localIndex(index, handlerIndex), resource, amount, transaction);
    }

    @Override
    public long insertLong(R resource, long amount, TransactionContext transaction) {
        return transfer(resource, amount, transaction, true);
    }

    @Override
    public long extractLong(R resource, long amount, TransactionContext transaction) {
        return transfer(resource, amount, transaction, false);
    }

    private long transfer(R resource, long amount, TransactionContext transaction, boolean insert) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        long remaining = amount;
        for (ResourceHandler<R> handler : handlers) {
            if (remaining == 0L) {
                break;
            }
            if (!allows(handler, insert)) {
                continue;
            }
            long moved = insert ? ResourceHandlerLongOps.insert(handler, resource, remaining, transaction) : ResourceHandlerLongOps.extract(handler, resource, remaining, transaction);
            remaining -= moved;
        }
        return amount - remaining;
    }

    private static boolean allows(ResourceHandler<?> handler, boolean insert) {
        if (handler instanceof ResourceTransferDirections directions) {
            return insert ? directions.allowsInsert() : directions.allowsExtract();
        }
        return true;
    }

    @Override
    public boolean directReady() {
        boolean hasSlot = false;
        for (ResourceHandler<R> handler : handlers) {
            DirectResourceAccess<R> direct = directAccess(handler);
            if (direct == null || !direct.directReady()) {
                return false;
            }
            hasSlot |= handler.size() > 0;
        }
        return hasSlot;
    }

    @Override
    public boolean directExactTransfers() {
        for (ResourceHandler<R> handler : handlers) {
            DirectResourceAccess<R> direct = directAccess(handler);
            if (direct == null || !direct.directExactTransfers()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public R directResource() {
        for (ResourceHandler<R> handler : handlers) {
            DirectResourceAccess<R> direct = requireReadyDirect(handler);
            R resource = direct.directResource();
            long amount = direct.directAmount();
            if (amount < 0L) {
                throw new IllegalStateException("Direct resource handler reported a negative amount: " + amount);
            }
            if (!resource.isEmpty() && amount > 0L) {
                return resource;
            }
        }
        return requireReadyDirect(handlers[0]).directResource();
    }

    @Override
    public long directAmount() {
        R resource = directResource();
        if (resource.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (ResourceHandler<R> handler : handlers) {
            DirectResourceAccess<R> direct = requireReadyDirect(handler);
            if (resource.equals(direct.directResource())) {
                long amount = direct.directAmount();
                if (amount < 0L) {
                    throw new IllegalStateException(
                            "Direct resource handler reported a negative amount: " + amount);
                }
                total = saturatedAdd(total, amount);
            }
        }
        return total;
    }

    @Override
    public long directFreeFor(R resource) {
        DirectResourceAccess.checkTransferRequest(resource, 0L);
        long total = 0L;
        for (ResourceHandler<R> handler : handlers) {
            DirectResourceAccess<R> direct = directAccess(handler);
            if (direct == null || !direct.directReady()) {
                return 0L;
            }
            long free = direct.directFreeFor(resource);
            if (free < 0L) {
                throw new IllegalStateException("Direct resource handler reported negative free space: " + free);
            }
            total = saturatedAdd(total, free);
        }
        return total;
    }

    @Override
    public long directExtract(R resource, long amount) {
        return directTransfer(resource, amount, DirectOperation.EXTRACT);
    }

    @Override
    public long directInsert(R resource, long amount) {
        return directTransfer(resource, amount, DirectOperation.INSERT);
    }

    @Override
    public long directGiveBack(R resource, long amount) {
        return directTransfer(resource, amount, DirectOperation.GIVE_BACK);
    }

    private long directTransfer(R resource, long amount, DirectOperation operation) {
        DirectResourceAccess.checkTransferRequest(resource, amount);
        if (amount == 0L) {
            return 0L;
        }
        long remaining = amount;
        for (ResourceHandler<R> handler : handlers) {
            if (remaining == 0L) {
                break;
            }
            DirectResourceAccess<R> direct = directAccess(handler);
            if (direct == null || !direct.directReady()) {
                return amount - remaining;
            }
            long moved = switch (operation) {
                case EXTRACT -> direct.directExtract(resource, remaining);
                case INSERT -> direct.directInsert(resource, remaining);
                case GIVE_BACK -> direct.directGiveBack(resource, remaining);
            };
            if (moved < 0L || moved > remaining) {
                throw new IllegalStateException("Direct resource handler " + operation.description + " returned " + moved + " for a request of " + remaining);
            }
            remaining -= moved;
        }
        return amount - remaining;
    }

    boolean sameStorageSlot(int slot, ResourceHandler<?> other, int otherSlot) {
        int handlerIndex = handlerIndex(slot);
        return ResourceHandlerLongOps.sameStorageSlot(
                handlers[handlerIndex], localIndex(slot, handlerIndex), other, otherSlot);
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource> DirectResourceAccess<R> directAccess(ResourceHandler<R> handler) {
        return handler instanceof DirectResourceAccess<?> direct ? (DirectResourceAccess<R>) direct : null;
    }

    private static <R extends Resource> DirectResourceAccess<R> requireReadyDirect(ResourceHandler<R> handler) {
        DirectResourceAccess<R> direct = directAccess(handler);
        if (direct == null || !direct.directReady()) {
            throw new IllegalStateException("Direct operation used while a combined delegate is not ready");
        }
        return direct;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private int handlerIndex(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot " + slot + " outside combined size " + size);
        }
        int low = 0;
        int high = endSlots.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (slot < endSlots[middle]) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private int localIndex(int slot, int handlerIndex) {
        return slot - (handlerIndex == 0 ? 0 : endSlots[handlerIndex - 1]);
    }

    private enum DirectOperation {

        EXTRACT("extract"),
        INSERT("insert"),
        GIVE_BACK("give-back");

        private final String description;

        DirectOperation(String description) {
            this.description = description;
        }
    }
}
