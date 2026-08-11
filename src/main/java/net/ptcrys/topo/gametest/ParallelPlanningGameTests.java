package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.resource.ResourceHandlerLongOps;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.content.OIFluidIngredient;
import net.ptcrys.topo.apiv2.recipe.content.OIItemInput;
import net.ptcrys.topo.apiv2.recipe.content.OIItemOutput;
import net.ptcrys.topo.apiv2.recipe.search.OIRecipeSearchIndex;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Correctness guards for the allocation-based maximum-parallel planner on real machine ports. */
public final class ParallelPlanningGameTests {

    private static final String SUITE = "parallel_planning";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 17;
    private static final int COMPLEX_POOL_RESOURCE_COUNT = 48;
    private static final long COMPLEX_POOL_PARALLEL = 64L;
    private static final Item[] COMPLEX_POOL_ITEMS = {
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.COPPER_INGOT,
            Items.REDSTONE,
            Items.COAL,
            Items.DIAMOND,
            Items.EMERALD,
            Items.LAPIS_LAZULI,
            Items.QUARTZ,
            Items.AMETHYST_SHARD,
            Items.BRICK,
            Items.CLAY_BALL,
            Items.FLINT,
            Items.CHARCOAL,
            Items.RAW_IRON,
            Items.RAW_GOLD,
            Items.RAW_COPPER,
            Items.NETHERITE_SCRAP,
            Items.GLOWSTONE_DUST,
            Items.GUNPOWDER,
            Items.BLAZE_POWDER,
            Items.BONE_MEAL,
            Items.STRING,
            Items.LEATHER,
            Items.PAPER,
            Items.SUGAR,
            Items.SLIME_BALL,
            Items.ENDER_PEARL,
            Items.PRISMARINE_SHARD,
            Items.PRISMARINE_CRYSTALS,
            Items.NAUTILUS_SHELL,
            Items.HEART_OF_THE_SEA,
            Items.ECHO_SHARD,
            Items.COBBLESTONE,
            Items.STONE,
            Items.GRANITE,
            Items.DIORITE,
            Items.ANDESITE,
            Items.DEEPSLATE,
            Items.TUFF,
            Items.CALCITE,
            Items.SAND,
            Items.RED_SAND,
            Items.GRAVEL,
            Items.DIRT,
            Items.MUD,
            Items.NETHERRACK,
            Items.SOUL_SAND
    };

    private static final ItemRecipeCapability ITEMS = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
    private static final FluidRecipeCapability FLUIDS = BuiltinOIResourceIntegrations.FLUID.recipeCapability();
    private static final ScalarRecipeCapability ENERGY = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
    private static final ScalarRecipeCapability HEAT = BuiltinOIResourceIntegrations.HEAT.recipeCapability();

