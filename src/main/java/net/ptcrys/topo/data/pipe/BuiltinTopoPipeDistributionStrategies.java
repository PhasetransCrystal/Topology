package net.ptcrys.topo.data.pipe;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionContext;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategies;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.ui.PipePortAccess;
import net.ptcrys.topo.api.pipe.ui.PipePortUiCollector;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.Objects;

/**
 * Builtin pipe distribution strategies. Tier gating and per-(pipe x strategy) envelopes are
 * expressed at the pipe registration sites ({@code BuiltinTopoPipes}) through
 * {@code PipeStrategyOffer} aggregation windows, not here.
 *
 * <p>
 * All builtin configs persist the per-window batch {@code amount} (clamped into
 * {@code maxExtractRate × interval} by the runtime) and the aggregation {@code interval}
 * (clamped into the offer's {@code AggregationWindow}).
 */
public final class BuiltinTopoPipeDistributionStrategies {

    private static final LangDomainRegistration LANG = OfficialTopoPlugin.INSTANCE.lang();

    public static final PipeDistributionStrategy BY_DISTANCE = register(new ByDistanceStrategy(IdHelper.oi("by_distance")),
            "By Distance", "按距离",
            "DIST", "DIST",
            "Nearest or farthest first", "最近或最远优先");

    public static final PipeDistributionStrategy EQUAL_SPLIT = register(new EqualSplitStrategy(IdHelper.oi("equal_split")),
            "Equal Split", "均分",
            "EQUL", "EQUL",
            "Even share per target", "每目标均分");

    public static final PipeDistributionStrategy ROUND_ROBIN = register(new RoundRobinStrategy(IdHelper.oi("round_robin")),
            "Round Robin", "轮询",
            "ROBN", "ROBN",
            "Targets take turns", "目标轮流");

    private BuiltinTopoPipeDistributionStrategies() {}

    /** Only activates class initialization. Must remain empty. */
    public static void init() {}

    private static <T extends LangBoundStrategy> T register(
                                                            T strategy,
                                                            String en,
                                                            String cn,
                                                            String shortEn,
                                                            String shortCn,
                                                            String descEn,
                                                            String descCn) {
        // Key minting lives only at registration (pipe.strategy.<ns>.<path>[.short|.desc]).
        String baseKey = strategy.id().toLanguageKey("pipe.strategy");
        strategy.bindLang(
                LANG.resource(strategy.id(), "pipe.strategy", en, cn),
                LANG.absolute(baseKey + ".desc", descEn, descCn),
                LANG.absolute(baseKey + ".short", shortEn, shortCn));
        PipeDistributionStrategies.register(strategy);
        return strategy;
    }

    /** Distance iteration order of {@link #BY_DISTANCE}. */
    public enum DistanceOrder implements StringRepresentable {

        NEAREST,
        FARTHEST;

        public static final Codec<DistanceOrder> CODEC = StringRepresentable.fromEnum(DistanceOrder::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Display handle for GUI toggle buttons / Jade labels. */
        public LangKey nameLang() {
            return BuiltinTopoPipeLang.distanceOrder(this);
        }

        public Component displayName() {
            return nameLang().getComponent();
        }
    }

    /** Shared name/desc/short LangKey binding for builtin strategies. */
    private abstract static class LangBoundStrategy implements PipeDistributionStrategy {

        private LangKey nameLang;
        private LangKey descriptionLang;
        private LangKey shortNameLang;

        final void bindLang(LangKey nameLang, LangKey descriptionLang, LangKey shortNameLang) {
            this.nameLang = Objects.requireNonNull(nameLang, "nameLang");
            this.descriptionLang = Objects.requireNonNull(descriptionLang, "descriptionLang");
            this.shortNameLang = Objects.requireNonNull(shortNameLang, "shortNameLang");
        }

        @Override
        public final LangKey nameLang() {
            return Objects.requireNonNull(nameLang, "strategy lang not bound: " + id());
        }

        @Override
        public final LangKey descriptionLang() {
            return Objects.requireNonNull(descriptionLang, "strategy lang not bound: " + id());
        }

        @Override
        public final LangKey shortNameLang() {
            return Objects.requireNonNull(shortNameLang, "strategy lang not bound: " + id());
        }
    }

    /** Shared amount+interval config of the single-knob strategies. */
    public record RateConfig(PipeDistributionStrategy strategy, int amount, int interval)
            implements PipePortStrategyConfig {

        static MapCodec<RateConfig> codec(PipeDistributionStrategy strategy) {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.fieldOf("amount").forGetter(RateConfig::amount),
                    Codec.INT.fieldOf("interval").forGetter(RateConfig::interval)).apply(instance, (amount, interval) -> new RateConfig(strategy, amount, interval)));
        }

