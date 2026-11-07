package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.data.*;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 同型约束只作为外部能力接入示例，框架不内置该业务规则。主场景为独立生成的 4×4×6 中空结构。 */
class StructureDetectionTest {

    private static final DataKey<String> BLOCK = DataKey.immutable(Identifier.fromNamespaceAndPath("example", "observed_block"), Codec.STRING, "");
    private static final DataKey<String> MODULE = DataKey.immutable(Identifier.fromNamespaceAndPath("example", "module_block"), Codec.STRING, "");
    private static final Identifier DIFFERENT = Identifier.fromNamespaceAndPath("example", "different_block");
    private static final CellPredicate<DataKey<String>> OBSERVE = (key, context, metadata) -> {
        if (context.actual() == null || context.actual().isAir()) return CellResult.failure("empty", "solid block", context.actual());
        String block = BuiltInRegistries.BLOCK.getKey(context.actual().getBlock()).toString();
        if (metadata.assigned(key) && !metadata.get(key).equals(block)) return CellResult.fail(DIFFERENT, Map.of("block", metadata.get(key)), Map.of("block", block));
        metadata.set(key, block);
        return CellResult.pass();
    };

    private static CellBinding observed(DataKey<String> key) {
        return CellBinding.of(OBSERVE, key).metadata(key);
    }

    private static Blueprint chamber() {
        var builder = Blueprint.of().aisle("@CCC", "C##C", "C##C", "CCCC");
        for (int y = 1; y < 5; y++) builder.aisle("CCCC", "C##C", "C##C", "CCCC");
        return builder.aisle("GGGG", "GGGG", "GGGG", "GGGG")
                .where('C', CellPredicates.allOf(CellPredicates.block(Blocks.STONE, Blocks.DIRT), observed(BLOCK)))
                .where('G', Blocks.GLASS).build();
    }

    @Test
    void callerRequirementsReachDetectionWithoutBecomingObservedFacts() {
        DataKey<Integer> tier = DataKey.immutable(Identifier.fromNamespaceAndPath("example", "required_tier"), Codec.INT, -1);
        CellPredicate<DataKey<Integer>> configured = (key, context, metadata) -> metadata.required(key) && metadata.requirement(key) == 2 ? CellResult.pass() : CellResult.fail(Identifier.fromNamespaceAndPath("example", "missing_tier"), Map.of("tier", "2"), Map.of("tier", String.valueOf(metadata.required(key) ? metadata.requirement(key).intValue() : -1)));
        Blueprint blueprint = Blueprint.of().aisle("@T").where('T', CellBinding.of(configured, tier).metadata(tier)).build();
        var source = StructureMetadata.defaults(List.of(blueprint)).edit();
        source.require(tier, 2);
        var result = StructureEngine.recognize(new World(), blueprint, Orientations.IDENTITY, source, "body");
        assertTrue(result.formed());
        assertTrue(result.metadata().required(tier));
        assertFalse(result.metadata().assignedKeys().contains(tier));
        assertEquals(2, result.metadata().requirement(tier));
    }

    @Test
    void bodyAndModulesShareFactsAcrossAll24RotationsAndMirrors() {
        Blueprint body = chamber();
        Blueprint extension = Blueprint.of().aisle("M@").where('M', observed(BLOCK)).build();
        var modules = List.of(MultiblockModule.of("extension", extension));
        for (Orientation orientation : Orientations.all()) {
            World world = World.chamber(orientation, Blocks.STONE.defaultBlockState());
            BlockPos module = orientation.apply(new BlockPos(-1, 0, 0), world.controllerPos());
            world.states.put(module, Blocks.DIRT.defaultBlockState());
            var source = StructureMetadata.defaults(List.of(body, extension));
            var result = StructureDetection.detect((blueprint, transform) -> world, body, modules, orientation, source, false);
            assertTrue(result.result().formed());
            assertEquals(96, result.result().matchedCount());
            assertFalse(result.modules().reports().get("extension").satisfied());
            assertEquals("minecraft:stone", result.metadata().get(BLOCK));
            world.states.put(module, Blocks.STONE.defaultBlockState());
            result = StructureDetection.detect((blueprint, transform) -> world, body, modules, orientation, source, false);
            assertTrue(result.modules().reports().get("extension").satisfied());
        }
    }

