package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.FormedMachineBlock;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.multiblock.MultiblockClientDiagnosis;
import net.ptcrys.topo.api.machine.multiblock.MultiblockControllerMetadata;
import net.ptcrys.topo.api.machine.multiblock.MultiblockLevelBinder;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.recipe.RecipePreviewPlan;
import net.ptcrys.topo.data.machine.BuiltinTopoControllerMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.machine.multiblock.BuiltinTopoCasingBlocks;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * GameTests for the large-grinder multiblock (first content controller from
 * {@code docs/design/machine_construction.md}): 3×3×3 dense structure casing, hollow centre,
 * controller-side item/energy ports, fine-grinder recipes with T3 energy + max-4 parallel.
 */
public final class MultiblockMachineGameTests {

    private static final String SUITE = "multiblock_machine";

    /**
     * The 3×3×3 large-grinder layers exactly as declared in
     * {@code BuiltinTopoControllerMachines}: one string array per horizontal layer (bottom to top),
     * strings are depth rows (north first), characters run along the width. Controller sits
     * front-centre of the middle layer ({@code '@'}); the absolute centre is a non-structure cell
     * ({@code ' '}) for the grinding chamber; every other body cell is dense structure casing.
     */
    private static final String[][] STRUCTURE_LAYERS = {
            { "XXX", "XXX", "XXX" },
            { "XXX", "X X", "X@X" },
            { "XXX", "XXX", "XXX" },
    };

    /** Controller world position; with FACING=SOUTH (identity) the structure occupies x0..2, y1..3, z0..2. */
    private static final BlockPos CONTROLLER_POS = new BlockPos(1, 2, 2);
    /** Any solid casing cell used for mark/break invalidation (bottom-north centre). */
    private static final BlockPos CASING_TO_BREAK_POS = new BlockPos(1, 1, 0);
    /** A neighbour casing used only to dirty the recheck queue after placement. */
    private static final BlockPos MARK_POS = new BlockPos(0, 1, 0);
    /** Controller of the rotated build; with FACING=NORTH the same x0..2, y1..3, z0..2 box is filled. */
    private static final BlockPos NORTH_CONTROLLER_POS = new BlockPos(1, 2, 0);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 8;
    private static final String ASYNC_THRESHOLD_PROPERTY = "oi.multiblock.asyncThreshold";

