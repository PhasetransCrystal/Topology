package net.ptcrys.topo.data.pipe;

import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeBlockTemplate;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeFilterSettings;
import net.ptcrys.topo.api.pipe.PipeResourceProfile;
import net.ptcrys.topo.api.pipe.Pipes;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.pipe.common.PipeBlockTemplates;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.TopoCompactNumber;

import static net.ptcrys.topo.data.pipe.BuiltinTopoPipeDistributionStrategies.BY_DISTANCE;
import static net.ptcrys.topo.data.pipe.BuiltinTopoPipeDistributionStrategies.EQUAL_SPLIT;
import static net.ptcrys.topo.data.pipe.BuiltinTopoPipeDistributionStrategies.ROUND_ROBIN;

/**
 * Builtin pipes: five resource kinds x three tiers, flat-registered so every (kind, tier) pair has
 * its own strong handle. Tier numbers follow the spec table (node throughput = 8x the same-tier
 * extract cap); the strategy set a tier unlocks is listed explicitly at each registration site.
 *
 * <p>
 * Every resource kind uses the original Topo full-face texture language under
 * {@code textures/block/pipe}: center-node icons identify the resource kind, while a symmetric
 * shaft rail uses one shared color per tier. Legacy file stems are retained only to avoid a
 * resource-pack compatibility break.
 */
public final class BuiltinTopoPipes {

