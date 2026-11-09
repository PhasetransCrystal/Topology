package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.PipeSideRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level pipe topology: network components, node roles/visuals, flood/retire, and deferred
 * structural work. It owns no port settings and no tick extraction; {@link PipeLevelRuntime}
 * coordinates those concerns around this graph.
 */
final class PipeTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeTopology.class);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final PipeNetwork<?>[] EMPTY_SNAPSHOT = new PipeNetwork<?>[0];

    private final ServerLevel level;
    private final PipeNetworksSavedData data;
    private final Runnable ensureTickHook;
    private final Long2ObjectOpenHashMap<PipeNetwork<?>> networkByNode = new Long2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<PipeNetwork<?>> networks = new ReferenceOpenHashSet<>();
    private final LongLinkedOpenHashSet rebuildSeeds = new LongLinkedOpenHashSet();
    private final LongArrayList deferredPortRefreshes = new LongArrayList();

    private boolean ticking;
    private PipeNetwork<?>[] tickSnapshot = EMPTY_SNAPSHOT;
    private boolean networksDirty;

    PipeTopology(ServerLevel level, PipeNetworksSavedData data, Runnable ensureTickHook) {
        this.level = level;
        this.data = data;
        this.ensureTickHook = ensureTickHook;
        if (data.nodeCount() > 0) {
            rebuildSeeds.addAll(data.nodePositions());
            ensureTickHook.run();
        }
    }

    void onPipePlaced(BlockPos pos, PipeDefinition definition) {
        long key = pos.asLong();
        data.addNode(key, definition, PipeSideIntent.packAll(PipeSideIntent.AUTO));
        recomputeRoles(key, true);
        ensureTickHook.run();
    }

    void onPipeRemoved(BlockPos pos) {
        long key = pos.asLong();
        PipeNodeRecord removed = data.removeNode(key);
        if (removed == null) {
            return;
        }
        retireAt(key);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            if (PipeSideRole.unpack(removed.roleBits(), direction) == PipeSideRole.LINK) {
                long neighbor = cursor.set(key).move(direction).asLong();
                retireAt(neighbor);
                rebuildSeeds.add(neighbor);
            }
        }
    }

    void onNeighborChanged(BlockPos pos) {
        long key = pos.asLong();
        if (data.node(key) != null) {
            recomputeRoles(key, true);
        }
    }

    void setSideIntent(BlockPos pos, Direction side, PipeSideIntent intent) {
        long key = pos.asLong();
        PipeNodeRecord record = data.node(key);
        if (record == null) {
            return;
        }
        data.setIntentBits(record, PipeSideIntent.pack(record.intentBits(), side, intent));
        recomputeRoles(key, true);
    }

    void recomputeRoles(long pos, boolean propagate) {
        PipeNodeRecord record = data.node(pos);
        if (record == null) {
            return;
        }
        int oldRoles = record.roleBits();
        int newRoles = computeRoles(pos, record);
        if (oldRoles == newRoles) {
            return;
        }
        data.setRoleBits(record, newRoles);
        writeVisual(pos, record, newRoles);

        boolean linkDelta = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            PipeSideRole oldRole = PipeSideRole.unpack(oldRoles, direction);
            PipeSideRole newRole = PipeSideRole.unpack(newRoles, direction);
            if (oldRole == newRole) {
                continue;
            }
            if (oldRole == PipeSideRole.LINK || newRole == PipeSideRole.LINK) {
                linkDelta = true;
                long neighbor = cursor.set(pos).move(direction).asLong();
                retireAt(neighbor);
                rebuildSeeds.add(neighbor);
                if (propagate) {
                    recomputeRoles(neighbor, false);
                }
            }
        }
        if (linkDelta) {
            retireAt(pos);
            rebuildSeeds.add(pos);
        } else {
            refreshPortsAt(pos);
        }
    }

    private int computeRoles(long pos, PipeNodeRecord record) {
        MachineResourceType<?> kind = record.definition().resourceType();
        BlockCapability<? extends ResourceHandler<?>, @Nullable Direction> capability = kind.blockCapability();
        int roles = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            PipeSideIntent intent = record.intent(direction);
            PipeSideRole role = PipeSideRole.NONE;
            if (intent != PipeSideIntent.DISABLED) {
                cursor.set(pos).move(direction);
                if (!level.isLoaded(cursor)) {
                    role = record.role(direction);
                } else {
                    BlockState neighborState = level.getBlockState(cursor);
                    if (neighborState.getBlock() instanceof PipeBlock pipe) {
                        if (pipe.definition().resourceType() == kind && neighborAllowsLink(cursor.asLong(), direction)) {
                            role = PipeSideRole.LINK;
                        }
                    } else if (capability != null && level.getCapability(capability, cursor, direction.getOpposite()) != null) {
                        role = intent == PipeSideIntent.EXTRACT ? PipeSideRole.EXTRACT : PipeSideRole.DESTINATION;
                    }
                }
            }
            roles = PipeSideRole.pack(roles, direction, role);
        }
        return roles;
    }

    private boolean neighborAllowsLink(long neighborPos, Direction directionTowardsNeighbor) {
        PipeNodeRecord neighbor = data.node(neighborPos);
        return neighbor == null || neighbor.intent(directionTowardsNeighbor.getOpposite()) != PipeSideIntent.DISABLED;
    }

    private void writeVisual(long pos, PipeNodeRecord record, int roleBits) {
        BlockPos blockPos = BlockPos.of(pos);
        BlockState current = level.getBlockState(blockPos);
        if (!(current.getBlock() instanceof PipeBlock pipe) || pipe.definition() != record.definition()) {
            return;
        }
        BlockState updated = current;
        for (Direction direction : DIRECTIONS) {
            updated = updated.setValue(
                    PipeBlock.property(direction),
                    PipeSideRole.unpack(roleBits, direction).visual());
        }
        if (updated != current) {
            level.setBlock(blockPos, updated, Block.UPDATE_CLIENTS);
        }
    }

    private void retireAt(long pos) {
        PipeNetwork<?> network = networkByNode.get(pos);
        if (network != null) {
            retire(network);
        }
    }

    private void retire(PipeNetwork<?> network) {
        if (network.retired) {
            return;
        }
        network.retired = true;
        networks.remove(network);
        networksDirty = true;
        for (long pos : network.nodePositions()) {
            PipeNetwork<?> current = networkByNode.get(pos);
            if (current == network) {
                networkByNode.remove(pos);
            }
        }
    }

    void refreshPortsAt(long pos) {
        if (ticking) {
            deferredPortRefreshes.add(pos);
            return;
        }
        PipeNetwork<?> network = networkByNode.get(pos);
        if (network != null && !network.retired) {
            network.refreshPortsAt(data, pos);
            networksDirty = true;
        } else {
            rebuildSeeds.add(pos);
        }
    }

    void resolvePending() {
        while (!deferredPortRefreshes.isEmpty()) {
            long pos = deferredPortRefreshes.removeLong(deferredPortRefreshes.size() - 1);
            PipeNetwork<?> network = networkByNode.get(pos);
            if (network != null && !network.retired) {
                network.refreshPortsAt(data, pos);
                networksDirty = true;
            } else {
                rebuildSeeds.add(pos);
            }
        }
        while (!rebuildSeeds.isEmpty()) {
            long seed = rebuildSeeds.removeFirstLong();
            PipeNetwork<?> existing = networkByNode.get(seed);
            if (existing != null && !existing.retired) {
                continue;
            }
            if (data.node(seed) == null) {
                continue;
            }
            flood(seed);
        }
    }

    private PipeNetwork<?> flood(long seed) {
        PipeNetwork<?> network = PipeNetwork.flood(level, data, seed);
        if (network == null) {
            throw new IllegalStateException("Flooded a pipe network from a missing node at " + BlockPos.of(seed));
        }
        for (long pos : network.nodePositions()) {
            PipeNetwork<?> previous = networkByNode.put(pos, network);
            if (previous != null && previous != network) {
                retire(previous);
                networkByNode.put(pos, network);
            }
        }
        networks.add(network);
        networksDirty = true;
        ensureTickHook.run();
        return network;
    }

    @Nullable
    PipeNetwork<?> networkAt(BlockPos pos) {
        long key = pos.asLong();
        if (data.node(key) == null) {
            return null;
        }
        resolvePending();
        PipeNetwork<?> network = networkByNode.get(key);
        if (network == null || network.retired) {
            network = flood(key);
        }
        return network;
    }

    void reconcileChunk(long chunkKey) {
        LongArrayList recorded = data.nodesInChunk(chunkKey);
        if (recorded.isEmpty()) {
            return;
        }
        long[] positions = recorded.toLongArray();
        for (long pos : positions) {
            PipeNodeRecord record = data.node(pos);
            if (record == null) {
                continue;
            }
            BlockPos blockPos = BlockPos.of(pos);
            BlockState state = level.getBlockState(blockPos);
            if (!(state.getBlock() instanceof PipeBlock pipe) || pipe.definition() != record.definition()) {
                LOGGER.warn("Pruning stale pipe node at {}: world block is {}", blockPos, state.getBlock());
                data.removeNode(pos);
                retireAt(pos);
                continue;
            }
            writeVisual(pos, record, record.roleBits());
        }
        ensureTickHook.run();
    }

    void beginTick() {
        ticking = true;
    }

    void endTick() {
        ticking = false;
    }

    PipeNetwork<?>[] activeNetworks() {
        if (networks.isEmpty()) {
            return EMPTY_SNAPSHOT;
        }
        if (!networksDirty) {
            return tickSnapshot;
        }
        int active = 0;
        for (PipeNetwork<?> network : networks) {
            if (network.extractorCount() > 0) {
                active++;
            }
        }
        PipeNetwork<?>[] snapshot = active == 0 ? EMPTY_SNAPSHOT : new PipeNetwork<?>[active];
        int index = 0;
        for (PipeNetwork<?> network : networks) {
            if (network.extractorCount() > 0) {
                snapshot[index++] = network;
            }
        }
        tickSnapshot = snapshot;
        networksDirty = false;
        return snapshot;
    }
}
