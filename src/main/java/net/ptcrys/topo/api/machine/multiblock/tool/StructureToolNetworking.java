package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.client.structure.StructureToolClient;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import io.netty.handler.codec.DecoderException;

/** 工具协议：服务端发送完整报告，客户端只能刷新已授权的检测会话，放置操作不接受客户端提供的位置或方块列表。 */
public final class StructureToolNetworking {

    private StructureToolNetworking() {}

    public static void register(IEventBus bus) {
        bus.addListener(StructureToolNetworking::payloads);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> StructureToolManager.forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> StructureToolManager.clear());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(Topology.MODID);
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> StructureToolClient.apply(payload.snapshot()));
        registrar.playToServer(Refresh.TYPE, Refresh.CODEC, (payload, context) -> { if (context.player() instanceof ServerPlayer player) StructureToolManager.refresh(player); });
    }

    public record Snapshot(StructureToolSnapshot snapshot) implements CustomPacketPayload {

        public static final Type<Snapshot> TYPE = new Type<>(IdHelper.topo("structure_tool_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.ofMember((payload, buffer) -> payload.snapshot.write(buffer), buffer -> {
            var value = StructureToolSnapshot.read(buffer);
            if (buffer.readableBytes() != 0) throw new DecoderException("Trailing structure tool data");
            return new Snapshot(value);
        });

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Refresh() implements CustomPacketPayload {

        public static final Type<Refresh> TYPE = new Type<>(IdHelper.topo("structure_tool_refresh"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Refresh> CODEC = StreamCodec.unit(new Refresh());

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
