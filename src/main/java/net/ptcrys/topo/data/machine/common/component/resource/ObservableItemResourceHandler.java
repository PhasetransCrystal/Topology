package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.api.machine.resource.DirectSlotResourceAccess;
import net.ptcrys.topo.api.machine.resource.LongResourceHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Machine item buffer. Implements {@link DirectResourceAccess} so the pipe engine can move
 * machine-to-machine without transactions; mutations go through the non-transactional
 * {@code set}, firing the same contents-changed listener a committed transaction would.
 */
final class ObservableItemResourceHandler extends ItemStacksResourceHandler
                                          implements LongResourceHandler<ItemResource>,
                                          DirectResourceAccess<ItemResource>,
                                          DirectSlotResourceAccess<ItemResource> {

    private final Predicate<Resource> accepts;
    private @Nullable ContentsChangedListener<ItemStack> onChanged;

    ObservableItemResourceHandler(int size) {
        this(size, resource -> true);
    }

    ObservableItemResourceHandler(int size, Predicate<Resource> accepts) {
        super(size);
        this.accepts = Objects.requireNonNull(accepts, "item resource filter");
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return super.isValid(index, resource) && accepts.test(resource);
    }

    @Override
    public long insertLong(
                           int index,
                           ItemResource resource,
                           long amount,
                           TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return insert(index, resource, (int) Math.min(amount, Integer.MAX_VALUE), transaction);
    }

    @Override
    public long extractLong(
                            int index,
                            ItemResource resource,
                            long amount,
                            TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return extract(index, resource, (int) Math.min(amount, Integer.MAX_VALUE), transaction);
    }

    @Override
    public ItemResource directResource() {
        return DirectStacksOps.firstResource(this, ItemResource.EMPTY);
    }

    @Override
    public long directAmount() {
        return DirectStacksOps.amountOfFirst(this, ItemResource.EMPTY);
    }

    @Override
    public long directFreeFor(ItemResource resource) {
        return DirectStacksOps.freeFor(this, resource);
    }

    @Override
    public long directExtract(ItemResource resource, long amount) {
        return DirectStacksOps.extract(this, ItemResource.EMPTY, resource, amount);
    }

    @Override
    public long directInsert(ItemResource resource, long amount) {
        return DirectStacksOps.insert(this, resource, amount);
    }

    @Override
    public void directSet(int index, ItemResource resource, int amount) {
        set(index, resource, amount);
    }

    void setOnChanged(@Nullable ContentsChangedListener<ItemStack> onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        ContentsChangedListener<ItemStack> callback = onChanged;
        if (callback != null) {
            callback.onChanged(index, previousContents);
        }
    }
}
