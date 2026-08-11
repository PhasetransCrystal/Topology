package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.apiv2.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.apiv2.machine.resource.DirectSlotResourceAccess;
import net.ptcrys.topo.apiv2.machine.resource.LongResourceHandler;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Machine fluid tank. Implements {@link DirectResourceAccess} so the pipe engine can move
 * tank-to-tank without transactions; mutations go through the non-transactional {@code set},
 * firing the same contents-changed listener a committed transaction would.
 */
final class ObservableFluidResourceHandler extends FluidStacksResourceHandler
                                           implements LongResourceHandler<FluidResource>,
                                           DirectResourceAccess<FluidResource>,
                                           DirectSlotResourceAccess<FluidResource> {

    private final Predicate<Resource> accepts;
    private @Nullable ContentsChangedListener<FluidStack> onChanged;

    ObservableFluidResourceHandler(int size, int capacity) {
        this(size, capacity, resource -> true);
    }

    ObservableFluidResourceHandler(int size, int capacity, Predicate<Resource> accepts) {
        super(size, capacity);
        this.accepts = Objects.requireNonNull(accepts, "fluid resource filter");
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource) && accepts.test(resource);
    }

    @Override
    public long insertLong(
                           int index,
                           FluidResource resource,
                           long amount,
                           TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return insert(index, resource, (int) Math.min(amount, Integer.MAX_VALUE), transaction);
    }

    @Override
    public long extractLong(
                            int index,
                            FluidResource resource,
                            long amount,
                            TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return extract(index, resource, (int) Math.min(amount, Integer.MAX_VALUE), transaction);
    }

    @Override
    public FluidResource directResource() {
        return DirectStacksOps.firstResource(this, FluidResource.EMPTY);
    }

    @Override
    public long directAmount() {
        return DirectStacksOps.amountOfFirst(this, FluidResource.EMPTY);
    }

    @Override
    public long directFreeFor(FluidResource resource) {
        return DirectStacksOps.freeFor(this, resource);
    }

    @Override
    public long directExtract(FluidResource resource, long amount) {
        return DirectStacksOps.extract(this, FluidResource.EMPTY, resource, amount);
    }

    @Override
    public long directInsert(FluidResource resource, long amount) {
        return DirectStacksOps.insert(this, resource, amount);
    }

    @Override
    public void directSet(int index, FluidResource resource, int amount) {
        set(index, resource, amount);
    }

    void setOnChanged(@Nullable ContentsChangedListener<FluidStack> onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        ContentsChangedListener<FluidStack> callback = onChanged;
        if (callback != null) {
            callback.onChanged(index, previousContents);
        }
    }
}
