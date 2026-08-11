package net.ptcrys.topo.apiv2.machine.data;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.data.network.MachineDataC2SPayload;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.connection.ConnectionType;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Machine-owned data domain: schema, persist, client sync, and business access gates.
 *
 * <h2>Naming</h2>
 * <ul>
 * <li>{@code lifecycle*} — framework entry points (only {@link MachineBlockEntity} / network)</li>
 * <li>{@code is*} / {@code require*} — queries and guards (traits may use)</li>
 * <li>{@code scope} / {@code enable*Sync} / {@code request*} — schema and dirty bookkeeping</li>
 * </ul>
 *
 * <h2>Developer model</h2>
 * Declare fields in trait constructors via {@link #scope}. After the machine is live, typed
 * accessors work; the framework enforces phase. Do not call {@code lifecycle*} from traits.
 *
 * <h2>Lifecycle</h2>
 * 
 * <pre>
 *   construct + mount traits  → Phase.BOOTSTRAP
 *   lifecycleLoadPersisted    → APPLYING_PERSIST (mutation suppressed) → BOOTSTRAP | SERVER_LIVE
 *   lifecycleAttachLevel      → SERVER_LIVE | CLIENT_WAITING
 *   lifecycleEnterWorld       → SERVER_LIVE | CLIENT_WAITING
 *   lifecycleReceiveClientBaseline → CLIENT_LIVE
 *   lifecycleServerTick       → recompute + flush
 *   lifecycleSavePersisted    → NBT out
 * </pre>
 */
public final class MachineDataScope {

    public static final String SAVE_KEY = "machine_data";
    public static final String UPDATE_TAG_SYNC_KEY = "machine_data_sync";
    static final int FORMAT_VERSION = 2;

    private static final DataField[] NO_FIELDS = new DataField[0];

    private final MachineBlockEntity machine;
    private final Map<String, DataScope> scopes = new LinkedHashMap<>();
    private final List<DataField> syncToClientFields = new ArrayList<>();
    private final List<DataField> syncToServerFields = new ArrayList<>();

    private DataField[] computedFieldsCache = NO_FIELDS;
    private long lastRecomputeGameTime = Long.MIN_VALUE;

    /** Monotonic server-thread generation for authoritative state that belongs in a chunk snapshot. */
    private long persistGeneration;
    /** Highest generation captured by any complete machine snapshot serialization. */
    private long capturedPersistGeneration;
    /** Highest captured generation confirmed by a real ChunkDataEvent.Save. */
    private long acknowledgedPersistGeneration;
    /** Generation for which the chunk-dirty mark currently outstanding was issued. */
    private long markedPersistGeneration;
    private boolean persistAwaitingSnapshot;

    private Phase phase = Phase.BOOTSTRAP;
    private boolean clientBaselineReceived;
    private boolean schemaSealed;
    private boolean endOfTickSyncRequested;
    /** Depth: while &gt; 0, handler mutations must not raise business dirty / recipe invalidation. */
    private int suppressMutationSideEffectsDepth;

    private MachineDataScope(MachineBlockEntity machine) {
        this.machine = Objects.requireNonNull(machine, "machine");
    }

    public static MachineDataScope create(MachineBlockEntity machine) {
        return new MachineDataScope(machine);
    }

    public MachineBlockEntity machine() {
        return machine;
    }

    // =============================================================================================
    // lifecycle* — sole phase-transition / framework templates
    // =============================================================================================

    /** {@link MachineBlockEntity#setLevel}. */
    public void lifecycleAttachLevel(Level level) {
        Objects.requireNonNull(level, "level");
        sealSchema();
        promoteToWorldSide(level);
    }

    /**
     * {@link MachineBlockEntity} loadAdditional: apply disk NBT. Safe with or without a level
     * (chunk post-load often has no level yet).
     */
    public void lifecycleLoadPersisted(ValueInput input) {
        Objects.requireNonNull(input, "input");
        Phase previous = phase;
        phase = Phase.APPLYING_PERSIST;
        try {
            runWithSuppressedMutationSideEffects(() -> applyPersistedScopes(input));
            sealSchema();
            Level level = machine.getLevel();
            if (level != null && !level.isClientSide()) {
                phase = Phase.SERVER_LIVE;
            } else if (previous == Phase.SERVER_LIVE || previous == Phase.CLIENT_LIVE || previous == Phase.CLIENT_WAITING) {
                phase = previous;
            } else {
                phase = Phase.BOOTSTRAP;
            }
            clearRuntimeDirty();
        } catch (RuntimeException ex) {
            phase = previous == Phase.APPLYING_PERSIST ? Phase.BOOTSTRAP : previous;
            throw ex;
        }
    }

    /** Optional client baseline packed into the same ValueInput as block-entity load. */
    public void lifecycleLoadClientBaselineFromUpdateTag(ValueInput input) {
        Objects.requireNonNull(input, "input");
        input.read(UPDATE_TAG_SYNC_KEY, Codec.BYTE_BUFFER).ifPresent(buffer -> {
            ByteBuffer copySource = buffer.slice();
            byte[] payload = new byte[copySource.remaining()];
            copySource.get(payload);
            Level level = machine.getLevel();
            RegistryAccess registryAccess = level == null ? RegistryAccess.EMPTY : level.registryAccess();
            lifecycleReceiveClientBaseline(payload, registryAccess, ConnectionType.OTHER);
        });
    }

    /** {@link MachineBlockEntity#onLoad}. */
    public void lifecycleEnterWorld() {
        Level level = machine.getLevel();
        if (level == null) {
            throw new IllegalStateException("Machine data cannot enter world without a level for " + machine.definition().id() + " at " + machine.getBlockPos());
        }
        sealSchema();
        promoteToWorldSide(level);
    }

    /** {@link MachineBlockEntity} saveAdditional. */
    public void lifecycleSavePersisted(ValueOutput output) {
        Objects.requireNonNull(output, "output");
        // Capture the generation before writing. If a serializer causes a re-entrant mutation, that
        // later generation is deliberately excluded and ChunkDataEvent.Save will re-arm the epoch.
        long snapshotGeneration = persistGeneration;
        ValueOutput root = output.child(SAVE_KEY);
        root.putInt("version", FORMAT_VERSION);
        ValueOutput scopesOut = root.child("scopes");
        for (DataScope scope : scopes.values()) {
            scope.save(scopesOut.child(scope.key()));
        }
        capturedPersistGeneration = maxGeneration(capturedPersistGeneration, snapshotGeneration);
    }

    /**
     * End of a server machine tick: recompute only. Persist and network queues are drained once by
     * their independent server-tick coordinators, so a ticking machine never performs a duplicate
     * local flush.
     */
    public void lifecycleServerTick(long gameTime) {
        requireBusinessReady("tick machine");
        lifecycleRecompute(gameTime);
    }

    /**
     * @deprecated Persistence and S2C queues now publish independently at mutation time. This
     *             compatibility hook only reasserts any outstanding requests.
     */
    @Deprecated(forRemoval = false)
    public void lifecycleFlushEndOfTick() {
        MachineDataPersistCoordinator.flushDomainNow(this);
        if (endOfTickSyncRequested) {
            MachineDataSyncBatcher.markClientDirty(this);
        }
    }

    /**
     * Refresh computed fields. Server-live only; no-op otherwise. {@code Long.MIN_VALUE} bypasses
     * per-tick dedupe (used after enter-world).
     */
    public void lifecycleRecompute(long gameTime) {
        if (phase != Phase.SERVER_LIVE || computedFieldsCache.length == 0) {
            return;
        }
        if (gameTime != Long.MIN_VALUE && gameTime == lastRecomputeGameTime) {
            return;
        }
        lastRecomputeGameTime = gameTime;
        for (DataField field : computedFieldsCache) {
            field.recompute();
        }
    }

    /** Full S2C snapshot (update-tag / first track). Moves client to {@link Phase#CLIENT_LIVE}. */
    public void lifecycleReceiveClientBaseline(
                                               byte[] data, RegistryAccess registryAccess, ConnectionType connectionType) {
        Objects.requireNonNull(data, "data");
        runWithSuppressedMutationSideEffects(
                () -> applySync(DataSyncDirection.TO_CLIENT, data, registryAccess, connectionType));
        clientBaselineReceived = true;
        phase = Phase.CLIENT_LIVE;
        clearRuntimeDirty();
    }

    /** Incremental S2C delta; requires a prior baseline. */
    public void lifecycleReceiveClientDelta(
                                            byte[] data, RegistryAccess registryAccess, ConnectionType connectionType) {
        if (!clientBaselineReceived) {
            throw new IllegalStateException("Cannot apply machine data delta before initial client baseline for " + machine.definition().id() + " at " + machine.getBlockPos() + "; deltas are incremental and require a full update-tag snapshot first");
        }
        runWithSuppressedMutationSideEffects(
                () -> applySync(DataSyncDirection.TO_CLIENT, data, registryAccess, connectionType));
    }

    /** C2S upload apply (when enabled). */
    public void lifecycleReceiveClientUpload(
                                             byte[] data, RegistryAccess registryAccess, ConnectionType connectionType) {
        requireBusinessReady("apply client machine data upload");
        applySync(DataSyncDirection.TO_SERVER, data, registryAccess, connectionType);
    }

    /** Coordinator-only: enter the current persist epoch; the coordinator marks the chunk once. */
    boolean lifecycleMarkPersistEpoch() {
        Level level = machine.getLevel();
        if (!(level instanceof ServerLevel) || persistAwaitingSnapshot || persistGeneration <= acknowledgedPersistGeneration) {
            return false;
        }
        markedPersistGeneration = persistGeneration;
        persistAwaitingSnapshot = true;
        return true;
    }

    /**
     * Real chunk-save acknowledgement. Called only from {@code ChunkDataEvent.Save}, after the
     * chunk's complete serializable snapshot has been captured on the server thread.
     */
    void lifecycleAcknowledgePersistSnapshot() {
        if (!persistAwaitingSnapshot) {
            return;
        }
        persistAwaitingSnapshot = false;
        if (capturedPersistGeneration >= markedPersistGeneration) {
            acknowledgedPersistGeneration = maxGeneration(acknowledgedPersistGeneration, capturedPersistGeneration);
        }
        if (persistGeneration > acknowledgedPersistGeneration) {
            queuePersistEpoch();
        }
    }

    /** Unload/removal backstop: make a pending epoch visible to vanilla before detaching. */
    public void lifecyclePrepareRemoval() {
        MachineDataPersistCoordinator.flushDomainNow(this);
        MachineDataPersistCoordinator.detach(this);
    }

    /**
     * @deprecated Use the automatic persist coordinator. Retained for integrations that invoke the
     *             previous save backstop explicitly.
     */
    @Deprecated(forRemoval = false)
    public void lifecyclePersistResidualForSave() {
        Level level = machine.getLevel();
        if (level != null) {
            MachineDataPersistCoordinator.flushResidualPersistForSave(level);
        }
    }

    /** Full S2C snapshot bytes for update-tag / first track. Does not clear delta dirty bits. */
    public byte[] lifecycleWriteUpdateTag(RegistryAccess registryAccess) {
        return encodeSync(
                DataSyncDirection.TO_CLIENT,
                registryAccess,
                ConnectionType.OTHER,
                MachineDataSyncCodec.WriteMode.FULL_SNAPSHOT);
    }

    public byte[] lifecycleDrainClientDelta(RegistryAccess registryAccess, ConnectionType connectionType) {
        try {
            return encodeSync(
                    DataSyncDirection.TO_CLIENT,
                    registryAccess,
                    connectionType,
                    MachineDataSyncCodec.WriteMode.DRAIN_DIRTY_DELTA);
        } finally {
            endOfTickSyncRequested = false;
        }
    }

    public void lifecycleDropClientDelta() {
        for (DataField field : syncToClientFields) {
            field.clearSyncDirty(DataSyncDirection.TO_CLIENT);
        }
        endOfTickSyncRequested = false;
    }

    public void lifecycleSendToServer(boolean force) {
        Level level = machine.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }
        requireBusinessReady("send machine data to server");
        byte[] payload = encodeSync(
                DataSyncDirection.TO_SERVER,
                level.registryAccess(),
                ConnectionType.NEOFORGE,
                force ? MachineDataSyncCodec.WriteMode.FULL_SNAPSHOT : MachineDataSyncCodec.WriteMode.DRAIN_DIRTY_DELTA);
        if (payload.length == 0) {
            return;
        }
        MachineDataC2SPayload.sendToServer(machine.getBlockPos().asLong(), payload);
    }

    // =============================================================================================
    // is* / require* — queries and guards
    // =============================================================================================

    public Phase phase() {
        return phase;
    }

    /** Business field R/W and runtime operations allowed ({@link Phase#SERVER_LIVE} / {@link Phase#CLIENT_LIVE}). */
    public boolean isBusinessReady() {
        return phase.allowsBusinessAccess();
    }

    public void requireBusinessReady(String operation) {
        if (!phase.allowsBusinessAccess()) {
            throw accessException(operation, null);
        }
    }

    /** Client domain (waiting or live). Storage traits treat mirror writes as non-authoritative. */
    public boolean isClientSide() {
        return phase == Phase.CLIENT_WAITING || phase == Phase.CLIENT_LIVE;
    }

    /**
     * Framework is applying persist NBT or client sync into handlers — no business dirty / recipe
     * invalidation.
     */
    public boolean isMutationSuppressed() {
        return suppressMutationSideEffectsDepth > 0;
    }

    public void requireFieldRead(DataField field) {
        if (!phase.allowsBusinessAccess()) {
            throw accessException("read", field);
        }
    }

    public void requireFieldWrite(DataField field) {
        if (!phase.allowsBusinessAccess()) {
            throw accessException("write", field);
        }
    }

    public void requireSchemaOpen(String operation) {
        if (schemaSealed) {
            throw new IllegalStateException("Cannot " + operation + " for " + machine.definition().id() + " at " + machine.getBlockPos() + " because machine data schema is sealed");
        }
    }

    public boolean hasClientToServerFields() {
        return !syncToServerFields.isEmpty();
    }

    // =============================================================================================
    // Schema + dirty bookkeeping
    // =============================================================================================

    public DataScope scope(ComponentKey<?> key) {
        Objects.requireNonNull(key, "trait key");
        return scope(key.id().toString());
    }

    public DataScope scope(String key) {
        DataScope existing = scopes.get(key);
        if (existing != null) {
            return existing;
        }
        requireSchemaOpen("create data scope '" + key + "'");
        DataScope created = new DataScope(this, null, key);
        scopes.put(key, created);
        return created;
    }

    public void enableClientSync(DataField field) {
        enableSyncField(field, DataSyncDirection.TO_CLIENT, syncToClientFields);
    }

    public void enableServerSync(DataField field) {
        throw new UnsupportedOperationException("TO_SERVER machine data sync is disabled until " + "menu/session authorization and field validators are implemented for " + field.path());
    }

    void recordManualPersistMutation() {
        recordPersistMutation();
    }

    public void requestEndOfTickSync() {
        endOfTickSyncRequested = true;
        MachineDataSyncBatcher.markClientDirty(this);
    }

    /** Called from {@link DataField#changed()} after dirty bits are raised. */
    public void notifyFieldChanged(DataField field) {
        if (isMutationSuppressed()) {
            return;
        }
        if (field.persistEnabled()) {
            recordPersistMutation();
        }
        if (field.isSyncDirty(DataSyncDirection.TO_CLIENT)) {
            requestEndOfTickSync();
        }
    }

    @SuppressWarnings("resource")
    public @Nullable ServerLevel serverLevel() {
        Level level = machine.getLevel();
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    public ChunkPos chunkPos() {
        return new ChunkPos(machine.getBlockPos().getX() >> 4, machine.getBlockPos().getZ() >> 4);
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    private void promoteToWorldSide(Level level) {
        if (level.isClientSide()) {
            phase = clientBaselineReceived ? Phase.CLIENT_LIVE : Phase.CLIENT_WAITING;
        } else {
            phase = Phase.SERVER_LIVE;
        }
    }

    private void applyPersistedScopes(ValueInput input) {
        input.child(SAVE_KEY).ifPresent(root -> {
            int version = root.getIntOr("version", -1);
            if (version != FORMAT_VERSION) {
                throw new IllegalStateException("Unsupported machine_data persist version " + version + " for " + machine.definition().id() + " at " + machine.getBlockPos());
            }
            root.child("scopes").ifPresent(scopesIn -> {
                for (DataScope scope : scopes.values()) {
                    scopesIn.child(scope.key()).ifPresent(scope::load);
                }
            });
        });
    }

    private void runWithSuppressedMutationSideEffects(Runnable action) {
        suppressMutationSideEffectsDepth++;
        try {
            action.run();
        } finally {
            suppressMutationSideEffectsDepth--;
        }
    }

    private void sealSchema() {
        if (schemaSealed) {
            return;
        }
        schemaSealed = true;
        buildHotPathFieldCaches();
    }

    private byte[] encodeSync(
                              DataSyncDirection direction,
                              RegistryAccess registryAccess,
                              ConnectionType connectionType,
                              MachineDataSyncCodec.WriteMode writeMode) {
        return MachineDataSyncCodec.write(
                machine,
                direction,
                syncFields(direction),
                registryAccess,
                connectionType,
                writeMode);
    }

    private void applySync(
                           DataSyncDirection direction,
                           byte[] data,
                           RegistryAccess registryAccess,
                           ConnectionType connectionType) {
        MachineDataSyncCodec.read(
                machine,
                direction,
                syncFields(direction),
                data,
                registryAccess,
                connectionType);
    }

    private List<DataField> allFields() {
        List<DataField> result = new ArrayList<>();
        for (DataScope scope : scopes.values()) {
            scope.collectFields(result);
        }
        return result;
    }

    private void buildHotPathFieldCaches() {
        List<DataField> computed = new ArrayList<>();
        for (DataField field : allFields()) {
            if (field.isComputed()) {
                computed.add(field);
            }
        }
        computedFieldsCache = computed.isEmpty() ? NO_FIELDS : computed.toArray(DataField[]::new);
    }

    private void clearRuntimeDirty() {
        for (DataScope scope : scopes.values()) {
            scope.clearRuntimeDirty();
        }
        MachineDataPersistCoordinator.detach(this);
        persistGeneration = 0L;
        capturedPersistGeneration = 0L;
        acknowledgedPersistGeneration = 0L;
        markedPersistGeneration = 0L;
        persistAwaitingSnapshot = false;
        endOfTickSyncRequested = false;
    }

    private void recordPersistMutation() {
        ServerLevel level = serverLevel();
        if (level == null) {
            return;
        }
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Authoritative machine data must be mutated on the server thread for " + machine.definition().id() + " at " + machine.getBlockPos());
        }
        persistGeneration = nextGeneration(persistGeneration);
        if (!persistAwaitingSnapshot) {
            queuePersistEpoch();
        }
    }

    private void queuePersistEpoch() {
        MachineDataPersistCoordinator.enqueue(this);
    }

    private static long nextGeneration(long generation) {
        if (generation == Long.MAX_VALUE) {
            throw new IllegalStateException("Machine persist generation overflow");
        }
        return generation + 1L;
    }

    private static long maxGeneration(long first, long second) {
        return Math.max(first, second);
    }

    private void enableSyncField(
                                 DataField field,
                                 DataSyncDirection direction,
                                 List<DataField> fields) {
        requireSchemaOpen("enable " + direction + " sync for data field '" + field.path() + "'");
        if (field.syncSlot(direction) >= 0) {
            return;
        }
        int slot = fields.size();
        field.assignSyncSlot(direction, slot);
        fields.add(field);
    }

    private List<DataField> syncFields(DataSyncDirection direction) {
        return direction == DataSyncDirection.TO_CLIENT ? syncToClientFields : syncToServerFields;
    }

    private IllegalStateException accessException(String operation, @Nullable DataField field) {
        String target = field == null ? "machine data" : "machine data field '" + field.path() + "'";
        return new IllegalStateException("Cannot " + operation + " " + target + " for " + machine.definition().id() + " at " + machine.getBlockPos() + " while phase=" + phase + ". Defaults may exist in memory until lifecycleAttachLevel/lifecycleEnterWorld " + "or client baseline completes.");
    }

    /**
     * Domain phase. Trait authors typically only need {@link #isBusinessReady()} /
     * {@link #isClientSide()}.
     */
    public enum Phase {

        /** Constructed; traits may register schema; no business field R/W. */
        BOOTSTRAP(false),
        /** Framework applying disk NBT; mutation side effects suppressed. */
        APPLYING_PERSIST(false),
        /** Server authoritative live state. */
        SERVER_LIVE(true),
        /** Client has level but no full S2C baseline yet. */
        CLIENT_WAITING(false),
        /** Client mirror is live (read/write of mirror; storage not authoritative). */
        CLIENT_LIVE(true);

        private final boolean businessAccess;

        Phase(boolean businessAccess) {
            this.businessAccess = businessAccess;
        }

        boolean allowsBusinessAccess() {
            return businessAccess;
        }
    }
}
