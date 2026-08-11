package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.PipeSideVisual;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.network.PipeNetworksSavedData;
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
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

/**
 * 管道网络游戏内测试(全局编号 156-170、191-199、289):端到端搬运、扳手三态、强拆分网、路径占用
 * 木桶限流、共享节点额度、分配策略、标量三连、流体、移除清理、SavedData codec 往返、
 * 活目的地分母、直搬通道与黑白名单过滤、批内公平份额记账。
 *
 * <p>
 * 场地约定:石地板铺 y=0 的 5×5,管道与容器立于 y=1。物品测试用原版箱子,
 * 标量/流体测试用 {@link TopoPipeGameTestFixtures} 的纯储存缓冲机。所有网络断言走
 * {@code networkAt} 的网络作用域读数,不读全局表(测试与并发测试共享同一份维度 SavedData)。
 */
public final class PipeNetworkGameTests {

    private static final String SUITE = "pipe_network";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 24;

    private PipeNetworkGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!TopoScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("pipe_network"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(event, environment, index++,
                "pipe_item_transfers_between_chests",
                "Tests the end-to-end pull model: a basic item pipe line extracts from one chest and " + "fills the other at the tier extract rate.",
                PipeNetworkGameTests::pipeItemTransfersBetweenChests);
        register(event, environment, index++,
                "pipe_wrench_cycles_side_intent",
                "Tests wrench shift-click cycling on a container side: AUTO(insert) -> EXTRACT -> " + "DISABLED -> AUTO, with the derived blockstate visuals following.",
                PipeNetworkGameTests::pipeWrenchCyclesSideIntent);
        register(event, environment, index++,
                "pipe_disabled_side_splits_network_and_stops_flow",
                "Tests forced disconnects: disabling a link side splits the component and stops the " + "flow; re-enabling reconnects and resumes.",
                PipeNetworkGameTests::pipeDisabledSideSplitsNetworkAndStopsFlow);
        register(event, environment, index++,
                "pipe_basic_midsection_caps_elite_endpoints",
                "Tests the path-occupancy bucket model: a basic node in the middle of an elite line " + "caps the whole route at the basic node throughput.",
                PipeNetworkGameTests::pipeBasicMidsectionCapsEliteEndpoints);
        register(event, environment, index++,
                "pipe_two_extractors_share_node_budget",
                "Tests ledger sharing: two extraction ports pulling through the same basic node cannot " + "jointly exceed its per-tick throughput.",
                PipeNetworkGameTests::pipeTwoExtractorsShareNodeBudget);
        register(event, environment, index++,
                "pipe_equal_split_strategy_splits_between_destinations",
                "Tests the equal-split distribution strategy on an advanced item pipe tee: two " + "destination chests fill at matching rates.",
                PipeNetworkGameTests::pipeEqualSplitStrategySplits);
        register(event, environment, index++,
                "pipe_round_robin_strategy_serves_both_destinations",
                "Tests the round-robin distribution strategy on an elite item pipe tee: the rotating " + "cursor serves both destinations evenly over time.",
                PipeNetworkGameTests::pipeRoundRobinStrategyServesBoth);
        register(event, environment, index++,
                "pipe_scalar_pipes_move_energy_advanced_heat",
                "Tests resource-agnostic transfer over the scalar block capabilities: energy, advanced " + "energy and heat pipes each move buffer to buffer.",
                PipeNetworkGameTests::pipeScalarPipesMoveAllThree);
        register(event, environment, index++,
                "pipe_fluid_pipe_moves_water",
                "Tests fluid transfer: a basic fluid pipe line moves water between tank machines at the " + "tier extract rate.",
                PipeNetworkGameTests::pipeFluidPipeMovesWater);
        register(event, environment, index++,
                "pipe_broken_pipe_prunes_node_and_splits_network",
                "Tests removal cleanup: breaking the middle pipe drops the block, prunes its saved-data " + "node and splits the remaining component.",
                PipeNetworkGameTests::pipeBrokenPipePrunesNodeAndSplits);
        register(event, environment, index++,
                "pipe_saved_data_codec_roundtrip_preserves_config",
                "Tests persistence: the pipe saved-data codec round-trips intents, roles and the " + "selected strategy port config bit-for-bit.",
                PipeNetworkGameTests::pipeSavedDataCodecRoundtrip);
        register(event, environment, index++,
                "pipe_equal_split_balances_scalar_machines",
                "Tests the user-reported trunk-plus-two-branches topology with scalar machines: an " + "equal-split energy extractor fills two buffer machines at matching rates.",
                PipeNetworkGameTests::pipeEqualSplitBalancesScalarMachines);
        register(event, environment, index++,
                "pipe_equal_split_shares_a_saturated_bottleneck",
                "Tests equal split through a shared saturated segment (elite extractor, basic " + "trunk): the rotating head alternates ticks so both targets average equal " + "instead of the first one monopolising the bucket.",
                PipeNetworkGameTests::pipeEqualSplitSharesSaturatedBottleneck);
        register(event, environment, index++,
                "pipe_equal_split_divides_scarce_supply_within_one_tick",
                "Tests true per-tick equal split under a scarce source: 30 energy under a 131k " + "budget lands as 15/15 in the same tick instead of the first destination " + "swallowing the whole supply.",
                PipeNetworkGameTests::pipeEqualSplitDividesScarceSupply);
        register(event, environment, index++,
                "pipe_equal_split_skips_dead_destinations",
                "Tests the user-reported four-target topology with one full buffer: equal split " + "divides the supply among the three live machines (10/10/10) and the dead " + "one neither receives nor eats a share.",
                PipeNetworkGameTests::pipeEqualSplitSkipsDeadDestinations);
        register(event, environment, index++,
                "pipe_item_machine_to_machine_direct_lane",
                "Tests item transfer between machine ports — the transaction-free direct lane, " + "including the insert-only directional view on the consumer side: 40 coal " + "arrive intact and conservation holds.",
                PipeNetworkGameTests::pipeItemMachineToMachineDirectLane);
        register(event, environment, index++,
                "pipe_mixed_kind_source_falls_back_to_transactional_remainder",
                "Tests direct-lane semantic equality on a mixed source: the target rejects the " + "front kind (stone, no room) so the direct loop stops, and the " + "transactional remainder still delivers the coal behind it.",
                PipeNetworkGameTests::pipeMixedKindSourceFallsBack);
        register(event, environment, index++,
                "pipe_by_distance_farthest_serves_far_target",
                "Tests the by-distance strategy's FARTHEST order on an item tee: every batch lands " + "fully on the far destination and the near one stays empty.",
                PipeNetworkGameTests::pipeByDistanceFarthest);
        register(event, environment, index++,
                "pipe_whitelist_only_moves_listed_item",
                "Tests the port whitelist on an advanced item pipe: only the listed item moves, the " + "unlisted one stays in the source, and invalid entries are rejected.",
                PipeNetworkGameTests::pipeWhitelistOnlyMovesListedItem);
        register(event, environment, index++,
                "pipe_blacklist_blocks_listed_item",
                "Tests the port blacklist: the listed item never moves while everything else flows " + "normally.",
                PipeNetworkGameTests::pipeBlacklistBlocksListedItem);
        register(event, environment, index++,
                "pipe_whitelist_and_blacklist_combine",
                "Tests simultaneous lists: an item on the whitelist but also on the blacklist stays, " + "while a purely whitelisted item moves.",
                PipeNetworkGameTests::pipeWhitelistAndBlacklistCombine);
        register(event, environment, index++,
                "pipe_tag_filter_matches_item_tag",
                "Tests #tag whitelist entries: oak planks match #minecraft:planks and move; dirt " + "does not match and stays.",
                PipeNetworkGameTests::pipeTagFilterMatchesItemTag);
        register(event, environment, index++,
                "pipe_filtered_direct_lane_falls_back_to_transactional",
                "Tests filter semantics on the transaction-free direct lane: a filtered-out front " + "kind stops the direct loop and the transactional remainder still delivers " + "the whitelisted item behind it.",
                PipeNetworkGameTests::pipeFilteredDirectLaneFallsBack);
        register(event, environment, index,
                "pipe_two_ports_keep_fair_slice_on_shared_trunk",
                "Tests strict per-batch fair-slice accounting: two same-interval ports crossing one " + "shared basic trunk each hold exactly half its throughput — neither port " + "can sweep the other's slice via repeated lane bookings.",
                PipeNetworkGameTests::pipeTwoPortsKeepFairSliceOnSharedTrunk);
    }

