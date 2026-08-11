package net.ptcrys.topo.api.machine.data.network;

import net.ptcrys.topo.Topology;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MachineDataNetworking {

    private MachineDataNetworking() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MachineDataNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Topology.MODID);
        registrar.playToClient(
                MachineDataBatchS2CPayload.TYPE,
                MachineDataBatchS2CPayload.CODEC,
                MachineDataBatchS2CPayload::execute);
        registrar.playToServer(MachineDataC2SPayload.TYPE, MachineDataC2SPayload.CODEC, MachineDataC2SPayload::execute);
    }
}
