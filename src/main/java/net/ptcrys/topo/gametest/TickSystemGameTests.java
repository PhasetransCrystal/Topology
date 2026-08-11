package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.api.tick.TickHandle;
import net.ptcrys.topo.api.tick.TickHub;
import net.ptcrys.topo.api.tick.TickKind;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.apiv2.machine.component.RecipeUi;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.BuiltinOIMeMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topo.integration.ae2.AeFluidInputSync;
import net.ptcrys.topo.integration.jade.MachineDataProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class TickSystemGameTests {

    private static final String SUITE = "tick_system";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 7;

    private TickSystemGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("tick_system"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "tick_kinds_expose_sync_and_async_lanes",
                "Tests TickKind: the builtin enum exposes exactly the sync and async lanes with " + "correct threading flags.",
                TickSystemGameTests::tickKindsExposeSyncAndAsyncLanes);
        register(
                event,
                environment,
                index++,
                "tickhub_exists_on_server_level",
                "Tests TickHeartbeat and TickHub level lifecycle: the current server level has a hub.",
                TickSystemGameTests::tickhubExistsOnServerLevel);
        register(
                event,
                environment,
                index++,
                "tickhub_interval_and_alert_drive_sync_hooks",
                "Tests TickHub sync scheduling: interval buckets skip off-beat ticks, and alert runs " + "a suspended hook exactly once.",
                TickSystemGameTests::tickhubIntervalAndAlertDriveSyncHooks);
        register(
                event,
                environment,
                index++,
                "machine_ticker_attaches_and_drives_macerator",
                "Tests MachineTicker, TickHub, RecipeLogic, and the real macerator: the " + "recipe ticker attaches to the level hub and drives recipe startup without a block ticker.",
                TickSystemGameTests::machineTickerAttachesAndDrivesMacerator);
        register(
                event,
                environment,
                index++,
                "machine_ticker_performance_is_nested_under_recipe_trait",
                "Tests MachinePerformanceSnapshot and Jade timing data: a real macerator recipe tick is " + "reported as a tick child under the recipe logic trait.",
                TickSystemGameTests::machineTickerPerformanceIsNestedUnderRecipeTrait);
        register(
                event,
                environment,
                index++,
                "machine_ticker_performance_snapshot_does_not_leak_after_monitor_expires",
                "Tests MachinePerformanceSnapshot and Jade timing data: expired samples from a previous " + "machine state are not reported as current idle cost.",
                TickSystemGameTests::machineTickerPerformanceSnapshotDoesNotLeakAfterMonitorExpires);
        register(
                event,
                environment,
                index,
                "machine_ticker_performance_window_covers_interval_tickers",
                "Tests MachineBlockEntity.performanceSampleMaxAgeTicks and Jade timing data: a machine " + "whose only ticker runs on a slow interval (ME hatch, 40t cadence) keeps its latest " + "sample visible between runs instead of expiring after 2 ticks.",
                TickSystemGameTests::machineTickerPerformanceWindowCoversIntervalTickers);
    }

    private static void tickKindsExposeSyncAndAsyncLanes(GameTestHelper helper) {
        if (TickKind.values().length != 2) {
            helper.fail("TickHub semantics only cover the sync and async lanes; adding a TickKind " + "constant requires revisiting TickHub before relaxing this, got " + TickKind.values().length);
        }
        if (TickKind.SYNC.async()) {
            helper.fail("Sync tick kind must not be async");
        }
        if (!TickKind.ASYNC.async()) {
            helper.fail("Async tick kind must be async");
        }
        helper.succeed();
    }

    private static void tickhubExistsOnServerLevel(GameTestHelper helper) {
        if (TickHub.of(helper.getLevel()) == null) {
            helper.fail("Server level should have a TickHub attached by TickHeartbeat");
        }
        helper.succeed();
    }

    private static void tickhubIntervalAndAlertDriveSyncHooks(GameTestHelper helper) {
        TickHub hub = hubOf(helper);

        AtomicInteger intervalCalls = new AtomicInteger();
        TickHandle interval = hub.register(TickKind.SYNC, 5, (gameTime, handle) -> intervalCalls.incrementAndGet());
        hub.tickSync(1);
        hub.tickSync(4);
        if (intervalCalls.get() != 0) {
            helper.fail("Interval hook should skip off-beat ticks, got " + intervalCalls.get());
        }
        hub.tickSync(5);
        if (intervalCalls.get() != 1) {
            helper.fail("Interval hook should run once on gameTime 5, got " + intervalCalls.get());
        }
        interval.unsubscribe();
        hub.tickSync(6);

        AtomicInteger alertCalls = new AtomicInteger();
        TickHandle alert = hub.register(TickKind.SYNC, 1000, (gameTime, handle) -> alertCalls.incrementAndGet());
        alert.suspend();
        alert.alert();
        hub.tickSync(7);
        hub.tickSync(8);
        if (alertCalls.get() != 1) {
            helper.fail("Alert should run suspended hook exactly once, got " + alertCalls.get());
        }
        alert.unsubscribe();
        hub.tickSync(9);
        helper.succeed();
    }

    private static void machineTickerAttachesAndDrivesMacerator(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        RecipeLogic logic = logic(machine);

        ResourceHandler<ItemResource> input = requireInputCapability(helper);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(ironOreResource(), 1, transaction);
            if (inserted != 1) {
                helper.fail("UP input capability should accept one iron ore, inserted " + inserted);
            }
            transaction.commit();
        }

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("TickHub should drive recipe logic to WORKING, got " + logic.state());
                    }
                    TickHandle currentHandle = logic.handle();
                    if (currentHandle == null || currentHandle.isCancelled()) {
                        helper.fail("Recipe logic ticker should be attached to TickHub while the machine is loaded");
                    }
                })
                .thenSucceed();
    }

    private static void machineTickerPerformanceIsNestedUnderRecipeTrait(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        RecipeLogic logic = logic(machine);
        machine.activatePerformanceMonitoring(20);

        ResourceHandler<ItemResource> input = requireInputCapability(helper);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(ironOreResource(), 1, transaction);
            if (inserted != 1) {
                helper.fail("UP input capability should accept one iron ore, inserted " + inserted);
            }
            transaction.commit();
        }

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Performance test setup should reach WORKING, got " + logic.state());
                    }
                    machine.publishPerformanceSnapshotForCurrentTick();
                    MachinePerformanceSnapshot snapshot = machine.lastPerformanceSnapshot();
                    String componentId = RecipeLogic.RECIPE_LOGIC_1.id().toString();
                    assertRecipeLogicSnapshot(helper, snapshot, componentId);
                    assertRecipeLogicJadeTree(helper, machine, componentId);
                })
                .thenSucceed();
    }

    private static void machineTickerPerformanceSnapshotDoesNotLeakAfterMonitorExpires(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        RecipeLogic logic = logic(machine);
        long sampleGameTime = helper.getLevel().getGameTime();
        machine.recordPerformanceSample(sampleGameTime, 20_000L, logic.profileSlot());
        if (machine.recentPerformanceSnapshot(0).totalNanos() != 20_000L) {
            helper.fail("Fresh synthetic performance sample should be visible before it expires");
        }

        helper.startSequence()
                // 空机器停泊退避后,合法新鲜度窗口=实时 interval+1(与下方 ME 舱室窗口测试同语义);
                // 等满退避上限+2 后,样本对任何 interval≤上限 都必定过期,意图(不漏报旧样本)不变。
                .thenExecuteAfter(RecipeLogic.PARKED_BACKOFF_MAX_INTERVAL + 2, () -> {
                    CompoundTag data = new CompoundTag();
                    MachineDataProvider.packSnapshot(machine, data);
                    if (data.get("perfTree") instanceof CompoundTag) {
                        helper.fail("Jade data must not report an expired machine performance snapshot");
                    }
                })
                .thenSucceed();
    }

    private static void machineTickerPerformanceWindowCoversIntervalTickers(GameTestHelper helper) {
        helper.setBlock(
                MACHINE_POS,
                BuiltinOIMeMachines.ME_DRAWING_FLUID_INPUT_HATCH.registeredBlock().getDefaultState());
        MachineBlockEntity machine = helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
        MachineTicker syncTicker = machine.machineComponents().require(AeFluidInputSync.AE_FLUID_INPUT_SYNC);
        int expectedWindow = syncTicker.tickInterval() + 1;
        if (machine.performanceSampleMaxAgeTicks() != expectedWindow) {
            helper.fail("Hatch freshness window should cover its slowest ticker interval, expected " + expectedWindow + ", got " + machine.performanceSampleMaxAgeTicks());
        }
        machine.recordPerformanceSample(helper.getLevel().getGameTime(), 20_000L, syncTicker.profileSlot());

        helper.startSequence()
                .thenExecuteAfter(4, () -> {
                    CompoundTag data = new CompoundTag();
                    MachineDataProvider.packSnapshot(machine, data);
                    CompoundTag root = requireCompound(helper, data, "perfTree");
                    CompoundTag traits = requireChildById(helper, root, "traits");
                    CompoundTag syncTrait = requireChildById(
                            helper, traits, AeFluidInputSync.AE_FLUID_INPUT_SYNC.id().toString());
                    if (syncTrait.getLongOr("nanos", 0L) != 20_000L) {
                        helper.fail("Interval ticker sample should keep its recorded nanos in the Jade tree");
                    }
                })
                .thenSucceed();
    }

    private static TickHub hubOf(GameTestHelper helper) {
        TickHub hub = TickHub.of(helper.getLevel());
        if (hub == null) {
            helper.fail("Missing TickHub for server level");
        }
        return hub;
    }

    private static MachineBlockEntity placeMacerator(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinOIMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        MachineBlockEntity machine = helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
        // 研磨配方按 tick 抽电;测试机起手满能,聚焦各自的本职断言。
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), 10_000);
        return machine;
    }

    private static ItemResource ironOreResource() {
        return ItemResource.of(MaterialHelper.requireItem(
                net.ptcrys.topo.datav2.material.BuiltinOIMaterials.IRON,
                net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.ORE));
    }

    private static RecipeLogic logic(MachineBlockEntity machine) {
        return machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static ResourceHandler<ItemResource> requireInputCapability(GameTestHelper helper) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(
                Capabilities.Item.BLOCK,
                helper.absolutePos(MACHINE_POS),
                Direction.UP);
        if (handler == null) {
            helper.fail("Macerator UP side should expose an item input capability");
        }
        return handler;
    }

    private static void assertRecipeLogicSnapshot(
                                                  GameTestHelper helper,
                                                  MachinePerformanceSnapshot snapshot,
                                                  String componentId) {
        if (snapshot.isEmpty()) {
            helper.fail("Machine performance snapshot should contain the monitored TickHub tick");
        }
        if (snapshot.totalNanos() < snapshot.componentsNanos()) {
            helper.fail("Performance total should include trait nanos, total=" + snapshot.totalNanos() + ", traits=" + snapshot.componentsNanos());
        }
        assertMountedComponentSamplePresent(helper, snapshot, ItemResourcePort.ITEM_INPUT_1.id().toString());
        assertMountedComponentSamplePresent(helper, snapshot, ItemResourcePort.ITEM_OUTPUT_1.id().toString());
        assertMountedComponentSamplePresent(helper, snapshot, RecipeUi.RECIPE_UI_1.id().toString());
        if (snapshot.components().size() < 4) {
            helper.fail("Macerator performance snapshot should list all mounted traits, got " + snapshot.components().size());
        }
        MachinePerformanceSnapshot.ComponentSample sample = assertMountedComponentSamplePresent(helper, snapshot, componentId);
        if (sample.nanos() <= 0L) {
            helper.fail("Recipe logic trait sample should record positive tick nanos");
        }
        if (sample.children().size() != 1) {
            helper.fail("Recipe logic trait should expose exactly one tick child, got " + sample.children().size());
        }
        MachinePerformanceSnapshot.TimingSample tick = sample.children().get(0);
        String tickId = componentId + ".tick";
        if (!tick.id().equals(tickId)) {
            helper.fail("Recipe logic tick child id should be " + tickId + ", got " + tick.id());
        }
        if (!tick.label().equals("tick")) {
            helper.fail("Recipe logic tick child label should be tick, got " + tick.label());
        }
        if (tick.nanos() != sample.nanos()) {
            helper.fail("Recipe logic trait nanos should equal its tick child nanos, trait=" + sample.nanos() + ", tick=" + tick.nanos());
        }
    }

    private static void assertRecipeLogicJadeTree(
                                                  GameTestHelper helper,
                                                  MachineBlockEntity machine,
                                                  String componentId) {
        CompoundTag data = new CompoundTag();
        MachineDataProvider.packSnapshot(machine, data);
        CompoundTag root = requireCompound(helper, data, "perfTree");
        CompoundTag traits = requireChildById(helper, root, "traits");
        requireChildById(helper, traits, ItemResourcePort.ITEM_INPUT_1.id().toString());
        requireChildById(helper, traits, ItemResourcePort.ITEM_OUTPUT_1.id().toString());
        requireChildById(helper, traits, RecipeUi.RECIPE_UI_1.id().toString());
        CompoundTag recipeTrait = requireChildById(helper, traits, componentId);
        CompoundTag tick = requireChildById(helper, recipeTrait, componentId + ".tick");
        long traitNanos = recipeTrait.getLongOr("nanos", 0L);
        long tickNanos = tick.getLongOr("nanos", 0L);
        if (traitNanos <= 0L || tickNanos <= 0L) {
            helper.fail("Jade performance tree should expose positive recipe trait and tick nanos");
        }
        if (traitNanos != tickNanos) {
            helper.fail("Jade recipe logic trait nanos should equal child tick nanos, trait=" + traitNanos + ", tick=" + tickNanos);
        }
    }

    private static MachinePerformanceSnapshot.ComponentSample assertMountedComponentSamplePresent(
                                                                                                  GameTestHelper helper,
                                                                                                  MachinePerformanceSnapshot snapshot,
                                                                                                  String componentId) {
        for (MachinePerformanceSnapshot.ComponentSample candidate : snapshot.components()) {
            if (candidate.id().equals(componentId)) {
                return candidate;
            }
        }
        helper.fail("Performance snapshot should contain mounted trait sample " + componentId);
        throw new IllegalStateException("Missing mounted trait sample " + componentId);
    }

    private static CompoundTag requireCompound(GameTestHelper helper, CompoundTag tag, String key) {
        if (tag.get(key) instanceof CompoundTag child) {
            return child;
        }
        helper.fail("Expected compound tag " + key);
        throw new IllegalStateException("Expected compound tag " + key);
    }

    private static CompoundTag requireChildById(GameTestHelper helper, CompoundTag parent, String id) {
        ListTag children = parent.getList("children").orElseGet(ListTag::new);
        for (int i = 0; i < children.size(); i++) {
            var child = children.getCompound(i);
            if (child.isPresent() && id.equals(child.get().getStringOr("id", ""))) {
                return child.get();
            }
        }
        helper.fail("Expected child timing node " + id);
        throw new IllegalStateException("Expected child timing node " + id);
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("TickSystem GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        testData(environment),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
                                                                           Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE);
    }
}
