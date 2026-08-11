package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.apiv2.machine.ui.LcdData;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UI 性能诊断探针:LDLib2 布局脏循环("UI layout is dirty for more than 10 times per frame")
 * 与重排风暴的全自动取证工具。仅当游戏工作目录存在 {@code oi-ui-probe.flag} 文件时激活,
 * 平时零开销;激活后从标题画面起全自动跑完并退出,报告写入 {@code oi-ui-probe-report.txt}。
 *
 * <p>
 * 流程:合成计时面板终验(定宽 LCD + 每 tick 实况文本)→ 存在 {@code saves/oi-probe-user-copy}
 * 则打开用户存档副本、扫描出生点周边机器按类型去重逐台开 GUI,否则自建平坦世界放电锅炉 →
 * 每台机器 GUI 期间虚拟鼠标扫掠悬浮、持续灌能量/抽热量保持运转 → 最后注视机器采集 Jade 阶段。
 *
 * <p>
 * 取证手段:整树 LAYOUT_CHANGED 元素级计数(每帧峰值 maxPerFrame=10 即吃满 LDLib2 循环上限)
 * 与最近几何序列;log4j tap 挂 LowDragLib2 logger 做进程内警告计数+首发抓栈(两个 dev 客户端
 * 共享 latest.log 会交织写入,文本日志无法归属进程);{@code instrumentExternal} 供 Jade 桥等
 * 外部 ldlib2 树接入。2026-06-11 用它定位并验证了 Jade 计时面板的布局死循环(见
 * MachineJadeTimingPanel 与 LcdDatanColumns 的注释)。
 */
