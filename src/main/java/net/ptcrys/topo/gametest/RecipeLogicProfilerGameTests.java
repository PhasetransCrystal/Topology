package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.api.tick.NoopTickHandle;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/**
 * Opt-in profiler scenario for IntelliJ Profiler and local bottleneck reports.
 *
 * <p>
 * Run with {@code runRecipeLogicProfilerGameTestServer}. This suite is intentionally not part
 * of normal correctness GameTests because it performs large timing loops.
 */
public final class RecipeLogicProfilerGameTests {

    private static final String SUITE = "recipe_logic_profiler";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 5;
    private static final int ITERATIONS = Integer.getInteger("oi.profiler.iterations", 50_000);
    private static final int WARMUP_ITERATIONS = Integer.getInteger("oi.profiler.warmupIterations", 5_000);
    private static final int REAL_TICK_ITERATIONS = Integer.getInteger("oi.profiler.realTickIterations", 600);
    private static final int REAL_TICK_WARMUP_ITERATIONS = Integer.getInteger("oi.profiler.realTickWarmupIterations", 100);
    private static final int SCALE_REAL_TICK_ITERATIONS = Integer.getInteger("oi.profiler.scaleRealTickIterations", 160);
    private static final int SCALE_MACHINE_COUNT = Integer.getInteger("oi.profiler.machineCount", 64);
    private static final int WORKING_TICK_CHUNK = 80;
    private static final int SCALAR_CHUNK = 1_000;
    private static final long WORKING_RECIPE_P95_BUDGET_NANOS = Long.getLong("oi.profiler.workingRecipeP95Nanos", 5_000L);
    private static final long WORKING_MACHINE_P95_BUDGET_NANOS = Long.getLong("oi.profiler.workingMachineP95Nanos", 10_000L);
    private static final long SCALE_MACHINE_P95_BUDGET_NANOS = Long.getLong("oi.profiler.scaleMachineP95Nanos", WORKING_MACHINE_P95_BUDGET_NANOS);
    private static final int MIN_WORKING_SAMPLES = Integer.getInteger("oi.profiler.minWorkingSamples", Math.max(200, REAL_TICK_ITERATIONS / 2));
    private static final int PLANNING_MACHINES_PER_TICK = 8;
    private static final long PLANNING_CAP = 128L;
    private static final double PLANNING_MEAN_BUDGET_NANOS = 2_000.0d;
    private static final long PLANNING_P99_BUDGET_NANOS = 5_000L;
    private static final long PLANNING_CPU_PEAK_BUDGET_NANOS = 100_000L;
    private static final long PLANNING_BATCH_P99_BUDGET_NANOS = 100_000L;
    private static final int DETAIL_SAMPLE_LIMIT = 12;
    private static final String SAMPLE_CSV_FILE = "topo-recipe-logic-profiler-samples.csv";
    /** Mirrors the StackWalker NeoForge's Transaction.openRoot() invokes on every root transaction. */
    private static final StackWalker CALLER_CLASS_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
    private static final boolean THREAD_CPU_TIME_AVAILABLE = enableThreadCpuTime();
    /** Keeps tight-loop results observable so the JIT cannot eliminate the measured calls. */
    @SuppressWarnings("unused")
    private static volatile Object profilerSink;

