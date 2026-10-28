package net.ptcrys.topodemo.data.pipe;

import net.ptcrys.topo.api.pipe.*;
import net.ptcrys.topodemo.api.pipe.BuiltinPipeFilterAdapters;
import net.ptcrys.topodemo.api.pipe.common.*;
import net.ptcrys.topodemo.api.recipe.BuiltinResources;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import net.minecraft.resources.Identifier;

public final class BuiltinPipes {

    public static final PipeDistributionStrategy ROUND_ROBIN = registerStrategy();
    private static final PipeResourceProfile ITEMS = new PipeResourceProfile(BuiltinResources.ITEMS, Long::toString, BuiltinPipeFilterAdapters.ITEM);
    private static final PipeResourceProfile FLUIDS = new PipeResourceProfile(BuiltinResources.FLUIDS, amount -> amount + " mB", BuiltinPipeFilterAdapters.FLUID);

    public static final PipeDefinition ITEM_PIPE = TopoDemoPlugin.INSTANCE.machine().pipe("item_pipe")
            .resource(ITEMS)
            .blockTemplate(PipeBlockTemplates.standard(
                    Identifier.withDefaultNamespace("block/copper_block"),
                    Identifier.withDefaultNamespace("block/copper_block"),
                    Identifier.withDefaultNamespace("block/copper_block")))
            .displayName("Copper Item Pipe", "铜制物品管道")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(8).nodeThroughput(32)
            .filter(new PipeFilterSettings(8, true))
            .strategy(ROUND_ROBIN, new AggregationWindow(1, 20, 4, 1)).build();

    public static final PipeDefinition FLUID_PIPE = TopoDemoPlugin.INSTANCE.machine().pipe("fluid_pipe")
            .resource(FLUIDS)
            .blockTemplate(PipeBlockTemplates.standard(
                    Identifier.withDefaultNamespace("block/iron_block"),
                    Identifier.withDefaultNamespace("block/iron_block"),
                    Identifier.withDefaultNamespace("block/iron_block")))
            .displayName("Iron Fluid Pipe", "铁制流体管道")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(250).nodeThroughput(1000)
            .filter(new PipeFilterSettings(8, true))
            .strategy(ROUND_ROBIN, new AggregationWindow(1, 20, 4, 1)).build();

    private static PipeDistributionStrategy registerStrategy() {
        var plugin = TopoDemoPlugin.INSTANCE;
        var id = plugin.machine().id("round_robin");
        String baseKey = id.toLanguageKey("pipe.strategy");
        var strategy = new RoundRobinStrategy(id);
        strategy.bindLang(
                plugin.lang().resource(id, "pipe.strategy", "Round Robin", "轮询"),
                plugin.lang().absolute(baseKey + ".desc", "Targets take turns", "目标轮流接收"),
                plugin.lang().absolute(baseKey + ".short", "RR", "轮询"));
        PipeDistributionStrategies.register(strategy);
        return strategy;
    }

    private BuiltinPipes() {}

    public static void init() {}
}
