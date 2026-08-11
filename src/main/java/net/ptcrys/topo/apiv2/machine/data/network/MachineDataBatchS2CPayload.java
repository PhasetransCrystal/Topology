package net.ptcrys.topo.apiv2.machine.data.network;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record MachineDataBatchS2CPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Topology.MODID, "machine_data_batch_s2c");
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
            if (level.getBlockEntity(BlockPos.of(entry.pos())) instanceof MachineBlockEntity machine) {
                machine.data().lifecycleReceiveClientDelta(
                        entry.data(),
                        level.registryAccess(),
                        connectionType);
            }
        }
    }

    public record Entry(long pos, byte[] data) {}
}
