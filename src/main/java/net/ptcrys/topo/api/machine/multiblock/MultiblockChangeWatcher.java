package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.MachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.Set;

/**
 * Sole owner of the world-event subscriptions feeding the multiblock runtime. Each handler is a
 * thin ingress adapter that routes changed cell positions into the per-level
 * {@link MultiblockRecheckDirtySet}; no handler ever creates services (it goes through
 * {@link MultiblockLevelBinder#servicesIfPresent}), so levels without multiblock activity stay
 * untouched.
 *
 * <p>
 * Events here only reduce recheck latency; correctness is guaranteed by the controllers'
 * periodic low-frequency self-check backstop. Change paths with no usable event (e.g. blocks moved
 * laterally by slime structures outside the piston axis) are intentionally left to that backstop.
 */
public final class MultiblockChangeWatcher {

    /**
     * Cells within this distance of a piston along its move axis may have moved (vanilla push limit
     * of 12 blocks plus the head). Marked in both axis directions to stay independent of the
     * extend/retract direction convention.
     */
    private static final int PISTON_AFFECTED_RANGE = 13;

    private MultiblockChangeWatcher() {}

    /** Subscribes all watcher handlers on {@link NeoForge#EVENT_BUS}. Call once during mod init. */
    public static void register() {
        NeoForge.EVENT_BUS.register(MultiblockChangeWatcher.class);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        // No runtime-property filter here: the event's snapshot is pre-placement only on the player
        // path (on the non-player path it already holds the placed state), so it is not a reliable
        // old state to diff against.
        routeCellChanged(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlocksPlaced(BlockEvent.EntityMultiPlaceEvent event) {
        routeCellChanged(event.getLevel(), event.getPos());
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            routeCellChanged(snapshot.getLevel(), snapshot.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BreakBlockEvent event) {
        routeCellChanged(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        // The only block event exposing an unambiguous old+new state pair for the same position, so
        // the runtime-property ingress filter applies here.
        if (isRuntimeOnlyDelta(event.getOriginalState(), event.getNewState())) {
            return;
        }
        routeCellChanged(event.getLevel(), event.getPos());
        routeCellChanged(event.getLevel(), event.getLiquidPos());
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        // Fires for every server-side block change; the event carries only the new state, so no
        // delta filter is possible. The route below is two map lookups on quiet levels.
        routeCellChanged(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        for (BlockPos affected : event.getAffectedBlocks()) {
            routeCellChanged(event.getLevel(), affected);
        }
    }

    @SubscribeEvent
    public static void onPistonMoved(PistonEvent.Post event) {
        // Post fires after blocks moved, so re-resolving the piston structure would read the wrong
        // layout; over-approximate with the reachable cells along the move axis instead.
        LevelAccessor level = event.getLevel();
        BlockPos pistonPos = event.getPos();
        routeCellChanged(level, pistonPos);
        for (int offset = 1; offset <= PISTON_AFFECTED_RANGE; offset++) {
            routeCellChanged(level, pistonPos.relative(event.getDirection(), offset));
            routeCellChanged(level, pistonPos.relative(event.getDirection().getOpposite(), offset));
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            MultiblockLevelBinder.forget(level);
        }
    }

    /**
     * Routes a surviving cell change to the level's scheduler. Read-only on the binder: levels that
     * never created multiblock services are skipped without instantiating them.
     */
    private static void routeCellChanged(LevelAccessor levelAccessor, BlockPos pos) {
        if (!(levelAccessor instanceof ServerLevel level)) {
            return;
        }
        MultiblockLevelBinder.Services services = MultiblockLevelBinder.servicesIfPresent(level);
        if (services == null) {
            return;
        }
        services.rechecks().markCellChanged(pos, services.claims());
    }

    /**
     * Runtime-property ingress filter: {@code true} when both states belong to the same
     * {@link MachineBlock} and every differing property is declared in that block's
     * {@link MachineBlock#runtimeStateProperties()} — such changes (e.g. FORMED/ACTIVE toggles
     * written by the runtime itself) carry no structural information and are dropped.
     */
    private static boolean isRuntimeOnlyDelta(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() != newState.getBlock()) {
            return false;
        }
        if (!(newState.getBlock() instanceof MachineBlock machineBlock)) {
            return false;
        }
        Set<Property<?>> runtimeProperties = machineBlock.runtimeStateProperties();
        for (Property<?> property : newState.getProperties()) {
            if (!runtimeProperties.contains(property) && !oldState.getValue(property).equals(newState.getValue(property))) {
                return false;
            }
        }
        return true;
    }
}
