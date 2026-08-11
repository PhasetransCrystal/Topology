package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.api.machine.ui.PortUiHighlight;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * side-IO 卡标题"端口命名 + 悬浮高亮"全自动游戏内验证探针(同 {@link JeiLookupProbe} 的
 * 流水线骨架,独立 flag):仅当工作目录存在 {@code topo-port-highlight-probe.flag} 时激活;
 * 自动建平坦世界,放一台锻压机,经 {@link BlockUIMenuType#openUI} 打开真实机器 UI,逐张
 * 定位目标卡(模具槽/能量)的图标标题栏,虚拟鼠标悬停:断言标题悬浮首行是端口名(模具槽
 * 显示专名 "Die Slot",未命名端口显示"资源 角色"拼接)、该端口全部登记元素已套高亮覆盖层,
 * 截图肉眼验收;再把鼠标移开,断言覆盖层全部还原。报告写
 * {@code topo-port-highlight-probe-report.txt} 后自动退出。
 */
public final class PortHighlightProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Topo-PortHighlightProbe");
    private static final Path FLAG_FILE = Path.of("topo-port-highlight-probe.flag");
    private static final Path REPORT_FILE = Path.of("topo-port-highlight-probe-report.txt");
    private static final Path LAYOUT_FLAG_FILE = Path.of("topo-machine-ui-layout-probe.flag");
    private static final Path LAYOUT_REPORT_FILE = Path.of("topo-machine-ui-layout-probe-report.txt");
    private static final Path SEARCH_POOL_FLAG_FILE = Path.of("topo-single-machine-search-pool-probe.flag");
    private static final Path SEARCH_POOL_REPORT_FILE = Path.of("topo-single-machine-search-pool-probe-report.txt");
    /** 每轮唯一世界名:复用同名存档会撞上一轮的残留场景(并行会话共用 run/ 时尤甚)。 */
    private static final String LEVEL_ID = "topo-port-highlight-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;
    private static final String CARD_ID = "machine_ui_component";
    private static final String CARD_TITLE_ID = "topo_side_io_card_title";
    private static final String MAIN_ID = "machine_ui_main";
    private static final String LEFT_COLUMN_ID = "machine_ui_left";
    private static final String SEARCH_POOL_ID = "topo_search_pool";
    private static final String SIDE_IO_PACKED_ID = "topo_side_io_packed";
    private static final String SIDE_IO_FACE_ID = "topo_side_io_face_up";
    private static final float GEOMETRY_EPSILON = 0.01f;

    private static final List<String> EVAPORATOR_SIDE_IO_IDS = List.of(
            "topo_side_io_item_input_1",
            "topo_side_io_item_output_1",
            "topo_side_io_fluid_input_1",
            "topo_side_io_fluid_output_1",
            "topo_side_io_heat_input_1");

    /** 一个验收目标:side-IO 卡的内容元素 id(= sink key)、端口 id、期望的标题悬浮首行。 */
    private record Target(String shot, String gridId, Identifier portId, Component expectedName) {}

    /** 锻压机的两张代表卡:命名端口(模具槽,有物品槽)+ 未命名标量端口(能量,资源条)。 */
    private static final List<Target> TARGETS = List.of(
            new Target(
                    "die-card",
                    "topo_side_io_item_die_1",
                    IdHelper.oi("item_die_1"),
                    traitName("item_die_1", "Die Slot")),
            new Target(
                    "energy-card",
                    "topo_side_io_energy_input_1",
                    IdHelper.oi("energy_input_1"),
                    traitName(
                            "energy_input_1",
                            "%s %s",
                            Component.translatableWithFallback(
                                    IdHelper.oi("energy").toLanguageKey("resource"), "Energy"),
                            Component.translatableWithFallback(
                                    "ui.topo.side_io.title.insert", "Input"))));

    private enum ProbeMode {
        PORT_HIGHLIGHT,
        MACHINE_UI_LAYOUT,
        SINGLE_MACHINE_SEARCH_POOL
    }

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        PLACE_WAIT,
        WAIT_SCREEN,
        SETTLE,
        SIDE_IO_CLICK,
        HOVER,
        LEAVE,
        FLUSH,
        DONE
    }

    private static PortHighlightProbe instance;

    private final StringBuilder report = new StringBuilder();
    private final ProbeMode mode;
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private int targetIndex;
    private int sideIoPackedBefore;
    private boolean sideIoClickArmed;
    private @Nullable BlockPos machinePos;
    private boolean layoutFailed;
    private boolean searchPoolFailed;

    private PortHighlightProbe(ProbeMode mode) {
        this.mode = mode;
    }

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        ProbeMode mode;
        if (Files.exists(LAYOUT_FLAG_FILE)) {
            mode = ProbeMode.MACHINE_UI_LAYOUT;
        } else if (Files.exists(SEARCH_POOL_FLAG_FILE)) {
            mode = ProbeMode.SINGLE_MACHINE_SEARCH_POOL;
        } else if (Files.exists(FLAG_FILE)) {
            mode = ProbeMode.PORT_HIGHLIGHT;
        } else {
            return;
        }
        instance = new PortHighlightProbe(mode);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        if (mode == ProbeMode.MACHINE_UI_LAYOUT) {
            LOGGER.warn("Machine UI layout probe armed: will validate the evaporator T3 card geometry");
        } else if (mode == ProbeMode.SINGLE_MACHINE_SEARCH_POOL) {
            LOGGER.warn("Single-machine search-pool probe armed: will validate the component processor T3 UI");
        } else {
            LOGGER.warn("Port highlight probe armed: will hover the forming press side-IO card titles and verify naming + slot highlight");
        }
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
                            new WorldOptions(20260613L, false, false),
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
                    countdown = 10;
                    state = State.SETTLE;
                }
            }
            case SETTLE -> {
                if (mode == ProbeMode.SINGLE_MACHINE_SEARCH_POOL) {
                    if (--countdown > 0) {
                        return;
                    }
                    checkSingleMachineSearchPoolHidden(minecraft);
                    grabScreenshot(minecraft, "topo-single-machine-search-pool");
                    countdown = 10;
                    state = State.FLUSH;
                    return;
                }
                if (mode == ProbeMode.MACHINE_UI_LAYOUT) {
                    if (--countdown > 0) {
                        return;
                    }
                    try {
                        checkMachineUiLayout(minecraft);
                    } catch (Exception exception) {
                        LOGGER.error("Machine UI layout probe assertion failed unexpectedly", exception);
                        layoutAssertion("layout check completed without exception", false);
                        report.append("layout check exception: ").append(exception).append('\n');
                    }
                    grabScreenshot(minecraft, "topo-machine-ui-layout-evaporator-t3");
                    countdown = 10;
                    state = State.FLUSH;
                    return;
                }
                // 留几个渲染帧给布局/样式引擎物化,卡片几何稳定后再定位标题。
                if (--countdown <= 0) {
                    if (!armSideIoButton(minecraft)) {
                        countdown = 5;
                        state = State.FLUSH;
                        return;
                    }
                    countdown = 3;
                    state = State.SIDE_IO_CLICK;
                }
            }
            case SIDE_IO_CLICK -> {
                if (--countdown > 0) {
                    return;
                }
                dispatchSideIoButtonClick(minecraft);
                targetIndex = 0;
                if (!beginHover(minecraft)) {
                    countdown = 5;
                    state = State.FLUSH;
                    return;
                }
                countdown = 25;
                state = State.HOVER;
            }
            case HOVER -> {
                Target target = TARGETS.get(targetIndex);
                UIElement title = locateCardTitle(minecraft, target);
                if (title != null) {
                    hoverAt(minecraft, titleCenterX(title), titleCenterY(title));
                }
                if (countdown == 10) {
                    if (targetIndex == 0) {
                        checkSideIoAuthoritativeReply(minecraft);
                    }
                    checkHovering(minecraft, target);
                }
                if (countdown == 5) {
                    grabScreenshot(minecraft, "topo-port-highlight-probe-" + target.shot());
                }
                if (--countdown <= 0) {
                    if (++targetIndex < TARGETS.size()) {
                        if (!beginHover(minecraft)) {
                            countdown = 5;
                            state = State.FLUSH;
                            return;
                        }
                        countdown = 25;
                    } else {
                        countdown = 15;
                        state = State.LEAVE;
                    }
                }
            }
            case LEAVE -> {
                // 鼠标挪到窗口左上角(任何卡片之外),全部端口的高亮应当还原。
                hoverAt(minecraft, 1.0, 1.0);
                if (countdown == 5) {
                    checkRestored(minecraft);
                    grabScreenshot(minecraft, "topo-port-highlight-probe-restored");
                }
                if (--countdown <= 0) {
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

    private boolean beginHover(Minecraft minecraft) {
        Target target = TARGETS.get(targetIndex);
        UIElement title = locateCardTitle(minecraft, target);
        if (title == null) {
            report.append("card title for ").append(target.gridId()).append(" not found -> FAIL\n");
            return false;
        }
        report.append(String.format(Locale.ROOT,
                "%s title rect: x=%.2f y=%.2f w=%.2f h=%.2f%n",
                target.shot(), title.getPositionX(), title.getPositionY(),
                title.getSizeWidth(), title.getSizeHeight()));
        return true;
    }

    /** 内容元素(id = sink key)先定位到卡片容器,再取卡内的图标标题栏——标题 id 全卡同名,不能全局找。 */
    private @Nullable UIElement locateCardTitle(Minecraft minecraft, Target target) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            return null;
        }
        UIElement grid = findById(root, target.gridId());
        if (grid == null) {
            return null;
        }
        UIElement card = grid.getParent();
        while (card != null && !CARD_ID.equals(card.getId())) {
            card = card.getParent();
        }
        return card == null ? null : findById(card, CARD_TITLE_ID);
    }

    private void checkHovering(Minecraft minecraft, Target target) {
        UIElement root = rootElement(minecraft);
        UIElement title = root == null ? null : locateCardTitle(minecraft, target);
        if (root == null || title == null) {
            report.append(target.shot()).append(": element tree unavailable while hovering -> FAIL\n");
            return;
        }

        Component firstTooltip = title.getStyle().tooltips().asList().stream().findFirst().orElse(null);
        String shownName = firstTooltip == null ? "<no tooltip>" : firstTooltip.getString();
        String expectedName = target.expectedName().getString();
        report.append(target.shot()).append(" hover title line: '").append(shownName).append("' (expect '")
                .append(expectedName).append("')")
                .append(expectedName.equals(shownName) ? " -> PASS" : " -> FAIL")
                .append('\n');

        List<UIElement> highlightTargets = PortUiHighlight.findTargets(root, target.portId());
        long highlighted = highlightTargets.stream()
                .filter(element -> PortUiHighlight.inlineOverlay(element) == MachineUiComponentStyle.INSTANCE.getPortHighlightOverlayTexture())
                .count();
        report.append(target.shot()).append(" highlighted port elements: ").append(highlighted)
                .append('/').append(highlightTargets.size())
                .append(!highlightTargets.isEmpty() && highlighted == highlightTargets.size() ? " -> PASS" : " -> FAIL")
                .append('\n');
    }

    private void checkRestored(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            report.append("element tree unavailable after leaving -> FAIL\n");
            return;
        }
        for (Target target : TARGETS) {
            long stillHighlighted = PortUiHighlight.findTargets(root, target.portId()).stream()
                    .filter(element -> PortUiHighlight.inlineOverlay(element) == MachineUiComponentStyle.INSTANCE.getPortHighlightOverlayTexture())
                    .count();
            report.append(target.shot()).append(" highlights after leave: ").append(stillHighlighted)
                    .append(stillHighlighted == 0 ? " -> PASS" : " -> FAIL")
                    .append('\n');
        }
    }

    private void checkMachineUiLayout(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        if (!(minecraft.screen instanceof ModularUIContainerScreen screen) || root == null) {
            layoutAssertion("machine UI tree available", false);
            return;
        }

        float viewportWidth = screen.getMenu().getModularUI().getScreenWidth();
        float viewportHeight = screen.getMenu().getModularUI().getScreenHeight();
        report.append(String.format(Locale.ROOT,
                "viewport: x=0.00 y=0.00 w=%.2f h=%.2f%n", viewportWidth, viewportHeight));

        for (String id : EVAPORATOR_SIDE_IO_IDS) {
            UIElement sideIo = findById(root, id);
            boolean inLeftCard = sideIo != null && isEffectivelyDisplayed(sideIo) && hasAncestor(sideIo, LEFT_COLUMN_ID) && hasAncestor(sideIo, CARD_ID);
            layoutAssertion(id + " exists and is displayed in " + LEFT_COLUMN_ID, inLeftCard);
        }

        UIElement main = findById(root, MAIN_ID);
        layoutAssertion(MAIN_ID + " exists", main != null);
        if (main != null) {
            reportRect("main", MAIN_ID, main);
        }

        List<UIElement> displayedCards = new java.util.ArrayList<>();
        collectDisplayedById(root, CARD_ID, displayedCards);
        layoutAssertion("displayed " + CARD_ID + " count >= 5", displayedCards.size() >= 5);

        boolean allInsideViewport = true;
        for (int index = 0; index < displayedCards.size(); index++) {
            UIElement card = displayedCards.get(index);
            String label = cardLabel(card);
            reportRect("card[" + index + "]", label, card);
            if (!isInsideViewport(card, viewportWidth, viewportHeight)) {
                report.append("  outside viewport: card[").append(index).append("] ")
                        .append(label).append('\n');
                allInsideViewport = false;
            }
        }
        layoutAssertion("all displayed cards fully inside viewport", allInsideViewport);

        boolean cardsDisjoint = true;
        for (int first = 0; first < displayedCards.size(); first++) {
            for (int second = first + 1; second < displayedCards.size(); second++) {
                if (overlaps(displayedCards.get(first), displayedCards.get(second))) {
                    report.append("  overlap: card[").append(first).append("] and card[")
                            .append(second).append("]\n");
                    cardsDisjoint = false;
                }
            }
        }
        layoutAssertion("displayed cards are pairwise non-overlapping", cardsDisjoint);

        boolean cardsClearMain = main != null;
        if (main != null) {
            for (int index = 0; index < displayedCards.size(); index++) {
                if (overlaps(displayedCards.get(index), main)) {
                    report.append("  overlap: card[").append(index).append("] and ")
                            .append(MAIN_ID).append('\n');
                    cardsClearMain = false;
                }
            }
        }
        layoutAssertion("displayed cards do not overlap " + MAIN_ID, cardsClearMain);
        report.append(UiGeometryDump.dump(root));
    }

    private void layoutAssertion(String assertion, boolean passed) {
        report.append(assertion).append(": ")
                .append(passed ? "PASS" : "FAIL")
                .append('\n');
        layoutFailed |= !passed;
    }

    private static boolean hasAncestor(UIElement element, String id) {
        for (UIElement current = element; current != null; current = current.getParent()) {
            if (id.equals(current.getId())) {
                return true;
            }
        }
        return false;
    }

    private static void collectDisplayedById(UIElement element, String id, List<UIElement> matches) {
        if (id.equals(element.getId()) && isEffectivelyDisplayed(element)) {
            matches.add(element);
        }
        for (UIElement child : element.getSafeChildren()) {
            collectDisplayedById(child, id, matches);
        }
    }

    private static boolean isEffectivelyDisplayed(UIElement element) {
        for (UIElement current = element; current != null; current = current.getParent()) {
            if (!current.isDisplayed() || !current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInsideViewport(UIElement element, float viewportWidth, float viewportHeight) {
        float x = element.getPositionX();
        float y = element.getPositionY();
        return x >= -GEOMETRY_EPSILON && y >= -GEOMETRY_EPSILON && x + element.getSizeWidth() <= viewportWidth + GEOMETRY_EPSILON && y + element.getSizeHeight() <= viewportHeight + GEOMETRY_EPSILON;
    }

    private static boolean overlaps(UIElement first, UIElement second) {
        return first.getPositionX() < second.getPositionX() + second.getSizeWidth() - GEOMETRY_EPSILON && first.getPositionX() + first.getSizeWidth() > second.getPositionX() + GEOMETRY_EPSILON && first.getPositionY() < second.getPositionY() + second.getSizeHeight() - GEOMETRY_EPSILON && first.getPositionY() + first.getSizeHeight() > second.getPositionY() + GEOMETRY_EPSILON;
    }

    private void reportRect(String kind, String label, UIElement element) {
        report.append(String.format(Locale.ROOT,
                "%s %s: x=%.2f y=%.2f w=%.2f h=%.2f%n",
                kind, label, element.getPositionX(), element.getPositionY(),
                element.getSizeWidth(), element.getSizeHeight()));
    }

    private static String cardLabel(UIElement card) {
        for (String sideIoId : EVAPORATOR_SIDE_IO_IDS) {
            if (findById(card, sideIoId) != null) {
                return sideIoId;
            }
        }
        return CARD_ID;
    }

    private void checkSingleMachineSearchPoolHidden(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            report.append("single-machine UI tree unavailable -> FAIL\n");
            searchPoolFailed = true;
            return;
        }
        UIElement searchPool = findById(root, SEARCH_POOL_ID);
        searchPoolFailed = searchPool != null;
        report.append("single-machine search-pool panel absent: ")
                .append(searchPool == null ? "yes -> PASS" : "no -> FAIL")
                .append('\n');
    }

    private boolean armSideIoButton(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        UIElement grid = root == null ? null : findById(root, TARGETS.getFirst().gridId());
        UIElement packed = grid == null ? null : findById(grid, SIDE_IO_PACKED_ID);
        UIElement face = grid == null ? null : findById(grid, SIDE_IO_FACE_ID);
        if (!(packed instanceof BindableValue<?> mirror) || !(mirror.getValue() instanceof Integer before) || face == null || minecraft.screen == null) {
            report.append("side-IO authoritative click prerequisites missing -> FAIL\n");
            return false;
        }
        sideIoPackedBefore = before;
        sideIoClickArmed = true;
        double x = face.getPositionX() + face.getSizeWidth() / 2.0;
        double y = face.getPositionY() + face.getSizeHeight() / 2.0;
        hoverAt(minecraft, x, y);
        report.append(String.format(Locale.ROOT, "side-IO face click armed at (%.2f, %.2f)%n", x, y));
        return true;
    }

    private void dispatchSideIoButtonClick(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        UIElement grid = root == null ? null : findById(root, TARGETS.getFirst().gridId());
        UIElement face = grid == null ? null : findById(grid, SIDE_IO_FACE_ID);
        if (root == null || face == null || minecraft.screen == null) {
            report.append("side-IO face vanished before Screen click -> FAIL\n");
            return;
        }
        double x = face.getPositionX() + face.getSizeWidth() / 2.0;
        double y = face.getPositionY() + face.getSizeHeight() / 2.0;
        UIElement hovered = root.getModularUI() == null ? null : root.getModularUI().getLastHoveredElement();
        MouseButtonEvent click = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        boolean consumed = minecraft.screen.mouseClicked(click, false);
        minecraft.screen.mouseReleased(click);
        boolean correctTarget = hovered != null && SIDE_IO_FACE_ID.equals(hovered.getId());
        report.append("side-IO face click dispatched through Screen mouse pipeline: hovered=")
                .append(hovered == null ? "<none>" : hovered.getId())
                .append(" consumed=")
                .append(consumed)
                .append(consumed && correctTarget ? " -> PASS" : " -> FAIL")
                .append('\n');
    }

    private void checkSideIoAuthoritativeReply(Minecraft minecraft) {
        if (!sideIoClickArmed) {
            return;
        }
        UIElement root = rootElement(minecraft);
        UIElement grid = root == null ? null : findById(root, TARGETS.getFirst().gridId());
        UIElement packed = grid == null ? null : findById(grid, SIDE_IO_PACKED_ID);
        Integer after = packed instanceof BindableValue<?> mirror && mirror.getValue() instanceof Integer value ? value : null;
        report.append("side-IO authoritative packed reply: ")
                .append(sideIoPackedBefore)
                .append(" -> ")
                .append(after)
                .append(after != null && after != sideIoPackedBefore ? " -> PASS" : " -> FAIL")
                .append('\n');
        sideIoClickArmed = false;
    }

    private static Component traitName(String path, String fallback, Object... args) {
        return Component.translatableWithFallback(IdHelper.oi(path).toLanguageKey("trait"), fallback, args);
    }

    private static @Nullable UIElement rootElement(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ModularUIContainerScreen screen)) {
            return null;
        }
        return screen.getMenu().getModularUI().ui.rootElement;
    }

    private static double titleCenterX(UIElement title) {
        return title.getPositionX() + title.getSizeWidth() / 2.0;
    }

    private static double titleCenterY(UIElement title) {
        return title.getPositionY() + title.getSizeHeight() / 2.0;
    }

    private void placeMachine(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        MachineDefinition definition;
        if (mode == ProbeMode.MACHINE_UI_LAYOUT) {
            definition = BuiltinTopoMachines.EVAPORATOR_T3;
        } else if (mode == ProbeMode.SINGLE_MACHINE_SEARCH_POOL) {
            definition = BuiltinTopoMachines.COMPONENT_PROCESSOR_T3;
        } else {
            // Built-in machine ids are tier-qualified (component_processor_tN). Keep the probe
            // pinned to one deterministic definition instead of searching for the obsolete
            // unsuffixed registry path.
            definition = BuiltinTopoMachines.COMPONENT_PROCESSOR_T3;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().relative(player.getDirection(), 2).above();
                level.setBlock(base, definition.registeredBlock().getDefaultState(), 3);
                machinePos = base;
                LOGGER.warn("Port highlight probe: component processor placed at {}", base);
            } catch (Exception e) {
                LOGGER.error("Port highlight probe: server-side machine placement failed", e);
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
            LOGGER.warn("Port highlight probe: GUI open requested for {}", pos);
        });
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

    /** 虚拟鼠标移到 GUI 坐标(写 MouseHandler 实位置,让真实 hover 管线触发)。 */
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
        Path reportFile = switch (mode) {
            case PORT_HIGHLIGHT -> REPORT_FILE;
            case MACHINE_UI_LAYOUT -> LAYOUT_REPORT_FILE;
            case SINGLE_MACHINE_SEARCH_POOL -> SEARCH_POOL_REPORT_FILE;
        };
        Path flagFile = switch (mode) {
            case PORT_HIGHLIGHT -> FLAG_FILE;
            case MACHINE_UI_LAYOUT -> LAYOUT_FLAG_FILE;
            case SINGLE_MACHINE_SEARCH_POOL -> SEARCH_POOL_FLAG_FILE;
        };
        if (mode == ProbeMode.MACHINE_UI_LAYOUT) {
            boolean failed = layoutFailed || report.indexOf("FAIL") >= 0 || report.indexOf("timeout") >= 0;
            report.append("RESULT ").append(failed ? "FAIL" : "PASS").append('\n');
        } else if (mode == ProbeMode.SINGLE_MACHINE_SEARCH_POOL) {
            boolean failed = searchPoolFailed || report.indexOf("FAIL") >= 0 || report.indexOf("timeout") >= 0;
            report.append("RESULT ").append(failed ? "FAIL" : "PASS").append('\n');
        } else {
            boolean failed = report.indexOf("FAIL") >= 0 || report.indexOf("timeout") >= 0;
            report.append("RESULT ").append(failed ? "FAIL" : "PASS").append('\n');
        }
        try {
            Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("Port highlight probe failed to flush its report", exception);
        }
        try {
            Files.deleteIfExists(flagFile);
        } catch (IOException exception) {
            LOGGER.error("Port highlight probe failed to delete its flag", exception);
        }
        LOGGER.warn("Port highlight probe done:\n{}", report);
        minecraft.stop();
    }
}
