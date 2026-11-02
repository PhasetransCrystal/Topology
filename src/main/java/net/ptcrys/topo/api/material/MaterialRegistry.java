package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Material storage, construction entry, and phase fences. Policy lives in dedicated collaborators. */
public final class MaterialRegistry {

    private static final FreezableStrategyRegistry<Identifier, Material, MaterialStrategy> REGISTRY = FreezableStrategyRegistry.create("materials");
    private static boolean materialCreationClosed;
    private static boolean declarationsClosed;
    private static List<Material> declarationSnapshot;

    private MaterialRegistry() {}

    /** Internal write entry used only by {@link MaterialDomainRegistration}. */
    @ApiStatus.Internal
    public static Builder begin(Identifier id, LangDomainRegistration lang) {
        requireCreationOpen(id);
        requireDeclarationsOpen(id);
        return new Builder(Objects.requireNonNull(id, "id"), Objects.requireNonNull(lang, "lang"));
    }

    /** Closes material creation after all {@code registerMaterials} hooks have completed. */
    @ApiStatus.Internal
    public static void closeMaterialCreation() {
        materialCreationClosed = true;
    }

    public static Material require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<Material> registered() {
        List<Material> snapshot = declarationSnapshot;
        return snapshot != null ? snapshot : REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    @ApiStatus.Internal
    public static void freeze() {
        if (REGISTRY.isFrozen()) {
            return;
        }
        if (declarationsClosed) {
            throw new IllegalStateException("materials: freeze already started or failed");
        }
        // Close every write path before invoking product predicates or validators.
        materialCreationClosed = true;
        declarationsClosed = true;
        declarationSnapshot = REGISTRY.handlesView();
        MaterialFormResolver.resolve(declarationSnapshot, MaterialFormRegistry.registered());
        MaterialValidator.validate(declarationSnapshot);
        MaterialLookup.rebuild(declarationSnapshot);
        REGISTRY.freeze();
        declarationSnapshot = null;
    }

    static void requireCreationOpen(Identifier id) {
        if (materialCreationClosed) {
            throw new IllegalStateException("material " + id + ": material creation is closed");
        }
    }

    static void requireDeclarationsOpen(Identifier id) {
        if (declarationsClosed) {
            throw new IllegalStateException("material " + id + ": declarations are closed");
        }
    }

    public static final class Builder {

        private final Identifier id;
        private final LangDomainRegistration langApi;
        private final MaterialStrategy strategy;
        private boolean built;
        private String langEn;
        private String langCn;

        private Builder(Identifier id, LangDomainRegistration langApi) {
            this.id = id;
            this.langApi = langApi;
            this.strategy = new MaterialStrategy(id);
        }

        public Builder lang(String en, String cn) {
            requireCreationOpen(id);
            requireDeclarationsOpen(id);
            if (built) {
                throw new IllegalStateException("material " + id + " is already built");
            }
            this.langEn = requireText(en, "en");
            this.langCn = requireText(cn, "cn");
            return this;
        }

        /** Configures scoped data and external targets; does not select the form. */
        public Builder form(MaterialForm form, Consumer<MaterialFormOptions> options) {
            requireCreationOpen(id);
            strategy.configureForm(form, options);
            return this;
        }

        public Builder data(MaterialDataUse<?> data) {
            requireCreationOpen(id);
            strategy.addData(data);
            return this;
        }

        public Material build() {
            requireCreationOpen(id);
            requireDeclarationsOpen(id);
            if (built || REGISTRY.get(id) != null) {
                throw new IllegalStateException("duplicate material " + id);
            }
            if (langEn == null || langCn == null) {
                throw new IllegalStateException(
                        "material " + id + " requires lang(en, cn); both languages are mandatory");
            }
            LangKey nameLang = langApi.resource(id, "material", langEn, langCn);
            Material material = new Material(id, strategy, langEn, langCn, nameLang);
            Material registered = REGISTRY.register(id, material, strategy);
            built = true;
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
