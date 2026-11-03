package net.ptcrys.topo.config;

import net.ptcrys.topo.Topology;

import dev.toma.configuration.config.*;

@Config(id = Topology.MODID)
public final class TopoConfiguration {

    @Configurable
    public Multiblock MULTIBLOCK = new Multiblock();

    public static class Multiblock {

        @Configurable
        @Configurable.Range(min = 20, max = 1200)
        @Configurable.Comment({
                "Formed controllers self-recheck at least this often (in ticks), staggered by position hash.",
                "Backstop upkeep catches missed world changes and is the cheap path on intact structures.",
                "Lower values increase responsiveness but cost more ticks; higher values reduce load but delay break detection.",
                "Default: 150 ticks (7.5s)"
        })
        public int backstopIntervalTicks = 150;

        @Configurable
        @Configurable.Range(min = 64, max = 16384)
        @Configurable.Comment({
                "Footprints at or above this many (maximal) cells recognize on the worker pool instead of the server thread.",
                "Sync recognition has lowest latency; async avoids tick lag for large structures.",
                "Default: 2048 cells"
        })
        public int asyncCellThreshold = 2048;

        @Configurable
        @Configurable.Range(min = 1, max = 16)
        @Configurable.Comment({
                "Maximum concurrent async recognitions per controller.",
                "Limits worker-thread load when world changes rapidly; excess submissions wait for in-flight verdicts.",
                "Default: 4"
        })
        public int maxAsyncSubmissionsPerController = 4;

        @Configurable
        @Configurable.Comment({
                "Whether formed controllers use targeted recheck (O(changes) cell re-tests instead of O(footprint) full recognize).",
                "Targeted recheck breaks structures faster on small edits; disabling it forces every mark into the full path.",
                "Default: true"
        })
        public boolean enableTargetedRecheck = true;
    }
}
