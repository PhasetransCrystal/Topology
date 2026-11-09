package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.MultiblockTestRoles;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 诊断只消费引擎结果，在 4×4×6 舱体上检查未知状态、部件统计和独立单格查询。 */
class MultiblockClientDiagnosisTest {

    @Test
    void unloadedCellsAreNotCountedAsMatchedOrDisplayedAsAllMet() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        world.unloaded.add(new BlockPos(2, 2, 2));
        RecognitionResult result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        MultiblockClientDiagnosis.Report report = report(blueprint, result, 8);
        assertFalse(report.allMet());
        assertEquals(95, report.matchedCells());
        assertEquals(96, report.totalCells());
        assertEquals(1, report.totalProblems());
        assertEquals(MultiblockClientDiagnosis.Kind.CHUNK_UNLOADED, report.shown().getFirst().kind());
        assertTrue(report.shown().getFirst().kind().warning());
    }

    @Test
    void unknownGrowthProbeDoesNotIncreaseTheConcreteFootprintTotal() {
        Blueprint blueprint = chamber(true);
        World world = new World();
        world.unloaded.add(new BlockPos(0, 6, 0));
        RecognitionResult result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertEquals(1, result.unloadedCount());
        assertEquals(96, result.matchedCount());
        MultiblockClientDiagnosis.Report report = report(blueprint, result, 8);
        assertEquals(96, report.matchedCells());
        assertEquals(96, report.totalCells());
        assertFalse(report.allMet());
        assertEquals(MultiblockClientDiagnosis.Kind.CHUNK_UNLOADED, report.shown().getFirst().kind());
    }

    @Test
    void unsupportedOrientationHasAnExplicitReasonInsteadOfZeroOfZeroSuccess() {
        Blueprint blueprint = Blueprint.of()
                .aisle("@CCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CDCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .where('C', Blocks.STONE)
                .where('D', Blocks.FURNACE, PropertyRules.direction(BlockStateProperties.HORIZONTAL_FACING)).build();
        RecognitionResult result = StructureEngine.recognize(new World(), blueprint, Orientations.forFacing(Direction.UP));
        assertEquals(RecognitionResult.FailureReason.ORIENTATION_UNSUPPORTED, result.failureReason());
        MultiblockClientDiagnosis.Report report = report(blueprint, result, 8);
        assertFalse(report.allMet());
        assertEquals(0, report.matchedCells());
        assertEquals(96, report.totalCells());
        assertEquals(MultiblockClientDiagnosis.Kind.ORIENTATION_UNSUPPORTED, report.shown().getFirst().kind());
    }

    @Test
    void onePositionWithTwoRolesCountsAsOnePart() {
        Blueprint blueprint = chamber(false);
        RecognitionResult result = StructureEngine.recognize(new World(), blueprint, Orientations.IDENTITY);
        assertTrue(result.formed());
        assertEquals(2, result.roleCounts().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, result.partCount());
        MultiblockClientDiagnosis.Report report = report(blueprint, result, 8);
        assertTrue(report.allMet());
        assertEquals(1, report.partCount());
    }

    @Test
    void selectedCellBeyondThe64MissingSamplesStillShowsItsActualFailure() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        world.empty = true;
        RecognitionResult result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        BlockPos selected = new BlockPos(3, 5, 3);
        assertEquals(95, result.missingCount());
        assertEquals(64, result.missingCells().size());
        assertTrue(result.missingCells().stream().noneMatch(cell -> cell.localPos().equals(selected)));
        MultiblockClientDiagnosis.Reason reason = MultiblockClientDiagnosis.diagnoseCell(world, blueprint, Orientations.IDENTITY, result.repeats(), selected);
        assertNotNull(reason);
        assertEquals(selected, reason.localPos());
        assertEquals(MultiblockClientDiagnosis.Kind.EMPTY_CELL, reason.kind());
        world.unloaded.add(selected);
        assertEquals(MultiblockClientDiagnosis.Kind.CHUNK_UNLOADED,
                MultiblockClientDiagnosis.diagnoseCell(world, blueprint, Orientations.IDENTITY, result.repeats(), selected).kind());
    }

    @Test
    void displayLimitDoesNotHideTheUnderlyingFailureState() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        world.unloaded.add(new BlockPos(2, 2, 2));
        RecognitionResult result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        MultiblockClientDiagnosis.Report report = report(blueprint, result, 0);
        assertTrue(report.shown().isEmpty());
        assertFalse(report.allMet());
        assertEquals(1, report.hiddenProblems());
        assertThrows(IllegalArgumentException.class, () -> report(blueprint, result, -1));
    }

    @Test
    void mirrorSelectionIsSharedByReportDetailsAndDetectedBlocks() {
        Blueprint blueprint = Blueprint.of().frame(Direction.EAST, Direction.SOUTH)
                .aisle("C@CC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCD")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .where('C', Blocks.STONE)
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), PropertyRules.direction(BlockStateProperties.FACING))
                .build();
        BlockPos selected = new BlockPos(2, 3, 3);
        // frame(EAST, SOUTH) 先将 (x,y,z) 映射为 (y,-x,z)，整体镜像再翻转 X。
        BlockPos previewSelected = new BlockPos(-3, -2, 3);
        BlockState canonical = Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        for (Orientation primary : Orientations.rotations()) {
            Orientation mirror = primary.mirrored();
            Orientation effective = blueprint.effectiveOrientation(mirror);
            Map<BlockPos, BlockState> states = new HashMap<>();
            for (int x = -1; x <= 2; x++) {
                for (int y = 0; y < 6; y++) {
                    for (int z = 0; z < 4; z++) {
                        BlockPos local = new BlockPos(x, y, z);
                        states.put(effective.apply(local, BlockPos.ZERO), local.equals(selected) ? Orientations.rotated(effective, canonical) : Blocks.STONE.defaultBlockState());
                    }
                }
            }
            StructureView world = new StructureView() {

                @Override
                public BlockPos controllerPos() {
                    return BlockPos.ZERO;
                }

                @Override
                public BlockState blockState(BlockPos pos) {
                    return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                }

                @Override
                public Set<PartRole> roles(BlockPos pos) {
                    return Set.of();
                }
            };
            AtomicInteger captures = new AtomicInteger();
            MultiblockClientDiagnosis.Frame frame = MultiblockClientDiagnosis.capture(blueprint, primary, orientation -> {
                captures.incrementAndGet();
                return world;
            });
            assertEquals(2, captures.get());
            assertEquals(mirror, frame.orientation());
            assertTrue(frame.report(8).allMet());
            assertEquals(96, frame.report(8).matchedCells());
            assertNull(frame.diagnoseCell(previewSelected));
            assertSame(Orientations.rotated(effective, canonical), frame.expectedStateAt(previewSelected));
            assertSame(frame.expectedStateAt(previewSelected), frame.foundBlockAt(previewSelected));
            assertSame(canonical.setValue(BlockStateProperties.FACING, Direction.DOWN), frame.detectedBlocks().get(previewSelected));
            assertEquals(96, frame.detectedBlocks().size());
            assertNull(frame.foundBlockAt(new BlockPos(100, 100, 100)));
        }
    }

    @Test
    void candidateMetadataAndMissingCellReportsDoNotRequireItemComponents() {
        List<CellBinding> predicates = List.of(
                CellPredicates.block(Blocks.STONE),
                CellPredicates.block(() -> Blocks.STONE),
                CellPredicates.state(Blocks.STONE.defaultBlockState()),
                CellPredicates.anyOf(CellPredicates.block(Blocks.STONE), CellPredicates.block(Blocks.DIRT)),
                CellPredicates.allOf(CellPredicates.block(Blocks.STONE), CellPredicates.not(CellPredicates.air())));
        World world = new World();
        world.empty = true;
        for (CellBinding predicate : predicates) {
            assertSame(Blocks.STONE, predicate.candidateBlocks().getFirst());
            Blueprint blueprint = solidChamber().where('C', predicate).build();
            assertMissingCellReport(blueprint, world);
        }
        // 固定状态走另一条名称和图标来源，也必须保持为无需物品组件的声明数据。
        assertMissingCellReport(solidChamber().where('C', Blocks.STONE).build(), world);
        // 角色计数问题同样只返回方块声明，不在报告生成过程中创建部件物品栈。
        Blueprint countedBlueprint = chamber(false);
        MultiblockClientDiagnosis.Report counted = report(countedBlueprint, StructureEngine.recognize(world, countedBlueprint, Orientations.IDENTITY), 8);
        assertEquals(97, counted.totalProblems());
        assertTrue(counted.shown().stream().anyMatch(reason -> reason.kind() == MultiblockClientDiagnosis.Kind.MISSING_PART));
    }

    @Test
    void framedPreviewAndCellSelectionUseControllerForwardAcrossAllWorldRotations() {
        Blueprint blueprint = Blueprint.of().frame(Direction.EAST, Direction.SOUTH).aisle("@D")
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), PropertyRules.direction(BlockStateProperties.FACING)).build();
        BlockPos previewPos = new BlockPos(0, -1, 0);
        for (Orientation rotation : Orientations.rotations()) for (boolean mirrored : List.of(false, true)) {
            Orientation orientation = mirrored ? rotation.mirrored() : rotation;
            Map<BlockPos, BlockState> states = new HashMap<>();
            BlockPos worldPos = rotation.apply(previewPos, BlockPos.ZERO);
            Direction direction = rotation.apply(mirrored ? Direction.WEST : Direction.EAST);
            states.put(worldPos, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, direction));
            StructureView world = new StructureView() {

                public BlockPos controllerPos() {
                    return BlockPos.ZERO;
                }

                public BlockState blockState(BlockPos pos) {
                    return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                }

                public Set<PartRole> roles(BlockPos pos) {
                    return Set.of();
                }
            };
            var frame = MultiblockClientDiagnosis.capture(blueprint, orientation, unused -> world);
            assertTrue(frame.report(8).formed());
            assertTrue(frame.containsCell(previewPos));
            assertFalse(frame.containsCell(new BlockPos(1, 0, 0)));
            assertNull(frame.diagnoseCell(previewPos));
            assertEquals(mirrored ? Direction.WEST : Direction.EAST, frame.detectedBlocks().get(previewPos).getValue(BlockStateProperties.FACING));
            states.put(worldPos, Blocks.DIRT.defaultBlockState());
            var reason = frame.diagnoseCell(previewPos);
            assertNotNull(reason);
            assertEquals(previewPos, reason.localPos());
            assertEquals(MultiblockClientDiagnosis.Kind.WRONG_BLOCK, reason.kind());
        }
    }

    @Test
    void externalFailureSurvivesReportAndSingleCellDiagnosisWithoutBeingGuessedAsBlockFailure() {
        var code = Identifier.fromNamespaceAndPath("example", "custom_constraint");
        CellPredicate<String> detector = (input, context, metadata) -> CellResult.fail(code, Map.of("constraint", input), Map.of("observed", "other"));
        Blueprint blueprint = Blueprint.of().aisle("@C").where('C', CellBinding.of(detector, "required")).build();
        var frame = MultiblockClientDiagnosis.capture(blueprint, Orientations.IDENTITY, unused -> new World());
        var reason = frame.report(8).shown().getFirst();
        assertEquals(MultiblockClientDiagnosis.Kind.CONSTRAINT_FAILED, reason.kind());
        assertEquals(code, reason.cellResult().code());
        var selected = frame.diagnoseCell(new BlockPos(1, 0, 0));
        assertNotNull(selected);
        assertEquals(reason.cellResult(), selected.cellResult());
        assertTrue(selected.message().getString().contains(code.toString()));
    }

    private static void assertMissingCellReport(Blueprint blueprint, World world) {
        RecognitionResult result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        MultiblockClientDiagnosis.Report report = report(blueprint, result, 8);
        assertFalse(report.allMet());
        assertEquals(95, report.totalProblems());
        assertEquals(96, report.totalCells());
        assertEquals(8, report.shown().size());
        for (MultiblockClientDiagnosis.Reason reason : report.shown()) {
            assertEquals(MultiblockClientDiagnosis.Kind.EMPTY_CELL, reason.kind());
            assertEquals(Blocks.STONE.getName(), reason.title());
            assertEquals(List.of(Blocks.STONE), reason.iconBlocks());
        }
        MultiblockClientDiagnosis.Reason selected = MultiblockClientDiagnosis.diagnoseCell(world, blueprint, Orientations.IDENTITY, result.repeats(), new BlockPos(3, 5, 3));
        assertNotNull(selected);
        assertEquals(List.of(Blocks.STONE), selected.iconBlocks());
        assertThrows(UnsupportedOperationException.class, () -> selected.iconBlocks().add(Blocks.DIRT));
    }

    private static Blueprint.Builder solidChamber() {
        return Blueprint.of().aisle("@CCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC");
    }

    private static MultiblockClientDiagnosis.Report report(Blueprint blueprint, RecognitionResult result, int max) {
        return MultiblockClientDiagnosis.report(blueprint, result, max, 0, 0);
    }

    private static Blueprint chamber(boolean repeatable) {
        Blueprint.Builder builder = Blueprint.of().aisle("@CCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC");
        if (repeatable) builder.beginRepeatable(2, 4).aisle("CCCC", "CCCC", "CCCC", "CCCC").endRepeatable();
        else builder.aisle("CCCC", "CCCC", "CCCC", "CCCC").aisle("CCCC", "CCCC", "CCCC", "CCCC");
        return builder.where('C', CellPredicates.anyOf(CellPredicates.block(Blocks.STONE), CellPredicates.role(MultiblockTestRoles.INPUT), CellPredicates.role(MultiblockTestRoles.OUTPUT)))
                .count(MultiblockTestRoles.INPUT, 1, 1).count(MultiblockTestRoles.OUTPUT, 1, 1).build();
    }

    private static final class World implements StructureView {

        private final Set<BlockPos> unloaded = new HashSet<>();
        private boolean empty;

        @Override
        public BlockPos controllerPos() {
            return BlockPos.ZERO;
        }

        @Override
        public BlockState blockState(BlockPos pos) {
            return !empty && pos.getY() >= 0 && pos.getY() < 6 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return !unloaded.contains(pos);
        }

        @Override
        public Set<PartRole> roles(BlockPos pos) {
            return !empty && pos.equals(new BlockPos(1, 1, 1)) ? Set.of(MultiblockTestRoles.INPUT, MultiblockTestRoles.OUTPUT) : Set.of();
        }
    }
}
