package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionContext;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipePortStrategyDetails;
import net.ptcrys.topo.api.pipe.ui.PipePortAccess;
import net.ptcrys.topo.api.pipe.ui.PipePortUiCollector;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/** 按网络距离依次填充目标；顺序与每批数量由端口配置决定。 */
public final class ByDistanceStrategy extends LangBoundStrategy {

    private static final LangKey ORDER = TopoDemoApiPlugin.INSTANCE.lang().key("ui", "pipe.port.distance_order", "Order", "顺序");
    private static final LangKey NEAREST = TopoDemoApiPlugin.INSTANCE.lang().key("pipe", "distance.nearest", "Nearest", "最近优先");
    private static final LangKey FARTHEST = TopoDemoApiPlugin.INSTANCE.lang().key("pipe", "distance.farthest", "Farthest", "最远优先");

    public enum DistanceOrder implements StringRepresentable {

        NEAREST,
        FARTHEST;

        public static final Codec<DistanceOrder> CODEC = StringRepresentable.fromEnum(DistanceOrder::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public LangKey nameLang() {
            return this == NEAREST ? ByDistanceStrategy.NEAREST : ByDistanceStrategy.FARTHEST;
        }
    }

    public record Config(PipeDistributionStrategy strategy, DistanceOrder order, int amount, int interval)
            implements PipePortStrategyDetails {

        private static MapCodec<Config> codec(PipeDistributionStrategy strategy) {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DistanceOrder.CODEC.optionalFieldOf("order", DistanceOrder.NEAREST).forGetter(Config::order),
                    Codec.INT.fieldOf("amount").forGetter(Config::amount),
                    Codec.INT.fieldOf("interval").forGetter(Config::interval))
                    .apply(instance, (order, amount, interval) -> new Config(strategy, order, amount, interval)));
        }

        public Config withOrder(DistanceOrder value) {
            return new Config(strategy, value, amount, interval);
        }

        @Override
        public int amountOr(int fallback) {
            return amount;
        }

        @Override
        public Config withAmount(int value) {
            return new Config(strategy, order, value, interval);
        }

        @Override
        public Config withInterval(int value) {
            return new Config(strategy, order, amount, value);
        }

        @Override
        public LangKey detailLang() {
            return order.nameLang();
        }
    }

    private final Identifier id;
    private final MapCodec<Config> codec = Config.codec(this);

    public ByDistanceStrategy(Identifier id) {
        this.id = id;
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public MapCodec<Config> configCodec() {
        return codec;
    }

    @Override
    public PipePortStrategyConfig initialPortConfig(PipeDefinition definition, AggregationWindow aggregation) {
        int interval = aggregation.initialInterval();
        return new Config(this, DistanceOrder.NEAREST, definition.maxBatchAmount(interval), interval);
    }

    @Override
    public int budget(PipeDefinition definition, PipePortStrategyConfig config) {
        return config instanceof Config distance ? distance.amount() : definition.maxBatchAmount(config.interval());
    }

    @Override
    public void distribute(PipeDistributionContext context) {
        int count = context.destinationCount();
        boolean farthest = context.config() instanceof Config distance && distance.order() == DistanceOrder.FARTHEST;
        for (int i = 0; i < count && context.budgetRemaining() > 0; i++) {
            context.transfer(farthest ? count - 1 - i : i, context.budgetRemaining());
        }
    }

    @Override
    public void contributeConfigUi(PipePortUiCollector collector, PipePortAccess access) {
        DistanceOrder[] orders = DistanceOrder.values();
        Label[] labels = new Label[orders.length];
        var selected = access.bindInt("topo_pipe_port_order_value", DistanceOrder.NEAREST.ordinal(),
                () -> access.currentConfig() instanceof Config config ? config.order().ordinal() : DistanceOrder.NEAREST.ordinal(),
                value -> {
                    if (value >= 0 && value < orders.length) {
                        access.updateConfig(config -> config instanceof Config distance ? distance.withOrder(orders[value]) : config);
                    }
                },
                value -> {
                    for (int i = 0; i < labels.length; i++) {
                        int index = i;
                        labels[i].textStyle(style -> style.textColor(index == value ? MachineUiComponentStyle.INSTANCE.getTextSelected() : MachineUiComponentStyle.INSTANCE.getTextMuted()));
                    }
                });
        UIElement[] buttons = new UIElement[orders.length];
        for (DistanceOrder order : orders) {
            int ordinal = order.ordinal();
            buttons[ordinal] = collector.intChoiceButton("topo_pipe_port_order_" + order.getSerializedName(),
                    order.nameLang().getComponent(), selected, ordinal, label -> labels[ordinal] = label);
        }
        collector.addRow(collector.labeledRow("topo_pipe_port_order_row", ORDER.getComponent(), buttons));
    }
}
