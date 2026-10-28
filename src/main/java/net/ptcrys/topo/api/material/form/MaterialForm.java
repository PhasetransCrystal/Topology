package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.material.Material;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public final class MaterialForm {

    private final Identifier id;
    private final MaterialFormStrategy strategy;
    private final @Nullable LangKey nameLang;
    private final Predicate<Material> applicability;

    public MaterialForm(Identifier id, MaterialFormStrategy strategy, @Nullable LangKey nameLang) {
        this(id, strategy, nameLang, material -> false);
    }

    public MaterialForm(Identifier id, MaterialFormStrategy strategy, @Nullable LangKey nameLang,
                        Predicate<Material> applicability) {
        this.id = Objects.requireNonNull(id, "material form id");
        this.strategy = Objects.requireNonNull(strategy, "material form strategy");
        this.nameLang = nameLang;
        this.applicability = Objects.requireNonNull(applicability, "material form applicability");
    }

    /** Evaluated after all plugins have declared materials, before content registration. */
    public boolean appliesTo(Material material) {
        return applicability.test(material);
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
