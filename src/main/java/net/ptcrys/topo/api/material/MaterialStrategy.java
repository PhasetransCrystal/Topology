package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.material.process.ProcessorDataType;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class MaterialStrategy {

    private final Identifier id;
    private final Map<MaterialDataType<?>, MaterialDataUse<?>> data = new LinkedHashMap<>();
    private final Map<MaterialForm, MaterialFormOptions> options = new LinkedHashMap<>();
    private List<MaterialForm> forms = List.of();
    private Set<MaterialForm> formSet = Set.of();
    private List<MaterialPostProcessor> postProcessors = List.of();

    MaterialStrategy(Identifier id) {
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    public <D> Optional<D> data(MaterialDataType<D> type) {
        MaterialDataUse<?> use = data.get(type);
        return use == null ? Optional.empty() : Optional.of((D) use.data());
    }

    public <D> Optional<D> data(MaterialDataType<D> type, MaterialForm form) {
        MaterialFormOptions formOptions = options.get(form);
        if (formOptions != null) {
            Optional<D> scoped = formOptions.data(type);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return data(type);
    }

    public List<MaterialForm> forms() {
        return forms;
    }

    /** Constant-time membership in the resolved form set. */
    public boolean hasForm(MaterialForm form) {
        return formSet.contains(form);
    }

    /** Activated processors, cached when declarations close and ordered by full ID. */
    public List<MaterialPostProcessor> postProcessors() {
        return postProcessors;
    }

    public Optional<MaterialFormOptions> optionsFor(MaterialForm form) {
        return Optional.ofNullable(options.get(form));
    }

    Collection<MaterialDataUse<?>> dataUses() {
        return data.values();
    }

    void configureForm(MaterialForm form, Consumer<MaterialFormOptions> configure) {
        MaterialRegistry.requireDeclarationsOpen(id);
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(configure, "configure");
        if (MaterialFormRegistry.require(form.id()) != form) {
            throw new IllegalArgumentException("foreign material form " + form.id() + " for " + id);
        }
        configure.accept(options.computeIfAbsent(form, key -> new MaterialFormOptions(id)));
    }

    void addData(MaterialDataUse<?> use) {
        MaterialRegistry.requireDeclarationsOpen(id);
        Objects.requireNonNull(use, "use");
        if (data.putIfAbsent(use.type(), use) != null) {
            throw new IllegalStateException("duplicate material data type " + use.type().id() + " for " + id);
        }
    }

    void resolveForms(List<MaterialForm> resolved) {
        forms = List.copyOf(resolved);
        formSet = Set.copyOf(resolved);
        postProcessors = data.keySet().stream()
                .filter(ProcessorDataType.class::isInstance)
                .map(ProcessorDataType.class::cast)
                .map(ProcessorDataType::processor)
                .sorted(Comparator.comparing(MaterialPostProcessor::id))
                .toList();
    }
}