        @Override
        public int amountOr(int fallback) {
            return amount;
        }

        @Override
        public PipePortStrategyConfig withAmount(int newAmount) {
            return new RateConfig(strategy, newAmount, interval);
        }

        @Override
        public PipePortStrategyConfig withInterval(int newInterval) {
            return new RateConfig(strategy, amount, newInterval);
        }
    }

    /** {@link #BY_DISTANCE} config: iteration order + amount + interval. */
    public record ByDistanceConfig(
                                   PipeDistributionStrategy strategy, DistanceOrder order, int amount, int interval)
            implements PipePortStrategyConfig {

        static MapCodec<ByDistanceConfig> codec(PipeDistributionStrategy strategy) {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DistanceOrder.CODEC.optionalFieldOf("order", DistanceOrder.NEAREST)
                            .forGetter(ByDistanceConfig::order),
                    Codec.INT.fieldOf("amount").forGetter(ByDistanceConfig::amount),
                    Codec.INT.fieldOf("interval").forGetter(ByDistanceConfig::interval)).apply(instance, (order, amount, interval) -> new ByDistanceConfig(strategy, order, amount, interval)));
        }

        public ByDistanceConfig withOrder(DistanceOrder newOrder) {
            return new ByDistanceConfig(strategy, newOrder, amount, interval);
        }

        @Override
        public int amountOr(int fallback) {
            return amount;
        }

        @Override
        public PipePortStrategyConfig withAmount(int newAmount) {
            return new ByDistanceConfig(strategy, order, newAmount, interval);
        }

        @Override
        public PipePortStrategyConfig withInterval(int newInterval) {
            return new ByDistanceConfig(strategy, order, amount, newInterval);
        }
    }

    private abstract static sealed class RateStrategy extends LangBoundStrategy
                                                      permits EqualSplitStrategy, RoundRobinStrategy {

        private final Identifier id;
        private final MapCodec<RateConfig> codec;

        RateStrategy(Identifier id) {
            this.id = id;
            this.codec = RateConfig.codec(this);
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public MapCodec<? extends PipePortStrategyConfig> configCodec() {
            return codec;
        }

        @Override
        public PipePortStrategyConfig initialPortConfig(PipeDefinition definition, AggregationWindow aggregation) {
            int interval = aggregation.initialInterval();
            return new RateConfig(this, definition.maxBatchAmount(interval), interval);
        }

        @Override
        public int budget(PipeDefinition definition, PipePortStrategyConfig config) {
            return config instanceof RateConfig rateConfig ? rateConfig.amount() : definition.maxBatchAmount(config.interval());
        }
    }

    private static final class ByDistanceStrategy extends LangBoundStrategy {

        private final Identifier id;
        private final MapCodec<ByDistanceConfig> codec;

        ByDistanceStrategy(Identifier id) {
            this.id = id;
            this.codec = ByDistanceConfig.codec(this);
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public MapCodec<? extends PipePortStrategyConfig> configCodec() {
            return codec;
        }

        @Override
        public PipePortStrategyConfig initialPortConfig(PipeDefinition definition, AggregationWindow aggregation) {
            int interval = aggregation.initialInterval();
            return new ByDistanceConfig(
                    this, DistanceOrder.NEAREST, definition.maxBatchAmount(interval), interval);
        }

        @Override
        public int budget(PipeDefinition definition, PipePortStrategyConfig config) {
            return config instanceof ByDistanceConfig byDistance ? byDistance.amount() : definition.maxBatchAmount(config.interval());
        }

        /** Greedy fill in distance order; FARTHEST simply walks the BFS-sorted order backwards. */
        @Override
        public void distribute(PipeDistributionContext context) {
            int count = context.destinationCount();
            if (count <= 0) {
                return;
            }
            boolean farthest = context.config() instanceof ByDistanceConfig byDistance && byDistance.order() == DistanceOrder.FARTHEST;
            for (int i = 0; i < count && context.budgetRemaining() > 0; i++) {
                context.transfer(farthest ? count - 1 - i : i, context.budgetRemaining());
            }
        }

        /**
         * One labeled settings row — "Order" caption left, the mutually exclusive
         * Nearest/Farthest pair right — matching the shell's row rhythm on the 176-wide screen.
         * Selection and edits share one hidden authoritative LDLib2 integer value.
         */
        @Override
        public void contributeConfigUi(PipePortUiCollector collector, PipePortAccess access) {
            DistanceOrder[] orders = DistanceOrder.values();
            Label[] labels = new Label[orders.length];
            var selectedOrder = access.bindInt(
                    "topo_pipe_port_order_value",
                    DistanceOrder.NEAREST.ordinal(),
                    () -> access.currentConfig() instanceof ByDistanceConfig byDistance ? byDistance.order().ordinal() : DistanceOrder.NEAREST.ordinal(),
                    selected -> {
                        if (selected < 0 || selected >= orders.length) {
                            return;
                        }
                        DistanceOrder order = orders[selected];
                        access.updateConfig(config -> config instanceof ByDistanceConfig byDistance ? byDistance.withOrder(order) : config);
                    },
                    selected -> {
                        for (int i = 0; i < labels.length; i++) {
                            int index = i;
                            labels[i].textStyle(style -> style.textColor(index == selected ? MachineUiComponentStyle.INSTANCE.getTextSelected() : MachineUiComponentStyle.INSTANCE.getTextMuted()));
                        }
                    });
            var buttons = new com.lowdragmc.lowdraglib2.gui.ui.UIElement[orders.length];
            for (DistanceOrder order : orders) {
                int ordinal = order.ordinal();
                buttons[ordinal] = collector.intChoiceButton(
                        "topo_pipe_port_order_" + order.getSerializedName(),
                        order.displayName(),
                        selectedOrder,
                        ordinal,
                        label -> labels[ordinal] = label);
            }
            collector.addRow(collector.labeledRow("topo_pipe_port_order_row",
                    BuiltinTopoPipeLang.UI_PIPE_PORT_DISTANCE_ORDER.getComponent(), buttons));
        }
    }

    private static final class EqualSplitStrategy extends RateStrategy {

        EqualSplitStrategy(Identifier id) {
            super(id);
        }

        /**
         * True per-batch equal split among destinations that can actually receive: shares are
         * sized from {@code min(budget, source supply) / liveCount}, where dead destinations
         * (full buffers, output-only views, unloaded) are excluded — they must not eat a share
         * (e.g. 30 over 3 live + 1 dead lands as a stable 10/10/10/0, not 7s plus leftovers).
         * The indivisible remainder goes to whoever accepts first in the leftover pass. The
         * iteration start still rotates per batch for the one case a single batch cannot split:
         * a saturated shared path segment, whose per-transfer allowance goes to the head of the
         * line.
         */
        @Override
        public void distribute(PipeDistributionContext context) {
            int count = context.destinationCount();
            if (count <= 0) {
                return;
            }
            long liveMask = liveMask(context, count);
            int live = count <= 64 ? Long.bitCount(liveMask) : count;
            if (live == 0) {
                return;
            }
            int start = Math.floorMod(context.cursor(), count);
            int deliverable = Math.min(context.budgetRemaining(), context.sourceAvailable());
            int share = Math.max(1, deliverable / live);
            for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
                int index = (start + offset) % count;
                if (isLive(liveMask, count, index)) {
                    context.transfer(index, Math.min(share, context.budgetRemaining()));
                }
            }
            // Greedy second pass hands the indivisible remainder to whoever still accepts.
            for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
                int index = (start + offset) % count;
                if (isLive(liveMask, count, index)) {
                    context.transfer(index, context.budgetRemaining());
                }
            }
            context.setCursor(start + 1);
        }
    }

    /** Bitmask of destinations that can accept right now; beyond 64 everyone counts as live. */
    private static long liveMask(PipeDistributionContext context, int count) {
        if (count > 64) {
            return -1L;
        }
        long mask = 0;
        for (int i = 0; i < count; i++) {
            if (context.destinationAcceptance(i) > 0) {
                mask |= 1L << i;
            }
        }
        return mask;
    }

    private static boolean isLive(long liveMask, int count, int index) {
        return count > 64 || (liveMask & (1L << index)) != 0;
    }

    private static final class RoundRobinStrategy extends RateStrategy {

        RoundRobinStrategy(Identifier id) {
            super(id);
        }

        @Override
        public void distribute(PipeDistributionContext context) {
            int count = context.destinationCount();
            if (count <= 0) {
                return;
            }
            int start = Math.floorMod(context.cursor(), count);
            boolean served = false;
            for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
                int index = (start + offset) % count;
                int moved = context.transfer(index, context.budgetRemaining());
                if (moved > 0 && !served) {
                    served = true;
                    context.setCursor(index + 1);
                }
            }
            if (!served) {
                context.setCursor(start + 1);
            }
        }
    }
}
