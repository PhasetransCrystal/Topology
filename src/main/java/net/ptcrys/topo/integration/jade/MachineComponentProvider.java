package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiLayout;
import net.ptcrys.topo.apiv2.machine.ui.ResourceBar;
import net.ptcrys.topo.client.debug.UiPerfProbe;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.AlignItems;
import kotlin.Unit;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side Jade tooltip renderer for OI machine scalar resource bars and timing trees. */
public final class MachineComponentProvider implements IBlockComponentProvider {

    public static final MachineComponentProvider INSTANCE = new MachineComponentProvider();

    private static final Identifier UID = IdHelper.oi("machine");
    private static final int CACHE_LIMIT = 64;
    private final LinkedHashMap<BlockPos, MachineTooltipCache> cache = new LinkedHashMap<>(16, 0.75f, true);

    private MachineComponentProvider() {}

    @Override
    public @NonNull Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(
                              @NonNull ITooltip tooltip,
                              @NonNull BlockAccessor accessor,
                              @NonNull IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        appendScalarBars(tooltip, accessor, data);
        appendTimingTree(tooltip, accessor, data);
    }

    private void appendScalarBars(ITooltip tooltip, BlockAccessor accessor, CompoundTag data) {
        if (!(data.get(MachineDataProvider.SCALAR_BARS_KEY) instanceof ListTag rawBars) || rawBars.isEmpty()) {
            return;
        }
        List<ScalarBarRow> rows = new ArrayList<>(rawBars.size());
        for (int i = 0; i < rawBars.size(); i++) {
            rawBars.getCompound(i).ifPresent(tag -> rows.add(new ScalarBarRow(
                    tag.getStringOr(MachineDataProvider.SCALAR_NAME_KEY, ""),
                    tag.getIntOr(MachineDataProvider.SCALAR_COLOR_KEY, 0xFFFFFFFF),
                    tag.getLongOr(MachineDataProvider.SCALAR_AMOUNT_KEY, 0L),
                    tag.getLongOr(MachineDataProvider.SCALAR_CAPACITY_KEY, 0L))));
        }
        if (rows.isEmpty()) {
            return;
        }
        MachineTooltipCache entry = tooltipCache(accessor);
        entry.setScalarBars(rows);
        tooltip.add(entry.scalarElement());
    }

    private void appendTimingTree(ITooltip tooltip, BlockAccessor accessor, CompoundTag data) {
        if (!(data.get(MachineDataProvider.PERFORMANCE_TREE_KEY) instanceof CompoundTag rawTree)) {
            return;
        }

        MachineJadeTimingPanel.TimingNode tree = decodeNode(rawTree);
        if (tree.isEmpty()) {
            return;
        }

        MachineTooltipCache entry = tooltipCache(accessor);
        entry.setTree(tree);
        tooltip.add(entry.element());
    }

    private MachineTooltipCache tooltipCache(BlockAccessor accessor) {
        MachineTooltipCache entry = cache.computeIfAbsent(accessor.getPosition().immutable(), unused -> new MachineTooltipCache());
        trimCache();
        return entry;
    }