    private MultiblockMachineGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("multiblock_machine"), new TestEnvironmentDefinition.AllOf());
        register(
                event,
                environment,
                1,
                "large_grinder_forms_accepts_input_and_unforms",
                "Tests MultiblockController, StructureEngine, FormedRuntime, and RecheckDirtySet: " + "a large grinder forms from its blueprint, accepts item input on the " + "controller ports, and unforms after a watched casing changes.",
                MultiblockMachineGameTests::largeGrinderFormsAcceptsInputAndUnforms);
        register(
                event,
                environment,
                2,
                "jei_slot_plan_uses_ported_machine",
                "Tests RecipeSlotCounts: the canonical JEI slot plan for the macerator recipe type " + "comes from the ported single-block macerator (4 in / 4 out).",
                MultiblockMachineGameTests::jeiSlotPlanUsesPortedMachine);
        register(
                event,
                environment,
                3,
                "large_grinder_forms_rotated",
                "Tests orientation folding end to end: a NORTH-facing large grinder still forms, " + "and the as-built diagnosis map folds block states back into the " + "canonical frame.",
                MultiblockMachineGameTests::largeGrinderFormsRotated);
        register(
                event,
                environment,
                4,
                "targeted_invalidation_unforms_on_next_tick",
                "Tests RecheckDirtySet change notes and the targeted-invalidation fast path: breaking " + "one member cell of a formed structure unforms the controller on its next " + "tick (O(changes) live re-test, no full footprint scan) — the ≤0.5s " + "invalidation budget at any structure size.",
                MultiblockMachineGameTests::targetedInvalidationUnformsOnNextTick);
        register(
                event,
                environment,
                5,
                "async_recognition_forms_and_unforms",
                "Tests AsyncStructureService and the pending-commit staleness checks: with the async " + "cell threshold forced to 1 the large grinder forms through the snapshot → " + "worker → commit pipeline, and a broken casing still unforms promptly via " + "the synchronous targeted path.",
                MultiblockMachineGameTests::asyncRecognitionFormsAndUnforms);
        register(
                event,
                environment,
                6,
                "recipe_progress_retained_across_unform",
                "Tests RecipeCondition gating and controller-local IO: an in-flight fine-grinding run " + "survives a mid-run unform (progress paused, never reset to IDLE), and after " + "the casing is restored the same run completes without re-feeding input.",
                MultiblockMachineGameTests::recipeProgressRetainedAcrossUnform);
        register(
                event,
                environment,
                7,
                "incomplete_structure_blocks_forming",
                "Tests that a large grinder missing a required dense-structure casing cell does not " + "form, and restoring the casing forms the structure.",
                MultiblockMachineGameTests::incompleteStructureBlocksForming);
        register(
                event,
                environment,
                8,
                "active_parallel_factor_does_not_reprobe_consumed_inputs",
                "A two-wide run keeps its selected factor after start inputs are consumed and emits exactly two outputs.",
                MultiblockMachineGameTests::activeParallelFactorDoesNotReprobeConsumedInputs);
    }

    private static void incompleteStructureBlocksForming(GameTestHelper helper) {
        placeController(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    placeStructureBlocks(helper);
                    helper.setBlock(CASING_TO_BREAK_POS, Blocks.AIR.defaultBlockState());
                    markChanged(helper, MARK_POS);
                })
                .thenExecuteAfter(5, () -> assertFormed(
                        helper,
                        false,
                        "A structure missing a required casing cell must not form"))
                .thenExecute(() -> {
                    helper.setBlock(CASING_TO_BREAK_POS, casingState());
                    markChanged(helper, CASING_TO_BREAK_POS);
                })
                .thenWaitUntil(() -> assertFormed(
                        helper,
                        true,
                        "Restoring the missing casing should form the structure"))
                .thenSucceed();
    }

    /**
     * The ≤0.5s invalidation budget, mechanically: after one watched member cell changes, the very
     * next controller tick must already publish UNFORMED — the targeted path re-tests only the
     * changed cell, so this latency is independent of structure size.
     */
    private static void targetedInvalidationUnformsOnNextTick(GameTestHelper helper) {
        placeController(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    placeStructureAroundController(helper);
                    markChanged(helper, MARK_POS);
                })
                .thenWaitUntil(() -> assertFormed(helper, true, "Completed blueprint should form"))
                .thenExecute(() -> {
                    helper.setBlock(CASING_TO_BREAK_POS, Blocks.AIR.defaultBlockState());
                    markChanged(helper, CASING_TO_BREAK_POS);
                })
                // Strict ticks on purpose: this IS the ≤0.5s invalidation budget. The targeted path
                // is synchronous, so concurrent async-threshold tests cannot add latency here.
                .thenExecuteAfter(2, () -> assertFormed(
                        helper,
                        false,
                        "Targeted invalidation must unform on the controller tick after the change"))
                .thenSucceed();
    }

    /** Forces every footprint through the async pipeline and replays the form/unform lifecycle. */
    private static void asyncRecognitionFormsAndUnforms(GameTestHelper helper) {
        String previousThreshold = System.getProperty(ASYNC_THRESHOLD_PROPERTY);
        System.setProperty(ASYNC_THRESHOLD_PROPERTY, "1");
        Runnable restore = () -> {
            if (previousThreshold == null) {
                System.clearProperty(ASYNC_THRESHOLD_PROPERTY);
            } else {
                System.setProperty(ASYNC_THRESHOLD_PROPERTY, previousThreshold);
            }
        };
        placeController(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    placeStructureAroundController(helper);
                    markChanged(helper, MARK_POS);
                })
                .thenWaitUntil(() -> assertFormed(
                        helper, true, "Async pipeline (snapshot → worker → commit) should form the structure"))
                .thenExecute(() -> {
                    helper.setBlock(CASING_TO_BREAK_POS, Blocks.AIR.defaultBlockState());
                    markChanged(helper, CASING_TO_BREAK_POS);
                })
                .thenExecuteAfter(2, () -> {
                    try {
                        assertFormed(
                                helper,
                                false,
                                "Breaking a casing must unform promptly even while async submissions exist");
                    } finally {
                        restore.run();
                    }
                })
                .thenSucceed();
    }

    /**
     * A mid-run unform must pause the active fine-grinding, never reset it: state stays off IDLE with
     * its progress intact while unformed, and the same run completes after the casing is restored
     * without re-feeding input.
     */
    private static void recipeProgressRetainedAcrossUnform(GameTestHelper helper) {
        int[] progressAtBreak = new int[1];
        placeController(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    placeStructureAroundController(helper);
                    markChanged(helper, MARK_POS);
                })
                .thenWaitUntil(() -> assertFormed(helper, true, "Completed blueprint should form"))
                .thenExecute(() -> assertInserted(
                        helper,
                        requireControllerInput(helper),
                        ironIngotResource(),
                        // ParallelModifier max 4: actual parallel from inputs — feed enough for a batch.
                        4,
                        "Formed controller should accept a 4-parallel fine-grinding batch"))
                .thenWaitUntil(() -> {
                    RecipeLogic logic = controllerLogic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING || logic.progress() <= 0) {
                        helper.fail("Fine grinding should start and accumulate progress", CONTROLLER_POS);
                    }
                })
                .thenExecute(() -> {
                    progressAtBreak[0] = controllerLogic(helper).progress();
                    helper.setBlock(CASING_TO_BREAK_POS, Blocks.AIR.defaultBlockState());
                    markChanged(helper, CASING_TO_BREAK_POS);
                })
                .thenExecuteAfter(5, () -> {
                    assertFormed(helper, false, "Broken casing should unform the structure mid-run");
                    RecipeLogic logic = controllerLogic(helper);
                    if (logic.state() == RecipeLogic.State.IDLE) {
                        helper.fail("Unforming must pause the active recipe, not reset it to IDLE", CONTROLLER_POS);
                    }
                    if (logic.progress() < progressAtBreak[0]) {
                        helper.fail(
                                "Unforming must retain recipe progress (had " + progressAtBreak[0] + ", now " + logic.progress() + ")",
                                CONTROLLER_POS);
                    }
                })
                .thenExecute(() -> {
                    helper.setBlock(CASING_TO_BREAK_POS, casingState());
                    markChanged(helper, CASING_TO_BREAK_POS);
                })
                .thenWaitUntil(() -> assertFormed(helper, true, "Restored casing should re-form the structure"))
                // 成型翻转(controller tick 内)对停泊配方 ticker 的唤醒走 alert,在下一个心跳才补跑;
                // 留 2 tick 让被阻塞的配方恢复 WORKING——同 tick 复活从来不是契约。
                .thenExecuteAfter(2, () -> {
                    RecipeLogic logic = controllerLogic(helper);
                    logic.setProgressForGameTest(Math.max(1, logic.maxProgress() - 1));
                })
                .thenWaitUntil(() -> {
                    RecipeLogic logic = controllerLogic(helper);
                    if (logic.state() != RecipeLogic.State.IDLE || logic.progress() != 0) {
                        helper.fail("Resumed fine grinding should run to completion", CONTROLLER_POS);
                    }
                })
                .thenSucceed();
    }

    private static void activeParallelFactorDoesNotReprobeConsumedInputs(GameTestHelper helper) {
        placeController(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    placeStructureAroundController(helper);
                    markChanged(helper, MARK_POS);
                })
                .thenWaitUntil(() -> assertFormed(helper, true, "Completed blueprint should form"))
                .thenExecute(() -> assertInserted(
                        helper,
                        requireControllerInput(helper),
                        ironIngotResource(),
                        2,
                        "The controller should accept a two-wide dynamic-parallel batch"))
                .thenWaitUntil(() -> {
                    RecipeLogic logic = controllerLogic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING || logic.progress() <= 0) {
                        helper.fail(
                                "The two-wide fine grinding should be active before checking its run lock",
                                CONTROLLER_POS);
                    }
                })
                .thenExecute(() -> {
                    long remainingInput = totalStored(requireControllerInput(helper));
                    if (remainingInput != 0L) {
                        helper.fail("Start inputs must already be consumed; found " + remainingInput, CONTROLLER_POS);
                    }
                    RecipeLogic logic = controllerLogic(helper);
                    logic.setProgressForGameTest(logic.maxProgress() - 1);
                })
                .thenWaitUntil(() -> {
                    if (controllerLogic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("The locked two-wide run should complete", CONTROLLER_POS);
                    }
                })
                .thenExecute(() -> {
                    long output = totalStored(requireControllerOutput(helper));
                    if (output != 2L) {
                        helper.fail(
                                "The active run must retain parallel=2 and emit two dust; output=" + output,
                                CONTROLLER_POS);
                    }
                })
                .thenSucceed();
    }

    private static RecipeLogic controllerLogic(GameTestHelper helper) {
        return controller(helper).machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static ItemResource ironIngotResource() {
        return ItemResource.of(MaterialHelper.requireItem(BuiltinTopoMaterials.IRON, BuiltinTopoMaterialForms.INGOT));
    }

    /**
     * Regression guard for the JEI preview: single-block macerators with ports remain the
     * canonical slot-plan source for the macerator recipe type (4 in / 4 out).
     */
    private static void jeiSlotPlanUsesPortedMachine(GameTestHelper helper) {
        RecipePreviewPlan.SlotPlan plan = BuiltinTopoRecipeTypes.MACERATOR
                .previewPlan()
                .slotted()
                .get(BuiltinTopoResourceIntegrations.ITEM.recipeCapability());
        if (plan == null) {
            helper.fail("Canonical macerator slot plan should include the item capability, got none");
            return;
        }
        if (plan.inputSlots() != 4 || plan.outputSlots() != 4) {
            helper.fail("Canonical macerator slot plan should be the single-block macerator's 4 in / 4 out," + " got " + plan.inputSlots() + " in / " + plan.outputSlots() + " out");
            return;
        }
        helper.succeed();
    }

    private static void largeGrinderFormsAcceptsInputAndUnforms(GameTestHelper helper) {
        placeController(helper);

        helper.startSequence()
                .thenExecuteAfter(2, () -> assertFormed(
                        helper,
                        false,
                        "Controller alone should remain unformed before the blueprint is completed"))
                .thenExecute(() -> {
                    placeStructureAroundController(helper);
                    markChanged(helper, MARK_POS);
                })
                .thenWaitUntil(() -> assertFormed(helper, true, "Completed large grinder blueprint should form"))
                .thenExecute(() -> {
                    ResourceHandler<ItemResource> input = requireControllerInput(helper);
                    assertInserted(
                            helper,
                            input,
                            ironIngotResource(),
                            1,
                            "Formed controller should accept item input on its local ports");
                })
                .thenExecute(() -> {
                    helper.setBlock(CASING_TO_BREAK_POS, Blocks.AIR.defaultBlockState());
                    markChanged(helper, CASING_TO_BREAK_POS);
                })
                .thenExecuteAfter(2, () -> assertFormed(
                        helper,
                        false,
                        "Breaking a watched casing should dirty the controller and unform the structure"))
                .thenSucceed();
    }

    /**
     * A north-facing build must form, and as-built diagnosis must fold the controller facing back
     * to the canonical south front.
     */
    private static void largeGrinderFormsRotated(GameTestHelper helper) {
        Orientation north = Orientations.forFacing(Direction.NORTH);
        helper.setBlock(NORTH_CONTROLLER_POS, BuiltinTopoControllerMachines.LARGE_GRINDER
                .registeredBlock()
                .getDefaultState()
                .setValue(OrientedMachineBlock.FACING, Direction.NORTH));

        helper.startSequence()
                .thenExecute(() -> {
                    placeRotatedStructure(helper, north);
                    markChanged(helper, north.apply(new BlockPos(-1, -1, 0), NORTH_CONTROLLER_POS));
                })
                .thenWaitUntil(() -> assertFormed(
                        helper,
                        NORTH_CONTROLLER_POS,
                        true,
                        "A north-facing large grinder build should form"))
                .thenExecute(() -> assertDetectedBlocksAreCanonical(helper))
                .thenSucceed();
    }

    /** Builds the rotated 3×3×3 structure: every world position folded through {@code orientation}. */
    private static void placeRotatedStructure(GameTestHelper helper, Orientation orientation) {
        for (int layer = 0; layer < STRUCTURE_LAYERS.length; layer++) {
            String[] rows = STRUCTURE_LAYERS[layer];
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length(); column++) {
                    char symbol = rows[row].charAt(column);
                    if (symbol == '@' || symbol == ' ') {
                        continue;
                    }
                    BlockPos local = new BlockPos(column - 1, layer - 1, row - 2);
                    BlockPos pos = orientation.apply(local, NORTH_CONTROLLER_POS);
                    helper.setBlock(pos, casingState());
                }
            }
        }
    }

    /**
     * The as-built diagnosis map must be entirely in the canonical frame: the controller cell shows
     * the canonical south front even though the world build faces north.
     */
    private static void assertDetectedBlocksAreCanonical(GameTestHelper helper) {
        MachineBlockEntity controller = helper.getBlockEntity(NORTH_CONTROLLER_POS, MachineBlockEntity.class);
        Map<BlockPos, BlockState> detected = MultiblockClientDiagnosis.detectedBlocks(
                controller,
                controller.definition().metadata(MultiblockControllerMetadata.TYPE).get(0).blueprint());
        BlockState controllerCell = detected.get(BlockPos.ZERO);
        if (controllerCell == null || controllerCell.getValue(OrientedMachineBlock.FACING) != Direction.SOUTH) {
            helper.fail(
                    "As-built diagnosis should fold the controller back to the canonical south front, got " + controllerCell,
                    NORTH_CONTROLLER_POS);
        }
    }

    private static void placeController(GameTestHelper helper) {
        helper.setBlock(CONTROLLER_POS, BuiltinTopoControllerMachines.LARGE_GRINDER
                .registeredBlock()
                .getDefaultState()
                .setValue(OrientedMachineBlock.FACING, Direction.SOUTH));
    }

    /**
     * Builds the 3×3×3 structure from {@link #STRUCTURE_LAYERS} around the pre-placed controller
     * and fills the controller's local energy buffer (T3 + 4 parallel draw is high).
     */
    private static void placeStructureAroundController(GameTestHelper helper) {
        placeStructureBlocks(helper);
        MachineBlockEntity be = controller(helper);
        ScalarResourcePort energy = be.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), (int) Math.min(Integer.MAX_VALUE, energy.capacityAmount()));
    }

    /** The block-placement half of {@link #placeStructureAroundController}, without the energy fill. */
    private static void placeStructureBlocks(GameTestHelper helper) {
        for (int layer = 0; layer < STRUCTURE_LAYERS.length; layer++) {
            String[] rows = STRUCTURE_LAYERS[layer];
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length(); column++) {
                    BlockPos pos = new BlockPos(column, 1 + layer, row);
                    char symbol = rows[row].charAt(column);
                    if (symbol == '@' || symbol == ' ' || pos.equals(CONTROLLER_POS)) {
                        continue;
                    }
                    helper.setBlock(pos, casingState());
                }
            }
        }
    }

    private static BlockState casingState() {
        return BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING.getDefaultState();
    }

    private static ResourceHandler<ItemResource> requireControllerInput(GameTestHelper helper) {
        ResourceHandler<ItemResource> input = controller(helper)
                .machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinTopoResourceIntegrations.ITEM.resourceType(), RecipeRole.INPUT);
        if (input == null) {
            helper.fail("Formed controller should expose a local item input handler", CONTROLLER_POS);
        }
        return input;
    }

    private static ResourceHandler<ItemResource> requireControllerOutput(GameTestHelper helper) {
        ResourceHandler<ItemResource> output = controller(helper)
                .machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinTopoResourceIntegrations.ITEM.resourceType(), RecipeRole.OUTPUT);
        if (output == null) {
            helper.fail("Formed controller should expose a local item output handler", CONTROLLER_POS);
        }
        return output;
    }

    private static long totalStored(ResourceHandler<ItemResource> handler) {
        long total = 0L;
        for (int slot = 0; slot < handler.size(); slot++) {
            total += handler.getAmountAsLong(slot);
        }
        return total;
    }

    private static MachineBlockEntity controller(GameTestHelper helper) {
        return helper.getBlockEntity(CONTROLLER_POS, MachineBlockEntity.class);
    }

    private static void assertFormed(GameTestHelper helper, boolean expected, String message) {
        assertFormed(helper, CONTROLLER_POS, expected, message);
    }

    private static void assertFormed(GameTestHelper helper, BlockPos controllerPos, boolean expected, String message) {
        BlockState state = helper.getBlockState(controllerPos);
        boolean actual = state.getValue(FormedMachineBlock.FORMED);
        if (actual != expected) {
            helper.fail(message + "; expected formed=" + expected + ", got " + actual, controllerPos);
        }
    }

    private static void markChanged(GameTestHelper helper, BlockPos pos) {
        // Compose the scheduler and claim index here (higher-layer caller), mirroring what the
        // change watcher does for real world events; the services never call each other directly.
        MultiblockLevelBinder.Services services = MultiblockLevelBinder.services(helper.getLevel());
        services.rechecks().markCellChanged(helper.absolutePos(pos), services.claims());
    }

    private static void assertInserted(
                                       GameTestHelper helper,
                                       ResourceHandler<ItemResource> handler,
                                       ItemResource resource,
                                       int amount,
                                       String message) {
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            inserted = handler.insert(resource, amount, transaction);
            transaction.commit();
        }
        if (inserted != amount) {
            helper.fail(message + "; expected insert=" + amount + ", got " + inserted, CONTROLLER_POS);
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
            throw new IllegalArgumentException("Multiblock GameTest index out of range: " + index);
        }
        Objects.requireNonNull(description, "description");
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
