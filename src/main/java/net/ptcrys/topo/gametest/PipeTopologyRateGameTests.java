package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

/**
 * 管道拓扑速率游戏内测试(全局编号 282-285):网络的有效传输速率应等于源→汇之间按
 * 节点吞吐计的最大流(最小割),并行不相交路径叠加、单点腰部封顶。四个场景对应玩家
 * 搭建的四类网格:角对角满网格(双邻路=2x)、双环单腰(1x)、双股并联(2x)、满网格
 * 中点对中点(三邻路=3x)。
 *
 * <p>
 * 场地约定:y=1 平面铺管于 5×5 石地板,绿=基础物品管(节点吞吐 8/t),
 * 紫=终极物品管(节点吞吐 512/t、抽取 64/t、最小聚合 5t),紫管只作端点引流,
 * 绿管格构成被测的容量网络。批次断言口径与测试 159 相同:批次整批落账,
 * 批量 = 最小割节点数 × 8 × interval;连续两批等量证明稳态,且总量守恒。
 */
public final class PipeTopologyRateGameTests {

    private static final String SUITE = "pipe_topology_rate";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 4;
    /** 基础物品管节点吞吐(/t):速率断言的"1 倍绿管"单位。 */
    private static final int BASIC_RATE = 8;
    private static final int SOURCE_FILL = 27 * 64;

    private PipeTopologyRateGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!TopoScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("pipe_topology_rate"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(event, environment, index++,
                "pipe_topology_corner_lattice_doubles_throughput",
                "Tests max-flow rate semantics on a full 3x4 basic lattice with elite corner " + "endpoints: two node-disjoint paths leave the entry corner, so the " + "steady batch must be 2x the basic node throughput.",
                PipeTopologyRateGameTests::pipeCornerLatticeDoublesThroughput);
        register(event, environment, index++,
                "pipe_topology_single_waist_caps_one_lane",
                "Tests the 1x control topology: two loops joined by a single basic waist node " + "cap the route at exactly one lane regardless of loop redundancy.",
                PipeTopologyRateGameTests::pipeSingleWaistCapsOneLane);
        register(event, environment, index++,
                "pipe_topology_twin_strands_double_throughput",
                "Tests parallel-strand aggregation: two node-disjoint basic strands between " + "elite endpoints must carry 2x the basic node throughput.",
                PipeTopologyRateGameTests::pipeTwinStrandsDoubleThroughput);
        register(event, environment, index,
                "pipe_topology_three_column_grid_triples_throughput",
                "Tests max-flow on a full 3x3 basic grid with mid-edge elite endpoints: the " + "entry node feeds three node-disjoint columns, so the steady batch " + "must be 3x the basic node throughput.",
                PipeTopologyRateGameTests::pipeThreeColumnGridTriplesThroughput);
    }

