package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.resource.DirectSlotResourceAccess;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.machine.ui.ComponentCollector;
import net.ptcrys.topo.api.machine.ui.MachineUiFrameTemplate;
import net.ptcrys.topo.api.machine.ui.PageCollector;
import net.ptcrys.topo.data.machine.BuiltinTopoMeMachines;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.integration.ae2.AeConfigSlot;
import net.ptcrys.topo.integration.ae2.AeFluidBufferPort;
import net.ptcrys.topo.integration.ae2.AeGridNode;
import net.ptcrys.topo.integration.ae2.AeItemBufferPort;
import net.ptcrys.topo.integration.ae2.AeItemInputSync;
import net.ptcrys.topo.integration.ae2.AePatternProvider;
import net.ptcrys.topo.integration.ae2.AeStockingFluidPort;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Functional GameTests for the migrated ME hatches: every test builds a real AE2 grid
 * (creative energy cell + prefilled drive placed adjacent to the machine — AE2 grid nodes of
 * neighbouring blocks connect directly) and asserts end-to-end behaviour through the machine's
 * recipe-side handlers and the live network inventory.
 */
public final class MeHatchGameTests {

    private static final String SUITE = "me_hatch";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 14;
    /** Drawing/push cadences are 40 ticks and AE grids boot asynchronously; allow several cycles. */
    private static final int TEST_TIMEOUT_TICKS = 400;

