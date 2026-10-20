package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Host-only NeoForge adapter. Dependent mods only call {@link TopoPlugins#register}. */
final class TopoPluginEngine {

    private static TopoPluginEngine host;

    private final PluginLifecycle lifecycle = new PluginLifecycle();
    private final Supplier<PluginSnapshot> snapshot;
    private final List<RegistrationModule> modules;
    private RegistrationPlan plan;

    TopoPluginEngine(Supplier<PluginSnapshot> snapshot, List<RegistrationModule> modules) {
        this.snapshot = Objects.requireNonNull(snapshot, "plugin snapshot factory");
        this.modules = List.copyOf(modules);
    }

    static synchronized void register(IEventBus bus, Supplier<PluginSnapshot> snapshotFactory) {
        Objects.requireNonNull(bus, "host event bus");
        if (host != null) throw new IllegalStateException("Topo plugin engine already attached to a host");
        host = new TopoPluginEngine(snapshotFactory, TopoContentBootstrap.MODULES);
        bus.addListener(EventPriority.HIGHEST, host::onRegister);
        bus.addListener(host::onCommonSetup);
        bus.addListener(host::onLoadComplete);
        for (RegistrationModule module : host.modules) module.attach(bus);
    }

    static synchronized TopoPluginEngine host() {
        return Objects.requireNonNull(host, "Topo plugin engine has no host");
    }

    private void onRegister(RegisterEvent event) {
        bootstrapContent();
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::bootstrapRuntime);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(this::freezeRuntime);
    }

    // Package-private so external callers cannot start global registration in a mod constructor.
    synchronized void bootstrapContent() {
        lifecycle.run(Phase.CONTENT, () -> {
            plan = TopoContentBootstrap.compile(snapshot.get(), modules);
            plan.execute(Phase.CONTENT);
        });
        lifecycle.run(Phase.DERIVED, () -> plan.execute(Phase.DERIVED));
        lifecycle.run(Phase.LANGUAGE, () -> plan.execute(Phase.LANGUAGE));
    }

    synchronized void bootstrapRuntime() {
        lifecycle.run(Phase.RUNTIME, () -> plan.execute(Phase.RUNTIME));
    }

    synchronized void freezeRuntime() {
        lifecycle.run(Phase.READY, () -> plan.execute(Phase.READY));
    }
}
