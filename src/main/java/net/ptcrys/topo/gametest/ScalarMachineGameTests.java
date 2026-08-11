package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.MachineComponents;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolRouter;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 标量资源机器(GameTest 夹具副本)的游戏内测试:烧煤发电、容量回压、10:1 转换、锅炉双输入、
 * 电锅炉断能停机与方块能力侧面策略。全部使用 {@link OIScalarGameTestFixtures} 的固定数值,
 * 与正式机器/配方解耦。
 */
public final class ScalarMachineGameTests {

    private static final String SUITE = "scalar_machine";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 9;

    private ScalarMachineGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!OIScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("scalar_machine"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "energy_generator_burns_coal_into_stored_energy",
                "Tests ScalarRecipeCapability tick output, ScalarResourcePort, and RecipeLogic: " + "one coal burns for the full duration and accumulates exactly per_tick*duration energy.",
                ScalarMachineGameTests::energyGeneratorBurnsCoalIntoStoredEnergy);
        register(
                event,
                environment,
                index++,
                "energy_generator_waits_when_buffer_full_and_resumes",
                "Tests RecipeLogic pre-start tick-output gating with a full scalar buffer: " + "coal is not consumed while waiting, and draining the buffer lets the burn start.",
                ScalarMachineGameTests::energyGeneratorWaitsWhenBufferFullAndResumes);
        register(
                event,
                environment,
                index++,
                "advanced_generator_converts_energy_10_to_1",
                "Tests scalar tick input + tick output in one recipe: a 100-energy preload converts to " + "exactly 10 advanced energy at the fixture 10:1 ratio and then stalls without input.",
                ScalarMachineGameTests::advancedGeneratorConvertsEnergyTenToOne);
        register(
                event,
                environment,
                index++,
                "advanced_generator_requires_energy_to_start",
                "Tests RecipeLogic tick-input start gating and mid-run stall: an empty generator waits, " + "a one-tick energy budget runs exactly one tick then waits for more input.",
                ScalarMachineGameTests::advancedGeneratorRequiresEnergyToStart,
                240);
        register(
                event,
                environment,
                index++,
                "boiler_requires_both_coal_and_water",
                "Tests multi-capability start matching (item + fluid): coal alone keeps the boiler idle, " + "adding water starts the burn and consumes both start inputs.",
                ScalarMachineGameTests::boilerRequiresBothCoalAndWater);
        register(
                event,
                environment,
                index++,
                "boiler_produces_heat_over_duration",
                "Tests scalar heat tick output fed by item+fluid start inputs: one coal plus 100mB water " + "produce exactly per_tick*duration heat.",
                ScalarMachineGameTests::boilerProducesHeatOverDuration);
        register(
                event,
                environment,
                index++,
                "electric_boiler_converts_and_stalls_when_drained",
                "Tests WAITING_TICK_INPUT_TO_PROCESS recovery on a pure tick recipe: the electric boiler stalls " + "when energy runs out mid-recipe and finishes after a refill with exact totals.",
                ScalarMachineGameTests::electricBoilerConvertsAndStallsWhenDrained,
                240);
        register(
                event,
                environment,
                index++,
                "remembered_recipe_gate_failure_retries_traditional_order",
                "Tests pool-local recipe history through a real RecipeLogic tick: a remembered input match whose " + "tick output is blocked must retry the same pool's traditional order and start its viable recipe.",
                ScalarMachineGameTests::rememberedRecipeGateFailureRetriesTraditionalOrder);
        register(
                event,
                environment,
                index,
                "scalar_block_capability_respects_port_sides",
                "Tests scalar BlockCapability exposure through PortAccess sides: the generator energy " + "buffer is extract-only on DOWN and absent on every other side.",
                ScalarMachineGameTests::scalarBlockCapabilityRespectsPortSides);
    }

    private static void energyGeneratorBurnsCoalIntoStoredEnergy(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.energyGenerator());
        insertItem(helper, machine, 1);

        int total = OIScalarGameTestFixtures.GENERATOR_ENERGY_PER_TICK * OIScalarGameTestFixtures.GENERATOR_DURATION;
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WORKING) {
                        helper.fail("Generator should be WORKING shortly after coal input, got " + logic(helper).state());
                    }
                    assertItemCount(helper, machine, 0, "Coal must be consumed when the burn starts");
                })
                .thenExecuteAfter(OIScalarGameTestFixtures.GENERATOR_DURATION + 10, () -> {
                    assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_OUTPUT_1, total,
                            "One coal burn must accumulate exactly " + total + " energy");
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Generator should be IDLE after the burn with no fuel left, got " + logic(helper).state());
                    }
                })
                .thenSucceed();
    }

    private static void energyGeneratorWaitsWhenBufferFullAndResumes(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.energyGenerator());
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_OUTPUT_1,
                BuiltinOIResourceIntegrations.ENERGY, OIScalarGameTestFixtures.GENERATOR_ENERGY_CAPACITY);
        insertItem(helper, machine, 1);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WAITING_TICK_OUTPUT_TO_START) {
                        helper.fail("Generator with a full buffer should wait to start, got " + logic(helper).state());
                    }
                    assertItemCount(helper, machine, 1, "Waiting to start must not consume the coal");
                    ResourceHandler<ScalarResource> down = requireScalarCapability(
                            helper, BuiltinOIResourceIntegrations.ENERGY, Direction.DOWN);
                    extractScalar(helper, down, BuiltinOIResourceIntegrations.ENERGY, 50,
                            "DOWN energy capability should drain half of the full buffer");
                })
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WORKING) {
                        helper.fail("Generator should start WORKING after the buffer was drained, got " + logic(helper).state());
                    }
                    assertItemCount(helper, machine, 0, "Coal must be consumed once the burn actually starts");
                })
                .thenSucceed();
    }

    private static void advancedGeneratorConvertsEnergyTenToOne(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.advancedGenerator());
        int totalEnergy = OIScalarGameTestFixtures.ADVANCED_ENERGY_IN_PER_TICK * OIScalarGameTestFixtures.ADVANCED_DURATION;
        int totalAdvanced = OIScalarGameTestFixtures.ADVANCED_OUT_PER_TICK * OIScalarGameTestFixtures.ADVANCED_DURATION;
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_INPUT_1,
                BuiltinOIResourceIntegrations.ENERGY, totalEnergy);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WORKING) {
                        helper.fail("Advanced generator should be WORKING after the energy preload, got " + logic(helper).state());
                    }
                })
                .thenExecuteAfter(OIScalarGameTestFixtures.ADVANCED_DURATION + 10, () -> {
                    assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_INPUT_1, 0,
                            "The preloaded energy must be fully consumed at the 10:1 ratio");
                    assertScalarAmount(helper, machine, ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1, totalAdvanced,
                            "Exactly " + totalAdvanced + " advanced energy must be produced from " + totalEnergy + " energy");
                    if (logic(helper).state() != RecipeLogic.State.WAITING_TICK_INPUT_TO_START) {
                        helper.fail("Advanced generator should wait for tick input once energy is gone, got " + logic(helper).state());
                    }
                })
                .thenSucceed();
    }

    private static void advancedGeneratorRequiresEnergyToStart(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.advancedGenerator());

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WAITING_TICK_INPUT_TO_START) {
                        helper.fail("An empty advanced generator must wait for tick input, got " + logic(helper).state());
                    }
                    assertScalarAmount(helper, machine, ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1, 0,
                            "No advanced energy may be produced without energy input");
                    fillScalar(helper, machine, ScalarResourcePort.ENERGY_INPUT_1,
                            BuiltinOIResourceIntegrations.ENERGY,
                            OIScalarGameTestFixtures.ADVANCED_ENERGY_IN_PER_TICK);
                })
                .thenExecuteAfter(6, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WAITING_TICK_INPUT_TO_PROCESS) {
                        helper.fail("A one-tick energy budget should stall the run mid-recipe, got " + logic(helper).state());
                    }
                    assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_INPUT_1, 0,
                            "The one-tick energy budget must be consumed");
                    assertScalarAmount(helper, machine, ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1,
                            OIScalarGameTestFixtures.ADVANCED_OUT_PER_TICK,
                            "Exactly one tick of advanced energy must be produced before the stall");
                })
                .thenSucceed();
    }

    private static void boilerRequiresBothCoalAndWater(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.boiler());
        insertItem(helper, machine, 1);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("Boiler with coal but no water must stay IDLE, got " + logic(helper).state());
                    }
                    assertItemCount(helper, machine, 1, "Idle boiler must not consume the coal");
                    fillWater(helper, machine, 500);
                })
                .thenExecuteAfter(2, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WORKING) {
                        helper.fail("Boiler should start once both coal and water are present, got " + logic(helper).state());
                    }
                    assertItemCount(helper, machine, 0, "Starting the boiler must consume the coal");
                    assertWaterAmount(helper, machine, 500 - OIScalarGameTestFixtures.BOILER_WATER_MB,
                            "Starting the boiler must consume exactly the recipe water");
                })
                .thenSucceed();
    }

    private static void boilerProducesHeatOverDuration(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.boiler());
        insertItem(helper, machine, 1);
        fillWater(helper, machine, 500);

        int totalHeat = OIScalarGameTestFixtures.BOILER_HEAT_PER_TICK * OIScalarGameTestFixtures.BOILER_DURATION;
        helper.startSequence()
                .thenExecuteAfter(OIScalarGameTestFixtures.BOILER_DURATION + 10, () -> {
                    assertScalarAmount(helper, machine, ScalarResourcePort.HEAT_OUTPUT_1, totalHeat,
                            "One boiler run must produce exactly " + totalHeat + " heat");
                    assertWaterAmount(helper, machine, 500 - OIScalarGameTestFixtures.BOILER_WATER_MB,
                            "One boiler run must consume exactly one recipe charge of water");
                    assertItemCount(helper, machine, 0, "One boiler run must consume the coal");
                })
                .thenSucceed();
    }

    private static void electricBoilerConvertsAndStallsWhenDrained(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.electricBoiler());
        int perTickIn = OIScalarGameTestFixtures.ELECTRIC_ENERGY_IN_PER_TICK;
        int perTickOut = OIScalarGameTestFixtures.ELECTRIC_HEAT_PER_TICK;
        int duration = OIScalarGameTestFixtures.ELECTRIC_DURATION;
        int partialTicks = 2;
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_INPUT_1,
                BuiltinOIResourceIntegrations.ENERGY, perTickIn * partialTicks);

        helper.startSequence()
                .thenExecuteAfter(partialTicks + 4, () -> {
                    if (logic(helper).state() != RecipeLogic.State.WAITING_TICK_INPUT_TO_PROCESS) {
                        helper.fail("Electric boiler should stall mid-recipe when energy runs out, got " + logic(helper).state());
                    }
                    assertScalarAmount(helper, machine, ScalarResourcePort.HEAT_OUTPUT_1, perTickOut * partialTicks,
                            "Heat before the stall must match the partial energy budget");
                    assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_INPUT_1, 0,
                            "The partial energy budget must be fully consumed at the stall");
                    fillScalar(helper, machine, ScalarResourcePort.ENERGY_INPUT_1,
                            BuiltinOIResourceIntegrations.ENERGY, perTickIn * (duration - partialTicks));
                })
                .thenExecuteAfter(duration + 10, () -> {
                    assertScalarAmount(helper, machine, ScalarResourcePort.HEAT_OUTPUT_1, perTickOut * duration,
                            "Resumed run must finish with exactly " + (perTickOut * duration) + " heat");
                    assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_INPUT_1, 0,
                            "Resumed run must consume the full refill");
                    if (logic(helper).state() != RecipeLogic.State.WAITING_TICK_INPUT_TO_START) {
                        helper.fail("Electric boiler should wait for tick input after finishing, got " + logic(helper).state());
                    }
                })
                .thenSucceed();
    }

    private static void scalarBlockCapabilityRespectsPortSides(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.energyGenerator());
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_OUTPUT_1,
                BuiltinOIResourceIntegrations.ENERGY, 50);

        ResourceHandler<ScalarResource> down = requireScalarCapability(
                helper, BuiltinOIResourceIntegrations.ENERGY, Direction.DOWN);
        extractScalar(helper, down, BuiltinOIResourceIntegrations.ENERGY, 20,
                "DOWN energy capability should extract stored energy");
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = down.insert(
                    BuiltinOIResourceIntegrations.ENERGY.recipeCapability().resource(), 5, transaction);
            if (inserted != 0) {
                helper.fail("Output-only DOWN energy capability must reject insertion, inserted " + inserted);
            }
            transaction.commit();
        }
        for (Direction side : new Direction[] { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST,
                Direction.WEST }) {
            if (scalarCapability(helper, BuiltinOIResourceIntegrations.ENERGY, side) != null) {
                helper.fail("Energy capability must be absent on side " + side);
            }
        }
        if (scalarCapability(helper, BuiltinOIResourceIntegrations.ENERGY, null) != null) {
            helper.fail("Energy capability must be absent for the null side");
        }
        assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_OUTPUT_1, 30,
                "Stored energy must reflect only the committed DOWN extraction");
        helper.succeed();
    }

    private static void rememberedRecipeGateFailureRetriesTraditionalOrder(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, OIScalarGameTestFixtures.historyGateFallback());
        insertItem(helper, machine, Items.COAL, 1);

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (logic(helper).state() != RecipeLogic.State.IDLE) {
                        helper.fail("The one-tick history seed recipe should be complete, got " + logic(helper).state());
                    }
                    assertScalarAmount(
                            helper,
                            machine,
                            ScalarResourcePort.ENERGY_OUTPUT_1,
                            OIScalarGameTestFixtures.HISTORY_GATE_ENERGY,
                            "The history seed recipe must fill the output gate");
                    insertItem(helper, machine, Items.COAL, 1);
                    insertItem(helper, machine, Items.REDSTONE, 1);

                    MachineComponents components = machine.machineComponents();
                    var recipeType = OIScalarGameTestFixtures.historyGateFallbackType();
                    RecipeSearchPoolRouter.SearchHit remembered = components.recipeSearchPoolRouter()
                            .search(
                                    machine,
                                    List.of(recipeType),
                                    helper.getLevel().getGameTime(),
                                    components::openRecipePool);
                    if (remembered == null || remembered.source() != RecipeSearchPoolRouter.SearchSource.HISTORY || !remembered.holder().id().identifier().equals(IdHelper.oi(
                            "gametest_history_gate_fallback/b_history_coal_with_tick_output"))) {
                        helper.fail("Expected the pool to prioritize its learned coal recipe, got " + remembered);
                    }

                    try (MachineComponents.RecipePoolScope scope = components.openRecipePool(RecipeSearchPoolId.DEFAULT)) {
                        var traditional = recipeType.findRecipe(machine);
                        if (traditional == null || !traditional.id().identifier().equals(IdHelper.oi(
                                "gametest_history_gate_fallback/a_unblocked_coal_and_redstone"))) {
                            helper.fail("Traditional order must select the viable two-input recipe, got " + traditional);
                        }
                    }
                    assertItemCount(helper, machine, 2, "Search probes must not consume either second-run input");
                })
                .thenExecuteAfter(2, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING || logic.activeRecipeId() == null || !logic.activeRecipeId().identifier().equals(IdHelper.oi(
                            "gametest_history_gate_fallback/a_unblocked_coal_and_redstone"))) {
                        helper.fail("RecipeLogic must recover traditional order after the history gate failure; state=" + logic.state() + ", recipe=" + logic.activeRecipeId());
                    }
                    assertItemCount(helper, machine, 0, "Fallback start must consume both inputs exactly once");
                    assertScalarAmount(
                            helper,
                            machine,
                            ScalarResourcePort.ENERGY_OUTPUT_1,
                            OIScalarGameTestFixtures.HISTORY_GATE_ENERGY,
                            "The rejected history candidate must not emit additional energy");
                })
                .thenSucceed();
    }

    private static MachineBlockEntity place(GameTestHelper helper, MachineDefinition definition) {
        helper.setBlock(MACHINE_POS, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static RecipeLogic logic(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class)
                .machineComponents()
                .require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static void insertItem(GameTestHelper helper, MachineBlockEntity machine, int coalCount) {
        insertItem(helper, machine, Items.COAL, coalCount);
    }

    private static void insertItem(
                                   GameTestHelper helper, MachineBlockEntity machine, net.minecraft.world.item.Item item, int count) {
        ItemResourcePort input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.handler().insert(ItemResource.of(item), count, transaction);
            if (inserted != count) {
                helper.fail("Setup: expected to insert " + count + " " + item + ", inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void assertItemCount(
                                        GameTestHelper helper,
                                        MachineBlockEntity machine,
                                        int expected,
                                        String message) {
        ItemResourcePort input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        int total = input.computeItemTotal();
        if (total != expected) {
            helper.fail(message + " (expected " + expected + " items, found " + total + ")");
        }
    }

    private static void fillWater(GameTestHelper helper, MachineBlockEntity machine, int amount) {
        FluidResourcePort tank = machine.machineComponents().require(FluidResourcePort.FLUID_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = tank.handler().insert(FluidResource.of(Fluids.WATER), amount, transaction);
            if (inserted != amount) {
                helper.fail("Setup: expected to insert " + amount + "mB water, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void assertWaterAmount(
                                          GameTestHelper helper,
                                          MachineBlockEntity machine,
                                          int expected,
                                          String message) {
        FluidResourcePort tank = machine.machineComponents().require(FluidResourcePort.FLUID_INPUT_1);
        long amount = tank.handler().getAmountAsLong(0);
        if (amount != expected) {
            helper.fail(message + " (expected " + expected + "mB, found " + amount + "mB)");
        }
    }

    private static void fillScalar(
                                   GameTestHelper helper,
                                   MachineBlockEntity machine,
                                   ComponentKey<ScalarResourcePort> key,
                                   BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                   int amount) {
        ScalarResourcePort storage = machine.machineComponents().require(key);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = storage.handler().insert(integration.recipeCapability().resource(), amount, transaction);
            if (inserted != amount) {
                helper.fail("Setup: expected to preload " + amount + " of " + integration.recipeCapability().resource().id() + ", inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void assertScalarAmount(
                                           GameTestHelper helper,
                                           MachineBlockEntity machine,
                                           ComponentKey<ScalarResourcePort> key,
                                           long expected,
                                           String message) {
        ScalarResourcePort storage = machine.machineComponents().require(key);
        long amount = storage.storedAmount();
        if (amount != expected) {
            helper.fail(message + " (expected " + expected + ", found " + amount + ")");
        }
    }

    private static void extractScalar(
                                      GameTestHelper helper,
                                      ResourceHandler<ScalarResource> handler,
                                      BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                      int amount,
                                      String message) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(integration.recipeCapability().resource(), amount, transaction);
            if (extracted != amount) {
                helper.fail(message + " (expected " + amount + ", extracted " + extracted + ")");
            }
            transaction.commit();
        }
    }

    private static @Nullable ResourceHandler<ScalarResource> scalarCapability(
                                                                              GameTestHelper helper,
                                                                              BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                                              @Nullable Direction side) {
        BlockCapability<ResourceHandler<ScalarResource>, @Nullable Direction> capability = integration.resourceType().blockCapability();
        if (capability == null) {
            helper.fail("Scalar resource " + integration.recipeCapability().resource().id() + " has no block capability");
            return null;
        }
        return helper.getLevel().getCapability(capability, helper.absolutePos(MACHINE_POS), side);
    }

    private static ResourceHandler<ScalarResource> requireScalarCapability(
                                                                           GameTestHelper helper,
                                                                           BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                                           Direction side) {
        ResourceHandler<ScalarResource> handler = scalarCapability(helper, integration, side);
        if (handler == null) {
            helper.fail("Expected scalar capability on side " + side, MACHINE_POS);
        }
        return handler;
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        register(event, environment, index, name, description, test, 120);
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test,
                                 int maxTicks) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Scalar machine GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
