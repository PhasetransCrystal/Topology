package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.pipe.PipeBlock;

import snownee.jade.addon.harvest.HarvestToolProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.HideThingsExtensionProvider;

/** Jade integration entry point discovered by Jade's {@link WailaPlugin} scan. */
@WailaPlugin
public final class TopoJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(HideThingsExtensionProvider.instance(), MachineBlockEntity.class);
        registration.registerFluidStorage(HideThingsExtensionProvider.instance(), MachineBlockEntity.class);
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, MachineBlockEntity.class);
        registration.registerBlockDataProvider(PipeDataProvider.INSTANCE, PipeBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineComponentProvider.INSTANCE, MachineBlock.class);
        registration.registerBlockComponent(PipeComponentProvider.INSTANCE, PipeBlock.class);
        // Harvest-tool icon: draw a wrench for wrench-mineable blocks (machines show only this;
        // pipes show it next to the built-in pickaxe since they stay pickaxe-mineable).
        HarvestToolProvider.registerHandler(() -> WrenchToolHandler.INSTANCE);
    }
}