    private static final PipeResourceProfile ITEM_PROFILE = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.ITEM.resourceType(), TopoCompactNumber::formatCompact,
            BuiltinTopoPipeFilterAdapters.ITEM);
    private static final PipeResourceProfile FLUID_PROFILE = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.FLUID.resourceType(), TopoCompactNumber::formatCompactBuckets,
            BuiltinTopoPipeFilterAdapters.FLUID);
    // Scalar kinds carry exactly one resource each — no registry identity to filter on.
    private static final PipeResourceProfile ENERGY_PROFILE = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.ENERGY.resourceType(), TopoCompactNumber::formatCompact, null);
    private static final PipeResourceProfile ADVANCED_ENERGY_PROFILE = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.resourceType(), TopoCompactNumber::formatCompact, null);
    private static final PipeResourceProfile HEAT_PROFILE = new PipeResourceProfile(
            BuiltinTopoResourceIntegrations.HEAT.resourceType(), TopoCompactNumber::formatCompact, null);

    /** Filter envelopes by tier (item/fluid pipes): BASIC none, then 16/32 entries with tags. */
    private static final PipeFilterSettings FILTER_ADVANCED = new PipeFilterSettings(16, true);
    private static final PipeFilterSettings FILTER_ELITE = new PipeFilterSettings(32, true);

    // --- item pipes (logistical_transporter textures) ---------------------------------------

    public static final PipeDefinition ITEM_PIPE_BASIC = Pipes.register("item_pipe_basic")
            .resource(ITEM_PROFILE)
            .blockTemplate(template("basic", "logistical_transporter"))
            .displayName("Basic Item Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(1)
            .nodeThroughput(8)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(40, 40, 40, 5))
            .build();

    public static final PipeDefinition ITEM_PIPE_ADVANCED = Pipes.register("item_pipe_advanced")
            .resource(ITEM_PROFILE)
            .blockTemplate(template("advanced", "logistical_transporter"))
            .displayName("Advanced Item Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(4)
            .nodeThroughput(32)
            .filter(FILTER_ADVANCED)
            .strategy(BY_DISTANCE, window(20, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(20, 40, 40, 5))
            .build();

    public static final PipeDefinition ITEM_PIPE_ELITE = Pipes.register("item_pipe_elite")
            .resource(ITEM_PROFILE)
            .blockTemplate(template("elite", "logistical_transporter"))
            .displayName("Elite Item Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(16)
            .nodeThroughput(128)
            .filter(FILTER_ELITE)
            .strategy(BY_DISTANCE, window(10, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(10, 40, 40, 5))
            .strategy(ROUND_ROBIN, window(10, 40, 40, 5))
            .build();

    // --- fluid pipes (mechanical_pipe textures) ----------------------------------------------

    public static final PipeDefinition FLUID_PIPE_BASIC = Pipes.register("fluid_pipe_basic")
            .resource(FLUID_PROFILE)
            .blockTemplate(template("basic", "mechanical_pipe"))
            .displayName("Basic Fluid Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(50)
            .nodeThroughput(400)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(40, 40, 40, 5))
            .build();

    public static final PipeDefinition FLUID_PIPE_ADVANCED = Pipes.register("fluid_pipe_advanced")
            .resource(FLUID_PROFILE)
            .blockTemplate(template("advanced", "mechanical_pipe"))
            .displayName("Advanced Fluid Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(250)
            .nodeThroughput(2000)
            .filter(FILTER_ADVANCED)
            .strategy(BY_DISTANCE, window(20, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(20, 40, 40, 5))
            .build();

    public static final PipeDefinition FLUID_PIPE_ELITE = Pipes.register("fluid_pipe_elite")
            .resource(FLUID_PROFILE)
            .blockTemplate(template("elite", "mechanical_pipe"))
            .displayName("Elite Fluid Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(1250)
            .nodeThroughput(10000)
            .filter(FILTER_ELITE)
            .strategy(BY_DISTANCE, window(10, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(10, 40, 40, 5))
            .strategy(ROUND_ROBIN, window(10, 40, 40, 5))
            .build();

    // --- energy pipes (universal_cable textures) ----------------------------------------------

    public static final PipeDefinition ENERGY_PIPE_BASIC = Pipes.register("energy_pipe_basic")
            .resource(ENERGY_PROFILE)
            .blockTemplate(template("basic", "universal_cable"))
            .displayName("Basic Energy Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(256)
            .nodeThroughput(2048)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(40, 40, 40, 5))
            .build();

    public static final PipeDefinition ENERGY_PIPE_ADVANCED = Pipes.register("energy_pipe_advanced")
            .resource(ENERGY_PROFILE)
            .blockTemplate(template("advanced", "universal_cable"))
            .displayName("Advanced Energy Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(2048)
            .nodeThroughput(16384)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(20, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(20, 40, 40, 5))
            .build();

    public static final PipeDefinition ENERGY_PIPE_ELITE = Pipes.register("energy_pipe_elite")
            .resource(ENERGY_PROFILE)
            .blockTemplate(template("elite", "universal_cable"))
            .displayName("Elite Energy Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(16384)
            .nodeThroughput(131072)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(10, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(10, 40, 40, 5))
            .strategy(ROUND_ROBIN, window(10, 40, 40, 5))
            .build();

    // --- advanced energy pipes (pressurized_tube textures) ------------------------------------

    public static final PipeDefinition ADVANCED_ENERGY_PIPE_BASIC = Pipes.register("advanced_energy_pipe_basic")
            .resource(ADVANCED_ENERGY_PROFILE)
            .blockTemplate(template("basic", "pressurized_tube"))
            .displayName("Basic Advanced Energy Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(64)
            .nodeThroughput(512)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(40, 40, 40, 5))
            .build();

    public static final PipeDefinition ADVANCED_ENERGY_PIPE_ADVANCED = Pipes.register("advanced_energy_pipe_advanced")
            .resource(ADVANCED_ENERGY_PROFILE)
            .blockTemplate(template("advanced", "pressurized_tube"))
            .displayName("Advanced Advanced Energy Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(512)
            .nodeThroughput(4096)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(20, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(20, 40, 40, 5))
            .build();

    public static final PipeDefinition ADVANCED_ENERGY_PIPE_ELITE = Pipes.register("advanced_energy_pipe_elite")
            .resource(ADVANCED_ENERGY_PROFILE)
            .blockTemplate(template("elite", "pressurized_tube"))
            .displayName("Elite Advanced Energy Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(4096)
            .nodeThroughput(32768)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(10, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(10, 40, 40, 5))
            .strategy(ROUND_ROBIN, window(10, 40, 40, 5))
            .build();

    // --- heat pipes (thermodynamic_conductor textures) -----------------------------------------

    public static final PipeDefinition HEAT_PIPE_BASIC = Pipes.register("heat_pipe_basic")
            .resource(HEAT_PROFILE)
            .blockTemplate(template("basic", "thermodynamic_conductor"))
            .displayName("Basic Heat Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(128)
            .nodeThroughput(1024)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(40, 40, 40, 5))
            .build();

    public static final PipeDefinition HEAT_PIPE_ADVANCED = Pipes.register("heat_pipe_advanced")
            .resource(HEAT_PROFILE)
            .blockTemplate(template("advanced", "thermodynamic_conductor"))
            .displayName("Advanced Heat Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(1024)
            .nodeThroughput(8192)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(20, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(20, 40, 40, 5))
            .build();

    public static final PipeDefinition HEAT_PIPE_ELITE = Pipes.register("heat_pipe_elite")
            .resource(HEAT_PROFILE)
            .blockTemplate(template("elite", "thermodynamic_conductor"))
            .displayName("Elite Heat Pipe")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .maxExtractRate(8192)
            .nodeThroughput(65536)
            .filter(PipeFilterSettings.NONE)
            .strategy(BY_DISTANCE, window(10, 40, 40, 5))
            .strategy(EQUAL_SPLIT, window(10, 40, 40, 5))
            .strategy(ROUND_ROBIN, window(10, 40, 40, 5))
            .build();

    private BuiltinTopoPipes() {}

    /** Only activates class initialization. Must remain empty. */
    public static void init() {}

    /** In-place window factory: (minInterval, maxInterval, initialInterval, coarseStep). */
    private static AggregationWindow window(
                                            int minInterval, int maxInterval, int initialInterval, int coarseStep) {
        return new AggregationWindow(minInterval, maxInterval, initialInterval, coarseStep);
    }

    private static PipeBlockTemplate template(String tier, String family) {
        String base = "block/pipe/" + tier + "_" + family;
        return PipeBlockTemplates.standard(
                IdHelper.oi(base), IdHelper.oi(base + "_vertical"), IdHelper.oi(base + "_terminal"));
    }
}
