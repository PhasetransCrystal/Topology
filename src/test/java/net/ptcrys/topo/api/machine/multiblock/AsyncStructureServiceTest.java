package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 在 4×4×6 非对称结构上检查同步与快照的同一镜像决策，不启动工作线程或世界。 */
class AsyncStructureServiceTest {

    private static final Blueprint BLUEPRINT = Blueprint.of()
            .aisle("C@CC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCG")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .where('C', Blocks.STONE).where('G', Blocks.GOLD_BLOCK).build();
    private static final Orientation PRIMARY = Orientations.IDENTITY;
    private static final Orientation MIRROR = PRIMARY.mirrored();

    @Test
    void formedPrimarySkipsLazyMirrorAndMatchesSnapshotDecision() {
        World world = World.built(PRIMARY);
        AtomicInteger reads = new AtomicInteger();
        AsyncStructureService.Outcome live = AsyncStructureService.recognizePreferringMirror(world, BLUEPRINT, PRIMARY,
                () -> {
                    reads.incrementAndGet();
                    return world;
                });
        assertTrue(live.result().formed());
        assertEquals(96, live.result().matchedCount());
        assertEquals(PRIMARY, live.orientation());
        assertEquals(0, reads.get());
        assertEquals(live, captured(world));
    }

    @Test
    void mirrorWinsOnlyAfterKnownPrimaryFailure() {
        World world = World.built(MIRROR);
        AsyncStructureService.Outcome result = AsyncStructureService.recognizePreferringMirror(world, BLUEPRINT, PRIMARY, () -> world);
        assertTrue(result.result().formed());
        assertEquals(MIRROR, result.orientation());
        assertEquals(result, captured(world));
    }

    @Test
    void formedMirrorWinsOverUnknownPrimary() {
        World world = World.built(MIRROR);
        world.unloaded.add(new BlockPos(2, 1, 0));
        AtomicInteger reads = new AtomicInteger();
        AsyncStructureService.Outcome result = AsyncStructureService.recognizePreferringMirror(world, BLUEPRINT, PRIMARY,
                () -> {
                    reads.incrementAndGet();
                    return world;
                });
        assertTrue(result.result().formed());
        assertEquals(RecognitionResult.FailureReason.NONE, result.result().failureReason());
        assertEquals(MIRROR, result.orientation());
        assertEquals(1, reads.get());
        assertEquals(result, captured(world));
    }

    @Test
    void unknownMirrorSurvivesKnownPrimaryFailure() {
        World world = World.built(MIRROR);
        world.unloaded.add(new BlockPos(-2, 1, 0));
        AsyncStructureService.Outcome result = AsyncStructureService.recognizePreferringMirror(world, BLUEPRINT, PRIMARY, () -> world);
        assertFalse(result.result().formed());
        assertEquals(RecognitionResult.FailureReason.CHUNK_UNLOADED, result.result().failureReason());
        assertEquals(MIRROR, result.orientation());
        assertEquals(result, captured(world));
    }

    @Test
    void snapshotCannotBeUsedForAnotherBlueprintOrOrientation() {
        CompiledSnapshot primary = snapshot(World.built(PRIMARY), PRIMARY);
        assertThrows(IllegalArgumentException.class, () -> StructureEngine.recognize(primary, BLUEPRINT, MIRROR));
        Blueprint another = Blueprint.of().aisle("@").build();
        assertThrows(IllegalArgumentException.class, () -> StructureEngine.verify(primary, another, PRIMARY));
    }

    private static AsyncStructureService.Outcome captured(World world) {
        return AsyncStructureService.recognizePreferringMirror(new AsyncStructureService.Captures(snapshot(world, PRIMARY), snapshot(world, MIRROR)), BLUEPRINT);
    }

    private static CompiledSnapshot snapshot(World world, Orientation orientation) {
        CompiledBlueprint compiled = BLUEPRINT.compiled(orientation);
        CompiledSnapshot.Builder builder = CompiledSnapshot.builder(compiled, BlockPos.ZERO);
        for (int n = 0; n < compiled.captureSize(); n++) {
            BlockPos pos = BlockPos.of(compiled.captureOffsetAt(n));
            builder.put(compiled.captureCellIndexAt(n), pos.asLong(), world.isLoaded(pos) ? world.blockState(pos) : null, null);
        }
        return builder.build();
    }

    private static final class World implements StructureView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();

        static World built(Orientation orientation) {
            World world = new World();
            for (int x = -1; x <= 2; x++) {
                for (int y = 0; y < 6; y++) {
                    for (int z = 0; z < 4; z++) {
                        world.states.put(orientation.apply(new BlockPos(x, y, z), BlockPos.ZERO),
                                (x == 2 && y == 3 && z == 3 ? Blocks.GOLD_BLOCK : Blocks.STONE).defaultBlockState());
                    }
                }
            }
            return world;
        }

        @Override
        public BlockPos controllerPos() {
            return BlockPos.ZERO;
        }

        @Override
        public BlockState blockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return !unloaded.contains(pos);
        }

        @Override
        public Set<net.ptcrys.topo.api.machine.multiblock.ability.PartRole> roles(BlockPos pos) {
            return Set.of();
        }
    }
}