    private ParallelPlanningGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("parallel_planning"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "parallel_planner_exact_and_anyof_are_order_independent",
                "A flexible AnyOf declared before an exact input must not steal the exact input's only resource.",
                ParallelPlanningGameTests::exactAndAnyOfAreOrderIndependent);
        register(
                event,
                environment,
                index++,
                "parallel_planner_aggregates_split_stacks_and_duplicate_lane_entries",
                "Duplicate declarations on one lane share one storage snapshot and one aggregate demand.",
                ParallelPlanningGameTests::splitStacksAndDuplicateLaneEntriesAggregate);
        register(
                event,
                environment,
                index++,
                "parallel_planner_shared_candidates_obey_min_cut",
                "Overlapping AnyOf inputs must honor their shared candidate capacity rather than double-count it.",
                ParallelPlanningGameTests::sharedCandidatesObeyMinCut);
        register(
                event,
                environment,
                index++,
                "parallel_planner_unconsumed_input_is_reserved_once",
                "A catalyst remains present and is never multiplied or extracted by a parallel batch.",
                ParallelPlanningGameTests::unconsumedInputIsReservedOnce);
        register(
                event,
                environment,
                index++,
                "parallel_planner_aggregates_fluid_and_scalar_lanes",
                "Split fluid tanks and repeated scalar/fluid lane entries produce one exact maximum bound per capability.",
                ParallelPlanningGameTests::fluidAndScalarLanesAggregate);
        register(
                event,
                environment,
                index++,
                "parallel_scaling_multiplies_all_io_but_not_duration",
                "Parallel scales start, tick, and output amounts while preserving duration and catalyst count.",
                ParallelPlanningGameTests::parallelScalingMultipliesIoButNotDuration);
        register(
                event,
                environment,
                index++,
                "parallel_planner_preserves_long_quantities",
                "Planning and exact scaling preserve quantities and factors above Integer.MAX_VALUE.",
                ParallelPlanningGameTests::parallelPlannerPreservesLongQuantities);
        register(
                event,
                environment,
                index++,
                "parallel_planner_preserves_aggregate_long_quantities",
                "Shared allocation stays exact when aggregate demand exceeds Long.MAX_VALUE.",
                ParallelPlanningGameTests::parallelPlannerPreservesAggregateLongQuantities);
        register(
                event,
                environment,
                index++,
                "parallel_planner_bounds_int_only_long_adapter",
                "The NeoForge int-only compatibility adapter performs a bounded number of chunks for a LONG request.",
                ParallelPlanningGameTests::intOnlyLongAdapterIsBounded);
        register(
                event,
                environment,
                index++,
                "parallel_planner_tick_precheck_is_transaction_free",
                "A non-direct item tick-input precheck remains a pure snapshot calculation inside a caller-owned transaction.",
                ParallelPlanningGameTests::tickPrecheckIsTransactionFree);
        register(
                event,
                environment,
                index++,
                "parallel_planner_groups_duplicate_direct_tick_lanes",
                "Repeated scalar tick entries are checked and applied as one aggregate lane demand.",
                ParallelPlanningGameTests::duplicateDirectTickLanesAreGrouped);
        register(
                event,
                environment,
                index++,
                "parallel_planner_same_lane_tick_exchange_reuses_extracted_capacity",
                "A full shared scalar buffer can extract and reinsert in one transaction-free tick plan.",
                ParallelPlanningGameTests::sameLaneTickExchangeReusesExtractedCapacity);
        register(
                event,
                environment,
                index++,
                "parallel_planner_recipe_index_duplicate_catalysts_use_max_reservation",
                "Indexed search must aggregate duplicate presence-only item inputs like the exact planner.",
                ParallelPlanningGameTests::recipeIndexDuplicateCatalystsUseMaxReservation);
        register(
                event,
                environment,
                index++,
                "fluid_planner_snapshots_complex_pool_once",
                "Exact-fluid planning snapshots a complex handler once and aggregates duplicate demands without transactions.",
                ParallelPlanningGameTests::fluidPlannerSnapshotsComplexPoolOnce);
        register(
                event,
                environment,
                index++,
                "parallel_planners_reject_pathological_dimensions",
                "Item and fluid planners reject oversized input and handler dimensions before reading handler contents.",
                ParallelPlanningGameTests::parallelPlannersRejectPathologicalDimensions);
        register(
                event,
                environment,
                index++,
                "fluid_planner_rejects_aggregate_long_overflow",
                "Duplicate fluid demands that exceed LONG range fail closed without mutating storage.",
                ParallelPlanningGameTests::fluidPlannerRejectsAggregateLongOverflow);
        register(
                event,
                environment,
                index,
                "output_planners_preserve_declared_order",
                "Item and fluid output snapshots reserve constrained empty slots in declared content order.",
                ParallelPlanningGameTests::outputPlannersPreserveDeclaredOrder);
    }

    private static void exactAndAnyOfAreOrderIndependent(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setItem(machine, 0, Items.IRON_INGOT, 4);
        setItem(machine, 1, Items.GOLD_INGOT, 4);
        OIRecipe recipe = recipe(new OIRecipe.InputEntry<?>[] {
                new OIRecipe.InputEntry<>(ITEMS, List.of(
                        anyOf(1, Items.IRON_INGOT, Items.GOLD_INGOT),
                        OIItemInput.of(Items.IRON_INGOT, 1)))
        });

        assertParallelInsideCallerTransaction(helper, recipe, machine, 4, 4);
        helper.succeed();
    }

    private static void splitStacksAndDuplicateLaneEntriesAggregate(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setItem(machine, 0, Items.IRON_INGOT, 30);
        setItem(machine, 1, Items.IRON_INGOT, 30);
        OIRecipe recipe = recipe(new OIRecipe.InputEntry<?>[] {
                new OIRecipe.InputEntry<>(ITEMS, List.of(OIItemInput.of(Items.IRON_INGOT, 2))),
                new OIRecipe.InputEntry<>(ITEMS, List.of(OIItemInput.of(Items.IRON_INGOT, 3)))
        });

        assertParallel(helper, recipe, machine, 32, 12);
        helper.succeed();
    }

    private static void sharedCandidatesObeyMinCut(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setItem(machine, 0, Items.IRON_INGOT, 4);
        setItem(machine, 1, Items.GOLD_INGOT, 4);
        OIRecipe recipe = recipe(new OIRecipe.InputEntry<?>[] {
                new OIRecipe.InputEntry<>(ITEMS, List.of(
                        anyOf(3, Items.IRON_INGOT, Items.GOLD_INGOT),
                        anyOf(3, Items.IRON_INGOT, Items.GOLD_INGOT)))
        });

        assertParallel(helper, recipe, machine, 2, 1);
        helper.succeed();
    }

    private static void unconsumedInputIsReservedOnce(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setItem(machine, 0, Items.COAL, 16);
        setItem(machine, 1, Items.CRAFTING_TABLE, 1);
        OIRecipe recipe = recipe(new OIRecipe.InputEntry<?>[] {
                new OIRecipe.InputEntry<>(ITEMS, List.of(
                        OIItemInput.of(Items.COAL, 2),
                        OIItemInput.unconsumed(Items.CRAFTING_TABLE)))
        });

        assertParallel(helper, recipe, machine, 16, 8);
        OIRecipe parallel = recipe.withParallel(8);
        Object catalyst = parallel.inputs()[0].contents().get(1);
        if (!(catalyst instanceof OIItemInput input) || input.count() != 1 || input.consumesOnMatch()) {
            helper.fail("Parallel scaling must keep the catalyst as one unconsumed item, got " + catalyst);
        }
        if (!parallel.consumeInputs(machine)) {
            helper.fail("The preplanned 8-parallel item batch should commit successfully");
        }
        assertStoredItem(helper, machine, Items.COAL, 0);
        assertStoredItem(helper, machine, Items.CRAFTING_TABLE, 1);
        helper.succeed();
    }

    private static void fluidAndScalarLanesAggregate(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        FluidResource water = FluidResource.of(Fluids.WATER);
        FluidResourcePort fluids = machine.machineComponents().require(FluidResourcePort.FLUID_INPUT_1);
        fluids.handler().set(0, water, 8_000);
        fluids.handler().set(1, water, 7_000);
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), 60_000);

        OIRecipe recipe = recipe(new OIRecipe.InputEntry<?>[] {
                new OIRecipe.InputEntry<>(FLUIDS, List.of(fluid(Fluids.WATER, 1_000))),
                new OIRecipe.InputEntry<>(FLUIDS, List.of(fluid(Fluids.WATER, 500))),
                new OIRecipe.InputEntry<>(ENERGY, List.of(2_000L)),
                new OIRecipe.InputEntry<>(ENERGY, List.of(1_000L))
        });

        assertParallel(helper, recipe, machine, 64, 10);
        helper.succeed();
    }

    private static void parallelScalingMultipliesIoButNotDuration(GameTestHelper helper) {
        OIRecipe base = new OIRecipe(
                BuiltinOIRecipeTypes.CHEMICAL_REACTOR,
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ITEMS, List.of(
                                OIItemInput.of(Items.COAL, 2),
                                OIItemInput.unconsumed(Items.CRAFTING_TABLE)))
                },
                new OIRecipe.OutputEntry<?>[] {
                        new OIRecipe.OutputEntry<>(ITEMS, List.of(OIItemOutput.of(Items.GOLD_INGOT, 3)))
                },
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ENERGY, List.of(5L))
                },
                new OIRecipe.OutputEntry<?>[] {
                        new OIRecipe.OutputEntry<>(HEAT, List.of(7L))
                },
                40);
        OIRecipe scaled = base.withParallel(3);

        assertAmount(helper, scaled.inputs()[0].contents().get(0), 6, "start input");
        assertAmount(helper, scaled.inputs()[0].contents().get(1), 1, "unconsumed input");
        assertAmount(helper, scaled.outputs()[0].contents().get(0), 9, "start output");
        assertLong(helper, scaled.tickInputs()[0].contents().get(0), 15L, "tick input");
        assertLong(helper, scaled.tickOutputs()[0].contents().get(0), 21L, "tick output");
        if (scaled.duration() != 40) {
            helper.fail("Parallel scaling must preserve duration 40, got " + scaled.duration());
        }
        helper.succeed();
    }

    private static void parallelPlannerPreservesLongQuantities(GameTestHelper helper) {
        long demand = (long) Integer.MAX_VALUE + 17L;
        long supply = demand * 3L;
        LongSupplyItemHandler handler = new LongSupplyItemHandler(ItemResource.of(Items.IRON_INGOT), supply);
        long planned = ItemRecipeCapability.maxParallelByInputs(
                handler,
                List.of(OIItemInput.of(Items.IRON_INGOT, demand)),
                Long.MAX_VALUE);
        if (planned != 3L) {
            helper.fail("Long item demand should plan exactly 3 parallels, got " + planned);
        }

        long factor = 3_000_000_000L;
        OIRecipe scaled = new OIRecipe(
                BuiltinOIRecipeTypes.CHEMICAL_REACTOR,
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ITEMS, List.of(OIItemInput.of(Items.COAL, 2L)))
                },
                new OIRecipe.OutputEntry<?>[] {
                        new OIRecipe.OutputEntry<>(ITEMS, List.of(OIItemOutput.of(Items.GOLD_INGOT, 3L)))
                },
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(FLUIDS, List.of(fluid(Fluids.WATER, 1_000)))
                },
                new OIRecipe.OutputEntry<?>[] {
                        new OIRecipe.OutputEntry<>(ENERGY, List.of(5L))
                },
                40)
                .withParallel(factor);
        assertAmount(helper, scaled.inputs()[0].contents().getFirst(), 6_000_000_000L, "long start input");
        assertAmount(helper, scaled.outputs()[0].contents().getFirst(), 9_000_000_000L, "long start output");
        assertLong(helper, ((OIFluidIngredient) scaled.tickInputs()[0].contents().getFirst()).amount(),
                3_000_000_000_000L, "long tick fluid input");
        assertLong(helper, scaled.tickOutputs()[0].contents().getFirst(), 15_000_000_000L,
                "long tick scalar output");
        if (scaled.duration() != 40) {
            helper.fail("Long parallel scaling must preserve duration 40, got " + scaled.duration());
        }

        OIRecipe overflow = new OIRecipe(
                BuiltinOIRecipeTypes.CHEMICAL_REACTOR,
                OIRecipe.EMPTY_INPUTS,
                new OIRecipe.OutputEntry<?>[] {
                        new OIRecipe.OutputEntry<>(ENERGY, List.of(Long.MAX_VALUE / 2L + 1L))
                },
                20);
        if (overflow.maxParallelByInputs(null, Long.MAX_VALUE) != 1L) {
            helper.fail("Output representation must cap long parallel before overflow");
        }
        try {
            overflow.withParallel(2L);
            helper.fail("Overflowing long parallel scaling must fail");
        } catch (IllegalArgumentException expected) {
            // Expected exact-overflow guard.
        }
        long exactDecimalScale = ENERGY.scaleOutput(Long.MAX_VALUE / 2L, 2.0d);
        if (exactDecimalScale != Long.MAX_VALUE - 1L) {
            helper.fail("Decimal scalar scaling lost LONG precision: " + exactDecimalScale);
        }
        try {
            ENERGY.scaleOutput(Long.MAX_VALUE / 2L + 1L, 2.0d);
            helper.fail("Overflowing decimal scalar scaling must fail");
        } catch (IllegalArgumentException expected) {
            // Expected exact BigDecimal overflow guard.
        }
        helper.succeed();
    }

    private static void parallelPlannerPreservesAggregateLongQuantities(GameTestHelper helper) {
        Item[] candidates = {
                Items.IRON_INGOT,
                Items.GOLD_INGOT,
                Items.COPPER_INGOT,
                Items.REDSTONE,
                Items.COAL,
                Items.DIAMOND,
                Items.EMERALD,
                Items.LAPIS_LAZULI,
                Items.QUARTZ,
                Items.AMETHYST_SHARD,
                Items.BRICK,
                Items.CLAY_BALL,
                Items.FLINT
        };
        ItemResource[] resources = java.util.Arrays.stream(candidates)
                .map(ItemResource::of)
                .toArray(ItemResource[]::new);
        long demand = Long.MAX_VALUE / 2L;
        OIItemInput shared = new OIItemInput.AnyOf(
                java.util.Arrays.stream(candidates)
                        .map(item -> new ItemStackTemplate(item, 1))
                        .toList(),
                demand);
        long planned = ItemRecipeCapability.maxParallelByInputs(
                new LongSupplyItemHandler(resources, Long.MAX_VALUE),
                java.util.Collections.nCopies(candidates.length, shared),
                2L);
        if (planned != 2L) {
            helper.fail("Aggregate-long shared allocation should plan exactly 2 parallels, got " + planned);
        }

        OIItemInput exact = OIItemInput.of(Items.IRON_INGOT, demand);
        long splitPlanned = ItemRecipeCapability.maxParallelByInputs(
                new LongSupplyItemHandler(
                        new ItemResource[] {
                                ItemResource.of(Items.IRON_INGOT),
                                ItemResource.of(Items.IRON_INGOT)
                        },
                        Long.MAX_VALUE),
                List.of(exact, exact, exact),
                2L);
        if (splitPlanned != 1L) {
            helper.fail("Split aggregate-long exact stacks should plan exactly 1 parallel, got " + splitPlanned);
        }
        helper.succeed();
    }

    private static void intOnlyLongAdapterIsBounded(GameTestHelper helper) {
        CountingIntItemHandler handler = new CountingIntItemHandler();
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        long moved;
        try (Transaction transaction = Transaction.openRoot()) {
            moved = ResourceHandlerLongOps.insert(handler, 0, iron, Long.MAX_VALUE, transaction);
        }
        long expected = 16L * Integer.MAX_VALUE;
        if (moved != expected || handler.calls != 16) {
            helper.fail("Int-only LONG adapter must stop at 16 full chunks; moved=" + moved + ", calls=" + handler.calls);
        }
        helper.succeed();
    }

    private static void tickPrecheckIsTransactionFree(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setItem(machine, 0, Items.COAL, 1);
        OIRecipe recipe = new OIRecipe(
                BuiltinOIRecipeTypes.CHEMICAL_REACTOR,
                OIRecipe.EMPTY_INPUTS,
                OIRecipe.EMPTY_OUTPUTS,
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ITEMS, List.of(OIItemInput.of(Items.COAL, 1)))
                },
                OIRecipe.EMPTY_OUTPUTS,
                40);
        if (recipe.hasDirectTickIo()) {
            helper.fail("Item tick IO must exercise the non-direct pure precheck path");
        }
        try (Transaction ignored = Transaction.openRoot()) {
            if (recipe.checkTickIo(machine) != OIRecipe.TickIoResult.SUCCESS) {
                helper.fail("One stored coal should satisfy the item tick precheck");
            }
            if (Transaction.getLifecycle() != Transaction.Lifecycle.OPEN) {
                helper.fail("Tick precheck modified the caller-owned transaction lifecycle");
            }
        }
        helper.succeed();
    }

    private static void duplicateDirectTickLanesAreGrouped(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setScalar(machine, ScalarResourcePort.ENERGY_INPUT_1, 100);
        OIRecipe recipe = new OIRecipe(
                BuiltinOIRecipeTypes.CHEMICAL_REACTOR,
                OIRecipe.EMPTY_INPUTS,
                OIRecipe.EMPTY_OUTPUTS,
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ENERGY, List.of(60L)),
                        new OIRecipe.InputEntry<>(ENERGY, List.of(60L))
                },
                OIRecipe.EMPTY_OUTPUTS,
                40);
        if (!recipe.hasDirectTickIo()) {
            helper.fail("Grouped scalar tick IO should use the direct lane");
        }
        if (recipe.checkTickIo(machine) != OIRecipe.TickIoResult.INPUT_BLOCKED) {
            helper.fail("Aggregate scalar tick demand 120 must reject inventory 100");
        }
        assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_INPUT_1, 100L);

        setScalar(machine, ScalarResourcePort.ENERGY_INPUT_1, 120);
        if (recipe.handleTickIo(machine) != OIRecipe.TickIoResult.SUCCESS) {
            helper.fail("Aggregate scalar tick demand 120 should consume inventory 120");
        }
        assertScalarAmount(helper, machine, ScalarResourcePort.ENERGY_INPUT_1, 0L);
        helper.succeed();
    }

    private static void sameLaneTickExchangeReusesExtractedCapacity(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        long capacity = storage.capacityAmount();
        storage.handler().set(0, storage.resource(), Math.toIntExact(capacity));
        try (Transaction ignored = Transaction.openRoot()) {
            if (!ENERGY.matchOutputAfterInputs(
                    storage.handler(), List.of(10L), storage.handler(), List.of(10L))) {
                helper.fail("Extracting 10 must free exactly enough capacity for same-storage output");
            }
            if (ENERGY.matchOutputAfterInputs(
                    storage.handler(), List.of(10L), storage.handler(), List.of(11L))) {
                helper.fail("A net-positive exchange must remain blocked at full capacity");
            }
            if (Transaction.getLifecycle() != Transaction.Lifecycle.OPEN) {
                helper.fail("The shared-storage planner modified the caller-owned transaction lifecycle");
            }
        }
        if (storage.storedAmount() != capacity) {
            helper.fail("The shared-storage planner mutated the live handler");
        }
        helper.succeed();
    }

    private static void recipeIndexDuplicateCatalystsUseMaxReservation(GameTestHelper helper) {
        MachineBlockEntity machine = placeChemicalReactor(helper);
        setItem(machine, 0, Items.CRAFTING_TABLE, 1);
        List<RecipeHolder<OIRecipe>> holders = new ArrayList<>(65);
        for (int index = 1; index <= 64; index++) {
            OIRecipe decoy = recipe(new OIRecipe.InputEntry<?>[] {
                    new OIRecipe.InputEntry<>(ITEMS, List.of(OIItemInput.of(Items.COAL, index)))
            });
            holders.add(new RecipeHolder<>(
                    ResourceKey.create(Registries.RECIPE, IdHelper.oi("parallel_index_decoy_" + index)),
                    decoy));
        }
        OIRecipe targetRecipe = recipe(new OIRecipe.InputEntry<?>[] {
                new OIRecipe.InputEntry<>(ITEMS, List.of(
                        OIItemInput.unconsumed(Items.CRAFTING_TABLE),
                        OIItemInput.unconsumed(Items.CRAFTING_TABLE)))
        });
        RecipeHolder<OIRecipe> target = new RecipeHolder<>(
                ResourceKey.create(Registries.RECIPE, IdHelper.oi("parallel_index_duplicate_catalysts")),
                targetRecipe);
        holders.add(target);

        if (!targetRecipe.matchInputs(machine)) {
            helper.fail("The exact planner must let one catalyst satisfy duplicate presence reservations");
        }
        RecipeHolder<OIRecipe> found = OIRecipeSearchIndex.build(holders).findRecipe(machine);
        if (found != target) {
            helper.fail("The indexed 65-recipe path pruned the duplicate-catalyst target");
        }
        helper.succeed();
    }

    private static void fluidPlannerSnapshotsComplexPoolOnce(GameTestHelper helper) {
        int distinctResources = 32;
        FluidResource[] resources = new FluidResource[distinctResources];
        List<OIFluidIngredient> inputs = new ArrayList<>(distinctResources * 2);
        for (int index = 0; index < distinctResources; index++) {
            OIFluidIngredient ingredient = distinctWaterIngredient(index);
            resources[index] = ingredient.resource();
            inputs.add(ingredient);
            inputs.add(ingredient);
        }
        CountingLongFluidHandler handler = new CountingLongFluidHandler(resources, 128L);

        long planned;
        try (Transaction ignored = Transaction.openRoot()) {
            planned = FluidRecipeCapability.maxParallelByInputs(handler, inputs, 128L);
            if (Transaction.getLifecycle() != Transaction.Lifecycle.OPEN) {
                helper.fail("Complex fluid planning modified the caller-owned transaction lifecycle");
            }
        }
        if (planned != 64L) {
            helper.fail("Expected 64 complex-fluid parallels, got " + planned);
        }
        if (handler.resourceReads != distinctResources || handler.amountReads != distinctResources) {
            helper.fail("Fluid planning must snapshot each of " + distinctResources + " slots once; resource reads=" + handler.resourceReads + ", amount reads=" + handler.amountReads);
        }
        helper.succeed();
    }

    private static void parallelPlannersRejectPathologicalDimensions(GameTestHelper helper) {
        int oversizedInputCount = (1 << 12) + 1;
        int oversizedHandlerSlots = (1 << 16) + 1;
        long itemInputs = ItemRecipeCapability.maxParallelByInputs(
                new UnreadableResourceHandler<>(1),
                java.util.Collections.nCopies(
                        oversizedInputCount, OIItemInput.of(Items.IRON_INGOT, 1L)),
                Long.MAX_VALUE);
        if (itemInputs != 0L) {
            helper.fail("Oversized item input lists must fail closed, got " + itemInputs);
        }

        OIFluidIngredient water = fluid(Fluids.WATER, 1);
        long fluidInputs = FluidRecipeCapability.maxParallelByInputs(
                new UnreadableResourceHandler<>(1),
                java.util.Collections.nCopies(oversizedInputCount, water),
                Long.MAX_VALUE);
        if (fluidInputs != 0L) {
            helper.fail("Oversized fluid input lists must fail closed, got " + fluidInputs);
        }

        long itemSlots = ItemRecipeCapability.maxParallelByInputs(
                new UnreadableResourceHandler<>(oversizedHandlerSlots),
                List.of(OIItemInput.of(Items.IRON_INGOT, 1L)),
                1L);
        long fluidSlots = FluidRecipeCapability.maxParallelByInputs(
                new UnreadableResourceHandler<>(oversizedHandlerSlots),
                List.of(water),
                1L);
        if (itemSlots != 0L || fluidSlots != 0L) {
            helper.fail("Oversized handler dimensions must fail closed; item=" + itemSlots + ", fluid=" + fluidSlots);
        }
        helper.succeed();
    }

    private static void fluidPlannerRejectsAggregateLongOverflow(GameTestHelper helper) {
        OIFluidIngredient water = fluid(Fluids.WATER, 1);
        CountingLongFluidHandler handler = new CountingLongFluidHandler(
                new FluidResource[] { water.resource() }, Long.MAX_VALUE);
        long planned = FluidRecipeCapability.maxParallelByInputs(
                handler,
                List.of(water.withAmount(Long.MAX_VALUE), water),
                1L);
        if (planned != 0L) {
            helper.fail("Aggregate fluid demand above LONG range must fail closed, got " + planned);
        }
        if (handler.resourceReads != 1 || handler.amountReads != 1) {
            helper.fail("Overflow rejection must use one immutable handler snapshot");
        }
        helper.succeed();
    }

    private static void outputPlannersPreserveDeclaredOrder(GameTestHelper helper) {
        OIItemOutput iron = OIItemOutput.of(Items.IRON_INGOT, 1L);
        OIItemOutput gold = OIItemOutput.of(Items.GOLD_INGOT, 1L);
        OrderSensitiveOutputHandler<ItemResource> items = new OrderSensitiveOutputHandler<>(
                ItemResource.EMPTY, iron.resource());
        if (!ItemRecipeCapability.canInsertOutputs(items, List.of(gold, iron))) {
            helper.fail("Gold must reserve the shared item slot before iron uses its constrained slot");
        }
        if (ItemRecipeCapability.canInsertOutputs(items, List.of(iron, gold))) {
            helper.fail("Reversing item output declaration must expose the constrained-slot conflict");
        }

        OIFluidIngredient water = fluid(Fluids.WATER, 1);
        OIFluidIngredient lava = fluid(Fluids.LAVA, 1);
        OrderSensitiveOutputHandler<FluidResource> fluids = new OrderSensitiveOutputHandler<>(
                FluidResource.EMPTY, water.resource());
        if (!FluidRecipeCapability.canInsertOutputs(fluids, List.of(lava, water))) {
            helper.fail("Lava must reserve the shared fluid slot before water uses its constrained slot");
        }
        if (FluidRecipeCapability.canInsertOutputs(fluids, List.of(water, lava))) {
            helper.fail("Reversing fluid output declaration must expose the constrained-slot conflict");
        }

        LongSupplyItemHandler fullItems = new LongSupplyItemHandler(iron.resource(), 10L);
        if (!ItemRecipeCapability.matchOutputAfterInputs(
                fullItems,
                List.of(OIItemInput.of(Items.IRON_INGOT, 10L)),
                fullItems,
                List.of(OIItemOutput.of(Items.IRON_INGOT, 10L)))) {
            helper.fail("A full shared item slot must reuse capacity released by tick input");
        }
        if (ItemRecipeCapability.matchOutputAfterInputs(
                fullItems,
                List.of(OIItemInput.of(Items.IRON_INGOT, 10L)),
                fullItems,
                List.of(OIItemOutput.of(Items.IRON_INGOT, 11L)))) {
            helper.fail("A net-positive item exchange must remain blocked at full capacity");
        }

        CountingLongFluidHandler fullFluids = new CountingLongFluidHandler(
                new FluidResource[] { water.resource() }, 10L);
        if (!FluidRecipeCapability.matchOutputAfterInputs(
                fullFluids, List.of(water.withAmount(10L)), fullFluids, List.of(water.withAmount(10L)))) {
            helper.fail("A full shared fluid tank must reuse capacity released by tick input");
        }
        if (FluidRecipeCapability.matchOutputAfterInputs(
                fullFluids, List.of(water.withAmount(10L)), fullFluids, List.of(water.withAmount(11L)))) {
            helper.fail("A net-positive fluid exchange must remain blocked at full capacity");
        }
        helper.succeed();
    }

    static List<PerformanceScenario> createPerformanceScenarios(
                                                                GameTestHelper helper,
                                                                int scenarioCount) {
        if (scenarioCount < 1 || scenarioCount > 8) {
            throw new IllegalArgumentException("Performance scenario count must be in [1, 8]");
        }
        List<PerformanceScenario> scenarios = new ArrayList<>(scenarioCount);
        for (int index = 0; index < scenarioCount; index++) {
            BlockPos position = new BlockPos(
                    1 + (index & 1),
                    1 + (index >> 1 & 1),
                    1 + (index >> 2 & 1));
            scenarios.add(createPerformanceScenario(helper, position, index));
        }
        return List.copyOf(scenarios);
    }

    private static PerformanceScenario createPerformanceScenario(
                                                                 GameTestHelper helper,
                                                                 BlockPos position,
                                                                 int scenarioIndex) {
        MachineBlockEntity machine = placeChemicalReactor(helper, position);
        setItem(machine, 0, Items.IRON_INGOT, 64);
        setItem(machine, 1, Items.GOLD_INGOT, 64);
        setItem(machine, 2, Items.COPPER_INGOT, 64);
        setItem(machine, 3, Items.REDSTONE, 64);

        FluidResourcePort fluids = machine.machineComponents().require(FluidResourcePort.FLUID_INPUT_1);
        fluids.handler().set(0, FluidResource.of(Fluids.WATER), 16_000);
        fluids.handler().set(1, FluidResource.of(Fluids.WATER), 16_000);
        fluids.handler().set(2, FluidResource.of(Fluids.LAVA), 16_000);
        fluids.handler().set(3, FluidResource.of(Fluids.LAVA), 16_000);
        setScalar(machine, ScalarResourcePort.ENERGY_INPUT_1, 100_000);
        setScalar(machine, ScalarResourcePort.HEAT_INPUT_1, 100_000);

        List<OIItemInput> firstItemGroup = List.of(
                OIItemInput.of(Items.IRON_INGOT, 2),
                OIItemInput.of(Items.GOLD_INGOT, 2),
                anyOf(2, Items.IRON_INGOT, Items.GOLD_INGOT),
                anyOf(2, Items.IRON_INGOT, Items.COPPER_INGOT));
        List<OIItemInput> secondItemGroup = List.of(
                anyOf(2, Items.GOLD_INGOT, Items.REDSTONE),
                anyOf(2, Items.COPPER_INGOT, Items.REDSTONE),
                anyOf(2, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE),
                OIItemInput.unconsumed(Items.IRON_INGOT));
        OIRecipe.InputEntry<?>[] itemInputs = {
                new OIRecipe.InputEntry<>(ITEMS, firstItemGroup),
                new OIRecipe.InputEntry<>(ITEMS, secondItemGroup)
        };
        OIRecipe.InputEntry<?>[] fluidInputs = {
                new OIRecipe.InputEntry<>(FLUIDS, List.of(fluid(Fluids.WATER, 500))),
                new OIRecipe.InputEntry<>(FLUIDS, List.of(fluid(Fluids.WATER, 700), fluid(Fluids.LAVA, 800)))
        };
        OIRecipe.InputEntry<?>[] scalarInputs = {
                new OIRecipe.InputEntry<>(ENERGY, List.of(1_000L)),
                new OIRecipe.InputEntry<>(ENERGY, List.of(500L)),
                new OIRecipe.InputEntry<>(HEAT, List.of(2_000L))
        };
        OIRecipe.InputEntry<?>[] allInputs = new OIRecipe.InputEntry<?>[itemInputs.length + fluidInputs.length + scalarInputs.length];
        System.arraycopy(itemInputs, 0, allInputs, 0, itemInputs.length);
        System.arraycopy(fluidInputs, 0, allInputs, itemInputs.length, fluidInputs.length);
        System.arraycopy(
                scalarInputs,
                0,
                allInputs,
                itemInputs.length + fluidInputs.length,
                scalarInputs.length);

        long complexDemand = (long) Integer.MAX_VALUE + 4_096L + scenarioIndex;
        long complexSupply = Math.multiplyExact(complexDemand, COMPLEX_POOL_PARALLEL);
        ItemResource[] complexResources = new ItemResource[COMPLEX_POOL_RESOURCE_COUNT];
        List<OIItemInput> complexItemInputs = new ArrayList<>(COMPLEX_POOL_RESOURCE_COUNT);
        for (int index = 0; index < COMPLEX_POOL_RESOURCE_COUNT; index++) {
            complexResources[index] = ItemResource.of(COMPLEX_POOL_ITEMS[index]);
            if (index < 32) {
                complexItemInputs.add(OIItemInput.of(COMPLEX_POOL_ITEMS[index], complexDemand));
                continue;
            }
            int next = 32 + (index - 31) % 16;
            complexItemInputs.add(anyOfLong(
                    complexDemand,
                    COMPLEX_POOL_ITEMS[index],
                    COMPLEX_POOL_ITEMS[next]));
        }
        List<OIItemInput> immutableComplexInputs = List.copyOf(complexItemInputs);
        OIRecipe complexRecipe = new OIRecipe(
                BuiltinOIRecipeTypes.CHEMICAL_REACTOR,
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ITEMS, immutableComplexInputs)
                },
                new OIRecipe.OutputEntry<?>[] {
                        new OIRecipe.OutputEntry<>(ITEMS, List.of(OIItemOutput.of(Items.IRON_NUGGET, 3L)))
                },
                new OIRecipe.InputEntry<?>[] {
                        new OIRecipe.InputEntry<>(ENERGY, List.of(1_000L)),
                        new OIRecipe.InputEntry<>(HEAT, List.of(500L))
                },
                OIRecipe.EMPTY_OUTPUTS,
                40);
        return new PerformanceScenario(
                machine,
                recipe(allInputs),
                recipe(itemInputs),
                recipe(fluidInputs),
                recipe(scalarInputs),
                18L,
                18L,
                26L,
                50L,
                new LongSupplyItemHandler(complexResources, complexSupply),
                immutableComplexInputs,
                complexRecipe,
                COMPLEX_POOL_PARALLEL,
                complexDemand);
    }

    private static OIRecipe recipe(OIRecipe.InputEntry<?>[] inputs) {
        return new OIRecipe(BuiltinOIRecipeTypes.CHEMICAL_REACTOR, inputs, OIRecipe.EMPTY_OUTPUTS, 40);
    }

    private static MachineBlockEntity placeChemicalReactor(GameTestHelper helper) {
        return placeChemicalReactor(helper, MACHINE_POS);
    }

    private static MachineBlockEntity placeChemicalReactor(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, BuiltinOIMachines.CHEMICAL_REACTOR_T2.registeredBlock().getDefaultState());
        return helper.getBlockEntity(position, MachineBlockEntity.class);
    }

    private static OIItemInput anyOf(int count, Item... items) {
        return new OIItemInput.AnyOf(java.util.Arrays.stream(items)
                .map(item -> new ItemStackTemplate(item, count))
                .toList());
    }

    private static OIItemInput anyOfLong(long count, Item... items) {
        return new OIItemInput.AnyOf(java.util.Arrays.stream(items)
                .map(item -> new ItemStackTemplate(item, 1))
                .toList(), count);
    }

    private static OIFluidIngredient fluid(net.minecraft.world.level.material.Fluid fluid, int amount) {
        return new OIFluidIngredient(new FluidStack(fluid, amount));
    }

    private static OIFluidIngredient distinctWaterIngredient(int identity) {
        FluidStack stack = new FluidStack(Fluids.WATER, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("parallel-planner-" + identity));
        return new OIFluidIngredient(stack);
    }

    private static void setItem(MachineBlockEntity machine, int slot, Item item, int amount) {
        machine.machineComponents()
                .require(ItemResourcePort.ITEM_INPUT_1)
                .handler()
                .set(slot, ItemResource.of(item), amount);
    }

    private static void setScalar(
                                  MachineBlockEntity machine,
                                  net.ptcrys.topo.apiv2.machine.component.ComponentKey<ScalarResourcePort> key,
                                  int amount) {
        ScalarResourcePort port = machine.machineComponents().require(key);
        port.handler().set(0, port.resource(), amount);
    }

    private static void assertScalarAmount(
                                           GameTestHelper helper,
                                           MachineBlockEntity machine,
                                           net.ptcrys.topo.apiv2.machine.component.ComponentKey<ScalarResourcePort> key,
                                           long expected) {
        ScalarResourcePort port = machine.machineComponents().require(key);
        long actual = port.handler().getAmountAsLong(0);
        if (actual != expected) {
            helper.fail("Expected scalar amount " + expected + ", got " + actual);
        }
    }

    private static void assertParallelInsideCallerTransaction(
                                                              GameTestHelper helper,
                                                              OIRecipe recipe,
                                                              MachineBlockEntity machine,
                                                              long cap,
                                                              long expected) {
        try (Transaction ignored = Transaction.openRoot()) {
            if (Transaction.getLifecycle() != Transaction.Lifecycle.OPEN) {
                helper.fail("Deterministic transaction guard did not enter OPEN lifecycle");
            }
            assertParallel(helper, recipe, machine, cap, expected);
            if (Transaction.getLifecycle() != Transaction.Lifecycle.OPEN) {
                helper.fail("Maximum-parallel planning modified the caller-owned transaction lifecycle");
            }
        }
    }

    private static void assertParallel(
                                       GameTestHelper helper,
                                       OIRecipe recipe,
                                       MachineBlockEntity machine,
                                       long cap,
                                       long expected) {
        long actual = recipe.maxParallelByInputs(machine, cap);
        if (actual != expected) {
            helper.fail("Expected maximum parallel " + expected + " at cap " + cap + ", got " + actual);
        }
    }

    private static void assertStoredItem(GameTestHelper helper, MachineBlockEntity machine, Item item, long expected) {
        ResourceHandler<ItemResource> handler = machine.machineComponents()
                .require(ItemResourcePort.ITEM_INPUT_1)
                .handler();
        ItemResource resource = ItemResource.of(item);
        long actual = 0L;
        for (int slot = 0; slot < handler.size(); slot++) {
            if (resource.equals(handler.getResource(slot))) {
                actual += handler.getAmountAsLong(slot);
            }
        }
        if (actual != expected) {
            helper.fail("Expected " + expected + " " + item + " in input ports, got " + actual);
        }
    }

    private static void assertAmount(GameTestHelper helper, Object content, long expected, String label) {
        long actual = switch (content) {
            case OIItemInput input -> input.count();
            case OIItemOutput output -> output.count();
            default -> -1;
        };
        if (actual != expected) {
            helper.fail("Expected scaled " + label + " amount " + expected + ", got " + content);
        }
    }

    private static void assertLong(GameTestHelper helper, Object content, long expected, String label) {
        if (!(content instanceof Long actual) || actual != expected) {
            helper.fail("Expected scaled " + label + " amount " + expected + ", got " + content);
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
            throw new IllegalArgumentException("Parallel planning GameTest index out of range: " + index);
        }
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }

    record PerformanceScenario(
                               MachineBlockEntity machine,
                               OIRecipe recipe,
                               OIRecipe itemRecipe,
                               OIRecipe fluidRecipe,
                               OIRecipe scalarRecipe,
                               long expectedParallel,
                               long expectedItemParallel,
                               long expectedFluidParallel,
                               long expectedScalarParallel,
                               ResourceHandler<ItemResource> complexItemHandler,
                               List<OIItemInput> complexItemInputs,
                               OIRecipe complexRecipe,
                               long expectedComplexParallel,
                               long complexDemand) {

        long maxComplexParallel(long cap) {
            return ItemRecipeCapability.maxParallelByInputs(complexItemHandler, complexItemInputs, cap);
        }

        int complexResourceCount() {
            return complexItemHandler.size();
        }
    }

    private static final class LongSupplyItemHandler implements ResourceHandler<ItemResource> {

        private final ItemResource[] resources;
        private final long amount;

        private LongSupplyItemHandler(ItemResource resource, long amount) {
            this(new ItemResource[] { resource }, amount);
        }

        private LongSupplyItemHandler(ItemResource[] resources, long amount) {
            this.resources = resources.clone();
            this.amount = amount;
        }

        @Override
        public int size() {
            return resources.length;
        }

        @Override
        public ItemResource getResource(int index) {
            return resources[index];
        }

        @Override
        public long getAmountAsLong(int index) {
            return amount;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource candidate) {
            return amount;
        }

        @Override
        public boolean isValid(int index, ItemResource candidate) {
            return resources[index].equals(candidate);
        }

        @Override
        public int insert(
                          int index,
                          ItemResource candidate,
                          int requested,
                          TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(
                           int index,
                           ItemResource candidate,
                           int requested,
                           TransactionContext transaction) {
            return 0;
        }
    }

    private static final class CountingLongFluidHandler implements ResourceHandler<FluidResource> {

        private final FluidResource[] resources;
        private final long amount;
        private int resourceReads;
        private int amountReads;

        private CountingLongFluidHandler(FluidResource[] resources, long amount) {
            this.resources = resources.clone();
            this.amount = amount;
        }

        @Override
        public int size() {
            return resources.length;
        }

        @Override
        public FluidResource getResource(int index) {
            resourceReads++;
            return resources[index];
        }

        @Override
        public long getAmountAsLong(int index) {
            amountReads++;
            return amount;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource candidate) {
            return amount;
        }

        @Override
        public boolean isValid(int index, FluidResource candidate) {
            return resources[index].equals(candidate);
        }

        @Override
        public int insert(
                          int index,
                          FluidResource candidate,
                          int requested,
                          TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(
                           int index,
                           FluidResource candidate,
                           int requested,
                           TransactionContext transaction) {
            return 0;
        }
    }

    private static final class OrderSensitiveOutputHandler<R extends Resource> implements ResourceHandler<R> {

        private final R empty;
        private final R constrainedResource;

        private OrderSensitiveOutputHandler(R empty, R constrainedResource) {
            this.empty = empty;
            this.constrainedResource = constrainedResource;
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public R getResource(int index) {
            return empty;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, R candidate) {
            return isValid(index, candidate) ? 1L : 0L;
        }

        @Override
        public boolean isValid(int index, R candidate) {
            return index == 0 || constrainedResource.equals(candidate);
        }

        @Override
        public int insert(int index, R candidate, int requested, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, R candidate, int requested, TransactionContext transaction) {
            return 0;
        }
    }

    private static final class UnreadableResourceHandler<R extends Resource> implements ResourceHandler<R> {

        private final int size;

        private UnreadableResourceHandler(int size) {
            this.size = size;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public R getResource(int index) {
            throw new AssertionError("Planner read a resource after rejecting its dimensions");
        }

        @Override
        public long getAmountAsLong(int index) {
            throw new AssertionError("Planner read an amount after rejecting its dimensions");
        }

        @Override
        public long getCapacityAsLong(int index, R candidate) {
            return 0L;
        }

        @Override
        public boolean isValid(int index, R candidate) {
            return false;
        }

        @Override
        public int insert(int index, R candidate, int requested, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, R candidate, int requested, TransactionContext transaction) {
            return 0;
        }
    }

    private static final class CountingIntItemHandler implements ResourceHandler<ItemResource> {

        private int calls;

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(Items.IRON_INGOT);
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return true;
        }

        @Override
        public int insert(
                          int index,
                          ItemResource resource,
                          int amount,
                          TransactionContext transaction) {
            calls++;
            return amount;
        }

        @Override
        public int extract(
                           int index,
                           ItemResource resource,
                           int amount,
                           TransactionContext transaction) {
            return 0;
        }
    }
}
