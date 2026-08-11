package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.machine.ui.LcdData;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.api.machine.ui.MachineUiLayout;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipeLang;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.AlignItems;
import kotlin.Unit;

import java.util.Locale;

/**
 * 管道悬浮面板的 LCD 形态(机器计时面板同款 {@link LcdData} 管线):节点占用、各连接方向的
 * 角色+上 tick 流量(抽取侧带策略与速率)、网络汇总与引擎耗时。列宽构建期按最坏样本实测后
 * {@code pinColumns} 定死——自适应宽在 taffy 浮点重分配下不收敛(机器面板的布局死循环教训)。
 *
 * <p>
 * 树形只随"形状"(方向角色集/策略/定义)重建;实况数值落在 {@link Values} 可变持有器里,
 * 由 LCD 行的本地数据源逐帧轮询,Jade 每次服务端刷新只回写字段。
 */
final class PipeJadePanel {

    static final int MAX_HEIGHT = 200;

    private static final float MAX_KEY_COLUMN_WIDTH = 120f;
    private static final float MAX_VALUE_COLUMN_WIDTH = 230f;
    private static final float COLUMN_SLACK = 2f;
    private static final Direction[] DIRECTIONS = Direction.values();

    private PipeJadePanel() {}

    /** Jade 刷新间可变的实况值;面板行经本地数据源轮询这里。 */
    static final class Values {

        int roles;
        long used;
        int throughput;
        int windowTicks = 1;
        final long[] flows = new long[6];
        final String[] strategyKey = new String[6];
        final String[] detailKey = new String[6];
        final int[] rate = new int[6];
        final int[] cap = new int[6];
        final int[] interval = new int[6];
        int netNodes;
        int netExtractors;
        int netDestinations;
        long moved;
        int peak;
        long tickNanos;

        String windowSeconds() {
            return seconds(windowTicks);
        }
    }

