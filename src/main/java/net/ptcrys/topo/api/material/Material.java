package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.lang.DisplayNameSource;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A registered material. Data is the capability surface: consumers query
 * {@link #data(MaterialDataType)}, forms select materials via
 * {@link MaterialForm#appliesTo(Material)}, and plugins add capabilities with
 * {@link #data(MaterialDataUse)} during the open declaration window.
 */
public final class Material implements DisplayNameSource {

    private final Identifier id;
    private final MaterialStrategy strategy;
    private final String displayNameEn;
    private final String displayNameCn;
    private final LangKey nameLang;

    Material(
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

    // --- capability data ---------------------------------------------------------------------

    /** Read one capability payload, or empty when the material does not declare it. */
    public <D> Optional<D> data(MaterialDataType<D> type) {
        return strategy.data(type);
    }

    public <D> Optional<D> data(MaterialDataType<D> type, MaterialForm form) {
        return strategy.data(type, form);
    }

    public boolean hasData(MaterialDataType<?> type) {
        return strategy.hasData(type);
    }

    /** Declare a property; only valid while the material declaration window is open. */
    public Material data(MaterialDataUse<?> use) {
        Objects.requireNonNull(use, "use");
        use.type().checkDependencies(this);
        strategy.addData(use);
        return this;
    }

    /** Immutable snapshot of declared capabilities, in declaration order. */
    public List<MaterialDataUse<?>> dataUses() {
        return strategy.dataUses();
    }

    // --- resolved forms and post-processors --------------------------------------------------

    public List<MaterialForm> forms() {
        return strategy.forms();
    }

    public boolean hasForm(MaterialForm form) {
        return strategy.hasForm(form);
    }

    public List<MaterialPostProcessor> postProcessors() {
        return strategy.postProcessors();
    }

    public Optional<MaterialFormOptions> optionsFor(MaterialForm form) {
        return strategy.optionsFor(form);
    }

    /** Configure one form's local options; only valid while declarations are open. */
    public Material configureForm(MaterialForm form, Consumer<MaterialFormOptions> configure) {
        strategy.configureForm(form, configure);
        return this;
    }

    // --- display -----------------------------------------------------------------------------

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

    /** Framework-only access for freeze/creation; product code must use the methods above. */
    @ApiStatus.Internal
    MaterialStrategy strategy() {
        return strategy;
    }
}