    /** 把抽取端口的聚合间隔钳到该策略 offer 的最小档,缩短测试的批次等待。 */
    private static int useMinInterval(GameTestHelper helper, BlockPos absExtractor, Direction side) {
        PipeLevelRuntime runtime = runtime(helper);
        int min = runtime.portWindow(absExtractor, side).minInterval();
        runtime.setExtractConfig(absExtractor, side,
                runtime.portConfig(absExtractor, side).withInterval(min));
        return min;
    }

    /** 测试 156:箱→基础物品管×3→箱,1 个/t 的速率端到端搬运,总量守恒。 */
    private static void pipeItemTransfersBetweenChests(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        source.setItem(0, new ItemStack(Items.COAL, 64));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        runtime(helper).setSideIntent(helper.absolutePos(new BlockPos(1, 1, 2)), Direction.WEST, PipeSideIntent.EXTRACT);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) <= 0) {
                        helper.fail("waiting for the first batch");
                    }
                })
                .thenExecute(() -> {
                    int moved = countItems(target, Items.COAL);
                    int left = countItems(source, Items.COAL);
                    // BASIC 固定 40t 聚合,批次 = 1/t x 40 = 40。
                    if (moved != 40) {
                        helper.fail("First basic batch must be exactly 40, got " + moved);
                    }
                    if (moved + left != 64) {
                        helper.fail("Item conservation violated: " + moved + " moved + " + left + " left != 64");
                    }
                })
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) < 64) {
                        helper.fail("waiting for the remainder batch");
                    }
                })
                .thenExecute(() -> {
                    if (countItems(source, Items.COAL) != 0) {
                        helper.fail("Source must drain completely");
                    }
                })
                .thenSucceed();
    }

    /** 测试 157:扳手三态循环——对容器侧 AUTO(普通)→EXTRACT→DISABLED→AUTO,视觉同步。 */
    private static void pipeWrenchCyclesSideIntent(GameTestHelper helper) {
        placeFloor(helper);
        BlockPos pipePos = new BlockPos(2, 1, 2);
        chest(helper, new BlockPos(3, 1, 2));
        placePipe(helper, pipePos, BuiltinTopoPipes.ITEM_PIPE_BASIC);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pipePos);

        helper.startSequence()
                .thenExecuteAfter(1, () -> assertVisual(helper, pipePos, Direction.EAST, PipeSideVisual.PIPE))
                .thenExecute(() -> PipeNetworkEngine.cycleSideIntent(level, absolute, Direction.EAST, player))
                .thenExecuteAfter(1, () -> assertVisual(helper, pipePos, Direction.EAST, PipeSideVisual.EXTRACT))
                .thenExecute(() -> PipeNetworkEngine.cycleSideIntent(level, absolute, Direction.EAST, player))
                .thenExecuteAfter(1, () -> assertVisual(helper, pipePos, Direction.EAST, PipeSideVisual.NONE))
                .thenExecute(() -> PipeNetworkEngine.cycleSideIntent(level, absolute, Direction.EAST, player))
                .thenExecuteAfter(1, () -> assertVisual(helper, pipePos, Direction.EAST, PipeSideVisual.PIPE))
                .thenSucceed();
    }

    /** 测试 158:DISABLED 拆分网络并停流,恢复 AUTO 复通续流。 */
    private static void pipeDisabledSideSplitsNetworkAndStopsFlow(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity targetChest = chest(helper, new BlockPos(4, 1, 2));
        fillChest(source, Items.COAL, 320);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime.setSideIntent(helper.absolutePos(new BlockPos(1, 1, 2)), Direction.WEST, PipeSideIntent.EXTRACT);
        int[] frozenCount = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(targetChest, Items.COAL) <= 0) {
                        helper.fail("waiting for flow before the disconnect");
                    }
                })
                .thenExecute(() -> {
                    runtime.setSideIntent(middle, Direction.EAST, PipeSideIntent.DISABLED);
                    PipeNetwork<?> left = runtime.networkAt(middle);
                    if (left == null || left.nodeCount() != 2) {
                        helper.fail("Disabling the middle-east side should leave a 2-node component, got " + (left == null ? "none" : left.nodeCount()));
                    }
                    PipeNetwork<?> right = runtime.networkAt(helper.absolutePos(new BlockPos(3, 1, 2)));
                    if (right == null || right.nodeCount() != 1) {
                        helper.fail("The cut-off pipe should form a 1-node component");
                    }
                    frozenCount[0] = countItems(targetChest, Items.COAL);
                })
                .thenExecuteAfter(45, () -> {
                    // 一个完整聚合周期内毫无流动 = 断开生效。
                    if (countItems(targetChest, Items.COAL) != frozenCount[0]) {
                        helper.fail("Flow must stop across a disabled side");
                    }
                    runtime.setSideIntent(middle, Direction.EAST, PipeSideIntent.AUTO);
                    PipeNetwork<?> rejoined = runtime.networkAt(middle);
                    if (rejoined == null || rejoined.nodeCount() != 3) {
                        helper.fail("Re-enabling should rejoin the 3-node component");
                    }
                })
                .thenWaitUntil(() -> {
                    if (countItems(targetChest, Items.COAL) <= frozenCount[0]) {
                        helper.fail("waiting for flow to resume after reconnecting");
                    }
                })
                .thenSucceed();
    }

    /** 测试 159:终极两端+基础中段,整条路被基础节点吞吐(8/t)钳制。 */
    private static void pipeBasicMidsectionCapsEliteEndpoints(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        fillChest(source, Items.COAL, 640);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime(helper).setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        // 终极批次预算 64x5=320,但基础中段未来逐 tick 仅 8 -> 每批被钳到 8x5=40。
        int cappedBatch = 8 * interval;

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) <= 0) {
                        helper.fail("waiting for the first batch");
                    }
                })
                .thenExecute(() -> {
                    int moved = countItems(target, Items.COAL);
                    if (moved != cappedBatch) {
                        helper.fail("A basic mid node must cap the batch to " + cappedBatch + ", moved " + moved);
                    }
                })
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) < cappedBatch * 2) {
                        helper.fail("waiting for the second batch");
                    }
                })
                .thenExecute(() -> {
                    int moved = countItems(target, Items.COAL);
                    if (moved != cappedBatch * 2) {
                        helper.fail("Second batch must also be capped, total " + moved);
                    }
                })
                .thenSucceed();
    }

    /** 测试 160:十字形双路线共享同一基础中段节点,合计流量不超其 8/t 额度。 */
    private static void pipeTwoExtractorsShareNodeBudget(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity sourceWest = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity sourceNorth = chest(helper, new BlockPos(2, 1, 0));
        ChestBlockEntity targetEast = chest(helper, new BlockPos(4, 1, 2));
        ChestBlockEntity targetSouth = chest(helper, new BlockPos(2, 1, 4));
        fillChest(sourceWest, Items.COAL, 27 * 64);
        fillChest(sourceNorth, Items.IRON_INGOT, 27 * 64);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractorWest = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos extractorNorth = helper.absolutePos(new BlockPos(2, 1, 1));
        runtime.setSideIntent(extractorWest, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setSideIntent(extractorNorth, Direction.NORTH, PipeSideIntent.EXTRACT);
        // 公平性场景:两入口取不同聚合周期(5t 与 40t),共享同一基础中段(8/t)。
        useMinInterval(helper, extractorWest, Direction.WEST);
        runtime.setExtractConfig(extractorNorth, Direction.NORTH,
                runtime.portConfig(extractorNorth, Direction.NORTH).withInterval(40));
        long[] baseline = new long[3];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (routeTotals(targetEast, targetSouth)[0] <= 0) {
                        helper.fail("waiting for first deliveries");
                    }
                })
                .thenExecute(() -> {
                    long[] totals = routeTotals(targetEast, targetSouth);
                    baseline[0] = totals[0];
                    baseline[1] = totals[1];
                    baseline[2] = totals[2];
                })
                .thenExecuteAfter(120, () -> {
                    long[] totals = routeTotals(targetEast, targetSouth);
                    long delta = totals[0] - baseline[0];
                    // 共享基础节点(8/t)的未来账本钳制:120 tick 窗口的合计平均不超 8/t
                    // (允许一个批次的边沿余量),且两条路线都持续获得份额(无饿死)。
                    if (delta > 8L * 120 + 8L * 40 || delta < 8L * 120 / 2) {
                        helper.fail("Shared basic node must meter combined flow near 8/t, delta=" + delta);
                    }
                    long coalDelta = totals[1] - baseline[1];
                    long ironDelta = totals[2] - baseline[2];
                    if (coalDelta <= 0 || ironDelta <= 0) {
                        helper.fail("Both routes must keep receiving (fairness), coal=" + coalDelta + " iron=" + ironDelta);
                    }
                })
                .thenSucceed();
    }

    /** 测试 289:双口同周期(5t)共享基础中段(8/t)——批内份额记账下谁也偷不走对方的 4/t 公平片。 */
    private static void pipeTwoPortsKeepFairSliceOnSharedTrunk(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity sourceWest = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity sourceNorth = chest(helper, new BlockPos(2, 1, 0));
        ChestBlockEntity targetEast = chest(helper, new BlockPos(4, 1, 2));
        ChestBlockEntity targetSouth = chest(helper, new BlockPos(2, 1, 4));
        fillChest(sourceWest, Items.COAL, 27 * 64);
        fillChest(sourceNorth, Items.IRON_INGOT, 27 * 64);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractorWest = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos extractorNorth = helper.absolutePos(new BlockPos(2, 1, 1));
        runtime.setSideIntent(extractorWest, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setSideIntent(extractorNorth, Direction.NORTH, PipeSideIntent.EXTRACT);
        // 与测试 160 的区别:两口同取最小聚合档(5t),份额窃取会让后相位口永久饿死。
        useMinInterval(helper, extractorWest, Direction.WEST);
        useMinInterval(helper, extractorNorth, Direction.NORTH);
        long[] baseline = new long[2];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long[] totals = fairTotals(targetEast, targetSouth);
                    if (totals[0] <= 0 || totals[1] <= 0) {
                        helper.fail("waiting for both ports' first deliveries (coal=" + totals[0] + " iron=" + totals[1] + ")");
                    }
                })
                .thenExecute(() -> {
                    long[] totals = fairTotals(targetEast, targetSouth);
                    baseline[0] = totals[0];
                    baseline[1] = totals[1];
                })
                .thenExecuteAfter(120, () -> {
                    long[] totals = fairTotals(targetEast, targetSouth);
                    long coal = totals[0] - baseline[0];
                    long iron = totals[1] - baseline[1];
                    // 公平片 4/t × 120t = 480 ± 两批(40)相位余量;窃取世界一方≈960、另一方≈0。
                    if (coal < 440 || coal > 520 || iron < 440 || iron > 520) {
                        helper.fail("Each port must hold exactly its fair slice (480±40): coal=" + coal + " iron=" + iron);
                    }
                })
                .thenSucceed();
    }

    /** {煤合计(西口), 铁合计(北口)} 跨两目标箱按物品归口统计。 */
    private static long[] fairTotals(ChestBlockEntity east, ChestBlockEntity south) {
        return new long[] {
                countItems(east, Items.COAL) + countItems(south, Items.COAL),
                countItems(east, Items.IRON_INGOT) + countItems(south, Items.IRON_INGOT) };
    }

    /** {合计, 煤(西路线), 铁(北路线)} 跨两目标箱合计。 */
    private static long[] routeTotals(ChestBlockEntity east, ChestBlockEntity south) {
        long coal = countItems(east, Items.COAL) + countItems(south, Items.COAL);
        long iron = countItems(east, Items.IRON_INGOT) + countItems(south, Items.IRON_INGOT);
        return new long[] { coal + iron, coal, iron };
    }

    /** 测试 161:平均分配策略在 T 形两目的地各半填充。 */
    private static void pipeEqualSplitStrategySplits(GameTestHelper helper) {
        ChestBlockEntity[] chests = placeTee(helper, BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        PipeLevelRuntime runtime = runtime(helper);
        runtime.setExtractStrategy(extractor, Direction.WEST, BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        int half = 4 * interval / 2;

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(chests[0], Items.COAL) + countItems(chests[1], Items.COAL) < half * 2) {
                        helper.fail("waiting for the first batch");
                    }
                })
                .thenExecute(() -> {
                    int north = countItems(chests[0], Items.COAL);
                    int south = countItems(chests[1], Items.COAL);
                    if (north != half || south != half) {
                        helper.fail("Equal split must halve each batch, got north=" + north + " south=" + south);
                    }
                })
                .thenSucceed();
    }

    /** 测试 162:轮询策略游标轮转,两目的地长期均衡。 */
    private static void pipeRoundRobinStrategyServesBoth(GameTestHelper helper) {
        ChestBlockEntity[] chests = placeTee(helper, BuiltinTopoPipes.ITEM_PIPE_ELITE);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime(helper).setExtractStrategy(extractor, Direction.WEST, BuiltinTopoPipeDistributionStrategies.ROUND_ROBIN);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        int batch = 16 * interval;

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(chests[0], Items.COAL) + countItems(chests[1], Items.COAL) < batch * 2) {
                        helper.fail("waiting for two alternating batches");
                    }
                })
                .thenExecute(() -> {
                    int north = countItems(chests[0], Items.COAL);
                    int south = countItems(chests[1], Items.COAL);
                    if (north != batch || south != batch) {
                        helper.fail("Round robin must alternate whole batches, got north=" + north + " south=" + south);
                    }
                })
                .thenSucceed();
    }

    /** 测试 164:标量三连——能量/高级能量/热各自经基础管在缓冲机间搬运。 */
    private static void pipeScalarPipesMoveAllThree(GameTestHelper helper) {
        placeFloor(helper);
        scalarRow(helper, 0, TopoPipeGameTestFixtures.energyBuffer(), BuiltinTopoPipes.ENERGY_PIPE_BASIC,
                ScalarResourcePort.ENERGY_STORAGE, BuiltinTopoResourceIntegrations.ENERGY);
        scalarRow(helper, 2, TopoPipeGameTestFixtures.advancedEnergyBuffer(), BuiltinTopoPipes.ADVANCED_ENERGY_PIPE_BASIC,
                ScalarResourcePort.ADVANCED_ENERGY_STORAGE, BuiltinTopoResourceIntegrations.ADVANCED_ENERGY);
        scalarRow(helper, 4, TopoPipeGameTestFixtures.heatBuffer(), BuiltinTopoPipes.HEAT_PIPE_BASIC,
                ScalarResourcePort.HEAT_STORAGE, BuiltinTopoResourceIntegrations.HEAT);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    // BASIC 标量管固定 40t 聚合:等三行的首批全部落地(各自相位不同)。
                    assertScalarMoved(helper, 0, ScalarResourcePort.ENERGY_STORAGE, 256 * 40, "energy");
                    assertScalarMoved(helper, 2, ScalarResourcePort.ADVANCED_ENERGY_STORAGE, 64 * 40, "advanced energy");
                    assertScalarMoved(helper, 4, ScalarResourcePort.HEAT_STORAGE, 128 * 40, "heat");
                })
                .thenSucceed();
    }

    /** 测试 165:基础流体管在储罐机之间搬运水(50mB/t)。 */
    private static void pipeFluidPipeMovesWater(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(0, 1, 2), TopoPipeGameTestFixtures.fluidTank());
        placeMachine(helper, new BlockPos(3, 1, 2), TopoPipeGameTestFixtures.fluidTank());
        insertResource(helper, source.machineComponents().require(FluidResourcePort.FLUID_STORAGE).handler(),
                FluidResource.of(Fluids.WATER), 8000, "mB water");
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.FLUID_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.FLUID_PIPE_BASIC);
        runtime(helper).setSideIntent(helper.absolutePos(new BlockPos(1, 1, 2)), Direction.WEST, PipeSideIntent.EXTRACT);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    MachineBlockEntity target = helper.getBlockEntity(new BlockPos(3, 1, 2), MachineBlockEntity.class);
                    if (target.machineComponents().require(FluidResourcePort.FLUID_STORAGE).handler().getAmountAsLong(0) <= 0) {
                        helper.fail("waiting for the first fluid batch");
                    }
                })
                .thenExecute(() -> {
                    MachineBlockEntity target = helper.getBlockEntity(new BlockPos(3, 1, 2), MachineBlockEntity.class);
                    long amount = target.machineComponents().require(FluidResourcePort.FLUID_STORAGE).handler().getAmountAsLong(0);
                    // BASIC 流体管固定 40t 聚合:批次 = 50mB/t x 40 = 2000mB。
                    if (amount != 2000) {
                        helper.fail("First fluid batch must be exactly 2000mB, moved " + amount);
                    }
                })
                .thenSucceed();
    }

    /** 测试 166:破坏中段管道——掉落自身、SavedData 节点剔除、网络拆为两个 1 节点分量。 */
    private static void pipeBrokenPipePrunesNodeAndSplits(GameTestHelper helper) {
        placeFloor(helper);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_BASIC);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos middle = helper.absolutePos(new BlockPos(2, 1, 2));

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    PipeNetwork<?> network = runtime.networkAt(middle);
                    if (network == null || network.nodeCount() != 3) {
                        helper.fail("Setup: expected one 3-node component");
                    }
                    // destroy WITH drops (helper.destroyBlock suppresses loot).
                    helper.getLevel().destroyBlock(middle, true);
                })
                .thenExecuteAfter(2, () -> {
                    if (runtime.data().node(middle.asLong()) != null) {
                        helper.fail("Breaking a pipe must prune its saved-data node");
                    }
                    PipeNetwork<?> left = runtime.networkAt(helper.absolutePos(new BlockPos(1, 1, 2)));
                    PipeNetwork<?> right = runtime.networkAt(helper.absolutePos(new BlockPos(3, 1, 2)));
                    if (left == null || left.nodeCount() != 1 || right == null || right.nodeCount() != 1) {
                        helper.fail("Breaking the middle pipe must split into two 1-node components");
                    }
                    helper.assertItemEntityCountIs(
                            BuiltinTopoPipes.ITEM_PIPE_BASIC.registeredBlock().get().asItem(),
                            new BlockPos(2, 1, 2), 2.0, 1);
                })
                .thenSucceed();
    }

    /** 测试 167:SavedData codec 往返——意图、角色、策略端口配置逐位保真。 */
    private static void pipeSavedDataCodecRoundtrip(GameTestHelper helper) {
        placeFloor(helper);
        chest(helper, new BlockPos(0, 1, 2));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos first = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime.setSideIntent(first, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setSideIntent(helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, PipeSideIntent.DISABLED);
        runtime.setExtractConfig(first, Direction.WEST, new BuiltinTopoPipeDistributionStrategies.RateConfig(
                BuiltinTopoPipeDistributionStrategies.ROUND_ROBIN, 17, 15));
        // 周期量 17 故意不整除 15t——亚每 tick 速率(≈1.13/t)是新语义的核心能力,必须保真往返。

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    PipeNetworksSavedData data = runtime.data();
                    Tag encoded = PipeNetworksSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                            .getOrThrow(message -> new IllegalStateException("encode failed: " + message));
                    PipeNetworksSavedData decoded = PipeNetworksSavedData.CODEC.parse(NbtOps.INSTANCE, encoded)
                            .getOrThrow(message -> new IllegalStateException("decode failed: " + message));
                    var original = data.node(first.asLong());
                    var roundTripped = decoded.node(first.asLong());
                    if (original == null || roundTripped == null) {
                        helper.fail("Round trip lost the pipe node");
                        return;
                    }
                    if (roundTripped.intentBits() != original.intentBits() || roundTripped.roleBits() != original.roleBits() || roundTripped.definition() != original.definition()) {
                        helper.fail("Round trip changed intent/roles/definition");
                    }
                    if (!(roundTripped.extractConfig(Direction.WEST) instanceof BuiltinTopoPipeDistributionStrategies.RateConfig rate) || rate.strategy() != BuiltinTopoPipeDistributionStrategies.ROUND_ROBIN || rate.amount() != 17 || rate.interval() != 15) {
                        helper.fail("Round trip changed the port config");
                    }
                })
                .thenSucceed();
    }

    /** 测试 168:标量版均分——干线+双分支拓扑下,平均分配把能量对半灌进两台缓冲机。 */
    private static void pipeEqualSplitBalancesScalarMachines(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(1, 1, 2), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 0), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 4), TopoPipeGameTestFixtures.energyBuffer());
        insertResource(helper, source.machineComponents().require(ScalarResourcePort.ENERGY_STORAGE).handler(),
                BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(),
                TopoPipeGameTestFixtures.SCALAR_CAPACITY, "energy");
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setExtractStrategy(extractor, Direction.WEST, BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        long half = BuiltinTopoPipes.ENERGY_PIPE_ELITE.maxBatchAmount(interval) / 2L;

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long total = scalarAmount(helper, new BlockPos(2, 1, 0)) + scalarAmount(helper, new BlockPos(2, 1, 4));
                    if (total < half * 2) {
                        helper.fail("waiting for the first batch");
                    }
                })
                .thenExecute(() -> {
                    long north = scalarAmount(helper, new BlockPos(2, 1, 0));
                    long south = scalarAmount(helper, new BlockPos(2, 1, 4));
                    if (north != half || south != half) {
                        helper.fail("Equal split must halve each batch, got north=" + north + " south=" + south);
                    }
                })
                .thenSucceed();
    }

    /** 测试 169:终极抽取端(131k/t)穿基础共享干线(2048/t)均分到两台机器——饱和瓶颈轮替。 */
    private static void pipeEqualSplitSharesSaturatedBottleneck(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(0, 1, 2), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 0), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 4), TopoPipeGameTestFixtures.energyBuffer());
        insertResource(helper, source.machineComponents().require(ScalarResourcePort.ENERGY_STORAGE).handler(),
                BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(),
                TopoPipeGameTestFixtures.SCALAR_CAPACITY, "energy");
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ENERGY_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ENERGY_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ENERGY_PIPE_BASIC);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setExtractStrategy(extractor, Direction.WEST, BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        // 饱和共享干线:每批被共享基础节点窗口钳到 2048 x interval,整批轮替到一侧。
        long saturatedBatch = 2048L * interval;

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long total = scalarAmount(helper, new BlockPos(2, 1, 0)) + scalarAmount(helper, new BlockPos(2, 1, 4));
                    if (total < saturatedBatch * 4) {
                        helper.fail("waiting for four saturated batches");
                    }
                })
                .thenExecute(() -> {
                    long north = scalarAmount(helper, new BlockPos(2, 1, 0));
                    long south = scalarAmount(helper, new BlockPos(2, 1, 4));
                    if (north < saturatedBatch || south < saturatedBatch) {
                        helper.fail("Rotation must serve both sides of the saturated bottleneck, got north=" + north + " south=" + south);
                    }
                    if (Math.abs(north - south) > saturatedBatch) {
                        helper.fail("Rotation should average the split, got north=" + north + " south=" + south);
                    }
                })
                .thenSucceed();
    }

    /** 测试 170:稀缺供给单 tick 真均分——源只有 30 能量、预算 131k,两台机器各 15。 */
    private static void pipeEqualSplitDividesScarceSupply(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(1, 1, 2), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 0), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 4), TopoPipeGameTestFixtures.energyBuffer());
        insertResource(helper, source.machineComponents().require(ScalarResourcePort.ENERGY_STORAGE).handler(),
                BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(), 30, "energy");
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setExtractStrategy(extractor, Direction.WEST, BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT);
        useMinInterval(helper, extractor, Direction.WEST);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long total = scalarAmount(helper, new BlockPos(2, 1, 0)) + scalarAmount(helper, new BlockPos(2, 1, 4));
                    if (total < 30) {
                        helper.fail("waiting for the scarce batch");
                    }
                })
                .thenExecute(() -> {
                    long north = scalarAmount(helper, new BlockPos(2, 1, 0));
                    long south = scalarAmount(helper, new BlockPos(2, 1, 4));
                    if (north != 15 || south != 15) {
                        helper.fail("A scarce 30-energy supply must split 15/15 within one batch, got north=" + north + " south=" + south);
                    }
                })
                .thenSucceed();
    }

    /** 测试 191:四目的地其一缓冲已满——供给 30 在三台活机器间 10/10/10,死目的地不吃份额。 */
    private static void pipeEqualSplitSkipsDeadDestinations(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(1, 1, 2), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 0), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(2, 1, 4), TopoPipeGameTestFixtures.energyBuffer());
        placeMachine(helper, new BlockPos(4, 1, 2), TopoPipeGameTestFixtures.energyBuffer());
        MachineBlockEntity dead = placeMachine(helper, new BlockPos(2, 2, 2), TopoPipeGameTestFixtures.energyBuffer());
        insertResource(helper, source.machineComponents().require(ScalarResourcePort.ENERGY_STORAGE).handler(),
                BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(), 30, "energy");
        insertResource(helper, dead.machineComponents().require(ScalarResourcePort.ENERGY_STORAGE).handler(),
                BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(),
                TopoPipeGameTestFixtures.SCALAR_CAPACITY, "energy (fills the dead buffer)");
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 3), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ENERGY_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setExtractStrategy(extractor, Direction.WEST, BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT);
        useMinInterval(helper, extractor, Direction.WEST);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long total = scalarAmount(helper, new BlockPos(2, 1, 0)) + scalarAmount(helper, new BlockPos(2, 1, 4)) + scalarAmount(helper, new BlockPos(4, 1, 2));
                    if (total < 30) {
                        helper.fail("waiting for the scarce batch");
                    }
                })
                .thenExecute(() -> {
                    long north = scalarAmount(helper, new BlockPos(2, 1, 0));
                    long south = scalarAmount(helper, new BlockPos(2, 1, 4));
                    long east = scalarAmount(helper, new BlockPos(4, 1, 2));
                    long deadAmount = scalarAmount(helper, new BlockPos(2, 2, 2));
                    if (north != 10 || south != 10 || east != 10) {
                        helper.fail("30 energy over three live machines must land 10/10/10, got " + north + "/" + south + "/" + east);
                    }
                    if (deadAmount != TopoPipeGameTestFixtures.SCALAR_CAPACITY) {
                        helper.fail("The full buffer must stay untouched, got " + deadAmount);
                    }
                })
                .thenSucceed();
    }

    private static long scalarAmount(GameTestHelper helper, BlockPos pos) {
        MachineBlockEntity machine = helper.getBlockEntity(pos, MachineBlockEntity.class);
        return machine.machineComponents().require(ScalarResourcePort.ENERGY_STORAGE).handler().getAmountAsLong(0);
    }

    /** 测试 192:物品 机器→管→机器(免事务直搬通道,消费端是 INSERT-only 方向视图):40 煤完整到达。 */
    private static void pipeItemMachineToMachineDirectLane(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(1, 1, 2), TopoPipeGameTestFixtures.itemBuffer());
        MachineBlockEntity sink = placeMachine(helper, new BlockPos(4, 1, 2), TopoPipeGameTestFixtures.itemSink());
        insertResource(helper, source.machineComponents().require(
                net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler(),
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COAL), 40, "coal");
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime(helper).setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        useMinInterval(helper, extractor, Direction.WEST);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    long arrived = handlerTotal(sink.machineComponents().require(
                            net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler());
                    if (arrived < 40) {
                        helper.fail("waiting for the machine-to-machine batch");
                    }
                })
                .thenExecute(() -> {
                    long arrived = handlerTotal(sink.machineComponents().require(
                            net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler());
                    long left = handlerTotal(source.machineComponents().require(
                            net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler());
                    if (arrived != 40 || left != 0) {
                        helper.fail("Expected 40 coal moved machine-to-machine, got arrived=" + arrived + " left=" + left);
                    }
                })
                .thenSucceed();
    }

    /**
     * 测试 193:混类源+挑剔目标的直搬语义等价——源前排石头(目标无处收)卡住直搬循环后,
     * 事务余量补完仍把后排的煤送达;石头原地不动,无凭空增减。
     */
    private static void pipeMixedKindSourceFallsBack(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(1, 1, 2), TopoPipeGameTestFixtures.itemBuffer());
        MachineBlockEntity sink = placeMachine(helper, new BlockPos(4, 1, 2), TopoPipeGameTestFixtures.itemBuffer());
        ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> sourceHandler = source.machineComponents().require(
                net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler();
        ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> sinkHandler = sink.machineComponents().require(
                net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler();
        // 源:槽序前排石头、后排煤。目标:9 槽灌到只剩 32 煤位、零空槽 -> freeFor(石头)=0。
        insertResource(helper, sourceHandler,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STONE), 16, "stone");
        insertResource(helper, sourceHandler,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COAL), 16, "coal");
        insertResource(helper, sinkHandler,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COAL), 8 * 64 + 32, "coal prefill");
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime(helper).setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        useMinInterval(helper, extractor, Direction.WEST);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (handlerTotal(sinkHandler) < 8 * 64 + 32 + 16) {
                        helper.fail("waiting for the coal behind the stone");
                    }
                })
                .thenExecute(() -> {
                    long sinkTotal = handlerTotal(sinkHandler);
                    long sourceTotal = handlerTotal(sourceHandler);
                    if (sinkTotal != 8 * 64 + 32 + 16) {
                        helper.fail("Coal behind the stuck stone must still arrive, sink=" + sinkTotal);
                    }
                    if (sourceTotal != 16) {
                        helper.fail("Stone must stay in the source untouched, source=" + sourceTotal);
                    }
                })
                .thenSucceed();
    }

    private static long handlerTotal(ResourceHandler<?> handler) {
        long total = 0;
        for (int index = 0; index < handler.size(); index++) {
            total += handler.getAmountAsLong(index);
        }
        return total;
    }

    /** 测试 194:按距离策略的"最远优先"——不对称双目的地,每批整批落最远端,近端为零。 */
    private static void pipeByDistanceFarthest(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(1, 1, 2));
        // 近目的地直贴抽取节点(距离 1);远目的地隔一根北分支管(距离 2)。
        ChestBlockEntity near = chest(helper, new BlockPos(3, 1, 2));
        ChestBlockEntity far = chest(helper, new BlockPos(2, 1, 0));
        fillChest(source, Items.COAL, 27 * 64);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(2, 1, 1), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        runtime.setExtractConfig(extractor, Direction.WEST, new BuiltinTopoPipeDistributionStrategies.ByDistanceConfig(
                BuiltinTopoPipeDistributionStrategies.BY_DISTANCE,
                BuiltinTopoPipeDistributionStrategies.DistanceOrder.FARTHEST,
                20, 5));
        int batch = 20;

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(far, Items.COAL) < batch) {
                        helper.fail("waiting for the farthest-first batch");
                    }
                })
                .thenExecute(() -> {
                    if (countItems(near, Items.COAL) != 0) {
                        helper.fail("FARTHEST order must leave the near destination empty, got " + countItems(near, Items.COAL));
                    }
                    if (countItems(far, Items.COAL) % batch != 0) {
                        helper.fail("Farthest deliveries must arrive in whole batches of " + batch);
                    }
                })
                .thenSucceed();
    }

    /**
     * 测试 195:白名单——进阶物品管 箱→箱,源混装铁锭+泥土,白名单只放行铁锭;非法串被拒,
     * 泥土在追加两个批次窗口后仍原地不动。
     */
    private static void pipeWhitelistOnlyMovesListedItem(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        source.setItem(0, new ItemStack(Items.DIRT, 64));
        source.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        runtime.uiAddFilterEntry(extractor, Direction.WEST, true, "minecraft:iron_ingot");
        runtime.uiAddFilterEntry(extractor, Direction.WEST, true, "not a valid id!!");
        var filter = runtime.portFilter(extractor, Direction.WEST);
        if (!filter.whitelist().equals(java.util.List.of("minecraft:iron_ingot"))) {
            helper.fail("Whitelist must hold exactly the valid entry, got " + filter.whitelist());
        }

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.IRON_INGOT) < 64) {
                        helper.fail("waiting for the whitelisted iron");
                    }
                })
                .thenExecuteAfter(2 * interval + 5, () -> {
                    if (countItems(target, Items.DIRT) != 0) {
                        helper.fail("Unlisted dirt must never move, target has " + countItems(target, Items.DIRT));
                    }
                    if (countItems(source, Items.DIRT) != 64) {
                        helper.fail("Dirt must stay in the source untouched");
                    }
                    if (countItems(source, Items.IRON_INGOT) != 0) {
                        helper.fail("All whitelisted iron must drain from the source");
                    }
                })
                .thenSucceed();
    }

    /** 测试 196:黑名单——源混装泥土+煤,黑名单拦泥土,煤照常全量到达。 */
    private static void pipeBlacklistBlocksListedItem(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        source.setItem(0, new ItemStack(Items.DIRT, 64));
        source.setItem(1, new ItemStack(Items.COAL, 64));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        runtime.uiAddFilterEntry(extractor, Direction.WEST, false, "minecraft:dirt");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.COAL) < 64) {
                        helper.fail("waiting for the unlisted coal");
                    }
                })
                .thenExecuteAfter(2 * interval + 5, () -> {
                    if (countItems(target, Items.DIRT) != 0) {
                        helper.fail("Blacklisted dirt must never move");
                    }
                    if (countItems(source, Items.DIRT) != 64) {
                        helper.fail("Blacklisted dirt must stay in the source");
                    }
                })
                .thenSucceed();
    }

    /** 测试 197:白黑同时生效——白名单(铁锭+金锭)先筛、黑名单(金锭)后剔,只有铁锭移动。 */
    private static void pipeWhitelistAndBlacklistCombine(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        source.setItem(0, new ItemStack(Items.GOLD_INGOT, 32));
        source.setItem(1, new ItemStack(Items.IRON_INGOT, 32));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        runtime.uiAddFilterEntry(extractor, Direction.WEST, true, "minecraft:iron_ingot");
        runtime.uiAddFilterEntry(extractor, Direction.WEST, true, "minecraft:gold_ingot");
        runtime.uiAddFilterEntry(extractor, Direction.WEST, false, "minecraft:gold_ingot");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.IRON_INGOT) < 32) {
                        helper.fail("waiting for the whitelisted-only iron");
                    }
                })
                .thenExecuteAfter(2 * interval + 5, () -> {
                    if (countItems(target, Items.GOLD_INGOT) != 0) {
                        helper.fail("Gold is whitelisted but also blacklisted - it must not move");
                    }
                    if (countItems(source, Items.GOLD_INGOT) != 32) {
                        helper.fail("Blacklisted gold must stay in the source");
                    }
                })
                .thenSucceed();
    }

    /** 测试 198:#tag 条目——白名单 #minecraft:planks,橡木板命中标签移动,泥土不动。 */
    private static void pipeTagFilterMatchesItemTag(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 2));
        source.setItem(0, new ItemStack(Items.DIRT, 64));
        source.setItem(1, new ItemStack(Items.OAK_PLANKS, 64));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ADVANCED);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        runtime.uiAddFilterEntry(extractor, Direction.WEST, true, "#minecraft:planks");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (countItems(target, Items.OAK_PLANKS) < 64) {
                        helper.fail("waiting for the tag-matched planks");
                    }
                })
                .thenExecuteAfter(2 * interval + 5, () -> {
                    if (countItems(target, Items.DIRT) != 0) {
                        helper.fail("Dirt does not match #minecraft:planks and must stay");
                    }
                })
                .thenSucceed();
    }

    /**
     * 测试 199:过滤×直搬语义等价——机器→终极管→机器走免事务直搬,源前排石头被白名单滤掉
     * 卡住直搬循环,事务余量补完仍把后排白名单煤送达;石头原地不动。
     */
    private static void pipeFilteredDirectLaneFallsBack(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity source = placeMachine(helper, new BlockPos(1, 1, 2), TopoPipeGameTestFixtures.itemBuffer());
        MachineBlockEntity sink = placeMachine(helper, new BlockPos(4, 1, 2), TopoPipeGameTestFixtures.itemBuffer());
        ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> sourceHandler = source.machineComponents().require(
                net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler();
        ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> sinkHandler = sink.machineComponents().require(
                net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort.ITEM_INPUT_1).handler();
        insertResource(helper, sourceHandler,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STONE), 16, "stone");
        insertResource(helper, sourceHandler,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COAL), 16, "coal");
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinTopoPipes.ITEM_PIPE_ELITE);
        PipeLevelRuntime runtime = runtime(helper);
        BlockPos extractor = helper.absolutePos(new BlockPos(2, 1, 2));
        runtime.setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        int interval = useMinInterval(helper, extractor, Direction.WEST);
        runtime.uiAddFilterEntry(extractor, Direction.WEST, true, "minecraft:coal");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (handlerTotal(sinkHandler) < 16) {
                        helper.fail("waiting for the whitelisted coal behind the stone");
                    }
                })
                .thenExecuteAfter(2 * interval + 5, () -> {
                    long sinkTotal = handlerTotal(sinkHandler);
                    long sourceTotal = handlerTotal(sourceHandler);
                    if (sinkTotal != 16) {
                        helper.fail("Exactly the 16 whitelisted coal must arrive, sink=" + sinkTotal);
                    }
                    if (sourceTotal != 16) {
                        helper.fail("Filtered-out stone must stay in the source, source=" + sourceTotal);
                    }
                })
                .thenSucceed();
    }

    // --- helpers ------------------------------------------------------------------------------

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

    private static MachineBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
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

    private static void assertVisual(GameTestHelper helper, BlockPos pos, Direction side, PipeSideVisual expected) {
        var state = helper.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock)) {
            helper.fail("Expected a pipe block at " + pos);
            return;
        }
        PipeSideVisual actual = PipeBlock.visual(state, side);
        if (actual != expected) {
            helper.fail("Expected " + side + " visual " + expected + " but found " + actual);
        }
    }

    /** T 形:西箱(源)+三根同级物品管+南北箱(双目的地);返回 {北箱, 南箱}。 */
    private static ChestBlockEntity[] placeTee(GameTestHelper helper, PipeDefinition pipe) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(1, 1, 2));
        ChestBlockEntity north = chest(helper, new BlockPos(2, 1, 0));
        ChestBlockEntity south = chest(helper, new BlockPos(2, 1, 4));
        fillChest(source, Items.COAL, 27 * 64);
        placePipe(helper, new BlockPos(2, 1, 2), pipe);
        placePipe(helper, new BlockPos(2, 1, 1), pipe);
        placePipe(helper, new BlockPos(2, 1, 3), pipe);
        runtime(helper).setSideIntent(helper.absolutePos(new BlockPos(2, 1, 2)), Direction.WEST, PipeSideIntent.EXTRACT);
        return new ChestBlockEntity[] { north, south };
    }

    private static void scalarRow(
                                  GameTestHelper helper,
                                  int z,
                                  MachineDefinition buffer,
                                  PipeDefinition pipe,
                                  ComponentKey<ScalarResourcePort> key,
                                  BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration) {
        MachineBlockEntity source = placeMachine(helper, new BlockPos(0, 1, z), buffer);
        placeMachine(helper, new BlockPos(3, 1, z), buffer);
        insertResource(helper, source.machineComponents().require(key).handler(),
                integration.recipeCapability().resource(), TopoPipeGameTestFixtures.SCALAR_CAPACITY / 2,
                integration.id().toString());
        placePipe(helper, new BlockPos(1, 1, z), pipe);
        placePipe(helper, new BlockPos(2, 1, z), pipe);
        runtime(helper).setSideIntent(helper.absolutePos(new BlockPos(1, 1, z)), Direction.WEST, PipeSideIntent.EXTRACT);
    }

    private static void assertScalarMoved(
                                          GameTestHelper helper, int z, ComponentKey<ScalarResourcePort> key, int minimum, String what) {
        MachineBlockEntity target = helper.getBlockEntity(new BlockPos(3, 1, z), MachineBlockEntity.class);
        long amount = target.machineComponents().require(key).handler().getAmountAsLong(0);
        if (amount < minimum) {
            helper.fail("Expected at least " + minimum + " " + what + " moved in ~10t, found " + amount);
        }
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
            throw new IllegalArgumentException("Pipe network GameTest index out of range: " + index);
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
