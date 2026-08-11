package net.ptcrys.topo.api.machine.data;

import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A nullable {@link ResourceKey} machine field for some registry {@code T}. */
public final class DataResourceKey<T> extends DataField {

    /** Non-null wire marker for a decoded absent key, kept distinct from a real {@link ResourceKey}. */
    private static final Object ABSENT = new Object();

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final Codec<ResourceKey<T>> codec;
    private @Nullable ResourceKey<T> value;

    DataResourceKey(
                    DataScope owner,
                    String key,
                    ResourceKey<? extends Registry<T>> registryKey,
                    @Nullable ResourceKey<T> initialValue,
                    FieldPolicy policy) {
        super(owner, key, policy);
        this.registryKey = registryKey;
        this.codec = ResourceKey.codec(registryKey);
        this.value = initialValue;
    }

    public @Nullable ResourceKey<T> value() {
        requireReadAccess();
        return value;
    }

    public void set(@Nullable ResourceKey<T> value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    void applyComputed(Object value) {
        applyValue(value instanceof ResourceKey<?> key ? (ResourceKey<T>) key : null);
    }

    private void applyValue(@Nullable ResourceKey<T> value) {
        if (Objects.equals(this.value, value)) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.storeNullable(key(), codec, value);
    }

    @Override
    void readPersist(ValueInput input) {
        // Safety contract: an absent/invalid value in an existing scope is not an explicit null.
        // It keeps the declared/default value instead of silently erasing it.
        value = input.read(key(), codec).orElse(value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeResourceKey(value);
        }
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        // A present flag of false decodes to the ABSENT sentinel so the whole sync frame can still be
        // validated before any field is mutated; applyDecodedSyncValue maps it back to a null key.
        return buffer.readBoolean() ? buffer.readResourceKey(registryKey) : ABSENT;
    }

    @Override
    @SuppressWarnings("unchecked")
    void applyDecodedSyncValue(Object value) {
        this.value = value instanceof ResourceKey<?> key ? (ResourceKey<T>) key : null;
    }

    @Override
    String schemaTypeId() {
        return "resource_key:" + registryKey.identifier();
    }
}
