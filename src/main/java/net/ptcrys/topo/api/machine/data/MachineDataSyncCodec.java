package net.ptcrys.topo.api.machine.data;

import net.ptcrys.topo.api.machine.MachineBlockEntity;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

final class MachineDataSyncCodec {

    private MachineDataSyncCodec() {}

    static byte[] write(
                        MachineBlockEntity machine,
                        DataSyncDirection direction,
                        List<DataField> fields,
                        RegistryAccess registryAccess,
                        ConnectionType connectionType,
                        WriteMode writeMode) {
        if (fields.isEmpty()) {
            return new byte[0];
        }
        long[] dirtyWords = dirtyWords(direction, fields, writeMode.force);
        if (!hasAnyBit(dirtyWords)) {
            return new byte[0];
        }
        var byteBuf = Unpooled.buffer();
        var buffer = new RegistryFriendlyByteBuf(byteBuf, registryAccess, connectionType);
        try {
            writeHeader(buffer, machine, direction, fields, dirtyWords);
            writeDirtyFieldValues(buffer, direction, fields, dirtyWords, writeMode.clearDirty);
            return copyReadableBytes(byteBuf);
        } finally {
            byteBuf.release();
        }
    }

    static void read(
                     MachineBlockEntity machine,
                     DataSyncDirection direction,
                     List<DataField> fields,
                     byte[] data,
                     RegistryAccess registryAccess,
                     ConnectionType connectionType) {
        if (data.length == 0) {
            return;
        }
        var byteBuf = Unpooled.wrappedBuffer(data);
        var buffer = new RegistryFriendlyByteBuf(byteBuf, registryAccess, connectionType);
        try {
            validateHeader(buffer, machine, direction, fields);
            long[] dirtyWords = readDirtyWords(buffer, fields.size());
            List<DecodedSyncValue> decodedValues = decodeDirtyValues(buffer, fields, dirtyWords);
            rejectTrailingBytes(byteBuf.readableBytes(), machine);
            applyDecodedValues(decodedValues);
        } finally {
            byteBuf.release();
        }
    }

    private static void writeHeader(
                                    RegistryFriendlyByteBuf buffer,
                                    MachineBlockEntity machine,
                                    DataSyncDirection direction,
                                    List<DataField> fields,
                                    long[] dirtyWords) {
        buffer.writeVarInt(MachineDataScope.FORMAT_VERSION);
        buffer.writeLong(schemaHash(machine, direction, fields));
        buffer.writeVarInt(fields.size());
        buffer.writeVarInt(dirtyWords.length);
        for (long word : dirtyWords) {
            buffer.writeLong(word);
        }
    }

    private static void writeDirtyFieldValues(
                                              RegistryFriendlyByteBuf buffer,
                                              DataSyncDirection direction,
                                              List<DataField> fields,
                                              long[] dirtyWords,
                                              boolean clearDirty) {
        for (int i = 0; i < fields.size(); i++) {
            if (bit(dirtyWords, i)) {
                DataField field = fields.get(i);
                field.writeSync(buffer);
                if (clearDirty) {
                    field.clearSyncDirty(direction);
                }
            }
        }
    }

    private static byte[] copyReadableBytes(ByteBuf byteBuf) {
        byteBuf.readerIndex(0);
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        return data;
    }

    private static void validateHeader(
                                       RegistryFriendlyByteBuf buffer,
                                       MachineBlockEntity machine,
                                       DataSyncDirection direction,
                                       List<DataField> fields) {
        int version = buffer.readVarInt();
        if (version != MachineDataScope.FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported machine data sync version " + version);
        }
        long remoteSchemaHash = buffer.readLong();
        long localSchemaHash = schemaHash(machine, direction, fields);
        if (remoteSchemaHash != localSchemaHash) {
            throw new IllegalStateException("Machine data sync schema mismatch for " + machine.definition().id() + " at " + machine.getBlockPos() + ", local=" + Long.toUnsignedString(localSchemaHash, 16) + ", remote=" + Long.toUnsignedString(remoteSchemaHash, 16));
        }
        int remoteFieldCount = buffer.readVarInt();
        if (remoteFieldCount != fields.size()) {
            throw new IllegalStateException("Machine data sync field count mismatch, local=" + fields.size() + ", remote=" + remoteFieldCount);
        }
    }

