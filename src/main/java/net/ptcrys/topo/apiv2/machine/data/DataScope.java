package net.ptcrys.topo.apiv2.machine.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A named container of machine data fields, owned by a {@link MachineDataScope}. Each trait gets one
 * top-level scope; scopes may nest via {@link #group(String)}. Fields are declared through the typed
 * factories, which return builders (see {@link DataFieldBuilder} / {@link SyncableFieldBuilder}):
 *
 * <pre>{@code
 * DataInt progress = data().intField("progress", 0).persisted().syncNone().done();
 * progress.set(0);
 * progress.inc();
 * int p = progress.value();   // runtime
 * data().markPersistedStateChanged();                                     // external mutable state
 * }</pre>
 *
 * <p>
 * Prefer {@link SyncableFieldBuilder#syncNone()} for gameplay/config fields. Client open-menu
 * chrome should use LDLib2 UI sync; machine-data {@link SyncableFieldBuilder#syncToClientAtEndOfDirtyTick()}
 * is only for special cases such as world rendering (prefer a render-side synchronizer when one
 * exists). See that method's javadoc.
 *
 * <p>
 * Use {@link #markPersistedStateChanged()} only when persistent state changes outside a field
 * setter. Registered fields and handler-backed fields mark themselves automatically.
 */
public final class DataScope {

    private final MachineDataScope domain;
    private final @Nullable DataScope parent;
    private final String key;
    // LinkedHashMap preserves declaration order, which IS the sync-slot order, so one ordered map
    // serves both ordered iteration and duplicate-key detection.
    private final Map<String, DataField> fieldsByKey = new LinkedHashMap<>();
    private final Map<String, DataScope> groups = new LinkedHashMap<>();

    DataScope(MachineDataScope domain, @Nullable DataScope parent, String key) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.parent = parent;
        this.key = validateKey(key);
    }

    public MachineDataScope domain() {
        return domain;
    }

    public String key() {
        return key;
    }

    /** A nested sub-scope; created lazily and only while registration is open. */
    public DataScope group(String key) {
        DataScope existing = groups.get(key);
        if (existing != null) {
            return existing;
        }
        domain.requireSchemaOpen("create data group '" + fullPath(key) + "'");
        DataScope created = new DataScope(domain, this, key);
        groups.put(key, created);
        return created;
    }

    public SyncableFieldBuilder<DataInt, Integer> intField(String key, int initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataInt(this, key, initialValue, policy));
    }

    public SyncableFieldBuilder<DataLong, Long> longField(String key, long initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataLong(this, key, initialValue, policy));
    }

    public SyncableFieldBuilder<DataBoolean, Boolean> booleanField(String key, boolean initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataBoolean(this, key, initialValue, policy));
    }

    public SyncableFieldBuilder<DataString, String> stringField(String key, String initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataString(this, key, initialValue, policy));
    }

    public SyncableFieldBuilder<DataFloat, Float> floatField(String key, float initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataFloat(this, key, initialValue, policy));
    }

    public SyncableFieldBuilder<DataDouble, Double> doubleField(String key, double initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataDouble(this, key, initialValue, policy));
    }

    public <E extends Enum<E>> SyncableFieldBuilder<DataEnum<E>, E> enumField(
                                                                              String key, Class<E> enumType, E initialValue) {
        return new SyncableFieldBuilder<>(this, key, policy -> new DataEnum<>(this, key, enumType, initialValue, policy));
    }

    public <T> SyncableFieldBuilder<DataResourceKey<T>, ResourceKey<T>> resourceKey(
                                                                                    String key, ResourceKey<? extends Registry<T>> registryKey) {
        return resourceKey(key, registryKey, null);
    }

    public <T> SyncableFieldBuilder<DataResourceKey<T>, ResourceKey<T>> resourceKey(
                                                                                    String key,
                                                                                    ResourceKey<? extends Registry<T>> registryKey,
                                                                                    @Nullable ResourceKey<T> initialValue) {
        return new SyncableFieldBuilder<>(
                this, key, policy -> new DataResourceKey<>(this, key, registryKey, initialValue, policy));
    }

    public DataFieldBuilder<DataValueIoField> valueIoField(
                                                           String key,
                                                           DataValueIoField.PersistWriter writer,
                                                           DataValueIoField.PersistReader reader,
                                                           Runnable afterRead) {
        return new DataFieldBuilder<>(this, key, policy -> new DataValueIoField(this, key, writer, reader, afterRead, policy));
    }

    public DataFieldBuilder<DataItemResourceHandler> itemResourceHandler(
                                                                         String key,
                                                                         ResourceHandler<ItemResource> handler,
                                                                         Runnable afterRead) {
        return itemResourceHandler(key, handler, DataItemResourceHandler.defaultLoader(handler), afterRead);
    }

    public DataFieldBuilder<DataItemResourceHandler> itemResourceHandler(
                                                                         String key,
                                                                         ResourceHandler<ItemResource> handler,
                                                                         DataItemResourceHandler.SlotLoader loader,
                                                                         Runnable afterRead) {
        return new DataFieldBuilder<>(
                this, key, policy -> new DataItemResourceHandler(this, key, handler, loader, afterRead, policy));
    }

    public DataFieldBuilder<DataFluidResourceHandler> fluidResourceHandler(
                                                                           String key,
                                                                           ResourceHandler<FluidResource> handler,
                                                                           Runnable afterRead) {
        return fluidResourceHandler(key, handler, DataFluidResourceHandler.defaultLoader(handler), afterRead);
    }

    public DataFieldBuilder<DataFluidResourceHandler> fluidResourceHandler(
                                                                           String key,
                                                                           ResourceHandler<FluidResource> handler,
                                                                           DataFluidResourceHandler.SlotLoader loader,
                                                                           Runnable afterRead) {
        return new DataFieldBuilder<>(
                this, key, policy -> new DataFluidResourceHandler(this, key, handler, loader, afterRead, policy));
    }

    /** Record persistent state mutated outside a registered {@link DataField} setter. */
    public void markPersistedStateChanged() {
        domain.recordManualPersistMutation();
    }

    /** Request one dirty-field client delta sync at the end of this tick. */
    public void syncAtEndOfTick() {
        domain.requestEndOfTickSync();
    }

    public String fullPath(String childKey) {
        return parent == null ? key + "/" + childKey : parent.fullPath(key) + "/" + childKey;
    }

    void save(ValueOutput output) {
        for (DataField field : fieldsByKey.values()) {
            if (field.persistEnabled()) {
                field.writePersist(output);
            }
        }
        for (DataScope group : groups.values()) {
            group.save(output.child(group.key()));
        }
    }

    void load(ValueInput input) {
        for (DataField field : fieldsByKey.values()) {
            if (field.persistEnabled()) {
                field.readPersist(input);
            }
        }
        for (DataScope group : groups.values()) {
            input.child(group.key()).ifPresent(group::load);
        }
    }

    void collectFields(List<DataField> result) {
        result.addAll(fieldsByKey.values());
        for (DataScope group : groups.values()) {
            group.collectFields(result);
        }
    }

    void clearRuntimeDirty() {
        for (DataField field : fieldsByKey.values()) {
            field.clearRuntimeDirty();
        }
        for (DataScope group : groups.values()) {
            group.clearRuntimeDirty();
        }
    }

    void register(DataField field) {
        domain.requireSchemaOpen("register data field '" + fullPath(field.key()) + "'");
        DataField previous = fieldsByKey.putIfAbsent(field.key(), field);
        if (previous != null) {
            throw new IllegalStateException("Duplicate data field '" + fullPath(field.key()) + "'");
        }
    }

    private static String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Data scope key must not be blank");
        }
        return key;
    }
}