    @Test
    void completeSnapshotCycleMatchesLiveAndSurvivesLaterWorldChanges() {
        Blueprint body = chamber();
        Blueprint extension = Blueprint.of().aisle("M@").where('M', observed(BLOCK)).build();
        List<MultiblockModule> modules = List.of(MultiblockModule.of("extension", extension));
        Orientation orientation = Orientations.forFacing(net.minecraft.core.Direction.UP).mirrored();
        World world = World.chamber(orientation, Blocks.STONE.defaultBlockState());
        world.put(orientation, -1, 0, 0, Blocks.STONE.defaultBlockState());
        var source = StructureMetadata.defaults(List.of(body, extension));
        var live = StructureDetection.detect((blueprint, transform) -> world, body, modules, orientation, source, false);
        Map<Blueprint, Map<Orientation, CompiledSnapshot>> snapshots = new HashMap<>();
        for (Blueprint blueprint : List.of(body, extension)) {
            Map<Orientation, CompiledSnapshot> byOrientation = new HashMap<>();
            for (Orientation transform : List.of(orientation, orientation.mirrored())) {
                var compiled = blueprint.compiled(transform);
                var builder = CompiledSnapshot.builder(compiled, world.controllerPos());
                for (int i = 0; i < compiled.captureSize(); i++) {
                    BlockPos pos = BlockPos.of(compiled.captureOffsetAt(i));
                    builder.put(compiled.captureCellIndexAt(i), pos.asLong(), world.blockState(pos), null);
                }
                byOrientation.put(transform, builder.build());
            }
            snapshots.put(blueprint, Map.copyOf(byOrientation));
        }
        world.states.clear();
        var captured = java.util.concurrent.CompletableFuture.supplyAsync(() -> StructureDetection.detect(
                (blueprint, transform) -> snapshots.get(blueprint).get(transform), body, modules, orientation, source, false)).join();
        assertEquals(live, captured);
        assertTrue(captured.result().formed());
        assertTrue(captured.modules().reports().get("extension").satisfied());
        assertFalse(source.assignedKeys().contains(BLOCK));
    }

    @Test
    void periodicVerificationPreservesBaselineEvenIfEveryObservedBlockChangesTogether() {
        Blueprint blueprint = chamber();
        World world = World.chamber(Orientations.IDENTITY, Blocks.STONE.defaultBlockState());
        var original = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertTrue(original.formed());
        world.states.replaceAll((pos, state) -> state.is(Blocks.STONE) ? Blocks.DIRT.defaultBlockState() : state);
        var verification = StructureEngine.verify(world, blueprint, Orientations.IDENTITY, original.metadata().verify());
        assertFalse(verification.formed());
        assertEquals("minecraft:stone", original.metadata().get(BLOCK));
        assertTrue(StructureEngine.recognize(world, blueprint, Orientations.IDENTITY).formed());
    }

    @Test
    void failedOptionalModuleDoesNotPolluteTheNextModule() {
        Blueprint body = chamber();
        Blueprint bad = Blueprint.of().aisle("MF@").where('M', observed(MODULE)).where('F', Blocks.GOLD_BLOCK).build();
        Blueprint good = Blueprint.of().aisle("@ N").where('N', observed(MODULE)).build();
        World world = World.chamber(Orientations.IDENTITY, Blocks.STONE.defaultBlockState());
        world.states.put(new BlockPos(-2, 0, 0), Blocks.DIRT.defaultBlockState());
        var result = StructureDetection.detect((blueprint, transform) -> world, body,
                List.of(MultiblockModule.of("bad", bad), MultiblockModule.of("good", good)), Orientations.IDENTITY,
                StructureMetadata.defaults(List.of(body, bad, good)), false);
        assertTrue(result.result().formed());
        assertFalse(result.modules().reports().get("bad").satisfied());
        assertTrue(result.modules().reports().get("good").satisfied());
        assertEquals("minecraft:stone", result.metadata().get(MODULE));
    }

    @Test
    void symmetricBodyUsesModulesToSelectOneWholeMirror() {
        Blueprint body = Blueprint.of().aisle("C@C").where('C', Blocks.STONE).build();
        Blueprint extension = Blueprint.of().aisle("@ M").where('M', Blocks.GOLD_BLOCK).build();
        for (Orientation orientation : Orientations.rotations()) {
            World world = new World();
            world.put(orientation, -1, 0, 0, Blocks.STONE.defaultBlockState());
            world.put(orientation, 1, 0, 0, Blocks.STONE.defaultBlockState());
            world.put(orientation, -2, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState());
            var result = StructureDetection.detect((blueprint, transform) -> world, body,
                    List.of(MultiblockModule.of("extension", extension)), orientation,
                    StructureMetadata.defaults(List.of(body, extension)), false);
            assertEquals(orientation.mirrored(), result.orientation());
            assertTrue(result.result().formed());
            assertTrue(result.modules().reports().get("extension").satisfied());
        }
    }

