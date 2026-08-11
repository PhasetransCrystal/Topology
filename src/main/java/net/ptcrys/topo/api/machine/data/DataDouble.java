package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A {@code double} machine field. */
public final class DataDouble extends DataField {

    private double value;

    DataDouble(DataScope owner, String key, double initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = initialValue;
    }

    public double value() {
        requireReadAccess();
        return value;
    }

    public void set(double value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    @Override
    void applyComputed(Object value) {
        applyValue((Double) value);
    }

    private void applyValue(double value) {
        if (Double.compare(this.value, value) == 0) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putDouble(key(), value);
    }

    @Override
    void readPersist(ValueInput input) {
        value = input.getDoubleOr(key(), value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readDouble();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = (Double) value;
    }

    @Override
    String schemaTypeId() {
        return "double";
    }
}
