package net.ptcrys.topo.api.machine.data;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.machine.data.network.MachineDataBatchS2CPayload;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class MachineDataSyncBatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineDataSyncBatcher.class);

    private static final Set<MachineDataScope> CLIENT_DIRTY_DOMAINS = ConcurrentHashMap.newKeySet();

    private MachineDataSyncBatcher() {}

    public static void register() {
        MachineDataPersistCoordinator.register();
        NeoForge.EVENT_BUS.register(MachineDataSyncBatcher.class);
    }

    public static void markClientDirty(MachineDataScope domain) {
        if (domain.serverLevel() != null) {
            CLIENT_DIRTY_DOMAINS.add(domain);
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        flushServerTick();
    }

    public static <P> List<PlayerBatch<P>> collectBatchesForTesting(
                                                                    Collection<MachineDataScope> domains,
                                                                    Function<MachineDataScope, List<P>> playersByDomain,
                                                                    RegistryAccess registryAccess) {
        CLIENT_DIRTY_DOMAINS.removeAll(domains);
        return collectBatches(domains, playersByDomain, ignored -> registryAccess);
    }

    private static void flushServerTick() {
        List<MachineDataScope> domains = snapshotDirtyDomains(CLIENT_DIRTY_DOMAINS);
        if (domains.isEmpty()) {
            return;
        }
        List<PlayerBatch<ServerPlayer>> batches = collectBatches(
                domains,
                MachineDataSyncBatcher::trackingPlayers,
                MachineDataSyncBatcher::registryAccess);
        for (PlayerBatch<ServerPlayer> batch : batches) {
            try {
                PacketDistributor.sendToPlayer(batch.player(), batch.payload());
            } catch (RuntimeException error) {
                LOGGER.error("Failed to send machine data batch to {}", batch.player(), error);
            }
        }
    }

    private static List<MachineDataScope> snapshotDirtyDomains(Set<MachineDataScope> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<MachineDataScope> domains = new ArrayList<>(source);
        for (MachineDataScope domain : domains) {
            source.remove(domain);
        }
        return domains;
    }

    private static <P> List<PlayerBatch<P>> collectBatches(
                                                           Collection<MachineDataScope> domains,
                                                           Function<MachineDataScope, List<P>> playersByDomain,
                                                           Function<MachineDataScope, RegistryAccess> registryAccessByDomain) {
        Map<P, List<MachineDataBatchS2CPayload.Entry>> entriesByPlayer = new LinkedHashMap<>();
        Map<P, Long> bytesByPlayer = new LinkedHashMap<>();
        List<PlayerBatch<P>> batches = new ArrayList<>();
        long maxBatchBytes = Math.max(1L, Topology.config.MACHINES.dataSyncMaxBatchBytes);
        for (MachineDataScope domain : domains) {
            try {
                List<P> players = playersByDomain.apply(domain);
                if (players.isEmpty()) {
                    // Optimization and safety contract: delta sync is only for clients already watching.
                    // With no watchers, drop sync dirty without allocating or encoding; later viewers are
                    // bootstrapped by the full update-tag snapshot and never depend on stale deltas.
                    domain.lifecycleDropClientDelta();
                    continue;
                }

                // Optimization: encode each dirty machine delta once per server tick, then share the
                // same byte payload across every tracking player's batch instead of encoding P times.
                byte[] data = domain.lifecycleDrainClientDelta(
                        registryAccessByDomain.apply(domain),
                        ConnectionType.NEOFORGE);
                if (data.length == 0) {
                    continue;
                }
                long pos = domain.machine().getBlockPos().asLong();
                long entryBytes = Long.BYTES + data.length + 5L;
                for (P player : players) {
                    List<MachineDataBatchS2CPayload.Entry> entries = entriesByPlayer
                            .computeIfAbsent(player, ignored -> new ArrayList<>());
                    long currentBytes = bytesByPlayer.getOrDefault(player, 0L);
                    if (!entries.isEmpty() && currentBytes + entryBytes > maxBatchBytes) {
                        batches.add(new PlayerBatch<>(player, new MachineDataBatchS2CPayload(entries)));
                        entries = new ArrayList<>();
                        entriesByPlayer.put(player, entries);
                        currentBytes = 0L;
                    }
                    entries.add(new MachineDataBatchS2CPayload.Entry(pos, data));
                    bytesByPlayer.put(player, currentBytes + entryBytes);
                }
            } catch (RuntimeException error) {
                LOGGER.error(
                        "Failed to encode machine data sync for {} at {}; dropping this delta",
                        domain.machine().definition().id(), domain.machine().getBlockPos(), error);
                domain.lifecycleDropClientDelta();
            }
        }
        for (Map.Entry<P, List<MachineDataBatchS2CPayload.Entry>> entry : entriesByPlayer.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                // Optimization: coalesce remaining dirty machines for the same player into one
                // custom payload, reducing packet count and Netty enqueue overhead.
                batches.add(new PlayerBatch<>(entry.getKey(), new MachineDataBatchS2CPayload(entry.getValue())));
            }
        }
        return batches;
    }

    @SuppressWarnings("resource") // ServerLevel lifecycle is owned by MinecraftServer; the batcher only reads tracking
                                  // state.
    private static List<ServerPlayer> trackingPlayers(MachineDataScope domain) {
        ServerLevel level = domain.serverLevel();
        if (level == null) {
            return List.of();
        }
        return level.getChunkSource().chunkMap.getPlayers(domain.chunkPos(), false);
    }

    @SuppressWarnings("resource") // Borrowed registry access from a server-owned level; never close the level here.
    private static RegistryAccess registryAccess(MachineDataScope domain) {
        ServerLevel level = domain.serverLevel();
        return level == null ? RegistryAccess.EMPTY : level.registryAccess();
    }

    public record PlayerBatch<P>(P player, MachineDataBatchS2CPayload payload) {}
}
