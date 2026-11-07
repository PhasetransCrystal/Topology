package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.MultiblockTestRoles;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

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

/** 最小 5×5×6 的组合场景；世界样本独立按施工坐标生成，不使用待测布局展开。 */
class ComplexBlueprintTest {

    private static final BlockPos ORIGIN = new BlockPos(15, 73, -17);
    private static final String[] FLOOR = { "SSSSS", "SSSSS", "SSSSS", "SSSSS", "SSSSS" };
    private static final String[] CASING = { "CCCCC", "C###C", "C###C", "C###C", "CCCCC" };
    private static final String[] CONTROL = { "C@ICC", "C###C", "C###C", "C###C", "CCOCC" };
    private static final String[] PROCESS = { "CCCCC", "C###C", "C#D#C", "C###C", "CCCCC" };
    private static final String[] GLASS = { "GGGGG", "G###G", "G###G", "G###G", "GGGGG" };
    private static final String[] CAP = { "TTTTT", "T###T", "T###T", "T###T", "TTTTT" };
    private static final String[] LOGS = { "  L  ", "  L  ", "  L  ", "  L  ", "  L  " };

    @Test
    void minimumFixtureContains150CellsAnd106SolidBlocks() {
        World view = variableWorld(Orientations.IDENTITY, 1, 1, 0);
        Blueprint blueprint = variableBlueprint(false);
        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(150, result.matchedCount());
        assertEquals(106L, view.states.values().stream().filter(state -> !state.isAir()).count());
        assertEquals(new RecognitionResult.Bounds(14, 71, -17, 18, 76, -13), result.bounds());
        assertFalse(blueprint.supportsSnapshot());
        assertThrows(IllegalArgumentException.class, () -> CompiledSnapshot.builder(blueprint.compiled(Orientations.IDENTITY), ORIGIN));
    }

    @Test
    void asymmetricShellResolvesEveryCombinationAcross24RotationsAndTwoFrames() {
        for (boolean sideways : List.of(false, true)) {
            Blueprint blueprint = variableBlueprint(sideways);
            // 手工指定声明基变换，避免用 blueprint.frame() 生成测试世界。
            Orientation frame = sideways ? Orientation.fromColumns(Direction.DOWN, Direction.EAST, Direction.SOUTH) : Orientations.IDENTITY;
            for (Orientation machine : Orientations.rotations()) {
                Orientation effective = machine.compose(frame);
                for (int lower = 1; lower <= 2; lower++) {
                    for (int groups = 1; groups <= 3; groups++) {
                        for (int optional = 0; optional <= 1; optional++) {
                            String context = machine + " / " + sideways + " / " + lower + "," + groups + "," + optional;
                            World world = variableWorld(effective, lower, groups, optional);
                            RecognitionResult result = StructureEngine.recognize(world, blueprint, machine);
                            assertTrue(result.formed(), context);
                            assertEquals(new RepeatResolution(new int[] { lower, groups, optional }), result.repeats(), context);
                            assertEquals(world.states.size(), result.matchedCount(), context);
                            assertEquals(world.bounds(), result.bounds(), context);
                            StructureEngine.Verification verification = StructureEngine.verify(world, blueprint, machine);
                            assertTrue(verification.formed(), context);
                            assertEquals(result.repeats(), verification.repeats(), context);
                            assertLayoutMatchesWorld(blueprint, blueprint.compiled(machine), result.repeats(), effective, world, context);
                        }
                    }
                }
            }
        }
    }

