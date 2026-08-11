package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;
import java.util.Objects;

/** An enum machine field. Enum names are the persist and wire ABI, never ordinals. */
public final class DataEnum<E extends Enum<E>> extends DataField {

    private final Class<E> enumType;
    private final E[] constants;
    private E value;

    DataEnum(DataScope owner, String key, Class<E> enumType, E initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.enumType = Objects.requireNonNull(enumType, "enum type");
        this.constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new IllegalArgumentException(enumType.getName() + " has no enum constants");
        }
        this.value = Objects.requireNonNull(initialValue, "initial value");
    }

    public E value() {
        requireReadAccess();
        return value;
    }

    public void set(E value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(Objects.requireNonNull(value, "value"));
    }

    @Override
    @SuppressWarnings("unchecked")
    void applyComputed(Object value) {
        applyValue((E) Objects.requireNonNull(value, "value"));
    }

    private void applyValue(E value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putString(key(), value.name());
    }

    @Override
    void readPersist(ValueInput input) {
        String name = input.getStringOr(key(), value.name());
        try {
            value = Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException("Unknown enum value '" + name + "' for machine data field " + path() + " of " + enumType.getName());
        }
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        // Safety contract: enum names are the wire ABI. Ordinals are compact, but make source
        // declaration order a hidden network contract and can silently remap state across versions.
        buffer.writeUtf(value.name());
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        String name = buffer.readUtf();
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException("Unknown enum sync value '" + name + "' for machine data field " + path() + " of " + enumType.getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    void applyDecodedSyncValue(Object value) {
        this.value = Objects.requireNonNull((E) value, "value");
    }

    @Override
    String schemaTypeId() {
        return "enum:" + enumType.getName() + ":" + Arrays.toString(constants);
    }
}