    private RecipeLogicProfilerGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!isEnabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("recipe_logic_profiler"), new TestEnvironmentDefinition.AllOf());
        register(
                event,
                environment,
                1,
                "macerator_working_tick_profile",
                "Profiles RecipeLogic WORKING tick cost on a real macerator and writes a profiler report.",
                RecipeLogicProfilerGameTests::maceratorWorkingTickProfile);
        register(
                event,
                environment,
                2,
                "energy_generator_working_tick_profile",
                "Profiles the energy generator WORKING tick (per-tick scalar output transaction) and writes a profiler report.",
                RecipeLogicProfilerGameTests::energyGeneratorWorkingTickProfile);
        register(
                event,
                environment,
                3,
                "advanced_generator_idle_tick_profile",
                "Profiles the zero-energy advanced generator idle tick (parked recipe logic) and decomposes the " + "idle-path cost segments into a profiler report.",
                RecipeLogicProfilerGameTests::advancedGeneratorIdleTickProfile);
        register(
                event,
                environment,
                4,
                "parallel_planning_performance_budget",
                "Enforces stable maximum-parallel planning budgets across eight independent real machines and 48-resource LONG pools.",
                RecipeLogicProfilerGameTests::parallelPlanningPerformanceBudget);
        register(
                event,
                environment,
                5,
                "combustion_generator_scale_working_tick_profile",
                "Profiles scheduled WORKING ticks across a configurable grid of real combustion generators.",
                RecipeLogicProfilerGameTests::combustionGeneratorScaleWorkingTickProfile);
    }

    public static boolean isProfilerOnlyMode() {
        return Boolean.getBoolean("oi.profiler.only") || "true".equalsIgnoreCase(System.getenv("TOPO_PROFILER_ONLY"));
    }

    private static boolean isEnabled() {
        // Profiler GameTests are gated by the isolation flag so a normal GameTest
        // invocation cannot accidentally mix timing loops with correctness tests.
        return isProfilerOnlyMode();
    }

    private static void parallelPlanningPerformanceBudget(GameTestHelper helper) {
        List<ParallelPlanningGameTests.PerformanceScenario> scenarios = ParallelPlanningGameTests.createPerformanceScenarios(helper, PLANNING_MACHINES_PER_TICK);
        int warmupIterations = Math.max(
                10_000,
                Integer.getInteger("oi.parallelPlanner.warmupIterations", 20_000));
        int sampleCount = Math.max(
                10_000,
                Integer.getInteger("oi.parallelPlanner.sampleCount", 50_000));

        if (scenarios.size() != PLANNING_MACHINES_PER_TICK) {
            helper.fail("Parallel planner budget requires exactly " + PLANNING_MACHINES_PER_TICK + " independent scenarios");
        }
        for (ParallelPlanningGameTests.PerformanceScenario scenario : scenarios) {
            if (scenario.complexResourceCount() < 32 || scenario.complexResourceCount() > 64) {
                helper.fail("Complex planner pool must contain 32-64 resource kinds, got " + scenario.complexResourceCount());
            }
        }

        for (int iteration = 0; iteration < warmupIterations; iteration++) {
            ParallelPlanningGameTests.PerformanceScenario scenario = scenarios.get(iteration % scenarios.size());
            assertPlanningResult(helper, PlanningWorkload.REAL_MACHINE_PORTS, scenario);
            assertPlanningResult(helper, PlanningWorkload.COMPLEX_LONG_POOL, scenario);
        }

        // A nested root is illegal in NeoForge. Planning successfully inside this caller-owned root
        // is a deterministic guard that neither measured planner opens a transaction to probe IO.
        try (Transaction ignored = Transaction.openRoot()) {
            for (ParallelPlanningGameTests.PerformanceScenario scenario : scenarios) {
                assertPlanningResult(helper, PlanningWorkload.REAL_MACHINE_PORTS, scenario);
                assertPlanningResult(helper, PlanningWorkload.COMPLEX_LONG_POOL, scenario);
            }
            if (Transaction.getLifecycle() != Transaction.Lifecycle.OPEN) {
                helper.fail("Parallel planning must leave the caller-owned root transaction open");
            }
        }

        for (PlanningWorkload workload : PlanningWorkload.values()) {
            PlanningCallStats calls = measurePlanningCalls(helper, scenarios, workload, sampleCount);
            PlanningTickStats ticks = measurePlanningTicks(helper, scenarios, workload, sampleCount);
            PlanningCpuStats cpu = measurePlanningCpuPeaks(helper, scenarios, workload, sampleCount);
            String report = planningReport(workload, scenarios, calls, ticks, cpu);
            com.mojang.logging.LogUtils.getLogger().info(report);
            enforcePlanningBudget(helper, report, calls, ticks, cpu);
        }

        verifyFoldPlanAndScale(helper, scenarios);
        int integrationSamples = Math.max(
                1_000,
                Integer.getInteger("oi.parallelPlanner.integrationSampleCount", sampleCount / 10));
        PlanningCallStats integration = measureFoldPlanAndScale(helper, scenarios, integrationSamples);
        com.mojang.logging.LogUtils.getLogger().info(
                "Topo modifier-fold + complex LONG plan + withParallel integration: samples={}, wallMean={} us, wallP99={} us, rawWallMax(observationOnly)={} us",
                integration.samples(),
                formatMicros(integration.meanNanos()),
                formatMicros(integration.p99Nanos()),
                formatMicros(integration.rawMaxNanos()));
        helper.succeed();
    }

    private static PlanningCallStats measurePlanningCalls(
                                                          GameTestHelper helper,
                                                          List<ParallelPlanningGameTests.PerformanceScenario> scenarios,
                                                          PlanningWorkload workload,
                                                          int sampleCount) {
        long[] samples = new long[sampleCount];
        long totalNanos = 0L;
        long checksum = 0L;
        for (int iteration = 0; iteration < sampleCount; iteration++) {
            ParallelPlanningGameTests.PerformanceScenario scenario = scenarios.get(iteration % scenarios.size());
            long start = System.nanoTime();
            long actual = workload.plan(scenario);
            long elapsed = System.nanoTime() - start;
            if (actual != workload.expected(scenario)) {
                helper.fail(workload.label + " expected " + workload.expected(scenario) + ", got " + actual);
            }
            samples[iteration] = elapsed;
            totalNanos += elapsed;
            checksum = 31L * checksum + actual;
        }
        profilerSink = checksum;
        Arrays.sort(samples);
        return new PlanningCallStats(
                sampleCount,
                totalNanos / (double) sampleCount,
                percentile(samples, 0.99d),
                samples[samples.length - 1]);
    }

    private static PlanningTickStats measurePlanningTicks(
                                                          GameTestHelper helper,
                                                          List<ParallelPlanningGameTests.PerformanceScenario> scenarios,
                                                          PlanningWorkload workload,
                                                          int totalPlans) {
        int ticks = Math.max(2_000, totalPlans / scenarios.size());
        long[] samples = new long[ticks];
        long totalNanos = 0L;
        long checksum = 0L;
        for (int tick = 0; tick < ticks; tick++) {
            long start = System.nanoTime();
            for (ParallelPlanningGameTests.PerformanceScenario scenario : scenarios) {
                long actual = workload.plan(scenario);
                if (actual != workload.expected(scenario)) {
                    helper.fail(workload.label + " batch expected " + workload.expected(scenario) + ", got " + actual);
                }
                checksum = 31L * checksum + actual;
            }
            long elapsed = System.nanoTime() - start;
            samples[tick] = elapsed;
            totalNanos += elapsed;
        }
        profilerSink = checksum;
        Arrays.sort(samples);
        return new PlanningTickStats(
                ticks,
                totalNanos / (double) ticks / scenarios.size(),
                percentile(samples, 0.99d),
                samples[samples.length - 1]);
    }

    private static void assertPlanningResult(
                                             GameTestHelper helper,
                                             PlanningWorkload workload,
                                             ParallelPlanningGameTests.PerformanceScenario scenario) {
        long actual = workload.plan(scenario);
        long expected = workload.expected(scenario);
        if (actual != expected) {
            helper.fail(workload.label + " expected " + expected + ", got " + actual);
        }
    }

    private static PlanningCpuStats measurePlanningCpuPeaks(
                                                            GameTestHelper helper,
                                                            List<ParallelPlanningGameTests.PerformanceScenario> scenarios,
                                                            PlanningWorkload workload,
                                                            int totalPlans) {
        if (!THREAD_CPU_TIME_AVAILABLE) {
            return PlanningCpuStats.unsupported();
        }
        long singleRawMax = 0L;
        long batchRawMax = 0L;
        long checksum = 0L;
        for (int iteration = 0; iteration < totalPlans; iteration++) {
            ParallelPlanningGameTests.PerformanceScenario scenario = scenarios.get(iteration % scenarios.size());
            long start = THREAD_MX_BEAN.getCurrentThreadCpuTime();
            long actual = workload.plan(scenario);
            long elapsed = THREAD_MX_BEAN.getCurrentThreadCpuTime() - start;
            if (actual != workload.expected(scenario)) {
                helper.fail(workload.label + " CPU sample expected " + workload.expected(scenario) + ", got " + actual);
            }
            singleRawMax = Math.max(singleRawMax, elapsed);
            checksum = 31L * checksum + actual;
        }
        int batchSamples = Math.max(2_000, totalPlans / scenarios.size());
        for (int sample = 0; sample < batchSamples; sample++) {
            long start = THREAD_MX_BEAN.getCurrentThreadCpuTime();
            for (ParallelPlanningGameTests.PerformanceScenario scenario : scenarios) {
                long actual = workload.plan(scenario);
                if (actual != workload.expected(scenario)) {
                    helper.fail(workload.label + " CPU batch expected " + workload.expected(scenario) + ", got " + actual);
                }
                checksum = 31L * checksum + actual;
            }
            batchRawMax = Math.max(
                    batchRawMax,
                    THREAD_MX_BEAN.getCurrentThreadCpuTime() - start);
        }
        profilerSink = checksum;
        return new PlanningCpuStats(true, totalPlans, singleRawMax, batchRawMax);
    }

    private static String planningReport(
                                         PlanningWorkload workload,
                                         List<ParallelPlanningGameTests.PerformanceScenario> scenarios,
                                         PlanningCallStats calls,
                                         PlanningTickStats ticks,
                                         PlanningCpuStats cpu) {
        String wall = String.format(
                Locale.ROOT,
                "Topo maximum-parallel planner [%s]: machines=%d, resources/machine=%d, samples=%d, wallMean=%.3f us, wallP99(operationalPeak)=%.3f us, rawWallMax(observationOnly)=%.3f us, batchTicks=%d, batchWallMeanPerMachine=%.3f us, batchWallP99(operationalPeak)=%.3f us, rawBatchWallMax(observationOnly)=%.3f us",
                workload.label,
                scenarios.size(),
                workload.resourceCount(scenarios.getFirst()),
                calls.samples(),
                calls.meanNanos() / 1_000.0d,
                calls.p99Nanos() / 1_000.0d,
                calls.rawMaxNanos() / 1_000.0d,
                ticks.ticks(),
                ticks.meanPerMachineNanos() / 1_000.0d,
                ticks.p99Nanos() / 1_000.0d,
                ticks.rawMaxNanos() / 1_000.0d);
        if (!cpu.supported()) {
            return wall + ", currentThreadCpuTime=unsupported(fallback=wallP99 operational peak)";
        }
        return wall + String.format(
                Locale.ROOT,
                ", cpuSamples=%d, rawCurrentThreadCpuMax=%.3f us, rawBatchCurrentThreadCpuMax=%.3f us",
                cpu.samples(),
                cpu.singleRawMaxNanos() / 1_000.0d,
                cpu.batchRawMaxNanos() / 1_000.0d);
    }

    private static void enforcePlanningBudget(
                                              GameTestHelper helper,
                                              String report,
                                              PlanningCallStats calls,
                                              PlanningTickStats ticks,
                                              PlanningCpuStats cpu) {
        if (calls.meanNanos() > PLANNING_MEAN_BUDGET_NANOS || ticks.meanPerMachineNanos() > PLANNING_MEAN_BUDGET_NANOS) {
            helper.fail(report + "; single and batched mean budget is <= 2 us per machine call");
        }
        if (calls.p99Nanos() > PLANNING_P99_BUDGET_NANOS) {
            helper.fail(report + "; single-call P99 budget is <= 5 us");
        }
        if (ticks.p99Nanos() > PLANNING_BATCH_P99_BUDGET_NANOS) {
            helper.fail(report + "; eight-independent-machine batch P99 budget is <= 100 us");
        }
        if (cpu.supported() && (cpu.singleRawMaxNanos() > PLANNING_CPU_PEAK_BUDGET_NANOS || cpu.batchRawMaxNanos() > PLANNING_CPU_PEAK_BUDGET_NANOS)) {
            helper.fail(report + "; raw current-thread CPU peak budget is <= 100 us for a single call and eight-machine batch");
        }
    }

    private static void verifyFoldPlanAndScale(
                                               GameTestHelper helper,
                                               List<ParallelPlanningGameTests.PerformanceScenario> scenarios) {
        for (ParallelPlanningGameTests.PerformanceScenario scenario : scenarios) {
            TopoRecipe scaled = foldPlanAndScale(scenario);
            long expectedItemAmount = Math.multiplyExact(
                    scenario.complexDemand(), scenario.expectedComplexParallel());
            Object firstInput = scaled.inputs()[0].contents().getFirst();
            if (!(firstInput instanceof TopoItemInput itemInput) || itemInput.count() != expectedItemAmount || expectedItemAmount <= Integer.MAX_VALUE) {
                helper.fail("Modifier/parallel integration must preserve LONG item amounts; expected " + expectedItemAmount + ", got " + firstInput);
            }
            if (scaled.duration() != 20 || !(scaled.tickInputs()[0].contents().getFirst() instanceof Long energy) || energy != 192_000L || !(scaled.tickInputs()[1].contents().getFirst() instanceof Long heat) || heat != 96_000L) {
                helper.fail("T2 modifier fold + parallel x64 produced unexpected duration/tick inputs");
            }
        }
    }

    private static PlanningCallStats measureFoldPlanAndScale(
                                                             GameTestHelper helper,
                                                             List<ParallelPlanningGameTests.PerformanceScenario> scenarios,
                                                             int sampleCount) {
        long[] samples = new long[sampleCount];
        long totalNanos = 0L;
        long checksum = 0L;
        for (int iteration = 0; iteration < sampleCount; iteration++) {
            ParallelPlanningGameTests.PerformanceScenario scenario = scenarios.get(iteration % scenarios.size());
            long start = System.nanoTime();
            TopoRecipe scaled = foldPlanAndScale(scenario);
            long elapsed = System.nanoTime() - start;
            Object firstInput = scaled.inputs()[0].contents().getFirst();
            if (!(firstInput instanceof TopoItemInput itemInput)) {
                helper.fail("Fold/plan/scale integration lost its first LONG item input");
                throw new IllegalStateException("unreachable");
            }
            samples[iteration] = elapsed;
            totalNanos += elapsed;
            checksum = 31L * checksum + itemInput.count() + scaled.duration();
        }
        profilerSink = checksum;
        Arrays.sort(samples);
        return new PlanningCallStats(
                sampleCount,
                totalNanos / (double) sampleCount,
                percentile(samples, 0.99d),
                samples[samples.length - 1]);
    }

    private static TopoRecipe foldPlanAndScale(
                                               ParallelPlanningGameTests.PerformanceScenario scenario) {
        TopoRecipe folded = scenario.complexRecipe();
        RecipeLogic recipeLogic = logic(scenario.machine());
        for (var match : scenario.machine().machineComponents().services(RecipeModifier.KEY, recipeLogic)) {
            folded = match.value().modify(folded, scenario.machine());
        }
        long parallel = scenario.maxComplexParallel(PLANNING_CAP);
        return folded.withParallel(parallel);
    }

    private static long percentile(long[] sortedSamples, double percentile) {
        int index = (int) Math.ceil(sortedSamples.length * percentile) - 1;
        return sortedSamples[Math.clamp(index, 0, sortedSamples.length - 1)];
    }

    private static boolean enableThreadCpuTime() {
        if (!THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported()) {
            return false;
        }
        try {
            if (!THREAD_MX_BEAN.isThreadCpuTimeEnabled()) {
                THREAD_MX_BEAN.setThreadCpuTimeEnabled(true);
            }
            return THREAD_MX_BEAN.isThreadCpuTimeEnabled();
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return false;
        }
    }

    private enum PlanningWorkload {

        REAL_MACHINE_PORTS("real item/fluid/scalar ports") {

            @Override
            long plan(ParallelPlanningGameTests.PerformanceScenario scenario) {
                return scenario.recipe().maxParallelByInputs(scenario.machine(), PLANNING_CAP);
            }

            @Override
            long expected(ParallelPlanningGameTests.PerformanceScenario scenario) {
                return scenario.expectedParallel();
            }

            @Override
            int resourceCount(ParallelPlanningGameTests.PerformanceScenario scenario) {
                return 10;
            }
        },
        COMPLEX_LONG_POOL("48-resource LONG item pool") {

            @Override
            long plan(ParallelPlanningGameTests.PerformanceScenario scenario) {
                return scenario.maxComplexParallel(PLANNING_CAP);
            }

            @Override
            long expected(ParallelPlanningGameTests.PerformanceScenario scenario) {
                return scenario.expectedComplexParallel();
            }

            @Override
            int resourceCount(ParallelPlanningGameTests.PerformanceScenario scenario) {
                return scenario.complexResourceCount();
            }
        };

        private final String label;

        PlanningWorkload(String label) {
            this.label = label;
        }

        abstract long plan(ParallelPlanningGameTests.PerformanceScenario scenario);

        abstract long expected(ParallelPlanningGameTests.PerformanceScenario scenario);

        abstract int resourceCount(ParallelPlanningGameTests.PerformanceScenario scenario);
    }

    private record PlanningCallStats(
                                     int samples,
                                     double meanNanos,
                                     long p99Nanos,
                                     long rawMaxNanos) {}

    private record PlanningTickStats(
                                     int ticks,
                                     double meanPerMachineNanos,
                                     long p99Nanos,
                                     long rawMaxNanos) {}

    private record PlanningCpuStats(
                                    boolean supported,
                                    int samples,
                                    long singleRawMaxNanos,
                                    long batchRawMaxNanos) {

        static PlanningCpuStats unsupported() {
            return new PlanningCpuStats(false, 0, -1L, -1L);
        }
    }

    private static void maceratorWorkingTickProfile(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        RecipeLogic logic = logic(machine);
        refillMaceratorEnergy(machine);
        insertIronInput(helper);
        List<ProfileResult> results = new ArrayList<>();
        OverlayTickAccumulator overlayTicks = new OverlayTickAccumulator();
        GcSnapshot[] scheduledGcStart = new GcSnapshot[1];

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Profiler setup should reach WORKING before sampling, got " + logic.state());
                    }
                    TopoRecipe activeRecipe = activeRecipe(
                            helper, machine, logic, BuiltinTopoRecipeTypes.MACERATOR);
                    results.addAll(runProfile(machine, logic, activeRecipe, helper.getLevel().getGameTime()));
                    refillMaceratorEnergy(machine);
                    logic.setProgressForGameTest(1);
                    machine.activatePerformanceMonitoring(
                            REAL_TICK_WARMUP_ITERATIONS + REAL_TICK_ITERATIONS + 20);
                })
                .thenExecuteFor(
                        REAL_TICK_WARMUP_ITERATIONS,
                        () -> warmScheduledOverlayTick(helper, machine, logic))
                .thenExecute(() -> scheduledGcStart[0] = GcSnapshot.capture())
                .thenExecuteFor(
                        REAL_TICK_ITERATIONS,
                        () -> sampleScheduledOverlayTick(helper, machine, logic, overlayTicks))
                .thenExecute(() -> {
                    if (overlayTicks.samples() == 0) {
                        helper.fail("Profiler should capture at least one overlay-equivalent WORKING tick sample");
                    }
                    results.addAll(overlayTicks.toResults());
                    WorkingBudget budget = workingBudget(overlayTicks, scheduledGcStart[0]);
                    Path report = writeReport(
                            "topo-recipe-logic-profiler.md",
                            SAMPLE_CSV_FILE,
                            "runRecipeLogicProfilerGameTestServer",
                            "macerator_working_tick_profile",
                            results,
                            overlayTicks.sampleDetails(),
                            budget.reportLines());
                    com.mojang.logging.LogUtils.getLogger()
                            .info("Topo recipe logic profiler report written to {}", report.toAbsolutePath());
                    enforceWorkingBudget(helper, "macerator", budget);
                })
                .thenSucceed();
    }

    private static void energyGeneratorWorkingTickProfile(GameTestHelper helper) {
        MachineBlockEntity machine = placeEnergyGenerator(helper);
        RecipeLogic logic = logic(machine);
        insertCoalFuel(helper);
        List<ProfileResult> results = new ArrayList<>();
        OverlayTickAccumulator overlayTicks = new OverlayTickAccumulator();
        GcSnapshot[] scheduledGcStart = new GcSnapshot[1];

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Profiler setup should reach WORKING before sampling, got " + logic.state());
                    }
                    TopoRecipe activeRecipe = activeRecipe(
                            helper, machine, logic, BuiltinTopoRecipeTypes.COMBUSTION_GENERATOR);
                    results.addAll(runGeneratorProfile(machine, logic, activeRecipe, helper.getLevel().getGameTime()));
                    drainGeneratorEnergy(machine);
                    logic.setProgressForGameTest(1);
                    machine.activatePerformanceMonitoring(
                            REAL_TICK_WARMUP_ITERATIONS + REAL_TICK_ITERATIONS + 20);
                })
                .thenExecuteFor(
                        REAL_TICK_WARMUP_ITERATIONS,
                        () -> warmScheduledOverlayTick(helper, machine, logic))
                .thenExecute(() -> scheduledGcStart[0] = GcSnapshot.capture())
                .thenExecuteFor(
                        REAL_TICK_ITERATIONS,
                        () -> sampleScheduledOverlayTick(helper, machine, logic, overlayTicks))
                .thenExecute(() -> {
                    if (overlayTicks.samples() == 0) {
                        helper.fail("Profiler should capture at least one overlay-equivalent WORKING tick sample");
                    }
                    results.addAll(overlayTicks.toResults());
                    WorkingBudget budget = workingBudget(overlayTicks, scheduledGcStart[0]);
                    Path report = writeReport(
                            "topo-energy-generator-profiler.md",
                            "topo-energy-generator-profiler-samples.csv",
                            "runEnergyGeneratorProfilerGameTestServer",
                            "energy_generator_working_tick_profile",
                            results,
                            overlayTicks.sampleDetails(),
                            budget.reportLines());
                    com.mojang.logging.LogUtils.getLogger()
                            .info("Topo energy generator profiler report written to {}", report.toAbsolutePath());
                    enforceWorkingBudget(helper, "combustion generator", budget);
                })
                .thenSucceed();
    }

    private static void combustionGeneratorScaleWorkingTickProfile(GameTestHelper helper) {
        if (SCALE_MACHINE_COUNT < 1 || SCALE_MACHINE_COUNT > 256) {
            helper.fail("Scale profiler machine count must be in [1, 256], got " + SCALE_MACHINE_COUNT);
        }
        List<MachineBlockEntity> machines = new ArrayList<>(SCALE_MACHINE_COUNT);
        for (int index = 0; index < SCALE_MACHINE_COUNT; index++) {
            BlockPos position = new BlockPos(
                    1 + (index & 7),
                    1 + ((index >> 6) & 3),
                    1 + ((index >> 3) & 7));
            MachineBlockEntity machine = placeEnergyGenerator(helper, position);
            insertCoalFuel(helper, machine);
            machines.add(machine);
        }
        ScaleTickAccumulator scaleTicks = new ScaleTickAccumulator();
        GcSnapshot[] scheduledGcStart = new GcSnapshot[1];

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (MachineBlockEntity machine : machines) {
                        RecipeLogic logic = logic(machine);
                        if (logic.state() != RecipeLogic.State.WORKING) {
                            helper.fail("Scale profiler setup should reach WORKING for " + machine.getBlockPos() + ", got " + logic.state());
                        }
                        machine.activatePerformanceMonitoring(
                                REAL_TICK_WARMUP_ITERATIONS + SCALE_REAL_TICK_ITERATIONS + 20);
                    }
                })
                .thenExecuteFor(
                        REAL_TICK_WARMUP_ITERATIONS,
                        () -> warmScheduledScaleTick(helper, machines))
                .thenExecute(() -> scheduledGcStart[0] = GcSnapshot.capture())
                .thenExecuteFor(
                        SCALE_REAL_TICK_ITERATIONS,
                        () -> scaleTicks.sample(helper, machines))
                .thenExecute(() -> {
                    GcSnapshot gcEnd = GcSnapshot.capture();
                    GcSnapshot gcDelta = gcEnd.minus(scheduledGcStart[0]);
                    long aggregateMachineBudget = Math.multiplyExact(
                            SCALE_MACHINE_P95_BUDGET_NANOS, SCALE_MACHINE_COUNT);
                    long aggregateRecipeBudget = Math.multiplyExact(
                            WORKING_RECIPE_P95_BUDGET_NANOS, SCALE_MACHINE_COUNT);
                    List<String> notes = List.of(
                            "Scale machines: " + SCALE_MACHINE_COUNT,
                            "Scheduled scale ticks: " + SCALE_REAL_TICK_ITERATIONS,
                            "Aggregate machine P95 budget: " + formatMicros(aggregateMachineBudget) + " us",
                            "Aggregate RecipeLogic P95 budget: " + formatMicros(aggregateRecipeBudget) + " us",
                            "Scheduled-window GC collections: " + gcDelta.collections(),
                            "Scheduled-window GC time: " + gcDelta.collectionMillis() + " ms");
                    Path report = writeReport(
                            "topo-combustion-generator-scale-profiler.md",
                            "topo-combustion-generator-scale-profiler-samples.csv",
                            "runMachineRuntimeScaleProfilerGameTestServer",
                            "combustion_generator_scale_working_tick_profile",
                            scaleTicks.toResults(),
                            List.of(),
                            notes);
                    com.mojang.logging.LogUtils.getLogger()
                            .info("Topo combustion generator scale profiler report written to {}", report.toAbsolutePath());
                    if (scaleTicks.samples() < Math.max(80, SCALE_REAL_TICK_ITERATIONS / 2)) {
                        helper.fail("Scale profiler captured too few complete aggregate samples: " + scaleTicks.samples());
                    }
                    if (gcDelta.collections() != 0L) {
                        helper.fail("Scale profiler scheduled window overlapped GC; rerun the isolated task");
                    }
                    if (scaleTicks.machineP95Nanos() > aggregateMachineBudget) {
                        helper.fail("Scale aggregate machine P95 exceeded budget: " + formatMicros(scaleTicks.machineP95Nanos()) + " us > " + formatMicros(aggregateMachineBudget) + " us");
                    }
                    if (scaleTicks.recipeP95Nanos() > aggregateRecipeBudget) {
                        helper.fail("Scale aggregate RecipeLogic P95 exceeded budget: " + formatMicros(scaleTicks.recipeP95Nanos()) + " us > " + formatMicros(aggregateRecipeBudget) + " us");
                    }
                })
                .thenSucceed();
    }

    private static void advancedGeneratorIdleTickProfile(GameTestHelper helper) {
        MachineBlockEntity machine = placeAdvancedGenerator(helper);
        RecipeLogic logic = logic(machine);
        List<ProfileResult> results = new ArrayList<>();
        IdleOverlayAccumulator overlayTicks = new IdleOverlayAccumulator();

        helper.startSequence()
                // No energy is ever inserted: the pure tick conversion recipe must park the logic
                // in a blocked state, which is exactly the "idle machine" the Jade panel shows.
                .thenExecuteAfter(5, () -> {
                    results.addAll(runIdleProfile(helper, machine, logic, helper.getLevel().getGameTime()));
                    machine.activatePerformanceMonitoring(REAL_TICK_ITERATIONS + 20);
                })
                .thenExecuteFor(
                        REAL_TICK_ITERATIONS,
                        () -> sampleIdleOverlayTick(machine, overlayTicks))
                .thenExecute(() -> {
                    if (overlayTicks.samples() == 0) {
                        helper.fail("Idle profiler should capture at least one overlay-equivalent idle tick sample");
                    }
                    results.addAll(overlayTicks.toResults());
                    Path report = writeReport(
                            "topo-idle-machine-profiler.md",
                            "topo-idle-machine-profiler-samples.csv",
                            "runIdleMachineProfilerGameTestServer",
                            "advanced_generator_idle_tick_profile",
                            results,
                            List.of());
                    com.mojang.logging.LogUtils.getLogger()
                            .info("Topo idle machine profiler report written to {}", report.toAbsolutePath());
                })
                .thenSucceed();
    }

    /**
     * Tight-loop spans that decompose the zero-energy advanced generator idle tick: the cache-key
     * reads recipe logic performs every idle tick, the full recipe search, the blocked tick-IO
     * precheck, and the whole logic tick with and without the monitoring probe.
     */
    private static List<ProfileResult> runIdleProfile(
                                                      GameTestHelper helper,
                                                      MachineBlockEntity machine,
                                                      RecipeLogic logic,
                                                      long baseGameTime) {
        List<ProfileResult> results = new ArrayList<>();
        RecipeLogic.State parkedState = logic.state();
        TopoRecipeType<TopoRecipe> recipeType = BuiltinTopoRecipeTypes.ENERGY_COMPRESSOR;

        var holder = recipeType.findRecipe(machine);
        if (holder == null) {
            helper.fail("Idle profiler expects the pure tick conversion recipe to match an empty machine");
            throw new IllegalStateException("unreachable");
        }
        TopoRecipe recipe = holder.value();
        results.add(new ProfileResult(
                "idle_parked_state_" + parkedState,
                0,
                0L,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                "evidence row; searchCacheable=" + recipeType.resourceVersionSearchCacheable(machine) + ", startRetryStable=" + recipe.startRetryResourceVersionStable() + ", directTickIo=" + recipe.hasDirectTickIo()));

        warmup(() -> profilerSink = machine.machineComponents().resourceContentVersion());
        results.add(measure(
                "traits_resourceContentVersion",
                ITERATIONS,
                () -> profilerSink = machine.machineComponents().resourceContentVersion()));

        warmup(() -> profilerSink = recipeType.resourceVersionSearchCacheable(machine));
        results.add(measure(
                "type_resourceVersionSearchCacheable",
                ITERATIONS,
                () -> profilerSink = recipeType.resourceVersionSearchCacheable(machine)));

        warmup(() -> profilerSink = recipeType.searchRevision());
        results.add(measure(
                "type_searchRevision",
                ITERATIONS,
                () -> profilerSink = recipeType.searchRevision()));

        warmup(() -> profilerSink = recipeType.findRecipe(machine));
        results.add(measure(
                "type_findRecipe_full_search",
                ITERATIONS,
                () -> profilerSink = recipeType.findRecipe(machine)));

        warmup(() -> profilerSink = recipe.checkTickIo(machine));
        results.add(measure(
                "recipe_checkTickIo_zero_energy",
                ITERATIONS,
                () -> profilerSink = recipe.checkTickIo(machine)));

        warmup(() -> profilerSink = recipe.canEmitOutputs(machine));
        results.add(measure(
                "recipe_canEmitOutputs",
                ITERATIONS,
                () -> profilerSink = recipe.canEmitOutputs(machine)));

        Runnable noReset = () -> {};
        warmupWorkingTicks(machine, logic, baseGameTime, false, noReset);
        results.add(measureWorkingTicks(
                "logic_tick_idle_direct_state_" + parkedState,
                machine,
                logic,
                baseGameTime + 10_000L,
                false,
                ITERATIONS,
                noReset));

        warmupWorkingTicks(machine, logic, baseGameTime + 20_000L, true, noReset);
        results.add(measureWorkingTicks(
                "runProfiledTick_idle_total_monitor_active",
                machine,
                logic,
                baseGameTime + 30_000L,
                true,
                ITERATIONS,
                noReset));

        if (logic.state() != parkedState) {
            helper.fail("Idle tight loops must not change the parked state, was " + parkedState + ", now " + logic.state());
        }
        return results;
    }

    private static void sampleIdleOverlayTick(MachineBlockEntity machine, IdleOverlayAccumulator overlayTicks) {
        CompoundTag data = new CompoundTag();
        long packStart = System.nanoTime();
        MachineDataProvider.packSnapshot(machine, data);
        long packSnapshotCostNanos = System.nanoTime() - packStart;

        if (data.get("perfTree") instanceof CompoundTag root) {
            Optional<CompoundTag> traits = childById(root, "traits");
            Optional<CompoundTag> tick = traits
                    .flatMap(node -> childById(node, RecipeLogic.RECIPE_LOGIC_1.id().toString()))
                    .flatMap(node -> childById(node, RecipeLogic.RECIPE_LOGIC_1.id() + ".tick"));
            if (tick.isPresent()) {
                overlayTicks.addSample(
                        root.getLongOr("nanos", 0L),
                        tick.get().getLongOr("nanos", 0L),
                        tick.get().getLongOr("avgNanos", 0L),
                        tick.get().getLongOr("peakNanos", 0L),
                        packSnapshotCostNanos);
                return;
            }
        }
        overlayTicks.addMissingSample(packSnapshotCostNanos);
    }

    /**
     * Tight-loop spans that decompose the generator WORKING tick: the NeoForge root-transaction
     * machinery (StackWalker + manager), the scalar insert with and without commit, the full
     * recipe tick-IO capability path, the framework after-tick hooks, and the whole logic tick.
     */
    private static List<ProfileResult> runGeneratorProfile(
                                                           MachineBlockEntity machine,
                                                           RecipeLogic logic,
                                                           TopoRecipe activeRecipe,
                                                           long baseGameTime) {
        List<ProfileResult> results = new ArrayList<>();
        ScalarResourcePort energyOutput = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
        var energyHandler = energyOutput.handler();
        ScalarResource energyResource = energyOutput.resource();
        Runnable drainEnergy = () -> energyHandler.set(0, ScalarResource.EMPTY, 0);

        warmup(() -> profilerSink = CALLER_CLASS_WALKER.getCallerClass());
        results.add(measure(
                "stack_walker_getCallerClass",
                ITERATIONS,
                () -> profilerSink = CALLER_CLASS_WALKER.getCallerClass()));

        warmup(RecipeLogicProfilerGameTests::openAndAbortEmptyRootTransaction);
        results.add(measure(
                "transaction_openRoot_close_empty",
                ITERATIONS,
                RecipeLogicProfilerGameTests::openAndAbortEmptyRootTransaction));

        ProfileOperation insertRevert = () -> {
            try (Transaction transaction = Transaction.openRoot()) {
                energyHandler.insert(energyResource, 30, transaction);
            }
        };
        warmup(insertRevert);
        results.add(measure("scalar_insert_30_revert", ITERATIONS, insertRevert));

        ProfileOperation insertCommit = () -> {
            try (Transaction transaction = Transaction.openRoot()) {
                energyHandler.insert(energyResource, 30, transaction);
                transaction.commit();
            }
        };
        measureChunked("warmup", WARMUP_ITERATIONS, SCALAR_CHUNK, drainEnergy, insertCommit);
        results.add(measureChunked("scalar_insert_30_commit", ITERATIONS, SCALAR_CHUNK, drainEnergy, insertCommit));

        ProfileOperation tickIo = () -> activeRecipe.handleTickIo(machine);
        measureChunked("warmup", WARMUP_ITERATIONS, SCALAR_CHUNK, drainEnergy, tickIo);
        results.add(measureChunked(
                "recipe_handleTickIo_tick_output_30_commit", ITERATIONS, SCALAR_CHUNK, drainEnergy, tickIo));

        warmup(machine::setChanged);
        results.add(measure("machine_setChanged", ITERATIONS, machine::setChanged));

        machine.afterTickerTick();
        warmup(machine::afterTickerTick);
        results.add(measure("machine_afterTickerTick_settled_active_state", ITERATIONS, machine::afterTickerTick));

        Runnable workingTickReset = () -> {
            logic.setProgressForGameTest(1);
            drainEnergy.run();
        };
        warmupWorkingTicks(machine, logic, baseGameTime, false, workingTickReset);
        results.add(measureWorkingTicks(
                "logic_tick_WORKING_direct", machine, logic, baseGameTime + 10_000L, false, ITERATIONS, workingTickReset));

        warmupWorkingTicks(machine, logic, baseGameTime + 20_000L, true, workingTickReset);
        results.add(measureWorkingTicks(
                "runProfiledTick_WORKING_total_monitor_active",
                machine,
                logic,
                baseGameTime + 30_000L,
                true,
                ITERATIONS,
                workingTickReset));

        return results;
    }

    private static void openAndAbortEmptyRootTransaction() {
        try (Transaction transaction = Transaction.openRoot()) {
            profilerSink = transaction;
        }
    }

    private static List<ProfileResult> runProfile(
                                                  MachineBlockEntity machine,
                                                  RecipeLogic logic,
                                                  TopoRecipe activeRecipe,
                                                  long baseGameTime) {
        List<ProfileResult> results = new ArrayList<>();

        Runnable refillEnergy = () -> refillMaceratorEnergy(machine);
        ProfileOperation tickIo = () -> activeRecipe.handleTickIo(machine);
        measureChunked("warmup", WARMUP_ITERATIONS, SCALAR_CHUNK, refillEnergy, tickIo);
        results.add(measureChunked(
                "active_recipe_handleTickIo_direct_energy_input",
                ITERATIONS,
                SCALAR_CHUNK,
                refillEnergy,
                tickIo));

        warmup(machine::setChanged);
        results.add(measure("machine_setChanged", ITERATIONS, machine::setChanged));

        machine.afterTickerTick();
        warmup(machine::afterTickerTick);
        results.add(measure("machine_afterTickerTick_settled_active_state", ITERATIONS, machine::afterTickerTick));

        Runnable workingTickReset = () -> {
            refillMaceratorEnergy(machine);
            logic.setProgressForGameTest(1);
        };
        warmupWorkingTicks(machine, logic, baseGameTime, false, workingTickReset);
        results.add(measureWorkingTicks(
                "logic_tick_WORKING_direct", machine, logic, baseGameTime + 10_000L, false, ITERATIONS, workingTickReset));

        warmupWorkingTicks(machine, logic, baseGameTime + 20_000L, true, workingTickReset);
        results.add(measureWorkingTicks(
                "runProfiledTick_WORKING_total_monitor_active",
                machine,
                logic,
                baseGameTime + 30_000L,
                true,
                ITERATIONS,
                workingTickReset));

        return results;
    }

    private static void warmup(ProfileOperation operation) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            operation.run();
        }
    }

    private static ProfileResult measure(String name, int iterations, ProfileOperation operation) {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            operation.run();
        }
        return ProfileResult.microBenchmark(name, iterations, System.nanoTime() - start);
    }

    /**
     * Tight loop with an untimed per-chunk reset, for operations whose repetition drifts machine
     * state (e.g. committed scalar inserts filling the buffer and flipping the measured branch).
     */
    private static ProfileResult measureChunked(
                                                String name,
                                                int iterations,
                                                int chunkSize,
                                                Runnable untimedPerChunkReset,
                                                ProfileOperation operation) {
        int measured = 0;
        long elapsed = 0L;
        while (measured < iterations) {
            untimedPerChunkReset.run();
            int chunk = Math.min(chunkSize, iterations - measured);
            long start = System.nanoTime();
            for (int i = 0; i < chunk; i++) {
                operation.run();
            }
            elapsed += System.nanoTime() - start;
            measured += chunk;
        }
        return ProfileResult.microBenchmark(name, iterations, elapsed);
    }

    private static void warmupWorkingTicks(
                                           MachineBlockEntity machine,
                                           RecipeLogic logic,
                                           long baseGameTime,
                                           boolean profiled,
                                           Runnable untimedPerChunkReset) {
        measureWorkingTicks("warmup", machine, logic, baseGameTime, profiled, WARMUP_ITERATIONS, untimedPerChunkReset);
    }

    private static ProfileResult measureWorkingTicks(
                                                     String name,
                                                     MachineBlockEntity machine,
                                                     RecipeLogic logic,
                                                     long baseGameTime,
                                                     boolean profiled,
                                                     int iterations,
                                                     Runnable untimedPerChunkReset) {
        int measured = 0;
        long elapsed = 0L;
        long gameTime = baseGameTime;
        if (profiled) {
            machine.activatePerformanceMonitoring(performanceWindowTicks(machine, baseGameTime, iterations));
        }
        while (measured < iterations) {
            untimedPerChunkReset.run();
            int chunk = Math.min(WORKING_TICK_CHUNK, iterations - measured);
            long start = System.nanoTime();
            for (int i = 0; i < chunk; i++) {
                if (profiled) {
                    logic.runProfiledTick(gameTime++, NoopTickHandle.INSTANCE);
                } else {
                    logic.tick(gameTime++, NoopTickHandle.INSTANCE);
                }
            }
            elapsed += System.nanoTime() - start;
            measured += chunk;
        }
        return ProfileResult.microBenchmark(name, iterations, elapsed);
    }

    private static void sampleScheduledOverlayTick(
                                                   GameTestHelper helper,
                                                   MachineBlockEntity machine,
                                                   RecipeLogic logic,
                                                   OverlayTickAccumulator overlayTicks) {
        if (logic.state() != RecipeLogic.State.WORKING) {
            helper.fail("Scheduled profiler sample should remain WORKING, got " + logic.state());
        }

        long scheduledGameTime = helper.getLevel().getGameTime();
        int progressBeforeKeepAlive = logic.progress();
        MachinePerformanceSnapshot beforePackSnapshot = machine.lastPerformanceSnapshot();
        long beforePackTickNanos = recipeLogicNanos(beforePackSnapshot);

        CompoundTag data = new CompoundTag();
        long packStart = System.nanoTime();
        MachineDataProvider.packSnapshot(machine, data);
        long packSnapshotCostNanos = System.nanoTime() - packStart;
        MachinePerformanceSnapshot afterPackSnapshot = machine.lastPerformanceSnapshot();
        long afterPackTickNanos = recipeLogicNanos(afterPackSnapshot);

        boolean captured = false;
        if (data.get("perfTree") instanceof CompoundTag root) {
            Optional<CompoundTag> traits = childById(root, "traits");
            Optional<CompoundTag> recipeTrait = traits.flatMap(node -> childById(node, RecipeLogic.RECIPE_LOGIC_1.id().toString()));
            Optional<CompoundTag> tick = recipeTrait.flatMap(node -> childById(node, RecipeLogic.RECIPE_LOGIC_1.id() + ".tick"));
            if (recipeTrait.isPresent() && tick.isPresent()) {
                long displayedTickNanos = tick.get().getLongOr("nanos", 0L);
                long boundaryDelta = Math.abs(displayedTickNanos - beforePackTickNanos) + Math.abs(afterPackTickNanos - displayedTickNanos);
                if (boundaryDelta != 0L) {
                    helper.fail("Jade packSnapshot must not mutate or add to recorded recipe tick nanos, before=" + beforePackTickNanos + ", displayed=" + displayedTickNanos + ", after=" + afterPackTickNanos);
                }
                overlayTicks.addOverlaySample(
                        scheduledGameTime,
                        beforePackSnapshot.gameTime(),
                        progressBeforeKeepAlive,
                        root.getLongOr("nanos", 0L),
                        recipeTrait.get().getLongOr("nanos", 0L),
                        displayedTickNanos,
                        packSnapshotCostNanos,
                        boundaryDelta);
                captured = true;
            }
        }
        if (!captured) {
            overlayTicks.addMissingSample(
                    scheduledGameTime,
                    beforePackSnapshot.gameTime(),
                    progressBeforeKeepAlive,
                    packSnapshotCostNanos);
        }

        keepWorkingInMiddle(logic);
    }

    private static void warmScheduledOverlayTick(
                                                 GameTestHelper helper,
                                                 MachineBlockEntity machine,
                                                 RecipeLogic logic) {
        if (logic.state() != RecipeLogic.State.WORKING) {
            helper.fail("Scheduled profiler warmup should remain WORKING, got " + logic.state());
        }
        MachineDataProvider.packSnapshot(machine, new CompoundTag());
        keepWorkingInMiddle(logic);
    }

    private static void warmScheduledScaleTick(
                                               GameTestHelper helper,
                                               List<MachineBlockEntity> machines) {
        for (MachineBlockEntity machine : machines) {
            RecipeLogic logic = logic(machine);
            if (logic.state() != RecipeLogic.State.WORKING) {
                helper.fail("Scale profiler warmup machine left WORKING at " + machine.getBlockPos() + ": " + logic.state());
            }
            keepWorkingInMiddle(logic);
        }
    }

    private static void keepWorkingInMiddle(RecipeLogic logic) {
        if (logic.state() == RecipeLogic.State.WORKING && logic.maxProgress() > 0 && logic.progress() >= logic.maxProgress() - 2) {
            logic.setProgressForGameTest(1);
        }
    }

    private static Optional<CompoundTag> childById(CompoundTag parent, String id) {
        ListTag children = parent.getList("children").orElseGet(ListTag::new);
        for (int i = 0; i < children.size(); i++) {
            Optional<CompoundTag> child = children.getCompound(i);
            if (child.isPresent() && id.equals(child.get().getStringOr("id", ""))) {
                return child;
            }
        }
        return Optional.empty();
    }

    private static long recipeLogicNanos(MachinePerformanceSnapshot snapshot) {
        String componentId = RecipeLogic.RECIPE_LOGIC_1.id().toString();
        for (MachinePerformanceSnapshot.ComponentSample sample : snapshot.components()) {
            if (sample.id().equals(componentId)) {
                return sample.nanos();
            }
        }
        return 0L;
    }

    private static int performanceWindowTicks(MachineBlockEntity machine, long baseGameTime, int iterations) {
        long currentGameTime = machine.getLevel() == null ? baseGameTime : machine.getLevel().getGameTime();
        long requestedEndGameTime = baseGameTime + iterations + WORKING_TICK_CHUNK + 20L;
        long ticks = Math.max(iterations + WORKING_TICK_CHUNK + 20L, requestedEndGameTime - currentGameTime + 1L);
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    private static WorkingBudget workingBudget(
                                               OverlayTickAccumulator overlayTicks,
                                               GcSnapshot scheduledGcStart) {
        GcSnapshot start = scheduledGcStart == null ? GcSnapshot.capture() : scheduledGcStart;
        return new WorkingBudget(
                overlayTicks.samples(),
                overlayTicks.missingSnapshots(),
                overlayTicks.machineP95Nanos(),
                overlayTicks.recipeP95Nanos(),
                GcSnapshot.capture().minus(start));
    }

    private static void enforceWorkingBudget(
                                             GameTestHelper helper,
                                             String scenario,
                                             WorkingBudget budget) {
        if (budget.samples() < MIN_WORKING_SAMPLES) {
            helper.fail(scenario + " profiler captured " + budget.samples() + " samples; required at least " + MIN_WORKING_SAMPLES);
        }
        if (budget.missingSnapshots() != 0) {
            helper.fail(scenario + " profiler missed " + budget.missingSnapshots() + " scheduled performance snapshots");
        }
        if (budget.gcDelta().collections() != 0L) {
            helper.fail(scenario + " scheduled measurement overlapped " + budget.gcDelta().collections() + " GC collections; rerun the isolated task");
        }
        if (budget.machineP95Nanos() > WORKING_MACHINE_P95_BUDGET_NANOS) {
            helper.fail(scenario + " scheduled whole-machine P95 exceeded budget: " + formatMicros(budget.machineP95Nanos()) + " us > " + formatMicros(WORKING_MACHINE_P95_BUDGET_NANOS) + " us");
        }
        if (budget.recipeP95Nanos() > WORKING_RECIPE_P95_BUDGET_NANOS) {
            helper.fail(scenario + " scheduled RecipeLogic P95 exceeded budget: " + formatMicros(budget.recipeP95Nanos()) + " us > " + formatMicros(WORKING_RECIPE_P95_BUDGET_NANOS) + " us");
        }
    }

    private static Path writeReport(
                                    String reportFileName,
                                    String csvFileName,
                                    String gradleTaskName,
                                    String testName,
                                    List<ProfileResult> results,
                                    List<OverlayTickSample> sampleDetails) {
        return writeReport(
                reportFileName,
                csvFileName,
                gradleTaskName,
                testName,
                results,
                sampleDetails,
                List.of());
    }

    private static Path writeReport(
                                    String reportFileName,
                                    String csvFileName,
                                    String gradleTaskName,
                                    String testName,
                                    List<ProfileResult> results,
                                    List<OverlayTickSample> sampleDetails,
                                    List<String> runNotes) {
        Path reportDir = projectRoot().resolve("build").resolve("reports");
        Path report = reportDir.resolve(reportFileName);
        Path csv = reportDir.resolve(csvFileName);
        try {
            Files.createDirectories(reportDir);
            Files.writeString(csv, formatSampleCsv(sampleDetails), StandardCharsets.UTF_8);
            Files.writeString(
                    report,
                    formatReport(gradleTaskName, testName, results, sampleDetails, csv, runNotes),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write recipe logic profiler report to " + report, exception);
        }
        return report;
    }

    private static String formatReport(
                                       String gradleTaskName,
                                       String testName,
                                       List<ProfileResult> results,
                                       List<OverlayTickSample> sampleDetails,
                                       Path csv,
                                       List<String> runNotes) {
        StringBuilder report = new StringBuilder(1024);
        report.append("# Topo Recipe Logic Profiler\n\n");
        report.append("Generated: ").append(Instant.now()).append("\n\n");
        report.append("Iterations: ").append(ITERATIONS).append("\n\n");
        report.append("Scheduled real ticks: ").append(REAL_TICK_ITERATIONS).append("\n\n");
        report.append("## Runtime environment\n\n");
        report.append("- Java: ").append(System.getProperty("java.runtime.version", "unknown")).append('\n');
        report.append("- VM: ").append(System.getProperty("java.vm.name", "unknown")).append('\n');
        report.append("- OS: ").append(System.getProperty("os.name", "unknown"))
                .append(' ').append(System.getProperty("os.version", "unknown"))
                .append(" / ").append(System.getProperty("os.arch", "unknown")).append('\n');
        report.append("- Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n\n");
        if (!runNotes.isEmpty()) {
            report.append("## Acceptance window\n\n");
            for (String note : runNotes) {
                report.append("- ").append(note).append('\n');
            }
            report.append('\n');
        }
        report.append("| Segment | Samples | Total ms | Avg us | Min us | P50 us | P90 us | P95 us | P99 us | Max us | Note |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (ProfileResult result : results) {
            report.append("| ")
                    .append(result.name())
                    .append(" | ")
                    .append(result.iterations())
                    .append(" | ")
                    .append(formatMillis(result.elapsedNanos()))
                    .append(" | ")
                    .append(formatMicros(result.averageNanos()))
                    .append(" | ")
                    .append(formatOptionalMicros(result.minNanos()))
                    .append(" | ")
                    .append(formatOptionalMicros(result.p50Nanos()))
                    .append(" | ")
                    .append(formatOptionalMicros(result.p90Nanos()))
                    .append(" | ")
                    .append(formatOptionalMicros(result.p95Nanos()))
                    .append(" | ")
                    .append(formatOptionalMicros(result.p99Nanos()))
                    .append(" | ")
                    .append(formatOptionalMicros(result.maxNanos()))
                    .append(" | ")
                    .append(result.note())
                    .append(" |\n");
        }
        report.append("\n");
        appendCostAttribution(report, csv);
        appendTopSamples(
                report,
                "Slowest displayed recipe tick samples",
                sampleDetails,
                OverlayTickSample::recipeTickNanos,
                true);
        appendTopSamples(
                report,
                "Slowest Jade packSnapshot samples",
                sampleDetails,
                OverlayTickSample::packSnapshotCostNanos,
                false);
        report.append("Run with IntelliJ Profiler by profiling Gradle task `").append(gradleTaskName).append("`. ")
                .append("The task selects only `topo:").append(testName).append("`; ")
                .append("discard the report if the GameTest log does not say `1 GAME TESTS COMPLETE`. ")
                .append("Tight-loop rows are reference baselines only. ")
                .append("Rows named `overlay_*_displayed_nanos` are read from the same `perfTree` NBT ")
                .append("that Jade uses for the in-game overlay, so compare screenshots against those ")
                .append("distribution rows, especially p90/p95/p99/max. ")
                .append("`overlay_server_packSnapshot_cost` is the server-side cost to prepare Jade data; ")
                .append("it is real overlay overhead but is not itself shown as a timing-tree node. ")
                .append("`overlay_jade_pack_boundary_delta_nanos` must stay zero; it proves the Jade ")
                .append("server data path did not mutate or add transfer/packing latency to the displayed ")
                .append("recipe tick sample.\n");
        return report.toString();
    }

    private static void appendCostAttribution(StringBuilder report, Path csv) {
        report.append("## Cost attribution\n\n");
        report.append("- `overlay_recipe_tick_child_displayed_nanos`: the screenshot-comparable recipe tick node from Jade `perfTree`.\n");
        report.append("- `overlay_server_packSnapshot_cost`: server-side Jade data preparation wall time, tracked separately from the timing tree.\n");
        report.append("- `overlay_jade_pack_boundary_delta_nanos`: must remain 0; non-zero means packing mutated or polluted the displayed tick sample.\n");
        report.append("- Detailed samples CSV: `")
                .append(relativeToProject(csv))
                .append("`.\n\n");
    }

    private static void appendTopSamples(
                                         StringBuilder report,
                                         String title,
                                         List<OverlayTickSample> sampleDetails,
                                         ToLongFunction<OverlayTickSample> metric,
                                         boolean requireOverlay) {
        report.append("## ").append(title).append("\n\n");
        List<OverlayTickSample> topSamples = sampleDetails.stream()
                .filter(sample -> !requireOverlay || sample.overlayPresent())
                .sorted(Comparator.comparingLong(metric).reversed())
                .limit(DETAIL_SAMPLE_LIMIT)
                .toList();
        if (topSamples.isEmpty()) {
            report.append("No samples captured.\n\n");
            return;
        }
        report.append("| Rank | Sample | GameTime | SnapshotTick | Progress | Overlay | Recipe tick us | Machine total us | Jade pack us | Boundary delta us |\n");
        report.append("| ---: | ---: | ---: | ---: | ---: | :---: | ---: | ---: | ---: | ---: |\n");
        int rank = 1;
        for (OverlayTickSample sample : topSamples) {
            report.append("| ")
                    .append(rank++)
                    .append(" | ")
                    .append(sample.sampleIndex())
                    .append(" | ")
                    .append(sample.scheduledGameTime())
                    .append(" | ")
                    .append(sample.snapshotGameTime())
                    .append(" | ")
                    .append(sample.progress())
                    .append(" | ")
                    .append(sample.overlayPresent() ? "yes" : "no")
                    .append(" | ")
                    .append(formatPresentMicros(sample.overlayPresent(), sample.recipeTickNanos()))
                    .append(" | ")
                    .append(formatPresentMicros(sample.overlayPresent(), sample.machineTotalNanos()))
                    .append(" | ")
                    .append(formatMicros(sample.packSnapshotCostNanos()))
                    .append(" | ")
                    .append(formatPresentMicros(sample.overlayPresent(), sample.packBoundaryDeltaNanos()))
                    .append(" |\n");
        }
        report.append("\n");
    }

    private static String formatSampleCsv(List<OverlayTickSample> sampleDetails) {
        StringBuilder csv = new StringBuilder(Math.max(256, sampleDetails.size() * 96));
        csv.append("sample,scheduledGameTime,snapshotGameTime,progress,overlayPresent,machineTotalUs,recipeTraitUs,recipeTickUs,packSnapshotUs,boundaryDeltaUs\n");
        for (OverlayTickSample sample : sampleDetails) {
            csv.append(sample.sampleIndex())
                    .append(',')
                    .append(sample.scheduledGameTime())
                    .append(',')
                    .append(sample.snapshotGameTime())
                    .append(',')
                    .append(sample.progress())
                    .append(',')
                    .append(sample.overlayPresent())
                    .append(',');
            appendCsvOptionalMicros(csv, sample.overlayPresent(), sample.machineTotalNanos());
            csv.append(',');
            appendCsvOptionalMicros(csv, sample.overlayPresent(), sample.recipeTraitNanos());
            csv.append(',');
            appendCsvOptionalMicros(csv, sample.overlayPresent(), sample.recipeTickNanos());
            csv.append(',')
                    .append(formatMicros(sample.packSnapshotCostNanos()))
                    .append(',');
            appendCsvOptionalMicros(csv, sample.overlayPresent(), sample.packBoundaryDeltaNanos());
            csv.append('\n');
        }
        return csv.toString();
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String formatMicros(double nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000.0);
    }

    private static String formatOptionalMicros(long nanos) {
        return nanos < 0L ? "-" : formatMicros(nanos);
    }

    private static String formatPresentMicros(boolean present, long nanos) {
        return present ? formatMicros(nanos) : "-";
    }

    private static void appendCsvOptionalMicros(StringBuilder csv, boolean present, long nanos) {
        if (present) {
            csv.append(formatMicros(nanos));
        }
    }

    private static String relativeToProject(Path path) {
        Path root = projectRoot().toAbsolutePath();
        Path absolute = path.toAbsolutePath();
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private static Path projectRoot() {
        String configured = System.getProperty("oi.profiler.projectRoot", "");
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path cwd = Path.of("").toAbsolutePath();
        return "run".equals(cwd.getFileName().toString()) ? cwd.getParent() : cwd;
    }

    private static MachineBlockEntity placeMacerator(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static MachineBlockEntity placeEnergyGenerator(GameTestHelper helper) {
        return placeEnergyGenerator(helper, MACHINE_POS);
    }

    private static MachineBlockEntity placeEnergyGenerator(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, BuiltinTopoMachines.COMBUSTION_GENERATOR_T1.registeredBlock().getDefaultState());
        return helper.getBlockEntity(position, MachineBlockEntity.class);
    }

    private static MachineBlockEntity placeAdvancedGenerator(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinTopoMachines.ENERGY_COMPRESSOR_T2.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static void insertCoalFuel(GameTestHelper helper) {
        MachineBlockEntity machine = helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
        insertCoalFuel(helper, machine);
    }

    private static void insertCoalFuel(GameTestHelper helper, MachineBlockEntity machine) {
        ResourceHandler<ItemResource> input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).handler();
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(ItemResource.of(Items.COAL), 64, transaction);
            if (inserted != 64) {
                helper.fail("Profiler setup should insert a full coal stack, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void drainGeneratorEnergy(MachineBlockEntity machine) {
        machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1).handler().set(0, ScalarResource.EMPTY, 0);
    }

    private static void refillMaceratorEnergy(MachineBlockEntity machine) {
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), Math.toIntExact(energy.capacityAmount()));
    }

    private static RecipeLogic logic(MachineBlockEntity machine) {
        return machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static void insertIronInput(GameTestHelper helper) {
        ResourceHandler<ItemResource> input = helper.getLevel().getCapability(
                Capabilities.Item.BLOCK,
                helper.absolutePos(MACHINE_POS),
                Direction.UP);
        if (input == null) {
            helper.fail("Macerator UP side should expose an item input capability");
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(ItemResource.of(MaterialHelper.requireItem(
                    BuiltinTopoMaterials.IRON,
                    BuiltinTopoMaterialForms.ORE)), 1, transaction);
            if (inserted != 1) {
                helper.fail("Profiler setup should insert one iron ore, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static TopoRecipe activeRecipe(
                                           GameTestHelper helper,
                                           MachineBlockEntity machine,
                                           RecipeLogic logic,
                                           TopoRecipeType<TopoRecipe> recipeType) {
        ResourceKey<Recipe<?>> recipeId = logic.activeRecipeId();
        if (recipeId == null) {
            helper.fail("Profiler setup should have an active recipe id");
        }
        var holder = recipeType.resolveRecipe(helper.getLevel().getServer(), recipeId);
        if (holder == null) {
            helper.fail("Profiler setup should resolve active Topo recipe " + recipeId);
        }
        TopoRecipe modified = holder.value();
        for (var match : machine.machineComponents().services(RecipeModifier.KEY, logic)) {
            modified = match.value().modify(modified, machine);
        }
        return modified;
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Recipe logic profiler GameTest index out of range: " + index);
        }
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
        return new TestData<>(
                environment,
                EMPTY_STRUCTURE,
                Math.max(120, REAL_TICK_WARMUP_ITERATIONS + REAL_TICK_ITERATIONS + 100),
                0,
                true,
                Rotation.NONE);
    }

    @FunctionalInterface
    private interface ProfileOperation {

        void run();
    }

    private static final class OverlayTickAccumulator {

        private final NanosAccumulator machineTotal = new NanosAccumulator();
        private final NanosAccumulator recipeTrait = new NanosAccumulator();
        private final NanosAccumulator recipeTick = new NanosAccumulator();
        private final NanosAccumulator packSnapshotCost = new NanosAccumulator();
        private final NanosAccumulator packBoundaryDelta = new NanosAccumulator();
        private final List<OverlayTickSample> sampleDetails = new ArrayList<>();
        private int missingSnapshots;

        void addOverlaySample(
                              long scheduledGameTime,
                              long snapshotGameTime,
                              int progress,
                              long machineTotalNanos,
                              long recipeTraitNanos,
                              long recipeTickNanos,
                              long packSnapshotCostNanos,
                              long packBoundaryDeltaNanos) {
            machineTotal.add(machineTotalNanos);
            recipeTrait.add(recipeTraitNanos);
            recipeTick.add(recipeTickNanos);
            packSnapshotCost.add(packSnapshotCostNanos);
            packBoundaryDelta.add(packBoundaryDeltaNanos);
            sampleDetails.add(new OverlayTickSample(
                    sampleDetails.size() + 1,
                    scheduledGameTime,
                    snapshotGameTime,
                    progress,
                    true,
                    Math.max(0L, machineTotalNanos),
                    Math.max(0L, recipeTraitNanos),
                    Math.max(0L, recipeTickNanos),
                    Math.max(0L, packSnapshotCostNanos),
                    Math.max(0L, packBoundaryDeltaNanos)));
        }

        void addMissingSample(long scheduledGameTime, long snapshotGameTime, int progress, long packSnapshotCostNanos) {
            missingSnapshots++;
            packSnapshotCost.add(packSnapshotCostNanos);
            sampleDetails.add(new OverlayTickSample(
                    sampleDetails.size() + 1,
                    scheduledGameTime,
                    snapshotGameTime,
                    progress,
                    false,
                    0L,
                    0L,
                    0L,
                    Math.max(0L, packSnapshotCostNanos),
                    0L));
        }

        int samples() {
            return recipeTick.size();
        }

        int missingSnapshots() {
            return missingSnapshots;
        }

        long machineP95Nanos() {
            return machineTotal.percentileNanos(0.95d);
        }

        long recipeP95Nanos() {
            return recipeTick.percentileNanos(0.95d);
        }

        List<OverlayTickSample> sampleDetails() {
            return List.copyOf(sampleDetails);
        }

        List<ProfileResult> toResults() {
            String note = "same perfTree source as Jade overlay; missingSnapshots=" + missingSnapshots;
            return List.of(
                    machineTotal.toResult("overlay_machine_total_displayed_nanos", note),
                    recipeTrait.toResult("overlay_recipe_trait_displayed_nanos", note),
                    recipeTick.toResult("overlay_recipe_tick_child_displayed_nanos", note),
                    packSnapshotCost.toResult(
                            "overlay_server_packSnapshot_cost",
                            "server data provider wall time; not displayed inside perfTree; missingSnapshots=" + missingSnapshots),
                    packBoundaryDelta.toResult(
                            "overlay_jade_pack_boundary_delta_nanos",
                            "must be 0; verifies Jade pack/transport latency is not included in displayed recipe tick nanos; missingSnapshots=" + missingSnapshots));
        }
    }

    /** Idle-machine variant: tracks the Jade panel's value column (EMA) and {@code ^peak} too. */
    private static final class IdleOverlayAccumulator {

        private final NanosAccumulator machineTotal = new NanosAccumulator();
        private final NanosAccumulator recipeTick = new NanosAccumulator();
        private final NanosAccumulator recipeTickAvg = new NanosAccumulator();
        private final NanosAccumulator recipeTickPeak = new NanosAccumulator();
        private final NanosAccumulator packSnapshotCost = new NanosAccumulator();
        private int missingSnapshots;

        void addSample(
                       long machineTotalNanos,
                       long recipeTickNanos,
                       long recipeTickAvgNanos,
                       long recipeTickPeakNanos,
                       long packSnapshotCostNanos) {
            machineTotal.add(machineTotalNanos);
            recipeTick.add(recipeTickNanos);
            recipeTickAvg.add(recipeTickAvgNanos);
            recipeTickPeak.add(recipeTickPeakNanos);
            packSnapshotCost.add(packSnapshotCostNanos);
        }

        void addMissingSample(long packSnapshotCostNanos) {
            missingSnapshots++;
            packSnapshotCost.add(packSnapshotCostNanos);
        }

        int samples() {
            return recipeTick.size();
        }

        List<ProfileResult> toResults() {
            String note = "idle overlay; same perfTree source as Jade; missingSnapshots=" + missingSnapshots;
            return List.of(
                    machineTotal.toResult("overlay_idle_machine_total_nanos", note),
                    recipeTick.toResult("overlay_idle_recipe_tick_nanos", note),
                    recipeTickAvg.toResult(
                            "overlay_idle_recipe_tick_avgNanos",
                            "EMA shown as the Jade panel value column"),
                    recipeTickPeak.toResult(
                            "overlay_idle_recipe_tick_peakNanos",
                            "decaying peak shown as ^ in the Jade panel"),
                    packSnapshotCost.toResult("overlay_idle_server_packSnapshot_cost", note));
        }
    }

    private static final class NanosAccumulator {

        private final List<Long> values = new ArrayList<>();

        void add(long nanos) {
            values.add(Math.max(0L, nanos));
        }

        int size() {
            return values.size();
        }

        long percentileNanos(double percentile) {
            if (values.isEmpty()) {
                return Long.MAX_VALUE;
            }
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            return percentile(sorted, percentile);
        }

        ProfileResult toResult(String name, String note) {
            if (values.isEmpty()) {
                return new ProfileResult(name, 0, 0L, -1L, -1L, -1L, -1L, -1L, -1L, note + "; noSamples");
            }
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            long total = 0L;
            for (long value : values) {
                total += value;
            }
            return new ProfileResult(
                    name,
                    values.size(),
                    total,
                    sorted.getFirst(),
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.90),
                    percentile(sorted, 0.95),
                    percentile(sorted, 0.99),
                    sorted.getLast(),
                    note);
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(sorted.size() * percentile) - 1;
            return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
        }
    }

    private record ProfileResult(
                                 String name,
                                 int iterations,
                                 long elapsedNanos,
                                 long minNanos,
                                 long p50Nanos,
                                 long p90Nanos,
                                 long p95Nanos,
                                 long p99Nanos,
                                 long maxNanos,
                                 String note) {

        static ProfileResult microBenchmark(String name, int iterations, long elapsedNanos) {
            return new ProfileResult(name, iterations, elapsedNanos, -1L, -1L, -1L, -1L, -1L, -1L, "tight-loop baseline; not overlay-equivalent");
        }

        double averageNanos() {
            return iterations <= 0 ? 0.0 : elapsedNanos / (double) iterations;
        }
    }

    private record OverlayTickSample(
                                     int sampleIndex,
                                     long scheduledGameTime,
                                     long snapshotGameTime,
                                     int progress,
                                     boolean overlayPresent,
                                     long machineTotalNanos,
                                     long recipeTraitNanos,
                                     long recipeTickNanos,
                                     long packSnapshotCostNanos,
                                     long packBoundaryDeltaNanos) {}

    private record GcSnapshot(long collections, long collectionMillis) {

        static GcSnapshot capture() {
            long collections = 0L;
            long millis = 0L;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                collections += Math.max(0L, bean.getCollectionCount());
                millis += Math.max(0L, bean.getCollectionTime());
            }
            return new GcSnapshot(collections, millis);
        }

        GcSnapshot minus(GcSnapshot before) {
            return new GcSnapshot(
                    Math.max(0L, collections - before.collections),
                    Math.max(0L, collectionMillis - before.collectionMillis));
        }
    }

    private record WorkingBudget(
                                 int samples,
                                 int missingSnapshots,
                                 long machineP95Nanos,
                                 long recipeP95Nanos,
                                 GcSnapshot gcDelta) {

        List<String> reportLines() {
            return List.of(
                    "Scheduled samples: " + samples,
                    "Missing snapshots: " + missingSnapshots,
                    "Whole-machine P95: " + formatMicros(machineP95Nanos) + " us (budget " + formatMicros(WORKING_MACHINE_P95_BUDGET_NANOS) + " us)",
                    "RecipeLogic P95: " + formatMicros(recipeP95Nanos) + " us (budget " + formatMicros(WORKING_RECIPE_P95_BUDGET_NANOS) + " us)",
                    "Scheduled-window GC collections: " + gcDelta.collections,
                    "Scheduled-window GC time: " + gcDelta.collectionMillis + " ms");
        }
    }

    private static final class ScaleTickAccumulator {

        private final NanosAccumulator machineTotals = new NanosAccumulator();
        private final NanosAccumulator recipeTotals = new NanosAccumulator();
        private int missingSnapshots;

        void sample(GameTestHelper helper, List<MachineBlockEntity> machines) {
            long machineTotal = 0L;
            long recipeTotal = 0L;
            for (MachineBlockEntity machine : machines) {
                RecipeLogic logic = logic(machine);
                if (logic.state() != RecipeLogic.State.WORKING) {
                    helper.fail("Scale profiler machine left WORKING at " + machine.getBlockPos() + ": " + logic.state());
                }
                if (!machine.publishRecentPerformanceSnapshot(machine.performanceSampleMaxAgeTicks())) {
                    missingSnapshots++;
                    return;
                }
                MachinePerformanceSnapshot snapshot = machine.lastPerformanceSnapshot();
                if (snapshot.isEmpty()) {
                    missingSnapshots++;
                    return;
                }
                machineTotal = Math.addExact(machineTotal, snapshot.totalNanos());
                recipeTotal = Math.addExact(recipeTotal, recipeLogicNanos(snapshot));
            }
            machineTotals.add(machineTotal);
            recipeTotals.add(recipeTotal);
        }

        int samples() {
            return machineTotals.size();
        }

        long machineP95Nanos() {
            return machineTotals.percentileNanos(0.95d);
        }

        long recipeP95Nanos() {
            return recipeTotals.percentileNanos(0.95d);
        }

        List<ProfileResult> toResults() {
            String note = "scheduled aggregate across " + SCALE_MACHINE_COUNT + " real machines; missingSnapshots=" + missingSnapshots;
            return List.of(
                    machineTotals.toResult("scale_scheduled_machine_total_nanos", note),
                    recipeTotals.toResult("scale_scheduled_recipe_logic_nanos", note));
        }
    }
}
