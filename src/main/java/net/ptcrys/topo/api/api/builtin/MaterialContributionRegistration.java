package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.topo.api.api.plugin.RegistrationDomain;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialFormOptions;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Deterministic contribution window: runs after every plugin has built its materials and before
 * form matching freezes. Contribute capabilities with {@link #data} and form-local configuration
 * with {@link #form}; the target material may belong to any plugin.
 */
public final class MaterialContributionRegistration {

    public static final RegistrationDomain<MaterialContributionRegistration> DOMAIN = RegistrationDomain.of(MaterialContributionRegistration::of);

    private final TopoPlugin plugin;

    private MaterialContributionRegistration(TopoPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    private static MaterialContributionRegistration of(TopoPlugin plugin) {
        return new MaterialContributionRegistration(plugin);
    }

    public TopoPlugin plugin() {
        return plugin;
    }

    public List<Material> materials() {
        return MaterialRegistry.registered();
    }

    public Material require(Identifier id) {
        return MaterialRegistry.require(id);
    }

    /** Add one capability value to any already-built material. */
    public void data(Material material, MaterialDataUse<?> use) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(use, "use");
        try {
            material.data(use);
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "plugin " + plugin.modId() + " failed to contribute data " + use.type().id() + " to material " + material.id() + ": " + error.getMessage(),
                    error);
        }
    }

    /** Configure one form's local options for an already-built material. */
    public void form(Material material, MaterialForm form, Consumer<MaterialFormOptions> configure) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(configure, "configure");
        try {
            material.configureForm(form, configure);
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "plugin " + plugin.modId() + " failed to configure form " + form.id() + " on material " + material.id() + ": " + error.getMessage(),
                    error);
        }
    }
}
