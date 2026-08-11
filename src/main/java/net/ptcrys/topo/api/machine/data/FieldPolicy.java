package net.ptcrys.topo.api.machine.data;

/**
 * Immutable persist + sync policy for a single {@link DataField}, resolved once by a field builder
 * and frozen onto the field at construction. Replaces the old mutable {@code PersistConfig} /
 * {@code SyncConfig} objects: a field's behaviour can no longer change after it is built.
 *
 * @param persistEnabled whether the field participates in the machine's complete NBT snapshot
 * @param syncToClient   whether dirty deltas are sent to tracking clients. Prefer {@code false}
 *                       ({@link SyncableFieldBuilder#syncNone()}): open-menu clients use LDLib2
 *                       UI sync; {@code true} is only for special cases such as world rendering
 *                       (and even then prefer a render-side synchronizer). See
 *                       {@link SyncableFieldBuilder#syncToClientAtEndOfDirtyTick()}.
 * @param syncToServer   whether the field accepts client-to-server writes (currently always
 *                       {@code false}; the axis is reserved until authorization exists)
 */
record FieldPolicy(boolean persistEnabled, boolean syncToClient, boolean syncToServer) {}
