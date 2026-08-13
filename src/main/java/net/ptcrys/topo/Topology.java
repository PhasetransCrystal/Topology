package net.ptcrys.topo;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.async.TopoAsyncExecutors;
import net.ptcrys.topo.api.api.plugin.TopoPluginEngine;
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.api.tick.TickHeartbeat;
import net.ptcrys.topo.api.api.visual.CtmClientInit;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.data.MachineDataSyncBatcher;
import net.ptcrys.topo.api.machine.data.network.MachineDataNetworking;
import net.ptcrys.topo.api.machine.multiblock.MultiblockChangeWatcher;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.ui.PipeSpecTooltips;
import net.ptcrys.topo.api.recipe.search.TopoRecipeSearchEvents;
import net.ptcrys.topo.client.debug.JeiLookupProbe;
import net.ptcrys.topo.client.debug.TooltipProbe;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Host mod entry. Plugins are registered explicitly ({@link TopoPlugins#register}); content bootstrap
 * runs on the first {@link RegisterEvent} at {@link EventPriority#HIGHEST} so every mod constructor
 * can register first, and RegistryLib ({@code LOW}) still sees queued entries. Official and
 * third-party share that path (no SPI).
 */
@Mod(Topology.MODID)
public class Topology {

    public static final String MODID = "topo";
    public static final RegistryCore REGISTRY = RegistryCore.create(MODID);

    private static boolean contentBootstrapped;

    public Topology(IEventBus modEventBus) {
        // Same path as third-party: explicit register in @Mod constructor.
        TopoPlugins.register(OfficialTopoAPIPlugin.INSTANCE);

        // Defer engine until all @Mod constructors have had a chance to register.
        modEventBus.addListener(EventPriority.HIGHEST, Topology::bootstrapContentPipeline);

        Machines.registerResourceCapabilities(modEventBus);
        // Content (product tables) registers through the same explicit plugin path. Its runtime
        // bindings must register before PipeSpecTooltips: that listener's setup task freezes
        // ItemTooltipUis and enqueueWork tasks run in submission order.
        PipeSpecTooltips.register(modEventBus);
        CtmClientInit.register(modEventBus);
        MachineDataNetworking.register(modEventBus);
        MachineDataSyncBatcher.register(modEventBus);
        TopoAsyncExecutors.register();
        TopoRecipeSearchEvents.register(modEventBus);
        MultiblockChangeWatcher.register();
        PipeNetworkEngine.register();
        net.ptcrys.topo.api.pipe.survey.PipeSurveyNetworking.register(modEventBus);
        net.ptcrys.topo.client.survey.PipeSurveyClientRenderer.register();
        TickHeartbeat.register(modEventBus);
        TooltipProbe.register();
        JeiLookupProbe.register();
    }

    /**
     * Shared content pipeline for runtime and datagen: registered plugins → recipe → material →
     * equipment → machine → ore → lang. First {@link RegisterEvent} only.
     */
    private static void bootstrapContentPipeline(RegisterEvent event) {
        if (contentBootstrapped) {
            return;
        }
        contentBootstrapped = true;

        TopoPluginEngine.prepare(); // recipe foundation/types → material → equipment
        TopoPluginEngine.bootstrapMachine();
        TopoPluginEngine.bootstrapOre();
        TopoPluginEngine.bootstrapLang();
    }
}
