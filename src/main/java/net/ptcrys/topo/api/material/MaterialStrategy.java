package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormPlan;
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
import java.util.function.Function;

/** Internal per-material state: capability data, form options, and resolved content tables. */
final class MaterialStrategy {

    private final Identifier id;
    private final Map<MaterialDataType<?>, MaterialDataUse<?>> data = new LinkedHashMap<>();
    private List<MaterialDataUse<?>> dataUses = List.of();
    private final Map<MaterialForm, MaterialFormOptions> options = new LinkedHashMap<>();
    private List<MaterialForm> forms = List.of();
    private Set<MaterialForm> formSet = Set.of();
    private boolean formsPublished;
    private Map<MaterialForm, MaterialFormPlan> plans = Map.of();
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
        return dataUses;
    }

    List<MaterialForm> forms() {
        return forms;
    }

    boolean hasForm(MaterialForm form) {
        return formSet.contains(form);
    }

    MaterialFormPlan plan(MaterialForm form) {
        MaterialFormPlan plan = plans.get(form);
        if (plan == null) {
            throw new IllegalStateException("material " + id + " has no resolved plan for form " + form.id());
        }
        return plan;
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
        configure.accept(options.computeIfAbsent(form, key -> new MaterialFormOptions(id, data::containsKey)));
    }

    void addData(MaterialDataUse<?> use) {
        MaterialRegistry.requireDeclarationsOpen(id);
        Objects.requireNonNull(use, "use");
        if (data.containsKey(use.type())) {
            throw new IllegalStateException("duplicate material data type " + use.type().id() + " for " + id);
        }
        use.type().checkDependencies(id, data::containsKey);
        data.put(use.type(), use);
        dataUses = List.copyOf(data.values());
    }

    /**
     * Publishes the complete resolved form list. All materials must be published before any plan is
     * created, so plan construction can safely inspect other materials' resolved forms.
     */
    void publishForms(List<MaterialForm> resolved) {
        Objects.requireNonNull(resolved, "resolved forms");
        if (formsPublished) {
            throw new IllegalStateException("material " + id + " already published its resolved forms");
        }
        forms = List.copyOf(resolved);
        formSet = Set.copyOf(resolved);
        formsPublished = true;
    }

    void resolvePlans(Function<MaterialForm, MaterialFormPlan> planFactory) {
        Objects.requireNonNull(planFactory, "planFactory");
        if (!formsPublished) {
            throw new IllegalStateException("material " + id + " must publish resolved forms before building plans");
        }
        Map<MaterialForm, MaterialFormPlan> resolvedPlans = new LinkedHashMap<>();
        for (MaterialForm form : forms) {
            resolvedPlans.put(form, Objects.requireNonNull(planFactory.apply(form), "form plan for " + form.id()));
        }
        plans = Map.copyOf(resolvedPlans);
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