    /** Ticks → "2.0s" 风格秒数文本。 */
    static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(1, ticks) / 20.0);
    }

    /** 形状指纹:定义+各向角色+抽取策略——变了才整树重建。 */
    static String shapeKey(PipeDefinition definition, Values values) {
        StringBuilder key = new StringBuilder(64);
        key.append(definition.id()).append('|').append(values.roles);
        for (Direction direction : DIRECTIONS) {
            String strategy = values.strategyKey[direction.ordinal()];
            if (strategy != null) {
                key.append('|').append(direction.ordinal()).append(strategy);
            }
        }
        return key.toString();
    }

    static UIElement build(PipeDefinition definition, Values values, float valueColumnWidth) {
        LcdData panel = MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .pinColumns(measureKeyColumn(values), valueColumnWidth);

        panel.addLocalBoundEntry(
                BuiltinTopoPipeLang.PIPE_JADE_LCD_NODE.getComponent(),
                () -> Component.literal(nodeText(definition, values)),
                () -> values.used > 0 ? LcdData.LED_RUNNING : LcdData.LED_WAITING);

        for (Direction direction : DIRECTIONS) {
            if (PipeSideRole.unpack(values.roles, direction) == PipeSideRole.NONE) {
                continue;
            }
            panel.addLocalBoundEntry(
                    BuiltinTopoPipeLang.side(direction).getComponent(),
                    () -> directionValue(definition, values, direction),
                    () -> switch (PipeSideRole.unpack(values.roles, direction)) {
                        case EXTRACT -> LcdData.LED_OUTPUT;
                        case DESTINATION -> LcdData.LED_RUNNING;
                        default -> LcdData.LED_TEXT;
                    });
        }

        panel.addLocalBoundEntry(
                BuiltinTopoPipeLang.PIPE_JADE_LCD_NETWORK.getComponent(),
                () -> Component.literal(networkText(values)),
                () -> LcdData.LED_TEXT);
        panel.addLocalBoundEntry(
                indented(BuiltinTopoPipeLang.PIPE_JADE_LCD_EXTRACTORS.getComponent()),
                () -> Component.literal(Integer.toString(values.netExtractors)),
                () -> values.netExtractors > 0 ? LcdData.LED_OUTPUT : LcdData.LED_TEXT);
        panel.addLocalBoundEntry(
                indented(BuiltinTopoPipeLang.PIPE_JADE_LCD_TARGETS.getComponent()),
                () -> Component.literal(Integer.toString(values.netDestinations)),
                () -> values.netDestinations > 0 ? LcdData.LED_RUNNING : LcdData.LED_TEXT);
        panel.addLocalBoundEntry(
                BuiltinTopoPipeLang.PIPE_JADE_LCD_MOVED.getComponent(),
                () -> Component.literal(movedText(definition, values)),
                () -> values.moved > 0 ? LcdData.LED_RUNNING : LcdData.LED_TEXT);
        panel.addLocalBoundEntry(
                BuiltinTopoPipeLang.PIPE_JADE_LCD_PEAK.getComponent(),
                () -> Component.literal(peakText(values)),
                () -> values.peak >= 100 ? LcdData.LED_OUTPUT : LcdData.LED_TEXT);
        panel.addLocalBoundEntry(
                BuiltinTopoPipeLang.PIPE_JADE_LCD_TICK.getComponent(),
                () -> Component.literal(tickText(values)),
                () -> LcdData.LED_TEXT);

        return MachineUiLayout.INSTANCE.column(
                0f,
                Float.NaN,
                Float.NaN,
                AlignItems.FLEX_START,
                null,
                "topo_jade_pipe_panel_root",
                scope -> {
                    scope.add(panel);
                    return Unit.INSTANCE;
                });
    }

    private static String nodeText(PipeDefinition definition, Values values) {
        long windowCapacity = (long) values.throughput * Math.max(1, values.windowTicks);
        return "avg " + format(definition, values.used) + " / " + format(definition, windowCapacity) + " ·" + values.windowSeconds();
    }

    private static String networkText(Values values) {
        return values.netNodes + " nodes";
    }

    private static String movedText(PipeDefinition definition, Values values) {
        return "avg " + format(definition, values.moved) + "/" + values.windowSeconds();
    }

    private static String peakText(Values values) {
        return values.peak + "%";
    }

    private static String tickText(Values values) {
        return String.format(Locale.ROOT, "%.1f us", values.tickNanos / 1000.0);
    }

    private static Component directionValue(PipeDefinition definition, Values values, Direction direction) {
        int ordinal = direction.ordinal();
        PipeSideRole role = PipeSideRole.unpack(values.roles, direction);
        StringBuilder text = new StringBuilder();
        text.append(BuiltinTopoPipeLang.role(role).getString());
        text.append(" · avg ").append(format(definition, values.flows[ordinal]))
                .append('/').append(values.windowSeconds());
        if (role == PipeSideRole.EXTRACT && values.strategyKey[ordinal] != null) {
            int interval = Math.max(1, values.interval[ordinal]);
            // The provider packs the per-window batch amount directly; no per-tick math here.
            long batch = values.rate[ordinal];
            text.append(" · ").append(format(definition, batch)).append('/').append(seconds(interval));
            // Network payload still carries registered keys; resolve via LangRegistry handles.
            text.append(" · ").append(LangRegistry.require(values.strategyKey[ordinal]).getString());
            if (values.detailKey[ordinal] != null) {
                text.append('·').append(LangRegistry.require(values.detailKey[ordinal]).getString());
            }
        }
        return Component.literal(text.toString());
    }

    private static String format(PipeDefinition definition, long amount) {
        return definition.profile().formatAmount(amount);
    }

    /** 二级缩进的键标签(机器计时面板同款:两空格前缀)。 */
    private static Component indented(Component label) {
        return Component.literal("  ").append(label);
    }

    private static float measureKeyColumn(Values values) {
        float width = lcdTextWidth(BuiltinTopoPipeLang.PIPE_JADE_LCD_NETWORK.getString());
        for (Direction direction : DIRECTIONS) {
            if (PipeSideRole.unpack(values.roles, direction) != PipeSideRole.NONE) {
                width = Math.max(width, lcdTextWidth(BuiltinTopoPipeLang.side(direction).getString()));
            }
        }
        width = Math.max(width, lcdTextWidth(BuiltinTopoPipeLang.PIPE_JADE_LCD_NODE.getString()));
        width = Math.max(width, lcdTextWidth("  " + BuiltinTopoPipeLang.PIPE_JADE_LCD_EXTRACTORS.getString()));
        width = Math.max(width, lcdTextWidth("  " + BuiltinTopoPipeLang.PIPE_JADE_LCD_TARGETS.getString()));
        width = Math.max(width, lcdTextWidth(BuiltinTopoPipeLang.PIPE_JADE_LCD_MOVED.getString()));
        width = Math.max(width, lcdTextWidth(BuiltinTopoPipeLang.PIPE_JADE_LCD_PEAK.getString()));
        width = Math.max(width, lcdTextWidth(BuiltinTopoPipeLang.PIPE_JADE_LCD_TICK.getString()));
        return Math.min(width + COLUMN_SLACK, MAX_KEY_COLUMN_WIDTH);
    }

    /**
     * Value-column width measured from the rows' CURRENT texts, rounded up to a 12px bucket.
     * The caller keeps a sticky high-watermark per panel (grow-only) so per-tick flow jitter
     * never straddles a bucket boundary into rebuild flapping — and the panel is never wider
     * than the widest content actually shown (the old saturated worst-case samples left a large
     * blank right region).
     */
    static float valueColumnBucket(PipeDefinition definition, Values values) {
        float width = lcdTextWidth(nodeText(definition, values));
        for (Direction direction : DIRECTIONS) {
            if (PipeSideRole.unpack(values.roles, direction) != PipeSideRole.NONE) {
                width = Math.max(width,
                        lcdTextWidth(directionValue(definition, values, direction).getString()));
            }
        }
        width = Math.max(width, lcdTextWidth(networkText(values)));
        width = Math.max(width, lcdTextWidth(movedText(definition, values)));
        width = Math.max(width, lcdTextWidth(peakText(values)));
        width = Math.max(width, lcdTextWidth(tickText(values)));
        float bucket = (float) (Math.ceil((width + COLUMN_SLACK) / 12f) * 12f);
        return Math.min(bucket, MAX_VALUE_COLUMN_WIDTH);
    }

    private static float lcdTextWidth(String text) {
        return Minecraft.getInstance().font.width(text) * (MachineUiComponentStyle.INSTANCE.getLcdFontSize() / 9f);
    }
}
