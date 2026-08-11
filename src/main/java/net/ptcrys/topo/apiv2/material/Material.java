package net.ptcrys.topo.apiv2.material;

import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.apiv2.lang.DisplayNameSource;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public final class Material implements DisplayNameSource {

    private final Identifier id;
    private final MaterialStrategy strategy;
    private final String displayNameEn;
    private final String displayNameCn;
    private final LangKey nameLang;

    public Material(
                    Identifier id,
                    MaterialStrategy strategy,
                    String displayNameEn,
                    String displayNameCn,
                    LangKey nameLang) {
        this.id = Objects.requireNonNull(id, "material id");
        this.strategy = Objects.requireNonNull(strategy, "material strategy");
        this.displayNameEn = Objects.requireNonNull(displayNameEn, "material English display name");
        this.displayNameCn = Objects.requireNonNull(displayNameCn, "material Chinese display name");
        this.nameLang = Objects.requireNonNull(nameLang, "material name lang");
    }

    public Identifier id() {
        return id;
    }

    public MaterialStrategy strategy() {
        return strategy;
    }

    @Override
    public String displayNameEn() {
        return displayNameEn;
    }

    @Override
    public String displayNameCn() {
        return displayNameCn;
    }

    /** Registered display-name handle ({@code material.<ns>.<path>}). */
    public LangKey nameLang() {
        return nameLang;
    }

    public Component displayName() {
        return nameLang.getComponent();
    }
}
