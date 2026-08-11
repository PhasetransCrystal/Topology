package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.form.FormDataRegistry;
import net.ptcrys.topo.api.material.form.FormDataType;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.material.process.MaterialPostProcessorRegistry;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole material-domain registration entry for one {@link TopoPlugin}. Paths are bare; namespace is
 * fixed by the plugin. No overloads — one method per concern.
 */
public final class MaterialDomainRegistration {

    private final TopoPlugin plugin;
    private final RegistryCore registry;

    private MaterialDomainRegistration(TopoPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static MaterialDomainRegistration of(TopoPlugin plugin) {
        return new MaterialDomainRegistration(plugin, plugin.registry());
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

    /** Begin a material declaration under this plugin's namespace. */
    public MaterialRegistry.Builder material(String path) {
        return MaterialRegistry.begin(id(path), plugin.lang());
    }

    /** Begin a material-form declaration under this plugin's namespace. */
    public MaterialFormRegistry.Builder form(String path) {
        return MaterialFormRegistry.begin(id(path), plugin.lang());
    }

    /** Register a material data type (mass, colors, tool stats, …). */
    public <D, H extends MaterialDataType<D>> H dataType(String path, H handle) {
        return MaterialDataRegistry.begin(id(path), handle, MaterialDataRegistry.noValidation());
    }

    /** Register a form payload type (e.g. amount). */
    public <D, H extends FormDataType<D>> H formDataType(String path, H handle) {
        return FormDataRegistry.begin(id(path), handle);
    }

    /** Register a material post-processor; {@code processor.id()} must equal {@link #id(path)}. */
    public <P extends MaterialPostProcessor> P postProcessor(String path, P processor) {
        Identifier key = id(path);
        if (!key.equals(processor.id())) {
            throw new IllegalArgumentException(
                    "material post-processor id mismatch: plugin path " + key + " != processor id " + processor.id());
        }
        return MaterialPostProcessorRegistry.begin(key, processor);
    }
}