public final class UiPerfProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("OI-UiPerfProbe");
    private static final Path FLAG_FILE = Path.of("oi-ui-probe.flag");
    private static final Path MACHINE_JADE_PERF_FLAG_FILE = Path.of("oi-machine-jade-perf-probe.flag");
    private static final Path REPORT_FILE = Path.of("oi-ui-probe-report.txt");
    private static final String LEVEL_ID = "oi-ui-probe-world";
    private static final String COPY_LEVEL_ID = "oi-probe-user-copy";
    private static final int SETTLE_DELAY_TICKS = 60;
    private static final int COLLECT_TICKS = 300;
    private static final int JADE_TICKS = 80;
    private static final int WAIT_TIMEOUT_TICKS = 2400;
    private static final int TOP_OFFENDERS = 25;
    private static final int GEOMETRY_HISTORY = 6;

    private enum State {
        WAIT_TITLE,
        SYNTH_COLLECT,
        WAIT_WORLD,
        SETTLE,
        PLACE_WAIT,
        WAIT_SCREEN,
        COLLECT_GUI,
        COLLECT_JADE,
        DONE
    }

    private static final class ElementStat {

        final String label;
        long events;
        long eventsThisFrame;
        long maxEventsPerFrame;
        final ArrayDeque<String> recentGeometry = new ArrayDeque<>();

        ElementStat(String label) {
            this.label = label;
        }

        void record(UIElement element) {
            events++;
            eventsThisFrame++;
            String geometry = String.format(
                    Locale.ROOT,
                    "(x=%.2f y=%.2f w=%.2f h=%.2f)",
                    element.getPositionX(),
                    element.getPositionY(),
                    element.getSizeWidth(),
                    element.getSizeHeight());
            if (recentGeometry.size() >= GEOMETRY_HISTORY) {
                recentGeometry.removeFirst();
            }
            recentGeometry.addLast(geometry);
        }

        void frameEnded() {
            if (eventsThisFrame > maxEventsPerFrame) {
                maxEventsPerFrame = eventsThisFrame;
            }
            eventsThisFrame = 0;
        }
    }

    private static UiPerfProbe instance;

    private final StringBuilder report = new StringBuilder();
    private final Map<UIElement, ElementStat> stats = new LinkedHashMap<>();
    /** Jade 等外部 ldlib2 树的统计(经 [instrumentExternal] 注入,全程累计)。 */
    private final Map<UIElement, ElementStat> externalStats = new LinkedHashMap<>();

    private final Map<State, Long> dirtyWarningsByState = new LinkedHashMap<>();
    private final Map<State, String> dirtyWarningStackByState = new LinkedHashMap<>();

    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private long collectStartNanos;
    private long framesObserved;
    private long hotFrames;
    private long maxFrameEvents;
    private int elementCount;
    private boolean instrumented;
    private BlockPos machinePos;
    private long jadeFrames;
    private double jadeFrameMillisTotal;
    private double jadeFrameMillisMax;
    private long lastFrameNanos;
    private boolean usingCopiedWorld;
    private boolean targetedMachinePerformance;
    private volatile boolean targetedWorkingObserved;
    private volatile boolean targetedRecipeTimingObserved;
    private volatile boolean targetedJadeRecipeNodeObserved;
    private int collectTotal = COLLECT_TICKS;
    private boolean synthDone;
    private long synthTick;
    /** 合成场景队列(渲染成本二分:基线/资源条/cap 卡/LCD/计时面板),标题画面逐个开屏测量。 */
    private final List<Map.Entry<String, java.util.function.Supplier<UIElement>>> synthScenes = new ArrayList<>();
    private int synthIndex;
    private String currentSynthName = "?";
    /** 帧渲染成本(RenderFrameEvent Pre→Post,与帧间隔无关,不受限帧/休眠污染)。 */
    private long renderPreNanos;
    private double renderMsTotal;
    private double renderMsMax;
    private long renderFrames;
    private boolean fpsOptionsApplied;
    private int savedFramerateLimit;
    private boolean savedVsync;
    /** 副本世界:按方块类型去重后的待测机器队列(逐台开 GUI)。 */
    private final ArrayDeque<Map.Entry<String, BlockPos>> machineQueue = new ArrayDeque<>();
    private String currentMachineName = "?";
    private volatile long totalDirtyWarnings;
    private long warningsAtPhaseStart;

    private UiPerfProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        boolean generalProbe = Files.exists(FLAG_FILE);
        boolean machineJadePerformance = Files.exists(MACHINE_JADE_PERF_FLAG_FILE);
        if (!generalProbe && !machineJadePerformance) {
            return;
        }
        instance = new UiPerfProbe();
        instance.targetedMachinePerformance = machineJadePerformance;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> instance.renderPreNanos = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> instance.onFrameEnd());
        instance.attachDirtyWarningTap();
        LOGGER.warn("UI perf probe armed: will create a probe world, run a machine and open its GUI");
    }

    /**
     * 在 LowDragLib2 logger 上挂一个进程内 appender:统计每个探针阶段的"dirty for more than
     * 10 times per frame"警告数,并在每个阶段第一次触发时抓取调用栈(定位是哪个 ModularUI 宿主
     * 在跑死循环——容器屏幕 / Jade 桥 / JEI 包装器)。两个客户端共享日志文件,文本日志无法区分
     * 进程;这个 tap 只看本进程。
     */
    private void attachDirtyWarningTap() {
        var ldlibLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger("LowDragLib2");
        AbstractAppender tap = new AbstractAppender(
                "OiUiPerfProbeTap", null, null, true, Property.EMPTY_ARRAY) {

            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (!message.contains("dirty for more than")) {
                    return;
                }
                UiPerfProbe.State current = UiPerfProbe.this.state;
                totalDirtyWarnings++;
                dirtyWarningsByState.merge(current, 1L, Long::sum);
                dirtyWarningStackByState.computeIfAbsent(current, key -> {
                    StringBuilder stack = new StringBuilder();
                    StackTraceElement[] frames = Thread.currentThread().getStackTrace();
                    int copied = 0;
                    for (StackTraceElement frame : frames) {
                        String cls = frame.getClassName();
                        if (cls.startsWith("java.") || cls.startsWith("org.apache.logging") || cls.startsWith("org.slf4j")) {
                            continue;
                        }
                        stack.append("      at ").append(frame).append('\n');
                        if (++copied >= 22) {
                            break;
                        }
                    }
                    return stack.toString();
                });
            }
        };
        tap.start();
        ldlibLogger.addAppender(tap);
    }

    /**
     * 供探针外部宿主(Jade 的 LDLibTooltipElement 等)在构建 ldlib2 树时主动接入监听;
     * 探针未激活时是空操作,正常游戏零开销。外部树进独立统计表,不随 GUI 阶段清空,
     * 最终报告单独列出(定位 Jade 面板内的振荡元素)。
     */
    public static void instrumentExternal(String tag, UIElement root) {
        UiPerfProbe probe = instance;
        if (probe == null || probe.state == State.DONE) {
            return;
        }
        probe.instrumentInto(probe.externalStats, root);
        LOGGER.warn("UI perf probe: instrumented external '{}' tree rooted at {} ({} external elements total)",
                tag, describe(root), probe.externalStats.size());
    }

    /** Records nodes that reached the real decoded Jade timing tree. Normal play exits at the null check. */
    public static void observeJadeTimingNode(String nodeId) {
        UiPerfProbe probe = instance;
        if (probe == null || !probe.targetedMachinePerformance || probe.state == State.DONE) {
            return;
        }
        if (RecipeLogic.RECIPE_LOGIC_1.id().toString().equals(nodeId)) {
            probe.targetedJadeRecipeNodeObserved = true;
        }
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (state) {
            case WAIT_TITLE -> {
                if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    // 探针客户端在后台运行(无焦点),失焦自动暂停会弹 PauseScreen 把 Jade 全程挡住;
                    // 运行期关闭(options.txt 已在启动脚本备份,跑完恢复)。
                    minecraft.options.pauseOnLostFocus = false;
                    applyUncappedFps(minecraft);
                    if (!synthDone) {
                        if (synthScenes.isEmpty()) {
                            buildSynthScenes();
                        }
                        if (synthIndex < synthScenes.size()) {
                            openSynthScene(minecraft, synthScenes.get(synthIndex));
                            return;
                        }
                        synthDone = true;
                    }
                    if (!targetedMachinePerformance && Files.isDirectory(Path.of("saves", COPY_LEVEL_ID))) {
                        // 用户存档副本存在:直接进真实复现环境(出生点即用户机器群)。
                        LOGGER.warn("UI perf probe: opening copied user world '{}'", COPY_LEVEL_ID);
                        usingCopiedWorld = true;
                        minecraft.createWorldOpenFlows().openWorld(COPY_LEVEL_ID, () -> {});
                    } else {
                        LOGGER.warn("UI perf probe: creating flat probe world '{}'", LEVEL_ID);
                        minecraft.createWorldOpenFlows().createFreshLevel(
                                LEVEL_ID,
                                new LevelSettings(
                                        LEVEL_ID,
                                        GameType.CREATIVE,
                                        LevelSettings.DifficultySettings.DEFAULT,
                                        true,
                                        WorldDataConfiguration.DEFAULT),
                                new WorldOptions(20260611L, false, false),
                                WorldPresets::createFlatWorldDimensions,
                                minecraft.screen);
                    }
                    waitTicks = 0;
                    state = State.WAIT_WORLD;
                }
            }
            case SYNTH_COLLECT -> {
                synthTick++;
                if (countdown == collectTotal / 2) {
                    grabScreenshot(minecraft, "oi-probe-synth-" + currentSynthName);
                }
                if (--countdown <= 0) {
                    finishSyntheticPhase(minecraft);
                }
            }
            case WAIT_WORLD -> {
                if (timedOut(minecraft, "waiting for probe world")) {
                    return;
                }
                if (minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null && minecraft.screen == null) {
                    countdown = usingCopiedWorld ? SETTLE_DELAY_TICKS * 3 : SETTLE_DELAY_TICKS;
                    state = State.SETTLE;
                }
            }
            case SETTLE -> {
                if (usingCopiedWorld) {
                    // 副本世界:落地后缓慢自旋一周,让用户的机器群进入视野/准星(Jade、世界渲染、
                    // 计时面板全部按用户实况触发);自旋开始时异步扫描最近的机器。
                    if (machinePos == null && countdown == SETTLE_DELAY_TICKS * 3 - 1) {
                        scanNearestMachine(minecraft);
                    }
                    var player = minecraft.player;
                    if (player != null) {
                        player.setYRot(player.getYRot() + 360f / (SETTLE_DELAY_TICKS * 3));
                    }
                }
                if (--countdown <= 0) {
                    placeMachine(minecraft);
                }
            }
            case PLACE_WAIT -> {
                // 给方块实体一点时间同步到客户端,再请求开 GUI(客户端 createUI 需要本地 BE)。
                // 期间持续喂料让机器运转:产生计时快照 → Jade 计时面板在注视窗口内渲染。
                churnMachine(minecraft);
                if (targetedMachinePerformance && machinePos != null) {
                    aimAt(minecraft, machinePos);
                }
                if (countdown == 5) {
                    grabScreenshot(minecraft, "oi-probe-jade");
                }
                if (--countdown <= 0) {
                    requestGuiOpen(minecraft);
                }
            }
            case WAIT_SCREEN -> {
                if (usingCopiedWorld && ++waitTicks > 100) {
                    // 单台机器 GUI 打不开(如 ME 舱口缺网格):记录后跳过,继续测下一台。
                    report.append(String.format(Locale.ROOT,
                            "=== GUI '%s' SKIPPED: screen did not open (screen=%s)%n%n",
                            currentMachineName,
                            minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName()));
                    minecraft.setScreen(null);
                    countdown = 10;
                    state = State.SETTLE;
                    return;
                }
                if (!usingCopiedWorld && timedOut(minecraft, "waiting for machine GUI screen")) {
                    return;
                }
                if (minecraft.screen instanceof ModularUIContainerScreen screen && !instrumented) {
                    UIElement root = screen.getMenu().getModularUI().ui.rootElement;
                    stats.clear();
                    elementCount = 0;
                    framesObserved = 0;
                    hotFrames = 0;
                    maxFrameEvents = 0;
                    renderFrames = 0;
                    renderMsTotal = 0;
                    renderMsMax = 0;
                    instrument(root);
                    instrumented = true;
                    collectStartNanos = System.nanoTime();
                    collectTotal = usingCopiedWorld ? 120 : COLLECT_TICKS;
                    countdown = collectTotal;
                    warningsAtPhaseStart = totalDirtyWarnings;
                    state = State.COLLECT_GUI;
                    LOGGER.warn("UI perf probe: '{}' GUI open, {} elements instrumented",
                            currentMachineName, elementCount);
                }
            }
            case COLLECT_GUI -> {
                churnMachine(minecraft);
                sweepVirtualMouse(minecraft, collectTotal - countdown);
                if (countdown == collectTotal / 2) {
                    grabScreenshot(minecraft, "oi-probe-gui-" + currentMachineName.replaceAll("[^A-Za-z0-9_-]", "_"));
                }
                if (--countdown <= 0) {
                    finishGuiPhase(minecraft);
                }
            }
            case COLLECT_JADE -> {
                churnMachine(minecraft);
                if (machinePos != null) {
                    aimAt(minecraft, machinePos);
                }
                if (targetedMachinePerformance && countdown == JADE_TICKS / 2) {
                    grabScreenshot(minecraft, "oi-probe-jade");
                }
                if (--countdown <= 0) {
                    finishProbe(minecraft);
                }
            }
            case DONE -> {}
        }
    }

    /** 截屏到 screenshots/<name>.png(覆盖旧文件),供修复后的面板做肉眼验收。 */
    private void grabScreenshot(Minecraft minecraft, String name) {
        try {
            Path target = Path.of("screenshots", name + ".png");
            Files.deleteIfExists(target);
            net.minecraft.client.Screenshot.grab(
                    minecraft.gameDirectory,
                    name + ".png",
                    minecraft.getMainRenderTarget(),
                    1,
                    component -> {});
            LOGGER.warn("UI perf probe: screenshot requested -> {}", target);
        } catch (Exception e) {
            LOGGER.error("UI perf probe: screenshot failed", e);
        }
    }

    private boolean timedOut(Minecraft minecraft, String stage) {
        if (++waitTicks > WAIT_TIMEOUT_TICKS) {
            report.append("TIMEOUT while ").append(stage)
                    .append("; current screen: ")
                    .append(minecraft.screen == null ? "null" : minecraft.screen.getClass().getName())
                    .append('\n');
            finishProbe(minecraft);
            return true;
        }
        return false;
    }

    private void onFrameEnd() {
        long now = System.nanoTime();
        if (renderPreNanos != 0 && (state == State.COLLECT_GUI || state == State.SYNTH_COLLECT || state == State.COLLECT_JADE)) {
            double renderMs = (now - renderPreNanos) / 1.0e6;
            renderFrames++;
            renderMsTotal += renderMs;
            if (renderMs > renderMsMax) {
                renderMsMax = renderMs;
            }
        }
        for (ElementStat stat : externalStats.values()) {
            stat.frameEnded();
        }
        if (state == State.COLLECT_GUI || state == State.SYNTH_COLLECT) {
            framesObserved++;
            long frameEvents = 0;
            for (ElementStat stat : stats.values()) {
                frameEvents += stat.eventsThisFrame;
                stat.frameEnded();
            }
            if (frameEvents > 50) {
                hotFrames++;
            }
            if (frameEvents > maxFrameEvents) {
                maxFrameEvents = frameEvents;
            }
        } else if (state == State.COLLECT_JADE) {
            if (lastFrameNanos != 0) {
                double millis = (now - lastFrameNanos) / 1.0e6;
                jadeFrames++;
                jadeFrameMillisTotal += millis;
                if (millis > jadeFrameMillisMax) {
                    jadeFrameMillisMax = millis;
                }
            }
            long frameEvents = 0;
            for (ElementStat stat : stats.values()) {
                frameEvents += stat.eventsThisFrame;
                stat.frameEnded();
            }
            if (frameEvents > 50) {
                hotFrames++;
            }
            if (frameEvents > maxFrameEvents) {
                maxFrameEvents = frameEvents;
            }
        }
        lastFrameNanos = now;
    }

    /** 解除帧率限制(上限 260=不限、关垂直同步),让渲染成本测量不被限帧/休眠污染。运行期设置,不落盘。 */
    private void applyUncappedFps(Minecraft minecraft) {
        if (fpsOptionsApplied) {
            return;
        }
        fpsOptionsApplied = true;
        savedFramerateLimit = minecraft.options.framerateLimit().get();
        savedVsync = minecraft.options.enableVsync().get();
        minecraft.options.framerateLimit().set(260);
        minecraft.options.enableVsync().set(false);
        minecraft.getWindow().updateVsync(false);
        LOGGER.warn("UI perf probe: fps uncapped for render-cost measurement (was limit={}, vsync={})",
                savedFramerateLimit, savedVsync);
    }

    private void restoreFpsOptions(Minecraft minecraft) {
        if (!fpsOptionsApplied) {
            return;
        }
        minecraft.options.framerateLimit().set(savedFramerateLimit);
        minecraft.options.enableVsync().set(savedVsync);
        minecraft.getWindow().updateVsync(savedVsync);
    }

    /**
     * 合成场景清单(渲染成本二分):同一屏幕宿主下分别渲染 基线空卡/4×资源条/4×side-IO cap 卡/
     * 2×实况 LCD/计时面板,逐场景测帧渲染耗时——哪类控件贵一目了然。cap 卡用脱离世界的
     * 电锅炉端口构建,与真实 GUI 左栏卡片同构。
     */
    private void buildSynthScenes() {
        synthScenes.add(Map.entry("baseline", (java.util.function.Supplier<UIElement>) () -> {
            UIElement card = MachineUiContainerTemplate.INSTANCE.createCardContent(
                    MachineUiComponentTemplate.INSTANCE.createStaticText(
                            net.minecraft.network.chat.Component.literal("baseline card")));
            UIElement root = new UIElement();
            root.addChild(card);
            return root;
        }));
        synthScenes.add(Map.entry("bars-x4", () -> {
            UIElement column = column();
            for (int i = 0; i < 4; i++) {
                column.addChild(MachineUiComponentTemplate.INSTANCE
                        .createResourceBar(
                                net.minecraft.network.chat.Component.literal("Bar " + i),
                                0xFFFFD64F,
                                net.ptcrys.topo.apiv2.machine.ui.ResourceBar.Orientation.HORIZONTAL)
                        .bindLocal(() -> 37_000L + synthTick % 1000, () -> 100_000L));
            }
            return column;
        }));
        synthScenes.add(Map.entry("sideio-cards-x4", () -> {
            var machine = net.ptcrys.topo.apiv2.machine.Machines.createBlockEntity(
                    BlockPos.ZERO, BuiltinOIMachines.RESISTIVE_HEATER_T1.registeredBlock().getDefaultState());
            var input = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
            var output = machine.machineComponents().require(ScalarResourcePort.HEAT_OUTPUT_1);
            UIElement column = column();
            for (int i = 0; i < 4; i++) {
                var port = i % 2 == 0 ? input : output;
                column.addChild(MachineUiContainerTemplate.INSTANCE.createCard(
                        net.ptcrys.topo.apiv2.machine.ui.SideIoConfigGrid.createTitleBar(port),
                        net.ptcrys.topo.apiv2.machine.ui.SideIoConfigGrid.create(port)));
            }
            return column;
        }));
        synthScenes.add(Map.entry("lcd-x2", () -> {
            UIElement column = column();
            for (int i = 0; i < 2; i++) {
                LcdData lcd = MachineUiContainerTemplate.INSTANCE.createLcdData(LcdData.Orientation.VERTICAL);
                for (int row = 0; row < 3; row++) {
                    long seed = 13L + row * 7L;
                    lcd.addLocalBoundEntry(
                            net.minecraft.network.chat.Component.literal("entry_" + row),
                            () -> net.minecraft.network.chat.Component.literal(
                                    (synthTick * seed % 997) + "/" + 997),
                            0xFF55FF55);
                }
                column.addChild(MachineUiContainerTemplate.INSTANCE.createCardContent(lcd));
            }
            return column;
        }));
        synthScenes.add(Map.entry("lcd-minfloor-x2", () -> {
            // lcd-x2 的对照组:同样逐 tick 变值,但值列带 minValueWidth 下限(机器 GUI 配方状态
            // 卡的防横跳修复)。预期 recent 几何恒定,与上一场景的呼吸宽度形成 A/B 取证。
            UIElement column = column();
            for (int i = 0; i < 2; i++) {
                LcdData lcd = MachineUiContainerTemplate.INSTANCE
                        .createLcdData(LcdData.Orientation.VERTICAL)
                        .minValueWidth(42f);
                for (int row = 0; row < 3; row++) {
                    long seed = 13L + row * 7L;
                    lcd.addLocalBoundEntry(
                            net.minecraft.network.chat.Component.literal("entry_" + row),
                            () -> net.minecraft.network.chat.Component.literal(
                                    (synthTick * seed % 997) + "/" + 997),
                            0xFF55FF55);
                }
                column.addChild(MachineUiContainerTemplate.INSTANCE.createCardContent(lcd));
            }
            return column;
        }));
        synthScenes.add(Map.entry("timing-panel", () -> {
            LcdData panel = MachineUiContainerTemplate.INSTANCE
                    .createLcdData(LcdData.Orientation.VERTICAL)
                    .pinColumns(104f, 124f);
            for (int i = 0; i < 9; i++) {
                long rowSeed = 37L + i * 91L;
                panel.addLocalBoundEntry(
                        net.minecraft.network.chat.Component.literal("  row_" + i + ".synthetic"),
                        () -> net.minecraft.network.chat.Component.literal(
                                (synthTick * rowSeed % 1000) + "." + (synthTick * 7 % 1000) + " us " + (synthTick % 100) + ".0% lv-passive interval=" + (1 + synthTick % 4)),
                        0xFF55FF55);
            }
            UIElement root = new UIElement();
            root.addChild(panel);
            return root;
        }));
    }

    private static UIElement column() {
        UIElement column = new UIElement();
        column.setId("oi_probe_synth_column");
        column.layout(layout -> {
            layout.flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN);
            layout.gapRow(8f);
        });
        return column;
    }

    private void openSynthScene(
                                Minecraft minecraft, Map.Entry<String, java.util.function.Supplier<UIElement>> scene) {
        currentSynthName = scene.getKey();
        UIElement root = scene.getValue().get();
        root.setId("oi_probe_synth_root_" + currentSynthName);

        stats.clear();
        elementCount = 0;
        framesObserved = 0;
        hotFrames = 0;
        maxFrameEvents = 0;
        renderFrames = 0;
        renderMsTotal = 0;
        renderMsMax = 0;
        instrument(root);
        ModularUI ui = ModularUI.of(UI.of(
                root,
                StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)));
        minecraft.setScreen(new ModularUIScreen(
                ui, net.minecraft.network.chat.Component.literal("oi synth " + currentSynthName)));
        collectStartNanos = System.nanoTime();
        warningsAtPhaseStart = totalDirtyWarnings;
        collectTotal = 100;
        countdown = collectTotal;
        state = State.SYNTH_COLLECT;
        LOGGER.warn("UI perf probe: synth scene '{}' open, {} elements instrumented",
                currentSynthName, elementCount);
    }

    private void finishSyntheticPhase(Minecraft minecraft) {
        double seconds = (System.nanoTime() - collectStartNanos) / 1.0e9;
        long totalEvents = stats.values().stream().mapToLong(stat -> stat.events).sum();
        long warningsDuring = totalDirtyWarnings - warningsAtPhaseStart;
        report.append(String.format(Locale.ROOT,
                "=== SYNTH '%s' | elements=%d | duration=%.1fs | frames=%d | renderAvg=%.2fms | renderMax=%.2fms | hotFrames=%d | maxEventsPerFrame=%d | dirtyWarnings=%d | layoutChangedEvents=%d%n",
                currentSynthName, elementCount, seconds, framesObserved,
                renderFrames == 0 ? 0 : renderMsTotal / renderFrames, renderMsMax,
                hotFrames, maxFrameEvents, warningsDuring, totalEvents));
        stats.values().stream()
                .filter(stat -> stat.events > 1)
                .sorted(Comparator.comparingLong((ElementStat stat) -> stat.events).reversed())
                .limit(8)
                .forEach(stat -> report.append(String.format(Locale.ROOT,
                        "  events=%-6d maxPerFrame=%-4d %s%n    recent: %s%n",
                        stat.events, stat.maxEventsPerFrame, stat.label,
                        String.join(" -> ", stat.recentGeometry))));
        report.append('\n');
        stats.clear();
        minecraft.setScreen(new TitleScreen());
        synthIndex++;
        state = State.WAIT_TITLE;
    }

    /** 副本世界:扫描玩家周边 5x5 区块内的全部机器方块实体,按方块类型去重生成待测队列。 */
    private void scanNearestMachine(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        if (server == null || player == null) {
            return;
        }
        BlockPos origin = player.blockPosition();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            Map<String, BlockPos> byType = new LinkedHashMap<>();
            StringBuilder inventory = new StringBuilder();
            int chunkX = origin.getX() >> 4;
            int chunkZ = origin.getZ() >> 4;
            for (int cx = chunkX - 2; cx <= chunkX + 2; cx++) {
                for (int cz = chunkZ - 2; cz <= chunkZ + 2; cz++) {
                    var chunk = level.getChunk(cx, cz);
                    for (var entry : chunk.getBlockEntities().entrySet()) {
                        if (!(entry.getValue() instanceof MachineBlockEntity machine)) {
                            continue;
                        }
                        BlockPos pos = entry.getKey();
                        String name = machine.getBlockState().getBlock().getName().getString();
                        inventory.append("  machine ").append(name).append(" at ").append(pos).append('\n');
                        byType.putIfAbsent(name, pos.immutable());
                    }
                }
            }
            machineQueue.clear();
            machineQueue.addAll(byType.entrySet());
            report.append("=== machines near user spawn ===\n");
            report.append(inventory.isEmpty() ? "  none found within 5x5 chunks\n" : inventory.toString());
            report.append(String.format(Locale.ROOT,
                    "=== will open %d unique machine GUIs ===%n%n", machineQueue.size()));
            LOGGER.warn("UI perf probe: {} unique machine types queued", machineQueue.size());
        });
    }

    /** 把视角对准目标方块(客户端本地设置 yaw/pitch,Jade 即按准星目标渲染)。 */
    private static void aimAt(Minecraft minecraft, BlockPos pos) {
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - (player.getY() + player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private void placeMachine(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            report.append("FAILED: integrated server missing after world load\n");
            finishProbe(minecraft);
            return;
        }
        if (usingCopiedWorld) {
            var next = machineQueue.poll();
            if (next == null) {
                report.append("no (more) machines to open\n");
                finishProbe(minecraft);
                return;
            }
            currentMachineName = next.getKey();
            machinePos = next.getValue();
            aimAt(minecraft, machinePos);
            countdown = 40;
            state = State.PLACE_WAIT;
            LOGGER.warn("UI perf probe: next machine '{}' at {}", currentMachineName, machinePos);
            return;
        }
        if (targetedMachinePerformance) {
            placeTargetedCombustionGenerator(server);
            countdown = 40;
            state = State.PLACE_WAIT;
            return;
        }
        currentMachineName = "electric_boiler(probe)";
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().relative(player.getDirection(), 2).above();
                level.setBlock(base, BuiltinOIMachines.RESISTIVE_HEATER_T1.registeredBlock().getDefaultState(), 3);
                machinePos = base;
                if (level.getBlockEntity(base) instanceof MachineBlockEntity machine) {
                    fillEnergy(machine, 60_000);
                }
                LOGGER.warn("UI perf probe: electric boiler placed at {}", base);
            } catch (Exception e) {
                LOGGER.error("UI perf probe: server-side machine placement failed", e);
                report.append("FAILED server placement: ").append(e).append('\n');
            }
        });
        countdown = 20;
        state = State.PLACE_WAIT;
    }

    private void placeTargetedCombustionGenerator(net.minecraft.client.server.IntegratedServer server) {
        currentMachineName = "combustion_generator_t1(probe)";
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().relative(player.getDirection(), 2).above();
                // The broad UI sweep may already have placed the same machine at this coordinate.
                // Re-applying an identical block state keeps its BlockEntity (including fuel and
                // rolling timing history), making this targeted scene depend on an earlier phase.
                // Remove it first so fuel, monitoring warmup, and the Jade screenshot are repeatable.
                level.removeBlock(base, false);
                level.setBlock(
                        base,
                        BuiltinOIMachines.COMBUSTION_GENERATOR_T1.registeredBlock().getDefaultState(),
                        3);
                machinePos = base;
                if (!(level.getBlockEntity(base) instanceof MachineBlockEntity machine)) {
                    throw new IllegalStateException("combustion generator block entity was not created");
                }
                insertCoal(machine, 64);
                machine.activatePerformanceMonitoring(COLLECT_TICKS + JADE_TICKS + 200);
                LOGGER.warn("UI perf probe: targeted combustion generator placed and fueled at {}", base);
            } catch (Exception e) {
                LOGGER.error("UI perf probe: targeted generator placement failed", e);
                report.append("FAILED targeted generator placement: ").append(e).append('\n');
            }
        });
    }

    private void requestGuiOpen(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || machinePos == null) {
            report.append("FAILED: server or machine missing before GUI open\n");
            finishProbe(minecraft);
            return;
        }
        BlockPos pos = machinePos;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            BlockUIMenuType.openUI(player, pos);
            LOGGER.warn("UI perf probe: GUI open requested for {}", pos);
        });
        waitTicks = 0;
        state = State.WAIT_SCREEN;
    }

    /**
     * 虚拟鼠标扫掠:经反射写 MouseHandler 的 xpos/ypos(dev 环境 mojmap 字段名),让真实的
     * hitTest/__hovered__/tooltip 管线随光标移动反复触发——复刻用户在 GUI 里移动鼠标的行为。
     * 前半程沿屏幕中线水平扫(穿过左侧 cap 卡、主区、右侧 LCD),后半程沿中竖线垂直扫
     * (穿过页签、进度条、资源条、物品栏)。每 5 tick 走一步,在元素上有停留时间。
     */
    private void sweepVirtualMouse(Minecraft minecraft, int elapsedTicks) {
        if (elapsedTicks % 5 != 0) {
            return;
        }
        var window = minecraft.getWindow();
        int guiWidth = window.getGuiScaledWidth();
        int guiHeight = window.getGuiScaledHeight();
        int half = COLLECT_TICKS / 2;
        double guiX;
        double guiY;
        if (elapsedTicks < half) {
            double t = elapsedTicks / (double) half;
            guiX = guiWidth * (0.08 + 0.84 * t);
            guiY = guiHeight * 0.45;
        } else {
            double t = (elapsedTicks - half) / (double) half;
            guiX = guiWidth * 0.5;
            guiY = guiHeight * (0.08 + 0.84 * t);
        }
        try {
            double xpos = guiX * window.getScreenWidth() / guiWidth;
            double ypos = guiY * window.getScreenHeight() / guiHeight;
            var handler = minecraft.mouseHandler;
            var xField = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var yField = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xField.setAccessible(true);
            yField.setAccessible(true);
            xField.setDouble(handler, xpos);
            yField.setDouble(handler, ypos);
        } catch (ReflectiveOperationException e) {
            if (elapsedTicks == 0) {
                LOGGER.error("UI perf probe: virtual mouse sweep unavailable", e);
                report.append("virtual mouse sweep unavailable: ").append(e).append('\n');
            }
        }
    }

    /**
     * 周期性补能量/抽热量,保证配方持续运转、数值每 tick 变化(贴近用户实况)。机器没有对应
     * 标量端口时静默跳过——副本世界逐台测真机,运转中的机器才会产生计时快照,Jade 计时面板
     * 才会出现(这正是要验证的面板)。
     */
    private void churnMachine(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || machinePos == null) {
            return;
        }
        BlockPos pos = machinePos;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
                return;
            }
            if (targetedMachinePerformance) {
                drainEnergyOutput(machine);
                observeTargetedMachine(machine);
                return;
            }
            try {
                fillEnergy(machine, 40);
            } catch (Exception ignored) {
                // 无能量输入端口的机器(纯物品/流体舱口等):跳过。
            }
            try {
                drainHeat(machine, 60);
            } catch (Exception ignored) {
                // 无热量输出端口:跳过。
            }
        });
    }

    private void observeTargetedMachine(MachineBlockEntity machine) {
        machine.activatePerformanceMonitoring(COLLECT_TICKS + JADE_TICKS + 200);
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        if (logic.state() == RecipeLogic.State.WORKING) {
            targetedWorkingObserved = true;
        }
        if (!machine.publishRecentPerformanceSnapshot(machine.performanceSampleMaxAgeTicks())) {
            return;
        }
        MachinePerformanceSnapshot snapshot = machine.lastPerformanceSnapshot();
        String recipeLogicId = RecipeLogic.RECIPE_LOGIC_1.id().toString();
        for (MachinePerformanceSnapshot.ComponentSample sample : snapshot.components()) {
            if (recipeLogicId.equals(sample.id()) && (sample.nanos() > 0L || sample.avgNanos() > 0L || sample.peakNanos() > 0L)) {
                targetedRecipeTimingObserved = true;
                return;
            }
        }
    }

    private static void insertCoal(MachineBlockEntity machine, int count) {
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = storage.handler().insert(ItemResource.of(Items.COAL), count, transaction);
            if (inserted != count) {
                throw new IllegalStateException("expected to insert " + count + " coal, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void drainEnergyOutput(MachineBlockEntity machine) {
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            storage.handler().extract(storage.resource(), Integer.MAX_VALUE, transaction);
            transaction.commit();
        }
    }

    private static void fillEnergy(MachineBlockEntity machine, int amount) {
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            storage.handler().insert(
                    BuiltinOIResourceIntegrations.ENERGY.recipeCapability().resource(), amount, transaction);
            transaction.commit();
        }
    }

    private static void drainHeat(MachineBlockEntity machine, int amount) {
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.HEAT_OUTPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            storage.handler().extract(
                    BuiltinOIResourceIntegrations.HEAT.recipeCapability().resource(), amount, transaction);
            transaction.commit();
        }
    }

    private void instrument(UIElement element) {
        elementCount++;
        instrumentInto(stats, element);
        for (UIElement child : element.getSafeChildren()) {
            instrument(child);
        }
    }

    private void instrumentInto(Map<UIElement, ElementStat> target, UIElement element) {
        ElementStat stat = new ElementStat(describe(element));
        target.put(element, stat);
        element.addEventListener(UIEvents.LAYOUT_CHANGED, event -> stat.record(element));
        if (target == externalStats) {
            for (UIElement child : element.getSafeChildren()) {
                instrumentInto(target, child);
            }
        }
    }

    private static String describe(UIElement element) {
        List<String> path = new ArrayList<>();
        UIElement current = element;
        int depth = 0;
        while (current != null && depth < 5) {
            String id = current.getId();
            path.addFirst((id == null || id.isBlank() ? current.getClass().getSimpleName() : id));
            current = current.getParent();
            depth++;
        }
        return String.join("/", path) + "@" + Integer.toHexString(System.identityHashCode(element));
    }

    private void finishGuiPhase(Minecraft minecraft) {
        double seconds = (System.nanoTime() - collectStartNanos) / 1.0e9;
        long totalEvents = stats.values().stream().mapToLong(stat -> stat.events).sum();
        long warningsDuring = totalDirtyWarnings - warningsAtPhaseStart;

        report.append(String.format(Locale.ROOT,
                "=== GUI '%s' | elements=%d | duration=%.1fs | frames=%d | renderAvg=%.2fms | renderMax=%.2fms | hotFrames(>50ev)=%d | maxEventsPerFrame=%d | dirtyWarnings=%d | layoutChangedEvents=%d (%.0f/s)%n",
                currentMachineName, elementCount, seconds, framesObserved,
                renderFrames == 0 ? 0 : renderMsTotal / renderFrames, renderMsMax,
                hotFrames, maxFrameEvents,
                warningsDuring, totalEvents, totalEvents / seconds));
        stats.values().stream()
                .filter(stat -> stat.events > 0)
                .sorted(Comparator.comparingLong((ElementStat stat) -> stat.events).reversed())
                .limit(warningsDuring > 0 ? TOP_OFFENDERS : 6)
                .forEach(stat -> report.append(String.format(Locale.ROOT,
                        "  events=%-6d maxPerFrame=%-4d %s%n    recent: %s%n",
                        stat.events, stat.maxEventsPerFrame, stat.label,
                        String.join(" -> ", stat.recentGeometry))));
        report.append('\n');

        minecraft.setScreen(null);
        instrumented = false;
        if (usingCopiedWorld && !machineQueue.isEmpty()) {
            // 队列未空:稍候转下一台机器。
            stats.clear();
            countdown = 20;
            state = State.SETTLE;
            return;
        }

        // 关 GUI,转 Jade 注视阶段(准星对着机器,统计帧耗时 + Jade ldlib2 树的元素级监听)。
        stats.clear();
        elementCount = 0;
        hotFrames = 0;
        maxFrameEvents = 0;
        jadeFrames = 0;
        jadeFrameMillisTotal = 0;
        jadeFrameMillisMax = 0;
        lastFrameNanos = 0;
        countdown = JADE_TICKS;
        state = State.COLLECT_JADE;
    }

    private void finishProbe(Minecraft minecraft) {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        if (jadeFrames > 0) {
            long totalEvents = stats.values().stream().mapToLong(stat -> stat.events).sum();
            report.append(String.format(Locale.ROOT,
                    "=== Jade phase (crosshair on machine, GUI closed) | instrumented=%d | frames=%d | hotFrames(>50ev)=%d | maxEventsPerFrame=%d | layoutChangedEvents=%d | avgFrame=%.2fms | maxFrame=%.2fms%n",
                    stats.size(), jadeFrames, hotFrames, maxFrameEvents, totalEvents,
                    jadeFrameMillisTotal / jadeFrames, jadeFrameMillisMax));
            stats.values().stream()
                    .filter(stat -> stat.events > 0)
                    .sorted(Comparator.comparingLong((ElementStat stat) -> stat.events).reversed())
                    .limit(TOP_OFFENDERS)
                    .forEach(stat -> report.append(String.format(Locale.ROOT,
                            "  events=%-6d maxPerFrame=%-4d %s%n    recent: %s%n",
                            stat.events, stat.maxEventsPerFrame, stat.label,
                            String.join(" -> ", stat.recentGeometry))));
            report.append('\n');
        }
        if (!externalStats.isEmpty()) {
            long totalExternal = externalStats.values().stream().mapToLong(stat -> stat.events).sum();
            report.append(String.format(Locale.ROOT,
                    "=== external (Jade) ldlib2 trees | elements=%d | layoutChangedEvents=%d ===%n",
                    externalStats.size(), totalExternal));
            externalStats.values().stream()
                    .filter(stat -> stat.events > 0)
                    .sorted(Comparator.comparingLong((ElementStat stat) -> stat.events).reversed())
                    .limit(TOP_OFFENDERS)
                    .forEach(stat -> report.append(String.format(Locale.ROOT,
                            "  events=%-6d maxPerFrame=%-4d %s%n    recent: %s%n",
                            stat.events, stat.maxEventsPerFrame, stat.label,
                            String.join(" -> ", stat.recentGeometry))));
            report.append('\n');
        }
        if (targetedMachinePerformance) {
            report.append("=== targeted machine/Jade assertions ===\n");
            report.append("  combustion generator WORKING observed: ")
                    .append(targetedWorkingObserved)
                    .append('\n');
            report.append("  RecipeLogic server timing observed: ")
                    .append(targetedRecipeTimingObserved)
                    .append('\n');
            report.append("  RecipeLogic node decoded by Jade: ")
                    .append(targetedJadeRecipeNodeObserved)
                    .append('\n');
            report.append("  Jade render frames observed: ").append(jadeFrames).append('\n');
            if (!targetedWorkingObserved) {
                report.append("FAILED targeted combustion generator never reached WORKING\n");
            }
            if (!targetedRecipeTimingObserved) {
                report.append("FAILED targeted RecipeLogic timing never appeared in a server snapshot\n");
            }
            if (!targetedJadeRecipeNodeObserved) {
                report.append("FAILED Jade never decoded the targeted RecipeLogic timing node\n");
            }
            if (jadeFrames <= 0) {
                report.append("FAILED targeted Jade render phase observed no frames\n");
            }
            report.append('\n');
        }
        report.append("=== in-process LDLib2 dirty-loop warnings by probe phase ===\n");
        if (dirtyWarningsByState.isEmpty()) {
            report.append("  none\n");
        } else {
            dirtyWarningsByState.forEach((phase, count) -> {
                report.append(String.format(Locale.ROOT, "  %s: %d warnings%n", phase, count));
                String stack = dirtyWarningStackByState.get(phase);
                if (stack != null) {
                    report.append("    first stack:\n").append(stack);
                }
            });
        }
        boolean failed = report.indexOf("FAILED") >= 0 || report.indexOf("timeout ") >= 0;
        report.append(failed ? "RESULT FAIL\n" : "RESULT PASS\n");
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
            Files.deleteIfExists(MACHINE_JADE_PERF_FLAG_FILE);
        } catch (IOException e) {
            LOGGER.error("UI perf probe: failed to write report", e);
        }
        restoreFpsOptions(minecraft);
        LOGGER.warn("UI perf probe finished, report at {}", REPORT_FILE.toAbsolutePath());
        minecraft.stop();
    }
}
