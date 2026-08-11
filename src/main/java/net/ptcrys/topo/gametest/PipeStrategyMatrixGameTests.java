package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipeDistributionStrategies;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToLongBiFunction;

/**
 * 策略×资源全矩阵逐批次行为测试(全局编号 171-185):3 个分配策略 × 5 种资源各一条,统一 T 形
 * 拓扑(源容器 - 精英抽取管 - 汇点 - 南北双分支 - 双目标),抽取速率经端口配置钳到 12/t、聚合
 * 间隔钳到该策略 offer 的最小档,逐 tick 采样两目标容器的绝对量,从增量
 * 序列还原批次:
 *
 * <ul>
 * <li>批次只发生在间隔整数倍的 tick 上(相位由坐标散列决定,测试按观测对齐);</li>
 * <li>每批总量 = rate × interval,守恒(目标增量之和 == 源减量);</li>
 * <li>按距离(最近):全部进近分支;平均分配:两分支各半;轮询:整批逐批交替。</li>
 * </ul>
 */
public final class PipeStrategyMatrixGameTests {

    private static final String SUITE = "pipe_strategy_matrix";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 15;

    private static final int RATE = 12;
    /** 采样窗口按"最大相位 + 3 个完整批次 + 余量"取,保证捕获 ≥3 个批次。 */
    private static final int REQUIRED_BATCHES = 3;

    private static final BlockPos SOURCE = new BlockPos(1, 1, 2);
    private static final BlockPos EXTRACTOR = new BlockPos(2, 1, 2);
    private static final BlockPos BRANCH_NEAR = new BlockPos(2, 1, 1);
    private static final BlockPos BRANCH_FAR = new BlockPos(2, 1, 3);
    private static final BlockPos TARGET_NEAR = new BlockPos(2, 1, 0);
    private static final BlockPos TARGET_FAR = new BlockPos(2, 1, 4);

    private PipeStrategyMatrixGameTests() {}

    /** 一种资源的测试装备:终极管 + 源/目标容器的摆放、预载与计数。 */
    private record Rig(String key, PipeDefinition pipe,
                       BiConsumer<GameTestHelper, BlockPos> placeSource,
                       BiConsumer<GameTestHelper, BlockPos> placeTarget,
                       ToLongBiFunction<GameTestHelper, BlockPos> count) {}

