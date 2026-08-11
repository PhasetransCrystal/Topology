package net.ptcrys.topo.apiv2.machine.data;

/**
 * Persist-only field backed by externally-owned mutable state.
 *
 * <p>
 * Typed value fields dirty themselves from their setters. Handler-backed fields mutate outside
 * the data object, so their owner explicitly calls {@link #markDirty()} after a backing mutation.
 */
public abstract class DataManualDirtyField extends DataField {

    protected DataManualDirtyField(DataScope owner, String key, FieldPolicy policy) {
        super(owner, key, policy);
    }

    public final void markDirty() {
        requireWriteAccess();
        changed();
    }
}
