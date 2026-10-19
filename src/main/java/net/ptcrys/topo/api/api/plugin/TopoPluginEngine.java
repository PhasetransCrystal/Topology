package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Objects;

/** Host-only NeoForge adapter. Dependent mods only call {@link TopoPlugins#register}. */
public final class TopoPluginEngine {

    private static final PluginLifecycle LIFECYCLE = new PluginLifecycle();
    private static RegistrationPlan plan;
    private static boolean attached;

    private TopoPluginEngine() {}

    public static synchronized void register(IEventBus bus) {
        Objects.requireNonNull(bus, "host event bus");
        if (attached) throw new IllegalStateException("Topo plugin engine already attached to a host");
        attached = true;
        bus.addListener(EventPriority.HIGHEST, TopoPluginEngine::onRegister);
        bus.addListener(TopoPluginEngine::onCommonSetup);
        bus.addListener(TopoPluginEngine::onLoadComplete);
        for (RegistrationModule module : TopoContentBootstrap.MODULES) module.attach(bus);
    }

    private static void onRegister(RegisterEvent event) {
        bootstrapContent();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TopoPluginEngine::bootstrapRuntime);
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(TopoPluginEngine::freezeRuntime);
    }

    // Package-private so external callers cannot start global registration in a mod constructor.
    static synchronized void bootstrapContent() {
        LIFECYCLE.run(Phase.CONTENT, () -> {
            plan = TopoContentBootstrap.compile(TopoPlugins.lock(), TopoContentBootstrap.MODULES);
            plan.execute(Phase.CONTENT);
        });
        LIFECYCLE.run(Phase.DERIVED, () -> plan.execute(Phase.DERIVED));
        LIFECYCLE.run(Phase.LANGUAGE, () -> plan.execute(Phase.LANGUAGE));
    }

    static synchronized void bootstrapRuntime() {
        LIFECYCLE.run(Phase.RUNTIME, () -> plan.execute(Phase.RUNTIME));
    }

    static synchronized void freezeRuntime() {
        LIFECYCLE.run(Phase.READY, () -> plan.execute(Phase.READY));
    }
}
