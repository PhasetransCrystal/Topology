package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.api.tick.TickHub;
import net.ptcrys.topo.api.api.tick.TickKind;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Real integrated-client profiler for a 10x10 machine-and-pipe world. Activated only by
 * {@code topo-machine-world-profiler.flag}; it owns an isolated world, records JFR and Spark during
 * an isolated Spark window, records the same counters without profilers, then records an isolated
 * JFR window. It exports the evidence, captures the world, and exits.
 */
public final class MachineWorldProfilerProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Topo-MachineWorldProfiler");
    private static final Path FLAG_FILE = Path.of("topo-machine-world-profiler.flag");
    private static final Path REPORT_FILE = Path.of("topo-machine-world-profiler-report.txt");
    private static final String LEVEL_ID = "topo-machine-world-profiler-" + (System.currentTimeMillis() % 100_000_000L);
    private static final String SCREENSHOT_NAME = "topo-machine-world-profiler.png";
    private static final int GRID_SIZE = 10;
    private static final int MODULE_COUNT = GRID_SIZE * GRID_SIZE;
    private static final int MODULE_SPACING = 5;
    private static final int WARMUP_TICKS = 200;
    private static final int PROFILER_SETTLE_TICKS = 5;
    private static final int WINDOW_TICKS = 600;
    private static final int SPARK_FLUSH_TIMEOUT_TICKS = 600;
    private static final int CLEAN_COOLDOWN_TICKS = 40;
    private static final int STABLE_SPARK_FILE_TICKS = 2;
    private static final int GENERATOR = 0;
    private static final int HEATER = 1;
    private static final int TARGET_COUNT = 2;
    private static final int HEATER_CYCLE_TICKS = 6;
    private static final int EXPECTED_HEATER_WORKING_SAMPLES = 500;
    private static final int EXPECTED_HEATER_IDLE_SAMPLES = 100;
    private static final long EXPECTED_HEAT_DELTA_PER_MODULE = 4_500L;
    private static final long EXPECTED_HEATER_STARTS_PER_WINDOW = (long) WINDOW_TICKS / HEATER_CYCLE_TICKS * MODULE_COUNT;
    private static final long EXPECTED_HEAT_DELTA_PER_WINDOW = EXPECTED_HEAT_DELTA_PER_MODULE * MODULE_COUNT;
    private static final long TICKER_P95_BUDGET_NANOS = 10_000L;
    private static final long RECIPE_LOGIC_P95_BUDGET_NANOS = 5_000L;
    private static final int WAIT_TIMEOUT_TICKS = 12_000;
    private static final int MONITOR_TICKS = WARMUP_TICKS + WINDOW_TICKS * 3 + SPARK_FLUSH_TIMEOUT_TICKS + CLEAN_COOLDOWN_TICKS + 100;
    private static final String RECIPE_LOGIC_ID = RecipeLogic.RECIPE_LOGIC_1.id().toString();
    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path REPORTS_DIR = PROJECT_ROOT.resolve("build/reports");
    private static final Path JFR_FILE = REPORTS_DIR.resolve("topo-machine-world-profiler.jfr");
    private static final Path RAW_FILE = REPORTS_DIR.resolve("topo-machine-world-profiler-samples.csv");
    private static final Path SUMMARY_FILE = REPORTS_DIR.resolve("topo-machine-world-profiler-summary.csv");
    private static final Path HOT_METHODS_FILE = REPORTS_DIR.resolve("topo-machine-world-profiler-hot-methods.csv");
    private static final Path STACKS_FILE = REPORTS_DIR.resolve("topo-machine-world-profiler-stacks.csv");
    private static final Path MARKDOWN_FILE = REPORTS_DIR.resolve("topo-machine-world-profiler.md");

    private enum ClientState {
        WAIT_TITLE,
        WAIT_WORLD,
        WAIT_SERVER,
        WAIT_SPARK,
        WAIT_SCREENSHOT,
        ABORTING,
        DONE
    }

    private enum ServerPhase {
        WARMUP,
        SPARK_SETTLE,
        SPARK_PROFILE,
        SPARK_FLUSH,
        CLEAN,
        JFR_PROFILE,
        COMPLETE
    }

    private record Module(
                          int index,
                          BlockPos generatorPos,
                          BlockPos sourcePipePos,
                          BlockPos sinkPos,
                          MachineBlockEntity generator,
                          MachineBlockEntity sink) {}

    private record TickAggregate(
                                 String window,
                                 int tickIndex,
                                 long generatorTickerNanos,
                                 long generatorRecipeLogicNanos,
                                 long heaterTickerNanos,
                                 long heaterRecipeLogicNanos,
                                 long continuousModuleTickHubNanos,
                                 int missingGeneratorSnapshots,
                                 int missingHeaterSnapshots,
                                 int nonWorkingGenerators,
                                 int invalidHeaterStates,
                                 long generatorOutput,
                                 long sinkInput,
                                 long sinkHeatOutput) {}

    private static final class MethodCount {

        long self;
        long inclusive;
    }

    private static MachineWorldProfilerProbe instance;

    private final StringBuilder report = new StringBuilder();
    private final List<Module> modules = new ArrayList<>(MODULE_COUNT);
    private final List<TickAggregate> aggregates = new ArrayList<>(WINDOW_TICKS * 2);
    private final int rawCapacity = WINDOW_TICKS * 2 * MODULE_COUNT;
    private final byte[] rawWindow = new byte[rawCapacity];
    private final int[] rawTick = new int[rawCapacity];
    private final long[] rawGameTime = new long[rawCapacity];
    private final int[] rawMachine = new int[rawCapacity];
    private final long[][] rawTicker = new long[TARGET_COUNT][rawCapacity];
    private final long[][] rawFramework = new long[TARGET_COUNT][rawCapacity];
    private final long[][] rawLogic = new long[TARGET_COUNT][rawCapacity];
    private final long[][] rawLogicAverage = new long[TARGET_COUNT][rawCapacity];
    private final long[][] rawLogicPeak = new long[TARGET_COUNT][rawCapacity];
    private final byte[][] rawLogicState = new byte[TARGET_COUNT][rawCapacity];
    private final long[] rawGeneratorOutput = new long[rawCapacity];
    private final long[] rawSinkInput = new long[rawCapacity];
    private final long[] rawSinkHeat = new long[rawCapacity];
    private final long[] sparkStartHeatByModule = new long[MODULE_COUNT];
    private final long[] sparkEndHeatByModule = new long[MODULE_COUNT];
    private final long[] cleanStartHeatByModule = new long[MODULE_COUNT];
    private final long[] cleanEndHeatByModule = new long[MODULE_COUNT];
    private final long[] jfrStartHeatByModule = new long[MODULE_COUNT];
    private final long[] jfrEndHeatByModule = new long[MODULE_COUNT];
    private int rawSize;
    private final Set<Path> sparkProfilesBefore = new java.util.HashSet<>();
    private final Map<String, long[]> gcBefore = new LinkedHashMap<>();
    private final Map<String, long[]> gcAfter = new LinkedHashMap<>();
    private volatile ClientState clientState = ClientState.WAIT_TITLE;
    private volatile ServerPhase serverPhase = ServerPhase.WARMUP;
    private volatile boolean serverComplete;
    private volatile @Nullable String serverFailure;
    private volatile @Nullable Path sparkProfile;
    private int waitTicks;
    private int countdown;
    private int phaseTick;
    private long sparkStartSinkHeat;
    private long sparkEndSinkHeat;
    private long cleanStartSinkHeat;
    private long cleanEndSinkHeat;
    private long jfrStartSinkHeat;
    private long jfrEndSinkHeat;
    private long sparkStartGameTime;
    private long sparkEndGameTime;
    private long cleanStartGameTime;
    private long cleanEndGameTime;
    private long jfrStartGameTime;
    private long jfrEndGameTime;
    private long sparkStartHeaterStarts;
    private long sparkStartHeaterHistoryStarts;
    private long sparkEndHeaterStarts;
    private long sparkEndHeaterHistoryStarts;
    private long cleanStartHeaterStarts;
    private long cleanStartHeaterHistoryStarts;
    private long jfrStartHeaterStarts;
    private long jfrStartHeaterHistoryStarts;
    private boolean warmupAssertionsPassed;
    private boolean transferAssertionsPassed;
    private boolean historyAssertionsPassed;
    private boolean windowAssertionsPassed;
    private boolean budgetAssertionsPassed;
    private boolean jfrIsolationAssertionsPassed;
    private long jfrServerThreadSamples;
    private boolean sparkStarted;
    private boolean sparkStopRequested;
    private boolean sparkSaveComplete;
    private int sparkStableTicks;
    private long sparkObservedSize = -1L;
    private long sparkObservedModifiedMillis = -1L;
    private int cooldownTicks;
    private @Nullable Recording recording;
    private @Nullable TickHandle batchStartHandle;
    private @Nullable TickHandle tickHandle;
    private @Nullable BlockPos galleryCenter;
    private long batchStartGameTime = Long.MIN_VALUE;
    private long batchStartNanos;

    private MachineWorldProfilerProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new MachineWorldProfilerProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Machine world profiler armed: building 100 independent working modules");
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (clientState) {
            case WAIT_TITLE -> waitForTitle(minecraft);
            case WAIT_WORLD -> waitForWorld(minecraft);
            case WAIT_SERVER -> waitForServer(minecraft);
            case WAIT_SPARK -> waitForSpark(minecraft);
            case WAIT_SCREENSHOT -> waitForScreenshot(minecraft);
            case ABORTING -> waitForAbort(minecraft);
            case DONE -> {}
        }
    }

    private void waitForTitle(Minecraft minecraft) {
        if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        minecraft.createWorldOpenFlows().createFreshLevel(
                LEVEL_ID,
                new LevelSettings(
                        LEVEL_ID,
                        GameType.CREATIVE,
                        LevelSettings.DifficultySettings.DEFAULT,
                        true,
                        WorldDataConfiguration.DEFAULT),
                new WorldOptions(20260711L, false, false),
                WorldPresets::createFlatWorldDimensions,
                minecraft.screen);
        waitTicks = 0;
        clientState = ClientState.WAIT_WORLD;
    }

    private void waitForWorld(Minecraft minecraft) {
        if (timedOut(minecraft, "waiting for integrated world")) {
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (minecraft.level == null || minecraft.player == null || server == null || minecraft.screen != null) {
            return;
        }
        minecraft.options.hideGui = true;
        server.execute(() -> buildScenario(server));
        waitTicks = 0;
        clientState = ClientState.WAIT_SERVER;
    }

    private void waitForServer(Minecraft minecraft) {
        if (serverFailure != null) {
            beginAbort(minecraft, serverFailure);
            return;
        }
        if (timedOut(minecraft, "waiting for profiler windows")) {
            return;
        }
        if (!serverComplete) {
            aimAt(minecraft, galleryCenter);
            return;
        }
        waitTicks = 0;
        clientState = ClientState.WAIT_SPARK;
    }

    private void waitForSpark(Minecraft minecraft) {
        Path profile = sparkProfile;
        if (profile != null && Files.isRegularFile(profile)) {
            report.append("Spark profile: ").append(profile).append(" -> PASS\n");
            captureScreenshot(minecraft);
            countdown = 30;
            clientState = ClientState.WAIT_SCREENSHOT;
            return;
        }
        if (timedOut(minecraft, "waiting for Spark profile")) {
            return;
        }
        aimAt(minecraft, galleryCenter);
    }

    private void waitForScreenshot(Minecraft minecraft) {
        aimAt(minecraft, galleryCenter);
        if (--countdown <= 0) {
            finish(minecraft);
        }
    }

    private void waitForAbort(Minecraft minecraft) {
        if (serverComplete || --countdown <= 0) {
            finish(minecraft);
        }
    }

    private void buildScenario(MinecraftServer server) {
        try {
            Files.createDirectories(REPORTS_DIR);
            snapshotSparkProfiles();
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            TickHub hub = TickHub.of(level);
            if (hub == null) {
                throw new IllegalStateException("TickHub is not attached to the integrated server level");
            }
            batchStartHandle = hub.register(TickKind.SYNC, 1, (gameTime, handle) -> {
                batchStartGameTime = gameTime;
                batchStartNanos = System.nanoTime();
            });
            BlockPos base = player.blockPosition().offset(-25, -1, -25);
            galleryCenter = base.offset((GRID_SIZE - 1) * MODULE_SPACING / 2, 0,
                    (GRID_SIZE - 1) * MODULE_SPACING / 2 + 1);
            PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int column = 0; column < GRID_SIZE; column++) {
                    int index = row * GRID_SIZE + column;
                    BlockPos generatorPos = base.offset(column * MODULE_SPACING, 0, row * MODULE_SPACING);
                    BlockPos pipe1 = generatorPos.south();
                    BlockPos pipe2 = generatorPos.south(2);
                    BlockPos sinkPos = generatorPos.south(3);
                    level.setBlock(generatorPos,
                            BuiltinTopoMachines.COMBUSTION_GENERATOR_T1.registeredBlock().getDefaultState(), 3);
                    level.setBlock(pipe1,
                            BuiltinTopoPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                    level.setBlock(pipe2,
                            BuiltinTopoPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                    level.setBlock(sinkPos,
                            BuiltinTopoMachines.RESISTIVE_HEATER_T3.registeredBlock().getDefaultState(), 3);
                    MachineBlockEntity generator = requireMachine(level, generatorPos);
                    MachineBlockEntity sink = requireMachine(level, sinkPos);
                    insertCoal(generator, 64);
                    runtime.setSideIntent(pipe1, Direction.NORTH, PipeSideIntent.EXTRACT);
                    PipePortStrategyConfig config = runtime.portConfig(pipe1, Direction.NORTH);
                    AggregationWindow window = runtime.portWindow(pipe1, Direction.NORTH);
                    if (window == null) {
                        throw new IllegalStateException("Missing aggregation window at " + pipe1);
                    }
                    runtime.setExtractConfig(pipe1, Direction.NORTH, config.withInterval(window.minInterval()));
                    generator.activatePerformanceMonitoring(MONITOR_TICKS);
                    sink.activatePerformanceMonitoring(MONITOR_TICKS);
                    modules.add(new Module(index, generatorPos, pipe1, sinkPos, generator, sink));
                }
            }
            validateNetworkIsolation(runtime);
            BlockPos center = galleryCenter;
            player.teleportTo(center.getX() + 0.5, center.getY() + 38.0, center.getZ() - 28.0);
            tickHandle = hub.register(TickKind.SYNC, 1, (gameTime, handle) -> onServerTick(server, gameTime));
            report.append("Scenario: 100 generators + 200 elite pipes + 100 resistive heaters -> PASS\n");
            report.append("Independent networks: 100, each with one extractor -> PASS\n");
            report.append("Continuous module timing: TickHub span from the pre-module probe to the collector " + "entry; covers 100 generators + 100 heaters + 200 pipe nodes without collector work -> ARMED\n");
        } catch (Throwable error) {
            failServer(server, "scenario construction failed", error);
        }
    }

    private void onServerTick(MinecraftServer server, long gameTime) {
        try {
            switch (serverPhase) {
                case WARMUP -> runWarmup(server);
                case SPARK_SETTLE -> settleSpark(server);
                case SPARK_PROFILE -> sampleWindow(server, gameTime, "spark");
                case SPARK_FLUSH -> waitForSparkFlush(server, gameTime);
                case CLEAN -> sampleWindow(server, gameTime, "clean");
                case JFR_PROFILE -> sampleJfrWindow(server, gameTime);
                case COMPLETE -> {}
            }
        } catch (Throwable error) {
            failServer(server, "server profiler state failed", error);
        }
    }

    private void runWarmup(MinecraftServer server) throws Exception {
        if (++phaseTick < WARMUP_TICKS) {
            return;
        }
        warmupAssertionsPassed = validateWarmup(server.overworld());
        if (!warmupAssertionsPassed) {
            throw new IllegalStateException("Warmup assertions failed; see report");
        }
        startSpark(server);
        phaseTick = 0;
        serverPhase = ServerPhase.SPARK_SETTLE;
    }

    private void settleSpark(MinecraftServer server) {
        if (++phaseTick < PROFILER_SETTLE_TICKS) {
            return;
        }
        sparkStartSinkHeat = totalSinkHeat();
        captureModuleHeat(sparkStartHeatByModule);
        sparkStartHeaterStarts = totalHeaterStarts(false);
        sparkStartHeaterHistoryStarts = totalHeaterStarts(true);
        sparkStartGameTime = server.overworld().getGameTime() + 1L;
        phaseTick = 0;
        serverPhase = ServerPhase.SPARK_PROFILE;
    }

    private void waitForSparkFlush(MinecraftServer server, long gameTime) throws IOException {
        if (++phaseTick > SPARK_FLUSH_TIMEOUT_TICKS) {
            throw new IllegalStateException("Spark profile did not finish saving within " + SPARK_FLUSH_TIMEOUT_TICKS + " server ticks");
        }
        Path profile = findNewSparkProfile();
        if (profile == null) {
            return;
        }
        long size = Files.size(profile);
        long modifiedMillis = Files.getLastModifiedTime(profile).toMillis();
        if (size != sparkObservedSize || modifiedMillis != sparkObservedModifiedMillis) {
            sparkObservedSize = size;
            sparkObservedModifiedMillis = modifiedMillis;
            sparkStableTicks = 1;
            sparkSaveComplete = false;
            cooldownTicks = 0;
            return;
        }
        if (++sparkStableTicks < STABLE_SPARK_FILE_TICKS) {
            return;
        }
        if (!sparkSaveComplete) {
            sparkSaveComplete = true;
            cooldownTicks = 0;
            return;
        }
        sparkProfile = profile.toAbsolutePath();
        if (++cooldownTicks < CLEAN_COOLDOWN_TICKS) {
            return;
        }
        cleanStartSinkHeat = totalSinkHeat();
        cleanStartGameTime = gameTime + 1L;
        captureModuleHeat(cleanStartHeatByModule);
        cleanStartHeaterStarts = totalHeaterStarts(false);
        cleanStartHeaterHistoryStarts = totalHeaterStarts(true);
        validateNetworkIsolation(PipeNetworkEngine.runtime(server.overworld()));
        captureGc(gcBefore);
        report.append("Spark save stabilized at ").append(sparkProfile)
                .append("; clean window begins after ").append(CLEAN_COOLDOWN_TICKS)
                .append(" cooldown ticks -> PASS\n");
        phaseTick = 0;
        serverPhase = ServerPhase.CLEAN;
    }

    private void sampleWindow(MinecraftServer server, long gameTime, String window) throws Exception {
        phaseTick++;
        collectTick(window, phaseTick, gameTime);
        if (phaseTick < WINDOW_TICKS) {
            return;
        }
        if (serverPhase == ServerPhase.SPARK_PROFILE) {
            sparkEndSinkHeat = totalSinkHeat();
            sparkEndGameTime = gameTime;
            captureModuleHeat(sparkEndHeatByModule);
            sparkEndHeaterStarts = totalHeaterStarts(false);
            sparkEndHeaterHistoryStarts = totalHeaterStarts(true);
            stopSpark(server);
            validateNetworkIsolation(PipeNetworkEngine.runtime(server.overworld()));
            phaseTick = 0;
            serverPhase = ServerPhase.SPARK_FLUSH;
            return;
        }
        finishCleanWindow(server, gameTime);
    }

    private void finishCleanWindow(MinecraftServer server, long gameTime) throws Exception {
        cleanEndSinkHeat = totalSinkHeat();
        cleanEndGameTime = gameTime;
        captureModuleHeat(cleanEndHeatByModule);
        validateNetworkIsolation(PipeNetworkEngine.runtime(server.overworld()));
        captureGc(gcAfter);
        int sparkExactModules = exactDeltas(sparkStartHeatByModule, sparkEndHeatByModule,
                EXPECTED_HEAT_DELTA_PER_MODULE);
        int cleanExactModules = exactDeltas(cleanStartHeatByModule, cleanEndHeatByModule,
                EXPECTED_HEAT_DELTA_PER_MODULE);
        long sparkHeatDelta = sparkEndSinkHeat - sparkStartSinkHeat;
        long cleanHeatDelta = cleanEndSinkHeat - cleanStartSinkHeat;
        transferAssertionsPassed = sparkExactModules == MODULE_COUNT && cleanExactModules == MODULE_COUNT && sparkHeatDelta == EXPECTED_HEAT_DELTA_PER_WINDOW && cleanHeatDelta == EXPECTED_HEAT_DELTA_PER_WINDOW;
        long sparkStarts = sparkEndHeaterStarts - sparkStartHeaterStarts;
        long sparkHistoryStarts = sparkEndHeaterHistoryStarts - sparkStartHeaterHistoryStarts;
        long cleanStarts = totalHeaterStarts(false) - cleanStartHeaterStarts;
        long cleanHistoryStarts = totalHeaterStarts(true) - cleanStartHeaterHistoryStarts;
        historyAssertionsPassed = sparkStarts == EXPECTED_HEATER_STARTS_PER_WINDOW && sparkHistoryStarts == sparkStarts && cleanStarts == EXPECTED_HEATER_STARTS_PER_WINDOW && cleanHistoryStarts == cleanStarts;
        report.append("Sustained transfer, Spark delta=").append(sparkEndSinkHeat - sparkStartSinkHeat)
                .append(", clean delta=").append(cleanEndSinkHeat - cleanStartSinkHeat)
                .append(", exact per-module deltas=").append(sparkExactModules).append('/').append(cleanExactModules)
                .append(" -> ").append(transferAssertionsPassed ? "PASS" : "FAIL").append('\n');
        report.append("Heater start churn, Spark starts/history=").append(sparkStarts).append('/')
                .append(sparkHistoryStarts).append(", clean starts/history=").append(cleanStarts).append('/')
                .append(cleanHistoryStarts).append(", expected each=").append(EXPECTED_HEATER_STARTS_PER_WINDOW)
                .append(" -> ").append(historyAssertionsPassed ? "PASS" : "FAIL").append('\n');
        report.append("Server gameTime windows: Spark=").append(sparkStartGameTime).append("..")
                .append(sparkEndGameTime).append(", clean=").append(cleanStartGameTime).append("..")
                .append(cleanEndGameTime).append('\n');
        int missingGenerators = aggregates.stream()
                .mapToInt(TickAggregate::missingGeneratorSnapshots)
                .sum();
        int missingHeaters = aggregates.stream()
                .mapToInt(TickAggregate::missingHeaterSnapshots)
                .sum();
        int nonWorkingGenerators = aggregates.stream()
                .mapToInt(TickAggregate::nonWorkingGenerators)
                .sum();
        int invalidHeaterStates = aggregates.stream()
                .mapToInt(TickAggregate::invalidHeaterStates)
                .sum();
        boolean exactSparkHeaterStates = allStateSamples(
                (byte) 0, HEATER, RecipeLogic.State.WORKING, EXPECTED_HEATER_WORKING_SAMPLES) && allStateSamples((byte) 0, HEATER, RecipeLogic.State.IDLE, EXPECTED_HEATER_IDLE_SAMPLES);
        boolean exactCleanHeaterStates = allStateSamples(
                (byte) 1, HEATER, RecipeLogic.State.WORKING, EXPECTED_HEATER_WORKING_SAMPLES) && allStateSamples((byte) 1, HEATER, RecipeLogic.State.IDLE, EXPECTED_HEATER_IDLE_SAMPLES);
        windowAssertionsPassed = aggregates.size() == WINDOW_TICKS * 2 && missingGenerators == 0 && missingHeaters == 0 && nonWorkingGenerators == 0 && invalidHeaterStates == 0 && exactSparkHeaterStates && exactCleanHeaterStates && sparkEndGameTime - sparkStartGameTime == WINDOW_TICKS - 1L && cleanEndGameTime - cleanStartGameTime == WINDOW_TICKS - 1L && sparkEndGameTime < cleanStartGameTime;
        report.append("Measured windows: ticks=").append(aggregates.size())
                .append(", missing generator/heater snapshots=").append(missingGenerators).append('/')
                .append(missingHeaters).append(", non-WORKING generators=").append(nonWorkingGenerators)
                .append(", invalid heater states=").append(invalidHeaterStates)
                .append(", exact heater WORKING/IDLE samples=")
                .append(exactSparkHeaterStates).append('/').append(exactCleanHeaterStates)
                .append(" -> ").append(windowAssertionsPassed ? "PASS" : "FAIL").append('\n');
        long worstGeneratorTickerP95 = worstPerMachinePercentile((byte) 1, rawTicker[GENERATOR], 0.95);
        long worstGeneratorLogicP95 = worstPerMachinePercentile((byte) 1, rawLogic[GENERATOR], 0.95);
        long worstHeaterTickerP95 = worstPerMachinePercentile((byte) 1, rawTicker[HEATER], 0.95);
        long worstHeaterLogicP95 = worstPerMachinePercentile((byte) 1, rawLogic[HEATER], 0.95);
        budgetAssertionsPassed = worstGeneratorTickerP95 <= TICKER_P95_BUDGET_NANOS && worstGeneratorLogicP95 <= RECIPE_LOGIC_P95_BUDGET_NANOS && worstHeaterTickerP95 <= TICKER_P95_BUDGET_NANOS && worstHeaterLogicP95 <= RECIPE_LOGIC_P95_BUDGET_NANOS;
        report.append("Clean-window budgets: steady generator ticker/RecipeLogic p95=")
                .append(worstGeneratorTickerP95).append('/').append(worstGeneratorLogicP95)
                .append(" ns, churn heater ticker/RecipeLogic p95=")
                .append(worstHeaterTickerP95).append('/').append(worstHeaterLogicP95)
                .append(" ns; limits=").append(TICKER_P95_BUDGET_NANOS).append('/')
                .append(RECIPE_LOGIC_P95_BUDGET_NANOS)
                .append(" ns -> ").append(budgetAssertionsPassed ? "PASS" : "FAIL").append('\n');
        startJfr();
        jfrStartSinkHeat = totalSinkHeat();
        captureModuleHeat(jfrStartHeatByModule);
        jfrStartHeaterStarts = totalHeaterStarts(false);
        jfrStartHeaterHistoryStarts = totalHeaterStarts(true);
        jfrStartGameTime = gameTime + 1L;
        phaseTick = 0;
        serverPhase = ServerPhase.JFR_PROFILE;
    }

    private void sampleJfrWindow(MinecraftServer server, long gameTime) throws IOException {
        if (++phaseTick < WINDOW_TICKS) {
            return;
        }
        jfrEndSinkHeat = totalSinkHeat();
        jfrEndGameTime = gameTime;
        captureModuleHeat(jfrEndHeatByModule);
        stopJfr();
        validateNetworkIsolation(PipeNetworkEngine.runtime(server.overworld()));
        int jfrExactModules = exactDeltas(jfrStartHeatByModule, jfrEndHeatByModule,
                EXPECTED_HEAT_DELTA_PER_MODULE);
        boolean jfrTransferPassed = jfrEndSinkHeat - jfrStartSinkHeat == EXPECTED_HEAT_DELTA_PER_WINDOW && jfrExactModules == MODULE_COUNT;
        long jfrStarts = totalHeaterStarts(false) - jfrStartHeaterStarts;
        long jfrHistoryStarts = totalHeaterStarts(true) - jfrStartHeaterHistoryStarts;
        boolean jfrHistoryPassed = jfrStarts == EXPECTED_HEATER_STARTS_PER_WINDOW && jfrHistoryStarts == jfrStarts;
        transferAssertionsPassed &= jfrTransferPassed;
        historyAssertionsPassed &= jfrHistoryPassed;
        report.append("JFR-only window: gameTime=").append(jfrStartGameTime).append("..")
                .append(jfrEndGameTime).append(", transfer delta=").append(jfrEndSinkHeat - jfrStartSinkHeat)
                .append(", exact per-module deltas=").append(jfrExactModules)
                .append(", heater starts/history=").append(jfrStarts).append('/').append(jfrHistoryStarts)
                .append(" -> ").append(jfrTransferPassed && jfrHistoryPassed ? "PASS" : "FAIL").append('\n');
        exportArtifacts();
        serverPhase = ServerPhase.COMPLETE;
        unsubscribeTickHook();
        serverComplete = true;
    }

    private void collectTick(String window, int tickIndex, long gameTime) {
        if (batchStartGameTime != gameTime) {
            throw new IllegalStateException("Scheduled batch start probe did not run for gameTime " + gameTime);
        }
        long continuousModuleTickHubNanos = Math.max(0L, System.nanoTime() - batchStartNanos);
        long generatorTickerNanos = 0L;
        long generatorLogicNanos = 0L;
        long heaterTickerNanos = 0L;
        long heaterLogicNanos = 0L;
        long generatorOutput = 0L;
        long sinkInput = 0L;
        long sinkHeat = 0L;
        int missingGenerators = 0;
        int missingHeaters = 0;
        int nonWorkingGenerators = 0;
        int invalidHeaterStates = 0;
        for (Module module : modules) {
            MachineBlockEntity generator = module.generator();
            MachineBlockEntity heater = module.sink();
            RecipeLogic generatorLogic = generator.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            RecipeLogic heaterLogic = heater.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            ScalarResourcePort source = generator.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
            ScalarResourcePort target = heater.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
            ScalarResourcePort heat = heater.machineComponents().require(ScalarResourcePort.HEAT_OUTPUT_1);
            long sourceAmount = source.storedAmount();
            long targetAmount = target.storedAmount();
            long heatAmount = heat.storedAmount();
            generatorOutput += sourceAmount;
            sinkInput += targetAmount;
            sinkHeat += heatAmount;
            if (generatorLogic.state() != RecipeLogic.State.WORKING) {
                nonWorkingGenerators++;
            }
            RecipeLogic.State heaterState = heaterLogic.state();
            if (heaterState != RecipeLogic.State.IDLE && heaterState != RecipeLogic.State.WORKING) {
                invalidHeaterStates++;
            }
            int rawIndex = rawSize++;
            rawWindow[rawIndex] = (byte) ("spark".equals(window) ? 0 : 1);
            rawTick[rawIndex] = tickIndex;
            rawGameTime[rawIndex] = gameTime;
            rawMachine[rawIndex] = module.index();
            if (!capturePerformance(generator, generatorLogic, gameTime, rawIndex, GENERATOR)) {
                missingGenerators++;
            }
            if (!capturePerformance(heater, heaterLogic, gameTime, rawIndex, HEATER)) {
                missingHeaters++;
            }
            generatorTickerNanos += rawTicker[GENERATOR][rawIndex];
            generatorLogicNanos += rawLogic[GENERATOR][rawIndex];
            heaterTickerNanos += rawTicker[HEATER][rawIndex];
            heaterLogicNanos += rawLogic[HEATER][rawIndex];
            rawGeneratorOutput[rawIndex] = sourceAmount;
            rawSinkInput[rawIndex] = targetAmount;
            rawSinkHeat[rawIndex] = heatAmount;
        }
        aggregates.add(new TickAggregate(
                window,
                tickIndex,
                generatorTickerNanos,
                generatorLogicNanos,
                heaterTickerNanos,
                heaterLogicNanos,
                continuousModuleTickHubNanos,
                missingGenerators,
                missingHeaters,
                nonWorkingGenerators,
                invalidHeaterStates,
                generatorOutput,
                sinkInput,
                sinkHeat));
    }

    private boolean capturePerformance(
                                       MachineBlockEntity machine,
                                       RecipeLogic logic,
                                       long gameTime,
                                       int rawIndex,
                                       int target) {
        rawLogicState[target][rawIndex] = (byte) logic.state().ordinal();
        if (!machine.publishRecentPerformanceSnapshot(machine.performanceSampleMaxAgeTicks())) {
            return false;
        }
        MachinePerformanceSnapshot snapshot = machine.lastPerformanceSnapshot();
        if (snapshot.isEmpty() || snapshot.gameTime() != gameTime) {
            return false;
        }
        rawTicker[target][rawIndex] = snapshot.totalNanos();
        rawFramework[target][rawIndex] = snapshot.selfNanos();
        for (MachinePerformanceSnapshot.ComponentSample component : snapshot.components()) {
            if (RECIPE_LOGIC_ID.equals(component.id())) {
                rawLogic[target][rawIndex] = component.nanos();
                rawLogicAverage[target][rawIndex] = component.avgNanos();
                rawLogicPeak[target][rawIndex] = component.peakNanos();
                return true;
            }
        }
        return false;
    }

    private boolean validateWarmup(ServerLevel level) {
        int generators = 0;
        int heaters = 0;
        int workingGenerators = 0;
        int validHeaterStates = 0;
        int heatersWithLearnedHistory = 0;
        Set<PipeNetwork<?>> networks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        int validExtractors = 0;
        PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
        for (Module module : modules) {
            if (level.getBlockEntity(module.generatorPos()) instanceof MachineBlockEntity) {
                generators++;
            }
            if (level.getBlockEntity(module.sinkPos()) instanceof MachineBlockEntity) {
                heaters++;
            }
            RecipeLogic generatorLogic = module.generator().machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            if (generatorLogic.state() == RecipeLogic.State.WORKING) {
                workingGenerators++;
            }
            RecipeLogic heaterLogic = module.sink().machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            RecipeLogic.State heaterState = heaterLogic.state();
            if (heaterState == RecipeLogic.State.WORKING || heaterState == RecipeLogic.State.IDLE) {
                validHeaterStates++;
            }
            if (heaterLogic.successfulStartCount() > heaterLogic.rememberedStartCount() && heaterLogic.rememberedStartCount() > 0L) {
                heatersWithLearnedHistory++;
            }
            PipeNetwork<?> network = runtime.networkAt(module.sourcePipePos());
            if (network != null) {
                networks.add(network);
                if (network.extractorCount() == 1) {
                    validExtractors++;
                }
            }
        }
        long successfulHeaterStarts = totalHeaterStarts(false);
        long rememberedHeaterStarts = totalHeaterStarts(true);
        boolean passed = generators == MODULE_COUNT && heaters == MODULE_COUNT && workingGenerators == MODULE_COUNT && validHeaterStates == MODULE_COUNT && heatersWithLearnedHistory == MODULE_COUNT && networks.size() == MODULE_COUNT && validExtractors == MODULE_COUNT && successfulHeaterStarts > rememberedHeaterStarts && rememberedHeaterStarts >= MODULE_COUNT && totalSinkHeat() > 0L;
        report.append("Warmup: generators/heaters=").append(generators).append('/').append(heaters)
                .append(", WORKING generators=").append(workingGenerators)
                .append(", valid heater states=").append(validHeaterStates)
                .append(", heaters with learned history=").append(heatersWithLearnedHistory)
                .append(", heater starts/history=").append(successfulHeaterStarts).append('/')
                .append(rememberedHeaterStarts).append(", networks=").append(networks.size())
                .append(", one-extractor=").append(validExtractors)
                .append(", sinkHeat=").append(totalSinkHeat()).append(" -> ")
                .append(passed ? "PASS" : "FAIL").append('\n');
        return passed;
    }

    private void validateNetworkIsolation(PipeLevelRuntime runtime) {
        Set<PipeNetwork<?>> networks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Module module : modules) {
            PipeNetwork<?> network = runtime.networkAt(module.sourcePipePos());
            PipeNetwork<?> secondPipeNetwork = runtime.networkAt(module.sourcePipePos().south());
            if (network == null || network != secondPipeNetwork || network.nodeCount() != 2 || network.extractorCount() != 1 || network.destinationCount() != 1) {
                throw new IllegalStateException("Invalid network at " + module.sourcePipePos());
            }
            networks.add(network);
        }
        if (networks.size() != MODULE_COUNT) {
            throw new IllegalStateException("Expected 100 independent networks, found " + networks.size());
        }
    }

    private void startSpark(MinecraftServer server) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "spark profiler start --interval 1 --thread Server thread --force-java-sampler");
        sparkStarted = true;
        report.append("Spark-only window: forced Java sampler -> STARTED\n");
    }

    private void startJfr() throws Exception {
        Configuration configuration = Configuration.getConfiguration("profile");
        Recording nextRecording = new Recording(configuration);
        nextRecording.setName("topo-machine-world-100");
        nextRecording.setToDisk(true);
        nextRecording.setDestination(JFR_FILE);
        nextRecording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
        nextRecording.enable("jdk.ObjectAllocationSample");
        nextRecording.enable("jdk.GarbageCollection");
        nextRecording.enable("jdk.SafepointBegin");
        nextRecording.enable("jdk.ThreadDump");
        nextRecording.start();
        recording = nextRecording;
        report.append("JFR-only window: 1ms ExecutionSample -> STARTED after clean metrics finalized\n");
    }

    private void stopSpark(MinecraftServer server) {
        if (sparkStarted && !sparkStopRequested) {
            try {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "spark profiler stop --save-to-file");
                sparkStopRequested = true;
            } catch (Throwable error) {
                report.append("Spark stop failed: ").append(error).append(" -> FAIL\n");
                throw error;
            }
        }
        report.append("Spark-only window -> STOP REQUESTED; waiting for file stabilization\n");
    }

    private void stopJfr() {
        Recording active = recording;
        recording = null;
        if (active == null) {
            return;
        }
        try {
            if (active.getState() == jdk.jfr.RecordingState.RUNNING) {
                active.stop();
            }
        } finally {
            active.close();
        }
    }

    private void exportArtifacts() throws IOException {
        Files.writeString(RAW_FILE, buildRawCsv(), StandardCharsets.UTF_8);
        String summary = buildSummaryCsv();
        Files.writeString(SUMMARY_FILE, summary, StandardCharsets.UTF_8);
        JfrSummary jfr = parseJfr();
        jfrServerThreadSamples = jfr.samples();
        jfrIsolationAssertionsPassed = jfrServerThreadSamples > 0L && sparkStopRequested && sparkSaveComplete && rawSize == rawCapacity && jfrEndGameTime - jfrStartGameTime == WINDOW_TICKS - 1L && cleanEndGameTime < jfrStartGameTime;
        report.append("JFR-only diagnostics: Server thread execution samples=").append(jfrServerThreadSamples)
                .append(" (~").append(jfrServerThreadSamples).append(" ms sampled)")
                .append(", allocation samples/estimated bytes/server bytes=")
                .append(jfr.allocationEvents()).append('/').append(jfr.allocationBytes()).append('/')
                .append(jfr.serverThreadAllocationBytes())
                .append(", GC events=").append(jfr.gcEvents())
                .append("/").append(nanosToMicros(jfr.gcDurationNanos())).append(" us")
                .append(", safepoint begins=").append(jfr.safepointEvents())
                .append("/").append(nanosToMicros(jfr.safepointDurationNanos())).append(" us")
                .append(", thread dumps=").append(jfr.threadDumpEvents())
                .append(" -> ").append(jfrIsolationAssertionsPassed ? "PASS" : "FAIL").append('\n');
        Files.writeString(HOT_METHODS_FILE, jfr.hotMethods(), StandardCharsets.UTF_8);
        Files.writeString(STACKS_FILE, jfr.stacks(), StandardCharsets.UTF_8);
        Files.writeString(MARKDOWN_FILE, buildMarkdown(jfr), StandardCharsets.UTF_8);
        report.append("Artifacts exported under ").append(REPORTS_DIR.toAbsolutePath()).append(" -> PASS\n");
    }

    private String buildRawCsv() {
        StringBuilder output = new StringBuilder(24 * 1024 * 1024);
        output.append("window,tickIndex,gameTime,machineIndex,x,y,z,generatorProfiledTickersNanos,")
                .append("generatorFrameworkNanos,generatorRecipeLogicNanos,generatorRecipeLogicAvgNanos,")
                .append("generatorRecipeLogicPeakNanos,generatorLogicState,heaterProfiledTickersNanos,")
                .append("heaterFrameworkNanos,heaterRecipeLogicNanos,heaterRecipeLogicAvgNanos,")
                .append("heaterRecipeLogicPeakNanos,heaterLogicState,")
                .append("generatorOutputAmount,sinkInputAmount,sinkHeatOutputAmount\n");
        RecipeLogic.State[] states = RecipeLogic.State.values();
        for (int index = 0; index < rawSize; index++) {
            Module module = modules.get(rawMachine[index]);
            BlockPos pos = module.generatorPos();
            output.append(rawWindow[index] == 0 ? "spark" : "clean").append(',').append(rawTick[index])
                    .append(',').append(rawGameTime[index]).append(',').append(rawMachine[index]).append(',')
                    .append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()).append(',')
                    .append(rawTicker[GENERATOR][index]).append(',').append(rawFramework[GENERATOR][index])
                    .append(',').append(rawLogic[GENERATOR][index]).append(',')
                    .append(rawLogicAverage[GENERATOR][index]).append(',')
                    .append(rawLogicPeak[GENERATOR][index]).append(',')
                    .append(states[rawLogicState[GENERATOR][index]].name()).append(',')
                    .append(rawTicker[HEATER][index]).append(',').append(rawFramework[HEATER][index]).append(',')
                    .append(rawLogic[HEATER][index]).append(',').append(rawLogicAverage[HEATER][index]).append(',')
                    .append(rawLogicPeak[HEATER][index]).append(',')
                    .append(states[rawLogicState[HEATER][index]].name()).append(',')
                    .append(rawGeneratorOutput[index])
                    .append(',').append(rawSinkInput[index]).append(',').append(rawSinkHeat[index]).append('\n');
        }
        return output.toString();
    }

    private String buildSummaryCsv() {
        StringBuilder output = new StringBuilder();
        output.append("window,metric,count,missing,min,p50,p95,p99,max,transferDelta,invalidStateTicks\n");
        appendWindowSummary(output, "spark", sparkEndSinkHeat - sparkStartSinkHeat);
        appendWindowSummary(output, "clean", cleanEndSinkHeat - cleanStartSinkHeat);
        output.append("gc,collector,collections,timeMillis,,,,,,,\n");
        for (Map.Entry<String, long[]> entry : gcAfter.entrySet()) {
            long[] before = gcBefore.getOrDefault(entry.getKey(), new long[] { 0L, 0L });
            output.append("gc,").append(csv(entry.getKey())).append(',')
                    .append(delta(entry.getValue()[0], before[0])).append(',')
                    .append(delta(entry.getValue()[1], before[1])).append(",,,,,,,\n");
        }
        return output.toString();
    }

    private void appendWindowSummary(StringBuilder output, String window, long transferDelta) {
        List<TickAggregate> ticks = aggregates.stream().filter(row -> row.window().equals(window)).toList();
        List<Long> generatorTickerPerTick = ticks.stream().map(TickAggregate::generatorTickerNanos).toList();
        List<Long> generatorLogicPerTick = ticks.stream().map(TickAggregate::generatorRecipeLogicNanos).toList();
        List<Long> heaterTickerPerTick = ticks.stream().map(TickAggregate::heaterTickerNanos).toList();
        List<Long> heaterLogicPerTick = ticks.stream().map(TickAggregate::heaterRecipeLogicNanos).toList();
        List<Long> continuousModulePerTick = ticks.stream().map(TickAggregate::continuousModuleTickHubNanos).toList();
        List<Long> pooledPerGeneratorTicker = new ArrayList<>(WINDOW_TICKS * MODULE_COUNT);
        List<Long> pooledPerGeneratorLogic = new ArrayList<>(WINDOW_TICKS * MODULE_COUNT);
        List<Long> pooledPerHeaterTicker = new ArrayList<>(WINDOW_TICKS * MODULE_COUNT);
        List<Long> pooledPerHeaterLogic = new ArrayList<>(WINDOW_TICKS * MODULE_COUNT);
        byte windowId = (byte) ("spark".equals(window) ? 0 : 1);
        for (int index = 0; index < rawSize; index++) {
            if (rawWindow[index] == windowId) {
                pooledPerGeneratorTicker.add(rawTicker[GENERATOR][index]);
                pooledPerGeneratorLogic.add(rawLogic[GENERATOR][index]);
                pooledPerHeaterTicker.add(rawTicker[HEATER][index]);
                pooledPerHeaterLogic.add(rawLogic[HEATER][index]);
            }
        }
        int missingGenerators = ticks.stream().mapToInt(TickAggregate::missingGeneratorSnapshots).sum();
        int missingHeaters = ticks.stream().mapToInt(TickAggregate::missingHeaterSnapshots).sum();
        long invalidGenerators = ticks.stream().mapToLong(TickAggregate::nonWorkingGenerators).sum();
        long invalidHeaters = ticks.stream().mapToLong(TickAggregate::invalidHeaterStates).sum();
        appendMetric(output, window, "pooled_per_generator_steady_profiled_tickers_nanos",
                pooledPerGeneratorTicker, missingGenerators, transferDelta, invalidGenerators);
        appendMetric(output, window, "pooled_per_generator_steady_recipe_logic_nanos",
                pooledPerGeneratorLogic, missingGenerators, transferDelta, invalidGenerators);
        appendPerMachineP95(output, window, "per_generator_steady_profiled_tickers_p95_nanos", windowId,
                rawTicker[GENERATOR], missingGenerators, transferDelta, invalidGenerators);
        appendPerMachineP95(output, window, "per_generator_steady_recipe_logic_p95_nanos", windowId,
                rawLogic[GENERATOR], missingGenerators, transferDelta, invalidGenerators);
        appendMetric(output, window, "sum_100_generator_steady_profiled_tickers_nanos",
                generatorTickerPerTick, missingGenerators, transferDelta, invalidGenerators);
        appendMetric(output, window, "sum_100_generator_steady_recipe_logic_nanos",
                generatorLogicPerTick, missingGenerators, transferDelta, invalidGenerators);
        appendMetric(output, window, "pooled_per_heater_churn_profiled_tickers_nanos",
                pooledPerHeaterTicker, missingHeaters, transferDelta, invalidHeaters);
        appendMetric(output, window, "pooled_per_heater_churn_recipe_logic_nanos",
                pooledPerHeaterLogic, missingHeaters, transferDelta, invalidHeaters);
        appendPerMachineP95(output, window, "per_heater_churn_profiled_tickers_p95_nanos", windowId,
                rawTicker[HEATER], missingHeaters, transferDelta, invalidHeaters);
        appendPerMachineP95(output, window, "per_heater_churn_recipe_logic_p95_nanos", windowId,
                rawLogic[HEATER], missingHeaters, transferDelta, invalidHeaters);
        appendMetric(output, window, "sum_100_heater_churn_profiled_tickers_nanos",
                heaterTickerPerTick, missingHeaters, transferDelta, invalidHeaters);
        appendMetric(output, window, "sum_100_heater_churn_recipe_logic_nanos",
                heaterLogicPerTick, missingHeaters, transferDelta, invalidHeaters);
        appendMetric(output, window, "per_heater_churn_idle_completion_samples",
                stateCounts(windowId, HEATER, RecipeLogic.State.IDLE), missingHeaters, transferDelta, invalidHeaters);
        appendMetric(output, window, "continuous_100_module_tickhub_nanos", continuousModulePerTick,
                missingGenerators + missingHeaters, transferDelta, invalidGenerators + invalidHeaters);
    }

    private void appendPerMachineP95(
                                     StringBuilder output, String window, String metric, byte windowId, long[] values, int missing,
                                     long transferDelta, long invalidStates) {
        List<Long> p95ByMachine = new ArrayList<>(MODULE_COUNT);
        for (int machineIndex = 0; machineIndex < MODULE_COUNT; machineIndex++) {
            p95ByMachine.add(perMachinePercentile(windowId, machineIndex, values, 0.95));
        }
        appendMetric(output, window, metric, p95ByMachine, missing, transferDelta, invalidStates);
    }

    private static void appendMetric(
                                     StringBuilder output, String window, String metric, List<Long> values, int missing,
                                     long transferDelta, long invalidStates) {
        List<Long> sorted = values.stream().sorted().toList();
        output.append(window).append(',').append(metric).append(',').append(sorted.size()).append(',')
                .append(missing).append(',').append(sorted.isEmpty() ? 0L : sorted.getFirst()).append(',')
                .append(percentile(sorted, 0.50)).append(',')
                .append(percentile(sorted, 0.95)).append(',').append(percentile(sorted, 0.99)).append(',')
                .append(sorted.isEmpty() ? 0L : sorted.getLast()).append(',').append(transferDelta).append(',')
                .append(invalidStates).append('\n');
    }

    private record JfrSummary(
                              long samples,
                              long allocationEvents,
                              long allocationBytes,
                              long serverThreadAllocationBytes,
                              long gcEvents,
                              long gcDurationNanos,
                              long safepointEvents,
                              long safepointDurationNanos,
                              long threadDumpEvents,
                              String hotMethods,
                              String stacks) {}

    private JfrSummary parseJfr() throws IOException {
        Map<String, MethodCount> methods = new HashMap<>();
        Map<String, Long> stacks = new TreeMap<>();
        long samples = 0L;
        long allocationEvents = 0L;
        long allocationBytes = 0L;
        long serverThreadAllocationBytes = 0L;
        long gcEvents = 0L;
        long gcDurationNanos = 0L;
        long safepointEvents = 0L;
        long safepointDurationNanos = 0L;
        long threadDumpEvents = 0L;
        try (RecordingFile recordingFile = new RecordingFile(JFR_FILE)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                String eventName = event.getEventType().getName();
                if ("jdk.ObjectAllocationSample".equals(eventName)) {
                    allocationEvents++;
                    long weight = event.hasField("weight") ? event.getLong("weight") : 0L;
                    allocationBytes += weight;
                    RecordedThread allocationThread = event.getThread("eventThread");
                    if (allocationThread != null && "Server thread".equals(allocationThread.getJavaName())) {
                        serverThreadAllocationBytes += weight;
                    }
                    continue;
                }
                if ("jdk.GarbageCollection".equals(eventName)) {
                    gcEvents++;
                    gcDurationNanos += event.getDuration().toNanos();
                    continue;
                }
                if ("jdk.SafepointBegin".equals(eventName)) {
                    safepointEvents++;
                    safepointDurationNanos += event.getDuration().toNanos();
                    continue;
                }
                if ("jdk.ThreadDump".equals(eventName)) {
                    threadDumpEvents++;
                    continue;
                }
                if (!"jdk.ExecutionSample".equals(eventName)) {
                    continue;
                }
                RecordedThread thread = event.getThread("sampledThread");
                if (thread == null || !"Server thread".equals(thread.getJavaName())) {
                    continue;
                }
                RecordedStackTrace trace = event.getStackTrace();
                if (trace == null || trace.getFrames().isEmpty()) {
                    continue;
                }
                samples++;
                List<String> frames = new ArrayList<>();
                String selfKey = null;
                for (RecordedFrame frame : trace.getFrames()) {
                    RecordedMethod method = frame.getMethod();
                    String key = method.getType().getName() + "," + method.getName() + "," + frame.getLineNumber();
                    methods.computeIfAbsent(key, ignored -> new MethodCount()).inclusive++;
                    if (selfKey == null) {
                        selfKey = key;
                    }
                    frames.add(method.getType().getName() + "." + method.getName());
                }
                methods.get(selfKey).self++;
                java.util.Collections.reverse(frames);
                stacks.merge(String.join(";", frames), 1L, Long::sum);
            }
        }
        final long totalSamples = samples;
        StringBuilder hot = new StringBuilder(
                "class,method,line,selfSamples,inclusiveSamples,approxSelfMillis,approxInclusiveMillis,selfPercent,inclusivePercent\n");
        methods.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, MethodCount>>comparingLong(entry -> entry.getValue().self)
                        .reversed())
                .forEach(entry -> {
                    String[] key = entry.getKey().split(",", 3);
                    MethodCount count = entry.getValue();
                    hot.append(csv(key[0])).append(',').append(csv(key[1])).append(',').append(key[2]).append(',')
                            .append(count.self).append(',').append(count.inclusive).append(',')
                            .append(count.self).append(',').append(count.inclusive).append(',')
                            .append(percent(count.self, totalSamples)).append(',')
                            .append(percent(count.inclusive, totalSamples)).append('\n');
                });
        StringBuilder collapsed = new StringBuilder("stack,samples\n");
        stacks.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> collapsed.append(csv(entry.getKey())).append(',').append(entry.getValue()).append('\n'));
        return new JfrSummary(
                samples,
                allocationEvents,
                allocationBytes,
                serverThreadAllocationBytes,
                gcEvents,
                gcDurationNanos,
                safepointEvents,
                safepointDurationNanos,
                threadDumpEvents,
                hot.toString(),
                collapsed.toString());
    }

    private String buildMarkdown(JfrSummary jfr) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 100-module integrated-world profile\n\n")
                .append("Generated: ").append(Instant.now()).append("\n\n")
                .append("- Scenario: 100 independent combustion-generator -> two elite-energy-pipe -> resistive-heater modules\n")
                .append("- Warmup: ").append(WARMUP_TICKS).append(" server ticks\n")
                .append("- Spark-only window: ").append(WINDOW_TICKS).append(" ticks, 1 ms forced Java sampler\n")
                .append("- Clean window: ").append(WINDOW_TICKS).append(" ticks after Spark file stabilization + ")
                .append(CLEAN_COOLDOWN_TICKS).append(" cooldown ticks\n")
                .append("- JFR-only window: ").append(WINDOW_TICKS).append(" ticks, 1 ms ExecutionSample\n")
                .append("- JFR Server-thread samples / GC / safepoints / thread dumps: ")
                .append(jfr.samples()).append(" / ").append(jfr.gcEvents()).append(" / ")
                .append(jfr.safepointEvents()).append(" / ").append(jfr.threadDumpEvents()).append("\n")
                .append("- JFR allocation samples / estimated bytes / Server-thread bytes: ")
                .append(jfr.allocationEvents()).append(" / ").append(jfr.allocationBytes()).append(" / ")
                .append(jfr.serverThreadAllocationBytes()).append("\n")
                .append("- JFR GC / time-to-safepoint duration: ")
                .append(nanosToMicros(jfr.gcDurationNanos())).append(" / ")
                .append(nanosToMicros(jfr.safepointDurationNanos())).append(" us\n")
                .append("- Spark / clean / JFR transfer delta: ")
                .append(sparkEndSinkHeat - sparkStartSinkHeat).append(" / ")
                .append(cleanEndSinkHeat - cleanStartSinkHeat).append(" / ")
                .append(jfrEndSinkHeat - jfrStartSinkHeat).append(" heat\n\n")
                .append("Generator steady and heater start-churn timings each cover profiled ticker bodies plus ")
                .append("afterTickerTick framework work. Heater state samples intentionally include one IDLE completion ")
                .append("tick per six-tick cycle.\n\n")
                .append("continuous_100_module_tickhub_nanos spans the pre-module TickHub probe to collector entry, ")
                .append("covering 100 generators, 100 heaters, and 200 pipe nodes without snapshot collection. It ")
                .append("does not represent the complete server tick.\n\n")
                .append("Raw rows use preallocated primitive arrays and CSV formatting occurs after all profiler ")
                .append("windows. See sibling CSV files for per-machine samples, internal sums, continuous module ")
                .append("timing, hot methods, and collapsed stacks.\n");
        return markdown.toString();
    }

    private void failServer(MinecraftServer server, String context, Throwable error) {
        LOGGER.error("Machine world profiler: {}", context, error);
        serverFailure = context + ": " + error;
        report.append("FAILED ").append(context).append(": ").append(error).append('\n');
        try {
            stopJfr();
        } catch (Throwable cleanupError) {
            LOGGER.error("JFR cleanup failed", cleanupError);
        }
        if (sparkStarted && !sparkStopRequested) {
            try {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "spark profiler stop --save-to-file");
                sparkStopRequested = true;
            } catch (Throwable cleanupError) {
                LOGGER.error("Spark cleanup failed", cleanupError);
            }
        }
        unsubscribeTickHook();
        serverComplete = true;
    }

    private void beginAbort(Minecraft minecraft, String reason) {
        if (clientState == ClientState.ABORTING || clientState == ClientState.DONE) {
            return;
        }
        report.append("FAILED client abort: ").append(reason).append('\n');
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> failServer(server, "client requested cleanup", new IllegalStateException(reason)));
        }
        countdown = 40;
        clientState = ClientState.ABORTING;
    }

    private boolean timedOut(Minecraft minecraft, String stage) {
        if (++waitTicks <= WAIT_TIMEOUT_TICKS) {
            return false;
        }
        beginAbort(minecraft, "timeout while " + stage);
        return true;
    }

    private void finish(Minecraft minecraft) {
        if (clientState == ClientState.DONE) {
            return;
        }
        clientState = ClientState.DONE;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null && (!serverComplete || recording != null || (sparkStarted && !sparkStopRequested))) {
            server.execute(() -> failServer(server, "final cleanup", new IllegalStateException("unfinished profiler")));
        }
        boolean passed = serverFailure == null && warmupAssertionsPassed && transferAssertionsPassed && historyAssertionsPassed && windowAssertionsPassed && budgetAssertionsPassed && jfrIsolationAssertionsPassed && jfrServerThreadSamples > 0L && sparkSaveComplete && sparkProfile != null && Files.isRegularFile(JFR_FILE);
        report.append("RESULT ").append(passed ? "PASS" : "FAIL").append('\n');
        minecraft.options.hideGui = false;
        try {
            Files.writeString(REPORT_FILE, report, StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException error) {
            LOGGER.error("Machine world profiler failed to flush run report", error);
        }
        LOGGER.warn("Machine world profiler finished; result={}", passed ? "PASS" : "FAIL");
        minecraft.stop();
    }

    private void captureScreenshot(Minecraft minecraft) {
        aimAt(minecraft, galleryCenter);
        try {
            Files.deleteIfExists(Path.of("screenshots", SCREENSHOT_NAME));
            Screenshot.grab(minecraft.gameDirectory, SCREENSHOT_NAME, minecraft.getMainRenderTarget(), 1,
                    component -> {});
            report.append("Screenshot: screenshots/").append(SCREENSHOT_NAME).append(" -> PASS\n");
        } catch (Throwable error) {
            serverFailure = "screenshot failed: " + error;
            report.append("FAILED screenshot: ").append(error).append('\n');
        }
    }

    private static void aimAt(Minecraft minecraft, @Nullable BlockPos pos) {
        if (minecraft.player == null || pos == null) {
            return;
        }
        double dx = pos.getX() + 0.5 - minecraft.player.getX();
        double dy = pos.getY() + 0.5 - (minecraft.player.getY() + minecraft.player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - minecraft.player.getZ();
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        minecraft.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private void snapshotSparkProfiles() throws IOException {
        Path directory = Path.of("config/spark");
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".sparkprofile"))
                    .map(Path::toAbsolutePath)
                    .forEach(sparkProfilesBefore::add);
        }
    }

    private @Nullable Path findNewSparkProfile() {
        Path directory = Path.of("config/spark");
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".sparkprofile"))
                    .filter(path -> !sparkProfilesBefore.contains(path.toAbsolutePath()))
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0L;
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException error) {
            return null;
        }
    }

    private long totalSinkHeat() {
        long total = 0L;
        for (Module module : modules) {
            total += module.sink().machineComponents().require(ScalarResourcePort.HEAT_OUTPUT_1).storedAmount();
        }
        return total;
    }

    private long totalHeaterStarts(boolean remembered) {
        long total = 0L;
        for (Module module : modules) {
            RecipeLogic logic = module.sink().machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            total += remembered ? logic.rememberedStartCount() : logic.successfulStartCount();
        }
        return total;
    }

    private void captureModuleHeat(long[] destination) {
        for (Module module : modules) {
            destination[module.index()] = module.sink().machineComponents()
                    .require(ScalarResourcePort.HEAT_OUTPUT_1)
                    .storedAmount();
        }
    }

    private static int exactDeltas(long[] before, long[] after, long expectedDelta) {
        int exact = 0;
        for (int index = 0; index < before.length; index++) {
            if (after[index] - before[index] == expectedDelta) {
                exact++;
            }
        }
        return exact;
    }

    private static MachineBlockEntity requireMachine(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("Missing machine block entity at " + pos);
    }

    private static void insertCoal(MachineBlockEntity machine, int amount) {
        ItemResourcePort input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.handler().insert(ItemResource.of(Items.COAL), amount, transaction);
            if (inserted != amount) {
                throw new IllegalStateException("Expected " + amount + " coal, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void captureGc(Map<String, long[]> destination) {
        destination.clear();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            destination.put(bean.getName(), new long[] { bean.getCollectionCount(), bean.getCollectionTime() });
        }
    }

    private void unsubscribeTickHook() {
        TickHandle startHandle = batchStartHandle;
        batchStartHandle = null;
        if (startHandle != null) {
            startHandle.unsubscribe();
        }
        TickHandle handle = tickHandle;
        tickHandle = null;
        if (handle != null) {
            handle.unsubscribe();
        }
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }

    private long worstPerMachinePercentile(byte window, long[] values, double percentile) {
        long worst = 0L;
        for (int machineIndex = 0; machineIndex < MODULE_COUNT; machineIndex++) {
            worst = Math.max(worst, perMachinePercentile(window, machineIndex, values, percentile));
        }
        return worst;
    }

    private long perMachinePercentile(byte window, int machineIndex, long[] values, double percentile) {
        long[] matching = new long[WINDOW_TICKS];
        int count = 0;
        for (int index = 0; index < rawSize; index++) {
            if (rawWindow[index] == window && rawMachine[index] == machineIndex) {
                matching[count++] = values[index];
            }
        }
        if (count != WINDOW_TICKS) {
            throw new IllegalStateException("Expected " + WINDOW_TICKS + " samples for machine " + machineIndex + " in window " + window + ", found " + count);
        }
        java.util.Arrays.sort(matching, 0, count);
        int index = (int) Math.ceil(percentile * count) - 1;
        return matching[Math.clamp(index, 0, count - 1)];
    }

    private boolean allStateSamples(byte window, int target, RecipeLogic.State state, long expected) {
        return stateCounts(window, target, state).stream().allMatch(count -> count == expected);
    }

    private List<Long> stateCounts(byte window, int target, RecipeLogic.State state) {
        List<Long> counts = new ArrayList<>(MODULE_COUNT);
        byte stateOrdinal = (byte) state.ordinal();
        for (int machineIndex = 0; machineIndex < MODULE_COUNT; machineIndex++) {
            long count = 0L;
            int samples = 0;
            for (int index = 0; index < rawSize; index++) {
                if (rawWindow[index] != window || rawMachine[index] != machineIndex) {
                    continue;
                }
                samples++;
                if (rawLogicState[target][index] == stateOrdinal) {
                    count++;
                }
            }
            if (samples != WINDOW_TICKS) {
                throw new IllegalStateException("Expected " + WINDOW_TICKS + " state samples for machine " + machineIndex + " in window " + window + ", found " + samples);
            }
            counts.add(count);
        }
        return counts;
    }

    private static long delta(long after, long before) {
        return after < 0L || before < 0L ? -1L : Math.max(0L, after - before);
    }

    private static String percent(long count, long total) {
        return String.format(Locale.ROOT, "%.4f", total == 0L ? 0.0 : count * 100.0 / total);
    }

    private static String nanosToMicros(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000.0);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static Path resolveProjectRoot() {
        String configured = System.getProperty("oi.profiler.projectRoot");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        return Files.isRegularFile(current.resolve("build.gradle")) ? current : current.getParent();
    }
}
