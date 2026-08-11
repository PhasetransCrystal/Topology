package net.ptcrys.topo.apiv2.material.form;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Material-form handle table (query + freeze). Product writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.MaterialDomainRegistration#form}.
 */
public final class MaterialFormRegistry {

    private static final FreezableStrategyRegistry<Identifier, MaterialForm, MaterialFormStrategy> REGISTRY = FreezableStrategyRegistry.create("material forms");

    private MaterialFormRegistry() {}

    /** Single write entry for the material domain plugin API. */
    public static Builder begin(Identifier id, LangDomainRegistration lang) {
        return new Builder(Objects.requireNonNull(id, "id"), Objects.requireNonNull(lang, "lang"));
    }

    public static MaterialForm require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<MaterialForm> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    public static final class Builder {

        private final Identifier id;
        private final LangDomainRegistration langApi;
        private MaterialFormStrategy strategy;
        private String langEn;
        private String langCn;

        private Builder(Identifier id, LangDomainRegistration langApi) {
            this.id = id;
            this.langApi = langApi;
        }

        public Builder lang(String en, String cn) {
            this.langEn = Objects.requireNonNull(en, "en");
            this.langCn = Objects.requireNonNull(cn, "cn");
            return this;
        }

        public Builder strategy(MaterialFormStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        public MaterialForm build() {
            MaterialFormStrategy registeredStrategy = Objects.requireNonNull(strategy, "material form requires strategy");
            if (langEn == null || langCn == null) {
                throw new IllegalStateException(
                        "material form " + id + " requires lang(en, cn); both languages are mandatory");
            }
            LangKey nameLang = langApi.resource(id, "form", langEn, langCn);
            MaterialForm form = new MaterialForm(id, registeredStrategy, nameLang);
            return REGISTRY.register(id, form, registeredStrategy);
        }
    }
}
