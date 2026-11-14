package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.material.form.MaterialForm;

import java.util.Objects;

/** A material/form pair that produced one indexed content entry. */
public record MaterialBinding(Material material, MaterialForm form) {

    public MaterialBinding {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(form, "form");
    }
}
