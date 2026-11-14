package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;
import net.ptcrys.topo.api.machine.multiblock.ui.BlueprintPreview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 不创建物品栈，直接验证场景范围和点选使用的组合诊断数据。 */
class MultiblockCompositePreviewTest {

    @Test
    void bodyAndFramedModuleKeepTheirExpandedLayoutsAcrossAllRotationsAndMirrors() {
        Blueprint body = body();
        Blueprint module = module();
        List<MultiblockModule> modules = List.of(MultiblockModule.of("side", module));
        RepeatResolution bodyRepeats = new RepeatResolution(new int[] { 7 });
        RepeatResolution moduleRepeats = new RepeatResolution(new int[] { 5 });
        for (Orientation rotation : Orientations.rotations()) for (boolean mirrored : List.of(false, true)) {
            Orientation orientation = mirrored ? rotation.mirrored() : rotation;
            World world = new World();
            fill(world, body, bodyRepeats, orientation);
            fill(world, module, moduleRepeats, orientation);
            var frame = MultiblockClientDiagnosis.capture(body, modules, rotation, (blueprint, chosen) -> world);
            assertEquals(orientation, frame.orientation());
            Set<BlockPos> expectedPositions = positions(body, bodyRepeats, mirrored);
            expectedPositions.addAll(positions(module, moduleRepeats, mirrored));
            assertEquals(304, expectedPositions.size()); // 4×4×10 本体 + 4×4×9 侧舱，共享控制器只计一次。
            assertEquals(expectedPositions, frame.positions());
            assertEquals(expectedPositions, frame.detectedBlocks().keySet());
            BlockPos declared = new BlockPos(3, 8, 9);
            BlockPos selected = BlueprintPreview.previewPosition(module, declared, mirrored);
            assertTrue(frame.containsCell(selected));
            assertNull(frame.diagnoseCell(selected));
            assertSame(frame.expectedStateAt(selected), frame.foundBlockAt(selected));
            BlockState canonical = BlueprintPreview.previewState(module, module.cells(moduleRepeats).stream().filter(cell -> cell.localPos().equals(declared)).findFirst().orElseThrow(), Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), mirrored);
            assertSame(canonical, frame.detectedBlocks().get(selected));
            world.blocks.put(module.effectiveOrientation(orientation).apply(declared, world.controllerPos()), Blocks.DIRT.defaultBlockState());
            assertEquals(MultiblockClientDiagnosis.Kind.WRONG_BLOCK, frame.diagnoseCell(selected).kind());
        }
    }

    @Test
    void aNewFrameTracksGrowthShrinkAndModuleCompletionInsteadOfTheOpeningFootprint() {
        Blueprint body = body();
        Blueprint module = module();
        List<MultiblockModule> modules = List.of(MultiblockModule.of("side", module));
        World world = new World();
        fill(world, body, body.minRepeats(), Orientations.IDENTITY);
        var initial = MultiblockClientDiagnosis.capture(body, modules, Orientations.IDENTITY, (blueprint, chosen) -> world);
        BlockPos grownTop = new BlockPos(3, 9, 3);
        assertFalse(initial.containsCell(grownTop));
        assertEquals(96, initial.detectedBlocks().size());
        world.blocks.clear();
        fill(world, body, body.maxRepeats(), Orientations.IDENTITY);
        fill(world, module, module.maxRepeats(), Orientations.IDENTITY);
        var expanded = MultiblockClientDiagnosis.capture(body, modules, Orientations.IDENTITY, (blueprint, chosen) -> world);
        assertTrue(expanded.containsCell(grownTop));
        assertEquals(304, expanded.detectedBlocks().size());
        world.blocks.clear();
        fill(world, body, body.minRepeats(), Orientations.IDENTITY);
        var shrunk = MultiblockClientDiagnosis.capture(body, modules, Orientations.IDENTITY, (blueprint, chosen) -> world);
        assertFalse(shrunk.containsCell(grownTop));
        assertEquals(initial.positions(), shrunk.positions());
        assertEquals(96, shrunk.detectedBlocks().size());
    }

    @Test
    void moduleDetailsReadTheModulesOwnSnapshotRatherThanTheBodySnapshot() {
        Blueprint body = Blueprint.of().aisle("@CCC", "CCCC", "CCCC", "CCCC")
                .beginRepeatable(5, 8).aisle("CCCC", "CCCC", "CCCC", "CCCC").endRepeatable()
                .where('C', Blocks.STONE).build();
        Blueprint module = Blueprint.of().frame(Direction.EAST, Direction.SOUTH)
                .aisle("@   ", "    ", "    ", "    ", "    ", "    ", "MMMM", "MMMM", "MMMM", "MMMM")
                .beginRepeatable(4, 4).aisle("    ", "    ", "    ", "    ", "    ", "    ", "MMMM", "MMMM", "MMMM", "MMMM").endRepeatable()
                .aisle("    ", "    ", "    ", "    ", "    ", "    ", "TTTT", "TTTT", "TTTT", "TTTD")
                .where('M', Blocks.IRON_BLOCK).where('T', Blocks.DIAMOND_BLOCK)
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), PropertyRules.direction(BlockStateProperties.FACING)).build();
        assertTrue(body.supportsSnapshot(), "末尾可变段的已有副本位置保持稳定，应能采集最大占地快照");
        assertTrue(module.supportsSnapshot(), "固定 4 次的重复段不会使后续顶层移动，应能采集快照");
        World world = new World();
        fill(world, body, body.maxRepeats(), Orientations.IDENTITY);
        fill(world, module, module.maxRepeats(), Orientations.IDENTITY);
        var frame = MultiblockClientDiagnosis.capture(body, List.of(MultiblockModule.of("side", module)), Orientations.IDENTITY, (blueprint, chosen) -> {
            CompiledBlueprint compiled = blueprint.compiled(chosen);
            var builder = CompiledSnapshot.builder(compiled, world.controllerPos());
            blueprint.layout(blueprint.maxRepeats()).forEachCell((index, definition, x, y, z) -> {
                BlockPos pos = blueprint.effectiveOrientation(chosen).apply(new BlockPos(x, y, z), world.controllerPos());
                builder.put(index, pos.asLong(), world.blockState(pos), null);
                return true;
            });
            return builder.build();
        });
        world.blocks.clear();
        BlockPos selected = BlueprintPreview.previewPosition(module, new BlockPos(3, 5, 9), false);
        assertSame(Blocks.DISPENSER, frame.foundBlockAt(selected).getBlock());
        assertSame(frame.expectedStateAt(selected), frame.foundBlockAt(selected));
        assertTrue(frame.detectedBlocks().containsKey(selected));
        assertNull(frame.diagnoseCell(selected));
    }

    @Test
    void onlyVariableRepeatCountsMakeFollowingCellsAndTheControllerAnchorMove() {
        for (boolean controllerFirst : List.of(false, true)) for (int max : List.of(4, 5)) {
            Blueprint blueprint = Blueprint.of().frame(Direction.EAST, Direction.SOUTH)
                    .aisle(controllerFirst ? "@CCC" : "CCCC", "CCCC", "CCCC", "CCCC")
                    .beginRepeatable(4, max).aisle("CCCC", "CCCC", "CCCC", "CCCC").endRepeatable()
                    .aisle(controllerFirst ? "TTTT" : "@TTT", "TTTT", "TTTT", "TTTT")
                    .where('C', Blocks.STONE).where('T', Blocks.GOLD_BLOCK).build();
            String context = "controllerFirst=" + controllerFirst + ", repeats=4.." + max;
            assertEquals(max == 4, blueprint.supportsSnapshot(), context);
            if (max == 4) {
                CompiledBlueprint compiled = blueprint.compiled(Orientations.IDENTITY);
                assertEquals(96, compiled.captureSize(), context);
                assertDoesNotThrow(() -> CompiledSnapshot.builder(compiled, BlockPos.ZERO), context);
            } else {
                assertThrows(IllegalArgumentException.class, () -> CompiledSnapshot.builder(blueprint.compiled(Orientations.IDENTITY), BlockPos.ZERO), context);
            }
        }
    }

    private static Blueprint body() {
        return Blueprint.of().aisle("@CCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .beginRepeatable(3, 7).aisle("CCCC", "CCCC", "CCCC", "CCCC").endRepeatable()
                .aisle("TTTT", "TTTT", "TTTT", "TTTT")
                .where('C', Blocks.STONE).where('T', Blocks.GOLD_BLOCK).build();
    }

    private static Blueprint module() {
        return Blueprint.of().frame(Direction.EAST, Direction.SOUTH)
                .aisle("@   ", "    ", "    ", "    ", "    ", "    ", "MMMM", "MMMM", "MMMM", "MMMM")
                .aisle("    ", "    ", "    ", "    ", "    ", "    ", "MMMM", "MMMM", "MMMM", "MMMM")
                .aisle("    ", "    ", "    ", "    ", "    ", "    ", "MMMM", "MMMM", "MMMM", "MMMM")
                .beginRepeatable(2, 5).aisle("    ", "    ", "    ", "    ", "    ", "    ", "MMMM", "MMMM", "MMMM", "MMMM").endRepeatable()
                .aisle("    ", "    ", "    ", "    ", "    ", "    ", "TTTT", "TTTT", "TTTT", "TTTD")
                .where('M', Blocks.IRON_BLOCK).where('T', Blocks.DIAMOND_BLOCK)
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), PropertyRules.direction(BlockStateProperties.FACING)).build();
    }

    private static Set<BlockPos> positions(Blueprint blueprint, RepeatResolution repeats, boolean mirrored) {
        Set<BlockPos> positions = new HashSet<>();
        for (Cell cell : blueprint.cells(repeats)) positions.add(BlueprintPreview.previewPosition(blueprint, cell.localPos(), mirrored));
        return positions;
    }

    private static void fill(World world, Blueprint blueprint, RepeatResolution repeats, Orientation orientation) {
        Orientation effective = blueprint.effectiveOrientation(orientation);
        for (Cell cell : blueprint.cells(repeats)) {
            BlockState state = cell.localPos().equals(BlockPos.ZERO) ? Blocks.STONE.defaultBlockState() : cell.expectedState() != null ? cell.expectedState() : cell.binding().candidateBlocks().getFirst().defaultBlockState();
            world.blocks.put(effective.apply(cell.localPos(), world.controllerPos()), Orientations.rotated(effective, state));
        }
    }

    private static final class World implements StructureView {

        final Map<BlockPos, BlockState> blocks = new HashMap<>();

        public BlockPos controllerPos() {
            return BlockPos.ZERO;
        }

        public BlockState blockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        public Set<PartRole> roles(BlockPos pos) {
            return Set.of();
        }
    }
}
