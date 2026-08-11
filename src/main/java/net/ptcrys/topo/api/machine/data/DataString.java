package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Objects;

/** A non-null {@link String} machine field. */
public final class DataString extends DataField {

    private String value;

    DataString(DataScope owner, String key, String initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = Objects.requireNonNull(initialValue, "initial value");
    }

    public String value() {
        requireReadAccess();
        return value;
    }

    /**
     * UI / structural-phase safe read. Returns the live value when the domain is business-ready;
     * otherwise {@code fallback} without throwing. Prefer this in {@code collectMachineUi} and
     * other client paths that may run before the S2C baseline.
     */
    public String valueOrElse(String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (!isBusinessReady()) {
            return fallback;
        }
        return value;
    }

    public void set(String value) {
        requireNotComputed();
        requireWriteAccess();
        applyValue(Objects.requireNonNull(value, "value"));
    }

    @Override
    void applyComputed(Object value) {
        applyValue(Objects.requireNonNull((String) value, "value"));
    }

    private void applyValue(String value) {
        if (this.value.equals(value)) {
            return;
        }
        this.value = value;
        changed();
    }

    @Override
    void writePersist(ValueOutput output) {
        output.putString(key(), value);
    }

    @Override
    void readPersist(ValueInput input) {
        value = input.getStringOr(key(), value);
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(value);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = Objects.requireNonNull((String) value, "value");
    }

    @Override
    String schemaTypeId() {
        return "string";
    }
}
