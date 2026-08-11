package net.ptcrys.topo.apiv2.material.form;

import net.ptcrys.topo.api.lang.LangKey;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class MaterialForm {

    private final Identifier id;
    private final MaterialFormStrategy strategy;
    private final @Nullable LangKey nameLang;

    public MaterialForm(Identifier id, MaterialFormStrategy strategy, @Nullable LangKey nameLang) {
        this.id = Objects.requireNonNull(id, "material form id");
        this.strategy = Objects.requireNonNull(strategy, "material form strategy");
        this.nameLang = nameLang;
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
