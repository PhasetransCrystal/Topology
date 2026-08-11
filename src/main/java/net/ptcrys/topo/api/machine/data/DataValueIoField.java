package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Objects;

/**
 * A persist-only field backed by externally-owned state (e.g. a resource handler). It has no network
 * codec, so its builder ({@link DataFieldBuilder}) exposes no sync methods at all — the value never
 * participates in client sync. Signal changes with {@link #markDirty()}.
 */
public final class DataValueIoField extends DataManualDirtyField {

    private final PersistWriter writer;
    private final PersistReader reader;
    private final Runnable afterRead;

    DataValueIoField(
                     DataScope owner,
                     String key,
                     PersistWriter writer,
                     PersistReader reader,
                     Runnable afterRead,
                     FieldPolicy policy) {
        super(owner, key, policy);
        this.writer = Objects.requireNonNull(writer, "writer");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.afterRead = Objects.requireNonNull(afterRead, "afterRead");
    }

    @Override
    void writePersist(ValueOutput output) {
        writer.write(output.child(key()));
    }

    @Override
    void readPersist(ValueInput input) {
        input.child(key()).ifPresent(child -> {
            reader.read(child);
            afterRead.run();
        });
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        throw new UnsupportedOperationException("ValueIO fields do not have a network codec");
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        throw new UnsupportedOperationException("ValueIO fields do not have a network codec");
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        throw new UnsupportedOperationException("ValueIO fields do not have a network codec");
    }

    @Override
    String schemaTypeId() {
        return "value_io";
    }

    @FunctionalInterface
    public interface PersistWriter {

        void write(ValueOutput output);
    }

    @FunctionalInterface
    public interface PersistReader {

        void read(ValueInput input);
    }
}
