package net.ptcrys.topodemo.data.pipe;

import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeBlockTemplate;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeFilterSettings;
import net.ptcrys.topo.api.pipe.PipeResourceProfile;
import net.ptcrys.topo.helper.TopoCompactNumber;
import net.ptcrys.topodemo.api.pipe.BuiltinPipeFilterAdapters;
import net.ptcrys.topodemo.api.pipe.common.PipeBlockTemplates;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.resources.Identifier;

/** 旧目录中的分级管道；在原版贴图上保留旧的吞吐、过滤和策略窗口。 */
public final class BuiltinTieredPipes {

    private static final PipeResourceProfile ITEMS = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.ITEM.resourceType(), TopoCompactNumber::formatCompact, BuiltinPipeFilterAdapters.ITEM);
    private static final PipeResourceProfile FLUIDS = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.FLUID.resourceType(), TopoCompactNumber::formatCompactBuckets, BuiltinPipeFilterAdapters.FLUID);
    private static final PipeResourceProfile ENERGY = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.ENERGY.resourceType(), TopoCompactNumber::formatCompact, null);
    private static final PipeResourceProfile ADVANCED_ENERGY = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.resourceType(), TopoCompactNumber::formatCompact, null);
    private static final PipeResourceProfile HEAT = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.HEAT.resourceType(), TopoCompactNumber::formatCompact, null);

    public static final PipeDefinition ITEM_BASIC = TopoDemoPlugin.INSTANCE.machine().pipe("item_pipe_basic")
            .resource(ITEMS).blockTemplate(texture("block/copper_block"))
            .displayName("Basic Item Pipe", "基础物品管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(1).nodeThroughput(8).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(40, 40, 40, 5)).build();
    public static final PipeDefinition ITEM_ADVANCED = TopoDemoPlugin.INSTANCE.machine().pipe("item_pipe_advanced")
            .resource(ITEMS).blockTemplate(texture("block/copper_block"))
            .displayName("Advanced Item Pipe", "进阶物品管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(4).nodeThroughput(32).filter(new PipeFilterSettings(16, true))
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(20, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(20, 40, 40, 5)).build();
    public static final PipeDefinition ITEM_ELITE = TopoDemoPlugin.INSTANCE.machine().pipe("item_pipe_elite")
            .resource(ITEMS).blockTemplate(texture("block/copper_block"))
            .displayName("Elite Item Pipe", "精英物品管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(16).nodeThroughput(128).filter(new PipeFilterSettings(32, true))
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.ROUND_ROBIN, new AggregationWindow(10, 40, 40, 5)).build();

    public static final PipeDefinition FLUID_BASIC = TopoDemoPlugin.INSTANCE.machine().pipe("fluid_pipe_basic")
            .resource(FLUIDS).blockTemplate(texture("block/iron_block"))
            .displayName("Basic Fluid Pipe", "基础流体管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(50).nodeThroughput(400).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(40, 40, 40, 5)).build();
    public static final PipeDefinition FLUID_ADVANCED = TopoDemoPlugin.INSTANCE.machine().pipe("fluid_pipe_advanced")
            .resource(FLUIDS).blockTemplate(texture("block/iron_block"))
            .displayName("Advanced Fluid Pipe", "进阶流体管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(250).nodeThroughput(2000).filter(new PipeFilterSettings(16, true))
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(20, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(20, 40, 40, 5)).build();
    public static final PipeDefinition FLUID_ELITE = TopoDemoPlugin.INSTANCE.machine().pipe("fluid_pipe_elite")
            .resource(FLUIDS).blockTemplate(texture("block/iron_block"))
            .displayName("Elite Fluid Pipe", "精英流体管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(1250).nodeThroughput(10000).filter(new PipeFilterSettings(32, true))
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.ROUND_ROBIN, new AggregationWindow(10, 40, 40, 5)).build();

    public static final PipeDefinition ENERGY_BASIC = TopoDemoPlugin.INSTANCE.machine().pipe("energy_pipe_basic")
            .resource(ENERGY).blockTemplate(texture("block/gold_block"))
            .displayName("Basic Energy Pipe", "基础能量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(256).nodeThroughput(2048).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(40, 40, 40, 5)).build();
    public static final PipeDefinition ENERGY_ADVANCED = TopoDemoPlugin.INSTANCE.machine().pipe("energy_pipe_advanced")
            .resource(ENERGY).blockTemplate(texture("block/gold_block"))
            .displayName("Advanced Energy Pipe", "进阶能量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(2048).nodeThroughput(16384).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(20, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(20, 40, 40, 5)).build();
    public static final PipeDefinition ENERGY_ELITE = TopoDemoPlugin.INSTANCE.machine().pipe("energy_pipe_elite")
            .resource(ENERGY).blockTemplate(texture("block/gold_block"))
            .displayName("Elite Energy Pipe", "精英能量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(16384).nodeThroughput(131072).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.ROUND_ROBIN, new AggregationWindow(10, 40, 40, 5)).build();

    public static final PipeDefinition ADVANCED_ENERGY_BASIC = TopoDemoPlugin.INSTANCE.machine().pipe("advanced_energy_pipe_basic")
            .resource(ADVANCED_ENERGY).blockTemplate(texture("block/amethyst_block"))
            .displayName("Basic Advanced Energy Pipe", "基础高级能量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(64).nodeThroughput(512).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(40, 40, 40, 5)).build();
    public static final PipeDefinition ADVANCED_ENERGY_ADVANCED = TopoDemoPlugin.INSTANCE.machine().pipe("advanced_energy_pipe_advanced")
            .resource(ADVANCED_ENERGY).blockTemplate(texture("block/amethyst_block"))
            .displayName("Advanced Advanced Energy Pipe", "进阶高级能量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(512).nodeThroughput(4096).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(20, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(20, 40, 40, 5)).build();
    public static final PipeDefinition ADVANCED_ENERGY_ELITE = TopoDemoPlugin.INSTANCE.machine().pipe("advanced_energy_pipe_elite")
            .resource(ADVANCED_ENERGY).blockTemplate(texture("block/amethyst_block"))
            .displayName("Elite Advanced Energy Pipe", "精英高级能量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(4096).nodeThroughput(32768).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.ROUND_ROBIN, new AggregationWindow(10, 40, 40, 5)).build();

    public static final PipeDefinition HEAT_BASIC = TopoDemoPlugin.INSTANCE.machine().pipe("heat_pipe_basic")
            .resource(HEAT).blockTemplate(texture("block/iron_block"))
            .displayName("Basic Heat Pipe", "基础热量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(128).nodeThroughput(1024).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(40, 40, 40, 5)).build();
    public static final PipeDefinition HEAT_ADVANCED = TopoDemoPlugin.INSTANCE.machine().pipe("heat_pipe_advanced")
            .resource(HEAT).blockTemplate(texture("block/iron_block"))
            .displayName("Advanced Heat Pipe", "进阶热量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(1024).nodeThroughput(8192).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(20, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(20, 40, 40, 5)).build();
    public static final PipeDefinition HEAT_ELITE = TopoDemoPlugin.INSTANCE.machine().pipe("heat_pipe_elite")
            .resource(HEAT).blockTemplate(texture("block/iron_block"))
            .displayName("Elite Heat Pipe", "精英热量管道").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .maxExtractRate(8192).nodeThroughput(65536).filter(PipeFilterSettings.NONE)
            .strategy(BuiltinPipes.BY_DISTANCE, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.EQUAL_SPLIT, new AggregationWindow(10, 40, 40, 5))
            .strategy(BuiltinPipes.ROUND_ROBIN, new AggregationWindow(10, 40, 40, 5)).build();

    private BuiltinTieredPipes() {}

    public static void init() {}

    private static PipeBlockTemplate texture(String path) {
        Identifier texture = Identifier.withDefaultNamespace(path);
        return PipeBlockTemplates.standard(texture, texture, texture);
    }
}
