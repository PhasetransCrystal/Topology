package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.apiv2.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.apiv2.machine.resource.LongResourceHandler;

import net.minecraft.core.NonNullList;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Single-index scalar buffer. One handler holds exactly one scalar resource kind; foreign scalars
 * are rejected by validity so distinct lanes (energy vs heat) can never cross-contaminate even when
 * combined into one machine view.
 *
 * <p>
 * The authoritative amount is a primitive {@code int}. The inherited stack list is refreshed
 * only at serialization/copy boundaries, retaining the historical {@code stacks} save format while
 * direct recipe/pipe mutations avoid allocating one immutable {@link ResourceStack} per change.
 */
final class ObservableScalarResourceHandler extends ResourceStacksResourceHandler<ScalarResource>
                                            implements LongResourceHandler<ScalarResource>, DirectResourceAccess<ScalarResource> {

    private static final Codec<ResourceStack<ScalarResource>> STACK_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ScalarResource.CODEC.fieldOf("resource").forGetter(ResourceStack::resource),
                    Codec.INT.fieldOf("amount").forGetter(ResourceStack::amount))
                    .apply(instance, ResourceStack::new));

    private final ScalarResource resource;
    private final int capacity;
    private final java.util.function.Predicate<net.neoforged.neoforge.transfer.resource.Resource> accepts;
    private int stored;
    private final SnapshotJournal<Integer> journal = new SnapshotJournal<>() {

        @Override
        protected Integer createSnapshot() {
            return stored;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            stored = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            fireChanged(stackOf(originalState));
        }
    };
    private @Nullable ContentsChangedListener<ResourceStack<ScalarResource>> onChanged;

    ObservableScalarResourceHandler(ScalarResource resource, int capacity) {
        this(resource, capacity, candidate -> true);
    }

    ObservableScalarResourceHandler(
                                    ScalarResource resource,
                                    int capacity,
                                    java.util.function.Predicate<net.neoforged.neoforge.transfer.resource.Resource> accepts) {
        super(1, ScalarResource.EMPTY, STACK_CODEC);
        this.resource = Objects.requireNonNull(resource, "scalar resource");
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Scalar storage cannot be created for the empty resource");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Scalar storage capacity must be > 0 (was " + capacity + ")");
        }
        this.capacity = capacity;
        this.accepts = Objects.requireNonNull(accepts, "scalar resource filter");
    }

    ScalarResource resource() {
        return resource;
    }

    long storedAmount() {
        return stored;
    }

    void setOnChanged(@Nullable ContentsChangedListener<ResourceStack<ScalarResource>> onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public boolean isValid(int index, ScalarResource candidate) {
        Objects.checkIndex(index, 1);
        return candidate == resource && accepts.test(candidate);
    }

    @Override
    public ScalarResource getResource(int index) {
        Objects.checkIndex(index, 1);
        return stored > 0 ? resource : ScalarResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, 1);
        return stored;
    }

    @Override
    public long getCapacityAsLong(int index, ScalarResource candidate) {
        Objects.checkIndex(index, 1);
        return candidate.isEmpty() || isValid(index, candidate) ? capacity : 0L;
    }

    @Override
    public void set(int index, ScalarResource candidate, int amount) {
        Objects.checkIndex(index, 1);
        Objects.requireNonNull(candidate, "scalar resource");
        if (amount < 0) {
            throw new IllegalArgumentException("Resource amount must be non-negative: " + amount);
        }
        if (candidate.isEmpty() && amount > 0) {
            throw new IllegalArgumentException("Resource is empty but the amount is positive: " + amount);
        }
        if (amount > 0 && candidate != resource) {
            throw new IllegalArgumentException("Foreign scalar " + candidate + " cannot be set in " + resource);
        }
        int previous = stored;
        stored = Math.min(amount, capacity);
        fireChanged(stackOf(previous));
    }

    @Override
    public int insert(
                      int index,
                      ScalarResource candidate,
                      int amount,
                      TransactionContext transaction) {
        return Math.toIntExact(insertLong(index, candidate, amount, transaction));
    }

    @Override
    public int insert(
                      ScalarResource candidate,
                      int amount,
                      TransactionContext transaction) {
        return Math.toIntExact(insertLong(0, candidate, amount, transaction));
    }

    @Override
    public int extract(
                       int index,
                       ScalarResource candidate,
                       int amount,
                       TransactionContext transaction) {
        return Math.toIntExact(extractLong(index, candidate, amount, transaction));
    }

    @Override
    public int extract(
                       ScalarResource candidate,
                       int amount,
                       TransactionContext transaction) {
        return Math.toIntExact(extractLong(0, candidate, amount, transaction));
    }

    @Override
    public long insertLong(
                           int index,
                           ScalarResource candidate,
                           long amount,
                           TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(candidate, amount, transaction);
        Objects.checkIndex(index, 1);
        if (!isValid(index, candidate) || amount == 0L) {
            return 0L;
        }
        int put = (int) Math.min(Math.max(0L, (long) capacity - stored), amount);
        if (put > 0) {
            journal.updateSnapshots(transaction);
            stored += put;
        }
        return put;
    }

    @Override
    public long extractLong(
                            int index,
                            ScalarResource candidate,
                            long amount,
                            TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(candidate, amount, transaction);
        Objects.checkIndex(index, 1);
        if (candidate != resource || amount == 0L) {
            return 0L;
        }
        int taken = (int) Math.min(stored, amount);
        if (taken > 0) {
            journal.updateSnapshots(transaction);
            stored -= taken;
        }
        return taken;
    }

    @Override
    public ScalarResource directResource() {
        return stored > 0 ? resource : ScalarResource.EMPTY;
    }

    @Override
    public long directAmount() {
        return stored;
    }

    @Override
    public boolean directExactTransfers() {
        return true;
    }

    @Override
    public long directFreeFor(ScalarResource candidate) {
        DirectResourceAccess.checkTransferRequest(candidate, 0L);
        return isValid(0, candidate) ? Math.max(0L, (long) capacity - stored) : 0L;
    }

    @Override
    public long directExtract(ScalarResource candidate, long amount) {
        DirectResourceAccess.checkTransferRequest(candidate, amount);
        if (candidate != resource || amount == 0L) {
            return 0L;
        }
        int taken = (int) Math.min(stored, amount);
        if (taken > 0) {
            stored -= taken;
            fireChanged(emptyStack);
        }
        return taken;
    }

    @Override
    public long directInsert(ScalarResource candidate, long amount) {
        DirectResourceAccess.checkTransferRequest(candidate, amount);
        if (!isValid(0, candidate) || amount == 0L) {
            return 0L;
        }
        int put = (int) Math.min(Math.max(0L, (long) capacity - stored), amount);
        if (put > 0) {
            stored += put;
            fireChanged(emptyStack);
        }
        return put;
    }

    @Override
    protected int getCapacity(int index, ScalarResource candidate) {
        Objects.checkIndex(index, 1);
        return capacity;
    }

    @Override
    public NonNullList<ResourceStack<ScalarResource>> copyToList() {
        return NonNullList.of(emptyStack, stackOf(stored));
    }

    @Override
    public void serialize(ValueOutput output) {
        stacks.set(0, stackOf(stored));
        super.serialize(output);
    }

    @Override
    public void deserialize(ValueInput input) {
        super.deserialize(input);
        ResourceStack<ScalarResource> persisted = stacks.isEmpty() ? emptyStack : stacks.getFirst();
        stored = persisted.resource() == resource ? Math.clamp(persisted.amount(), 0, capacity) : 0;
        if (stacks.size() != 1) {
            setStacks(NonNullList.withSize(1, stackOf(stored)));
        }
    }

    @Override
    protected void onContentsChanged(int index, ResourceStack<ScalarResource> previousContents) {
        fireChanged(previousContents);
    }

    private void fireChanged(ResourceStack<ScalarResource> previousContents) {
        ContentsChangedListener<ResourceStack<ScalarResource>> callback = onChanged;
        if (callback != null) {
            callback.onChanged(0, previousContents);
        }
    }

    private ResourceStack<ScalarResource> stackOf(int amount) {
        return amount > 0 ? new ResourceStack<>(resource, amount) : emptyStack;
    }
}
