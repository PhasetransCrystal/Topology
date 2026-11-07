package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.registration.ExternalBlockTarget;
import net.ptcrys.topo.api.api.registration.ExternalFluidTarget;
import net.ptcrys.topo.api.api.registration.ExternalItemTarget;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.process.ProcessorDataType;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class MaterialFormOptions {

    private final Identifier materialId;
    private final Predicate<MaterialDataType<?>> materialData;
    private MaterialFormTargets targets = MaterialFormTargets.empty();
    private final Map<MaterialDataType<?>, MaterialDataUse<?>> data = new LinkedHashMap<>();

    MaterialFormOptions(Identifier materialId, Predicate<MaterialDataType<?>> materialData) {
        this.materialId = materialId;
        this.materialData = Objects.requireNonNull(materialData, "material data");
    }

    public MaterialFormOptions overrideItem(ExternalItemTarget target) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        if (targets.item().isPresent()) {
            throw new IllegalStateException("duplicate external item override for " + materialId);
        }
        this.targets = targets.withItem(Objects.requireNonNull(target, "target"));
        return this;
    }

    public MaterialFormOptions overrideBlock(ExternalBlockTarget target) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        if (targets.block().isPresent()) {
            throw new IllegalStateException("duplicate external block override for " + materialId);
        }
        this.targets = targets.withBlock(Objects.requireNonNull(target, "target"));
        return this;
    }

    public MaterialFormOptions overrideFluid(ExternalFluidTarget target) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        if (targets.fluid().isPresent()) {
            throw new IllegalStateException("duplicate external fluid override for " + materialId);
        }
        this.targets = targets.withFluid(Objects.requireNonNull(target, "target"));
        return this;
    }

    public MaterialFormOptions data(MaterialDataUse<?> use) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        Objects.requireNonNull(use, "use");
        if (use.type() instanceof ProcessorDataType) {
            throw new IllegalArgumentException(
                    "processor activation " + use.type().id() + " is material-level data; declare it on the material, not on a form");
        }
        if (data.containsKey(use.type())) {
            throw new IllegalStateException(
                    "duplicate material data type " + use.type().id() + " in form options");
        }
        use.type().checkDependencies(materialId, type -> data.containsKey(type) || materialData.test(type));
        data.put(use.type(), use);
        return this;
    }

    public MaterialFormTargets targets() {
        return targets;
    }

    @SuppressWarnings("unchecked")
    <D> Optional<D> data(MaterialDataType<D> type) {
        MaterialDataUse<?> use = data.get(type);
        return use == null ? Optional.empty() : Optional.of((D) use.data());
    }

    Collection<MaterialDataUse<?>> dataUses() {
        return data.values();
    }

    boolean hasData(MaterialDataType<?> type) {
        return data.containsKey(type);
    }
}
