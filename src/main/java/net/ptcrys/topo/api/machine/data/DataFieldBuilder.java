package net.ptcrys.topo.api.machine.data;

import java.util.function.Function;

/**
 * Configures and registers a single machine data field. Returned by the persist-only field
 * factories on {@link DataScope} (such as {@link DataScope#valueIoField},
 * {@link DataScope#itemResourceHandler}, and {@link DataScope#fluidResourceHandler}); syncable
 * fields return the richer {@link SyncableFieldBuilder}.
 *
 * <p>
 * The builder phase <i>is</i> the declaration phase: call the explicit policy methods directly,
 * then {@link #done()} validates the declaration, constructs the immutable field, and registers it.
 * Once {@code done()} returns the field, the field type carries no policy methods, so a field's
 * persist/sync behaviour can never change after registration.
 *
 * <pre>{@code
 * 
 * DataValueIoField contents = data().valueIoField("contents", w, r, after)
 *         .persisted()
 *         .done();
 * }</pre>
 *
 * <p>
 * Assign the result to the concrete field type, not {@code var}: a forgotten {@code .done()} then
 * fails to compile (the builder is not the field) instead of silently dropping the field.
 */
public sealed class DataFieldBuilder<F extends DataField> permits SyncableFieldBuilder {

    enum PersistMode {
        UNDECLARED,
        NONE,
        PERSISTED
    }

    final DataScope scope;
    final String key;
    private final Function<FieldPolicy, F> constructor;
    private PersistMode persistMode = PersistMode.UNDECLARED;
    private Function<F, Runnable> computerWiring;

    DataFieldBuilder(DataScope scope, String key, Function<FieldPolicy, F> constructor) {
        this.scope = scope;
        this.key = key;
        this.constructor = constructor;
    }

    /** Include this field in the machine's complete persistent snapshot. */
    public DataFieldBuilder<F> persisted() {
        declarePersist(PersistMode.PERSISTED);
        return this;
    }

    /** Never persist this field. */
    public DataFieldBuilder<F> saveNone() {
        declarePersist(PersistMode.NONE);
        return this;
    }

    /** Validate the declaration, build the immutable field, and register it on its scope. */
    public final F done() {
        validateDeclaration();
        F field = constructor.apply(resolvePolicy());
        scope.register(field);
        enableSyncSlots(field);
        if (computerWiring != null) {
            field.markComputed(computerWiring.apply(field));
        }
        return field;
    }

    /**
     * Declare this field computed: {@code wiring} turns the built field into the per-tick recompute
     * action. Type-specific builders expose a typed {@code computed...} method that calls this.
     */
    final void declareComputed(Function<F, Runnable> wiring) {
        scope.domain().requireSchemaOpen("set computer for data field '" + path() + "'");
        if (computerWiring != null) {
            throw new IllegalStateException("Computer for machine data field '" + path() + "' is already configured");
        }
        computerWiring = wiring;
    }

    final void declarePersist(PersistMode mode) {
        scope.domain().requireSchemaOpen("set persist policy for data field '" + path() + "'");
        if (persistMode != PersistMode.UNDECLARED) {
            throw new IllegalStateException("Persist strategy for machine data field '" + path() + "' is already configured");
        }
        persistMode = mode;
    }

    final String path() {
        return scope.fullPath(key);
    }

    /** Subclasses extend this to add their own required axes (e.g. client sync). */
    void validateDeclaration() {
        if (persistMode == PersistMode.UNDECLARED) {
            throw new IllegalStateException("Machine data field '" + path() + "' has no explicit persist strategy; declare persisted() or saveNone()");
        }
    }

    /** Subclasses extend this to fold in their own axes. */
    FieldPolicy resolvePolicy() {
        return new FieldPolicy(persistMode == PersistMode.PERSISTED, false, false);
    }

    /** Subclasses register their declared sync directions with the domain after construction. */
    void enableSyncSlots(F field) {}
}
