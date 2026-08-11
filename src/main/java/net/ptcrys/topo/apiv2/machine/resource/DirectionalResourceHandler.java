package net.ptcrys.topo.apiv2.machine.resource;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Capability-facing wrapper that gates insert/extract while preserving storage queries. Also
 * forwards {@link DirectResourceAccess} (mode-gated) so the pipe fast lane reaches machine ports
 * exposed as insert-only/extract-only views; the give-back path stays ungated by contract.
 */
final class DirectionalResourceHandler<R extends Resource>
                                      implements LongResourceHandler<R>, DirectResourceAccess<R>, ResourceTransferDirections {

    private final ResourceHandler<R> delegate;
    private final AutomationIo mode;
    private @Nullable TxnPoisonJournal poisonJournal;

    DirectionalResourceHandler(ResourceHandler<R> delegate, AutomationIo mode) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.mode = Objects.requireNonNull(mode, "mode");
        if (mode == AutomationIo.NONE || mode == AutomationIo.BOTH) {
            throw new IllegalArgumentException("DirectionalResourceHandler only wraps INSERT or EXTRACT views");
        }
    }

    ResourceHandler<R> delegate() {
        return delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public R getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, R resource) {
        return delegate.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, R resource) {
        return mode.canInsert() && delegate.isValid(index, resource);
    }

    @Override
    public boolean allowsInsert() {
        return mode.canInsert();
    }

    @Override
    public boolean allowsExtract() {
        return mode.canExtract();
    }

    @Override
    public int insert(int index, R resource, int amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canInsert(), transaction) ? delegate.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int insert(R resource, int amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canInsert(), transaction) ? delegate.insert(resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, R resource, int amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canExtract(), transaction) ? delegate.extract(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(R resource, int amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canExtract(), transaction) ? delegate.extract(resource, amount, transaction) : 0;
    }

    @Override
    public long insertLong(int index, R resource, long amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canInsert(), transaction) ? ResourceHandlerLongOps.insert(delegate, index, resource, amount, transaction) : 0L;
    }

    @Override
    public long insertLong(R resource, long amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canInsert(), transaction) ? ResourceHandlerLongOps.insert(delegate, resource, amount, transaction) : 0L;
    }

    @Override
    public long extractLong(int index, R resource, long amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canExtract(), transaction) ? ResourceHandlerLongOps.extract(delegate, index, resource, amount, transaction) : 0L;
    }

    @Override
    public long extractLong(R resource, long amount, TransactionContext transaction) {
        LongResourceHandler.checkTransferRequest(resource, amount, transaction);
        return allow(mode.canExtract(), transaction) ? ResourceHandlerLongOps.extract(delegate, resource, amount, transaction) : 0L;
    }

    @SuppressWarnings("unchecked")
    private @Nullable DirectResourceAccess<R> directDelegate() {
        return delegate instanceof DirectResourceAccess<?> direct ? (DirectResourceAccess<R>) direct : null;
    }

    @Override
    public boolean directReady() {
        DirectResourceAccess<R> direct = directDelegate();
        return direct != null && direct.directReady();
    }

    @Override
    public boolean directExactTransfers() {
        DirectResourceAccess<R> direct = directDelegate();
        return direct != null && direct.directExactTransfers();
    }

    @Override
    public R directResource() {
        DirectResourceAccess<R> direct = directDelegate();
        return direct == null ? getResource(0) : direct.directResource();
    }

    @Override
    public long directAmount() {
        DirectResourceAccess<R> direct = directDelegate();
        return direct == null ? 0 : direct.directAmount();
    }

    @Override
    public long directFreeFor(R resource) {
        DirectResourceAccess.checkTransferRequest(resource, 0L);
        DirectResourceAccess<R> direct = directDelegate();
        return direct == null || !mode.canInsert() ? 0 : direct.directFreeFor(resource);
    }

    @Override
    public long directExtract(R resource, long amount) {
        DirectResourceAccess.checkTransferRequest(resource, amount);
        DirectResourceAccess<R> direct = directDelegate();
        return direct == null || !mode.canExtract() ? 0L : direct.directExtract(resource, amount);
    }

    @Override
    public long directInsert(R resource, long amount) {
        DirectResourceAccess.checkTransferRequest(resource, amount);
        DirectResourceAccess<R> direct = directDelegate();
        return direct == null || !mode.canInsert() ? 0L : direct.directInsert(resource, amount);
    }

    @Override
    public long directGiveBack(R resource, long amount) {
        DirectResourceAccess.checkTransferRequest(resource, amount);
        DirectResourceAccess<R> direct = directDelegate();
        return direct == null ? 0L : direct.directGiveBack(resource, amount);
    }

    private boolean allow(boolean allowedDirection, TransactionContext transaction) {
        if (poisoned()) {
            return false;
        }
        if (!allowedDirection) {
            poison(transaction);
            return false;
        }
        return true;
    }

    private boolean poisoned() {
        TxnPoisonJournal journal = poisonJournal;
        return journal != null && journal.poisoned();
    }

    private void poison(TransactionContext transaction) {
        TxnPoisonJournal journal = poisonJournal;
        if (journal == null) {
            journal = new TxnPoisonJournal();
            poisonJournal = journal;
        }
        journal.poison(transaction);
    }

    private static final class TxnPoisonJournal extends SnapshotJournal<Boolean> {

        private boolean poisoned;

        void poison(TransactionContext transaction) {
            if (poisoned) {
                return;
            }
            updateSnapshots(transaction);
            poisoned = true;
        }

        boolean poisoned() {
            return poisoned;
        }

        @Override
        protected Boolean createSnapshot() {
            return poisoned;
        }

        @Override
        protected void revertToSnapshot(Boolean snapshot) {
            poisoned = snapshot;
        }

        @Override
        protected void onRootCommit(Boolean originalState) {
            poisoned = originalState;
        }
    }
}
