package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.pipe.ui.PipeSpecTooltips;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Objects;

/**
 * Host event adapter and phase coordinator. {@link PluginLifecycle} owns startup state;
 * {@link TopoContentBootstrap} owns domain declarations and freeze fences.
 * Dependent mods contribute content through plugins and runtime bindings through setup events.
 *
 * <p>
 * The host calls {@link #register(IEventBus)} once; dependent mods only register plugins in
 * their constructors. Content runs on the first {@code RegisterEvent} at {@code HIGHEST}, before
 * RegistryLib {@code LOW}. Built-in runtime bindings run in common-setup deferred work; the shared runtime
 * tables freeze in load-complete deferred work, after every mod's common and sided setup.
 */
public final class TopoPluginEngine {

    private static final PluginLifecycle LIFECYCLE = new PluginLifecycle();
    private static boolean eventsRegistered;

    private TopoPluginEngine() {}

    /**
     * Locks in already-{@link TopoPlugins#register registered} plugins then runs recipe foundation →
     * material → equipment. Retained for callers using the explicit phase API; the host's
     * {@link #bootstrapContent()} runs the remaining content phases. Only completed stages are
     * idempotent; failed startup cannot be retried in the same process.
     */
    public static synchronized void prepare() {
        bootstrapRecipe();
        bootstrapMaterial();
        bootstrapEquipment();
    }

    /** Attach once to the host mod bus. Dependent mods only register their plugins. */
    public static synchronized void register(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "mod event bus");
        if (eventsRegistered) {
            throw new IllegalStateException("TopoPluginEngine: lifecycle listeners already registered by a host");
        }
        modEventBus.addListener(EventPriority.HIGHEST, TopoPluginEngine::onRegister);
        modEventBus.addListener(TopoPluginEngine::onCommonSetup);
        modEventBus.addListener(TopoPluginEngine::onLoadComplete);
        eventsRegistered = true;
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

    /** Runtime and datagen use the same ordered content pipeline, before RegistryLib LOW. */
    public static synchronized void bootstrapContent() {
        prepare();
        bootstrapMachine();
        bootstrapOre();
        bootstrapLang();
    }

    /** Bind built-in pipe tooltips once; dependent mods use their own setup deferred work. */
    public static synchronized void bootstrapRuntime() {
        LIFECYCLE.run(Phase.RUNTIME, PipeSpecTooltips::registerProviders);
    }

    /** Engine-owned fence after all mods have finished common-setup deferred work. */
    public static synchronized void freezeRuntime() {
        LIFECYCLE.run(Phase.READY, () -> {
            ItemTooltipUis.freeze();
            MachineItemBehaviors.freeze();
        });
    }

    public static synchronized void bootstrapRecipe() {
        LIFECYCLE.run(Phase.RECIPE, TopoContentBootstrap::bootstrapRecipe);
    }

    public static synchronized void bootstrapMaterial() {
        LIFECYCLE.run(Phase.MATERIAL, TopoContentBootstrap::bootstrapMaterial);
    }

    public static synchronized void bootstrapEquipment() {
        LIFECYCLE.run(Phase.EQUIPMENT, TopoContentBootstrap::bootstrapEquipment);
    }

    public static synchronized void bootstrapMachine() {
        LIFECYCLE.run(Phase.MACHINE, TopoContentBootstrap::bootstrapMachine);
    }

    public static synchronized void bootstrapOre() {
        LIFECYCLE.run(Phase.ORE, TopoContentBootstrap::bootstrapOre);
    }

    public static synchronized void bootstrapLang() {
        LIFECYCLE.run(Phase.LANG, TopoContentBootstrap::bootstrapLang);
    }
}
