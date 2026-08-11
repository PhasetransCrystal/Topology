package net.ptcrys.topo.api.pipe.survey;

import net.ptcrys.topo.Topology;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S 勘测刷新空包:客户端激活期间每 20t 一发;服务端限频重建快照,不持仪即清。 */
public record PipeSurveyRefreshC2SPayload() implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Topology.MODID, "pipe_survey_refresh");
    public static final Type<PipeSurveyRefreshC2SPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeSurveyRefreshC2SPayload> CODEC = StreamCodec.unit(new PipeSurveyRefreshC2SPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void execute(PipeSurveyRefreshC2SPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PipeSurveyManager.refresh(player, PipeSurveyRefreshC2SPayload::holdsSurveyor);
        }
    }

    private static boolean holdsSurveyor(ServerPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PipeSurveyTool || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof PipeSurveyTool;
    }
}
