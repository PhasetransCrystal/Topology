package net.ptcrys.topo.apiv2.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.apiv2.ore.OreVeins;
import net.ptcrys.topo.apiv2.ore.display.OreVeinDisplay;
import net.ptcrys.topo.apiv2.ore.display.OreVeinDisplays;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinModes;
import net.ptcrys.topo.apiv2.ore.policy.OreAirExposurePolicies;
import net.ptcrys.topo.apiv2.ore.policy.OreAirExposurePolicy;
import net.ptcrys.topo.apiv2.ore.policy.OreConflictPolicies;
import net.ptcrys.topo.apiv2.ore.policy.OreConflictPolicy;
import net.ptcrys.topo.apiv2.ore.shape.OreVeinShape;
import net.ptcrys.topo.apiv2.ore.shape.OreVeinShapes;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole ore-domain registration entry for one {@link OIPlugin}. Paths are bare; namespace is fixed by
 * the plugin. No overloads — one method per concern.
 */
public final class OreDomainRegistration {

    private final OIPlugin plugin;
    private final RegistryCore registry;

    private OreDomainRegistration(OIPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static OreDomainRegistration of(OIPlugin plugin) {
        return new OreDomainRegistration(plugin, plugin.registry());
    }

    public OIPlugin plugin() {
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
