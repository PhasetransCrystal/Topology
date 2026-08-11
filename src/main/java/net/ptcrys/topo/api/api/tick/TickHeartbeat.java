package net.ptcrys.topo.api.api.tick;

import net.ptcrys.topo.api.api.async.TopoAsyncExecutors;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.concurrent.ScheduledExecutorService;

/** Bridges NeoForge level lifecycle and tick events into the TickHub system. */
public final class TickHeartbeat {

    private TickHeartbeat() {}

    public static void register(IEventBus ignoredModEventBus) {
        NeoForge.EVENT_BUS.register(TickHeartbeat.class);
    }

    private static ScheduledExecutorService asyncPool() {
        return TopoAsyncExecutors.tickScheduler();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            TickHub.attach(serverLevel, asyncPool());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            TickHub.detach(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        TickHub hub = TickHub.of(level);
        if (hub != null) {
            hub.tickSync(level.getGameTime());
        }
    }
}
