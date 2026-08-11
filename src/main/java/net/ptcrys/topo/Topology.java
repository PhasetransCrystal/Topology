package net.ptcrys.topo;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.async.OIAsyncExecutors;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.ui.PipeSpecTooltips;
import net.ptcrys.topo.api.player.PlayerLoginNotice;
import net.ptcrys.topo.api.tick.TickHeartbeat;
import net.ptcrys.topo.api.visual.CtmClientInit;
import net.ptcrys.topo.apiv2.OfficialOIAPIPlugin;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.data.MachineDataSyncBatcher;
import net.ptcrys.topo.apiv2.machine.data.network.MachineDataNetworking;
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockChangeWatcher;
import net.ptcrys.topo.apiv2.plugin.OIPluginEngine;
import net.ptcrys.topo.apiv2.plugin.OIPlugins;
import net.ptcrys.topo.apiv2.recipe.search.OIRecipeSearchEvents;
import net.ptcrys.topo.client.debug.JeiLookupProbe;
import net.ptcrys.topo.client.debug.MachineNetworkProfilerProbe;
import net.ptcrys.topo.client.debug.MachinePerfProbe;
import net.ptcrys.topo.client.debug.MachineWorldProfilerProbe;
import net.ptcrys.topo.client.debug.PipeProbe;
import net.ptcrys.topo.client.debug.PortHighlightProbe;
import net.ptcrys.topo.client.debug.TooltipProbe;
import net.ptcrys.topo.client.debug.UiPerfProbe;
import net.ptcrys.topo.data.bootstrap.ContentRegistrationBootstrap;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.dev.OIDevCommands;
import net.ptcrys.topo.gametest.GrindingMachineSeparationGameTests;
import net.ptcrys.topo.gametest.HatchUiGameTests;
import net.ptcrys.topo.gametest.MaceratorMachineGameTests;
import net.ptcrys.topo.gametest.MachineDataSafetyGameTests;
import net.ptcrys.topo.gametest.MachineDataSyncGameTests;
import net.ptcrys.topo.gametest.MachineDestroyedGameTests;
import net.ptcrys.topo.gametest.MachineRenderComponentGameTests;
import net.ptcrys.topo.gametest.MachineTickHotPathGameTests;
import net.ptcrys.topo.gametest.MeHatchGameTests;
import net.ptcrys.topo.gametest.MultiblockMachineGameTests;
import net.ptcrys.topo.gametest.ParallelPlanningGameTests;
import net.ptcrys.topo.gametest.PipeNetworkGameTests;
import net.ptcrys.topo.gametest.PipeNetworkScaleGameTests;
import net.ptcrys.topo.gametest.PipeStrategyMatrixGameTests;
import net.ptcrys.topo.gametest.PipeTopologyRateGameTests;
import net.ptcrys.topo.gametest.RecipeLogicProfilerGameTests;
import net.ptcrys.topo.gametest.RecipeSearchPoolGameTests;
import net.ptcrys.topo.gametest.ScalarMachineGameTests;
import net.ptcrys.topo.gametest.SideIoGameTests;
import net.ptcrys.topo.gametest.TickSystemGameTests;
import net.ptcrys.topo.integration.ae2.AeIntegration;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Host mod entry. Plugins are registered explicitly ({@link OIPlugins#register}); content bootstrap
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
        // Recipe foundation/types run inside OIPluginEngine.prepare() (no early *Bootstrap).
        OIPlugins.register(OfficialOIAPIPlugin.INSTANCE);
        OIPlugins.register(OfficialOIPlugin.INSTANCE);

        // Defer engine until all @Mod constructors have had a chance to register.
        modEventBus.addListener(EventPriority.HIGHEST, Topology::bootstrapContentPipeline);

        Machines.registerResourceCapabilities(modEventBus);
        // 必须先于 PipeSpecTooltips:它的 setup 任务会 freeze ItemTooltipUis,enqueueWork 按提交序执行。
        net.ptcrys.topo.datav2.equipment.EquipmentRuntimeBindings.register(modEventBus);
        net.ptcrys.topo.datav2.material.MaterialRuntimeBindings.register(modEventBus);
        net.ptcrys.topo.datav2.machine.MachineRuntimeBindings.register(modEventBus);
        PipeSpecTooltips.register(modEventBus);
        CtmClientInit.register(modEventBus);
        AeIntegration.register(modEventBus);
        MachineDataNetworking.register(modEventBus);
        MachineDataSyncBatcher.register(modEventBus);
        OIAsyncExecutors.register();
        OIRecipeSearchEvents.register(modEventBus);
        MultiblockChangeWatcher.register();
        PipeNetworkEngine.register();
        PlayerLoginNotice.register();
        net.ptcrys.topo.api.pipe.survey.PipeSurveyNetworking.register(modEventBus);
        net.ptcrys.topo.client.survey.PipeSurveyClientRenderer.register();
        TickHeartbeat.register(modEventBus);
        UiPerfProbe.register();
        MachinePerfProbe.register();
        MachineNetworkProfilerProbe.register();
        MachineWorldProfilerProbe.register();
        PipeProbe.register();
        net.ptcrys.topo.client.debug.PipeSurveyorProbe.register();
        TooltipProbe.register();
        JeiLookupProbe.register();
        PortHighlightProbe.register();
        net.ptcrys.topo.client.debug.MachineDestroyedFeedbackProbe.register();
        net.ptcrys.topo.client.debug.PopupShellProbe.register();
        net.ptcrys.topo.client.debug.AeUiSyncProbe.register();
        OIDevCommands.register();
        if (!RecipeLogicProfilerGameTests.isProfilerOnlyMode()) {
            modEventBus.addListener(net.ptcrys.topo.gametest.EquipmentGameTests::register);
            modEventBus.addListener(MaceratorMachineGameTests::register);
            modEventBus.addListener(GrindingMachineSeparationGameTests::register);
            modEventBus.addListener(MachineDataSafetyGameTests::register);
            modEventBus.addListener(MachineDataSyncGameTests::register);
            modEventBus.addListener(MachineDestroyedGameTests::register);
            modEventBus.addListener(MachineRenderComponentGameTests::register);
            modEventBus.addListener(MachineTickHotPathGameTests::register);
            modEventBus.addListener(MeHatchGameTests::register);
            modEventBus.addListener(HatchUiGameTests::register);
            modEventBus.addListener(net.ptcrys.topo.gametest.CreativeMachineGameTests::register);
            modEventBus.addListener(MultiblockMachineGameTests::register);
            modEventBus.addListener(PipeNetworkGameTests::register);
            modEventBus.addListener(PipeNetworkScaleGameTests::register);
            modEventBus.addListener(PipeStrategyMatrixGameTests::register);
            modEventBus.addListener(PipeTopologyRateGameTests::register);
            modEventBus.addListener(ParallelPlanningGameTests::register);
            modEventBus.addListener(RecipeSearchPoolGameTests::register);
            modEventBus.addListener(net.ptcrys.topo.gametest.PipeSurveyorGameTests::register);
            modEventBus.addListener(ScalarMachineGameTests::register);
            modEventBus.addListener(SideIoGameTests::register);
            modEventBus.addListener(TickSystemGameTests::register);
            modEventBus.addListener(net.ptcrys.topo.gametest.OreWorldgenGameTests::register);
        }
        modEventBus.addListener(RecipeLogicProfilerGameTests::register);
    }

    /**
     * Shared content pipeline for runtime and datagen: registered plugins → recipe → material →
     * equipment → residual content item touch → machine → ore → lang. First {@link RegisterEvent}
     * only.
     */
    private static void bootstrapContentPipeline(RegisterEvent event) {
        if (contentBootstrapped) {
            return;
        }
        contentBootstrapped = true;

        OIPluginEngine.prepare(); // recipe foundation/types → material → equipment
        ContentRegistrationBootstrap.bootstrap();
        OIPluginEngine.bootstrapMachine();
        OIPluginEngine.bootstrapOre();
        OIPluginEngine.bootstrapLang();
    }
}
