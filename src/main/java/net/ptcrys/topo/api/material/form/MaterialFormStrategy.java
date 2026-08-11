package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.data.MaterialDataType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class MaterialFormStrategy {

    private final Map<FormDataType<?>, FormDataUse<?>> data;

    protected MaterialFormStrategy(Collection<FormDataUse<?>> data) {
        Map<FormDataType<?>, FormDataUse<?>> byType = new java.util.LinkedHashMap<>();
        for (FormDataUse<?> use : data) {
            if (byType.put(use.type(), use) != null) {
                throw new IllegalStateException(
                        "duplicate form data declaration for type " + use.type().id() + "; a form declares each payload type at most once");
            }
        }
        this.data = byType;
    }

    /** The typed payload declared for {@code type} on this form, or empty when not declared. */
    @SuppressWarnings("unchecked")
    public final <D> Optional<D> data(FormDataType<D> type) {
        FormDataUse<?> use = data.get(type);
        return use == null ? Optional.empty() : Optional.of((D) use.data());
    }

    public final <D> D requireData(FormDataType<D> type) {
        return data(type).orElseThrow(() -> new IllegalStateException(
                "form strategy '" + registryPath() + "' does not declare form data " + type.id()));
    }

    /**
     * The registry-path format string for this form, e.g. {@code "%s_ingot"} or {@code "%s_ore"}.
     *
     * <p>
     * Exposed on the base type so cross-form, material-level steps (such as recipe generation)
     * can derive the registered item id of any form without branching on whether the form is an
     * item or a block; that split lives in the concrete strategy implementations.
     */
    public abstract String registryPath();

    /**
     * Fail-fast checks for one material that declares this form, run when the material registry
     * freezes: the material must declare every material data type the form's render requires, and
     * may only use the override shape matching the form kind.
     */
    public abstract void validateMaterial(Material material, MaterialForm form);

    public abstract void register(MaterialContentContext context, MaterialForm form);

    /** Throws unless {@code material} declares every data type in {@code required}. */
    protected final void requireMaterialData(Material material, MaterialForm form,
                                             List<MaterialDataType<?>> required) {
        for (MaterialDataType<?> type : required) {
            if (material.strategy().data(type).isEmpty()) {
                throw new IllegalStateException(
                        "material " + material.id() + " is missing data " + type.id() + " required by the render of form " + form.id());
            }
        }
    }

    /** Throws unless {@code value} is a non-null format pattern containing {@code %s}. */
    protected static void requirePattern(String value, String field) {
        Objects.requireNonNull(value, () -> "material form strategy requires " + field);
        if (!value.contains("%s")) {
            throw new IllegalArgumentException("material form " + field + " must contain %s: " + value);
        }
    }

    /** Formats a material path as a display name: {@code "stainless_steel"} becomes {@code "Stainless Steel"}. */
    protected static String formatDisplayName(String materialPath) {
        return net.ptcrys.topo.helper.MaterialHelper.displayName(materialPath);
    }
}
