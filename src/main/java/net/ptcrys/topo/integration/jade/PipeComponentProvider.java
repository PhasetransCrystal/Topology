package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side Jade panel for pipes, rendered as an LDLib2 {@link PipeJadePanel LCD panel} (the
 * machine timing panel pipeline). The element tree is cached per position and rebuilt only when
 * the panel shape changes (roles/strategies/definition); per-refresh values land in the cached
 * {@link PipeJadePanel.Values} holder polled by the LCD rows every frame.
 */
public final class PipeComponentProvider implements IComponentProvider<BlockAccessor> {

    public static final PipeComponentProvider INSTANCE = new PipeComponentProvider();

    private static final Identifier UID = IdHelper.oi("pipe");
    private static final int CACHE_LIMIT = 16;

    private final Map<BlockPos, PanelCache> cache = new LinkedHashMap<>();

    private PipeComponentProvider() {}

    @Override
    public @NonNull Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, @NonNull BlockAccessor accessor, @NonNull IPluginConfig config) {
        if (!(accessor.getBlock() instanceof PipeBlock pipe)) {
            return;
        }
        CompoundTag data = accessor.getServerData();
        if (!data.contains(PipeDataProvider.ROLES_KEY)) {
            return;
        }
        PanelCache entry = cache.computeIfAbsent(accessor.getPosition().immutable(), unused -> new PanelCache());
        while (cache.size() > CACHE_LIMIT) {
            cache.remove(cache.keySet().iterator().next());
        }
        entry.update(pipe.definition(), data);
        tooltip.add(entry.element);
    }

    private static final class PanelCache {

        private final PipeJadePanel.Values values = new PipeJadePanel.Values();
        private LDLibTooltipElement element;
        private String shapeKey = "";
        /** Sticky high-watermark of the value column (grow-only; see valueColumnBucket). */
        private float valueColumnWidth;

        void update(PipeDefinition definition, CompoundTag data) {
            values.roles = data.getIntOr(PipeDataProvider.ROLES_KEY, 0);
            values.used = data.getLongOr(PipeDataProvider.USED_KEY, 0L);
            values.throughput = data.getIntOr(PipeDataProvider.THROUGHPUT_KEY, 0);
            values.windowTicks = Math.max(1, data.getIntOr(PipeDataProvider.WINDOW_TICKS_KEY, 1));
            long[] flows = data.getLongArray(PipeDataProvider.FLOWS_KEY).orElse(null);
            for (Direction direction : Direction.values()) {
                int ordinal = direction.ordinal();
                values.flows[ordinal] = flows != null && ordinal < flows.length ? flows[ordinal] : 0L;
                CompoundTag extract = data.getCompound(
                        PipeDataProvider.EXTRACT_PREFIX + direction.getSerializedName()).orElse(null);
                if (extract != null) {
                    values.strategyKey[ordinal] = extract.getStringOr(PipeDataProvider.EXTRACT_STRATEGY_KEY, "");
                    values.rate[ordinal] = extract.getIntOr(PipeDataProvider.EXTRACT_RATE_KEY, 0);
                    values.cap[ordinal] = extract.getIntOr(PipeDataProvider.EXTRACT_CAP_KEY, 0);
                    values.interval[ordinal] = extract.getIntOr(PipeDataProvider.EXTRACT_INTERVAL_KEY, 1);
                    String detail = extract.getStringOr(PipeDataProvider.EXTRACT_DETAIL_KEY, "");
                    values.detailKey[ordinal] = detail.isEmpty() ? null : detail;
                } else {
                    values.strategyKey[ordinal] = null;
                    values.detailKey[ordinal] = null;
                    values.rate[ordinal] = 0;
                    values.cap[ordinal] = 0;
                    values.interval[ordinal] = 1;
                }
            }
            values.netNodes = data.getIntOr(PipeDataProvider.NET_NODES_KEY, 0);
            values.netExtractors = data.getIntOr(PipeDataProvider.NET_EXTRACTORS_KEY, 0);
            values.netDestinations = data.getIntOr(PipeDataProvider.NET_DESTINATIONS_KEY, 0);
            values.moved = data.getLongOr(PipeDataProvider.NET_MOVED_KEY, 0L);
            values.peak = data.getIntOr(PipeDataProvider.NET_PEAK_KEY, 0);
            values.tickNanos = data.getLongOr(PipeDataProvider.NET_TICK_NANOS_KEY, 0L);

            float bucket = PipeJadePanel.valueColumnBucket(definition, values);
            if (bucket > valueColumnWidth) {
                valueColumnWidth = bucket;
            }
            String currentShape = PipeJadePanel.shapeKey(definition, values) + "|w" + valueColumnWidth;
            if (element == null || !shapeKey.equals(currentShape)) {
                element = new LDLibTooltipElement(
                        PipeJadePanel.build(definition, values, valueColumnWidth),
                        PipeJadePanel.MAX_HEIGHT);
                shapeKey = currentShape;
            }
        }
    }
}