    private MeHatchGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("me_hatch"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "me_grid_node_comes_online_on_powered_network",
                "Tests AeGridNode and AeIntegration: a drawing bus next to a creative energy cell " + "creates an in-world grid node, exposes IN_WORLD_GRID_NODE_HOST, and reports online.",
                MeHatchGameTests::meGridNodeComesOnlineOnPoweredNetwork);
        register(
                event,
                environment,
                index++,
                "me_drawing_item_bus_pulls_to_target_and_stops",
                "Tests AeItemInputSync, AeConfiguredResourceSync, and ItemResourcePort: a slot configured " + "to 32 iron pulls exactly 32 from the network drive into the local cache and stops there.",
                MeHatchGameTests::meDrawingItemBusPullsToTargetAndStops);
        register(
                event,
                environment,
                index++,
                "me_direct_fluid_hatch_exposes_network_stock_to_recipe_side",
                "Tests AeStockingFluidPort and AeStockingResourceHandler: the recipe INPUT handler reports " + "the configured window of network water, and a committed extract debits the network.",
                MeHatchGameTests::meDirectFluidHatchExposesNetworkStockToRecipeSide);
        register(
                event,
                environment,
                index++,
                "me_direct_hatch_rollback_refunds_network",
                "Tests AeStockingResourceHandler transaction journal: aborting a transaction that extracted " + "water refunds the network in full with no pending refund left behind.",
                MeHatchGameTests::meDirectHatchRollbackRefundsNetwork);
        register(
                event,
                environment,
                index++,
                "me_pattern_provider_receives_pushed_pattern_inputs",
                "Tests AePatternProvider, AePatternInventory, and the buffer ports: a pushed processing " + "pattern lands its item+fluid inputs in the local buffers atomically and isBusy gates " + "further pushes until the buffers drain.",
                MeHatchGameTests::mePatternProviderReceivesPushedPatternInputs);
        register(
                event,
                environment,
                index++,
                "me_export_hatch_pushes_buffered_outputs_to_network",
                "Tests AeItemBufferPort, AeFluidBufferPort, and AeResourceOutputPush: recipe-side " + "OUTPUT inserts of iron and water are pushed into the network drives and the buffers empty.",
                MeHatchGameTests::meExportHatchPushesBufferedOutputsToNetwork);
        register(
                event,
                environment,
                index++,
                "me_pattern_input_plan_aggregates_without_transaction_probe",
                "Tests the pattern input planner with real AE item keys and a stack handler: repeated inputs " + "are aggregated across existing and empty slots without transactional insertion probes.",
                MeHatchGameTests::mePatternInputPlanAggregatesWithoutTransactionProbe);
        register(
                event,
                environment,
                index++,
                "me_pattern_input_plan_matches_constrained_large_handler",
                "Tests exact empty-slot competition in a large item handler: a restricted resource keeps its " + "only valid slot while the bulk resource occupies every interchangeable slot.",
                MeHatchGameTests::mePatternInputPlanMatchesConstrainedLargeHandler);
        register(
                event,
                environment,
                index++,
                "me_pattern_capacity_failure_preserves_ae_inputs",
                "Tests a real pattern provider capacity failure: no buffer is partially changed, AE KeyCounters " + "are not published, and read-only capacity rejection never probes insert.",
                MeHatchGameTests::mePatternCapacityFailurePreservesAeInputs);
        register(
                event,
                environment,
                index++,
                "me_pattern_fluid_plan_commits_without_transaction_probe",
                "Tests real AE fluid keys and a fluid stack handler: one aggregate demand spans tanks through " + "direct commits without transactional insertion probes.",
                MeHatchGameTests::mePatternFluidPlanCommitsWithoutTransactionProbe);
        register(
                event,
                environment,
                index++,
                "me_pattern_nonstandard_stack_handler_respects_transactional_insert",
                "Tests the explicit direct-access boundary: a stack handler without Topo's capability commits " + "through a real transaction, respects custom insert semantics, and rolls back rejection.",
                MeHatchGameTests::mePatternNonstandardStackHandlerRespectsTransactionalInsert);
        register(
                event,
                environment,
                index++,
                "me_pattern_direct_commit_compensates_callback_failure",
                "Tests direct-commit compensation: a synthetic contents-changed failure restores every item " + "slot instead of leaving a partially published pattern input.",
                MeHatchGameTests::mePatternDirectCommitCompensatesCallbackFailure);
        register(
                event,
                environment,
                index++,
                "me_pattern_provider_persist_reload_suppresses_dirty_callback",
                "Tests a populated pattern provider save/load through Minecraft block-entity persistence: " + "APPLYING_PERSIST restores patterns without invoking business dirty writes.",
                MeHatchGameTests::mePatternProviderPersistReloadSuppressesDirtyCallback);
        register(
                event,
                environment,
                index,
                "me_pattern_provider_separated_mode_persists",
                "Tests that enabling per-pattern search pools writes the authoritative mode and restores it " + "through Minecraft block-entity persistence.",
                MeHatchGameTests::mePatternProviderSeparatedModePersists);
    }

    // ---- 069 ----

    private static void meGridNodeComesOnlineOnPoweredNetwork(GameTestHelper helper) {
        placeMeMachine(helper, BuiltinTopoMeMachines.ME_DRAWING_ITEM_INPUT_BUS);
        placeAeCreativeEnergyCell(helper, MACHINE_POS.west());

        helper.startSequence()
                .thenWaitUntil(() -> assertNetworkOnline(helper))
                .thenExecute(() -> {
                    var host = helper.getLevel().getCapability(
                            AECapabilities.IN_WORLD_GRID_NODE_HOST,
                            helper.absolutePos(MACHINE_POS));
                    if (host == null) {
                        helper.fail("Machine should expose IN_WORLD_GRID_NODE_HOST", MACHINE_POS);
                    }
                    if (host.getGridNode(null) == null) {
                        helper.fail("Exposed grid node host should carry a created grid node", MACHINE_POS);
                    }
                })
                .thenSucceed();
    }

    // ---- 070 ----

    private static void meDrawingItemBusPullsToTargetAndStops(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_DRAWING_ITEM_INPUT_BUS);
        placeAeCreativeEnergyCell(helper, MACHINE_POS.west());
        placeAeItemDrive(helper, MACHINE_POS.east(), Items.IRON_INGOT, 64);

        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        AEItemKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        machine.machineComponents().require(AeItemInputSync.AE_ITEM_INPUT_SYNC)
                .setConfigSlot(0, AeConfigSlot.of(iron, 32));
        ResourceHandler<ItemResource> localCache = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).handler();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (amountOf(localCache, iron) != 32L) {
                        helper.fail("Drawing bus should pull the configured 32 iron into its local cache, has " + amountOf(localCache, iron), MACHINE_POS);
                    }
                })
                .thenWaitUntil(() -> assertNetworkAmount(helper, ironKey, 32L,
                        "Network should be debited down to 32 iron after the pull"))
                .thenExecuteAfter(80, () -> {
                    // Two further sync cycles: the bus must hold at target, not keep pulling.
                    if (amountOf(localCache, iron) != 32L) {
                        helper.fail("Drawing bus must stop at its 32-iron target, has " + amountOf(localCache, iron), MACHINE_POS);
                    }
                    assertNetworkAmount(helper, ironKey, 32L,
                            "Network stock must stay at 32 iron once the target is reached");
                })
                .thenSucceed();
    }

    // ---- 071 ----

    private static void meDirectFluidHatchExposesNetworkStockToRecipeSide(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_DIRECT_FLUID_INPUT_HATCH);
        placeAeCreativeEnergyCell(helper, MACHINE_POS.west());
        placeAeFluidDrive(helper, MACHINE_POS.east(), Fluids.WATER, 16_000);

        FluidResource water = FluidResource.of(Fluids.WATER);
        AEFluidKey waterKey = AEFluidKey.of(Fluids.WATER);
        machine.machineComponents().require(AeStockingFluidPort.AE_STOCKING_FLUID_PORT)
                .setConfigSlot(0, AeConfigSlot.of(water, 8_000));
        ResourceHandler<FluidResource> recipeInput = requireRecipeFluidInput(helper, machine);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertNetworkOnline(helper);
                    if (amountOf(recipeInput, water) != 8_000L) {
                        helper.fail("Recipe INPUT handler should report the 8000 mB configured window, got " + amountOf(recipeInput, water), MACHINE_POS);
                    }
                })
                .thenExecute(() -> {
                    int extracted;
                    try (Transaction tx = Transaction.openRoot()) {
                        extracted = recipeInput.extract(water, 3_000, tx);
                        tx.commit();
                    }
                    if (extracted != 3_000) {
                        helper.fail("Committed extract should pull 3000 mB through the hatch, got " + extracted,
                                MACHINE_POS);
                    }
                    assertNetworkAmount(helper, waterKey, 13_000L,
                            "Network should be debited to 13000 mB after the committed extract");
                    if (amountOf(recipeInput, water) != 8_000L) {
                        helper.fail("Configured window should refill from remaining network stock, got " + amountOf(recipeInput, water), MACHINE_POS);
                    }
                })
                .thenSucceed();
    }

    // ---- 072 ----

    private static void meDirectHatchRollbackRefundsNetwork(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_DIRECT_FLUID_INPUT_HATCH);
        placeAeCreativeEnergyCell(helper, MACHINE_POS.west());
        placeAeFluidDrive(helper, MACHINE_POS.east(), Fluids.WATER, 16_000);

        FluidResource water = FluidResource.of(Fluids.WATER);
        AEFluidKey waterKey = AEFluidKey.of(Fluids.WATER);
        AeStockingFluidPort port = machine.machineComponents().require(AeStockingFluidPort.AE_STOCKING_FLUID_PORT);
        port.setConfigSlot(0, AeConfigSlot.of(water, 8_000));
        ResourceHandler<FluidResource> recipeInput = requireRecipeFluidInput(helper, machine);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertNetworkOnline(helper);
                    if (amountOf(recipeInput, water) != 8_000L) {
                        helper.fail("Recipe INPUT handler should report the configured window before rollback, got " + amountOf(recipeInput, water), MACHINE_POS);
                    }
                })
                .thenExecute(() -> {
                    try (Transaction tx = Transaction.openRoot()) {
                        int extracted = recipeInput.extract(water, 4_000, tx);
                        if (extracted != 4_000) {
                            helper.fail("Pre-rollback extract should modulate 4000 mB, got " + extracted,
                                    MACHINE_POS);
                        }
                        // No commit: closing the root transaction aborts and must refund the network.
                    }
                    assertNetworkAmount(helper, waterKey, 16_000L,
                            "Aborted transaction must refund the network to 16000 mB");
                    if (port.handler().pendingRefundAmountForTest(water) != 0L) {
                        helper.fail("Online refund must not leave a pending refund queue entry", MACHINE_POS);
                    }
                    if (amountOf(recipeInput, water) != 8_000L) {
                        helper.fail("Configured window should be fully restored after rollback, got " + amountOf(recipeInput, water), MACHINE_POS);
                    }
                })
                .thenSucceed();
    }

    // ---- 073 ----

    private static void mePatternProviderReceivesPushedPatternInputs(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_PATTERN_PROVIDER);
        AePatternProvider provider = machine.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);

        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(
                        new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2),
                        new GenericStack(AEFluidKey.of(Fluids.WATER), 100)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        provider.patterns().set(0, encoded);

        List<IPatternDetails> available = provider.getAvailablePatterns();
        if (available.size() != 1) {
            helper.fail("Pattern inventory should decode exactly one processing pattern, got " + available.size(),
                    MACHINE_POS);
        }
        if (provider.isBusy()) {
            helper.fail("Provider with empty buffers must not report busy", MACHINE_POS);
        }

        IPatternDetails details = available.get(0);
        KeyCounter[] acceptedInputs = inputHolderFor(details);
        if (!provider.pushPattern(details, acceptedInputs)) {
            helper.fail("pushPattern should accept inputs into empty buffers", MACHINE_POS);
        }
        if (amountInCounters(acceptedInputs, AEItemKey.of(Items.IRON_INGOT)) != 2L || amountInCounters(acceptedInputs, AEFluidKey.of(Fluids.WATER)) != 100L) {
            helper.fail("The standard processing pattern must publish its unchanged staged counters", MACHINE_POS);
        }

        ResourceHandler<ItemResource> itemBuffer = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).handler();
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        ResourceHandler<FluidResource> fluidBuffer = machine.machineComponents().require(FluidResourcePort.FLUID_INPUT_1).handler();
        if (!(itemBuffer instanceof DirectSlotResourceAccess<?>)) {
            helper.fail("Topo pattern item buffer must opt into direct slot commits", MACHINE_POS);
        }
        if (!(fluidBuffer instanceof DirectSlotResourceAccess<?>)) {
            helper.fail("Topo pattern fluid buffer must opt into direct slot commits", MACHINE_POS);
        }
        if (amountOf(itemBuffer, iron) != 2L) {
            helper.fail("Item buffer should hold the pattern's 2 iron, got " + amountOf(itemBuffer, iron),
                    MACHINE_POS);
        }
        if (amountOf(fluidBuffer, water) != 100L) {
            helper.fail("Fluid buffer should hold the pattern's 100 mB water, got " + amountOf(fluidBuffer, water),
                    MACHINE_POS);
        }
        if (!provider.isBusy()) {
            helper.fail("Provider must report busy while buffers hold content", MACHINE_POS);
        }
        KeyCounter[] busyInputs = inputHolderFor(details);
        long busyIronBefore = amountInCounters(busyInputs, AEItemKey.of(Items.IRON_INGOT));
        long busyWaterBefore = amountInCounters(busyInputs, AEFluidKey.of(Fluids.WATER));
        if (provider.pushPattern(details, busyInputs)) {
            helper.fail("Busy provider must reject a second pattern push", MACHINE_POS);
        }
        if (amountInCounters(busyInputs, AEItemKey.of(Items.IRON_INGOT)) != busyIronBefore || amountInCounters(busyInputs, AEFluidKey.of(Fluids.WATER)) != busyWaterBefore) {
            helper.fail("Rejected push must leave every AE input counter unchanged", MACHINE_POS);
        }

        try (Transaction tx = Transaction.openRoot()) {
            if (itemBuffer.extract(iron, 2, tx) != 2 || fluidBuffer.extract(water, 100, tx) != 100) {
                helper.fail("Draining the buffers should extract exactly the pushed inputs", MACHINE_POS);
            }
            tx.commit();
        }
        if (provider.isBusy()) {
            helper.fail("Provider must report not busy once the buffers drain", MACHINE_POS);
        }
        helper.succeed();
    }

    /** One KeyCounter per pattern input, holding exactly that input's key and amount. */
    private static KeyCounter[] inputHolderFor(IPatternDetails details) {
        IPatternDetails.IInput[] inputs = details.getInputs();
        KeyCounter[] holder = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            KeyCounter counter = new KeyCounter();
            GenericStack template = inputs[i].getPossibleInputs()[0];
            counter.add(template.what(), template.amount() * inputs[i].getMultiplier());
            holder[i] = counter;
        }
        return holder;
    }

    // ---- 074 ----

    private static void meExportHatchPushesBufferedOutputsToNetwork(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_EXPORT_HATCH);
        placeAeCreativeEnergyCell(helper, MACHINE_POS.west());
        placeAeItemDrive(helper, MACHINE_POS.east(), Items.IRON_INGOT, 0);
        // Above, not south: an AE2 drive's grid node connects on every side EXCEPT its front
        // (DriveBlockEntity#getGridConnectableSides), and the default blockstate faces NORTH —
        // a drive south of the machine would point its non-connectable front at the machine.
        placeAeFluidDrive(helper, MACHINE_POS.above(), Fluids.WATER, 0);

        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        AEItemKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey waterKey = AEFluidKey.of(Fluids.WATER);

        ResourceHandler<ItemResource> itemOutput = requireRecipeItemHandler(helper, machine, RecipeRole.OUTPUT);
        ResourceHandler<FluidResource> fluidOutput = requireRecipeFluidHandler(helper, machine, RecipeRole.OUTPUT);

        try (Transaction tx = Transaction.openRoot()) {
            if (itemOutput.insert(iron, 5, tx) != 5) {
                helper.fail("Recipe OUTPUT path should accept 5 iron into the item buffer", MACHINE_POS);
            }
            if (fluidOutput.insert(water, 3_000, tx) != 3_000) {
                helper.fail("Recipe OUTPUT path should accept 3000 mB water into the fluid buffer", MACHINE_POS);
            }
            tx.commit();
        }

        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertNetworkAmount(helper, ironKey, 5L,
                            "Push trait should deliver the buffered 5 iron into the network");
                    assertNetworkAmount(helper, waterKey, 3_000L,
                            "Push trait should deliver the buffered 3000 mB water into the network");
                })
                .thenExecute(() -> {
                    int itemKinds = machine.machineComponents().require(AeItemBufferPort.AE_ITEM_BUFFER_PORT)
                            .buffer().kindsInUse();
                    int fluidKinds = machine.machineComponents().require(AeFluidBufferPort.AE_FLUID_BUFFER_PORT)
                            .buffer().kindsInUse();
                    if (itemKinds != 0 || fluidKinds != 0) {
                        helper.fail("Both buffers must be empty after the push (item kinds=" + itemKinds + ", fluid kinds=" + fluidKinds + ")", MACHINE_POS);
                    }
                })
                .thenSucceed();
    }

    // ---- pattern planning regressions ----

    private static void mePatternInputPlanAggregatesWithoutTransactionProbe(GameTestHelper helper) {
        AEItemKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        ItemResource iron = ironKey.toResource();
        TrackingItemHandler handler = new TrackingItemHandler(2);
        handler.set(0, iron, 60);
        handler.resetTracking();

        KeyCounter first = counter(ironKey, 2L);
        KeyCounter second = counter(ironKey, 3L);
        if (!AePatternProvider.pushInputsToBuffers(new KeyCounter[] { first, second }, handler, null)) {
            helper.fail("Aggregated item plan should fit the two-slot handler");
        }
        if (handler.getAmountAsLong(0) != 64L || handler.getAmountAsLong(1) != 1L) {
            helper.fail("Repeated iron inputs should aggregate to [64, 1], got [" + handler.getAmountAsLong(0) + ", " + handler.getAmountAsLong(1) + "]");
        }
        assertDirectItemCommit(helper, handler, "Aggregated item plan");
        if (first.get(ironKey) != 2L || second.get(ironKey) != 3L) {
            helper.fail("Read-only planning helper must not mutate source counters");
        }
        helper.succeed();
    }

    private static void mePatternInputPlanMatchesConstrainedLargeHandler(GameTestHelper helper) {
        AEItemKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        ItemResource iron = ironKey.toResource();
        ItemResource gold = goldKey.toResource();
        RestrictedItemHandler handler = new RestrictedItemHandler(12, gold, 0);

        if (!AePatternProvider.pushInputsToBuffers(
                new KeyCounter[] { counter(ironKey, 11L * 64L), counter(goldKey, 1L) },
                handler,
                null)) {
            helper.fail("Constrained 12-slot assignment should find the complete matching");
        }
        if (!gold.equals(handler.getResource(0)) || handler.getAmountAsLong(0) != 1L) {
            helper.fail("Gold must occupy its only valid slot 0");
        }
        if (amountOf(handler, iron) != 11L * 64L) {
            helper.fail("Iron should occupy all eleven interchangeable slots, got " + amountOf(handler, iron));
        }
        for (int slot = 1; slot < handler.size(); slot++) {
            if (!iron.equals(handler.getResource(slot)) || handler.getAmountAsLong(slot) != 64L) {
                helper.fail("Iron slot " + slot + " should contain exactly 64 items");
            }
        }
        assertDirectItemCommit(helper, handler, "Constrained large-handler plan");
        helper.succeed();
    }

    private static void mePatternCapacityFailurePreservesAeInputs(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_PATTERN_PROVIDER);
        AePatternProvider provider = machine.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);
        provider.setBlocking(false);

        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        provider.patterns().set(0, encoded);
        List<IPatternDetails> available = provider.getAvailablePatterns();
        if (available.size() != 1) {
            helper.fail("Capacity fixture should decode exactly one processing pattern", MACHINE_POS);
        }

        var itemBuffer = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).handler();
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        for (int slot = 0; slot < itemBuffer.size(); slot++) {
            itemBuffer.set(slot, iron, 64);
        }
        KeyCounter[] inputHolder = inputHolderFor(available.get(0));
        AEItemKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        long goldBefore = amountInCounters(inputHolder, goldKey);
        if (provider.pushPattern(available.get(0), inputHolder)) {
            helper.fail("Full item buffer must reject the gold pattern input", MACHINE_POS);
        }
        if (amountInCounters(inputHolder, goldKey) != goldBefore) {
            helper.fail("Failed provider push must not publish staged KeyCounter removal", MACHINE_POS);
        }
        for (int slot = 0; slot < itemBuffer.size(); slot++) {
            if (!iron.equals(itemBuffer.getResource(slot)) || itemBuffer.getAmountAsLong(slot) != 64L) {
                helper.fail("Capacity failure partially changed item slot " + slot, MACHINE_POS);
            }
        }

        TrackingItemHandler zeroCapacity = new TrackingItemHandler(1, 0);
        KeyCounter readOnlyCounter = counter(goldKey, 1L);
        if (AePatternProvider.pushInputsToBuffers(
                new KeyCounter[] { readOnlyCounter }, zeroCapacity, null)) {
            helper.fail("Zero-capacity handler must reject the planned input");
        }
        if (zeroCapacity.transactionalInsertCalls != 0) {
            helper.fail("Capacity rejection must not call transactional insert, got " + zeroCapacity.transactionalInsertCalls + " calls");
        }
        if (readOnlyCounter.get(goldKey) != 1L) {
            helper.fail("Capacity rejection must leave the source KeyCounter unchanged");
        }
        helper.succeed();
    }

    private static void mePatternFluidPlanCommitsWithoutTransactionProbe(GameTestHelper helper) {
        AEFluidKey waterKey = AEFluidKey.of(Fluids.WATER);
        FluidResource water = waterKey.toResource();
        TrackingFluidHandler handler = new TrackingFluidHandler(2, 1_000);

        if (!AePatternProvider.pushInputsToBuffers(
                new KeyCounter[] { counter(waterKey, 1_500L) }, null, handler)) {
            helper.fail("1500 mB water should fit across two 1000 mB tanks");
        }
        if (amountOf(handler, water) != 1_500L) {
            helper.fail("Fluid plan should commit exactly 1500 mB, got " + amountOf(handler, water));
        }
        if (handler.getAmountAsLong(0) != 1_000L || handler.getAmountAsLong(1) != 500L) {
            helper.fail("Fluid demand must respect per-tank capacity, got [" + handler.getAmountAsLong(0) + ", " + handler.getAmountAsLong(1) + "]");
        }
        if (handler.transactionalInsertCalls != 0) {
            helper.fail("Fluid plan must not call transactional insert, got " + handler.transactionalInsertCalls + " calls");
        }
        if (handler.directSetCalls == 0) {
            helper.fail("Fluid plan should use the explicit direct-slot capability");
        }
        if (handler.changeLifecycles.isEmpty()) {
            helper.fail("Fluid direct commit should notify the real stack handler");
        }
        for (Transaction.Lifecycle lifecycle : handler.changeLifecycles) {
            if (lifecycle != Transaction.Lifecycle.NONE) {
                helper.fail("Fluid contents changed inside a transaction lifecycle: " + lifecycle);
            }
        }
        helper.succeed();
    }

    private static void mePatternNonstandardStackHandlerRespectsTransactionalInsert(
                                                                                    GameTestHelper helper) {
        AEItemKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        TransactionalLimitItemHandler handler = new TransactionalLimitItemHandler();

        if (AePatternProvider.pushInputsToBuffers(
                new KeyCounter[] { counter(ironKey, 2L) }, handler, null)) {
            helper.fail("Custom handler that accepts only one of two planned items must reject the commit");
        }
        if (handler.insertCalls != 1) {
            helper.fail("Opaque stack handler must be committed through insert exactly once, got " + handler.insertCalls + " calls");
        }
        if (handler.insertLifecycles.size() != 1 || handler.insertLifecycles.getFirst() != Transaction.Lifecycle.OPEN) {
            helper.fail("Opaque stack handler insert must run inside one open transaction, got " + handler.insertLifecycles);
        }
        if (!handler.getResource(0).isEmpty() || handler.getAmountAsLong(0) != 0L) {
            helper.fail("Rejected opaque handler commit must roll back its partial insertion");
        }
        helper.succeed();
    }

    private static void mePatternDirectCommitCompensatesCallbackFailure(GameTestHelper helper) {
        AEItemKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        ThrowingItemHandler handler = new ThrowingItemHandler(2, 2);

        try {
            AePatternProvider.pushInputsToBuffers(
                    new KeyCounter[] { counter(ironKey, 1L), counter(goldKey, 1L) },
                    handler,
                    null);
            helper.fail("Synthetic second-slot callback failure should escape the direct commit");
        } catch (IllegalStateException expected) {
            if (!"synthetic callback failure".equals(expected.getMessage())) {
                helper.fail("Unexpected direct-commit failure: " + expected);
            }
        }
        if (handler.changes != 4) {
            helper.fail("Expected two applied callbacks and two compensating callbacks, got " + handler.changes);
        }
        for (int slot = 0; slot < handler.size(); slot++) {
            if (!handler.getResource(slot).isEmpty() || handler.getAmountAsLong(slot) != 0L) {
                helper.fail("Callback compensation left partial content in slot " + slot);
            }
        }
        helper.succeed();
    }

    // ---- fixture helpers ----

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }

    private static long amountInCounters(KeyCounter[] counters, AEKey key) {
        long amount = 0L;
        for (KeyCounter counter : counters) {
            amount += counter.get(key);
        }
        return amount;
    }

    private static void assertDirectItemCommit(
                                               GameTestHelper helper, TrackingItemHandler handler, String operation) {
        if (handler.transactionalInsertCalls != 0) {
            helper.fail(operation + " must not call transactional insert, got " + handler.transactionalInsertCalls + " calls");
        }
        if (handler.changeLifecycles.isEmpty()) {
            helper.fail(operation + " should notify the real stack handler");
        }
        if (handler.directSetCalls == 0) {
            helper.fail(operation + " should use the explicit direct-slot capability");
        }
        for (Transaction.Lifecycle lifecycle : handler.changeLifecycles) {
            if (lifecycle != Transaction.Lifecycle.NONE) {
                helper.fail(operation + " changed contents inside a transaction lifecycle: " + lifecycle);
            }
        }
    }

    private static class TrackingItemHandler extends ItemStacksResourceHandler
                                             implements DirectSlotResourceAccess<ItemResource> {

        private final int capacity;
        private final ArrayList<Transaction.Lifecycle> changeLifecycles = new ArrayList<>();
        private int transactionalInsertCalls;
        private int directSetCalls;

        private TrackingItemHandler(int size) {
            this(size, 64);
        }

        private TrackingItemHandler(int size, int capacity) {
            super(size);
            this.capacity = capacity;
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return capacity;
        }

        @Override
        public void directSet(int index, ItemResource resource, int amount) {
            directSetCalls++;
            set(index, resource, amount);
        }

        @Override
        public int insert(
                          int index,
                          ItemResource resource,
                          int amount,
                          TransactionContext transaction) {
            transactionalInsertCalls++;
            return super.insert(index, resource, amount, transaction);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            changeLifecycles.add(Transaction.getLifecycle());
        }

        private void resetTracking() {
            transactionalInsertCalls = 0;
            directSetCalls = 0;
            changeLifecycles.clear();
        }
    }

    private static final class RestrictedItemHandler extends TrackingItemHandler {

        private final ItemResource restrictedResource;
        private final int restrictedSlot;

        private RestrictedItemHandler(int size, ItemResource restrictedResource, int restrictedSlot) {
            super(size);
            this.restrictedResource = restrictedResource;
            this.restrictedSlot = restrictedSlot;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !restrictedResource.equals(resource) || index == restrictedSlot;
        }
    }

    private static final class TrackingFluidHandler extends FluidStacksResourceHandler
                                                    implements DirectSlotResourceAccess<FluidResource> {

        private final ArrayList<Transaction.Lifecycle> changeLifecycles = new ArrayList<>();
        private int transactionalInsertCalls;
        private int directSetCalls;

        private TrackingFluidHandler(int tanks, int capacity) {
            super(tanks, capacity);
        }

        @Override
        public void directSet(int index, FluidResource resource, int amount) {
            directSetCalls++;
            set(index, resource, amount);
        }

        @Override
        public int insert(
                          int index,
                          FluidResource resource,
                          int amount,
                          TransactionContext transaction) {
            transactionalInsertCalls++;
            return super.insert(index, resource, amount, transaction);
        }

        @Override
        protected void onContentsChanged(
                                         int index,
                                         net.neoforged.neoforge.fluids.FluidStack previousContents) {
            changeLifecycles.add(Transaction.getLifecycle());
        }
    }

    private static final class TransactionalLimitItemHandler extends ItemStacksResourceHandler {

        private final ArrayList<Transaction.Lifecycle> insertLifecycles = new ArrayList<>();
        private int insertCalls;

        private TransactionalLimitItemHandler() {
            super(1);
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return 64;
        }

        @Override
        public int insert(
                          int index,
                          ItemResource resource,
                          int amount,
                          TransactionContext transaction) {
            insertCalls++;
            insertLifecycles.add(Transaction.getLifecycle());
            return super.insert(index, resource, Math.min(amount, 1), transaction);
        }
    }

    private static final class ThrowingItemHandler extends ItemStacksResourceHandler
                                                   implements DirectSlotResourceAccess<ItemResource> {

        private final int throwAtChange;
        private int changes;

        private ThrowingItemHandler(int size, int throwAtChange) {
            super(size);
            this.throwAtChange = throwAtChange;
        }

        @Override
        public void directSet(int index, ItemResource resource, int amount) {
            set(index, resource, amount);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            changes++;
            if (changes == throwAtChange) {
                throw new IllegalStateException("synthetic callback failure");
            }
        }
    }

    private static MachineBlockEntity placeMeMachine(GameTestHelper helper, MachineDefinition definition) {
        helper.setBlock(MACHINE_POS, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static void mePatternProviderPersistReloadSuppressesDirtyCallback(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_PATTERN_PROVIDER);
        AePatternProvider provider = machine.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        provider.patterns().set(0, encoded);

        var saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loadedBlockEntity;
        try {
            loadedBlockEntity = BlockEntity.loadStatic(
                    machine.getBlockPos(),
                    machine.getBlockState(),
                    saved,
                    helper.getLevel().registryAccess());
        } catch (IllegalStateException failure) {
            helper.fail("Pattern persistence replay must not dirty machine data: " + failure.getMessage());
            return;
        }
        if (!(loadedBlockEntity instanceof MachineBlockEntity loaded)) {
            helper.fail("Saved pattern provider should reload as a machine, got " + loadedBlockEntity);
            return;
        }
        loaded.setLevel(helper.getLevel());
        AePatternProvider loadedProvider = loaded.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);
        if (loadedProvider.patterns().get(0).isEmpty()) {
            helper.fail("Persisted pattern must survive reload");
        }
        helper.succeed();
    }

    private static void mePatternProviderSeparatedModePersists(GameTestHelper helper) {
        MachineBlockEntity machine = placeMeMachine(helper, BuiltinTopoMeMachines.ME_PATTERN_PROVIDER);
        AePatternProvider provider = machine.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);
        ResourceHandler<ItemResource> sharedItems = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).handler();
        try (Transaction tx = Transaction.openRoot()) {
            if (sharedItems.insert(ItemResource.of(Items.OAK_LOG), 64, tx) != 64) {
                helper.fail("Unable to seed the shared pattern input buffer");
                return;
            }
            tx.commit();
        }
        if (!provider.trySetSeparated(true) || !provider.separated()) {
            helper.fail("Pattern provider must accept separated mode with legacy shared inputs");
            return;
        }

        PageCollector pages = new PageCollector();
        ComponentCollector components = new ComponentCollector();
        machine.collectMachineUi(pages, components);
        if (MachineUiFrameTemplate.create(
                machine.getBlockState().getBlock().getName(), pages.entries(), components.entries())
                .selectId("topo_side_card_live_topo_search_pool")
                .findFirst()
                .isEmpty()) {
            helper.fail("Search-pool live visibility must survive UI normalization");
            return;
        }

        var saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loadedBlockEntity = BlockEntity.loadStatic(
                machine.getBlockPos(),
                machine.getBlockState(),
                saved,
                helper.getLevel().registryAccess());
        if (!(loadedBlockEntity instanceof MachineBlockEntity loaded)) {
            helper.fail("Saved pattern provider should reload as a machine, got " + loadedBlockEntity);
            return;
        }
        loaded.setLevel(helper.getLevel());
        AePatternProvider loadedProvider = loaded.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);
        if (!loadedProvider.separated()) {
            helper.fail("Separated mode must survive block-entity reload");
            return;
        }
        helper.succeed();
    }

    private static AeGridNode gridTrait(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class)
                .machineComponents().require(AeGridNode.AE_GRID);
    }

    private static void assertNetworkOnline(GameTestHelper helper) {
        if (!gridTrait(helper).network().isOnline()) {
            helper.fail("AE grid should be online", MACHINE_POS);
        }
    }

    private static void assertNetworkAmount(GameTestHelper helper, AEKey key, long expected, String message) {
        assertNetworkOnline(helper);
        long available = gridTrait(helper).network().available(key);
        if (available != expected) {
            helper.fail(message + "; network has " + available, MACHINE_POS);
        }
    }

    private static ResourceHandler<FluidResource> requireRecipeFluidInput(
                                                                          GameTestHelper helper, MachineBlockEntity machine) {
        return requireRecipeFluidHandler(helper, machine, RecipeRole.INPUT);
    }

    private static ResourceHandler<FluidResource> requireRecipeFluidHandler(
                                                                            GameTestHelper helper, MachineBlockEntity machine, RecipeRole io) {
        ResourceHandler<FluidResource> handler = machine.machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinTopoResourceIntegrations.FLUID.resourceType(), io);
        if (handler == null) {
            helper.fail("Machine should expose a recipe-side fluid " + io + " handler", MACHINE_POS);
        }
        return handler;
    }

    private static ResourceHandler<ItemResource> requireRecipeItemHandler(
                                                                          GameTestHelper helper, MachineBlockEntity machine, RecipeRole io) {
        ResourceHandler<ItemResource> handler = machine.machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinTopoResourceIntegrations.ITEM.resourceType(), io);
        if (handler == null) {
            helper.fail("Machine should expose a recipe-side item " + io + " handler", MACHINE_POS);
        }
        return handler;
    }

    private static <R extends Resource> long amountOf(ResourceHandler<R> handler, R resource) {
        long amount = 0L;
        for (int index = 0; index < handler.size(); index++) {
            if (handler.getResource(index).equals(resource)) {
                amount += handler.getAmountAsLong(index);
            }
        }
        return amount;
    }

    private static Block aeBlock(GameTestHelper helper, String path) {
        Identifier id = Identifier.fromNamespaceAndPath("ae2", path);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = BuiltInRegistries.BLOCK.getValue(key);
        if (block == null || BuiltInRegistries.BLOCK.getKey(block).equals(Identifier.withDefaultNamespace("air"))) {
            helper.fail("Missing AE2 block " + id);
        }
        return block;
    }

    private static Item aeItem(GameTestHelper helper, String path) {
        Identifier id = Identifier.fromNamespaceAndPath("ae2", path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (item == null || item == Items.AIR) {
            helper.fail("Missing AE2 item " + id);
        }
        return item;
    }

    private static void placeAeCreativeEnergyCell(GameTestHelper helper, BlockPos relativePos) {
        helper.getLevel().setBlockAndUpdate(
                helper.absolutePos(relativePos),
                aeBlock(helper, "creative_energy_cell").defaultBlockState());
    }

    private static void placeAeItemDrive(GameTestHelper helper, BlockPos relativePos, Item item, long amount) {
        ItemStack cell = new ItemStack(aeItem(helper, "item_storage_cell_1k"));
        var cellInventory = StorageCells.getCellInventory(cell, null);
        if (cellInventory == null) {
            helper.fail("AE2 item storage cell did not expose a cell inventory");
            return;
        }
        if (amount > 0L) {
            AEItemKey key = AEItemKey.of(item);
            long inserted = cellInventory.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
            if (inserted != amount) {
                helper.fail("Expected to prefill AE2 item cell with " + amount + " " + item + ", inserted " + inserted);
                return;
            }
        }
        insertCellIntoDrive(helper, relativePos, cell, "item");
    }

    private static void placeAeFluidDrive(GameTestHelper helper, BlockPos relativePos, Fluid fluid, long amount) {
        ItemStack cell = new ItemStack(aeItem(helper, "fluid_storage_cell_1k"));
        var cellInventory = StorageCells.getCellInventory(cell, null);
        if (cellInventory == null) {
            helper.fail("AE2 fluid storage cell did not expose a cell inventory");
            return;
        }
        if (amount > 0L) {
            AEFluidKey key = AEFluidKey.of(fluid);
            long inserted = cellInventory.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
            if (inserted != amount) {
                helper.fail("Expected to prefill AE2 fluid cell with " + amount + " " + fluid + ", inserted " + inserted);
                return;
            }
        }
        insertCellIntoDrive(helper, relativePos, cell, "fluid");
    }

    private static void insertCellIntoDrive(
                                            GameTestHelper helper, BlockPos relativePos, ItemStack cell, String kind) {
        BlockPos drivePos = helper.absolutePos(relativePos);
        helper.getLevel().setBlockAndUpdate(drivePos, aeBlock(helper, "drive").defaultBlockState());
        ResourceHandler<ItemResource> driveItems = helper.getLevel().getCapability(Capabilities.Item.BLOCK, drivePos, (Direction) null);
        if (driveItems == null) {
            helper.fail("Expected AE2 drive item capability at " + relativePos);
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = driveItems.insert(ItemResource.of(cell), 1, tx);
            if (inserted != 1) {
                helper.fail("AE2 drive rejected prefilled " + kind + " cell, inserted " + inserted);
                return;
            }
            tx.commit();
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
            throw new IllegalArgumentException("ME hatch GameTest index out of range: " + index);
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
        return new TestData<>(environment, EMPTY_STRUCTURE, TEST_TIMEOUT_TICKS, 0, true, Rotation.NONE);
    }
}
