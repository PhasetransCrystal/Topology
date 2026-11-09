package net.ptcrys.topo.api.pipe.survey;

import net.ptcrys.topo.Topology;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 勘测协议接线:S2C 快照 + C2S 刷新,登出清状态。模总线注册一次。 */
public final class PipeSurveyNetworking {

    private PipeSurveyNetworking() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PipeSurveyNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PipeSurveyManager.onLoggedOut(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> PipeSurveyManager.clearAll());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Topology.MODID);
        registrar.playToClient(
                PipeSurveySnapshotS2CPayload.TYPE,
                PipeSurveySnapshotS2CPayload.CODEC,
                PipeSurveySnapshotS2CPayload::execute);
        registrar.playToServer(
                PipeSurveyRefreshC2SPayload.TYPE,
                PipeSurveyRefreshC2SPayload.CODEC,
                PipeSurveyRefreshC2SPayload::execute);
    }
}