    /** 测试 282:3×4 满网格角对角(对应截图 1)——入口紫角两条绿邻路,稳态 2× 绿管速率。 */
    private static void pipeCornerLatticeDoublesThroughput(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(4, 1, 0));
        ChestBlockEntity target = chest(helper, new BlockPos(0, 1, 4));
        fillChest(source, Items.COAL, SOURCE_FILL);
        // 紫端:东北引流柄 + 入口角 + 出口角;绿格:3×4 满网格其余 10 节点。
        placePipe(helper, new BlockPos(3, 1, 0), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 1), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(1, 1, 4), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 4; z++) {
                if ((x == 3 && z == 1) || (x == 1 && z == 4)) {
                    continue;
                }
                placePipe(helper, new BlockPos(x, 1, z), BuiltinTopoPipes.ITEM_PIPE_BASIC);
            }
        }
        int interval = startExtraction(helper, new BlockPos(3, 1, 0), Direction.EAST);
        assertSteadyBatches(helper, source, target, new BlockPos(3, 1, 1), 13, 2 * BASIC_RATE * interval);
    }

    /** 测试 283:双环单腰 S 形(对应截图 2)——环内冗余不增流,单腰节点钳 1× 绿管速率。 */
    private static void pipeSingleWaistCapsOneLane(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 0));
        ChestBlockEntity target = chest(helper, new BlockPos(0, 1, 4));
        fillChest(source, Items.COAL, SOURCE_FILL);
        placePipe(helper, new BlockPos(1, 1, 0), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(1, 1, 4), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        // 北环 2×2、单腰 (3,2)、南环 2×2:唯一割点是腰。
        placePipe(helper, new BlockPos(2, 1, 0), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 0), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 1), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 3), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 4), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 4), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        int interval = startExtraction(helper, new BlockPos(1, 1, 0), Direction.WEST);
        assertSteadyBatches(helper, source, target, new BlockPos(1, 1, 0), 11, BASIC_RATE * interval);
    }

    /** 测试 284:双股并联(对应截图 3)——两条点不相交绿股叠加,稳态 2× 绿管速率。 */
    private static void pipeTwinStrandsDoubleThroughput(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        fillChest(source, Items.COAL, SOURCE_FILL);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        // 北股与南股各 3 节点,中间 (2,2) 留空成两条独立车道。
        for (int x = 1; x <= 3; x++) {
            placePipe(helper, new BlockPos(x, 1, 1), BuiltinTopoPipes.ITEM_PIPE_BASIC);
            placePipe(helper, new BlockPos(x, 1, 3), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        }
        int interval = startExtraction(helper, new BlockPos(1, 1, 2), Direction.WEST);
        assertSteadyBatches(helper, source, target, new BlockPos(1, 1, 2), 8, 2 * BASIC_RATE * interval);
    }

    /** 测试 285:3×3 满网格中点对中点(对应截图 4)——入口紫点三条绿邻路,稳态 3× 绿管速率。 */
    private static void pipeThreeColumnGridTriplesThroughput(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        fillChest(source, Items.COAL, SOURCE_FILL);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (z == 2 && (x == 1 || x == 3)) {
                    continue;
                }
                placePipe(helper, new BlockPos(x, 1, z), BuiltinTopoPipes.ITEM_PIPE_BASIC);
            }
        }
        int interval = startExtraction(helper, new BlockPos(1, 1, 2), Direction.WEST);
        assertSteadyBatches(helper, source, target, new BlockPos(1, 1, 2), 9, 3 * BASIC_RATE * interval);
    }

    // --- shared helpers (mirrors PipeNetworkGameTests conventions) ------------------------------

    /** 设抽取口并钳到最小聚合档;返回 interval 供批量推算。 */
    private static int startExtraction(GameTestHelper helper, BlockPos pipePos, Direction side) {
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(pipePos);
        runtime.setSideIntent(extractor, side, PipeSideIntent.EXTRACT);
        int min = runtime.portWindow(extractor, side).minInterval();
        runtime.setExtractConfig(extractor, side,
                runtime.portConfig(extractor, side).withInterval(min));
        return min;
    }

    /**
     * 统一断言:成网节点数 → 首批整批恰为 {@code expectedBatch} → 次批等量(稳态)且守恒。
     * 批后 interval(≥5t)内序列步进 1t,断言不会跨到下一批。
     */
    private static void assertSteadyBatches(GameTestHelper helper, ChestBlockEntity source,
                                            ChestBlockEntity target, BlockPos anyPipe, int expectedNodes, int expectedBatch) {
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    PipeNetwork<?> network = PipeNetworkEngine.networkAt(
                            helper.getLevel(), helper.absolutePos(anyPipe));
                    if (network == null || network.nodeCount() != expectedNodes) {
                        helper.fail("Topology must flood into one component of " + expectedNodes + " nodes, got " + (network == null ? "none" : network.nodeCount()));
                    }
                })
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) <= 0) {
                        helper.fail("waiting for the first batch");
                    }
                })
                .thenExecute(() -> {
                    int moved = countItems(target, Items.COAL);
                    if (moved != expectedBatch) {
                        helper.fail("First batch must equal the min-cut capacity " + expectedBatch + ", moved " + moved);
                    }
                })
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) < expectedBatch * 2) {
                        helper.fail("waiting for the second batch");
                    }
                })
                .thenExecute(() -> {
                    int moved = countItems(target, Items.COAL);
                    int left = countItems(source, Items.COAL);
                    if (moved != expectedBatch * 2) {
                        helper.fail("Second batch must repeat the min-cut capacity, total " + moved + " expected " + expectedBatch * 2);
                    }
                    if (moved + left != SOURCE_FILL) {
                        helper.fail("Conservation violated: " + moved + " moved + " + left + " left != " + SOURCE_FILL);
                    }
                })
                .thenSucceed();
    }

    private static PipeLevelRuntime runtime(GameTestHelper helper) {
        return PipeNetworkEngine.runtime(helper.getLevel());
    }

    private static void placeFloor(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void placePipe(GameTestHelper helper, BlockPos pos, PipeDefinition definition) {
        helper.setBlock(pos, definition.registeredBlock().get().defaultBlockState());
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ChestBlockEntity chestEntity)) {
            throw new IllegalStateException("Setup: expected a chest at " + pos);
        }
        return chestEntity;
    }

    private static void fillChest(ChestBlockEntity chestEntity, Item item, int count) {
        int slot = 0;
        int remaining = count;
        while (remaining > 0 && slot < chestEntity.getContainerSize()) {
            int put = Math.min(64, remaining);
            chestEntity.setItem(slot++, new ItemStack(item, put));
            remaining -= put;
        }
    }

    private static int countItems(ChestBlockEntity chestEntity, Item item) {
        int total = 0;
        for (int slot = 0; slot < chestEntity.getContainerSize(); slot++) {
            ItemStack stack = chestEntity.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Pipe topology rate GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 400, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
