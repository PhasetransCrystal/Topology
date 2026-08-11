package net.ptcrys.topo.api.pipe.survey;

import net.ptcrys.topo.Topology;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C 勘测快照:客户端缓存最新一帧供世界渲染,空帧清屏。主线程处理。 */
public record PipeSurveySnapshotS2CPayload(PipeSurveySnapshot snapshot) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Topology.MODID, "pipe_survey_snapshot");
    public static final Type<PipeSurveySnapshotS2CPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeSurveySnapshotS2CPayload> CODEC = StreamCodec.ofMember(PipeSurveySnapshotS2CPayload::write, PipeSurveySnapshotS2CPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        snapshot.write(buffer);
    }

    private static PipeSurveySnapshotS2CPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PipeSurveySnapshotS2CPayload(PipeSurveySnapshot.read(buffer));
    }

    public static void execute(PipeSurveySnapshotS2CPayload payload, IPayloadContext context) {
        net.ptcrys.topo.client.survey.PipeSurveyClientState.apply(payload.snapshot());
    }
}
