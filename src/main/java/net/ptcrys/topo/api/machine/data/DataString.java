package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** A non-null {@link String} machine field. */
public final class DataString extends DataField {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataString.class);
    /** Matches {@link RegistryFriendlyByteBuf#writeUtf(String)}'s default wire limit. */
    private static final int MAX_LENGTH = 32_767;

    private String value;

    DataString(DataScope owner, String key, String initialValue, FieldPolicy policy) {
        super(owner, key, policy);
        this.value = requireValid(initialValue);
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
        applyValue(requireValid(value));
    }

    @Override
    void applyComputed(Object value) {
        applyValue(requireValid((String) value));
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
        String stored = input.getStringOr(key(), value);
        if (stored.length() > MAX_LENGTH) {
            LOGGER.warn(
                    "Ignoring oversized persisted value for machine data field {} ({} > {} characters); keeping '{}'",
                    path(), stored.length(), MAX_LENGTH, value);
            return;
        }
        value = stored;
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(value, MAX_LENGTH);
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf();
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        this.value = requireValid((String) value);
    }

    private String requireValid(String value) {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Machine data string field " + path() + " exceeds " + MAX_LENGTH + " characters");
        }
        return value;
    }

    @Override
    String schemaTypeId() {
        return "string";
    }
}
