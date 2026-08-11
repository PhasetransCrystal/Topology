package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.ore.OreVeins;
import net.ptcrys.topo.api.ore.display.OreVeinDisplay;
import net.ptcrys.topo.api.ore.display.OreVeinDisplays;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.api.ore.mode.OreVeinModes;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicies;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicy;
import net.ptcrys.topo.api.ore.policy.OreConflictPolicies;
import net.ptcrys.topo.api.ore.policy.OreConflictPolicy;
import net.ptcrys.topo.api.ore.shape.OreVeinShape;
import net.ptcrys.topo.api.ore.shape.OreVeinShapes;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole ore-domain registration entry for one {@link TopoPlugin}. Paths are bare; namespace is fixed by
 * the plugin. No overloads — one method per concern.
 */
public final class OreDomainRegistration {

    private final TopoPlugin plugin;
    private final RegistryCore registry;

    private OreDomainRegistration(TopoPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static OreDomainRegistration of(TopoPlugin plugin) {
        return new OreDomainRegistration(plugin, plugin.registry());
    }

    public TopoPlugin plugin() {
        return plugin;
    }

    public RegistryCore registry() {
        return registry;
    }

    public String modId() {
        return plugin.modId();
    }

    public Identifier id(String path) {
        return PluginIds.id(plugin, path);
    }

    public OreVeins.Builder vein(String path) {
        return OreVeins.begin(id(path), plugin.lang());
    }

    public OreVeinShape shape(String path, OreVeinShape.Strategy strategy) {
        return OreVeinShapes.begin(id(path), strategy);
    }

    public OreVeinMode mode(String path, OreVeinMode.Strategy strategy) {
        return OreVeinModes.begin(id(path), strategy);
    }

    public OreAirExposurePolicy airExposure(String path, OreAirExposurePolicy.Strategy strategy) {
        return OreAirExposurePolicies.begin(id(path), strategy);
    }

    public OreConflictPolicy conflict(String path, OreConflictPolicy.Strategy strategy) {
        return OreConflictPolicies.begin(id(path), strategy);
    }

    /** Display plug keyed by placement-mode id (same id space as {@link #mode}). */
    public OreVeinDisplay display(Identifier modeId, OreVeinDisplay.Strategy strategy) {
        return OreVeinDisplays.begin(modeId, strategy);
    }
}
