package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A {@code long} machine field. */
public final class DataLong extends DataField {

    private long value;

    DataLong(DataScope owner, String key, long initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = initialValue;
    }

    public long value() {
        requireReadAccess();
        return value;
    }

    public void set(long value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    public void inc() {
        set(value + 1L);
    }

    public void dec() {
        set(value - 1L);
    }

    @Override
    void applyComputed(Object value) {
        applyValue((Long) value);
    }

    private void applyValue(long value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putLong(key(), value);
    }

    @Override
    void readPersist(ValueInput input) {
        value = input.getLongOr(key(), value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readVarLong();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = (Long) value;
    }

    @Override
    String schemaTypeId() {
        return "long";
    }
}
