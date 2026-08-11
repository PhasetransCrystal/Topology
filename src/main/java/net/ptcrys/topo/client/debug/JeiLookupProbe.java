package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.ui.recipe.XeiRecipeLookup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.mojang.datafixers.util.Either;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 进度条点击查配方全自动游戏内验证探针(同 {@link TooltipProbe} 的流水线骨架,独立 flag):
 * 仅当工作目录存在 {@code oi-jei-lookup-probe.flag} 时激活;自动建平坦世界,放一台指定机器,
 * 经 {@link BlockUIMenuType#openUI} 打开真实机器 UI,等 JEI runtime 就绪(插座可用),在元素树
 * 里按 id 定位 {@code oi_recipe_progress_bar},虚拟鼠标悬停截图(肉眼验收 tooltip),再经
 * {@code Screen.mouseClicked} 走真实点击管线;CHECK 断言当前屏切到 JEI 配方屏(类名 mezz.jei
 * 前缀)并截图;随后在 JEI 屏内沿配方区网格扫描,借 GatherComponents 命中非空物品即停在该槽,
 * 截图物品 tooltip 并断言:模组名行唯一(gather 层恰一条 + Jade 物品模组名功能已被 mods.toml
 * 元数据关停)、面板元素紧贴物品名(index 1,压在模组名行之上),报告写
 * {@code oi-jei-lookup-probe-report.txt} 后自动退出。
 */
public final class JeiLookupProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("OI-JeiLookupProbe");
    private static final Path FLAG_FILE = Path.of("oi-jei-lookup-probe.flag");
    private static final Path REPORT_FILE = Path.of("oi-jei-lookup-probe-report.txt");
    /** 每轮唯一世界名:复用同名存档会撞上一轮的残留场景(并行会话共用 run/ 时尤甚)。 */
    private static final String LEVEL_ID = "oi-jei-lookup-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;
    private static final String PROGRESS_BAR_ID = "oi_recipe_progress_bar";

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        PLACE_WAIT,
        WAIT_SCREEN,
        WAIT_JEI_RUNTIME,
        HOVER,
        VERIFY,
        SWEEP_ITEM,
        ITEM_TOOLTIP,
        FLUSH,
        DONE
    }

    private static JeiLookupProbe instance;

    private final StringBuilder report = new StringBuilder();
    /** 被测机器 id path:flag 文件内容给出,空文件回落到精细研磨机(任何带配方 UI 的机器都可测)。 */
    private final String machineId;
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private @Nullable BlockPos machinePos;
    private double clickGuiX;
    private double clickGuiY;
    /** JEI 配方屏无元素树可查:屏幕中心锚定、槽位带优先向外的网格扫描点序列与游标。 */
    private final List<double[]> sweepPoints = new ArrayList<>();
    private int sweepIndex;
    private double sweepHoverX;
    private double sweepHoverY;
    /** 上一帧渲染期间 GatherComponents 是否带非空物品开火(=虚拟鼠标正悬浮物品槽)。 */
    private boolean gatherHitSeen;
    private @Nullable ItemStack hoveredStack;
    /** gather 层元素全序快照:文字行存 getString,面板等组件存 "[panel 类名]" 标记。 */
    private final List<String> gatheredTooltipShape = new ArrayList<>();

    private JeiLookupProbe(String machineId) {
        this.machineId = machineId;
    }

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        String machineId = "fine_grinder_t1";
        try {
            String configured = Files.readString(FLAG_FILE, StandardCharsets.UTF_8).trim();
            if (!configured.isEmpty()) {
                machineId = configured;
            }
        } catch (IOException ignored) {
            // 读不动 flag 内容就按缺省机器跑。
        }
        instance = new JeiLookupProbe(machineId);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        // LOWEST:务必在我们注入器(NORMAL 优先级)之后开火,捕获的元素序才是最终渲染序——
        // 否则会在面板插入前快照,误报 panel index = -1。
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                (RenderTooltipEvent.GatherComponents event) -> instance.onGatherTooltip(event));
        LOGGER.warn("JEI lookup probe armed: will click the '{}' progress bar and expect the JEI recipes screen", machineId);
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (state) {
            case WAIT_TITLE -> {
                if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    minecraft.options.pauseOnLostFocus = false;
                    if (minecraft.getWindow().getWidth() < 1280) {
                        minecraft.getWindow().setWindowed(1280, 720);
                    }
                    minecraft.createWorldOpenFlows().createFreshLevel(
                            LEVEL_ID,
                            new LevelSettings(
                                    LEVEL_ID,
                                    GameType.SURVIVAL,
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
                    countdown = 30;
                    state = State.PLACE_WAIT;
                }
            }
            case PLACE_WAIT -> {
                if (countdown == 20) {
                    minecraft.options.hideGui = true;
                    report.append("HUD hidden for unobstructed machine screenshot\n");
                }
                if (countdown == 15) {
                    grabScreenshot(minecraft, "oi-jei-lookup-probe-machine");
                }
                if (countdown == 10) {
                    minecraft.options.hideGui = false;
                }
                if (--countdown <= 0) {
                    requestGuiOpen(minecraft);
                    waitTicks = 0;
                    state = State.WAIT_SCREEN;
                }
            }
            case WAIT_SCREEN -> {
                if (timedOut(minecraft, "waiting for machine GUI screen")) {
                    return;
                }
                if (minecraft.screen instanceof ModularUIContainerScreen) {
                    report.append("machine GUI open: ").append(minecraft.screen.getClass().getSimpleName())
                            .append('\n');
                    waitTicks = 0;
                    state = State.WAIT_JEI_RUNTIME;
                }
            }
            case WAIT_JEI_RUNTIME -> {
                // JEI 世界加载后异步建 runtime;插座可用前点击只会静默 false,tooltip 也不显示。
                if (timedOut(minecraft, "waiting for the JEI runtime plug")) {
                    return;
                }
                if (XeiRecipeLookup.isAvailable()) {
                    report.append("XeiRecipeLookup available after ").append(waitTicks).append(" ticks\n");
                    if (!locateProgressBar(minecraft)) {
                        report.append("progress bar element '").append(PROGRESS_BAR_ID)
                                .append("' not found in the element tree -> FAIL\n");
                        state = State.FLUSH;
                        countdown = 5;
                        return;
                    }
                    hoverAt(minecraft, clickGuiX, clickGuiY);
                    countdown = 25;
                    state = State.HOVER;
                }
            }
            case HOVER -> {
                hoverAt(minecraft, clickGuiX, clickGuiY);
                if (countdown == 5) {
                    grabScreenshot(minecraft, "oi-jei-lookup-probe-hover");
                }
                if (--countdown <= 0) {
                    dispatchClick(minecraft);
                    waitTicks = 0;
                    state = State.VERIFY;
                }
            }
            case VERIFY -> {
                var screen = minecraft.screen;
                if (screen != null && screen.getClass().getName().startsWith("mezz.jei")) {
                    report.append("JEI screen open: ").append(screen.getClass().getName()).append(" -> PASS\n");
                    grabScreenshot(minecraft, "oi-jei-lookup-probe-jei-screen");
                    buildSweepGrid(minecraft);
                    state = State.SWEEP_ITEM;
                    return;
                }
                if (++waitTicks > 60) {
                    report.append("screen after click: ")
                            .append(screen == null ? "null" : screen.getClass().getName())
                            .append(" (expected mezz.jei.* recipes gui) -> FAIL\n");
                    grabScreenshot(minecraft, "oi-jei-lookup-probe-fail");
                    countdown = 5;
                    state = State.FLUSH;
                }
            }
            case SWEEP_ITEM -> {
                if (gatherHitSeen) {
                    report.append(String.format(Locale.ROOT,
                            "item slot hit at (%.0f, %.0f): %s%n", sweepHoverX, sweepHoverY, describeHoveredStack()));
                    countdown = 20;
                    state = State.ITEM_TOOLTIP;
                    return;
                }
                if (sweepIndex >= sweepPoints.size()) {
                    report.append("sweep exhausted without hitting an item slot -> FAIL\n");
                    grabScreenshot(minecraft, "oi-jei-lookup-probe-sweep-fail");
                    countdown = 5;
                    state = State.FLUSH;
                    return;
                }
                double[] point = sweepPoints.get(sweepIndex++);
                sweepHoverX = point[0];
                sweepHoverY = point[1];
                hoverAt(minecraft, sweepHoverX, sweepHoverY);
            }
            case ITEM_TOOLTIP -> {
                // 停在命中槽位让 tooltip 持续渲染;截图供肉眼验收,再按 gather 快照断言模组名行唯一。
                hoverAt(minecraft, sweepHoverX, sweepHoverY);
                if (countdown == 5) {
                    grabScreenshot(minecraft, "oi-jei-lookup-probe-item-tooltip");
                }
                if (--countdown <= 0) {
                    assertSingleModNameLine();
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
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        MachineDefinition definition = Machines.registered().stream()
                .filter(candidate -> candidate.id().getPath().equals(machineId))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            report.append("FAILED: unknown machine id '").append(machineId).append("' -> FAIL\n");
            finish(minecraft);
            return;
        }
        report.append("machine selected: ").append(definition.id()).append('\n');
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().relative(player.getDirection(), 2).above();
                level.setBlock(base, definition.registeredBlock().getDefaultState(), 3);
                machinePos = base;
                LOGGER.warn("JEI lookup probe: '{}' placed at {}", machineId, base);
            } catch (Exception e) {
                LOGGER.error("JEI lookup probe: server-side machine placement failed", e);
                report.append("FAILED server placement: ").append(e).append('\n');
            }
        });
    }

    private void requestGuiOpen(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || machinePos == null) {
            report.append("FAILED: server or machine missing before GUI open -> FAIL\n");
            finish(minecraft);
            return;
        }
        BlockPos pos = machinePos;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            BlockUIMenuType.openUI(player, pos);
            LOGGER.warn("JEI lookup probe: GUI open requested for {}", pos);
        });
    }

    /** 在元素树里按 id 找进度条,记录其中心 GUI 坐标;找不到返回 false。 */
    private boolean locateProgressBar(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ModularUIContainerScreen screen)) {
            return false;
        }
        UIElement bar = findById(screen.getMenu().getModularUI().ui.rootElement, PROGRESS_BAR_ID);
        if (bar == null) {
            return false;
        }
        clickGuiX = bar.getPositionX() + bar.getSizeWidth() / 2.0;
        clickGuiY = bar.getPositionY() + bar.getSizeHeight() / 2.0;
        report.append(String.format(Locale.ROOT,
                "progress bar rect: x=%.2f y=%.2f w=%.2f h=%.2f -> click at (%.2f, %.2f)%n",
                bar.getPositionX(), bar.getPositionY(), bar.getSizeWidth(), bar.getSizeHeight(),
                clickGuiX, clickGuiY));
        return true;
    }

    /** JEI 配方屏不是 ModularUI 元素树,槽位坐标拿不到:以屏幕中心为锚、配方槽位带的行优先向外网格扫。 */
    private void buildSweepGrid(Minecraft minecraft) {
        sweepPoints.clear();
        sweepIndex = 0;
        double centerX = minecraft.getWindow().getGuiScaledWidth() / 2.0;
        double centerY = minecraft.getWindow().getGuiScaledHeight() / 2.0;
        int[] rowOffsets = { 0, 12, -12, 24, -24, 36, -36, 48, -48, 60, -60, 72, -72, 84 };
        for (int rowOffset : rowOffsets) {
            for (int columnOffset = -80; columnOffset <= 88; columnOffset += 8) {
                sweepPoints.add(new double[] { centerX + columnOffset, centerY + rowOffset });
            }
        }
    }

    /** 扫描/停驻期间记录最后一次带非空物品的 tooltip gather:命中信号 + 文字行快照(渲染前的最终文字序)。 */
    private void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        if (state != State.SWEEP_ITEM && state != State.ITEM_TOOLTIP) {
            return;
        }
        if (event.getItemStack().isEmpty()) {
            return;
        }
        gatherHitSeen = true;
        hoveredStack = event.getItemStack();
        gatheredTooltipShape.clear();
        for (Either<FormattedText, TooltipComponent> element : event.getTooltipElements()) {
            element.left().ifPresent(text -> gatheredTooltipShape.add(text.getString()));
            element.right().ifPresent(component -> gatheredTooltipShape.add("[panel " + component.getClass().getSimpleName() + "]"));
        }
    }

    /**
     * 验收"模组名只渲染一条":gather 层文字行里物品所属模组的显示名必须恰好一行(JEI 自己加的那条),
     * 且 Jade 的物品模组名功能必须已被我们 mods.toml 的 jade 元数据声明关停(Jade 在 gather 之后的
     * ClientTooltipComponent 层追加,gather 快照看不见它,只能查它的总开关)。两断言合并封死第二条。
     */
    private void assertSingleModNameLine() {
        ItemStack stack = hoveredStack;
        if (stack == null) {
            report.append("no hovered stack captured -> FAIL\n");
            return;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        String displayName = ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
        // JEI 的模组名行是 §-格式码字符串拼接(modNameFormat),先剥格式码再比对。
        long modNameLines = gatheredTooltipShape.stream()
                .map(net.minecraft.ChatFormatting::stripFormatting)
                .filter(displayName::equals)
                .count();
        report.append("gathered tooltip shape: ").append(gatheredTooltipShape).append('\n');
        report.append("mod-name line ('").append(displayName).append("') count at gather layer: ")
                .append(modNameLines).append(modNameLines == 1 ? " -> PASS\n" : " -> FAIL\n");
        int panelIndex = -1;
        for (int i = 0; i < gatheredTooltipShape.size(); i++) {
            if (gatheredTooltipShape.get(i).startsWith("[panel")) {
                panelIndex = i;
                break;
            }
        }
        report.append("panel element index: ").append(panelIndex)
                .append(panelIndex == 1 ? " (directly under the item name) -> PASS\n" : " (expected 1; -1 = no panel element) -> FAIL\n");
        try {
            boolean jadeAppends = snownee.jade.api.config.IWailaConfig.get().general().showItemModNameTooltip();
            report.append("Jade showItemModNameTooltip: ").append(jadeAppends)
                    .append(jadeAppends ? " (Jade would append a second mod-name line) -> FAIL\n" : " -> PASS\n");
        } catch (Throwable jadeAbsent) {
            report.append("Jade config unavailable (").append(jadeAbsent.getClass().getSimpleName())
                    .append("), Jade assertion skipped\n");
        }
    }

    private String describeHoveredStack() {
        ItemStack stack = hoveredStack;
        if (stack == null) {
            return "<none>";
        }
        return stack.getHoverName().getString() + " (" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ")";
    }

    private static @Nullable UIElement findById(UIElement element, String id) {
        if (id.equals(element.getId())) {
            return element;
        }
        for (UIElement child : element.getSafeChildren()) {
            UIElement found = findById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 虚拟鼠标移到 GUI 坐标(写 MouseHandler 实位置,让真实 hover/tooltip 管线触发)。 */
    private void hoverAt(Minecraft minecraft, double guiX, double guiY) {
        double scaleX = (double) minecraft.getWindow().getScreenWidth() / minecraft.getWindow().getGuiScaledWidth();
        double scaleY = (double) minecraft.getWindow().getScreenHeight() / minecraft.getWindow().getGuiScaledHeight();
        setMousePosition(minecraft.mouseHandler, guiX * scaleX, guiY * scaleY);
    }

    /** dev 环境 mojmap 字段名直写;探针专用,生产零调用。 */
    private void setMousePosition(MouseHandler handler, double x, double y) {
        try {
            Field xField = MouseHandler.class.getDeclaredField("xpos");
            Field yField = MouseHandler.class.getDeclaredField("ypos");
            xField.setAccessible(true);
            yField.setAccessible(true);
            xField.setDouble(handler, x);
            yField.setDouble(handler, y);
        } catch (ReflectiveOperationException exception) {
            report.append("virtual mouse failed: ").append(exception).append(" -> FAIL\n");
        }
    }

    /** 走真实 Screen 点击管线(mouseClicked/mouseReleased),与玩家左键完全同路。 */
    private void dispatchClick(Minecraft minecraft) {
        var screen = minecraft.screen;
        if (screen == null) {
            report.append("FAILED: machine screen vanished before the click -> FAIL\n");
            return;
        }
        report.append(String.format(Locale.ROOT, "clicking at (%.2f, %.2f)%n", clickGuiX, clickGuiY));
        MouseButtonEvent click = new MouseButtonEvent(clickGuiX, clickGuiY, new MouseButtonInfo(0, 0));
        screen.mouseClicked(click, false);
        screen.mouseReleased(click);
    }

    private void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), 1, c -> {});
        report.append("screenshot ").append(name).append(".png captured\n");
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
        minecraft.options.hideGui = false;
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException exception) {
            LOGGER.error("JEI lookup probe failed to flush its report", exception);
        }
        LOGGER.warn("JEI lookup probe done:\n{}", report);
        minecraft.stop();
    }
}
