package net.ptcrys.topo.apiv2.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A {@code boolean} machine field. */
public final class DataBoolean extends DataField {

    private boolean value;

    DataBoolean(DataScope owner, String key, boolean initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = initialValue;
    }

    public boolean value() {
        requireReadAccess();
        return value;
    }

    /**
     * UI / structural-phase safe read. Returns the live value when the domain is business-ready;
     * otherwise {@code fallback} without throwing. Prefer this in {@code collectMachineUi} and
     * other client paths that may run before the S2C baseline.
     */
    public boolean valueOrElse(boolean fallback) {
        if (!isBusinessReady()) {
            return fallback;
        }
        return value;
    }

    public void set(boolean value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(value);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    void applyComputed(Object value) {
        applyValue((Boolean) value);
    }

    private void applyValue(boolean value) {
        if (this.value == value) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putBoolean(key(), value);
    }

    @Override
    void readPersist(ValueInput input) {
        value = input.getBooleanOr(key(), value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = (Boolean) value;
    }

    @Override
    String schemaTypeId() {
        return "boolean";
    }
}
