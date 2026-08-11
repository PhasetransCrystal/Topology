package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.pipe.PipeDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Static facade of the pipe network runtime: bridges NeoForge level/chunk lifecycle events and
 * {@link net.ptcrys.topo.api.pipe.PipeBlock} server hooks into the per-dimension
 * {@link PipeLevelRuntime}. Main server thread only.
 */
public final class PipeNetworkEngine {

    private static final Map<ServerLevel, PipeLevelRuntime> RUNTIMES = new IdentityHashMap<>();

    private PipeNetworkEngine() {}

    /** Game-bus wiring; called once from the mod constructor. */
    public static void register() {
        NeoForge.EVENT_BUS.register(PipeNetworkEngine.class);
    }

    public static PipeLevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(level, PipeLevelRuntime::new);
    }

    public static void onPipePlaced(ServerLevel level, BlockPos pos, PipeDefinition definition) {
        runtime(level).onPipePlaced(pos, definition);
    }

    public static void onPipeRemoved(ServerLevel level, BlockPos pos) {
        runtime(level).onPipeRemoved(pos);
    }

    public static void onNeighborChanged(ServerLevel level, BlockPos pos) {
        runtime(level).onNeighborChanged(pos);
    }

    public static void cycleSideIntent(ServerLevel level, BlockPos pos, Direction side, Player player) {
        runtime(level).cycleSideIntent(pos, side, player);
    }

    /**
     * Opens the builtin LDLib2 port strategy screen; when it cannot open (no extract role, fake
     * player), falls back to cycling the port through its tier's strategies.
     */
    public static boolean openPortConfig(ServerLevel level, BlockPos pos, Direction side, Player player) {
        PipeLevelRuntime runtime = runtime(level);
        if (runtime.openPortScreen(pos, side, player)) {
            return true;
        }
        runtime.cycleExtractStrategy(pos, side, player);
        return true;
    }

    public static @Nullable PipeNetwork<?> networkAt(ServerLevel level, BlockPos pos) {
        return runtime(level).networkAt(pos);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            runtime(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            PipeLevelRuntime runtime = RUNTIMES.remove(serverLevel);
            if (runtime != null) {
                runtime.close();
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() || !(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        runtime(serverLevel).reconcileChunk(chunk.getPos().pack());
    }
}
