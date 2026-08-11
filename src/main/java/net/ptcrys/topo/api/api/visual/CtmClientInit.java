package net.ptcrys.topo.api.api.visual;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;

/** Client-side registration of the CTM blockstate-model codecs; no-op on the dedicated server. */
public final class CtmClientInit {

    private CtmClientInit() {}

    public static void register(IEventBus modEventBus) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        modEventBus.addListener(CtmClientInit::onRegisterBlockStateModels);
    }

    private static void onRegisterBlockStateModels(RegisterBlockStateModels event) {
        CtmBlockStateModelCodecs.register(event::registerModel);
    }
}
