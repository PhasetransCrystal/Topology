package net.ptcrys.topo.apiv2.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A {@code float} machine field. */
public final class DataFloat extends DataField {

    private float value;

    DataFloat(DataScope owner, String key, float initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = initialValue;
    }

    public float value() {
        requireReadAccess();
        return value;
    }

    public void set(float value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    @Override
    void applyComputed(Object value) {
        applyValue((Float) value);
    }

    private void applyValue(float value) {
        if (Float.compare(this.value, value) == 0) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putFloat(key(), value);
    }

    @Override
    void readPersist(ValueInput input) {
        value = input.getFloatOr(key(), value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeFloat(value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readFloat();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = (Float) value;
    }

    @Override
    String schemaTypeId() {
        return "float";
    }
}