    private static Rig itemRig() {
        return new Rig("item", BuiltinTopoPipes.ITEM_PIPE_ELITE,
                (helper, pos) -> {
                    ChestBlockEntity chest = chest(helper, pos);
                    // 相位可能让首批早于基线采样,窗口里最多吃 5 个批次——给足供给。
                    for (int slot = 0; slot < 27; slot++) {
                        chest.setItem(slot, new ItemStack(Items.COAL, 64));
                    }
                },
                PipeStrategyMatrixGameTests::chest,
                (helper, pos) -> {
                    if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ChestBlockEntity chest)) {
                        return 0L;
                    }
                    long total = 0;
                    for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                        total += chest.getItem(slot).getCount();
                    }
                    return total;
                });
    }

    private static Rig fluidRig() {
        return new Rig("fluid", BuiltinTopoPipes.FLUID_PIPE_ELITE,
                (helper, pos) -> {
                    MachineBlockEntity tank = placeMachine(helper, pos, TopoPipeGameTestFixtures.fluidTank());
                    insertResource(helper, tank.machineComponents().require(FluidResourcePort.FLUID_STORAGE).handler(),
                            FluidResource.of(Fluids.WATER), 50_000, "mB water");
                },
                (helper, pos) -> placeMachine(helper, pos, TopoPipeGameTestFixtures.fluidTank()),
                (helper, pos) -> handlerTotal(helper, pos, FluidResourcePort.FLUID_STORAGE));
    }

    private static Rig scalarRig(String key, PipeDefinition pipe, MachineDefinition buffer,
                                 ComponentKey<ScalarResourcePort> traitKey,
                                 BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration) {
        return new Rig(key, pipe,
                (helper, pos) -> {
                    MachineBlockEntity machine = placeMachine(helper, pos, buffer);
                    insertResource(helper, machine.machineComponents().require(traitKey).handler(),
                            integration.recipeCapability().resource(), TopoPipeGameTestFixtures.SCALAR_CAPACITY / 2,
                            integration.id().toString());
                },
                (helper, pos) -> placeMachine(helper, pos, buffer),
                (helper, pos) -> {
                    MachineBlockEntity machine = helper.getBlockEntity(pos, MachineBlockEntity.class);
                    return sumHandler(machine.machineComponents().require(traitKey).handler());
                });
    }

    private static long handlerTotal(GameTestHelper helper, BlockPos pos, ComponentKey<FluidResourcePort> key) {
        MachineBlockEntity machine = helper.getBlockEntity(pos, MachineBlockEntity.class);
        return sumHandler(machine.machineComponents().require(key).handler());
    }

    private static long sumHandler(ResourceHandler<?> handler) {
        long total = 0;
        for (int index = 0; index < handler.size(); index++) {
            total += handler.getAmountAsLong(index);
        }
        return total;
    }

    private static Rig energyRig() {
        return scalarRig("energy", BuiltinTopoPipes.ENERGY_PIPE_ELITE, TopoPipeGameTestFixtures.energyBuffer(),
                ScalarResourcePort.ENERGY_STORAGE, BuiltinTopoResourceIntegrations.ENERGY);
    }

    private static Rig advancedEnergyRig() {
        return scalarRig("advanced_energy", BuiltinTopoPipes.ADVANCED_ENERGY_PIPE_ELITE,
                TopoPipeGameTestFixtures.advancedEnergyBuffer(),
                ScalarResourcePort.ADVANCED_ENERGY_STORAGE, BuiltinTopoResourceIntegrations.ADVANCED_ENERGY);
    }

    private static Rig heatRig() {
        return scalarRig("heat", BuiltinTopoPipes.HEAT_PIPE_ELITE, TopoPipeGameTestFixtures.heatBuffer(),
                ScalarResourcePort.HEAT_STORAGE, BuiltinTopoResourceIntegrations.HEAT);
    }

    public static void register(RegisterGameTestsEvent event) {
        if (!TopoScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("pipe_strategy_matrix"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        // 测试 171-185:每资源一组,组内 按距离(最近)/平均分配/轮询 三条。
        for (Rig rig : new Rig[] { itemRig(), fluidRig(), energyRig(), advancedEnergyRig(), heatRig() }) {
            register(event, environment, index++,
                    "pipe_matrix_" + rig.key() + "_by_distance_nearest",
                    "Per-batch container probe, " + rig.key() + " x by-distance(nearest): every batch lands " + "fully on the near branch and the far branch stays at zero.",
                    helper -> runScenario(helper, rig, BuiltinTopoPipeDistributionStrategies.BY_DISTANCE,
                            Expectation.constant(1, 0)));
            register(event, environment, index++,
                    "pipe_matrix_" + rig.key() + "_equal_split",
                    "Per-batch container probe, " + rig.key() + " x equal-split: every batch splits exactly in " + "half between the branches.",
                    helper -> runScenario(helper, rig, BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT,
                            Expectation.halves()));
            register(event, environment, index++,
                    "pipe_matrix_" + rig.key() + "_round_robin",
                    "Per-batch container probe, " + rig.key() + " x round-robin: whole batches alternate " + "between the two branches.",
                    helper -> runScenario(helper, rig, BuiltinTopoPipeDistributionStrategies.ROUND_ROBIN,
                            Expectation.alternatingBatches()));
        }
    }

    // --- scenario runner -----------------------------------------------------------------------

    /** 每批期望:near/far 的份额比(分子),alternating 表示整批逐批交替。 */
    private record Expectation(boolean alternating, int nearShare, int farShare) {

        static Expectation constant(int nearShare, int farShare) {
            return new Expectation(false, nearShare, farShare);
        }

        static Expectation halves() {
            return new Expectation(false, 1, 1);
        }

        static Expectation alternatingBatches() {
            return new Expectation(true, 0, 0);
        }
    }

    private static void runScenario(
                                    GameTestHelper helper, Rig rig, PipeDistributionStrategy strategy, Expectation expectation) {
        placeFloor(helper);
        rig.placeSource().accept(helper, SOURCE);
        rig.placeTarget().accept(helper, TARGET_NEAR);
        rig.placeTarget().accept(helper, TARGET_FAR);
        placePipe(helper, EXTRACTOR, rig.pipe());
        placePipe(helper, BRANCH_NEAR, rig.pipe());
        placePipe(helper, BRANCH_FAR, rig.pipe());

        PipeLevelRuntime runtime = PipeNetworkEngine.runtime(helper.getLevel());
        BlockPos extractor = helper.absolutePos(EXTRACTOR);
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setExtractStrategy(extractor, Direction.WEST, strategy);
        int interval = runtime.portWindow(extractor, Direction.WEST).minInterval();
        // 每周期量 = RATE x interval:批次断言(总量/份额/节奏)与旧每 tick 口径数值完全一致。
        int amount = RATE * interval;
        runtime.setExtractConfig(extractor, Direction.WEST,
                runtime.portConfig(extractor, Direction.WEST).withInterval(interval).withAmount(amount));

        int sampleCount = interval * (REQUIRED_BATCHES + 1) + 6;
        long[] nearSeries = new long[sampleCount];
        long[] farSeries = new long[sampleCount];
        GameTestSequence sequence = helper.startSequence().thenExecuteAfter(1, () -> {
            nearSeries[0] = rig.count().applyAsLong(helper, TARGET_NEAR);
            farSeries[0] = rig.count().applyAsLong(helper, TARGET_FAR);
        });
        for (int i = 1; i < sampleCount; i++) {
            int tick = i;
            sequence = sequence.thenExecuteAfter(1, () -> {
                nearSeries[tick] = rig.count().applyAsLong(helper, TARGET_NEAR);
                farSeries[tick] = rig.count().applyAsLong(helper, TARGET_FAR);
            });
        }
        sequence.thenExecute(() -> assertBatches(helper, rig, nearSeries, farSeries, interval, expectation))
                .thenSucceed();
    }

    /** 从绝对量序列还原批次增量并断言节奏、总量与分配比。 */
    private static void assertBatches(
                                      GameTestHelper helper, Rig rig, long[] near, long[] far, int interval, Expectation expectation) {
        long batch = (long) RATE * interval;
        record Batch(int tick, long near, long far) {}
        List<Batch> batches = new ArrayList<>();
        for (int i = 1; i < near.length; i++) {
            long nearDelta = near[i] - near[i - 1];
            long farDelta = far[i] - far[i - 1];
            if (nearDelta != 0 || farDelta != 0) {
                batches.add(new Batch(i, nearDelta, farDelta));
            }
        }
        if (batches.size() < REQUIRED_BATCHES) {
            helper.fail(describe(rig, batches, "expected >= " + REQUIRED_BATCHES + " batches in the window"));
            return;
        }
        for (int b = 0; b < batches.size(); b++) {
            Batch current = batches.get(b);
            long total = current.near() + current.far();
            if (total != batch) {
                helper.fail(describe(rig, batches,
                        "batch " + b + " moved " + total + ", expected " + batch));
                return;
            }
            if (b > 0 && current.tick() - batches.get(b - 1).tick() != interval) {
                helper.fail(describe(rig, batches,
                        "batch spacing must be " + interval + " ticks"));
                return;
            }
            if (expectation.alternating()) {
                boolean nearFull = current.near() == batch && current.far() == 0;
                boolean farFull = current.far() == batch && current.near() == 0;
                if (!nearFull && !farFull) {
                    helper.fail(describe(rig, batches, "round robin must send whole batches to one branch"));
                    return;
                }
                if (b > 0 && batches.get(b - 1).near() == current.near()) {
                    helper.fail(describe(rig, batches, "round robin must alternate branches between batches"));
                    return;
                }
            } else {
                long shares = expectation.nearShare() + expectation.farShare();
                long expectedNear = batch * expectation.nearShare() / shares;
                long expectedFar = batch * expectation.farShare() / shares;
                if (current.near() != expectedNear || current.far() != expectedFar) {
                    helper.fail(describe(rig, batches,
                            "expected per-batch split " + expectedNear + "/" + expectedFar));
                    return;
                }
            }
        }
    }

    private static String describe(Rig rig, List<?> batches, String message) {
        return rig.key() + ": " + message + "; batches = " + batches.stream()
                .map(Object::toString)
                .reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append);
    }

    // --- shared helpers (mirrors PipeNetworkGameTests conventions) ------------------------------

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

    private static MachineBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ChestBlockEntity chestEntity)) {
            throw new IllegalStateException("Setup: expected a chest at " + pos);
        }
        return chestEntity;
    }

    private static <R extends Resource> void insertResource(
                                                            GameTestHelper helper, ResourceHandler<R> handler, R resource, int amount, String what) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, transaction);
            if (inserted != amount) {
                helper.fail("Setup: expected to preload " + amount + " " + what + ", inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Pipe strategy matrix GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 300, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
