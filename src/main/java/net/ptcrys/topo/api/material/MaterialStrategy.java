package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.material.process.ProcessorDataType;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Internal per-material state: capability data, form options, and resolved content tables. */
final class MaterialStrategy {

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
    <D> Optional<D> data(MaterialDataType<D> type) {
        MaterialDataUse<?> use = data.get(type);
        return use == null ? Optional.empty() : Optional.of((D) use.data());
    }

    <D> Optional<D> data(MaterialDataType<D> type, MaterialForm form) {
        MaterialFormOptions formOptions = options.get(form);
        if (formOptions != null) {
            Optional<D> scoped = formOptions.data(type);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return data(type);
    }

    boolean hasData(MaterialDataType<?> type) {
        return data.containsKey(type);
    }

    List<MaterialDataUse<?>> dataUses() {
        return List.copyOf(data.values());
    }

    List<MaterialForm> forms() {
        return forms;
    }

    boolean hasForm(MaterialForm form) {
        return formSet.contains(form);
    }

    List<MaterialPostProcessor> postProcessors() {
        return postProcessors;
    }

    Optional<MaterialFormOptions> optionsFor(MaterialForm form) {
        return Optional.ofNullable(options.get(form));
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
        for (MaterialDataType<?> dependency : use.type().dependencies()) {
            if (!data.containsKey(dependency)) {
                throw new IllegalStateException(
                        "material " + id + " declares " + use.type().id() + " but is missing required property " + dependency.id());
            }
        }
        if (data.putIfAbsent(use.type(), use) != null) {
            throw new IllegalStateException("duplicate material data type " + use.type().id() + " for " + id);
        }
    }

    void resolveForms(List<MaterialForm> resolved) {
        forms = List.copyOf(resolved);
        formSet = Set.copyOf(resolved);
        List<MaterialPostProcessor> processors = new ArrayList<>();
        for (MaterialDataUse<?> use : data.values()) {
            if (use.type() instanceof ProcessorDataType processorType) {
                processors.add(processorType.processor());
            }
        }
        processors.sort(Comparator.comparing(MaterialPostProcessor::id));
        postProcessors = List.copyOf(processors);
    }
}
