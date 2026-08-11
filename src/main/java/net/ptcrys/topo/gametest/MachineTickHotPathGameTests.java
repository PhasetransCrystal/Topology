package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.MachineWorkControl;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.data.MachineDataSyncBatcher;
import net.ptcrys.topo.api.machine.resource.MachineSearchPoolConfig;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.integration.jade.MachineDataProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

/**
 * 机器 tick 热路径行为测试:标量内容落盘节流(区块 unsaved 标记频率)、框架 after-tick
 * 每机器每 tick 只跑一次、标量 tick-IO 直通道的资格判定/全或无/多口分配/非标量回退,
 * 性能探针 self 行与滚动均值的真实上报,以及停泊机器的 tick 退避与事件唤醒。
 */
public final class MachineTickHotPathGameTests {

    private static final String SUITE = "machine_tick_hot_path";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 17;
    /** 退避爬升最坏路径(1→2→4→8→16→20,含相位等待)约 31t;留余量到 60t。 */
    private static final int BACKOFF_RAMP_MARGIN_TICKS = 60;
    /** 事件唤醒裕度:gametest 回调与 TickHub 在同一服务器 tick 内的先后顺序无保证。 */
    private static final int WAKE_MARGIN_TICKS = 3;

    /** 标量内容持久化周期;节流窗口必须整体落在一个周期内。 */
                                     /**
                                      * 并行 GameTest 可能与本测试共享区块并随时把 unsaved 标记打上(噪声只会"误标记",
                                      * 永远不会"误清洁")。节流回归时机器自己每 tick 都标记,clean tick 必然为 0;因此
                                      * 断言"窗口内至少 1 个 clean tick"对回归是确定性的,同时对邻居噪声最大限度鲁棒。
                                      */

