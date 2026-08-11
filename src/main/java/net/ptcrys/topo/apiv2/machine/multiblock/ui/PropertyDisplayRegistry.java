package net.ptcrys.topo.apiv2.machine.multiblock.ui;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.List;
import java.util.Map;

/**
 * Canonical KHS registry owner for {@link PropertyDisplay}. The display is its own strategy
 * (H == S): it executes with the handle alone.
 *
 * <p>
 * The two typed methods are the ONLY registration doors — enum and boolean properties are the
 * displayable kinds, nothing else registers. Registration is single-threaded bootstrap and must
 * complete before {@link #freeze()}; runtime reads are served from the frozen snapshot.
 */
public final class PropertyDisplayRegistry {

    private static final FreezableStrategyRegistry<Identifier, PropertyDisplay, PropertyDisplay> REGISTRY = FreezableStrategyRegistry.create("property displays");

    private PropertyDisplayRegistry() {}

    /**
     * Single write entry for {@link net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration#propertyEnum}.
     * Product code must not call this.
     */
    @SafeVarargs
    public static synchronized <T extends Enum<T> & StringRepresentable> PropertyDisplay beginEnum(
                                                                                                   Identifier id, Component label, Map<T, Component> valueTexts, EnumProperty<T>... properties) {
        PropertyDisplay display = PropertyDisplays.ofEnum(id, label, valueTexts, properties);
        return REGISTRY.register(id, display, display);
    }

    /**
     * Single write entry for {@link net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration#propertyBoolean}.
     * Product code must not call this.
     */
    public static synchronized PropertyDisplay beginBoolean(
                                                            Identifier id, Component label, Component whenTrue, Component whenFalse, BooleanProperty... properties) {
        PropertyDisplay display = PropertyDisplays.ofBoolean(id, label, whenTrue, whenFalse, properties);
        return REGISTRY.register(id, display, display);
    }

    public static PropertyDisplay require(Identifier id) {
        return REGISTRY.require(id);
    }

    /** Frozen-view driver access for the same-package {@link PropertyDisplays} lookup; not public API. */
    static List<PropertyDisplay> handlesView() {
        return REGISTRY.handlesView();
    }

    public static synchronized void freeze() {
        REGISTRY.freeze();
    }
}
