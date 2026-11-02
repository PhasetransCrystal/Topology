package net.ptcrys.topo.helper;

import net.ptcrys.topo.api.api.registration.ExternalBlockTarget;
import net.ptcrys.topo.api.api.registration.ExternalFluidTarget;
import net.ptcrys.topo.api.api.registration.ExternalItemTarget;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialFormOptions;
import net.ptcrys.topo.api.material.MaterialFormTargets;
import net.ptcrys.topo.api.material.form.MaterialContentKey;
import net.ptcrys.topo.api.material.form.MaterialContentTypes;
import net.ptcrys.topo.api.material.form.MaterialForm;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class MaterialHelper {

    private MaterialHelper() {}

    /**
     * Resolves the registered item backing a material form, including item and block overrides.
     *
     * <p>
     * Only safe after the item registry is frozen / populated. During material post-processors
     * (RegisterEvent HIGHEST) prefer {@link #materialItemSupplier} and {@link #itemId}.
     */
    public static Optional<Item> item(Material material, MaterialForm form) {
        if (!material.hasForm(form)) {
            return Optional.empty();
        }

        return BuiltInRegistries.ITEM.getOptional(itemId(material, form));
    }

    /**
     * Stable registry id for a material form's primary item (including overrides). Once forms are
     * frozen the plan's item output is authoritative; before freeze this falls back to declared
     * external targets and then to the form's registry-path convention.
     */
    public static Identifier itemId(Material material, MaterialForm form) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(form, "form");
        if (material.hasForm(form)) {
            for (MaterialContentKey key : material.formPlan(form).outputs()) {
                if (key.type().equals(MaterialContentTypes.ITEM)) {
                    return key.id();
                }
            }
        }
        MaterialFormTargets targets = targets(material, form);
        Optional<Identifier> overrideId = targets.item()
                .map(ExternalItemTarget::id)
                .or(() -> targets.block().map(ExternalBlockTarget::itemId));
        return overrideId.orElseGet(() -> material.id().withPath(String.format(form.strategy().registryPath(), material.id().getPath())));
    }

    public static String itemPath(Material material, MaterialForm form) {
        return itemId(material, form).getPath();
    }

    public static boolean isMinecraftNamespace(Material material, MaterialForm form) {
        return "minecraft".equals(itemId(material, form).getNamespace());
    }

    /** Formats a material path as a display name: {@code "stainless_steel"} becomes {@code "Stainless Steel"}. */
    public static String displayName(String materialPath) {
        StringBuilder result = new StringBuilder(materialPath.length());
        for (String word : materialPath.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return result.toString();
    }

    public static Item requireItem(Material material, MaterialForm form) {
        return item(material, form)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing item for material form " + material.id() + " / " + form.id()));
    }

    public static Supplier<Item> materialItemSupplier(Material material, MaterialForm form) {
        return new MaterialItemSupplier(material, form);
    }

    /** Resolves the registered source fluid backing a material fluid form. */
    public static Optional<Fluid> fluid(Material material, MaterialForm form) {
        if (!material.hasForm(form)) {
            return Optional.empty();
        }

        for (MaterialContentKey key : material.formPlan(form).outputs()) {
            if (key.type().equals(MaterialContentTypes.FLUID)) {
                return BuiltInRegistries.FLUID.getOptional(key.id());
            }
        }
        Optional<ExternalFluidTarget> override = targets(material, form).fluid();
        if (override.isPresent()) {
            return BuiltInRegistries.FLUID.getOptional(override.get().id());
        }
        Identifier fluidId = material.id().withPath(String.format(form.strategy().registryPath(), material.id().getPath()));
        return BuiltInRegistries.FLUID.getOptional(fluidId);
    }

    public static Fluid requireFluid(Material material, MaterialForm form) {
        return fluid(material, form)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing fluid for material form " + material.id() + " / " + form.id()));
    }

    public static Supplier<Fluid> materialFluidSupplier(Material material, MaterialForm form) {
        return new MaterialFluidSupplier(material, form);
    }

    private static MaterialFormTargets targets(Material material, MaterialForm form) {
        if (material.hasForm(form)) {
            return material.formPlan(form).targets();
        }
        return material.optionsFor(form)
                .map(MaterialFormOptions::targets)
                .orElseGet(MaterialFormTargets::empty);
    }

    private record MaterialItemSupplier(Material material, MaterialForm form) implements Supplier<Item> {

        private MaterialItemSupplier {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(form, "form");
        }

        @Override
        public Item get() {
            return requireItem(material, form);
        }
    }

    private record MaterialFluidSupplier(Material material, MaterialForm form) implements Supplier<Fluid> {

        private MaterialFluidSupplier {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(form, "form");
        }

        @Override
        public Fluid get() {
            return requireFluid(material, form);
        }
    }
}