    private MachineTickHotPathGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!TopoScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("machine_tick_hot_path"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "scalar_persist_epoch_coalesces_until_real_chunk_save",
                "Tests the persistence epoch coordinator: after the first scalar mutation marks the chunk, " + "repeated writes must not re-mark it until a real ChunkDataEvent.Save acknowledges the epoch.",
                MachineTickHotPathGameTests::scalarPersistEpochCoalescesUntilRealChunkSave);
        register(
                event,
                environment,
                index++,
                "scalar_persist_epoch_rearms_after_real_chunk_save",
                "Tests the persistence epoch coordinator: a real chunk snapshot acknowledgement must re-arm the " + "domain so the next persisted scalar mutation marks a new epoch.",
                MachineTickHotPathGameTests::scalarPersistEpochRearmsAfterRealChunkSave);
        register(
                event,
                environment,
                index++,
                "item_contents_change_marks_chunk_unsaved_same_tick",
                "Tests ItemResourcePort contents persist policy stays immediate: an item slot change marks the " + "chunk unsaved by the end of the same server tick.",
                MachineTickHotPathGameTests::itemContentsChangeMarksChunkUnsavedSameTick);
        register(
                event,
                environment,
                index++,
                "framework_after_tick_recomputes_computed_fields_once_per_tick",
                "Tests MachineBlockEntity.afterTickerTick and MachineDataDomain.lifecycleRecompute: a machine " + "with two sync tickers recomputes its computed fields exactly once per server tick.",
                MachineTickHotPathGameTests::frameworkAfterTickRecomputesComputedFieldsOncePerTick);
        register(
                event,
                environment,
                index++,
                "scalar_tick_recipes_classify_as_direct_lane_eligible",
                "Tests TopoRecipe.hasDirectTickIo eligibility: all-scalar tick entries, including a shared capability, " + "are direct-lane eligible; item tick entries are not.",
                MachineTickHotPathGameTests::scalarTickRecipesClassifyAsDirectLaneEligible);
        register(
                event,
                environment,
                index++,
                "direct_tick_io_blocks_atomically_when_output_full",
                "Tests TopoRecipe.handleTickIo all-or-nothing semantics on the advanced generator: a full advanced " + "energy output blocks the tick without consuming any energy input.",
                MachineTickHotPathGameTests::directTickIoBlocksAtomicallyWhenOutputFull);
        register(
                event,
                environment,
                index++,
                "direct_tick_output_spans_multiple_ports_in_mount_order",
                "Tests scalar tick output distribution across two energy ports: the first port fills to capacity " + "in mount order and the remainder lands in the second port.",
                MachineTickHotPathGameTests::directTickOutputSpansMultiplePortsInMountOrder);
        register(
                event,
                environment,
                index++,
                "item_tick_entries_fall_back_to_transactional_path",
                "Tests TopoRecipe.handleTickIo fallback for non-scalar tick entries: an item tick input recipe " + "still consumes exactly one item and emits its scalar tick output per call.",
                MachineTickHotPathGameTests::itemTickEntriesFallBackToTransactionalPath);
        register(
                event,
                environment,
                index++,
                "monitored_tick_reports_framework_self_nanos",
                "Tests MachineTicker.runProfiledTick and MachinePerformanceSnapshot: while monitored, the " + "framework after-tick cost is reported as a non-zero self sample.",
                MachineTickHotPathGameTests::monitoredTickReportsFrameworkSelfNanos);
        register(
                event,
                environment,
                index++,
                "scalar_residual_dirty_marks_chunk_unsaved_on_level_save",
                "Tests MachineDataSyncBatcher.flushResidualPersistForSave and MachineDataDomain: a save event " + "must mark chunks of machines holding mid-period dirty scalar fields, closing the " + "throttle window for shutdown and post-save sequences.",
                MachineTickHotPathGameTests::scalarResidualDirtyMarksChunkUnsavedOnLevelSave);
        register(
                event,
                environment,
                index++,
                "perf_tree_reports_rolling_average_and_peak",
                "Tests MachineDataProvider perfTree encoding: monitored real ticks publish per-node rolling " + "average and peak nanos alongside the last sample.",
                MachineTickHotPathGameTests::perfTreeReportsRollingAverageAndPeak);
        register(
                event,
                environment,
                index++,
                "idle_blocked_machine_backs_off_tick_interval",
                "Tests RecipeLogic parked-tick backoff: a zero-energy advanced generator parked in " + "WAITING_TICK_INPUT_TO_START must decay its tick interval to the backoff cap instead " + "of polling every tick.",
                MachineTickHotPathGameTests::idleBlockedMachineBacksOffTickInterval);
        register(
                event,
                environment,
                index++,
                "parked_machine_wakes_on_resource_change_within_wake_margin",
                "Tests MachineResourceWake fan-out from noteResourceContentChanged: committing energy into a " + "parked machine must snap its interval back to 1 and start the recipe within the wake margin.",
                MachineTickHotPathGameTests::parkedMachineWakesOnResourceChange);
        register(
                event,
                environment,
                index++,
                "working_machine_keeps_every_tick_interval",
                "Tests parked-tick backoff never affects productive machines: a WORKING advanced generator keeps " + "its sync interval at 1 across the run.",
                MachineTickHotPathGameTests::workingMachineKeepsEveryTickInterval);
        register(
                event,
                environment,
                index++,
                "workmode_toggle_wakes_parked_machine",
                "Tests halt-vs-wake precedence and the toggle wake hook: a halted parked machine must not start " + "when energy arrives, and resuming RUNNING must wake the backed-off ticker within the " + "wake margin.",
                MachineTickHotPathGameTests::workModeToggleWakesParkedMachine);
        register(
                event,
                environment,
                index++,
                "bound_direct_tick_io_rebinds_after_routing_invalidation",
                "Tests the active scalar tick plan fails closed on a routing revision change, rebinds against " + "the rebuilt router, and continues producing with exact progress/output conservation.",
                MachineTickHotPathGameTests::boundDirectTickIoRebindsAfterRoutingInvalidation);
        register(
                event,
                environment,
                index,
                "bound_direct_tick_io_blocks_when_active_pool_disappears",
                "Tests an active recipe never switches pools: removing its bound pool must freeze progress and " + "output until that exact pool returns, then resume with exact conservation.",
                MachineTickHotPathGameTests::boundDirectTickIoBlocksWhenActivePoolDisappears);
    }

    private static void scalarPersistEpochCoalescesUntilRealChunkSave(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());

        helper.startSequence()
                .thenExecuteAfter(92, () -> {
                    simulateRealChunkSnapshot(helper);
                    setScalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1, 10);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk(helper).isUnsaved()) {
                        helper.fail("The first persisted scalar mutation must mark a new chunk epoch");
                    }

                    // Clear only vanilla's flag. Without ChunkDataEvent.Save the coordinator must
                    // keep this domain parked in the already-outstanding epoch.
                    clearUnsaved(helper);
                    for (int amount = 20; amount < 30; amount++) {
                        setScalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1, amount);
                    }
                    if (chunk(helper).isUnsaved()) {
                        helper.fail("Writes in one unacknowledged persist epoch must not re-mark the chunk");
                    }
                })
                .thenSucceed();
    }

    private static void scalarPersistEpochRearmsAfterRealChunkSave(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());

        helper.startSequence()
                .thenExecuteAfter(92, () -> {
                    simulateRealChunkSnapshot(helper);
                    setScalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1, 10);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk(helper).isUnsaved()) {
                        helper.fail("The first persisted scalar mutation must mark the chunk unsaved");
                    }

                    simulateRealChunkSnapshot(helper);
                    setScalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1, 20);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk(helper).isUnsaved()) {
                        helper.fail("A mutation after real ChunkDataEvent.Save must mark a new persist epoch");
                    }
                })
                .thenSucceed();
    }

    private static void itemContentsChangeMarksChunkUnsavedSameTick(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    suspendRecipeTicker(helper, machine);
                    simulateRealChunkSnapshot(helper);
                    insertCoal(helper, machine, 1);
                })
                .thenExecuteAfter(1, () -> {
                    if (!chunk(helper).isUnsaved()) {
                        helper.fail("An item slot change must mark the chunk unsaved by the end of the same tick");
                    }
                })
                .thenSucceed();
    }

    private static void frameworkAfterTickRecomputesComputedFieldsOncePerTick(GameTestHelper helper) {
        place(helper, TopoTickHotPathGameTestFixtures.dualTickerMachine());
        int[] baseline = { 0 };

        helper.startSequence()
                .thenExecuteAfter(3, () -> baseline[0] = TopoTickHotPathGameTestFixtures.recomputeCount())
                .thenExecuteAfter(1, () -> {
                    int delta = TopoTickHotPathGameTestFixtures.recomputeCount() - baseline[0];
                    if (delta != 1) {
                        helper.fail("Computed fields must recompute exactly once per server tick for a machine " + "with two tickers, got " + delta + " recomputes in one tick");
                    }
                })
                .thenSucceed();
    }

    private static void scalarTickRecipesClassifyAsDirectLaneEligible(GameTestHelper helper) {
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        ScalarRecipeCapability advanced = BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability();

        TopoRecipe disjointScalar = TopoScalarGameTestFixtures.energyGeneratorType()
                .recipe("gametest_direct_eligibility_disjoint")
                .tickInput(energy.in(10))
                .tickOutput(advanced.out(1))
                .duration(5)
                .buildRecipe();
        if (!disjointScalar.hasDirectTickIo()) {
            helper.fail("Disjoint all-scalar tick entries must classify as direct-lane eligible");
        }

        TopoRecipe sameLane = TopoScalarGameTestFixtures.energyGeneratorType()
                .recipe("gametest_direct_eligibility_same_lane")
                .tickInput(energy.in(10))
                .tickOutput(energy.out(5))
                .duration(5)
                .buildRecipe();
        if (!sameLane.hasDirectTickIo()) {
            helper.fail("A scalar lane with exact post-input planning must remain direct-lane eligible");
        }

        TopoRecipe itemTick = TopoScalarGameTestFixtures.energyGeneratorType()
                .recipe("gametest_direct_eligibility_item_tick")
                .tickInput(BuiltinTopoResourceIntegrations.ITEM.recipeCapability().in(Items.COAL, 1))
                .tickOutput(energy.out(5))
                .duration(5)
                .buildRecipe();
        if (itemTick.hasDirectTickIo()) {
            helper.fail("Item tick entries must not classify as direct-lane eligible");
        }
        helper.succeed();
    }

    private static void directTickIoBlocksAtomicallyWhenOutputFull(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.advancedGenerator());
        setScalarAmount(machine, ScalarResourcePort.ENERGY_INPUT_1, 1_000);
        RecipeLogic logic = logic(machine);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (logic.state() != RecipeLogic.State.WORKING || logic.progress() < 2) {
                        helper.fail("Advanced generator should be working with at least 2 ticks of progress");
                    }
                })
                .thenExecute(() -> setScalarAmount(machine, ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1, 1_000))
                // Two ticks of margin: callback-vs-TickHub ordering within one server tick is not
                // guaranteed, so the blocked machine tick is only certain to have run by T+2.
                .thenExecuteAfter(2, () -> {
                    long energyAfterBlock = scalarAmount(machine, ScalarResourcePort.ENERGY_INPUT_1);
                    if (logic.state() != RecipeLogic.State.WAITING_OUTPUT) {
                        helper.fail("Full advanced output should park the machine in WAITING_OUTPUT, got " + logic.state());
                    }
                    long consumed = 1_000 - energyAfterBlock;
                    if (consumed % TopoScalarGameTestFixtures.ADVANCED_ENERGY_IN_PER_TICK != 0) {
                        helper.fail("A blocked tick must not partially consume energy input; consumed " + consumed + " is not a multiple of " + TopoScalarGameTestFixtures.ADVANCED_ENERGY_IN_PER_TICK);
                    }
                })
                .thenSucceed();
    }

    private static void directTickOutputSpansMultiplePortsInMountOrder(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoTickHotPathGameTestFixtures.dualPortGenerator());
        insertCoal(helper, machine, 1);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long total = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1) + scalarAmount(machine, ScalarResourcePort.ENERGY_STORAGE);
                    if (total < TopoTickHotPathGameTestFixtures.DUAL_PORT_FIRST_CAPACITY + TopoScalarGameTestFixtures.GENERATOR_ENERGY_PER_TICK) {
                        helper.fail("Waiting for the tick output to overflow the first port, total=" + total);
                    }
                })
                .thenExecute(() -> {
                    long first = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1);
                    long second = scalarAmount(machine, ScalarResourcePort.ENERGY_STORAGE);
                    if (first != TopoTickHotPathGameTestFixtures.DUAL_PORT_FIRST_CAPACITY) {
                        helper.fail("First energy port must fill to capacity before the second receives anything, got " + first + "/" + TopoTickHotPathGameTestFixtures.DUAL_PORT_FIRST_CAPACITY);
                    }
                    if (second <= 0) {
                        helper.fail("Overflow must land in the second energy port in mount order, got " + second);
                    }
                })
                .thenSucceed();
    }

    private static void itemTickEntriesFallBackToTransactionalPath(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        TopoRecipe itemTickRecipe = TopoScalarGameTestFixtures.energyGeneratorType()
                .recipe("gametest_item_tick_fallback")
                .tickInput(BuiltinTopoResourceIntegrations.ITEM.recipeCapability().in(Items.COAL, 1))
                .tickOutput(energy.out(5))
                .duration(5)
                .buildRecipe();

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    suspendRecipeTicker(helper, machine);
                    insertCoal(helper, machine, 3);
                })
                .thenExecuteAfter(1, () -> {
                    if (itemTickRecipe.hasDirectTickIo()) {
                        helper.fail("Item tick entries must report no direct lane support");
                    }
                    TopoRecipe.TickIoResult result = itemTickRecipe.handleTickIo(machine);
                    if (result != TopoRecipe.TickIoResult.SUCCESS) {
                        helper.fail("Item tick fallback should succeed with stocked input, got " + result);
                    }
                    int coalLeft = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).computeItemTotal();
                    if (coalLeft != 2) {
                        helper.fail("Item tick fallback must consume exactly one coal, left " + coalLeft);
                    }
                    long stored = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1);
                    if (stored != 5) {
                        helper.fail("Item tick fallback must emit exactly one tick of energy, stored " + stored);
                    }
                })
                .thenSucceed();
    }

    private static void scalarResidualDirtyMarksChunkUnsavedOnLevelSave(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());

        helper.startSequence()
                // First dirty transition opens an outstanding persist epoch.
                .thenExecuteAfter(2, () -> setScalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1, 10))
                .thenExecuteAfter(2, () -> {
                    // Same-callback sequence keeps this deterministic: mutate again, simulate a
                    // failed snapshot clearing the flag, then run the save-event recovery hook.
                    setScalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1, 30);
                    clearUnsaved(helper);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk(helper).isUnsaved()) {
                        helper.fail("Save-event recovery must restore a dropped unsaved mark for the latest " + "persist generation");
                    }
                })
                .thenSucceed();
    }

    private static void monitoredTickReportsFrameworkSelfNanos(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());
        insertCoal(helper, machine, 1);
        machine.activatePerformanceMonitoring(40);
        RecipeLogic logic = logic(machine);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Self nanos test setup should reach WORKING, got " + logic.state());
                    }
                })
                .thenExecuteAfter(2, () -> {
                    if (!machine.publishRecentPerformanceSnapshot(machine.performanceSampleMaxAgeTicks())) {
                        helper.fail("A monitored working machine must have a fresh performance sample");
                    }
                    MachinePerformanceSnapshot snapshot = machine.lastPerformanceSnapshot();
                    if (snapshot.selfNanos() <= 0L) {
                        helper.fail("Monitored ticks must report the framework after-tick cost as self nanos, got " + snapshot.selfNanos());
                    }
                })
                .thenSucceed();
    }

    private static void perfTreeReportsRollingAverageAndPeak(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());
        insertCoal(helper, machine, 1);
        machine.activatePerformanceMonitoring(60);
        RecipeLogic logic = logic(machine);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Perf tree test setup should reach WORKING, got " + logic.state());
                    }
                })
                .thenExecuteAfter(5, () -> {
                    CompoundTag data = new CompoundTag();
                    MachineDataProvider.packSnapshot(machine, data);
                    CompoundTag root = requireCompound(helper, data, "perfTree");
                    CompoundTag traits = requireChildById(helper, root, "traits");
                    CompoundTag recipeTrait = requireChildById(
                            helper, traits, RecipeLogic.RECIPE_LOGIC_1.id().toString());
                    long avg = recipeTrait.getLongOr("avgNanos", 0L);
                    long peak = recipeTrait.getLongOr("peakNanos", 0L);
                    if (avg <= 0L) {
                        helper.fail("Monitored perf tree nodes must publish a rolling average, avgNanos=" + avg);
                    }
                    if (peak <= 0L) {
                        helper.fail("Monitored perf tree nodes must publish a recent peak, peakNanos=" + peak);
                    }
                })
                .thenSucceed();
    }

    private static void idleBlockedMachineBacksOffTickInterval(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.advancedGenerator());
        RecipeLogic logic = logic(machine);

        helper.startSequence()
                .thenExecuteAfter(BACKOFF_RAMP_MARGIN_TICKS, () -> {
                    if (logic.state() != RecipeLogic.State.WAITING_TICK_INPUT_TO_START) {
                        helper.fail("Zero-energy advanced generator should park waiting for tick input, got " + logic.state());
                    }
                    var handle = logic.handle();
                    if (handle == null) {
                        helper.fail("Parked machine should keep its tick handle attached");
                        return;
                    }
                    if (handle.interval() != RecipeLogic.PARKED_BACKOFF_MAX_INTERVAL) {
                        helper.fail("Parked recipe logic must back its tick interval off to " + RecipeLogic.PARKED_BACKOFF_MAX_INTERVAL + ", got " + handle.interval());
                    }
                })
                .thenSucceed();
    }

    private static void parkedMachineWakesOnResourceChange(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.advancedGenerator());
        RecipeLogic logic = logic(machine);

        helper.startSequence()
                .thenExecuteAfter(BACKOFF_RAMP_MARGIN_TICKS, () -> {
                    var handle = logic.handle();
                    if (handle == null || handle.interval() <= 1) {
                        helper.fail("Setup: machine should be parked with a backed-off interval before the wake, " + "interval=" + (handle == null ? "detached" : handle.interval()));
                    }
                    insertEnergy(helper, machine, 1_000);
                })
                .thenExecuteAfter(WAKE_MARGIN_TICKS, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Energy arriving must wake the parked machine within " + WAKE_MARGIN_TICKS + " ticks, state=" + logic.state());
                    }
                    var handle = logic.handle();
                    if (handle == null || handle.interval() != 1) {
                        helper.fail("A woken working machine must tick every tick again, interval=" + (handle == null ? "detached" : handle.interval()));
                    }
                })
                .thenSucceed();
    }

    private static void workingMachineKeepsEveryTickInterval(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.advancedGenerator());
        setScalarAmount(machine, ScalarResourcePort.ENERGY_INPUT_1, 900);
        RecipeLogic logic = logic(machine);
        int[] workingTicks = { 0 };

        var sequence = helper.startSequence()
                .thenWaitUntil(() -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Fueled advanced generator should reach WORKING, got " + logic.state());
                    }
                });
        // 夹具配方时长仅 10t,连续生产会循环"完成→IDLE 间隙 tick→重启";逐 tick 采样断言节奏,
        // 而不是断言任一瞬时状态:产线循环中的机器永远不允许衰减 interval。
        for (int window = 0; window < 12; window++) {
            sequence = sequence.thenExecuteAfter(1, () -> {
                if (logic.state() == RecipeLogic.State.WORKING) {
                    workingTicks[0]++;
                }
                var handle = logic.handle();
                if (handle == null || handle.interval() != 1) {
                    helper.fail("A continuously producing machine must keep its every-tick cadence, interval=" + (handle == null ? "detached" : handle.interval()) + ", state=" + logic.state());
                }
            });
        }
        sequence
                .thenExecute(() -> {
                    if (workingTicks[0] < 8) {
                        helper.fail("Sampling window should observe a mostly WORKING machine, saw " + workingTicks[0] + "/12 working ticks");
                    }
                })
                .thenSucceed();
    }

    private static void workModeToggleWakesParkedMachine(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.advancedGenerator());
        RecipeLogic logic = logic(machine);

        helper.startSequence()
                .thenExecuteAfter(BACKOFF_RAMP_MARGIN_TICKS, () -> {
                    // 先停泊再急停、随后注能:资源唤醒必须把 ticker 叫起来,但 HALTED 必须压住启动。
                    if (logic.toggleWorkMode() != MachineWorkControl.WorkMode.HALTED) {
                        helper.fail("First toggle from the default mode should halt the machine");
                    }
                    insertEnergy(helper, machine, 1_000);
                })
                .thenExecuteAfter(25, () -> {
                    if (logic.state() == RecipeLogic.State.WORKING) {
                        helper.fail("A halted machine must not start even after energy arrives");
                    }
                    logic.toggleWorkMode();
                })
                .thenExecuteAfter(WAKE_MARGIN_TICKS, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Resuming from halt must wake the backed-off ticker and start within " + WAKE_MARGIN_TICKS + " ticks, got " + logic.state());
                    }
                })
                .thenSucceed();
    }

    private static void boundDirectTickIoRebindsAfterRoutingInvalidation(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());
        insertCoal(helper, machine, 1);
        RecipeLogic logic = logic(machine);
        long[] outputBefore = { 0L };

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Routing invalidation setup should reach WORKING, got " + logic.state());
                    }
                })
                .thenExecute(() -> {
                    logic.setProgressForGameTest(2);
                    outputBefore[0] = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1);
                    machine.machineComponents().invalidateRecipeHandlers();
                })
                .thenExecuteAfter(3, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("A routing revision change must not discard or stall the active recipe, got " + logic.state());
                    }
                    int advanced = logic.progress() - 2;
                    long produced = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1) - outputBefore[0];
                    if (advanced <= 0) {
                        helper.fail("The rebound active recipe must continue advancing, progress=" + logic.progress());
                    }
                    long expected = (long) advanced * TopoScalarGameTestFixtures.GENERATOR_ENERGY_PER_TICK;
                    if (produced != expected) {
                        helper.fail("Routing-plan rebind must conserve exact tick output: advanced=" + advanced + ", expected=" + expected + ", produced=" + produced);
                    }
                })
                .thenSucceed();
    }

    private static void boundDirectTickIoBlocksWhenActivePoolDisappears(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoScalarGameTestFixtures.energyGenerator());
        insertCoal(helper, machine, 1);
        RecipeLogic logic = logic(machine);
        MachineSearchPoolConfig poolConfig = machine.machineComponents().require(MachineSearchPoolConfig.RECIPE_SEARCH_POOL);
        long[] blockedOutput = { 0L };

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Missing-pool setup should reach WORKING, got " + logic.state());
                    }
                })
                .thenExecute(() -> {
                    logic.setProgressForGameTest(2);
                    blockedOutput[0] = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1);
                    poolConfig.setConfiguredPoolIdRaw("a1b2c3");
                })
                .thenExecuteAfter(3, () -> {
                    if (logic.state() != RecipeLogic.State.WAITING_OUTPUT) {
                        helper.fail("Removing the active DEFAULT pool must block instead of switching pools, got " + logic.state());
                    }
                    if (logic.progress() != 2) {
                        helper.fail("A missing active pool must freeze progress at 2, got " + logic.progress());
                    }
                    long output = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1);
                    if (output != blockedOutput[0]) {
                        helper.fail("A missing active pool must not emit through another pool: before=" + blockedOutput[0] + ", after=" + output);
                    }
                    poolConfig.resetToFieldDefault();
                })
                .thenExecuteAfter(3, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Restoring the active DEFAULT pool must resume WORKING, got " + logic.state());
                    }
                    int advanced = logic.progress() - 2;
                    long produced = scalarAmount(machine, ScalarResourcePort.ENERGY_OUTPUT_1) - blockedOutput[0];
                    long expected = (long) advanced * TopoScalarGameTestFixtures.GENERATOR_ENERGY_PER_TICK;
                    if (advanced <= 0 || produced != expected) {
                        helper.fail("Restored active pool must resume with exact conservation: advanced=" + advanced + ", expected=" + expected + ", produced=" + produced);
                    }
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------

    private static MachineBlockEntity place(GameTestHelper helper, MachineDefinition definition) {
        helper.setBlock(MACHINE_POS, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static RecipeLogic logic(MachineBlockEntity machine) {
        return machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static void suspendRecipeTicker(GameTestHelper helper, MachineBlockEntity machine) {
        var handle = logic(machine).handle();
        if (handle == null) {
            helper.fail("Recipe ticker should be attached before suspension");
        }
        handle.suspend();
    }

    private static ChunkAccess chunk(GameTestHelper helper) {
        return helper.getLevel().getChunkAt(helper.absolutePos(MACHINE_POS));
    }

    private static void clearUnsaved(GameTestHelper helper) {
        chunk(helper).tryMarkSaved();
    }

    /** Mirrors ChunkMap.save's main-thread acknowledgement without writing the GameTest level. */
    private static void simulateRealChunkSnapshot(GameTestHelper helper) {
        LevelChunk levelChunk = (LevelChunk) chunk(helper);
        levelChunk.tryMarkSaved();
        SerializableChunkData captured = SerializableChunkData.copyOf(helper.getLevel(), levelChunk);
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(levelChunk, helper.getLevel(), captured));
    }

    private static void setScalarAmount(MachineBlockEntity machine, ComponentKey<ScalarResourcePort> key, int amount) {
        ScalarResourcePort storage = machine.machineComponents().require(key);
        storage.handler().set(0, amount > 0 ? storage.resource() : ScalarResource.EMPTY, amount);
    }

    private static long scalarAmount(MachineBlockEntity machine, ComponentKey<ScalarResourcePort> key) {
        return machine.machineComponents().require(key).storedAmount();
    }

    /** 走事务提交路径注入能量:与管道/自动化的生产路径同一条 onStorageChanged→版本撞击→唤醒链。 */
    private static void insertEnergy(GameTestHelper helper, MachineBlockEntity machine, int amount) {
        ScalarResourcePort input = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            long inserted = input.handler().insert(input.resource(), amount, transaction);
            if (inserted != amount) {
                helper.fail("Setup: expected to insert " + amount + " energy, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void insertCoal(GameTestHelper helper, MachineBlockEntity machine, int count) {
        ItemResourcePort input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.handler().insert(ItemResource.of(Items.COAL), count, transaction);
            if (inserted != count) {
                helper.fail("Setup: expected to insert " + count + " coal, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static CompoundTag requireCompound(GameTestHelper helper, CompoundTag parent, String key) {
        if (!(parent.get(key) instanceof CompoundTag tag)) {
            helper.fail("Expected compound '" + key + "' in " + parent);
            throw new IllegalStateException("unreachable");
        }
        return tag;
    }

    private static CompoundTag requireChildById(GameTestHelper helper, CompoundTag parent, String id) {
        ListTag children = parent.getList("children").orElseGet(ListTag::new);
        for (int i = 0; i < children.size(); i++) {
            var child = children.getCompound(i);
            if (child.isPresent() && id.equals(child.get().getStringOr("id", ""))) {
                return child.get();
            }
        }
        helper.fail("Missing perf tree child '" + id + "' in " + parent);
        throw new IllegalStateException("unreachable");
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Machine tick hot path GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
