package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.api.machine.resource.LongResourceHandler;

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
 * Creative scalar buffer: a single-kind battery with a {@link Long#MAX_VALUE} capacity that
 * <b>starts empty</b> and tracks a real {@code long} stored amount (the standard int-stack model
 * caps at ~2.1e9, so the amount is held in a dedicated long field with its own transaction
 * journal and long persistence). Insertion accumulates up to the long ceiling; extraction drains
 * what is stored. The parent {@link ResourceStacksResourceHandler} machinery (size 1, codec) is
 * kept only to satisfy {@code ResourcePort}'s handler contract — every amount path is
 * overridden onto the long field.
 */
final class CreativeScalarResourceHandler extends ResourceStacksResourceHandler<ScalarResource>
                                          implements DirectResourceAccess<ScalarResource>, LongResourceHandler<ScalarResource> {

    private static final Codec<ResourceStack<ScalarResource>> STACK_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ScalarResource.CODEC.fieldOf("resource").forGetter(ResourceStack::resource),
                    Codec.INT.fieldOf("amount").forGetter(ResourceStack::amount))
                    .apply(instance, ResourceStack::new));
    private static final String AMOUNT_KEY = "creative_amount";

    private final ScalarResource resource;
    /** Authoritative stored amount; a true long so the cell's headline LONG capacity is real. */
    private long stored;
    private final SnapshotJournal<Long> journal = new SnapshotJournal<>() {

        @Override
        protected Long createSnapshot() {
            return stored;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            stored = snapshot;
        }

        @Override
        protected void onRootCommit(Long originalState) {
            fireChanged();
        }
    };
    private @Nullable ContentsChangedListener<ResourceStack<ScalarResource>> onChanged;

    CreativeScalarResourceHandler(ScalarResource resource) {
        super(1, ScalarResource.EMPTY, STACK_CODEC);
        this.resource = Objects.requireNonNull(resource, "scalar resource");
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Creative scalar storage cannot be created for the empty resource");
        }
    }

    ScalarResource resource() {
        return resource;
    }

    void setOnChanged(@Nullable ContentsChangedListener<ResourceStack<ScalarResource>> onChanged) {
        this.onChanged = onChanged;
    }

    private void fireChanged() {
        ContentsChangedListener<ResourceStack<ScalarResource>> callback = onChanged;
        if (callback != null) {
            callback.onChanged(0, emptyStack);
        }
    }

    @Override
    public boolean isValid(int index, ScalarResource candidate) {
        Objects.checkIndex(index, 1);
        return candidate == resource;
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
    protected int getCapacity(int index, ScalarResource candidate) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getCapacityAsLong(int index, ScalarResource candidate) {
        Objects.checkIndex(index, 1);
        return candidate.isEmpty() || candidate == resource ? Long.MAX_VALUE : 0;
    }

    @Override
    public int insert(int index, ScalarResource candidate, int amount, TransactionContext transaction) {
        return Math.toIntExact(insertLong(index, candidate, amount, transaction));
    }

    @Override
    public int extract(int index, ScalarResource candidate, int amount, TransactionContext transaction) {
        return Math.toIntExact(extractLong(index, candidate, amount, transaction));
    }

    @Override
    public long insertLong(
                           int index,
                           ScalarResource candidate,
                           long amount,
                           TransactionContext transaction) {
        checkLongTransfer(index, candidate, amount, transaction);
        if (candidate != resource || amount == 0L) {
            return 0L;
        }
        long put = Math.min(amount, Long.MAX_VALUE - stored);
        if (put > 0L) {
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
        checkLongTransfer(index, candidate, amount, transaction);
        if (candidate != resource || amount == 0L) {
            return 0L;
        }
        long taken = Math.min(amount, stored);
        if (taken > 0L) {
            journal.updateSnapshots(transaction);
            stored -= taken;
        }
        return taken;
    }

    @Override
    public long insertLong(
                           ScalarResource candidate,
                           long amount,
                           TransactionContext transaction) {
        return insertLong(0, candidate, amount, transaction);
    }

    @Override
    public long extractLong(
                            ScalarResource candidate,
                            long amount,
                            TransactionContext transaction) {
        return extractLong(0, candidate, amount, transaction);
    }

    private static void checkLongTransfer(
                                          int index,
                                          ScalarResource candidate,
                                          long amount,
                                          TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        LongResourceHandler.checkTransferRequest(candidate, amount, transaction);
    }

    @Override
    public void serialize(ValueOutput output) {
        output.store(AMOUNT_KEY, Codec.LONG, stored);
    }

    @Override
    public void deserialize(ValueInput input) {
        stored = Math.max(0L, input.read(AMOUNT_KEY, Codec.LONG).orElse(0L));
    }

    // --- DirectResourceAccess: lets the pipe engine move scalar without transactions. ---

    @Override
    public ScalarResource directResource() {
        return getResource(0);
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
        return candidate == resource ? Long.MAX_VALUE - stored : 0;
    }

    @Override
    public long directExtract(ScalarResource candidate, long amount) {
        DirectResourceAccess.checkTransferRequest(candidate, amount);
        if (candidate != resource || amount == 0L) {
            return 0L;
        }
        long taken = Math.min(amount, stored);
        if (taken > 0L) {
            stored -= taken;
            fireChanged();
        }
        return taken;
    }

    @Override
    public long directInsert(ScalarResource candidate, long amount) {
        DirectResourceAccess.checkTransferRequest(candidate, amount);
        if (candidate != resource || amount == 0L) {
            return 0L;
        }
        long put = Math.min(amount, Long.MAX_VALUE - stored);
        if (put > 0L) {
            stored += put;
            fireChanged();
        }
        return put;
    }
}
