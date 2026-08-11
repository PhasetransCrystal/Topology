package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A single typed unit of machine state with its persist + sync policy frozen at construction.
 *
 * <p>
 * <b>Call layer.</b> Trait authors only touch typed accessors on the concrete subclass
 * ({@link DataInt#value()}, {@link DataInt#set(int)}, {@link DataInt#inc()}...). End-of-tick flush
 * requests are machine-scoped and live on {@link DataScope} ({@link DataScope#markPersistedStateChanged()} /
 * {@link DataScope#syncAtEndOfTick()}). Declaration-time policy lives entirely on the field builders
 * ({@link DataFieldBuilder} / {@link SyncableFieldBuilder}); once {@code builder.done()} returns the
 * field, its policy is immutable and out of reach.
 *
 * <p>
 * Everything else here is framework-internal (package-private) and driven by
 * {@link MachineDataScope} / {@link DataScope}: serialization, dirty-bit bookkeeping, and sync-slot
 * assignment.
 */
public abstract class DataField {

    private final DataScope owner;
    private final String key;
    private final boolean persistEnabled;
    private final boolean syncToClientEnabled;
    private final boolean syncToServerEnabled;
    private boolean syncToClientDirty;
    private boolean syncToServerDirty;
    private int syncToClientSlot = -1;
    private int syncToServerSlot = -1;
    private boolean computed;
    private Runnable recomputeAction;

    protected DataField(DataScope owner, String key, FieldPolicy policy) {
        this.owner = owner;
        this.key = validateKey(key);
        this.persistEnabled = policy.persistEnabled();
        this.syncToClientEnabled = policy.syncToClient();
        this.syncToServerEnabled = policy.syncToServer();
    }

    // ---------------------------------------------------------------------------------------------
    // Subclass helpers
    // ---------------------------------------------------------------------------------------------

    /** Whether this field is published to clients. Immutable policy, safe to query at any time. */
    public final boolean isClientSynced() {
        return syncToClientEnabled;
    }

    /**
     * Mark this field as computed: its value is produced by {@code recomputeAction} every internal tick
     * and direct writes are rejected. Wired once by the field builder; never changes afterwards.
     */
    final void markComputed(Runnable recomputeAction) {
        if (this.recomputeAction != null) {
            throw new IllegalStateException("Data field '" + path() + "' already has a computer");
        }
        this.computed = true;
        this.recomputeAction = recomputeAction;
    }

    /** Whether this field derives its value from a computer instead of accepting direct writes. */
    public final boolean isComputed() {
        return computed;
    }

    /** Run the computer and apply its value. No-op for non-computed fields. Server-driven, per tick. */
    final void recompute() {
        if (recomputeAction != null) {
            recomputeAction.run();
        }
    }

    /**
     * Apply a value produced by this field's computer, marking it dirty like a normal write but
     * bypassing the computed-write guard. Computable field types override this; others reject it.
     */
    void applyComputed(Object value) {
        throw new UnsupportedOperationException(
                "Data field '" + path() + "' (" + getClass().getSimpleName() + ") does not support computed values");
    }

    /** Guard for subclass setters: a computed field rejects direct writes. */
    protected final void requireNotComputed() {
        if (computed) {
            throw new IllegalStateException(
                    "Data field '" + path() + "' is computed; its value comes from its computer, not set()");
        }
    }

    /** Raise the configured dirty bits after an in-place value change and notify the domain. */
    protected final void changed() {
        markDirtyInternal();
        owner.domain().notifyFieldChanged(this);
    }

    protected final void requireReadAccess() {
        owner.domain().requireFieldRead(this);
    }

    protected final void requireWriteAccess() {
        owner.domain().requireFieldWrite(this);
    }

    /** Whether business field R/W is allowed ({@code SERVER_LIVE} / {@code CLIENT_LIVE}). */
    protected final boolean isBusinessReady() {
        return owner.domain().isBusinessReady();
    }

    final String key() {
        return key;
    }

    // ---------------------------------------------------------------------------------------------
    // Framework-internal serialization contract
    // ---------------------------------------------------------------------------------------------

    abstract void writePersist(ValueOutput output);

    abstract void readPersist(ValueInput input);

    abstract void writeSync(RegistryFriendlyByteBuf buffer);

    /** Decode the wire value without applying it, so a whole frame can be validated before mutation. */
    abstract Object readSyncValue(RegistryFriendlyByteBuf buffer);

    abstract void applyDecodedSyncValue(Object value);

    /** Stable per-field type fingerprint folded into the sync schema hash. */
    abstract String schemaTypeId();

    // ---------------------------------------------------------------------------------------------
    // Framework-internal bookkeeping (driven by MachineDataDomain / DataScope)
    // ---------------------------------------------------------------------------------------------

    final DataScope owner() {
        return owner;
    }

    final String path() {
        return owner.fullPath(key);
    }

    final boolean persistEnabled() {
        return persistEnabled;
    }

    final boolean isSyncDirty(DataSyncDirection direction) {
        return direction == DataSyncDirection.TO_CLIENT ? syncToClientDirty : syncToServerDirty;
    }

    final void clearRuntimeDirty() {
        syncToClientDirty = false;
        syncToServerDirty = false;
    }

    final void clearSyncDirty(DataSyncDirection direction) {
        if (direction == DataSyncDirection.TO_CLIENT) {
            syncToClientDirty = false;
        } else {
            syncToServerDirty = false;
        }
    }

    final int syncSlot(DataSyncDirection direction) {
        return direction == DataSyncDirection.TO_CLIENT ? syncToClientSlot : syncToServerSlot;
    }

    final void assignSyncSlot(DataSyncDirection direction, int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("Sync slot must be non-negative");
        }
        if (direction == DataSyncDirection.TO_CLIENT) {
            if (syncToClientSlot < 0) {
                syncToClientSlot = slot;
            }
        } else if (syncToServerSlot < 0) {
            syncToServerSlot = slot;
        }
    }

    private void markDirtyInternal() {
        if (syncToClientEnabled) {
            syncToClientDirty = true;
        }
        if (syncToServerEnabled) {
            syncToServerDirty = true;
        }
    }

    private static String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Data field key must not be blank");
        }
        return key;
    }
}
