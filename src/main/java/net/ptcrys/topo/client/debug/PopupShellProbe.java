package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.ui.AmountEditorPopup;
import net.ptcrys.topo.api.machine.ui.ItemPickerPopup;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import kotlin.Unit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 共享模态弹窗壳({@code MachineUiContainerTemplate.openModalPopup})的游戏内回归探针(同
 * {@link PortHighlightProbe} 的流水线骨架,独立 flag):仅当存在 {@code topo-popup-shell-probe.flag}
 * 时激活。开一台机器 UI 取根作 host,经共享壳先开数字编辑弹窗截图、再开物品选取弹窗截图,断言两者
 * 都按专属 id 挂上遮罩。物品选取弹窗用平凡 entryFor/previewIcon stub(只验壳 + 正文排版,不验筛选
 * 语义),重点看正文提示是否在面板宽度内换行而非单行溢出。报告写文件后自动退出。
 */
public final class PopupShellProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Topo-PopupShellProbe");
    private static final Path FLAG_FILE = Path.of("topo-popup-shell-probe.flag");
    private static final Path REPORT_FILE = Path.of("topo-popup-shell-probe-report.txt");
    private static final String LEVEL_ID = "topo-popup-shell-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        PLACE_WAIT,
        WAIT_SCREEN,
        SHOOT_AMOUNT,
        SHOOT_ITEM,
        FLUSH,
        DONE
    }

    private static PopupShellProbe instance;

    private final StringBuilder report = new StringBuilder();
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private @Nullable BlockPos machinePos;

    private PopupShellProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new PopupShellProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Popup shell probe armed: will open the amount + item-picker popups via the shared shell");
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
                                    GameType.CREATIVE,
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
                    if (openAmountPopup(minecraft)) {
                        countdown = 15;
                        state = State.SHOOT_AMOUNT;
                    } else {
                        countdown = 5;
                        state = State.FLUSH;
                    }
                }
            }
            case SHOOT_AMOUNT -> {
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "topo-popup-shell-amount");
                    closePopup(minecraft, "topo_amount_popup_backdrop");
                    if (openItemPopup(minecraft)) {
                        countdown = 15;
                        state = State.SHOOT_ITEM;
                    } else {
                        countdown = 5;
                        state = State.FLUSH;
                    }
                }
            }
            case SHOOT_ITEM -> {
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "topo-popup-shell-item-picker");
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

    private boolean openAmountPopup(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            report.append("amount: no modular UI root -> FAIL\n");
            return false;
        }
        AmountEditorPopup.open(root, Component.literal("Set amount"), 42L, 0L, 1000L, committed -> Unit.INSTANCE);
        boolean mounted = findById(root, "topo_amount_popup_backdrop") != null;
        report.append("amount popup mounted via shared shell: ").append(mounted ? "PASS" : "FAIL").append('\n');
        return mounted;
    }

    private boolean openItemPopup(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            report.append("item-picker: no modular UI root -> FAIL\n");
            return false;
        }
        // 平凡 stub:本探针只验壳 + 正文排版(提示是否换行),不验筛选 entry/icon 语义。
        ItemPickerPopup.open(
                root,
                Component.literal("Pick items"),
                (Function<ItemStack, String>) stack -> stack.isEmpty() ? null : "probe:" + stack.getItem(),
                (Function<String, IGuiTexture>) entry -> IGuiTexture.EMPTY,
                (Consumer<List<String>>) entries -> {});
        boolean mounted = findById(root, "topo_item_picker_backdrop") != null;
        report.append("item-picker popup mounted via shared shell: ").append(mounted ? "PASS" : "FAIL").append('\n');
        return mounted;
    }

    private void closePopup(Minecraft minecraft, String backdropId) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            return;
        }
        UIElement backdrop = findById(root, backdropId);
        if (backdrop != null) {
            root.removeChild(backdrop);
        }
    }

    private static @Nullable UIElement rootElement(Minecraft minecraft) {
        if (!(minecraft.screen instanceof ModularUIContainerScreen screen)) {
            return null;
        }
        return screen.getMenu().getModularUI().ui.rootElement;
    }

    private void placeMachine(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        // Registry ids are tier-qualified (component_processor_tN); use a deterministic built-in
        // host instead of searching for the obsolete unsuffixed path.
        MachineDefinition definition = BuiltinTopoMachines.COMPONENT_PROCESSOR_T3;
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos base = player.blockPosition().relative(player.getDirection(), 2).above();
                level.setBlock(base, definition.registeredBlock().getDefaultState(), 3);
                machinePos = base;
            } catch (Exception exception) {
                LOGGER.error("Popup shell probe: server-side machine placement failed", exception);
                report.append("FAILED server placement: ").append(exception).append('\n');
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
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException exception) {
            LOGGER.error("Popup shell probe failed to flush its report", exception);
        }
        LOGGER.warn("Popup shell probe done:\n{}", report);
        minecraft.stop();
    }
}
