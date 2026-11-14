package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class MaterialForm {

    private final Identifier id;
    private final MaterialFormStrategy strategy;
    private final @Nullable LangKey nameLang;
    private final Predicate<Material> applicability;
    private final Set<MaterialDataType<?>> requiredData;

    public MaterialForm(Identifier id, MaterialFormStrategy strategy, @Nullable LangKey nameLang) {
        this(id, strategy, nameLang, material -> false, Set.of());
    }

    public MaterialForm(Identifier id, MaterialFormStrategy strategy, @Nullable LangKey nameLang,
                        Predicate<Material> applicability) {
        this(id, strategy, nameLang, applicability, Set.of());
    }

    public MaterialForm(Identifier id, MaterialFormStrategy strategy, @Nullable LangKey nameLang,
                        Predicate<Material> applicability, Set<MaterialDataType<?>> requiredData) {
        this.id = Objects.requireNonNull(id, "material form id");
        this.strategy = Objects.requireNonNull(strategy, "material form strategy");
        this.nameLang = nameLang;
        this.applicability = Objects.requireNonNull(applicability, "material form applicability");
        this.requiredData = Set.copyOf(Objects.requireNonNull(requiredData, "required data"));
    }

    /** Evaluated after all plugins have declared materials, before content registration. */
    public boolean appliesTo(Material material) {
        return applicability.test(material);
    }

    /**
     * Material-level data types that must be present before {@link #appliesTo} is evaluated. This is
     * an optional matching index hint, not a replacement for the predicate.
     */
    public Set<MaterialDataType<?>> requiredData() {
        return requiredData;
    }

    public Identifier id() {
        return id;
    }

    public MaterialFormStrategy strategy() {
        return strategy;
    }

    /** Registered display-name handle when the form declared {@code lang(...)}. */
    public @Nullable LangKey nameLang() {
        return nameLang;
    }

    public Component displayName() {
        if (nameLang == null) {
            throw new IllegalStateException("material form " + id + " has no registered display name");
        }
        return nameLang.getComponent();
    }
}
