package net.ptcrys.topo.apiv2.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.apiv2.equipment.Equipment;
import net.ptcrys.topo.apiv2.equipment.EquipmentRegistry;
import net.ptcrys.topo.apiv2.equipment.EquipmentStrategy;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole equipment/tool-domain registration entry for one {@link OIPlugin}. Paths are bare; namespace
 * is fixed by the plugin. No overloads.
 */
public final class EquipmentDomainRegistration {

    private final OIPlugin plugin;
    private final RegistryCore registry;

    private EquipmentDomainRegistration(OIPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static EquipmentDomainRegistration of(OIPlugin plugin) {
        return new EquipmentDomainRegistration(plugin, plugin.registry());
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

    /** Register one equipment kind (tool / armor / surveyor / …). */
    public Equipment kind(String path, EquipmentStrategy strategy) {
        return EquipmentRegistry.begin(id(path), strategy);
    }
}
