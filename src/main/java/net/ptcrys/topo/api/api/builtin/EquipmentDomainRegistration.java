package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.equipment.EquipmentStrategy;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole equipment/tool-domain registration entry for one {@link TopoPlugin}. Paths are bare; namespace
 * is fixed by the plugin. No overloads.
 */
public final class EquipmentDomainRegistration {

    private final TopoPlugin plugin;
    private final RegistryCore registry;

    private EquipmentDomainRegistration(TopoPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static EquipmentDomainRegistration of(TopoPlugin plugin) {
        return new EquipmentDomainRegistration(plugin, plugin.registry());
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

    /** Register one equipment kind (tool / armor / surveyor / …). */
    public Equipment kind(String path, EquipmentStrategy strategy) {
        return EquipmentRegistry.begin(id(path), strategy);
    }
}
