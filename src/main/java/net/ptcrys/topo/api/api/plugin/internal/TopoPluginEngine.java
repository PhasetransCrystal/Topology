package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Objects;
import java.util.function.Supplier;

/** Host-only NeoForge adapter. Dependent mods only call {@link TopoPlugins#register}. */
public final class TopoPluginEngine {

    private static final PluginLifecycle LIFECYCLE = new PluginLifecycle();
    private static RegistrationPlan plan;
    private static Supplier<PluginSnapshot> snapshot;

    private TopoPluginEngine() {}

    public static synchronized void register(IEventBus bus, Supplier<PluginSnapshot> snapshotFactory) {
        Objects.requireNonNull(bus, "host event bus");
        if (snapshot != null) throw new IllegalStateException("Topo plugin engine already attached to a host");
        snapshot = Objects.requireNonNull(snapshotFactory, "plugin snapshot factory");
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
            plan = TopoContentBootstrap.compile(snapshot.get(), TopoContentBootstrap.MODULES);
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
