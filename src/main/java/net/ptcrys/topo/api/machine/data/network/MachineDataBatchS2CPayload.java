package net.ptcrys.topo.api.machine.data.network;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.netty.handler.codec.DecoderException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public record MachineDataBatchS2CPayload(List<Entry> entries) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineDataBatchS2CPayload.class);

    public static final Identifier ID = IdHelper.id("machine_data_batch_s2c");
    public static final Type<MachineDataBatchS2CPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineDataBatchS2CPayload> CODEC = StreamCodec.ofMember(MachineDataBatchS2CPayload::write, MachineDataBatchS2CPayload::decode);

    public MachineDataBatchS2CPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeLong(entry.pos());
            buffer.writeByteArray(entry.data());
        }
    }

    private static MachineDataBatchS2CPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        // Each entry needs at least 8 bytes of position plus a 1-byte empty payload length.
        if (count < 0 || count > buffer.readableBytes() / 9) {
            throw new DecoderException("Invalid machine data batch entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buffer.readLong(), buffer.readByteArray()));
        }
        return new MachineDataBatchS2CPayload(entries);
    }

    public static void execute(MachineDataBatchS2CPayload payload, IPayloadContext context) {
        applyToLevel(
                Minecraft.getInstance().level,
                payload.entries,
                context.listener().getConnectionType());
    }

    public static void applyToLevel(
                                    @Nullable Level level,
                                    List<Entry> entries,
                                    ConnectionType connectionType) {
        if (level == null) {
            return;
        }
        for (Entry entry : entries) {
            BlockPos pos = BlockPos.of(entry.pos());
            try {
                if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
                    machine.data().lifecycleReceiveClientDelta(
                            entry.data(),
                            level.registryAccess(),
                            connectionType);
                }
            } catch (RuntimeException error) {
                LOGGER.error("Failed to apply machine data sync at {}; dropping this entry", pos, error);
            }
        }
    }

    public record Entry(long pos, byte[] data) {}
}
