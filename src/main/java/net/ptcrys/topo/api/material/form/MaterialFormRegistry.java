package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.api.api.annotation.SyntaxSugar;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.material.Material;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Material-form handle table (query + freeze). Product writes only via
 * {@link MaterialDomainRegistration#form}.
 */
public final class MaterialFormRegistry {

    private static final FreezableStrategyRegistry<Identifier, MaterialForm, MaterialFormStrategy> REGISTRY = FreezableStrategyRegistry.create("material forms");

    private MaterialFormRegistry() {}

    /** Single write entry for the material domain plugin API. */
    @ApiStatus.Internal
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

    @ApiStatus.Internal
    public static void freeze() {
        REGISTRY.freeze();
    }

    public static final class Builder {

        private final Identifier id;
        private final LangDomainRegistration langApi;
        private MaterialFormStrategy strategy;
        private String langEn;
        private String langCn;
        private Predicate<Material> applicability = material -> false;

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

        /** Defaults to false. Select by declared data; do not depend on other automatic forms. */
        public Builder appliesTo(Predicate<Material> applicability) {
            this.applicability = Objects.requireNonNull(applicability, "applicability");
            return this;
        }

        /** Select every material, including materials contributed by other plugins. */
        @SyntaxSugar(
                     forMethod = "appliesTo(Predicate<Material> applicability)",
                     reason = "Selects all materials by supplying a predicate that always returns true.")
        public Builder appliesToAll() {
            return appliesTo(material -> true);
        }

        public MaterialForm build() {
            MaterialFormStrategy registeredStrategy = Objects.requireNonNull(strategy, "material form requires strategy");
            if (langEn == null || langCn == null) {
                throw new IllegalStateException(
                        "material form " + id + " requires lang(en, cn); both languages are mandatory");
            }
            LangKey nameLang = langApi.resource(id, "form", langEn, langCn);
            MaterialForm form = new MaterialForm(id, registeredStrategy, nameLang, applicability);
            return REGISTRY.register(id, form, registeredStrategy);
        }
    }
}
