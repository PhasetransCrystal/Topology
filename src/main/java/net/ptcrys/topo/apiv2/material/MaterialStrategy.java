package net.ptcrys.topo.apiv2.material;

import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.data.MaterialDataUse;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MaterialStrategy {

    private final Map<MaterialDataType<?>, MaterialDataUse<?>> data;
    private final List<MaterialForm> forms;
    private final Map<MaterialForm, MaterialFormOptions> options;

    MaterialStrategy(Map<MaterialDataType<?>, MaterialDataUse<?>> data, List<MaterialForm> forms,
                     Map<MaterialForm, MaterialFormOptions> options) {
        this.data = Map.copyOf(data);
        this.forms = List.copyOf(forms);
        this.options = Map.copyOf(options);
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

    public Optional<MaterialFormOptions> optionsFor(MaterialForm form) {
        return Optional.ofNullable(options.get(form));
    }

    Collection<MaterialDataUse<?>> dataUses() {
        return data.values();
    }

    Collection<MaterialFormOptions> formOptions() {
        return options.values();
    }
}
