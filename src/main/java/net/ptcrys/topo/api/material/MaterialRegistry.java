package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.annotation.SyntaxSugar;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class MaterialRegistry {

    private static final FreezableStrategyRegistry<Identifier, Material, MaterialStrategy> REGISTRY = FreezableStrategyRegistry.create("materials");
    private static final List<Material> PENDING_VALIDATION = new ArrayList<>();

    private MaterialRegistry() {}

    /**
     * Internal write entry used only by {@link MaterialDomainRegistration}.
     * Product code must use {@code plugin.material().material(path)}.
     */
    public static Builder begin(Identifier id, LangDomainRegistration lang) {
        return new Builder(Objects.requireNonNull(id, "id"), Objects.requireNonNull(lang, "lang"));
    }

    public static Material require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<Material> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        if (!REGISTRY.isFrozen()) {
            for (Material material : PENDING_VALIDATION) {
                validate(material);
            }
        }
        REGISTRY.freeze();
        PENDING_VALIDATION.clear();
    }

    static void validate(Material material) {
        MaterialStrategy strategy = material.strategy();
        for (MaterialDataUse<?> use : strategy.dataUses()) {
            MaterialDataRegistry.validate(material, use);
        }
        for (MaterialFormOptions options : strategy.formOptions()) {
            for (MaterialDataUse<?> use : options.dataUses()) {
                MaterialDataRegistry.validate(material, use);
            }
        }
        for (MaterialForm form : strategy.forms()) {
            form.strategy().validateMaterial(material, form);
        }
    }

    public static final class Builder {

        private final Identifier id;
        private final LangDomainRegistration langApi;
        private final java.util.Map<MaterialDataType<?>, MaterialDataUse<?>> data = new java.util.LinkedHashMap<>();
        private final java.util.List<MaterialForm> forms = new java.util.ArrayList<>();
        private final java.util.Map<MaterialForm, MaterialFormOptions> options = new java.util.LinkedHashMap<>();
        private String langEn;
        private String langCn;

        private Builder(Identifier id, LangDomainRegistration langApi) {
            this.id = id;
            this.langApi = langApi;
        }

        public Builder lang(String en, String cn) {
            this.langEn = requireText(en, "en");
            this.langCn = requireText(cn, "cn");
            return this;
        }

        @SyntaxSugar(
                     forMethod = "form(MaterialForm form, Consumer<MaterialFormOptions> options)",
                     reason = "Adds each MaterialForm in order with no scoped options.")
        public Builder forms(MaterialForm... forms) {
            for (MaterialForm form : forms) {
                form(form);
            }
            return this;
        }

        @SyntaxSugar(
                     forMethod = "form(MaterialForm form, Consumer<MaterialFormOptions> options)",
                     reason = "Declares the form with no scoped Material+Form options.")
        public Builder form(MaterialForm form) {
            return form(form, options -> {});
        }

        public Builder form(MaterialForm form, Consumer<MaterialFormOptions> options) {
            if (!forms.contains(form)) {
                forms.add(form);
            }
            MaterialFormOptions formOptions = this.options.computeIfAbsent(form, key -> new MaterialFormOptions());
            options.accept(formOptions);
            return this;
        }

        public Builder data(MaterialDataUse<?> data) {
            if (this.data.containsKey(data.type())) {
                throw new IllegalStateException("duplicate material data type " + data.type().id() + " for " + id);
            }
            this.data.put(data.type(), data);
            return this;
        }

        public Material build() {
            if (langEn == null || langCn == null) {
                throw new IllegalStateException(
                        "material " + id + " requires lang(en, cn); both languages are mandatory");
            }
            MaterialStrategy strategy = new MaterialStrategy(data, forms, options);
            LangKey nameLang = langApi.resource(id, "material", langEn, langCn);
            Material material = new Material(id, strategy, langEn, langCn, nameLang);
            Material registered = REGISTRY.register(id, material, strategy);
            PENDING_VALIDATION.add(registered);
            return registered;
        }

        private static String requireText(String value, String what) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("material lang " + what + " must not be blank");
            }
            return value;
        }
    }
}
