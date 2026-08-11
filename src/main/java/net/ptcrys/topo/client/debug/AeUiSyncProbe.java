package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.data.machine.BuiltinTopoMeMachines;
import net.ptcrys.topo.integration.ae2.AePatternProvider;
import net.ptcrys.topo.integration.ae2.ui.AeBufferPageUi;
import net.ptcrys.topo.integration.ae2.ui.AeConfigPageUi;
import net.ptcrys.topo.integration.ae2.ui.AePatternProviderUi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flag-driven real-client validation for the three AE machine UIs. The probe opens a fresh
 * container for each machine, checks the final LDLib2 tree, captures screenshots, and exercises
 * the pattern provider's validated separated-mode RPC through the normal screen click pipeline.
 */
public final class AeUiSyncProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Topo-AeUiSyncProbe");
    private static final Path FLAG_FILE = Path.of("topo-ae-ui-sync-probe.flag");
    private static final Path REPORT_FILE = Path.of("topo-ae-ui-sync-probe-report.txt");
    private static final String LEVEL_ID = "topo-ae-ui-sync-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;

    private static final String CONFIG_PAGE_ID = AeConfigPageUi.PAGE_KEY;
    private static final String CONFIG_TARGET_SYNC_ID = "topo_ae_config_target_sync";
    private static final String CONFIG_STOCK_SYNC_ID = "topo_ae_stock_amount_sync";
    private static final String BUFFER_PAGE_ID = AeBufferPageUi.PAGE_KEY;
    private static final String BUFFER_AMOUNT_SYNC_ID = "topo_ae_buffer_amount_sync";
    private static final String PATTERN_PAGE_ID = AePatternProviderUi.PAGE_KEY;
    private static final String PATTERN_SEPARATED_SYNC_ID = "topo_ae_pattern_separated_chrome";
    private static final String PATTERN_POOL_IDS_SYNC_ID = "topo_ae_pattern_slot_pool_ids_chrome";
    private static final String PATTERN_SEPARATED_BUTTON_ID = "topo_ae_pattern_provider_separated";
    private static final String SEARCH_POOL_PANEL_ID = "topo_search_pool";
    private static final String SEARCH_POOL_VISIBILITY_SYNC_ID = "topo_side_card_live_topo_search_pool";

    private static final MachineDefinition[] DEFINITIONS = {
            BuiltinTopoMeMachines.ME_DIRECT_ITEM_INPUT_BUS,
            BuiltinTopoMeMachines.ME_EXPORT_HATCH,
            BuiltinTopoMeMachines.ME_PATTERN_PROVIDER
    };

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        WAIT_SCENE,
        OPEN_DELAY,
        WAIT_SCREEN,
        SETTLE_SCREEN,
        PATTERN_HOVER,
        PATTERN_WAIT_REPLY,
        WAIT_SERVER_CHECK,
        CLOSE_SCREEN,
        BETWEEN_SCREENS,
        FLUSH,
        DONE
    }

    private static @Nullable AeUiSyncProbe instance;

    private final StringBuilder report = new StringBuilder();
    private final BlockPos[] machinePositions = new BlockPos[DEFINITIONS.length];
    private State state = State.WAIT_TITLE;
    private int machineIndex;
    private int countdown;
    private int waitTicks;
    private boolean failed;
    private volatile boolean sceneReady;
    private volatile @Nullable String sceneError;
    private volatile @Nullable String openError;
    private volatile boolean serverCheckComplete;
    private volatile boolean serverSeparated;
    private volatile @Nullable String serverCheckError;
    private double clickGuiX;
    private double clickGuiY;
    private @Nullable UIElement patternSearchPanelBefore;
    private @Nullable UIElement patternVisibilitySyncBefore;

    private AeUiSyncProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new AeUiSyncProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("AE UI sync probe armed");
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            switch (state) {
                case WAIT_TITLE -> waitForTitle(minecraft);
                case WAIT_WORLD -> waitForWorld(minecraft);
                case WAIT_SCENE -> waitForScene();
                case OPEN_DELAY -> openAfterDelay(minecraft);
                case WAIT_SCREEN -> waitForMachineScreen(minecraft);
                case SETTLE_SCREEN -> settleAndValidate(minecraft);
                case PATTERN_HOVER -> hoverAndClickSeparated(minecraft);
                case PATTERN_WAIT_REPLY -> waitForSeparatedReply(minecraft);
                case WAIT_SERVER_CHECK -> waitForServerCheck(minecraft);
                case CLOSE_SCREEN -> closeAfterScreenshot(minecraft);
                case BETWEEN_SCREENS -> waitBetweenScreens(minecraft);
                case FLUSH -> flushAfterDelay(minecraft);
                case DONE -> {}
            }
        } catch (Throwable throwable) {
            LOGGER.error("AE UI sync probe failed unexpectedly", throwable);
            failure("unexpected " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            finish(minecraft);
        }
    }

    private void waitForTitle(Minecraft minecraft) {
        if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.getWindow().getWidth() < 1280 || minecraft.getWindow().getHeight() < 720) {
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
                new WorldOptions(20260710L, false, false),
                WorldPresets::createFlatWorldDimensions,
                minecraft.screen);
        waitTicks = 0;
        state = State.WAIT_WORLD;
    }

    private void waitForWorld(Minecraft minecraft) {
        if (timedOut(minecraft, "waiting for probe world")) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null || minecraft.screen != null) {
            return;
        }
        buildScene(minecraft);
        waitTicks = 0;
        state = State.WAIT_SCENE;
    }

    private void waitForScene() {
        if (++waitTicks > WAIT_TIMEOUT_TICKS) {
            failure("timeout waiting for server-side AE machine placement");
            finish(Minecraft.getInstance());
            return;
        }
        if (sceneReady) {
            assertion("three AE machines placed", sceneError == null);
            if (sceneError != null) {
                report.append("placement error: ").append(sceneError).append('\n');
                finish(Minecraft.getInstance());
                return;
            }
            countdown = 30;
            state = State.OPEN_DELAY;
        }
    }

    private void openAfterDelay(Minecraft minecraft) {
        if (--countdown > 0) {
            return;
        }
        requestGuiOpen(minecraft);
        waitTicks = 0;
        state = State.WAIT_SCREEN;
    }

    private void waitForMachineScreen(Minecraft minecraft) {
        if (openError != null) {
            failure("server-side GUI open failed: " + openError);
            finish(minecraft);
            return;
        }
        if (timedOut(minecraft, "waiting for machine " + machineIndex + " UI")) {
            return;
        }
        if (!(minecraft.screen instanceof ModularUIContainerScreen)) {
            return;
        }
        report.append("machine[")
                .append(machineIndex)
                .append("] GUI open: ")
                .append(minecraft.screen.getClass().getSimpleName())
                .append("\n");
        countdown = 25;
        state = State.SETTLE_SCREEN;
    }

    private void settleAndValidate(Minecraft minecraft) {
        if (--countdown > 0) {
            return;
        }
        UIElement root = rootElement(minecraft);
        assertion("machine[" + machineIndex + "] final ModularUI tree available", root != null);
        if (root == null) {
            countdown = 5;
            state = State.FLUSH;
            return;
        }
        switch (machineIndex) {
            case 0 -> {
                validateConfigPage(root);
                grabScreenshot(minecraft, "topo-ae-ui-sync-config");
                countdown = 10;
                state = State.CLOSE_SCREEN;
            }
            case 1 -> {
                validateBufferPage(root);
                grabScreenshot(minecraft, "topo-ae-ui-sync-buffer");
                countdown = 10;
                state = State.CLOSE_SCREEN;
            }
            case 2 -> {
                validatePatternPageBeforeClick(root);
                grabScreenshot(minecraft, "topo-ae-ui-sync-pattern-shared");
                UIElement button = findById(root, PATTERN_SEPARATED_BUTTON_ID);
                if (button == null || !isEffectivelyDisplayed(button)) {
                    failure("separated button is unavailable for real click");
                    countdown = 10;
                    state = State.FLUSH;
                    return;
                }
                clickGuiX = button.getPositionX() + button.getSizeWidth() / 2.0;
                clickGuiY = button.getPositionY() + button.getSizeHeight() / 2.0;
                assertion("separated button center is inside the GUI viewport", isInsideViewport(minecraft, clickGuiX, clickGuiY));
                hoverAt(minecraft, clickGuiX, clickGuiY);
                countdown = 8;
                state = State.PATTERN_HOVER;
            }
            default -> throw new IllegalStateException("Unexpected machine index " + machineIndex);
        }
    }

    private void hoverAndClickSeparated(Minecraft minecraft) {
        hoverAt(minecraft, clickGuiX, clickGuiY);
        if (--countdown > 0) {
            return;
        }
        dispatchClick(minecraft, clickGuiX, clickGuiY);
        countdown = 50;
        state = State.PATTERN_WAIT_REPLY;
    }

    private void waitForSeparatedReply(Minecraft minecraft) {
        if (--countdown > 0) {
            return;
        }
        requestServerSeparatedCheck(minecraft);
        waitTicks = 0;
        state = State.WAIT_SERVER_CHECK;
    }

    private void waitForServerCheck(Minecraft minecraft) {
        if (timedOut(minecraft, "waiting for authoritative separated state")) {
            return;
        }
        if (!serverCheckComplete) {
            return;
        }
        assertion("server-side separated state query completed", serverCheckError == null);
        if (serverCheckError != null) {
            report.append("server query error: ").append(serverCheckError).append('\n');
        }
        UIElement root = rootElement(minecraft);
        assertion("pattern ModularUI tree remains available after RPC", root != null);
        if (root != null) {
            validatePatternPageAfterClick(root);
            grabScreenshot(minecraft, "topo-ae-ui-sync-pattern-separated");
        }
        countdown = 10;
        state = State.FLUSH;
    }

    private void closeAfterScreenshot(Minecraft minecraft) {
        if (--countdown > 0) {
            return;
        }
        if (minecraft.player == null) {
            failure("client player vanished before closing machine UI");
            state = State.FLUSH;
            countdown = 5;
            return;
        }
        minecraft.player.closeContainer();
        waitTicks = 0;
        state = State.BETWEEN_SCREENS;
    }

    private void waitBetweenScreens(Minecraft minecraft) {
        if (timedOut(minecraft, "waiting for machine UI to close")) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        machineIndex++;
        assertion("next AE machine index is valid", machineIndex < DEFINITIONS.length);
        countdown = 15;
        state = State.OPEN_DELAY;
    }

    private void flushAfterDelay(Minecraft minecraft) {
        if (--countdown <= 0) {
            finish(minecraft);
        }
    }

    private void validateConfigPage(UIElement root) {
        assertion("AE config page exists and is displayed", isDisplayedById(root, CONFIG_PAGE_ID));
        assertion("AE config stock item slots are displayed", allEffectivelyDisplayed(findAllById(root, "topo_ae_stock_item_slot"), 9));
        assertHiddenBindableValues(root, CONFIG_TARGET_SYNC_ID, 9);
        assertHiddenBindableValues(root, CONFIG_STOCK_SYNC_ID, 9);
    }

    private void validateBufferPage(UIElement root) {
        assertion("AE buffer page exists and is displayed", isDisplayedById(root, BUFFER_PAGE_ID));
        assertion("AE buffer item slots are displayed", allEffectivelyDisplayed(findAllById(root, "topo_ae_buffer_item_slot"), 36));
        assertion("AE buffer fluid slots are displayed", allEffectivelyDisplayed(findAllById(root, "topo_ae_buffer_fluid_slot"), 9));
        assertHiddenBindableValues(root, BUFFER_AMOUNT_SYNC_ID, 45);
    }

    private void validatePatternPageBeforeClick(UIElement root) {
        assertion("AE pattern page exists and is displayed", isDisplayedById(root, PATTERN_PAGE_ID));
        assertHiddenBindableValues(root, PATTERN_SEPARATED_SYNC_ID, 1);
        assertHiddenBindableValues(root, PATTERN_POOL_IDS_SYNC_ID, 1);

        UIElement button = findById(root, PATTERN_SEPARATED_BUTTON_ID);
        assertion("separated control uses ServerToggleButton", button instanceof MachineUiComponentTemplate.ServerToggleButton);
        if (button instanceof MachineUiComponentTemplate.ServerToggleButton toggle) {
            assertion("separated control initially reflects false", !toggle.getValue());
        }

        patternSearchPanelBefore = findById(root, SEARCH_POOL_PANEL_ID);
        patternVisibilitySyncBefore = findById(root, SEARCH_POOL_VISIBILITY_SYNC_ID);
        assertion("shared search-pool panel exists and is displayed before click",
                patternSearchPanelBefore != null && isEffectivelyDisplayed(patternSearchPanelBefore));
        assertion("search-pool visibility mirror exists as hidden BindableValue",
                patternVisibilitySyncBefore instanceof BindableValue<?> && !patternVisibilitySyncBefore.isDisplayed());
    }

    private void validatePatternPageAfterClick(UIElement root) {
        assertion("authoritative server separated state is true", serverSeparated);

        UIElement button = findById(root, PATTERN_SEPARATED_BUTTON_ID);
        assertion("client separated control remains ServerToggleButton", button instanceof MachineUiComponentTemplate.ServerToggleButton);
        if (button instanceof MachineUiComponentTemplate.ServerToggleButton toggle) {
            assertion("client separated control reflects authoritative true", toggle.getValue());
        }

        UIElement searchPanel = findById(root, SEARCH_POOL_PANEL_ID);
        UIElement visibilitySync = findById(root, SEARCH_POOL_VISIBILITY_SYNC_ID);
        assertion("search-pool panel stays in the stable tree after separation",
                searchPanel != null && searchPanel == patternSearchPanelBefore);
        assertion("search-pool visibility mirror stays in the stable tree after separation",
                visibilitySync != null && visibilitySync == patternVisibilitySyncBefore);
        assertion("search-pool panel is layout-hidden after separation",
                searchPanel != null && !isEffectivelyDisplayed(searchPanel));
        assertHiddenBindableValues(root, PATTERN_SEPARATED_SYNC_ID, 1);
    }

    private void assertHiddenBindableValues(UIElement root, String id, int expectedCount) {
        List<UIElement> matches = findAllById(root, id);
        assertion(id + " count == " + expectedCount, matches.size() == expectedCount);
        assertion(id + " nodes are BindableValue", !matches.isEmpty() && matches.stream().allMatch(BindableValue.class::isInstance));
        assertion(id + " nodes are layout-hidden", !matches.isEmpty() && matches.stream().noneMatch(UIElement::isDisplayed));
    }

    private void buildScene(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            failure("integrated server missing while placing AE machines");
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().relative(player.getDirection(), 2).above();
                for (int index = 0; index < DEFINITIONS.length; index++) {
                    BlockPos pos = base.offset(index * 2, 0, 0);
                    level.setBlock(pos, DEFINITIONS[index].registeredBlock().getDefaultState(), 3);
                    machinePositions[index] = pos;
                }
            } catch (Throwable throwable) {
                sceneError = throwable.toString();
            } finally {
                sceneReady = true;
            }
        });
    }

    private void requestGuiOpen(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        BlockPos pos = machinePositions[machineIndex];
        if (server == null || pos == null) {
            failure("server or AE machine position missing before GUI open");
            countdown = 5;
            state = State.FLUSH;
            return;
        }
        openError = null;
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                BlockUIMenuType.openUI(player, pos);
            } catch (Throwable throwable) {
                openError = throwable.toString();
            }
        });
    }

    private void requestServerSeparatedCheck(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        BlockPos pos = machinePositions[2];
        if (server == null || pos == null) {
            serverCheckError = "server or pattern-provider position missing";
            serverCheckComplete = true;
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                if (!(player.level().getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
                    throw new IllegalStateException("pattern-provider block entity is missing at " + pos);
                }
                AePatternProvider provider = machine.machineComponents().require(AePatternProvider.AE_PATTERN_PROVIDER);
                serverSeparated = provider.separated();
            } catch (Throwable throwable) {
                serverCheckError = throwable.toString();
            } finally {
                serverCheckComplete = true;
            }
        });
    }

    private void dispatchClick(Minecraft minecraft, double guiX, double guiY) {
        var screen = minecraft.screen;
        if (screen == null) {
            failure("machine screen vanished before separated click");
            return;
        }
        report.append(String.format(Locale.ROOT, "clicking separated at (%.2f, %.2f)%n", guiX, guiY));
        MouseButtonEvent click = new MouseButtonEvent(guiX, guiY, new MouseButtonInfo(0, 0));
        boolean consumed = screen.mouseClicked(click, false);
        screen.mouseReleased(click);
        assertion("real screen click was consumed", consumed);
    }

    private void hoverAt(Minecraft minecraft, double guiX, double guiY) {
        double scaleX = (double) minecraft.getWindow().getScreenWidth() / minecraft.getWindow().getGuiScaledWidth();
        double scaleY = (double) minecraft.getWindow().getScreenHeight() / minecraft.getWindow().getGuiScaledHeight();
        setMousePosition(minecraft.mouseHandler, guiX * scaleX, guiY * scaleY);
    }

    private void setMousePosition(MouseHandler handler, double x, double y) {
        try {
            Field xField = MouseHandler.class.getDeclaredField("xpos");
            Field yField = MouseHandler.class.getDeclaredField("ypos");
            xField.setAccessible(true);
            yField.setAccessible(true);
            xField.setDouble(handler, x);
            yField.setDouble(handler, y);
        } catch (ReflectiveOperationException exception) {
            failure("virtual mouse positioning failed: " + exception);
        }
    }

    private static @Nullable UIElement rootElement(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ModularUIContainerScreen screen)) {
            return null;
        }
        return screen.getMenu().getModularUI().ui.rootElement;
    }

    private static @Nullable UIElement findById(UIElement root, String id) {
        if (id.equals(root.getId())) {
            return root;
        }
        for (UIElement child : root.getSafeChildren()) {
            UIElement found = findById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<UIElement> findAllById(UIElement root, String id) {
        List<UIElement> matches = new ArrayList<>();
        collectById(root, id, matches);
        return matches;
    }

    private static void collectById(UIElement element, String id, List<UIElement> matches) {
        if (id.equals(element.getId())) {
            matches.add(element);
        }
        for (UIElement child : element.getSafeChildren()) {
            collectById(child, id, matches);
        }
    }

    private static boolean isDisplayedById(UIElement root, String id) {
        // Repeated component contributions can preserve more than one same-id page node in the
        // stable tree while only one is selected. The assertion is about the visible page, so do
        // not let an earlier layout-hidden twin mask the displayed one.
        return findAllById(root, id).stream().anyMatch(AeUiSyncProbe::isEffectivelyDisplayed);
    }

    private static boolean allEffectivelyDisplayed(List<UIElement> elements, int expectedCount) {
        return elements.size() == expectedCount && elements.stream().allMatch(AeUiSyncProbe::isEffectivelyDisplayed);
    }

    private static boolean isEffectivelyDisplayed(UIElement element) {
        for (UIElement current = element; current != null; current = current.getParent()) {
            if (!current.isDisplayed() || !current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInsideViewport(Minecraft minecraft, double x, double y) {
        return x >= 0.0 && y >= 0.0 && x <= minecraft.getWindow().getGuiScaledWidth() && y <= minecraft.getWindow().getGuiScaledHeight();
    }

    private void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), 1, ignored -> {});
        report.append("screenshot ").append(name).append(".png captured\n");
    }

    private boolean timedOut(Minecraft minecraft, String what) {
        if (++waitTicks <= WAIT_TIMEOUT_TICKS) {
            return false;
        }
        failure("timeout " + what);
        finish(minecraft);
        return true;
    }

    private void assertion(String description, boolean passed) {
        report.append(description).append(": ").append(passed ? "PASS" : "FAIL").append('\n');
        failed |= !passed;
    }

    private void failure(String description) {
        assertion(description, false);
    }

    private void finish(Minecraft minecraft) {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        report.append("RESULT ").append(failed ? "FAIL" : "PASS").append('\n');
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("AE UI sync probe failed to write its report", exception);
        }
        try {
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException exception) {
            LOGGER.error("AE UI sync probe failed to delete its flag", exception);
        }
        LOGGER.warn("AE UI sync probe done:\n{}", report);
        minecraft.stop();
    }
}
