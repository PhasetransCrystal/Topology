package net.ptcrys.topo.apiv2.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** An {@code int} machine field. */
public final class DataInt extends DataField {

    private int value;

    DataInt(DataScope owner, String key, int initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = initialValue;
    }

    public int value() {
        requireReadAccess();
        return value;
    }

    public void set(int value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    public void inc() {
        set(value + 1);
    }

    public void dec() {
        set(value - 1);
    }

    @Override
    void applyComputed(Object value) {
        applyValue((Integer) value);
    }

    private void applyValue(int value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putInt(key(), value);
    }

    @Override
    void readPersist(ValueInput input) {
        value = input.getIntOr(key(), value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readVarInt();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = (Integer) value;
    }

    @Override
    String schemaTypeId() {
        return "int";
    }
}
