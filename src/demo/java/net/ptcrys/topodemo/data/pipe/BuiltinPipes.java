package net.ptcrys.topodemo.data.pipe;

import net.ptcrys.topo.api.pipe.*;
import net.ptcrys.topodemo.api.pipe.common.*;
import net.ptcrys.topodemo.api.recipe.BuiltinResources;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import net.minecraft.resources.Identifier;

public final class BuiltinPipes {

    public static final PipeDistributionStrategy ROUND_ROBIN = registerStrategy();
    public static final PipeDefinition ITEM_PIPE = TopoDemoPlugin.INSTANCE.machine().pipe("item_pipe")
            .resource(new PipeResourceProfile(BuiltinResources.ITEMS, Long::toString, new ItemFilter()))
            .blockTemplate(new MetalPipeTemplate(Identifier.withDefaultNamespace("block/copper_block")))
            .displayName("Copper Item Pipe")
            .creativeTab(BuiltinCreativeTabs.KEY.getKey())
            .maxExtractRate(8).nodeThroughput(32)
            .filter(new PipeFilterSettings(8, true))
            .strategy(ROUND_ROBIN, new AggregationWindow(1, 20, 4, 1)).build();

    private static PipeDistributionStrategy registerStrategy() {
        var plugin = TopoDemoPlugin.INSTANCE;
        var id = plugin.machine().id("round_robin");
        String baseKey = id.toLanguageKey("pipe.strategy");
        var strategy = new RoundRobinStrategy(id,
                plugin.lang().resource(id, "pipe.strategy", "Round Robin", "轮询"),
                plugin.lang().absolute(baseKey + ".desc", "Targets take turns", "目标轮流接收"),
                plugin.lang().absolute(baseKey + ".short", "RR", "轮询"));
        PipeDistributionStrategies.register(strategy);
        plugin.lang().absolute("block." + plugin.modId() + ".item_pipe", "Copper Item Pipe", "铜制物品管道");
        return strategy;
    }

    private BuiltinPipes() {}

    public static void init() {}
}
