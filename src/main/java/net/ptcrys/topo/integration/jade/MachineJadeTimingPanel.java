package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.api.machine.ui.LcdData;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.api.machine.ui.MachineUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.AlignItems;
import kotlin.Unit;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class MachineJadeTimingPanel {

    static final int MAX_HEIGHT = 220;

    /** 列宽安全上限(异常超长文本截断+悬浮兜底)与实测后的呼吸余量。 */
    private static final float MAX_KEY_COLUMN_WIDTH = 170f;
    private static final float MAX_VALUE_COLUMN_WIDTH = 220f;
    private static final float COLUMN_SLACK = 2f;
    private static final String ROOT_ID = "topo_jade_machine_timing_root";

    private MachineJadeTimingPanel() {}

    static UIElement build(
                           List<Row> rows,
                           Function<String, TimingNode> nodeById,
                           LongSupplier totalNanos) {
        // 悬浮面板必须定宽(pinColumns):自适应宽标签+右对齐+自动宽根+最宽锁的组合在 taffy
        // 浮点重分配下不收敛,每帧跑满 LDLib2 的 10 轮布局循环上限(UiPerfProbe 取证)。
        // 宽度在构建期按真实内容实测(键列=各行键文本最大宽;值列=按"微秒位数饱和+满百分比+
        // 该行静态明细"的最坏样本量取),运行期零布局回写——既不截断正常内容也不复发循环。
        LcdData panel = MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .pinColumns(measureKeyColumn(rows), measureValueColumn(rows, nodeById));
        for (Row row : rows) {
            panel.addLocalBoundEntry(
                    Component.literal(label(row)),
                    () -> value(row, nodeById.apply(row.id()), totalNanos.getAsLong()),
                    () -> led(row));
        }
        return MachineUiLayout.INSTANCE.column(
                0f,
                Float.NaN,
                Float.NaN,
                AlignItems.FLEX_START,
                null,
                ROOT_ID,
                scope -> {
                    scope.add(panel);
                    return Unit.INSTANCE;
                });
    }

    private static float measureKeyColumn(List<Row> rows) {
        float width = 0f;
        for (Row row : rows) {
            width = Math.max(width, lcdTextWidth(label(row)));
        }
        return Math.min(width + COLUMN_SLACK, MAX_KEY_COLUMN_WIDTH);
    }

    private static float measureValueColumn(List<Row> rows, Function<String, TimingNode> nodeById) {
        float width = 0f;
        for (Row row : rows) {
            TimingNode node = nodeById.apply(row.id());
            String detail = node == null ? row.detail() : node.detail();
            long display = node == null ? 0L : node.displayNanos();
            long peak = node == null ? 0L : node.peakNanos();
            width = Math.max(width, lcdTextWidth(reserveValueString(display, peak, detail)));
        }
        return Math.min(width + COLUMN_SLACK, MAX_VALUE_COLUMN_WIDTH);
    }

    /**
     * 值列保留串:贴合该行实测量级而非幻想最坏样本。主值/峰值按其微秒位数 + 一位进位余量取满格
     * ("99.999"/"^99.9"),百分比恒按满格 "100.0%"(任何行都可能升到 100%),峰值为 0 的空闲行
     * 不预留 ^ 段。这样空闲机器每行只占 "99.999 us 100.0% 明细" 的宽度,不再为 6 位微秒
     * (≈1 秒/tick,真实机器永不出现)的幻想宽度让位——用户实测面板右侧大片留白的根因。
     *
     * <p>
     * shapeKey 仍只含行结构(不含实况值,避免每帧整树重建);进位余量替代"按值重建"承担防裁剪:
     * 实况值在缓存面板存活期内增长 ≤10× 不会越界。
     */
    static String reserveValueString(long displayNanos, long peakNanos, String detail) {
        StringBuilder reserve = new StringBuilder();
        reserve.append(saturatedMicros(displayNanos)).append(" us ");
        if (peakNanos > 0L) {
            reserve.append('^').append(saturatedPeakMicros(peakNanos)).append(' ');
        }
        reserve.append("100.0%");
        if (!detail.isBlank()) {
            reserve.append(' ').append(detail);
        }
        return reserve.toString();
    }

    /** 同量级 + 一位进位的满格微秒串(与 {@link #formatMicros} 等宽:整数位 + 3 位小数)。 */
    private static String saturatedMicros(long nanos) {
        return "9".repeat(microsIntegerDigits(nanos) + 1) + ".999";
    }

    /** 峰值同量级 + 一位进位的满格串(与 {@link #formatPeakMicros} 等宽:整数位 + 1 位小数)。 */
    private static String saturatedPeakMicros(long nanos) {
        return "9".repeat(microsIntegerDigits(nanos) + 1) + ".9";
    }

    /** 微秒整数部分的位数(亚微秒计 1 位);值列量级定宽的依据。 */
    static int microsIntegerDigits(long nanos) {
        long micros = Math.max(0L, nanos) / 1_000L;
        int digits = 1;
        while (micros >= 10L) {
            micros /= 10L;
            digits++;
        }
        return digits;
    }

    /** LCD 字号下的文本像素宽:原版字宽(行高 9 基准)按 lcdFontSize 缩放。 */
    private static float lcdTextWidth(String text) {
        return Minecraft.getInstance().font.width(text) * (MachineUiComponentStyle.INSTANCE.getLcdFontSize() / 9f);
    }

    private static String label(Row row) {
        // 键列定宽 104px:剥掉命名空间前缀(本模组面板里恒为 topo,纯噪音),
        // 让 trait path 尽量完整可读;仍溢出的部分由 SCISSOR 截断、悬浮看全文。
        String text = row.label();
        int colon = text.indexOf(':');
        if (colon >= 0) {
            text = text.substring(colon + 1);
        }
        return "  ".repeat(Math.max(0, row.depth())) + text;
    }

    /**
     * 值列以滚动均值领跑(单 tick 瞬时样本被调度/缓存噪声支配,容易把尾部样本误读成稳态成本),
     * 紧跟 {@code ^峰值} 提示近期最大;无均值时(刚开始监控/合成样本)回退最近一次采样。
     */
    private static Component value(Row row, TimingNode node, long totalNanos) {
        long nanos = node == null ? 0L : node.displayNanos();
        long peak = node == null ? 0L : node.peakNanos();
        StringBuilder text = new StringBuilder();
        text.append(formatMicros(nanos)).append(" us ");
        if (peak > 0L) {
            text.append('^').append(formatPeakMicros(peak)).append(' ');
        }
        text.append(formatPercent(nanos, totalNanos));
        String detail = node == null ? row.detail() : node.detail();
        if (!detail.isBlank()) {
            text.append(' ').append(detail);
        }
        return Component.literal(text.toString());
    }

    private static int led(Row row) {
        if (row.depth() == 0) {
            return LcdData.LED_RUNNING;
        }
        if (row.id().equals("machine.self")) {
            return LcdData.LED_WAITING;
        }
        if (row.id().equals("traits")) {
            return LcdData.LED_OUTPUT;
        }
        return LcdData.LED_TEXT;
    }

    private static String formatMicros(long nanos) {
        long whole = nanos / 1_000L;
        long fraction = nanos % 1_000L;
        if (fraction < 10L) {
            return whole + ".00" + fraction;
        }
        if (fraction < 100L) {
            return whole + ".0" + fraction;
        }
        return whole + "." + fraction;
    }

    /** 峰值只做量级提示,1 位小数足够且不撑爆值列。 */
    private static String formatPeakMicros(long nanos) {
        return (nanos / 1_000L) + "." + (nanos % 1_000L / 100L);
    }

    private static String formatPercent(long nanos, long totalNanos) {
        if (totalNanos <= 0L) {
            return "0.0%";
        }
        long tenths = Math.round(nanos * 1000.0 / totalNanos);
        return (tenths / 10L) + "." + (tenths % 10L) + "%";
    }

    record Row(String id, String label, String detail, int depth) {

        Row {
            id = Objects.requireNonNull(id, "row id");
            label = Objects.requireNonNull(label, "row label");
            detail = Objects.requireNonNull(detail, "row detail");
            depth = Math.max(0, depth);
        }
    }

    record TimingNode(
                      String id,
                      String label,
                      String detail,
                      long nanos,
                      long avgNanos,
                      long peakNanos,
                      List<TimingNode> children) {

        TimingNode(String id, String label, String detail, long nanos, List<TimingNode> children) {
            this(id, label, detail, nanos, 0L, 0L, children);
        }

        TimingNode {
            id = Objects.requireNonNull(id, "timing node id");
            label = Objects.requireNonNull(label, "timing node label");
            detail = Objects.requireNonNull(detail, "timing node detail");
            nanos = Math.max(0L, nanos);
            avgNanos = Math.max(0L, avgNanos);
            peakNanos = Math.max(0L, peakNanos);
            children = List.copyOf(Objects.requireNonNull(children, "timing node children"));
        }

        /** 面板主读数:有滚动均值用均值,否则回退最近一次采样。 */
        long displayNanos() {
            return avgNanos > 0L ? avgNanos : nanos;
        }

        boolean isEmpty() {
            return nanos <= 0L && children.isEmpty();
        }
    }
}
