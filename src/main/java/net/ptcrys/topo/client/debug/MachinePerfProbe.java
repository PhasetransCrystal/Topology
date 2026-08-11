package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.api.tick.NoopTickHandle;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 空闲机器性能取证探针(同 {@link TooltipProbe} 的流水线骨架,独立 flag):复现"零能量高级能量
 * 发生器空闲时 Jade 计时面板显示数微秒/tick"的用户实况。真实客户端+集成服务器环境放一台
 * 高级能量发生器(永不注入能量,停泊在等待输入态),开性能监控逐 tick 记录 perfTree 序列
 * (last/EMA/peak):先采"冷"段(世界刚载入,热路径方法只被解释执行),再 tight-loop 分解空闲
 * 路径各段(顺带触发 JIT 编译),最后采"暖"段——冷暖两段同机器同状态,唯一变量是代码热度,
 * 用于判定面板高读数是 JIT 冷代码采样还是真实持续开销。
 *
 * <p>
 * flag: {@code topo-machine-perf-probe.flag};报告: {@code topo-machine-perf-probe-report.txt}。
 */
public final class MachinePerfProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Topo-MachinePerfProbe");
    private static final Path FLAG_FILE = Path.of("topo-machine-perf-probe.flag");
    private static final Path REPORT_FILE = Path.of("topo-machine-perf-probe-report.txt");
    /** 每轮唯一世界名:复用同名存档会撞残留场景(并行会话共用 run/ 时尤甚)。 */
    private static final String LEVEL_ID = "topo-machine-perf-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;
    private static final int SETTLE_TICKS = 40;
    private static final int SAMPLE_TICKS = 120;
    private static final int SERIES_HEAD = 20;
    private static final int TIGHT_LOOP_WARMUP = 5_000;
    private static final int TIGHT_LOOP_ITERATIONS = 20_000;

    /** 让 JIT 无法消除被测调用。 */
    @SuppressWarnings("unused")
    private static volatile Object sink;

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        PLACE,
        SETTLE,
        SAMPLE_COLD,
        DECOMPOSE,
        SAMPLE_WARM,
        FLUSH,
        DONE
    }

    private record TickSample(long gameTime, long lastNanos, long avgNanos, long peakNanos, String state) {}

    private static MachinePerfProbe instance;

    private final StringBuilder report = new StringBuilder();
    private final List<TickSample> coldSeries = new ArrayList<>();
    private final List<TickSample> warmSeries = new ArrayList<>();
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private long lastSampledGameTime = Long.MIN_VALUE;
    private BlockPos machinePos;
    private boolean decomposeDone;

    private MachinePerfProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new MachinePerfProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Machine perf probe armed: sampling a working combustion generator cold and warm");
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
                                    LevelSettings.DifficultySettings.DEFAULT,
                                    true,
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20260612L, false, false),
                            WorldPresets::createFlatWorldDimensions,
                            minecraft.screen);
                    waitTicks = 0;
                    state = State.WAIT_WORLD;
                }
            }
            case WAIT_WORLD -> {
                if (timedOut(minecraft, "waiting for probe world")) {
                    return;
                }
                if (minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null && minecraft.screen == null) {
                    placeMachine(minecraft);
                    countdown = SETTLE_TICKS;
                    state = State.SETTLE;
                }
            }
            case PLACE -> {}
            case SETTLE -> {
                // 不开监控:让机器以未观测状态停泊,冷段的第一个监控样本即用户"刚看向机器"的样本。
                if (--countdown <= 0) {
                    countdown = SAMPLE_TICKS;
                    state = State.SAMPLE_COLD;
                }
            }
            case SAMPLE_COLD -> {
                sampleTick(minecraft, coldSeries);
                if (--countdown <= 0) {
                    decomposeDone = false;
                    state = State.DECOMPOSE;
                    runDecomposition(minecraft);
                }
            }
            case DECOMPOSE -> {
                if (decomposeDone) {
                    countdown = SAMPLE_TICKS;
                    state = State.SAMPLE_WARM;
                }
            }
            case SAMPLE_WARM -> {
                sampleTick(minecraft, warmSeries);
                if (--countdown <= 0) {
                    summarize();
                    countdown = 5;
                    state = State.FLUSH;
                }
            }
            case FLUSH -> {
                if (--countdown <= 0) {
                    finish(minecraft);
                }
            }
            case DONE -> {}
        }
    }

    private void placeMachine(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return;
            }
            ServerLevel level = server.overworld();
            // 放在玩家脚边下方两格:避开准星方向,防止 Jade 数据轮询提前激活监控污染冷段。
            BlockPos pos = players.getFirst().blockPosition().east(4).below(2);
            level.setBlock(pos, BuiltinTopoMachines.COMBUSTION_GENERATOR_T1.registeredBlock().getDefaultState(), 3);
            if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
                report.append("machine block entity missing -> FAIL\n");
                return;
            }
            insertCoal(machine, 64);
            machinePos = pos;
            report.append("machine placed at ").append(pos).append('\n');
        });
    }

    /** 每个客户端 tick 在服务端读一次最新性能快照(按 gameTime 去重);首次采样时开监控。 */
    private void sampleTick(Minecraft minecraft, List<TickSample> series) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || machinePos == null) {
            return;
        }
        server.execute(() -> {
            MachineBlockEntity machine = machineOrNull(server);
            if (machine == null) {
                return;
            }
            drainEnergyOutput(machine);
            machine.activatePerformanceMonitoring(SAMPLE_TICKS * 4);
            long gameTime = server.overworld().getGameTime();
            if (gameTime == lastSampledGameTime) {
                return;
            }
            if (!machine.publishRecentPerformanceSnapshot(machine.performanceSampleMaxAgeTicks())) {
                return;
            }
            MachinePerformanceSnapshot snapshot = machine.lastPerformanceSnapshot();
            if (snapshot.isEmpty()) {
                return;
            }
            lastSampledGameTime = gameTime;
            RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            String componentId = RecipeLogic.RECIPE_LOGIC_1.id().toString();
            for (MachinePerformanceSnapshot.ComponentSample sample : snapshot.components()) {
                if (sample.id().equals(componentId)) {
                    series.add(new TickSample(
                            gameTime,
                            sample.nanos(),
                            sample.avgNanos(),
                            sample.peakNanos(),
                            logic.state().name()));
                    return;
                }
            }
        });
    }

    /**
     * 空闲路径各段 tight-loop 分解(在服务端线程跑)。副作用:把空闲 tick 涉及的方法全部推过
     * JIT 编译阈值,因此它同时是 SAMPLE_WARM 的热身段。
     */
    private void runDecomposition(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            decomposeDone = true;
            return;
        }
        server.execute(() -> {
            try {
                MachineBlockEntity machine = machineOrNull(server);
                if (machine == null) {
                    report.append("decompose: machine missing -> FAIL\n");
                    return;
                }
                RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
                TopoRecipeType<TopoRecipe> type = BuiltinTopoRecipeTypes.COMBUSTION_GENERATOR;
                RecipeHolder<TopoRecipe> holder = type.findRecipe(machine);
                report.append("decompose: parked state=").append(logic.state())
                        .append(", searchCacheable=").append(type.resourceVersionSearchCacheable(machine))
                        .append(", recipeFound=").append(holder != null)
                        .append('\n');
                if (holder == null) {
                    return;
                }
                TopoRecipe recipe = holder.value();
                report.append("decompose: startRetryStable=").append(recipe.startRetryResourceVersionStable())
                        .append(", directTickIo=").append(recipe.hasDirectTickIo())
                        .append('\n');
                report.append("-- working path decomposition (client-env server thread, ns/op over ")
                        .append(TIGHT_LOOP_ITERATIONS).append(" iterations) --\n");
                measure("traits_resourceContentVersion", () -> sink = machine.machineComponents().resourceContentVersion());
                measure("type_resourceVersionSearchCacheable", () -> sink = type.resourceVersionSearchCacheable(machine));
                measure("type_searchRevision", () -> sink = type.searchRevision());
                measure("type_findRecipe_full_search", () -> sink = type.findRecipe(machine));
                measure("recipe_checkTickIo_zero_energy", () -> sink = recipe.checkTickIo(machine));
                measure("recipe_canEmitOutputs", () -> sink = recipe.canEmitOutputs(machine));
                long[] syntheticGameTime = { server.overworld().getGameTime() + 100_000L };
                measure("logic_tick_working_direct", () -> {
                    logic.tick(syntheticGameTime[0]++, NoopTickHandle.INSTANCE);
                    drainEnergyOutput(machine);
                });
                report.append("decompose: state after tight loops=").append(logic.state()).append('\n');
            } finally {
                decomposeDone = true;
            }
        });
    }

    private void measure(String name, Runnable operation) {
        for (int i = 0; i < TIGHT_LOOP_WARMUP; i++) {
            operation.run();
        }
        long start = System.nanoTime();
        for (int i = 0; i < TIGHT_LOOP_ITERATIONS; i++) {
            operation.run();
        }
        long elapsed = System.nanoTime() - start;
        report.append(String.format(Locale.ROOT, "%-40s %8.1f ns/op%n", name, elapsed / (double) TIGHT_LOOP_ITERATIONS));
    }

    private MachineBlockEntity machineOrNull(MinecraftServer server) {
        if (machinePos == null) {
            return null;
        }
        return server.overworld().getBlockEntity(machinePos) instanceof MachineBlockEntity machine ? machine : null;
    }

    private void summarize() {
        appendSeries("cold (fresh world, first monitored ticks)", coldSeries);
        appendSeries("warm (after tight-loop JIT warmup)", warmSeries);
    }

    private void appendSeries(String title, List<TickSample> series) {
        report.append("== ").append(title).append(": ").append(series.size()).append(" samples ==\n");
        if (series.isEmpty()) {
            report.append("no samples -> FAIL\n");
            return;
        }
        int head = Math.min(SERIES_HEAD, series.size());
        for (int i = 0; i < head; i++) {
            TickSample sample = series.get(i);
            report.append(String.format(
                    Locale.ROOT,
                    "#%-3d t=%-8d last=%-8d ema=%-8d peak=%-8d %s%n",
                    i + 1,
                    sample.gameTime(),
                    sample.lastNanos(),
                    sample.avgNanos(),
                    sample.peakNanos(),
                    sample.state()));
        }
        List<TickSample> tail = series.subList(series.size() / 2, series.size());
        List<Long> lastSorted = tail.stream().map(TickSample::lastNanos).sorted().toList();
        List<Long> emaSorted = tail.stream().map(TickSample::avgNanos).sorted().toList();
        report.append(String.format(
                Locale.ROOT,
                "steady half: last avg=%.0f ns, last p95=%d ns, ema avg=%.0f ns, ema p95=%d ns, ema max=%d ns, peak max=%d ns%n",
                tail.stream().mapToLong(TickSample::lastNanos).average().orElse(0),
                percentile(lastSorted, 0.95),
                tail.stream().mapToLong(TickSample::avgNanos).average().orElse(0),
                percentile(emaSorted, 0.95),
                tail.stream().mapToLong(TickSample::avgNanos).max().orElse(0),
                tail.stream().mapToLong(TickSample::peakNanos).max().orElse(0)));
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }

    private static void insertCoal(MachineBlockEntity machine, int count) {
        ItemResourcePort input = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.handler().insert(ItemResource.of(Items.COAL), count, transaction);
            if (inserted != count) {
                throw new IllegalStateException("Expected " + count + " coal, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void drainEnergyOutput(MachineBlockEntity machine) {
        ScalarResourcePort output = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            output.handler().extract(output.resource(), Integer.MAX_VALUE, transaction);
            transaction.commit();
        }
    }

    private boolean timedOut(Minecraft minecraft, String what) {
        if (++waitTicks > WAIT_TIMEOUT_TICKS) {
            report.append("timeout ").append(what).append(" -> FAIL\n");
            finish(minecraft);
            return true;
        }
        return false;
    }

    private void finish(Minecraft minecraft) {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException exception) {
            LOGGER.error("Machine perf probe failed to flush its report", exception);
        }
        LOGGER.warn("Machine perf probe done:\n{}", report);
        minecraft.stop();
    }
}
