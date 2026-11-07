package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Objects;

/** 通过 Codec 扩展值类型，沿用机器数据域的持久化、生命周期和脏字段同步。 */
public final class DataValue<T> extends DataField {

    private final DataKey<T> definition;
    private T value;
    private boolean assigned;

    DataValue(DataScope owner, DataKey<T> definition, FieldPolicy policy) {
        super(owner, definition.id().toString(), policy);
        this.definition = definition;
        this.value = definition.copy(definition.initialValue());
    }

    public boolean assigned() {
        requireReadAccess();
        return assigned;
    }

    public DataKey<T> definition() {
        return definition;
    }

    public T value() {
        requireReadAccess();
        return definition.copy(value);
    }

    public void set(T value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    private void applyValue(T value) {
        T copied = definition.copy(value);
        if (!assigned || !Objects.equals(this.value, copied)) {
            this.value = copied;
            assigned = true;
            changed();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    void applyComputed(Object value) {
        applyValue((T) value);
    }

    @Override
    void writePersist(ValueOutput output) {
        ValueOutput nested = output.child(key());
        nested.store("value", definition.codec(), value);
        nested.putBoolean("assigned", assigned);
    }

    @Override
    void readPersist(ValueInput input) {
        input.child(key()).ifPresent(nested -> nested.read("value", definition.codec()).ifPresent(decoded -> {
            value = definition.copy(decoded);
            assigned = nested.getBooleanOr("assigned", false);
        }));
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(assigned);
        ByteBufCodecs.fromCodecWithRegistries(definition.codec()).encode(buffer, value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return new Decoded<>(buffer.readBoolean(), definition.copy(ByteBufCodecs.fromCodecWithRegistries(definition.codec()).decode(buffer)));
    }

    @Override
    @SuppressWarnings("unchecked")
    void applyDecodedSyncValue(Object value) {
        Decoded<T> decoded = (Decoded<T>) value;
        this.value = definition.copy(decoded.value());
        this.assigned = decoded.assigned();
    }

    void restore(T value, boolean assigned) {
        requireNotComputed();
        requireWriteAccess();
        T copied = definition.copy(value);
        if (this.assigned != assigned || !Objects.equals(this.value, copied)) {
            this.value = copied;
            this.assigned = assigned;
            changed();
        }
    }

    private record Decoded<T>(boolean assigned, T value) {}

    @Override
    String schemaTypeId() {
        return "value:" + definition.id();
    }
}