    @Test
    void movingCapAirCoreAndDirectionalStateEachRejectLocalDamage() {
        Blueprint blueprint = variableBlueprint(false);
        for (Orientation rotation : Orientations.rotations()) {
            // 两组中后一组的方向方块、内腔及已推移的顶盖。
            for (BlockPos local : List.of(new BlockPos(1, 3, 2), new BlockPos(0, 2, 1), new BlockPos(3, 5, 4))) {
                World world = variableWorld(rotation, 2, 2, 1);
                BlockPos damaged = ORIGIN.offset(rotation.apply(local, BlockPos.ZERO));
                BlockState original = world.states.get(damaged);
                world.states.put(damaged, original.is(Blocks.DISPENSER) ? original.setValue(BlockStateProperties.FACING, rotation.apply(Direction.WEST)) : original.isAir() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                RecognitionResult result = StructureEngine.recognize(world, blueprint, rotation);
                assertFalse(result.formed(), local.toString());
                assertTrue(result.missingCount() > 0);
                assertFalse(StructureEngine.verify(world, blueprint, rotation).formed());
                assertTrue(result.missingCells().stream().anyMatch(cell -> cell.worldPos().equals(damaged)));
            }
        }
    }

    @Test
    void unloadedAirCellAcrossChunkBoundaryIsUnknownAndDoesNotBecomeMissing() {
        Blueprint blueprint = variableBlueprint(false);
        World world = variableWorld(Orientations.IDENTITY, 2, 3, 1);
        BlockPos air = ORIGIN.offset(2, 0, 3);
        assertNotEquals(ORIGIN.getX() >> 4, air.getX() >> 4);
        world.unloaded.add(air);
        RecognitionResult unknown = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertFalse(unknown.formed());
        assertEquals(1, unknown.unloadedCount());
        assertEquals(0, unknown.missingCount());
        assertEquals(RecognitionResult.FailureReason.CHUNK_UNLOADED, unknown.failureReason());
        assertTrue(StructureEngine.verify(world, blueprint, Orientations.IDENTITY).unloaded());
        world.unloaded.clear();
        world.states.put(air, Blocks.STONE.defaultBlockState());
        RecognitionResult broken = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertEquals(0, broken.unloadedCount());
        assertEquals(1, broken.missingCount());
    }

    @Test
    void stableCaptureIsABijectionAndAgreesWithLiveRecognitionForEveryRotation() {
        Blueprint blueprint = stableBlueprint();
        assertTrue(blueprint.supportsSnapshot());
        for (Orientation rotation : Orientations.rotations()) {
            CompiledBlueprint compiled = blueprint.compiled(rotation);
            assertEquals(500, compiled.captureSize());
            for (int groups : List.of(1, 3, 8)) {
                World world = stableWorld(rotation, groups);
                CompiledSnapshot.Builder builder = CompiledSnapshot.builder(compiled, ORIGIN);
                Set<Integer> indices = new HashSet<>();
                Set<BlockPos> positions = new HashSet<>();
                for (int n = 0; n < compiled.captureSize(); n++) {
                    int index = compiled.captureCellIndexAt(n);
                    BlockPos pos = ORIGIN.offset(BlockPos.of(compiled.captureOffsetAt(n)));
                    assertTrue(indices.add(index));
                    assertTrue(positions.add(pos));
                    assertEquals(index, compiled.cellIndexOfWorld(pos, ORIGIN));
                    builder.put(index, pos.asLong(), world.blockState(pos), world.member(pos));
                    builder.roles(pos.asLong(), world.roles(pos));
                }
                CompiledSnapshot snapshot = builder.build();
                RecognitionResult live = StructureEngine.recognize(world, blueprint, rotation);
                RecognitionResult captured = StructureEngine.recognize(snapshot, blueprint, rotation);
                assertTrue(live.formed());
                assertEquals(live, captured);
                assertEquals(groups, captured.repeats().count(0));
                assertEquals(Set.copyOf(world.memberPositions), Set.copyOf(captured.members()));
                assertThrows(IllegalStateException.class, builder::build);
                assertThrows(IllegalStateException.class, () -> builder.put(0, ORIGIN.asLong(), Blocks.AIR.defaultBlockState(), null));
                assertThrows(IllegalStateException.class, () -> builder.roles(ORIGIN.asLong(), Set.of()));
            }
        }
    }

    @Test
    void denseDamageCountsAllMissingCellsButBoundsTheDiagnosticSample() {
        Blueprint blueprint = stableBlueprint();
        World world = stableWorld(Orientations.IDENTITY, 3);
        // 中间三层均为实心底座，共 75 个缺失；保留控制器层和所有重复组。
        world.states.replaceAll((pos, state) -> pos.getY() > ORIGIN.getY() && pos.getY() <= ORIGIN.getY() + 3 ? Blocks.AIR.defaultBlockState() : state);
        RecognitionResult result = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertFalse(result.formed());
        assertEquals(75, result.missingCount());
        assertEquals(64, result.missingCells().size());
        assertEquals(250 - 75, result.matchedCount());
        assertEquals(3, result.repeats().count(0));
    }

    @Test
    void mismatchAtWorldOriginIsNotConfusedWithNoMismatch() {
        Blueprint blueprint = stableBlueprint();
        World world = new World(new BlockPos(1, 0, 0));
        // 世界原点是控制器左侧的机壳位置，未放置方块。
        StructureEngine.Verification result = StructureEngine.verify(world, blueprint, Orientations.IDENTITY);
        assertFalse(result.formed());
        assertEquals(BlockPos.ZERO, result.firstMismatchPos());
    }

    @Test
    void roleCountsCanRejectOtherwiseMatchingLargeStructureWithoutRequiringVanillaBlockEntities() {
        Blueprint blueprint = variableBlueprint(false);
        World world = variableWorld(Orientations.IDENTITY, 2, 3, 1);
        RecognitionResult intact = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertTrue(intact.formed());
        assertEquals(Map.of(MultiblockTestRoles.INPUT, 1, MultiblockTestRoles.OUTPUT, 1), intact.roleCounts());
        assertEquals(2, intact.members().size());
        world.roles.remove(ORIGIN.offset(1, 0, 0));
        RecognitionResult missingRole = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertFalse(missingRole.formed());
        assertEquals(0, missingRole.missingCount());
        assertFalse(StructureEngine.verify(world, blueprint, Orientations.IDENTITY).formed());
        world.roles.put(ORIGIN.offset(1, 0, 0), Set.of(MultiblockTestRoles.INPUT));
        world.roles.put(ORIGIN.offset(-1, 0, 0), Set.of(MultiblockTestRoles.INPUT));
        RecognitionResult excess = StructureEngine.recognize(world, blueprint, Orientations.IDENTITY);
        assertFalse(excess.formed());
        assertEquals(2, excess.roleCounts().get(MultiblockTestRoles.INPUT));
        assertEquals(0, excess.missingCount());
    }

    @Test
    void reservedSymbolsAndDeclaredRowsCannotBeReboundOrMutated() {
        assertThrows(IllegalArgumentException.class, () -> Blueprint.of().where('@', CellPredicates.controller()));
        assertThrows(IllegalArgumentException.class, () -> Blueprint.of().where('#', CellPredicates.air()));
        String[] rows = CONTROL.clone();
        Blueprint.Builder builder = Blueprint.of().aisle(rows).where('C', Blocks.COPPER_BLOCK).where('I', Blocks.IRON_BLOCK).where('O', Blocks.LAPIS_BLOCK);
        rows[0] = "CCCCC";
        Blueprint blueprint = builder.build();
        assertEquals(25, blueprint.cellCount(blueprint.minRepeats()));
        assertThrows(IllegalArgumentException.class, () -> variableBlueprint(false).layout(new RepeatResolution(new int[] { 0, 1, 0 })));
    }

    private static Blueprint variableBlueprint(boolean sideways) {
        Blueprint.Builder builder = Blueprint.of();
        if (sideways) builder.frame(Direction.EAST, Direction.SOUTH);
        return definitions(builder.aisle(FLOOR)
                .beginRepeatable(1, 2).aisle(CASING).endRepeatable()
                .aisle(CONTROL)
                .beginRepeatable(1, 3).aisle(PROCESS).aisle(GLASS).endRepeatable()
                .aisle(CAP)
                .beginRepeatable(0, 1).aisle(LOGS).endRepeatable(), true).build();
    }

    private static Blueprint stableBlueprint() {
        return definitions(Blueprint.of().aisle(CONTROL).aisle(FLOOR).aisle(FLOOR).aisle(FLOOR)
                .beginRepeatable(1, 8).aisle(PROCESS).aisle(GLASS).endRepeatable(), false).build();
    }

    private static Blueprint.Builder definitions(Blueprint.Builder builder, boolean variable) {
        if (variable) builder.where('T', Blocks.GOLD_BLOCK)
                .where('L', Blocks.OAK_LOG, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), PropertyRules.axis(BlockStateProperties.AXIS));
        return builder.where('S', Blocks.STONE_BRICKS).where('C', Blocks.COPPER_BLOCK)
                .where('I', CellPredicates.anyOf(CellPredicates.block(Blocks.IRON_BLOCK), CellPredicates.role(MultiblockTestRoles.INPUT)))
                .where('O', CellPredicates.anyOf(CellPredicates.block(Blocks.LAPIS_BLOCK), CellPredicates.role(MultiblockTestRoles.OUTPUT))).where('G', Blocks.GLASS)
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), PropertyRules.direction(BlockStateProperties.FACING))
                .count(MultiblockTestRoles.INPUT, 1, 1).count(MultiblockTestRoles.OUTPUT, 1, 1);
    }

    private static World variableWorld(Orientation rotation, int lower, int groups, int optional) {
        World world = new World(ORIGIN);
        world.plane(rotation, -lower - 1, FLOOR);
        for (int y = -lower; y < 0; y++) world.plane(rotation, y, CASING);
        world.plane(rotation, 0, CONTROL);
        for (int n = 0; n < groups; n++) {
            world.plane(rotation, 1 + n * 2, PROCESS);
            world.plane(rotation, 2 + n * 2, GLASS);
        }
        world.plane(rotation, groups * 2 + 1, CAP);
        if (optional == 1) world.plane(rotation, groups * 2 + 2, LOGS);
        return world;
    }

    private static World stableWorld(Orientation rotation, int groups) {
        World world = new World(ORIGIN);
        world.plane(rotation, 0, CONTROL);
        for (int y = 1; y <= 3; y++) world.plane(rotation, y, FLOOR);
        for (int n = 0; n < groups; n++) {
            world.plane(rotation, 4 + n * 2, PROCESS);
            world.plane(rotation, 5 + n * 2, GLASS);
        }
        world.memberPositions.add(ORIGIN.offset(rotation.apply(new BlockPos(1, 0, 0), BlockPos.ZERO)));
        world.memberPositions.add(ORIGIN.offset(rotation.apply(new BlockPos(1, 0, 4), BlockPos.ZERO)));
        return world;
    }

    private static void assertLayoutMatchesWorld(Blueprint blueprint, CompiledBlueprint compiled, RepeatResolution repeats, Orientation rotation, World world, String context) {
        BlueprintLayout layout = blueprint.layout(repeats);
        Set<BlockPos> expanded = new HashSet<>();
        layout.forEachCell((index, definition, x, y, z) -> {
            assertEquals(index, layout.cellIndexAt(x, y, z), context);
            BlockPos pos = ORIGIN.offset(rotation.apply(new BlockPos(x, y, z), BlockPos.ZERO));
            assertTrue(expanded.add(pos), context);
            assertEquals(index, compiled.cellIndexOfWorld(pos, ORIGIN, repeats), context);
            assertTrue(compiled.containsWorldAnyLayout(pos, ORIGIN), context);
            assertTrue(StructureEngine.evaluateCompiled(world, pos, compiled.definitionAt(index), StructureMetadata.defaults(java.util.List.of(blueprint)).edit()).passed(), context);
            return true;
        });
        assertEquals(world.states.keySet(), expanded, context);
        Set<BlockPos> footprint = new HashSet<>();
        compiled.forEachWorldPos(ORIGIN, repeats, packed -> assertTrue(footprint.add(BlockPos.of(packed)), context));
        assertEquals(expanded, footprint, context);
        int[] bounds = compiled.worldBounds(ORIGIN);
        assertTrue(bounds[0] <= world.bounds().minX() && bounds[3] >= world.bounds().maxX(), context);
        assertTrue(bounds[1] <= world.bounds().minY() && bounds[4] >= world.bounds().maxY(), context);
        assertTrue(bounds[2] <= world.bounds().minZ() && bounds[5] >= world.bounds().maxZ(), context);
        assertEquals(world.states.size(), layout.cellCount(), context);
        assertEquals(-1, layout.cellIndexAt(100, 100, 100));
        assertEquals(blueprint.cells(repeats).size(), expanded.size());
    }

    private static final class World implements StructureView {

        private final BlockPos origin;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private final Set<BlockPos> memberPositions = new HashSet<>();
        private final Map<BlockPos, Set<PartRole>> roles = new HashMap<>();

        World(BlockPos origin) {
            this.origin = origin;
        }

        void plane(Orientation rotation, int y, String[] rows) {
            for (int z = 0; z < rows.length; z++) {
                for (int col = 0; col < rows[z].length(); col++) {
                    char symbol = rows[z].charAt(col);
                    if (symbol == ' ') continue;
                    BlockState state = switch (symbol) {
                        case 'S' -> Blocks.STONE_BRICKS.defaultBlockState();
                        case 'C' -> Blocks.COPPER_BLOCK.defaultBlockState();
                        case 'I' -> Blocks.IRON_BLOCK.defaultBlockState();
                        case 'O' -> Blocks.LAPIS_BLOCK.defaultBlockState();
                        case 'G' -> Blocks.GLASS.defaultBlockState();
                        case 'T' -> Blocks.GOLD_BLOCK.defaultBlockState();
                        case 'D' -> Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, rotation.apply(Direction.EAST));
                        case 'L' -> Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, rotation.apply(Direction.EAST).getAxis());
                        case '@' -> Blocks.BEDROCK.defaultBlockState();
                        case '#' -> Blocks.AIR.defaultBlockState();
                        default -> throw new IllegalArgumentException("Unknown construction symbol: " + symbol);
                    };
                    BlockPos pos = rotation.apply(new BlockPos(col - 1, y, z), origin);
                    states.put(pos, state);
                    if (symbol == 'I' || symbol == 'O') {
                        memberPositions.add(pos);
                        roles.put(pos, Set.of(symbol == 'I' ? MultiblockTestRoles.INPUT : MultiblockTestRoles.OUTPUT));
                    }
                }
            }
        }

        RecognitionResult.Bounds bounds() {
            return new RecognitionResult.Bounds(states.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                    states.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow(), states.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow(),
                    states.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow(), states.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow(),
                    states.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow());
        }

        @Override
        public BlockPos controllerPos() {
            return origin;
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
        public Set<PartRole> roles(BlockPos pos) {
            return roles.getOrDefault(pos, Set.of());
        }

        @Override
        public Member member(BlockPos pos) {
            return memberPositions.contains(pos) ? new Member(pos, true) : null;
        }
    }
}