    private static long[] readDirtyWords(RegistryFriendlyByteBuf buffer, int fieldCount) {
        int wordCount = buffer.readVarInt();
        int expectedWordCount = wordCount(fieldCount);
        if (wordCount != expectedWordCount) {
            throw new IllegalStateException("Machine data sync dirty word count mismatch, local=" + expectedWordCount + ", remote=" + wordCount);
        }
        long[] dirtyWords = new long[wordCount];
        for (int i = 0; i < wordCount; i++) {
            dirtyWords[i] = buffer.readLong();
        }
        rejectOutOfRangeBits(dirtyWords, fieldCount);
        return dirtyWords;
    }

    private static List<DecodedSyncValue> decodeDirtyValues(
                                                            RegistryFriendlyByteBuf buffer,
                                                            List<DataField> fields,
                                                            long[] dirtyWords) {
        List<DecodedSyncValue> decodedValues = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            if (bit(dirtyWords, i)) {
                DataField field = fields.get(i);
                decodedValues.add(new DecodedSyncValue(field, field.readSyncValue(buffer)));
            }
        }
        return decodedValues;
    }

    private static void rejectTrailingBytes(int readableBytes, MachineBlockEntity machine) {
        if (readableBytes != 0) {
            throw new IllegalStateException("Machine data sync frame has trailing " + readableBytes + " byte(s) for " + machine.definition().id() + " at " + machine.getBlockPos());
        }
    }

    private static void applyDecodedValues(List<DecodedSyncValue> decodedValues) {
        for (DecodedSyncValue decodedValue : decodedValues) {
            decodedValue.apply();
        }
    }

    private static long[] dirtyWords(DataSyncDirection direction, List<DataField> fields, boolean force) {
        long[] words = new long[wordCount(fields.size())];
        for (int i = 0; i < fields.size(); i++) {
            if (force || fields.get(i).isSyncDirty(direction)) {
                words[i / Long.SIZE] |= 1L << (i % Long.SIZE);
            }
        }
        return words;
    }

    private static int wordCount(int fieldCount) {
        return (fieldCount + Long.SIZE - 1) / Long.SIZE;
    }

    private static void rejectOutOfRangeBits(long[] words, int fieldCount) {
        if (words.length == 0 || fieldCount == 0) {
            return;
        }
        int usedBitsInLastWord = fieldCount % Long.SIZE;
        if (usedBitsInLastWord == 0) {
            return;
        }
        long outOfRangeMask = -1L << usedBitsInLastWord;
        long outOfRangeBits = words[words.length - 1] & outOfRangeMask;
        if (outOfRangeBits != 0L) {
            throw new IllegalStateException("Machine data sync dirty bitset contains out-of-range bits");
        }
    }

    private static long schemaHash(MachineBlockEntity machine, DataSyncDirection direction, List<DataField> fields) {
        // Safety contract: positional bitsets stay compact, but every frame carries a stable schema
        // fingerprint so equal field counts cannot hide key/type/order mismatches.
        long hash = 0xcbf29ce484222325L;
        hash = hashString(hash, machine.definition().id().toString());
        hash = hashString(hash, direction.name());
        for (int i = 0; i < fields.size(); i++) {
            DataField field = fields.get(i);
            hash = hashLong(hash, i);
            hash = hashString(hash, field.path());
            hash = hashString(hash, field.schemaTypeId());
        }
        return hash;
    }

    private static long hashString(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        hash ^= 0xff;
        return hash * 0x100000001b3L;
    }

    private static long hashLong(long hash, long value) {
        for (int i = 0; i < Long.BYTES; i++) {
            hash ^= (value >>> (i * Byte.SIZE)) & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static boolean bit(long[] words, int index) {
        int word = index / Long.SIZE;
        return word < words.length && (words[word] & (1L << (index % Long.SIZE))) != 0L;
    }

    private static boolean hasAnyBit(long[] words) {
        for (long word : words) {
            if (word != 0L) {
                return true;
            }
        }
        return false;
    }

    private record DecodedSyncValue(DataField field, Object value) {

        void apply() {
            field.applyDecodedSyncValue(value);
        }
    }

    enum WriteMode {

        FULL_SNAPSHOT(true, false),
        DRAIN_DIRTY_DELTA(false, true);

        private final boolean force;
        private final boolean clearDirty;

        WriteMode(boolean force, boolean clearDirty) {
            this.force = force;
            this.clearDirty = clearDirty;
        }
    }
}