    @Test
    void failedRepeatGrowthRollsBackAllWritesFromTheCandidateCopy() {
        CellPredicate<DataKey<String>> speculative = (key, context, metadata) -> {
            if (context.worldPos().getY() >= 2) metadata.set(key, "speculative");
            return CellResult.pass();
        };
        Blueprint blueprint = Blueprint.of().aisle("@ ").beginRepeatable(1, 3).aisle("XR").endRepeatable().aisle("G ")
                .where('X', CellBinding.of(speculative, MODULE).metadata(MODULE)).where('R', Blocks.STONE).where('G', Blocks.GOLD_BLOCK).build();
        World world = new World();
        world.put(Orientations.IDENTITY, 0, 1, 0, Blocks.STONE.defaultBlockState());
        world.put(Orientations.IDENTITY, 1, 1, 0, Blocks.STONE.defaultBlockState());
        world.put(Orientations.IDENTITY, 0, 2, 0, Blocks.GOLD_BLOCK.defaultBlockState());
        var result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertTrue(result.formed());
        assertEquals(1, result.repeats().count(0));
        assertFalse(result.metadata().assignedKeys().contains(MODULE));
    }

    @Test
    void unknownExternalFactsAreDistinctFromUnloadedChunksAndCannotBeNegated() {
        CellPredicate<DataKey<String>> pending = (key, context, metadata) -> {
            metadata.set(key, "discard me");
            return CellResult.unknown();
        };
        var unknown = CellBinding.of(pending, MODULE).metadata(MODULE);
        Blueprint blueprint = Blueprint.of().aisle("@U").where('U', CellPredicates.not(unknown)).build();
        var result = StructureEngine.recognize(new World(), blueprint, Orientations.IDENTITY);
        assertFalse(result.formed());
        assertEquals(0, result.unloadedCount());
        assertEquals(1, result.unknownCount());
        assertEquals(RecognitionResult.FailureReason.CELL_UNKNOWN, result.failureReason());
        assertFalse(result.metadata().assignedKeys().contains(MODULE));
    }

    @Test
    void loadedButUnavailableStateIsUnknownRatherThanAirOrAnOrdinaryMismatch() {
        Blueprint blueprint = Blueprint.of().aisle("@C").where('C', Blocks.STONE).build();
        StructureView view = new StructureView() {

            public BlockPos controllerPos() {
                return BlockPos.ZERO;
            }

            public boolean isLoaded(BlockPos pos) {
                return true;
            }

            public BlockState blockState(BlockPos pos) {
                return null;
            }

            public Set<net.ptcrys.topo.api.machine.multiblock.ability.PartRole> roles(BlockPos pos) {
                return Set.of();
            }
        };
        var result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);
        assertFalse(result.formed());
        assertEquals(0, result.missingCount());
        assertEquals(0, result.unloadedCount());
        assertEquals(1, result.unknownCount());
        assertEquals(RecognitionResult.FailureReason.CELL_UNKNOWN, result.failureReason());
        assertEquals(CellResult.Status.UNKNOWN, result.unknownCells().getFirst().result().status());
    }

    @Test
    void failedAlternativeKeepsItsErrorAndRollsBackBeforeTheSuccessfulAlternative() {
        CellPredicate<DataKey<String>> rejected = (key, context, metadata) -> {
            metadata.set(key, "wrong");
            return CellResult.fail(DIFFERENT, Map.of("block", "required"), Map.of("block", "wrong"));
        };
        var bad = CellBinding.of(rejected, MODULE).metadata(MODULE);
        var context = new CellContext(BlockPos.ZERO, new StructureView.CellInfo(Blocks.STONE.defaultBlockState(), Set.of(), null), false, null);
        var metadata = DataSnapshot.defaults(List.of(MODULE)).edit();
        assertTrue(CellPredicates.anyOf(bad, observed(MODULE)).evaluate(context, metadata).passed());
        assertEquals("minecraft:stone", metadata.get(MODULE));
        var failed = CellPredicates.allOf(bad, CellPredicates.air()).evaluate(context, metadata);
        assertEquals(CellResult.Status.FAIL, failed.status());
        assertTrue(failed.children().stream().anyMatch(issue -> issue.code().equals(DIFFERENT)));
        assertEquals("minecraft:stone", metadata.get(MODULE));
    }

    private static final class World implements StructureView {

        final Map<BlockPos, BlockState> states = new HashMap<>();

        public BlockPos controllerPos() {
            return BlockPos.ZERO;
        }

        public BlockState blockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        public Set<net.ptcrys.topo.api.machine.multiblock.ability.PartRole> roles(BlockPos pos) {
            return Set.of();
        }

        void put(Orientation orientation, int x, int y, int z, BlockState state) {
            states.put(orientation.apply(new BlockPos(x, y, z), BlockPos.ZERO), state);
        }

        static World chamber(Orientation orientation, BlockState casing) {
            World world = new World();
            for (int y = 0; y < 6; y++) for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
                boolean cavity = y < 5 && x > 0 && x < 3 && z > 0 && z < 3;
                world.put(orientation, x, y, z, cavity ? Blocks.AIR.defaultBlockState() : y == 5 ? Blocks.GLASS.defaultBlockState() : casing);
            }
            return world;
        }
    }
}
