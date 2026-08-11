package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.apiv2.machine.data.MachineDataScope;
import net.ptcrys.topo.apiv2.machine.resource.AutomationIo;
import net.ptcrys.topo.apiv2.machine.resource.LongResourceHandler;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.CRUDE_DUST;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.DUST;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.ORE;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterials.COPPER;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterials.IRON;

public final class MaceratorMachineGameTests {

    private static final String SUITE = "macerator_machine";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 18;

    private MaceratorMachineGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("macerator_machine"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "macerator_capability_sides_are_directional",
                "Tests ResourcePort, ResourcePortMetadata, and MachineBlockEntity capability exposure: " + "every concrete side exposes item capability, while the sideless query exposes none.",
                MaceratorMachineGameTests::maceratorTransferSidesAreDirectional);
        register(
                event,
                environment,
                index++,
                "macerator_top_is_insert_only",
                "Tests DirectionalResourceHandler through the macerator UP item capability: " + "UP accepts inserts and rejects extracts without removing the inserted item.",
                MaceratorMachineGameTests::maceratorTopIsInsertOnly);
        register(
                event,
                environment,
                index++,
                "macerator_bottom_is_extract_only",
                "Tests DirectionalResourceHandler through the macerator DOWN item capability: " + "DOWN extracts output items and rejects item insertion.",
                MaceratorMachineGameTests::maceratorBottomIsExtractOnly);
        register(
                event,
                environment,
                index++,
                "macerator_processes_iron_ore_top_to_bottom",
                "Tests RecipeLogic, OIRecipeType, OIRecipe, ItemRecipeCapability, and ItemResourcePort: " + "iron ore inserted from UP is processed into two crude iron dust extractable from DOWN.",
                MaceratorMachineGameTests::maceratorProcessesIronOreTopToBottom);
        register(
                event,
                environment,
                index++,
                "macerator_wrong_input_direction_does_not_half_commit",
                "Tests DirectionalResourceHandler transaction poison through the real UP input capability: " + "a wrong extract poisons the transaction so a later insert in the same transaction is not committed.",
                MaceratorMachineGameTests::maceratorWrongInputDirectionDoesNotHalfCommit);
        register(
                event,
                environment,
                index++,
                "macerator_wrong_output_direction_does_not_half_commit",
                "Tests DirectionalResourceHandler transaction poison through the real DOWN output capability: " + "a wrong insert poisons the transaction so a later extract in the same transaction is not committed.",
                MaceratorMachineGameTests::maceratorWrongOutputDirectionDoesNotHalfCommit);
        register(
                event,
                environment,
                index++,
                "macerator_output_long_wrong_direction_poisons_transaction",
                "Tests the native LONG direction gate through a dedicated output view: an insertion larger " + "than INT is rejected without chunking and poisons the transaction, while a fresh transaction extracts.",
                MaceratorMachineGameTests::maceratorOutputLongWrongDirectionPoisonsTransaction);
        register(
                event,
                environment,
                index++,
                "macerator_mixed_direction_long_transfer_skips_forbidden_delegate",
                "Tests a mixed input/output capability: a partial LONG input insert skips the known output-only " + "delegate instead of probing it, so a legal output extract still commits in the same transaction.",
                MaceratorMachineGameTests::maceratorMixedDirectionLongTransferSkipsForbiddenDelegate);
        register(
                event,
                environment,
                index++,
                "macerator_rejects_sideless_capability",
                "Tests PortAccess side filtering through the real machine capability surface: " + "the null side returns null and cannot transfer items.",
                MaceratorMachineGameTests::maceratorRejectsSidelessCapability);
        register(
                event,
                environment,
                index++,
                "macerator_waits_when_output_fills_during_processing",
                "Tests RecipeLogic and ItemRecipeCapability output matching: " + "if output fills after the recipe starts, completion waits without overfilling output.",
                MaceratorMachineGameTests::maceratorWaitsWhenOutputFillsDuringProcessing);
        register(
                event,
                environment,
                index++,
                "macerator_resumes_after_output_space_available",
                "Tests RecipeLogic WAITING_OUTPUT recovery through the real DOWN output capability: " + "freeing output space lets the active recipe emit and reset to IDLE.",
                MaceratorMachineGameTests::maceratorResumesAfterOutputSpaceAvailable);
        register(
                event,
                environment,
                index++,
                "macerator_does_not_start_without_recipe",
                "Tests RecipeLogic, OIRecipeType#findRecipe, and ItemRecipeCapability input matching: " + "items without a macerator recipe remain in input and keep the machine IDLE.",
                MaceratorMachineGameTests::maceratorDoesNotStartWithoutRecipe);
        register(
                event,
                environment,
                index++,
                "macerator_failed_search_cache_invalidates_after_input_changes",
                "Tests RecipeLogic failed-search caching, ResourcePort content versions, " + "and OIRecipeType indexed search: after an empty failed search, inserting iron ore from UP " + "invalidates the cache and starts the recipe.",
                MaceratorMachineGameTests::maceratorFailedSearchCacheInvalidatesAfterInputChanges);
        register(
                event,
                environment,
                index++,
                "macerator_active_blockstate_follows_working_recipe",
                "Tests MachineBlockEntity, MachineWorkView, and the real macerator BlockState: " + "the active property turns on while a recipe is WORKING and turns off after completion.",
                MaceratorMachineGameTests::maceratorActiveBlockstateFollowsWorkingRecipe);
        register(
                event,
                environment,
                index++,
                "macerator_active_blockstate_stays_on_while_waiting_output",
                "Tests MachineBlockEntity, MachineWorkView, and RecipeLogic WAITING_OUTPUT: " + "post-start output blocking keeps the active blockstate on until the recipe can finish.",
                MaceratorMachineGameTests::maceratorActiveBlockstateStaysOnWhileWaitingOutput);
        register(
                event,
                environment,
                index++,
                "macerator_waits_to_start_when_output_already_full",
                "Tests RecipeLogic pre-start output gating through the real macerator: " + "a full output keeps the candidate recipe waiting without consuming input or setting active.",
                MaceratorMachineGameTests::maceratorWaitsToStartWhenOutputAlreadyFull);
        register(
                event,
                environment,
                index++,
                "macerator_indexed_search_processes_copper_ore",
                "Tests OIRecipeType indexed recipe search through the real macerator: " + "copper ore selects the copper crushing recipe and produces crude copper dust.",
                MaceratorMachineGameTests::maceratorIndexedSearchProcessesCopperOre);
        register(
                event,
                environment,
                index,
                "macerator_machine_data_persists_working_recipe_and_storage",
                "Tests MachineDataDomain persistence through a real macerator: " + "machine_data saves working recipe state and consumed input storage without machine_traits.",
                MaceratorMachineGameTests::maceratorMachineDataPersistsWorkingRecipeAndStorage);
    }

    private static void maceratorTransferSidesAreDirectional(GameTestHelper helper) {
        placeMacerator(helper);
        for (Direction side : Direction.values()) {
            assertCapabilityPresent(helper, side);
        }
        assertCapabilityAbsent(helper, null);
        helper.succeed();
    }

    private static void maceratorTopIsInsertOnly(GameTestHelper helper) {
        configureTopInputOnly(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);

        assertInserted(helper, top, iron, 1, 1, "UP input capability should accept one iron ingot");
        assertExtracted(helper, top, iron, 1, 0, "UP input capability must reject extraction");
        assertAmount(helper, top, iron, 1, "UP input capability must still contain the inserted iron ingot");

        helper.succeed();
    }

    private static void maceratorBottomIsExtractOnly(GameTestHelper helper) {
        MachineBlockEntity machine = configureBottomOutputOnly(placeMacerator(helper));
        ItemResource dust = ironDustResource();
        fillRecipeOutput(helper, machine, dust, 1);

        ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
        assertExtracted(helper, bottom, dust, 1, 1, "DOWN output capability should extract existing iron dust");
        assertInserted(helper, bottom, ItemResource.of(Items.IRON_INGOT), 1, 0,
                "DOWN output capability must reject insertion");

        helper.succeed();
    }

    private static void maceratorProcessesIronOreTopToBottom(GameTestHelper helper) {
        chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        assertInserted(helper, top, ironOreResource(), 1, 1,
                "UP input capability should accept the recipe input");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should start WORKING one tick after iron ore input, got " + logic.state());
                    }
                    if (logic.activeRecipeId() == null || !logic.activeRecipeId().identifier().equals(IdHelper.oi("macerator/iron_crude_dust_from_ore"))) {
                        helper.fail("Macerator active recipe should be iron_crude_dust_from_ore, got " + logic.activeRecipeId());
                    }
                    moveActiveRecipeToLastTick(logic);
                })
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should return to IDLE after completing the recipe, got " + logic.state());
                    }
                    ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
                    assertExtracted(helper, bottom, ironCrudeDustResource(), 2, 2,
                            "DOWN output capability should extract two crude iron dust after processing");
                    assertRecipeInputEmpty(helper, machine(helper), "Recipe input should be consumed after completion");
                })
                .thenSucceed();
    }

    private static void maceratorWrongInputDirectionDoesNotHalfCommit(GameTestHelper helper) {
        configureTopInputOnly(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);

        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = top.extract(iron, 1, transaction);
            int inserted = top.insert(iron, 1, transaction);
            if (extracted != 0 || inserted != 0) {
                helper.fail("Poisoned UP transaction should reject extract and later insert, got extract=" + extracted + ", insert=" + inserted);
            }
            transaction.commit();
        }

        assertAmount(helper, top, iron, 0, "Poisoned UP transaction must not insert iron");
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should remain IDLE after poisoned input transaction, got " + logic(helper).state());
                    }
                })
                .thenSucceed();
    }

    private static void maceratorWrongOutputDirectionDoesNotHalfCommit(GameTestHelper helper) {
        MachineBlockEntity machine = configureBottomOutputOnly(placeMacerator(helper));
        ItemResource dust = ironDustResource();
        fillRecipeOutput(helper, machine, dust, 1);

        ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = bottom.insert(ItemResource.of(Items.IRON_INGOT), 1, transaction);
            int extracted = bottom.extract(dust, 1, transaction);
            if (inserted != 0 || extracted != 0) {
                helper.fail("Poisoned DOWN transaction should reject insert and later extract, got insert=" + inserted + ", extract=" + extracted);
            }
            transaction.commit();
        }

        assertAmount(helper, bottom, dust, 1, "Poisoned DOWN transaction must leave output dust in the machine");
        helper.succeed();
    }

    private static void maceratorOutputLongWrongDirectionPoisonsTransaction(GameTestHelper helper) {
        MachineBlockEntity machine = configureBottomOutputOnly(placeMacerator(helper));
        ItemResource dust = ironDustResource();
        fillRecipeOutput(helper, machine, dust, 1);
        LongResourceHandler<ItemResource> bottom = requireLongCapability(helper, Direction.DOWN);

        try (Transaction transaction = Transaction.openRoot()) {
            long inserted = bottom.insertLong(
                    ItemResource.of(Items.IRON_INGOT), (long) Integer.MAX_VALUE + 1L, transaction);
            long extracted = bottom.extractLong(dust, 1L, transaction);
            if (inserted != 0L || extracted != 0L) {
                helper.fail("Poisoned LONG output transaction should reject both operations, got insert=" + inserted + ", extract=" + extracted);
            }
            transaction.commit();
        }

        try (Transaction transaction = Transaction.openRoot()) {
            long extracted = bottom.extractLong(dust, 1L, transaction);
            if (extracted != 1L) {
                helper.fail("A fresh LONG transaction should extract the stored dust, got " + extracted);
            }
            transaction.commit();
        }
        helper.succeed();
    }

    private static void maceratorMixedDirectionLongTransferSkipsForbiddenDelegate(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource dust = ironDustResource();
        fillRecipeOutput(helper, machine, dust, 1);
        LongResourceHandler<ItemResource> bottom = requireLongCapability(helper, Direction.DOWN);

        try (Transaction transaction = Transaction.openRoot()) {
            long inserted = bottom.insertLong(iron, (long) Integer.MAX_VALUE + 1L, transaction);
            long extracted = bottom.extractLong(dust, 1L, transaction);
            if (inserted <= 0L || inserted >= (long) Integer.MAX_VALUE + 1L) {
                helper.fail("Mixed capability should partially fill its bounded input, got " + inserted);
            }
            if (extracted != 1L) {
                helper.fail("Known output-only delegates must be skipped during insert; same-transaction extract got " + extracted);
            }
            transaction.commit();
        }

        assertAmount(helper, bottom, dust, 0L, "Mixed LONG transaction should commit the legal output extraction");
        helper.succeed();
    }

    private static void maceratorRejectsSidelessCapability(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        fillRecipeOutput(helper, machine, ironDustResource(), 1);

        assertCapabilityAbsent(helper, null);
        helper.succeed();
    }

    private static void maceratorWaitsWhenOutputFillsDuringProcessing(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        assertInserted(helper, top, ironOreResource(), 1, 1,
                "UP input capability should accept the recipe input before output is filled");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should be WORKING before output is filled, got " + logic(helper).state());
                    }
                    moveActiveRecipeToLastTick(logic(helper));
                    fillRecipeOutput(helper, machine, ironCrudeDustResource(), 256);
                })
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WAITING_OUTPUT) {
                        helper.fail("Macerator should wait for output space when output fills during processing, got " + logic.state());
                    }
                    ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
                    assertAmount(helper, bottom, ironCrudeDustResource(), 256,
                            "Output should remain full and should not overfill while waiting");
                })
                .thenSucceed();
    }

    private static void maceratorResumesAfterOutputSpaceAvailable(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        ItemResource dust = ironCrudeDustResource();
        assertInserted(helper, top, ironOreResource(), 1, 1,
                "UP input capability should accept the recipe input before output is filled");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    moveActiveRecipeToLastTick(logic(helper));
                    fillRecipeOutput(helper, machine, dust, 256);
                })
                .thenExecuteAfter(1, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WAITING_OUTPUT) {
                        helper.fail("Macerator should be WAITING_OUTPUT before output space is freed, got " + logic(helper).state());
                    }
                    ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
                    assertExtracted(helper, bottom, dust, 2, 2,
                            "DOWN output capability should free enough space for the two-item recipe output");
                })
                .thenExecuteAfter(1, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should finish and return to IDLE after output space is freed, got " + logic(helper).state());
                    }
                    ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
                    assertAmount(helper, bottom, dust, 256,
                            "Freed output space should be refilled by the waiting recipe output");
                })
                .thenSucceed();
    }

    private static void maceratorDoesNotStartWithoutRecipe(GameTestHelper helper) {
        placeMacerator(helper);
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        ItemResource ingot = ItemResource.of(Items.IRON_INGOT);
        assertInserted(helper, top, ingot, 1, 1,
                "UP input capability should accept fine-grinding-only ingots as storage input");

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should stay IDLE for iron ingot input, got " + logic(helper).state());
                    }
                    assertAmount(helper, top, ingot, 1,
                            "Iron ingot belongs to the fine grinder and should remain in macerator input");
                })
                .thenSucceed();
    }

    private static void maceratorFailedSearchCacheInvalidatesAfterInputChanges(GameTestHelper helper) {
        chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should remain IDLE before any recipe input, got " + logic(helper).state());
                    }
                })
                .thenExecuteAfter(1, () -> assertInserted(
                        helper,
                        top,
                        ironOreResource(),
                        1,
                        1,
                        "UP input insertion should invalidate the failed-search cache"))
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should start WORKING after input changes invalidate search cache, got " + logic.state());
                    }
                    if (logic.activeRecipeId() == null || !logic.activeRecipeId().identifier().equals(IdHelper.oi("macerator/iron_crude_dust_from_ore"))) {
                        helper.fail("Macerator should select iron_crude_dust_from_ore after cache invalidation, got " + logic.activeRecipeId());
                    }
                })
                .thenSucceed();
    }

    private static void maceratorActiveBlockstateFollowsWorkingRecipe(GameTestHelper helper) {
        chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        assertActiveBlockstate(helper, false, "Newly placed macerator should render as inactive");
        assertInserted(helper, top, ironOreResource(), 1, 1,
                "UP input capability should accept the recipe input");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should be WORKING before active render assertion, got " + logic(helper).state());
                    }
                    assertActiveBlockstate(helper, true, "Macerator should render active while WORKING");
                    moveActiveRecipeToLastTick(logic(helper));
                })
                .thenExecuteAfter(1, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should return to IDLE before inactive render assertion, got " + logic(helper).state());
                    }
                    assertActiveBlockstate(helper, false, "Macerator should render inactive after recipe completion");
                })
                .thenSucceed();
    }

    private static void maceratorActiveBlockstateStaysOnWhileWaitingOutput(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        assertInserted(helper, top, ironOreResource(), 1, 1,
                "UP input capability should accept the recipe input before output is filled");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    moveActiveRecipeToLastTick(logic(helper));
                    fillRecipeOutput(helper, machine, ironCrudeDustResource(), 256);
                })
                .thenExecuteAfter(1, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WAITING_OUTPUT) {
                        helper.fail("Macerator should be WAITING_OUTPUT before active wait assertion, got " + logic(helper).state());
                    }
                    assertActiveBlockstate(helper, true,
                            "Macerator should keep active render while waiting for output space");
                })
                .thenSucceed();
    }

    private static void maceratorWaitsToStartWhenOutputAlreadyFull(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
        ItemResource iron = ironOreResource();
        ItemResource dust = ironCrudeDustResource();
        fillRecipeOutput(helper, machine, dust, 256);
        assertInserted(helper, top, iron, 1, 1,
                "UP input capability should accept the recipe input before the start gate runs");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WAITING_TICK_OUTPUT_TO_START) {
                        helper.fail("Macerator should wait to start while output is already full, got " + logic.state());
                    }
                    assertAmount(helper, top, iron, 1,
                            "Pre-start output waiting must not consume the recipe input");
                    assertActiveBlockstate(helper, false,
                            "Pre-start output waiting must not render the machine active");
                    assertExtracted(helper, bottom, dust, 2, 2,
                            "DOWN output extraction should free space for the waiting start candidate");
                })
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should start WORKING after output space is freed, got " + logic.state());
                    }
                    assertRecipeInputEmpty(helper, machine,
                            "Pre-start waiting recipe should consume input only after output space is freed");
                })
                .thenSucceed();
    }

    private static void maceratorIndexedSearchProcessesCopperOre(GameTestHelper helper) {
        chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        assertInserted(helper, top, copperOreResource(), 1, 1,
                "UP input capability should accept the indexed recipe input");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should start WORKING one tick after copper ore input, got " + logic.state());
                    }
                    if (logic.activeRecipeId() == null || !logic.activeRecipeId().identifier()
                            .equals(IdHelper.oi("macerator/copper_crude_dust_from_ore"))) {
                        helper.fail("Indexed recipe search should select copper_crude_dust_from_ore, got " + logic.activeRecipeId());
                    }
                    moveActiveRecipeToLastTick(logic);
                })
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.IDLE) {
                        helper.fail("Macerator should return to IDLE after completing copper recipe, got " + logic.state());
                    }
                    ResourceHandler<ItemResource> bottom = requireCapability(helper, Direction.DOWN);
                    assertExtracted(helper, bottom, copperCrudeDustResource(), 2, 2,
                            "DOWN output capability should extract two crude copper dust after indexed search");
                    assertRecipeInputEmpty(helper, machine(helper), "Indexed recipe input should be consumed after completion");
                })
                .thenSucceed();
    }

    private static void maceratorMachineDataPersistsWorkingRecipeAndStorage(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        assertInserted(helper, top, ironOreResource(), 1, 1,
                "UP input capability should accept the recipe input before save/load");

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should be WORKING before save/load, got " + logic.state());
                    }
                    CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
                    if (!saved.contains("machine_data")) {
                        helper.fail("Machine save should contain new machine_data root: " + saved);
                    }
                    if (saved.contains("machine_traits")) {
                        helper.fail("Machine save must not contain removed machine_traits root: " + saved);
                    }

                    CompoundTag updateTag = machine.getUpdateTag(helper.getLevel().registryAccess());
                    if (updateTag.contains(MachineDataScope.UPDATE_TAG_SYNC_KEY)) {
                        helper.fail("Recipe logic UI state should not use machine_data update-tag sync; " + "LDLib2 menu bindings own UI-open synchronization: " + updateTag);
                    }

                    BlockEntity loadedBlockEntity = BlockEntity.loadStatic(
                            machine.getBlockPos(),
                            machine.getBlockState(),
                            saved,
                            helper.getLevel().registryAccess());
                    if (!(loadedBlockEntity instanceof MachineBlockEntity loaded)) {
                        helper.fail("Saved macerator should load as MachineBlockEntity, got " + loadedBlockEntity);
                        return;
                    }
                    loaded.setLevel(helper.getLevel());
                    RecipeLogic loadedLogic = loaded.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
                    if (loadedLogic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Loaded macerator should keep WORKING state, got " + loadedLogic.state());
                    }
                    if (loadedLogic.activeRecipeId() == null || !loadedLogic.activeRecipeId().identifier()
                            .equals(IdHelper.oi("macerator/iron_crude_dust_from_ore"))) {
                        helper.fail("Loaded macerator should keep active iron recipe, got " + loadedLogic.activeRecipeId());
                    }
                    assertRecipeInputEmpty(helper, loaded,
                            "Loaded macerator input should stay consumed after machine_data load");
                })
                .thenSucceed();
    }

    private static MachineBlockEntity placeMacerator(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinOIMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        return machine(helper);
    }

    /** 给研磨机灌满启动用电——配方每 tick 抽能,无能则停在等待态。 */
    private static MachineBlockEntity chargeEnergy(MachineBlockEntity machine) {
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        storage.handler().set(0, storage.resource(), 10_000);
        return machine;
    }

    private static MachineBlockEntity machine(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static RecipeLogic logic(GameTestHelper helper) {
        return machine(helper).machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static void moveActiveRecipeToLastTick(RecipeLogic logic) {
        logic.setProgressForGameTest(logic.maxProgress() - 1);
    }

    private static @Nullable ResourceHandler<ItemResource> capability(GameTestHelper helper, @Nullable Direction side) {
        return helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE_POS), side);
    }

    private static ResourceHandler<ItemResource> requireCapability(GameTestHelper helper, Direction side) {
        ResourceHandler<ItemResource> handler = capability(helper, side);
        if (handler == null) {
            helper.fail("Expected item capability on macerator side " + side, MACHINE_POS);
        }
        return handler;
    }

    @SuppressWarnings("unchecked")
    private static LongResourceHandler<ItemResource> requireLongCapability(
                                                                           GameTestHelper helper,
                                                                           Direction side) {
        ResourceHandler<ItemResource> handler = requireCapability(helper, side);
        if (!(handler instanceof LongResourceHandler<?>)) {
            helper.fail("Expected native LONG item capability on macerator side " + side, MACHINE_POS);
            throw new IllegalStateException("helper.fail should abort");
        }
        return (LongResourceHandler<ItemResource>) handler;
    }

    private static MachineBlockEntity configureTopInputOnly(MachineBlockEntity machine) {
        ItemResourcePort output = machine.machineComponents().require(ItemResourcePort.ITEM_OUTPUT_1);
        if (!output.setSideIo(Direction.UP, AutomationIo.NONE)) {
            throw new IllegalStateException("Could not close macerator output on UP");
        }
        return machine;
    }

    private static MachineBlockEntity configureBottomOutputOnly(MachineBlockEntity machine) {
        ItemResourcePort input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        if (!input.setSideIo(Direction.DOWN, AutomationIo.NONE)) {
            throw new IllegalStateException("Could not close macerator input on DOWN");
        }
        return machine;
    }

    private static void assertCapabilityPresent(GameTestHelper helper, Direction side) {
        if (capability(helper, side) == null) {
            helper.fail("Expected item capability on macerator side " + side, MACHINE_POS);
        }
    }

    private static void assertCapabilityAbsent(GameTestHelper helper, @Nullable Direction side) {
        if (capability(helper, side) != null) {
            helper.fail("Expected no item capability on macerator side " + side, MACHINE_POS);
        }
    }

    private static ResourceHandler<ItemResource> recipeOutputHandler(GameTestHelper helper, MachineBlockEntity machine) {
        ResourceHandler<ItemResource> handler = machine.machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinOIResourceIntegrations.ITEM.resourceType(), RecipeRole.OUTPUT);
        if (handler == null) {
            helper.fail("Macerator should expose an internal recipe output item handler", MACHINE_POS);
        }
        return handler;
    }

    private static void fillRecipeOutput(
                                         GameTestHelper helper,
                                         MachineBlockEntity machine,
                                         ItemResource resource,
                                         int amount) {
        ResourceHandler<ItemResource> output = recipeOutputHandler(helper, machine);
        assertInserted(helper, output, resource, amount, amount,
                "Internal recipe output handler should accept output setup item amount " + amount);
    }

    private static void assertRecipeInputEmpty(GameTestHelper helper, MachineBlockEntity machine, String message) {
        ResourceHandler<ItemResource> input = machine.machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinOIResourceIntegrations.ITEM.resourceType(), RecipeRole.INPUT);
        if (input == null) {
            helper.fail("Macerator should expose an internal recipe input item handler", MACHINE_POS);
        }
        for (int index = 0; index < input.size(); index++) {
            if (!input.getResource(index).isEmpty() && input.getAmountAsLong(index) > 0) {
                helper.fail(message + "; slot " + index + " contains " + input.getAmountAsLong(index) + "x " + input.getResource(index));
            }
        }
    }

    private static void assertActiveBlockstate(GameTestHelper helper, boolean expected, String message) {
        BlockState state = helper.getBlockState(MACHINE_POS);
        BooleanProperty activeProperty = activeProperty(helper, state);
        boolean active = state.getValue(activeProperty);
        if (active != expected) {
            helper.fail(message + "; expected active=" + expected + ", got " + active);
        }
    }

    private static BooleanProperty activeProperty(GameTestHelper helper, BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty && booleanProperty.getName().equals("active")) {
                return booleanProperty;
            }
        }
        helper.fail("Macerator blockstate should expose boolean property 'active'", MACHINE_POS);
        throw new IllegalStateException("helper.fail should abort");
    }

    private static void assertInserted(
                                       GameTestHelper helper,
                                       ResourceHandler<ItemResource> handler,
                                       ItemResource resource,
                                       int amount,
                                       int expected,
                                       String message) {
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            inserted = handler.insert(resource, amount, transaction);
            transaction.commit();
        }
        if (inserted != expected) {
            helper.fail(message + "; expected insert=" + expected + ", got " + inserted);
        }
    }

    private static void assertExtracted(
                                        GameTestHelper helper,
                                        ResourceHandler<ItemResource> handler,
                                        ItemResource resource,
                                        int amount,
                                        int expected,
                                        String message) {
        int extracted;
        try (Transaction transaction = Transaction.openRoot()) {
            extracted = handler.extract(resource, amount, transaction);
            transaction.commit();
        }
        if (extracted != expected) {
            helper.fail(message + "; expected extract=" + expected + ", got " + extracted);
        }
    }

    private static void assertAmount(
                                     GameTestHelper helper,
                                     ResourceHandler<ItemResource> handler,
                                     ItemResource resource,
                                     long expected,
                                     String message) {
        long amount = amountOf(handler, resource);
        if (amount != expected) {
            helper.fail(message + "; expected amount=" + expected + ", got " + amount);
        }
    }

    private static long amountOf(ResourceHandler<ItemResource> handler, ItemResource resource) {
        long amount = 0;
        for (int index = 0; index < handler.size(); index++) {
            if (handler.getResource(index).equals(resource)) {
                amount += handler.getAmountAsLong(index);
            }
        }
        return amount;
    }

    private static ItemResource ironDustResource() {
        return ItemResource.of(ironDustItem());
    }

    private static ItemResource ironOreResource() {
        return ItemResource.of(MaterialHelper.requireItem(IRON, ORE));
    }

    private static ItemResource ironCrudeDustResource() {
        return ItemResource.of(MaterialHelper.requireItem(IRON, CRUDE_DUST));
    }

    private static ItemResource copperOreResource() {
        return ItemResource.of(MaterialHelper.requireItem(COPPER, ORE));
    }

    private static ItemResource copperCrudeDustResource() {
        return ItemResource.of(MaterialHelper.requireItem(COPPER, CRUDE_DUST));
    }

    private static Item ironDustItem() {
        return MaterialHelper.requireItem(IRON, DUST);
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Macerator GameTest index out of range: " + index);
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
