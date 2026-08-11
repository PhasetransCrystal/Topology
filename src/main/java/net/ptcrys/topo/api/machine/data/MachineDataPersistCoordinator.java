package net.ptcrys.topo.api.machine.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Server-thread coordinator for machine persistence epochs.
 *
 * <p>
 * A domain establishes its chunk epoch immediately on the clean-to-dirty transition, closing
 * same-tick save and unload windows. It then remains parked by chunk until a real
 * {@link ChunkDataEvent.Save} confirms that Minecraft captured the complete snapshot. Repeated
 * field writes while parked are constant-time and do not scan fields or touch the chunk.
 */
final class MachineDataPersistCoordinator {

    private static final long FAILED_SNAPSHOT_RECOVERY_INTERVAL_TICKS = 200L;
    private static final Map<ServerLevel, LevelState> LEVEL_STATES = new IdentityHashMap<>();

    private MachineDataPersistCoordinator() {}

    static void register() {
        NeoForge.EVENT_BUS.register(MachineDataPersistCoordinator.class);
    }

    static void enqueue(MachineDataScope domain) {
        markDomainNow(domain);
    }

    static void flushDomainNow(MachineDataScope domain) {
        markDomainNow(domain);
    }

    private static void markDomainNow(MachineDataScope domain) {
        ServerLevel level = domain.serverLevel();
        if (level == null || !domain.lifecycleMarkPersistEpoch()) {
            return;
        }
        trackOutstanding(state(level), domain);
    }

    static void detach(MachineDataScope domain) {
        ServerLevel level = domain.serverLevel();
        if (level == null) {
            return;
        }
        LevelState state = LEVEL_STATES.get(level);
        if (state == null) {
            return;
        }
        long chunkKey = domain.chunkPos().pack();
        ObjectArrayList<MachineDataScope> outstanding = state.outstandingByChunk.get(chunkKey);
        if (outstanding != null) {
            removeIdentity(outstanding, domain);
            if (outstanding.isEmpty()) {
                state.outstandingByChunk.remove(chunkKey);
            }
        }
    }

    static void flushResidualPersistForSave(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            recoverDroppedChunkMarks(serverLevel, LEVEL_STATES.get(serverLevel));
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        for (Map.Entry<ServerLevel, LevelState> entry : LEVEL_STATES.entrySet()) {
            ServerLevel level = entry.getKey();
            LevelState state = entry.getValue();
            long gameTime = level.getGameTime();
            if (gameTime >= state.nextRecoveryGameTime) {
                state.nextRecoveryGameTime = gameTime + FAILED_SNAPSHOT_RECOVERY_INTERVAL_TICKS;
                recoverDroppedChunkMarks(level, state);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelState state = LEVEL_STATES.get(level);
        if (state == null) {
            return;
        }
        ObjectArrayList<MachineDataScope> domains = state.outstandingByChunk.remove(event.getChunk().getPos().pack());
        if (domains == null) {
            return;
        }
        for (int i = 0; i < domains.size(); i++) {
            domains.get(i).lifecycleAcknowledgePersistSnapshot();
        }
    }

    /** Save events are post-save; drain any command-window mutations for the next safe snapshot. */
    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            recoverDroppedChunkMarks(level, LEVEL_STATES.get(level));
        }
    }

    /** Ensure the final shutdown save sees mutations made after the last server-tick post phase. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Map.Entry<ServerLevel, LevelState> entry : LEVEL_STATES.entrySet()) {
            recoverDroppedChunkMarks(entry.getKey(), entry.getValue());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVEL_STATES.remove(level);
        }
    }

    /**
     * Vanilla clears a chunk's unsaved flag before snapshot capture. If a serializer throws before
     * NeoForge can emit ChunkDataEvent.Save, keep our outstanding epoch and restore the flag so a
     * later save retries instead of parking the machine forever in an unacknowledged generation.
     */
    private static void recoverDroppedChunkMarks(ServerLevel level, LevelState state) {
        if (state == null || state.outstandingByChunk.isEmpty()) {
            return;
        }
        for (var entry : state.outstandingByChunk.long2ObjectEntrySet()) {
            ObjectArrayList<MachineDataScope> domains = entry.getValue();
            if (domains.isEmpty()) {
                continue;
            }
            long chunkKey = entry.getLongKey();
            var chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (chunk != null && !chunk.isUnsaved()) {
                domains.getFirst().machine().markChunkUnsavedForPersist();
            }
        }
    }

    private static void trackOutstanding(LevelState state, MachineDataScope domain) {
        long chunkKey = domain.chunkPos().pack();
        ObjectArrayList<MachineDataScope> domains = state.outstandingByChunk.get(chunkKey);
        if (domains == null) {
            domains = new ObjectArrayList<>(2);
            state.outstandingByChunk.put(chunkKey, domains);
            domain.machine().markChunkUnsavedForPersist();
        }
        domains.add(domain);
    }

    private static LevelState state(ServerLevel level) {
        return LEVEL_STATES.computeIfAbsent(level, ignored -> new LevelState());
    }

    private static void removeIdentity(ObjectArrayList<MachineDataScope> domains, MachineDataScope target) {
        for (int i = domains.size() - 1; i >= 0; i--) {
            if (domains.get(i) == target) {
                domains.remove(i);
            }
        }
    }

    private static final class LevelState {

        private final Long2ObjectOpenHashMap<ObjectArrayList<MachineDataScope>> outstandingByChunk = new Long2ObjectOpenHashMap<>();
        private long nextRecoveryGameTime;
    }
}
