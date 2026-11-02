package net.ptcrys.topodemo;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.ore.common.VanillaOreDatagenProvider;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Local development mod; content added here is isolated from Topo's published library. */
@Mod(TopoDemo.MOD_ID)
public final class TopoDemo {

    public static final String MOD_ID = "topodemo";
    public static final RegistryCore REGISTRY = RegistryCore.create(MOD_ID);

    public TopoDemo(IEventBus bus) {
        TopoPlugins.register(TopoDemoApiPlugin.INSTANCE);
        TopoPlugins.register(TopoDemoPlugin.INSTANCE);
        bus.addListener((GatherDataEvent.Client event) -> event.getGenerator().addProvider(true, new VanillaOreDatagenProvider(event)));
    }
}
