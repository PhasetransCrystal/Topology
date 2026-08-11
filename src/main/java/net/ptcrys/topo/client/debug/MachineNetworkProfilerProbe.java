package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.data.pipe.BuiltinOIPipes;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
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
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Programmatic integrated-server profile of 100 working machines on 100 real pipe networks. */
public final class MachineNetworkProfilerProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("OI-MachineNetworkProfilerProbe");
    private static final Path FLAG_FILE = Path.of("oi-machine-network-profiler.flag");
    private static final Path REPORT_FILE = Path.of("oi-machine-network-profiler-report.txt");
    private static final Path CSV_FILE = Path.of("oi-machine-network-profiler-samples.csv");
    private static final Path SCREENSHOT_FILE = Path.of("screenshots", "oi-machine-network-profiler-world.png");
    private static final String LEVEL_ID = "oi-machine-network-profiler";
    private static final int MACHINE_COUNT = 100;
    private static final int GRID_SIZE = 10;
    private static final int MODULE_SPACING = 6;
    private static final int SETUP_TICKS = 80;
    private static final int WARMUP_TICKS = Integer.getInteger("oi.profiler.networkWarmupTicks", 200);
    private static final int PROFILE_TICKS = Integer.getInteger("oi.profiler.networkProfileTicks", 600);
    private static final int WAIT_TIMEOUT_TICKS = 3600;

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        BUILD,
        SETTLE,
        WARMUP,
        PROFILE,
        FLUSH,
        DONE
    }

    private record Module(BlockPos generator, BlockPos firstPipe, BlockPos secondPipe, BlockPos sink) {}

    private record TickSample(long gameTime, int working, int networks, long moved, long machineNanos, long recipeNanos) {}

    private record MethodKey(String className, String methodName, int line) {

        String display() {
            return className + "." + methodName + (line > 0 ? ":" + line : "");
        }
    }

    private static MachineNetworkProfilerProbe instance;

    private final List<Module> modules = new ArrayList<>(MACHINE_COUNT);
    private final List<TickSample> samples = new ArrayList<>(PROFILE_TICKS);
    private State state = State.WAIT_TITLE;
    private long phaseStartGameTime = Long.MIN_VALUE;
    private int waitTicks;
    private boolean buildComplete;
    private long lastSampledGameTime = Long.MIN_VALUE;
    private long firstProfileGameTime = Long.MIN_VALUE;
    private long gcCollectionsAtStart;
    private long gcMillisAtStart;
    private Recording recording;
    private Path jfrFile;

    private MachineNetworkProfilerProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new MachineNetworkProfilerProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("100-machine integrated-server profiler armed");
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (state) {
            case WAIT_TITLE -> {
                if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    minecraft.options.pauseOnLostFocus = false;
                    minecraft.createWorldOpenFlows().createFreshLevel(
                            LEVEL_ID,
                            new LevelSettings(
                                    LEVEL_ID,
                                    GameType.CREATIVE,
                                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, true),
                                    true,
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20260711L, false, false),
                            WorldPresets::createFlatWorldDimensions,
                            minecraft.screen);
                    state = State.WAIT_WORLD;
                }
            }
            case WAIT_WORLD -> {
                if (timedOut(minecraft, "waiting for integrated world")) {
                    return;
                }
                if (minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null && minecraft.screen == null) {
                    state = State.BUILD;
                    buildWorld(minecraft);
                }
            }
            case BUILD -> {
                if (timedOut(minecraft, "building 100-machine scene")) {
                    return;
                }
                if (buildComplete) {
                    phaseStartGameTime = Long.MIN_VALUE;
                    state = State.SETTLE;
                }
            }
            case SETTLE -> {
                sampleWorld(minecraft, false);
                if (serverTicksElapsed(minecraft, SETUP_TICKS)) {
                    screenshot(minecraft);
                    phaseStartGameTime = Long.MIN_VALUE;
                    state = State.WARMUP;
                }
            }
            case WARMUP -> {
                sampleWorld(minecraft, false);
                if (serverTicksElapsed(minecraft, WARMUP_TICKS)) {
                    startProfile(minecraft);
                }
            }
            case PROFILE -> {
                sampleWorld(minecraft, true);
                if (serverTicksElapsed(minecraft, PROFILE_TICKS)) {
                    stopProfile(minecraft);
                }
            }
            case FLUSH -> {
                if (serverTicksElapsed(minecraft, 20)) {
                    finish(minecraft);
                }
            }
            case DONE -> {}
        }
    }

    private void buildWorld(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                BlockPos origin = server.getPlayerList().getPlayers().getFirst().blockPosition().offset(-27, -1, -27);
                for (int index = 0; index < MACHINE_COUNT; index++) {
                    int column = index % GRID_SIZE;
                    int row = index / GRID_SIZE;
                    BlockPos generator = origin.offset(column * MODULE_SPACING, 0, row * MODULE_SPACING);
                    BlockPos firstPipe = generator.south();
                    BlockPos secondPipe = generator.south(2);
                    BlockPos sink = generator.south(3);
                    level.setBlock(
                            generator,
                            BuiltinOIMachines.COMBUSTION_GENERATOR_T1.registeredBlock().getDefaultState(),
                            3);
                    level.setBlock(
                            firstPipe,
                            BuiltinOIPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState(),
                            3);
                    level.setBlock(
                            secondPipe,
                            BuiltinOIPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState(),
                            3);
                    level.setBlock(
                            sink,
                            BuiltinOIMachines.RESISTIVE_HEATER_T3.registeredBlock().getDefaultState(),
                            3);
                    MachineBlockEntity machine = requireMachine(level, generator);
                    insertCoal(machine, 64);
                    modules.add(new Module(generator, firstPipe, secondPipe, sink));
                }
                PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
                for (Module module : modules) {
                    runtime.setSideIntent(module.firstPipe(), Direction.NORTH, PipeSideIntent.EXTRACT);
                    int minInterval = runtime.portWindow(module.firstPipe(), Direction.NORTH).minInterval();
                    runtime.setExtractConfig(
                            module.firstPipe(),
                            Direction.NORTH,
                            runtime.portConfig(module.firstPipe(), Direction.NORTH).withInterval(minInterval));
                }
                buildComplete = true;
            } catch (Throwable throwable) {
                LOGGER.error("Unable to build 100-machine profile scene", throwable);
                finishWithFailure(minecraft, "build failed: " + throwable);
            }
        });
    }

    private void sampleWorld(Minecraft minecraft, boolean recordSample) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || modules.size() != MACHINE_COUNT) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            long gameTime = level.getGameTime();
            if (gameTime == lastSampledGameTime) {
                return;
            }
            lastSampledGameTime = gameTime;
            int working = 0;
            int networks = 0;
            long moved = 0L;
            long machineNanos = 0L;
            long recipeNanos = 0L;
            for (Module module : modules) {
                MachineBlockEntity machine = requireMachine(level, module.generator());
                MachineBlockEntity sink = requireMachine(level, module.sink());
                RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
                if (logic.state() == RecipeLogic.State.WORKING) {
                    working++;
                }
                PipeNetwork<?> network = PipeNetworkEngine.networkAt(level, module.firstPipe());
                if (network != null && network.extractorCount() == 1 && network.nodeCount() == 2) {
                    networks++;
                    moved += network.windowMovedLast();
                }
                ScalarResourcePort sinkStorage = sink.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
                if (sinkStorage.handler().getAmountAsLong(0) >= sinkStorage.capacityAmount() / 2L) {
                    sinkStorage.handler().set(0, sinkStorage.resource(), 0);
                }
                ScalarResourcePort sinkHeat = sink.machineComponents().require(ScalarResourcePort.HEAT_OUTPUT_1);
                if (sinkHeat.handler().getAmountAsLong(0) >= sinkHeat.capacityAmount() / 2L) {
                    sinkHeat.handler().set(0, sinkHeat.resource(), 0);
                }
                if (recordSample) {
                    machine.activatePerformanceMonitoring(PROFILE_TICKS + 20);
                    if (machine.publishRecentPerformanceSnapshot(machine.performanceSampleMaxAgeTicks())) {
                        var snapshot = machine.lastPerformanceSnapshot();
                        machineNanos += snapshot.totalNanos();
                        String logicId = RecipeLogic.RECIPE_LOGIC_1.id().toString();
                        for (var component : snapshot.components()) {
                            if (logicId.equals(component.id())) {
                                recipeNanos += component.nanos();
                                break;
                            }
                        }
                    }
                }
            }
            if (recordSample) {
                if (firstProfileGameTime == Long.MIN_VALUE) {
                    firstProfileGameTime = gameTime;
                }
                samples.add(new TickSample(gameTime, working, networks, moved, machineNanos, recipeNanos));
            }
        });
    }

    private void startProfile(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            finishWithFailure(minecraft, "integrated server disappeared before profiling");
            return;
        }
        server.execute(() -> {
            try {
                Path projectRoot = Path.of(System.getProperty("oi.profiler.projectRoot", ".")).toAbsolutePath();
                Path reports = projectRoot.resolve("build").resolve("reports");
                Files.createDirectories(reports);
                jfrFile = reports.resolve("oi-100-machine-real-pipe.jfr");
                Files.deleteIfExists(jfrFile);
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.setName("oi-100-machine-real-pipe");
                recording.setToDisk(true);
                recording.setDestination(jfrFile);
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
                recording.start();
                gcCollectionsAtStart = gcCollections();
                gcMillisAtStart = gcMillis();
                executeSpark(server, "spark profiler start --interval 1 --thread Server thread --force-java-sampler");
                phaseStartGameTime = Long.MIN_VALUE;
                state = State.PROFILE;
            } catch (Throwable throwable) {
                LOGGER.error("Unable to start profiler", throwable);
                finishWithFailure(minecraft, "profile start failed: " + throwable);
            }
        });
    }

    private void stopProfile(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            finishWithFailure(minecraft, "integrated server disappeared while profiling");
            return;
        }
        server.execute(() -> {
            try {
                executeSpark(server, "spark profiler stop --save-to-file");
                closeRecording();
                writeOutputs();
                phaseStartGameTime = Long.MIN_VALUE;
                state = State.FLUSH;
            } catch (Throwable throwable) {
                LOGGER.error("Unable to finish profiler", throwable);
                finishWithFailure(minecraft, "profile finish failed: " + throwable);
            }
        });
    }

    private static void executeSpark(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private void writeOutputs() throws IOException {
        if (jfrFile == null || !Files.isRegularFile(jfrFile)) {
            throw new IOException("JFR output is missing");
        }
        Path reportDir = jfrFile.getParent();
        Path hotMethods = reportDir.resolve("oi-100-machine-real-pipe-hot-methods.csv");
        Path collapsed = reportDir.resolve("oi-100-machine-real-pipe-collapsed.txt");
        Map<MethodKey, Long> self = new HashMap<>();
        Map<MethodKey, Long> inclusive = new HashMap<>();
        Map<String, Long> stacks = new HashMap<>();
        long executionSamples = 0L;
        long serverThreadSamples = 0L;
        try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (!"jdk.ExecutionSample".equals(event.getEventType().getName())) {
                    continue;
                }
                executionSamples++;
                if (!"Server thread".equals(event.getThread("sampledThread").getJavaName()) || event.getStackTrace() == null) {
                    continue;
                }
                List<RecordedFrame> frames = event.getStackTrace().getFrames();
                if (frames.isEmpty()) {
                    continue;
                }
                serverThreadSamples++;
                MethodKey top = methodKey(frames.getFirst());
                self.merge(top, 1L, Long::sum);
                StringBuilder stack = new StringBuilder();
                for (int index = frames.size() - 1; index >= 0; index--) {
                    MethodKey key = methodKey(frames.get(index));
                    inclusive.merge(key, 1L, Long::sum);
                    if (!stack.isEmpty()) {
                        stack.append(';');
                    }
                    stack.append(key.className()).append('.').append(key.methodName());
                }
                stacks.merge(stack.toString(), 1L, Long::sum);
            }
        }
        List<MethodKey> keys = new ArrayList<>(inclusive.keySet());
        keys.sort(Comparator.comparingLong((MethodKey key) -> inclusive.getOrDefault(key, 0L)).reversed());
        StringBuilder hotCsv = new StringBuilder("class,method,line,selfSamples,inclusiveSamples,inclusivePercent\n");
        for (MethodKey key : keys) {
            long inclusiveCount = inclusive.getOrDefault(key, 0L);
            hotCsv.append(csv(key.className())).append(',')
                    .append(csv(key.methodName())).append(',')
                    .append(key.line()).append(',')
                    .append(self.getOrDefault(key, 0L)).append(',')
                    .append(inclusiveCount).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", percent(inclusiveCount, serverThreadSamples)))
                    .append('\n');
        }
        Files.writeString(hotMethods, hotCsv.toString(), StandardCharsets.UTF_8);
        StringBuilder collapsedText = new StringBuilder();
        stacks.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> collapsedText.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n'));
        Files.writeString(collapsed, collapsedText.toString(), StandardCharsets.UTF_8);

        StringBuilder sampleCsv = new StringBuilder("gameTime,working,networks,moved,machineNanos,recipeNanos\n");
        for (TickSample sample : samples) {
            sampleCsv.append(sample.gameTime()).append(',')
                    .append(sample.working()).append(',')
                    .append(sample.networks()).append(',')
                    .append(sample.moved()).append(',')
                    .append(sample.machineNanos()).append(',')
                    .append(sample.recipeNanos()).append('\n');
        }
        Files.writeString(CSV_FILE, sampleCsv.toString(), StandardCharsets.UTF_8);

        long[] machineSeries = samples.stream().mapToLong(TickSample::machineNanos).filter(value -> value > 0L).toArray();
        long[] recipeSeries = samples.stream().mapToLong(TickSample::recipeNanos).filter(value -> value > 0L).toArray();
        int minWorking = samples.stream().mapToInt(TickSample::working).min().orElse(0);
        int minNetworks = samples.stream().mapToInt(TickSample::networks).min().orElse(0);
        long totalMoved = samples.stream().mapToLong(TickSample::moved).sum();
        long gcCollections = gcCollections() - gcCollectionsAtStart;
        long gcMillis = gcMillis() - gcMillisAtStart;
        StringBuilder report = new StringBuilder();
        report.append("OI 100-machine real-pipe profiler\n")
                .append("machines: ").append(MACHINE_COUNT).append('\n')
                .append("real networks: ").append(MACHINE_COUNT).append('\n')
                .append("profile ticks requested: ").append(PROFILE_TICKS).append('\n')
                .append("profile tick samples: ").append(samples.size()).append('\n')
                .append("first/last server game time: ").append(firstProfileGameTime).append('/')
                .append(samples.isEmpty() ? Long.MIN_VALUE : samples.getLast().gameTime()).append('\n')
                .append("minimum WORKING machines: ").append(minWorking).append('\n')
                .append("minimum valid 2-node/1-extractor networks: ").append(minNetworks).append('\n')
                .append("sum of network moved windows: ").append(totalMoved).append('\n')
                .append("profile-window GC collections: ").append(gcCollections).append('\n')
                .append("profile-window GC time: ").append(gcMillis).append(" ms\n")
                .append(String.format(Locale.ROOT, "aggregate whole-machine p95: %.3f us%n", percentile(machineSeries, 0.95) / 1_000.0))
                .append(String.format(Locale.ROOT, "aggregate RecipeLogic p95: %.3f us%n", percentile(recipeSeries, 0.95) / 1_000.0))
                .append("JFR execution samples (all/server): ").append(executionSamples).append('/')
                .append(serverThreadSamples).append('\n')
                .append("JFR: ").append(jfrFile.toAbsolutePath()).append('\n')
                .append("hot methods: ").append(hotMethods.toAbsolutePath()).append('\n')
                .append("collapsed stacks: ").append(collapsed.toAbsolutePath()).append('\n')
                .append("Spark: config/spark/profile-*.sparkprofile\n");
        int top = Math.min(30, keys.size());
        report.append("top server-thread methods by inclusive JFR samples:\n");
        for (int index = 0; index < top; index++) {
            MethodKey key = keys.get(index);
            report.append(String.format(
                    Locale.ROOT,
                    "  %6.2f%% self=%-5d inclusive=%-5d %s%n",
                    percent(inclusive.getOrDefault(key, 0L), serverThreadSamples),
                    self.getOrDefault(key, 0L),
                    inclusive.getOrDefault(key, 0L),
                    key.display()));
        }
        boolean pass = samples.size() >= PROFILE_TICKS / 2 && minWorking == MACHINE_COUNT && minNetworks == MACHINE_COUNT && totalMoved > 0L && gcCollections == 0L && serverThreadSamples > 0L;
        report.append(pass ? "RESULT PASS\n" : "RESULT FAIL\n");
        Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
    }

    private static MethodKey methodKey(RecordedFrame frame) {
        RecordedMethod method = frame.getMethod();
        return new MethodKey(method.getType().getName(), method.getName(), frame.getLineNumber());
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static double percent(long samples, long total) {
        return total <= 0L ? 0.0 : samples * 100.0 / total;
    }

    private static long percentile(long[] values, double quantile) {
        if (values.length == 0) {
            return 0L;
        }
        java.util.Arrays.sort(values);
        int index = Math.max(0, Math.min(values.length - 1, (int) Math.ceil(quantile * values.length) - 1));
        return values[index];
    }

    private static long gcCollections() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, bean.getCollectionCount());
        }
        return total;
    }

    private static long gcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, bean.getCollectionTime());
        }
        return total;
    }

    private static MachineBlockEntity requireMachine(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("Machine block entity missing at " + pos);
    }

    private static void insertCoal(MachineBlockEntity machine, int count) {
        ItemResourcePort port = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = port.handler().insert(ItemResource.of(Items.COAL), count, transaction);
            if (inserted != count) {
                throw new IllegalStateException("Expected " + count + " coal, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private void screenshot(Minecraft minecraft) {
        try {
            Files.deleteIfExists(SCREENSHOT_FILE);
            Screenshot.grab(
                    minecraft.gameDirectory,
                    SCREENSHOT_FILE.getFileName().toString(),
                    minecraft.getMainRenderTarget(),
                    1,
                    component -> {});
        } catch (Exception exception) {
            LOGGER.error("Unable to capture profiler scene", exception);
        }
    }

    private boolean timedOut(Minecraft minecraft, String stage) {
        if (++waitTicks <= WAIT_TIMEOUT_TICKS) {
            return false;
        }
        finishWithFailure(minecraft, "timeout while " + stage);
        return true;
    }

    private boolean serverTicksElapsed(Minecraft minecraft, int ticks) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return false;
        }
        long gameTime = server.overworld().getGameTime();
        if (phaseStartGameTime == Long.MIN_VALUE) {
            phaseStartGameTime = gameTime;
            return false;
        }
        return gameTime - phaseStartGameTime >= ticks;
    }

    private void finishWithFailure(Minecraft minecraft, String failure) {
        try {
            closeRecording();
            Files.writeString(REPORT_FILE, failure + "\nRESULT FAIL\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("Unable to write profiler failure", exception);
        }
        finish(minecraft);
    }

    private void closeRecording() {
        Recording active = recording;
        recording = null;
        if (active == null) {
            return;
        }
        try {
            active.stop();
        } finally {
            active.close();
        }
    }

    private void finish(Minecraft minecraft) {
        if (state == State.DONE) {
            return;
        }
        closeRecording();
        state = State.DONE;
        try {
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException exception) {
            LOGGER.error("Unable to clean profiler flag", exception);
        }
        minecraft.stop();
    }
}
