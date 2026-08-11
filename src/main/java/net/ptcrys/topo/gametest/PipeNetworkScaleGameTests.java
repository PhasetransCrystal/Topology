package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.data.pipe.BuiltinOIPipes;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 管道网络规模/性能游戏内测试(全局编号 286-288):跨区块密铺大网格的速率正确性与每 tick
 * 引擎成本上限、多网络并行的正确性与总成本、无端口密铺零成本与增量唤醒。
 *
 * <p>
 * 场地约定:规模场景超出测试网格单元,统一搭在远场绝对坐标(测试格原点 + 4096 格
 * 对角偏移,三条测试各占独立 z 槽位互不相撞),搭建前强载区块、收尾时禁用抽取口并解除
 * 强载,不给共跑与后续测试留下背景成本。成本探针读每网络的
 * {@link PipeNetwork#lastBatchNanos()}(该网络上一 tick 的引擎整段耗时,含空闲门控)。
 * 该读数是墙钟,gametest 并行批次共跑会偷 CPU 抬高它——故活跃成本取 p25 窗口"地板"
 * (剔除污染窗口、还原引擎计算量),规模无关性靠大/小网格同分位之比(共跑噪声抵消)。
 * 真实量级另行落日志(`[pipe-scale-28x]` 行)供人工对照。
 */
public final class PipeNetworkScaleGameTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeNetworkScaleGameTests.class);
    private static final String SUITE = "pipe_network_scale";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 3;
    private static final int SOURCE_FILL = 27 * 64;
    /** 远场槽位间距(格):三条规模测试的场地彼此隔开,亦远离测试网格。 */
    private static final int FAR_OFFSET = 4096;
    private static final int FAR_SLOT_SPACING = 256;

    private PipeNetworkScaleGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!OIScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("pipe_network_scale"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(event, environment, index++,
                "pipe_scale_dense_lattice_crosses_chunks",
                "Tests a 48x48 dense basic lattice spanning multiple chunks: corner-to-corner " + "throughput must hold the min-cut rate over a long window, and the " + "per-tick engine cost must stay bounded both active and idle.",
                PipeNetworkScaleGameTests::pipeScaleDenseLatticeCrossesChunks);
        register(event, environment, index++,
                "pipe_scale_many_parallel_lines",
                "Tests 32 independent extraction lines ticking in parallel: every line keeps its " + "own full batch cadence (no cross-network interference) and the " + "per-network engine cost stays bounded.",
                PipeNetworkScaleGameTests::pipeScaleManyParallelLines);
        register(event, environment, index,
                "pipe_scale_portless_mesh_idle_then_wakes",
                "Tests that a 24x24 port-less mesh never enters the engine tick at all, and that " + "attaching an extraction port afterwards wakes the same component " + "in place and delivers a correct first batch.",
                PipeNetworkScaleGameTests::pipeScalePortlessMeshIdleThenWakes);
    }

    /**
     * 测试 286:规模无关性主测——同口径的 8×8(64 节点)与 48×48(2304 节点,横跨 ≥4×4
     * 区块)密铺基础网格、终极紫角对角搬运依次实测。两者角点最小割 = 2 → 稳态 16/t;
     * 每相 80t 逐 tick 采样,按聚合周期折成窗口取中位(抗 GC/JIT 离群)。硬指标:大网格
     * 活跃中位 ≤15µs/t、排空后空闲中位 ≤2µs/t、且大网格 ≤ 小网格×2.5 + 5µs——批稳态
     * 成本只允许随路径长度/事务走,不许随节点总数走(36 倍节点数不得换来超 2.5 倍耗时)。
     */
    private static void pipeScaleDenseLatticeCrossesChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = farBase(helper, 0);
        int largeGrid = 48;
        int smallGrid = 8;
        BlockPos smallBase = base.offset(0, 0, largeGrid + 16);
        forceChunks(level, base.offset(-17, 0, -17),
                base.offset(largeGrid + 16, 0, largeGrid + 16 + smallGrid + 16), true);
        ChestBlockEntity largeSource = chestAbs(level, base.offset(-1, 0, 0));
        ChestBlockEntity largeTarget = chestAbs(level, base.offset(largeGrid, 0, largeGrid - 1));
        ChestBlockEntity smallSource = chestAbs(level, smallBase.offset(-1, 0, 0));
        ChestBlockEntity smallTarget = chestAbs(level, smallBase.offset(smallGrid, 0, smallGrid - 1));
        fillChest(largeSource, Items.COAL, SOURCE_FILL);
        fillChest(smallSource, Items.COAL, SOURCE_FILL);
        placeCornerLattice(level, base, largeGrid);
        placeCornerLattice(level, smallBase, smallGrid);
        // 先测小网格,期间大网格端口不开(纯密铺零参与),两相同 JIT 状态可比。
        int interval = startExtractionAbs(level, smallBase, Direction.WEST);
        int ratePerTick = 2 * 8; // 入口紫角两条绿邻路:min-cut 2 × 基础吞吐 8/t。
        // 活跃成本取 p25 窗口(成本"地板"):gametest 并行批次会偷 CPU 抬高 lastBatchNanos 的
        // 墙钟读数(中位数仍被几个污染窗口拉偏),低分位剔除共跑尖峰、还原引擎本身的计算量。
        // 窗口 80t 而非更长:单箱满载 1728 煤,16/t × 80t = 1280 < 1728,整窗不断供(否则
        // 源排空后引擎转空闲,p25 地板被拉低成假快)。
        int window = 80;
        int idleWindow = 40;
        PipeNetwork<?>[] nets = new PipeNetwork<?>[2]; // {small, large}
        long[] baseline = new long[1];
        long[] smallSeries = new long[window];
        long[] largeSeries = new long[window];
        long[] idleSeries = new long[idleWindow];
        long[] smallFloor = new long[1];

        GameTestSequence sequence = helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    nets[0] = PipeNetworkEngine.networkAt(level, smallBase);
                    nets[1] = PipeNetworkEngine.networkAt(level, base);
                    if (nets[0] == null || nets[0].nodeCount() != smallGrid * smallGrid) {
                        helper.fail("Small lattice must flood into " + smallGrid * smallGrid + " nodes, got " + (nets[0] == null ? "none" : nets[0].nodeCount()));
                        return;
                    }
                    if (nets[1] == null || nets[1].nodeCount() != largeGrid * largeGrid) {
                        helper.fail("Large lattice must flood into " + largeGrid * largeGrid + " nodes, got " + (nets[1] == null ? "none" : nets[1].nodeCount()));
                    }
                })
                .thenWaitUntil(() -> {
                    if (countItems(smallTarget, Items.COAL) <= 0) {
                        helper.fail("waiting for the small lattice's first batch");
                    }
                })
                .thenExecute(() -> baseline[0] = countItems(smallTarget, Items.COAL));
        for (int i = 0; i < window; i++) {
            int tick = i;
            sequence = sequence.thenExecuteAfter(1, () -> smallSeries[tick] = nets[0].lastBatchNanos());
        }
        sequence = sequence.thenExecute(() -> {
            assertMinCutRate(helper, "small", countItems(smallTarget, Items.COAL) - baseline[0],
                    ratePerTick, window, interval);
            smallFloor[0] = percentileWindowPerTickNanos(smallSeries, interval, 25);
            LOGGER.info("[pipe-scale-286] small {}x{} active {}t: p25={}ns/t max={}ns",
                    smallGrid, smallGrid, window, smallFloor[0], maxOf(smallSeries));
            // 小网格退场:关口排空,让大网格相独享读数。
            runtime(helper).setSideIntent(smallBase, Direction.WEST, PipeSideIntent.DISABLED);
            smallSource.clearContent();
            startExtractionAbs(level, base, Direction.WEST);
        });
        sequence = sequence.thenWaitUntil(() -> {
            if (countItems(largeTarget, Items.COAL) <= 0) {
                helper.fail("waiting for the large lattice's first batch");
            }
        }).thenExecute(() -> baseline[0] = countItems(largeTarget, Items.COAL));
        for (int i = 0; i < window; i++) {
            int tick = i;
            sequence = sequence.thenExecuteAfter(1, () -> largeSeries[tick] = nets[1].lastBatchNanos());
        }
        sequence = sequence.thenExecute(() -> {
            assertMinCutRate(helper, "large", countItems(largeTarget, Items.COAL) - baseline[0],
                    ratePerTick, window, interval);
            largeSource.clearContent();
        });
        for (int i = 0; i < idleWindow; i++) {
            int tick = i;
            sequence = sequence.thenExecuteAfter(1, () -> idleSeries[tick] = nets[1].lastBatchNanos());
        }
        sequence.thenExecute(() -> {
            long largeFloor = percentileWindowPerTickNanos(largeSeries, interval, 25);
            long idleMedian = medianWindowPerTickNanos(idleSeries, interval);
            LOGGER.info("[pipe-scale-286] large {}x{} active {}t: p25={}ns/t max={}ns" + " | idle {}t: median={}ns/t max={}ns | small p25={}ns/t",
                    largeGrid, largeGrid, window, largeFloor, maxOf(largeSeries),
                    idleWindow, idleMedian, maxOf(idleSeries), smallFloor[0]);
            if (largeFloor > 15_000) {
                helper.fail("Large lattice active cost: p25 floor " + largeFloor + "ns/t exceeds the 15µs budget");
            }
            if (idleMedian > 2_000) {
                helper.fail("Drained lattice idle cost: median " + idleMedian + "ns/t exceeds the 2µs budget");
            }
            if (largeFloor > smallFloor[0] * 5 / 2 + 5_000) {
                helper.fail("Engine cost scales with node count: large p25 " + largeFloor + "ns/t vs small p25 " + smallFloor[0] + "ns/t breaks the x2.5+5µs scale-independence bound");
            }
            runtime(helper).setSideIntent(base, Direction.WEST, PipeSideIntent.DISABLED);
            forceChunks(level, base.offset(-17, 0, -17),
                    base.offset(largeGrid + 16, 0, largeGrid + 16 + smallGrid + 16), false);
        })
                .thenSucceed();
    }

    /** 角对角紫端网格:四角中 (0,0)/(max,max) 放终极管,其余基础管。 */
    private static void placeCornerLattice(ServerLevel level, BlockPos base, int grid) {
        for (int x = 0; x < grid; x++) {
            for (int z = 0; z < grid; z++) {
                boolean corner = (x == 0 && z == 0) || (x == grid - 1 && z == grid - 1);
                placePipeAbs(level, base.offset(x, 0, z),
                        corner ? BuiltinOIPipes.ITEM_PIPE_ELITE : BuiltinOIPipes.ITEM_PIPE_BASIC);
            }
        }
    }

    /** 角对角最小割 2 的窗口到货断言:rate×window ± 一批相位余量。 */
    private static void assertMinCutRate(GameTestHelper helper, String label, long delta,
                                         int ratePerTick, int window, int interval) {
        long expected = (long) ratePerTick * window;
        long slack = (long) ratePerTick * interval;
        if (delta < expected - slack || delta > expected + slack) {
            helper.fail("The " + label + " lattice must sustain the min-cut rate " + ratePerTick + "/t: moved " + delta + " in " + window + "t, expected " + expected + "±" + slack);
        }
    }

    /**
     * 测试 287:32 条独立抽取线并行(各自成网)。共同基线后 60t 窗口,每条线保持
     * 8/t 整批节奏(12 批 ±1 批相位余量);抽样 4 条线的引擎耗时按周期窗口取中位,
     * 硬指标 ≤10µs/线/t——多网络规模只允许随活跃端口数线性走。
     */
    private static void pipeScaleManyParallelLines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = farBase(helper, 1);
        int lines = 32;
        forceChunks(level, base.offset(-17, 0, -17), base.offset(20, 0, lines * 2 + 16), true);
        ChestBlockEntity[] sources = new ChestBlockEntity[lines];
        ChestBlockEntity[] targets = new ChestBlockEntity[lines];
        for (int i = 0; i < lines; i++) {
            BlockPos row = base.offset(0, 0, i * 2);
            sources[i] = chestAbs(level, row.offset(-1, 0, 0));
            targets[i] = chestAbs(level, row.offset(3, 0, 0));
            fillChest(sources[i], Items.COAL, SOURCE_FILL);
            placePipeAbs(level, row, BuiltinOIPipes.ITEM_PIPE_ELITE);
            placePipeAbs(level, row.offset(1, 0, 0), BuiltinOIPipes.ITEM_PIPE_BASIC);
            placePipeAbs(level, row.offset(2, 0, 0), BuiltinOIPipes.ITEM_PIPE_BASIC);
        }
        int lastInterval = 0;
        for (int i = 0; i < lines; i++) {
            lastInterval = startExtractionAbs(level, base.offset(0, 0, i * 2), Direction.WEST);
        }
        int interval = lastInterval;
        int batch = 8 * interval; // 基础段 8/t 封顶的整批量。
        int window = 60;
        int expectedBatches = window / interval;
        int[] reps = { 0, 10, 21, lines - 1 };
        PipeNetwork<?>[] repNets = new PipeNetwork<?>[reps.length];
        long[] baseline = new long[lines];
        long[] repSeries = new long[window]; // 每 tick 的 4 条抽样线引擎耗时合计

        GameTestSequence sequence = helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (int r = 0; r < reps.length; r++) {
                        PipeNetwork<?> network = PipeNetworkEngine.networkAt(
                                level, base.offset(1, 0, reps[r] * 2));
                        if (network == null || network.nodeCount() != 3) {
                            helper.fail("Line " + reps[r] + " must flood into its own 3-node " + "component, got " + (network == null ? "none" : network.nodeCount()));
                            return;
                        }
                        repNets[r] = network;
                    }
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < lines; i++) {
                        if (countItems(targets[i], Items.COAL) <= 0) {
                            helper.fail("waiting for the first batch on line " + i);
                            return;
                        }
                    }
                })
                .thenExecute(() -> {
                    for (int i = 0; i < lines; i++) {
                        baseline[i] = countItems(targets[i], Items.COAL);
                    }
                });
        for (int t = 0; t < window; t++) {
            int tick = t;
            sequence = sequence.thenExecuteAfter(1, () -> {
                long sum = 0;
                for (PipeNetwork<?> network : repNets) {
                    sum += network.lastBatchNanos();
                }
                repSeries[tick] = sum;
            });
        }
        sequence.thenExecute(() -> {
            long floorPerLine = percentileWindowPerTickNanos(repSeries, interval, 25) / reps.length;
            LOGGER.info("[pipe-scale-287] {} lines, window {}t: p25={}ns/line/t " + "max(4-line tick)={}ns", lines, window, floorPerLine, maxOf(repSeries));
            for (int i = 0; i < lines; i++) {
                long delta = countItems(targets[i], Items.COAL) - baseline[i];
                long min = (long) batch * (expectedBatches - 1);
                long max = (long) batch * (expectedBatches + 1);
                if (delta < min || delta > max) {
                    helper.fail("Line " + i + " lost its cadence: moved " + delta + " in " + window + "t, expected " + batch + "x" + expectedBatches + "±1 batches");
                    return;
                }
            }
            if (floorPerLine > 10_000) {
                helper.fail("Per-line engine cost: p25 floor " + floorPerLine + "ns/t exceeds the 10µs budget");
            }
            for (int i = 0; i < lines; i++) {
                runtime(helper).setSideIntent(base.offset(0, 0, i * 2), Direction.WEST,
                        PipeSideIntent.DISABLED);
            }
            forceChunks(level, base.offset(-17, 0, -17), base.offset(20, 0, lines * 2 + 16), false);
        })
                .thenSucceed();
    }

    /**
     * 测试 288:24×24 无端口密铺(576 节点)整窗不进引擎 tick(tickStamp 不动、零耗时),
     * 随后补上箱子与抽取口,同一组件原地唤醒,首批恰为基础抽取率整批(1/t × 40t = 40)。
     */
    private static void pipeScalePortlessMeshIdleThenWakes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = farBase(helper, 2);
        int grid = 24;
        forceChunks(level, base.offset(-17, 0, -17), base.offset(grid + 16, 0, grid + 16), true);
        for (int x = 0; x < grid; x++) {
            for (int z = 0; z < grid; z++) {
                placePipeAbs(level, base.offset(x, 0, z), BuiltinOIPipes.ITEM_PIPE_BASIC);
            }
        }
        PipeNetwork<?>[] netRef = new PipeNetwork<?>[1];
        ChestBlockEntity[] rig = new ChestBlockEntity[2];

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    PipeNetwork<?> network = PipeNetworkEngine.networkAt(level, base);
                    if (network == null || network.nodeCount() != grid * grid) {
                        helper.fail("Mesh must flood into one component of " + grid * grid + " nodes, got " + (network == null ? "none" : network.nodeCount()));
                        return;
                    }
                    if (network.extractorCount() != 0) {
                        helper.fail("Port-less mesh must have no extraction ports");
                        return;
                    }
                    netRef[0] = network;
                })
                .thenExecuteAfter(30, () -> {
                    if (netRef[0].tickStamp() != Long.MIN_VALUE || netRef[0].lastBatchNanos() != 0) {
                        helper.fail("Port-less mesh must never enter the engine tick: stamp=" + netRef[0].tickStamp() + " nanos=" + netRef[0].lastBatchNanos());
                        return;
                    }
                    rig[0] = chestAbs(level, base.offset(-1, 0, 0));
                    rig[1] = chestAbs(level, base.offset(grid, 0, grid - 1));
                    fillChest(rig[0], Items.COAL, SOURCE_FILL);
                    startExtractionAbs(level, base, Direction.WEST);
                })
                .thenWaitUntil(() -> {
                    if (countItems(rig[1], Items.COAL) <= 0) {
                        helper.fail("waiting for the woken mesh to deliver");
                    }
                })
                .thenExecute(() -> {
                    int moved = countItems(rig[1], Items.COAL);
                    if (moved != 40) {
                        helper.fail("Woken basic port must deliver one extract-rate batch " + "(1/t x 40t), moved " + moved);
                        return;
                    }
                    if (netRef[0].tickStamp() == Long.MIN_VALUE) {
                        helper.fail("The same component must wake in place (tickStamp must advance)");
                        return;
                    }
                    runtime(helper).setSideIntent(base, Direction.WEST, PipeSideIntent.DISABLED);
                    forceChunks(level, base.offset(-17, 0, -17), base.offset(grid + 16, 0, grid + 16), false);
                })
                .thenSucceed();
    }

    // --- shared helpers (far-field variants of PipeNetworkGameTests conventions) ---------------

    /** 远场基准:测试格原点对角偏移 4096,三条规模测试各占一个 z 槽位;y 抬 8 格悬空铺设。 */
    private static BlockPos farBase(GameTestHelper helper, int slot) {
        return helper.absolutePos(BlockPos.ZERO).offset(FAR_OFFSET, 8, FAR_OFFSET + slot * FAR_SLOT_SPACING);
    }

    private static void forceChunks(ServerLevel level, BlockPos from, BlockPos to, boolean add) {
        for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
            for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
                level.setChunkForced(cx, cz, add);
            }
        }
    }

    /**
     * 逐 tick 采样按聚合周期折成窗口和,取中位窗口的均摊每 tick 纳秒——批 tick 的真实
     * 成本保留在每个窗口里,GC/JIT 离群窗口被中位数剔除,可设紧围栏。
     */
    private static long medianWindowPerTickNanos(long[] samples, int interval) {
        return percentileWindowPerTickNanos(samples, interval, 50);
    }

    /**
     * 逐 tick 采样按聚合周期折成窗口和,取 {@code percentile} 分位窗口的均摊每 tick 纳秒。
     * 低分位(如 25)给出成本"地板"——剔除 gametest 并行批次共跑偷 CPU 抬高的污染窗口,
     * 还原引擎自身的计算量;比中位数抗墙钟噪声,稳态规模围栏据此设。
     */
    private static long percentileWindowPerTickNanos(long[] samples, int interval, int percentile) {
        int windows = Math.max(1, samples.length / interval);
        long[] sums = new long[windows];
        for (int w = 0; w < windows; w++) {
            long sum = 0;
            for (int k = 0; k < interval && w * interval + k < samples.length; k++) {
                sum += samples[w * interval + k];
            }
            sums[w] = sum;
        }
        java.util.Arrays.sort(sums);
        int index = Math.min(windows - 1, Math.max(0, windows * percentile / 100));
        return sums[index] / interval;
    }

    private static long maxOf(long[] samples) {
        long max = 0;
        for (long sample : samples) {
            max = Math.max(max, sample);
        }
        return max;
    }

    /** 设抽取口(绝对坐标)并钳到最小聚合档;返回 interval。 */
    private static int startExtractionAbs(ServerLevel level, BlockPos extractor, Direction side) {
        PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
        runtime.setSideIntent(extractor, side, PipeSideIntent.EXTRACT);
        int min = runtime.portWindow(extractor, side).minInterval();
        runtime.setExtractConfig(extractor, side,
                runtime.portConfig(extractor, side).withInterval(min));
        return min;
    }

    private static PipeLevelRuntime runtime(GameTestHelper helper) {
        return PipeNetworkEngine.runtime(helper.getLevel());
    }

    private static void placePipeAbs(ServerLevel level, BlockPos pos, PipeDefinition definition) {
        level.setBlockAndUpdate(pos, definition.registeredBlock().get().defaultBlockState());
    }

    private static ChestBlockEntity chestAbs(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(pos);
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
            throw new IllegalArgumentException("Pipe network scale GameTest index out of range: " + index);
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
