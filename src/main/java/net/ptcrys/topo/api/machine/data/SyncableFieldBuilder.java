package net.ptcrys.topo.api.machine.data;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builder for fields that can participate in client/server sync ({@link DataInt}, {@link DataLong},
 * {@link DataBoolean}, {@link DataString}, {@link DataFloat}, {@link DataDouble}, {@link DataEnum},
 * {@link DataResourceKey}). Adds the explicit client-sync axis and the optional computed-value axis on
 * top of {@link DataFieldBuilder}'s persist axis; the persist and sync axes must be declared before
 * {@link #done()}.
 *
 * <p>
 * <b>Client sync policy (default preference: {@link #syncNone()}):</b>
 * <ul>
 * <li>Open-menu chrome that needs to <em>display or edit</em> machine state on the client must use
 * LDLib2 UI sync ({@code DataBindingBuilder} S2C/C2S, {@code addSyncValue}, RPC) — not this
 * machine-data channel.</li>
 * <li>{@link #syncToClientAtEndOfDirtyTick()} is reserved for special non-menu cases (e.g. world
 * rendering mirrors). Prefer the render pipeline's own synchronizer when one exists; only use
 * machine-data S2C when no better render-side channel applies.</li>
 * </ul>
 *
 * <pre>{@code
 * 
 * DataInt progress = data().intField("progress", 0)
 *         .persisted()
 *         .syncNone()
 *         .done();
 * }</pre>
 *
 * @param <F> the concrete field type
 * @param <V> the field's value type (what a computer supplies)
 */
public final class SyncableFieldBuilder<F extends DataField, V> extends DataFieldBuilder<F> {

    private Boolean syncToClient;

    SyncableFieldBuilder(DataScope scope, String key, Function<FieldPolicy, F> constructor) {
        super(scope, key, constructor);
    }

    // These overrides exist only to narrow the fluent return type to SyncableFieldBuilder<F, V>.
    @Override
    public SyncableFieldBuilder<F, V> persisted() {
        super.persisted();
        return this;
    }

    @Override
    public SyncableFieldBuilder<F, V> saveNone() {
        super.saveNone();
        return this;
    }

    /**
     * Send dirty deltas of this field to tracking clients (full baseline via the block update tag).
     *
     * <p>
     * <b>When to use:</b> rare special cases where the client must observe machine state
     * <em>outside</em> an open LDLib2 menu — typically world/TESR/BER rendering, or similar always-on
     * client mirrors. Prefer the render side's own synchronizer when available; only fall back to
     * this machine-data S2C channel when that is not applicable.
     *
     * <p>
     * <b>When not to use:</b> any open-menu UI that needs to show or edit the value. Use LDLib2
     * UI sync instead ({@code DataBindingBuilder.*S2C} / RPC / {@code addSyncValue} on the element
     * tree). Menu chrome must not depend on this field channel.
     */
    public SyncableFieldBuilder<F, V> syncToClientAtEndOfDirtyTick() {
        declareSyncToClient(true);
        return this;
    }

    /**
     * Keep this field server-only: declared and optionally persisted, never machine-data synced to
     * clients. Prefer this for gameplay / config state whose client presentation goes through an
     * open menu (LDLib2 UI bindings and RPC).
     */
    public SyncableFieldBuilder<F, V> syncNone() {
        declareSyncToClient(false);
        return this;
    }

    /**
     * Drive this field from {@code computer} every internal tick, like a Vue computed value: the
     * framework runs the computer on the server each tick and applies the result. The field becomes
     * read-only — a direct {@code set(...)} (or {@code inc()}/{@code dec()}) then throws.
     */
    public SyncableFieldBuilder<F, V> computedEveryInternalTick(Supplier<V> computer) {
        Objects.requireNonNull(computer, "computer");
        declareComputed(field -> () -> field.applyComputed(computer.get()));
        return this;
    }

    /**
     * Reserved client-to-server write axis. Disabled until per-field authorization and validators
     * exist; declaring it fails loudly, mirroring the domain contract.
     */
    public SyncableFieldBuilder<F, V> syncToServer() {
        throw new UnsupportedOperationException("TO_SERVER machine data sync is disabled until " + "menu/session authorization and field validators are implemented for " + path());
    }

    @Override
    void validateDeclaration() {
        super.validateDeclaration();
        if (syncToClient == null) {
            throw new IllegalStateException("Machine data field '" + path() + "' has no explicit client sync strategy; declare syncToClientAtEndOfDirtyTick() or syncNone()");
        }
    }

    @Override
    FieldPolicy resolvePolicy() {
        FieldPolicy base = super.resolvePolicy();
        return new FieldPolicy(
                base.persistEnabled(),
                Boolean.TRUE.equals(syncToClient),
                false);
    }

    @Override
    void enableSyncSlots(F field) {
        if (Boolean.TRUE.equals(syncToClient)) {
            scope.domain().enableClientSync(field);
        }
    }

    private void declareSyncToClient(boolean enabled) {
        scope.domain().requireSchemaOpen("set client sync policy for data field '" + path() + "'");
        if (syncToClient != null) {
            throw new IllegalStateException("Client sync strategy for machine data field '" + path() + "' is already configured");
        }
        syncToClient = enabled;
    }
}
