package net.ptcrys.topo.api.machine.data.network;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.machine.MachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jspecify.annotations.NonNull;

public record MachineDataC2SPayload(long pos, byte[] data) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Topology.MODID, "machine_data_c2s");
    public static final Type<MachineDataC2SPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineDataC2SPayload> CODEC = StreamCodec.ofMember(MachineDataC2SPayload::write, MachineDataC2SPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToServer(long pos, byte[] data) {
        ClientPacketDistributor.sendToServer(new MachineDataC2SPayload(pos, data));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(pos);
        buffer.writeByteArray(data);
    }

    private static MachineDataC2SPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MachineDataC2SPayload(buffer.readLong(), buffer.readByteArray());
    }

    public static void execute(MachineDataC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getBlockEntity(BlockPos.of(payload.pos)) instanceof MachineBlockEntity machine) {
            if (!machine.data().hasClientToServerFields()) {
                return;
            }
            machine.data().lifecycleReceiveClientUpload(
                    payload.data,
                    player.level().registryAccess(),
                    context.listener().getConnectionType());
        }
    }
}
