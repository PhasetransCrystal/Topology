package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.Pipes;
import net.ptcrys.topo.apiv2.machine.ui.tooltip.ItemTooltipUis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
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

import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 物品 tooltip 面板全自动游戏内验证探针(同 {@link PipeProbe} 的流水线骨架,独立 flag):
 * 仅当工作目录存在 {@code oi-tooltip-probe.flag} 时激活;自动建生存平坦世界(创造模式下
 * InventoryScreen 会被原版换成创造背包,生存布局的悬停几何失效),向热栏发本轮验收目标
 * (装备:斧/调控器/扳手;Form 件:OI 铁齿轮 + vanilla 覆写铁粒/铁锭),打开背包用虚拟鼠标
 * 逐个悬停截图;CHECK 阶段程序化断言**每一个**已注册管道/装备/材料 Form 物品都有面板且
 * 上报尺寸正常,报告写 {@code oi-tooltip-probe-report.txt} 后自动退出。
 */
public final class TooltipProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("OI-TooltipProbe");
    private static final Path FLAG_FILE = Path.of("oi-tooltip-probe.flag");
    private static final Path REPORT_FILE = Path.of("oi-tooltip-probe-report.txt");
    /** 每轮唯一世界名:复用同名存档会撞上一轮的残留场景(并行会话共用 run/ 时尤甚)。 */
    private static final String LEVEL_ID = "oi-tooltip-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;

    /** 原版生存背包几何(176×166 居中,热栏行 y=142):虚拟鼠标定位用。 */
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;
    private static final int HOTBAR_FIRST_SLOT_X = 8;
    private static final int HOTBAR_SLOT_Y = 142;
    private static final int SLOT_SIZE = 18;

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        GIVE_ITEMS,
        HOVER,
        CHECK,
        FLUSH,
        DONE
    }

    /** 一个悬停验收目标:热栏槽位、截图名、物品(注册表绑定后才解析)。 */
    private record Target(String shot, java.util.function.Supplier<net.minecraft.world.item.Item> item) {}

    /**
     * 本轮肉眼验收清单:装备(Function 父行数量+词|简述子项)+ Form 件(化学三行,含覆写件)
     * + 管道(策略行数量+全名|简述子项,用户截图同款顶级热管)。
     */
    private static final java.util.List<Target> TARGETS = java.util.List.of(
            new Target("equipment-iron-axe", () -> requireItem("topo", "iron_axe")),
            new Target("equipment-iron-regulator", () -> requireItem("topo", "iron_regulator")),
            new Target("equipment-iron-wrench", () -> requireItem("topo", "iron_wrench")),
            new Target("material-iron-gear", () -> requireItem("topo", "iron_gear")),
            new Target("material-iron-nugget", () -> requireItem("minecraft", "iron_nugget")),
            new Target("material-iron-ingot", () -> requireItem("minecraft", "iron_ingot")),
            new Target("pipe-heat-elite", () -> requireItem("topo", "heat_pipe_elite")));

    private static net.minecraft.world.item.Item requireItem(String namespace, String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, path))
                .orElseThrow(() -> new IllegalStateException(
                        "tooltip probe target item missing: " + namespace + ":" + path));
    }

    private static TooltipProbe instance;

    private final StringBuilder report = new StringBuilder();
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private int hoverIndex;

    private TooltipProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new TooltipProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Tooltip probe armed: will hover pipe items in the inventory and screenshot their spec panels");
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
                    giveItems(minecraft);
                    countdown = 20;
                    state = State.GIVE_ITEMS;
                }
            }
            case GIVE_ITEMS -> {
                // 等服务端背包同步到客户端再开屏。
                if (--countdown <= 0) {
                    minecraft.setScreen(new InventoryScreen(minecraft.player));
                    countdown = 25;
                    hoverIndex = 0;
                    state = State.HOVER;
                }
            }
            case HOVER -> {
                hoverHotbarSlot(minecraft, hoverIndex);
                if (countdown == 5) {
                    grabScreenshot(minecraft, "oi-tooltip-probe-" + TARGETS.get(hoverIndex).shot());
                }
                if (--countdown <= 0) {
                    if (++hoverIndex < TARGETS.size()) {
                        countdown = 25;
                    } else {
                        state = State.CHECK;
                    }
                }
            }
            case CHECK -> {
                runChecks();
                countdown = 5;
                state = State.FLUSH;
            }
            case FLUSH -> {
                if (--countdown <= 0) {
                    finish(minecraft);
                }
            }
            case DONE -> {}
        }
    }

    private void giveItems(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayer player = players.getFirst();
            for (int slot = 0; slot < TARGETS.size(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(TARGETS.get(slot).item().get()));
            }
        });
    }

    /** 虚拟鼠标移到热栏槽 {@code index} 中心(背包屏几何按原版常量计算,无需反射屏幕字段)。 */
    private void hoverHotbarSlot(Minecraft minecraft, int index) {
        if (!(minecraft.screen instanceof InventoryScreen screen)) {
            return;
        }
        double guiX = (screen.width - INVENTORY_WIDTH) / 2.0 + HOTBAR_FIRST_SLOT_X + index * SLOT_SIZE + SLOT_SIZE / 2.0 - 1.0;
        double guiY = (screen.height - INVENTORY_HEIGHT) / 2.0 + HOTBAR_SLOT_Y + 8.0;
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

    /** 不止抽查悬停的几件:管道/装备/材料 Form 三个域逐一断言每个物品都有面板且尺寸正常。 */
    private void runChecks() {
        int total = 0;
        int healthy = 0;
        for (PipeDefinition definition : Pipes.registered()) {
            total++;
            if (hasHealthyPanel(definition.registeredBlock().get().asItem())) {
                healthy++;
            } else {
                report.append("pipe ").append(definition.id()).append(" has no healthy spec panel -> FAIL\n");
            }
        }
        report.append("pipes with healthy spec panels: ").append(healthy).append('/').append(total)
                .append(healthy == total && total > 0 ? " -> PASS" : " -> FAIL")
                .append('\n');

        total = 0;
        healthy = 0;
        for (var record : net.ptcrys.topo.apiv2.equipment.EquipmentRegistry.itemRecords()) {
            total++;
            if (hasHealthyPanel(record.entry().get())) {
                healthy++;
            } else {
                report.append("equipment ").append(record.equipment().id()).append('/')
                        .append(record.material().id()).append(" has no healthy tooltip panel -> FAIL\n");
            }
        }
        report.append("equipment items with healthy tooltip panels: ").append(healthy).append('/').append(total)
                .append(healthy == total && total > 0 ? " -> PASS" : " -> FAIL")
                .append('\n');

        total = 0;
        healthy = 0;
        for (var material : net.ptcrys.topo.apiv2.material.MaterialRegistry.registered()) {
            for (var form : material.strategy().forms()) {
                var item = net.ptcrys.topo.helper.MaterialHelper.item(material, form).orElse(null);
                if (item == null) {
                    continue;
                }
                total++;
                if (hasHealthyPanel(item)) {
                    healthy++;
                } else {
                    report.append("material form ").append(material.id()).append('/').append(form.id())
                            .append(" has no healthy chemistry panel -> FAIL\n");
                }
            }
        }
        report.append("material form items with healthy chemistry panels: ").append(healthy)
                .append('/').append(total)
                .append(healthy == total && total > 0 ? " -> PASS" : " -> FAIL")
                .append('\n');
    }

    private boolean hasHealthyPanel(net.minecraft.world.item.Item item) {
        TooltipComponent component = ItemTooltipUis.componentFor(new ItemStack(item));
        return component instanceof ModularUITooltipComponent wrapped && wrapped.modularUI.getWidth() > 10 && wrapped.modularUI.getHeight() > 10;
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
            LOGGER.error("Tooltip probe failed to flush its report", exception);
        }
        LOGGER.warn("Tooltip probe done:\n{}", report);
        minecraft.stop();
    }
}