    private void trimCache() {
        while (cache.size() > CACHE_LIMIT) {
            BlockPos eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    private static MachineJadeTimingPanel.TimingNode decodeNode(CompoundTag tag) {
        ListTag rawChildren = tag.getList(MachineDataProvider.NODE_CHILDREN_KEY).orElseGet(ListTag::new);
        List<MachineJadeTimingPanel.TimingNode> children = new ArrayList<>(rawChildren.size());
        for (int i = 0; i < rawChildren.size(); i++) {
            rawChildren.getCompound(i).ifPresent(child -> children.add(decodeNode(child)));
        }
        return new MachineJadeTimingPanel.TimingNode(
                tag.getStringOr(MachineDataProvider.NODE_ID_KEY, ""),
                tag.getStringOr(MachineDataProvider.NODE_LABEL_KEY, ""),
                tag.getStringOr(MachineDataProvider.NODE_DETAIL_KEY, ""),
                tag.getLongOr(MachineDataProvider.NODE_NANOS_KEY, 0L),
                tag.getLongOr(MachineDataProvider.NODE_AVG_NANOS_KEY, 0L),
                tag.getLongOr(MachineDataProvider.NODE_PEAK_NANOS_KEY, 0L),
                children);
    }

    private static void flattenRows(
                                    MachineJadeTimingPanel.TimingNode node,
                                    int depth,
                                    List<MachineJadeTimingPanel.Row> rows) {
        rows.add(new MachineJadeTimingPanel.Row(node.id(), node.label(), node.detail(), depth));
        for (MachineJadeTimingPanel.TimingNode child : node.children()) {
            flattenRows(child, depth + 1, rows);
        }
    }

    /**
     * 行结构指纹:深度/id/标签。不含 detail——它嵌着实况值(如自适应 tick 的 {@code interval=N}),
     * 进指纹会让面板每次刷新都整树重建(新 ModularUI + 全量样式匹配 + 字体排版),帧率被拖垮;
     * detail 的展示由值标签的实况数据源承担,无需重建。
     */
    private static String shapeKey(List<MachineJadeTimingPanel.Row> rows) {
        StringBuilder key = new StringBuilder(rows.size() * 32);
        for (MachineJadeTimingPanel.Row row : rows) {
            key.append(row.depth())
                    .append('|')
                    .append(row.id())
                    .append('|')
                    .append(row.label())
                    .append('\n');
        }
        return key.toString();
    }

    /** One decoded scalar bar row: translation key + resource color + live amounts. */
    private record ScalarBarRow(String nameKey, int color, long amount, long capacity) {

        String shapeKey() {
            return nameKey + '#' + Integer.toHexString(color);
        }
    }

    private static final class MachineTooltipCache {

        private MachineJadeTimingPanel.TimingNode tree = new MachineJadeTimingPanel.TimingNode("", "", "", 0L, List.of());
        private final Map<String, MachineJadeTimingPanel.TimingNode> nodesById = new LinkedHashMap<>();
        private LDLibTooltipElement element;
        private String shapeKey = "";
        // Scalar bars: the element tree is rebuilt only when the bar set changes; per-update values
        // land in this mutable list, which the bars' local data sources poll every frame.
        private final List<long[]> scalarValues = new ArrayList<>();
        private LDLibTooltipElement scalarElement;
        private String scalarShapeKey = "";

        void setScalarBars(List<ScalarBarRow> rows) {
            StringBuilder shape = new StringBuilder(rows.size() * 24);
            for (ScalarBarRow row : rows) {
                shape.append(row.shapeKey()).append('\n');
            }
            String currentShape = shape.toString();
            if (scalarElement == null || !scalarShapeKey.equals(currentShape)) {
                scalarValues.clear();
                List<ResourceBar> bars = new ArrayList<>(rows.size());
                for (ScalarBarRow row : rows) {
                    long[] values = new long[] { row.amount(), row.capacity() };
                    scalarValues.add(values);
                    ResourceBar bar = MachineUiComponentTemplate.INSTANCE.createResourceBar(
                            Component.translatable(row.nameKey()),
                            row.color(),
                            ResourceBar.Orientation.HORIZONTAL,
                            MachineUiComponentStyle.INSTANCE.getJadeResourceBarWidth());
                    bar.bindLocal(() -> values[0], () -> values[1]);
                    bars.add(bar);
                }
                UIElement column = MachineUiLayout.INSTANCE.column(
                        MachineUiComponentStyle.INSTANCE.getResourceBarGap(),
                        Float.NaN,
                        Float.NaN,
                        AlignItems.FLEX_START,
                        null,
                        "oi_jade_scalar_bars",
                        scope -> {
                            for (ResourceBar bar : bars) {
                                scope.add(bar);
                            }
                            return Unit.INSTANCE;
                        });
                int heightHint = (int) (rows.size() * (MachineUiComponentStyle.INSTANCE.getResourceBarHeight() + MachineUiComponentStyle.INSTANCE.getResourceBarGap()));
                scalarElement = new LDLibTooltipElement(column, Math.max(1, heightHint));
                scalarShapeKey = currentShape;
            } else {
                for (int i = 0; i < rows.size() && i < scalarValues.size(); i++) {
                    scalarValues.get(i)[0] = rows.get(i).amount();
                    scalarValues.get(i)[1] = rows.get(i).capacity();
                }
            }
        }

        LDLibTooltipElement scalarElement() {
            if (scalarElement == null) {
                throw new IllegalStateException("Scalar bar element requested before bars were set");
            }
            return scalarElement;
        }

        void setTree(MachineJadeTimingPanel.TimingNode tree) {
            this.tree = tree;
            nodesById.clear();
            index(tree);
            List<MachineJadeTimingPanel.Row> rows = new ArrayList<>();
            flattenRows(tree, 0, rows);
            String currentShape = shapeKey(rows);
            if (element == null || !shapeKey.equals(currentShape)) {
                element = new LDLibTooltipElement(
                        // 百分比基数与行读数同源(均值对均值,无均值回退最近采样),避免混合口径。
                        MachineJadeTimingPanel.build(rows, this::nodeById, () -> this.tree.displayNanos()),
                        MachineJadeTimingPanel.MAX_HEIGHT);
                shapeKey = currentShape;
            }
        }

        LDLibTooltipElement element() {
            if (element == null) {
                throw new IllegalStateException("Machine tooltip element requested before tree was set");
            }
            return element;
        }

        private MachineJadeTimingPanel.TimingNode nodeById(String id) {
            return nodesById.get(id);
        }

        private void index(MachineJadeTimingPanel.TimingNode node) {
            nodesById.put(node.id(), node);
            UiPerfProbe.observeJadeTimingNode(node.id());
            for (MachineJadeTimingPanel.TimingNode child : node.children()) {
                index(child);
            }
        }
    }
}
