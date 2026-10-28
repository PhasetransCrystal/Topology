package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.registration.ExternalBlockTarget;
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

public final class MaterialFormOptions {

    private final Identifier materialId;
    private ExternalItemTarget itemOverride;
    private ExternalBlockTarget blockOverride;
    private final Map<MaterialDataType<?>, MaterialDataUse<?>> data = new LinkedHashMap<>();

    MaterialFormOptions(Identifier materialId) {
        this.materialId = materialId;
    }

    public MaterialFormOptions overrideItem(ExternalItemTarget target) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        this.itemOverride = Objects.requireNonNull(target, "target");
        return this;
    }

    public MaterialFormOptions overrideBlock(ExternalBlockTarget target) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        this.blockOverride = Objects.requireNonNull(target, "target");
        return this;
    }

    public MaterialFormOptions data(MaterialDataUse<?> use) {
        MaterialRegistry.requireDeclarationsOpen(materialId);
        Objects.requireNonNull(use, "use");
        if (use.type() instanceof ProcessorDataType) {
            throw new IllegalArgumentException(
                    "processor activation " + use.type().id() + " is material-level data; declare it on the material, not on a form");
        }
        if (data.putIfAbsent(use.type(), use) != null) {
            throw new IllegalStateException(
                    "duplicate material data type " + use.type().id() + " in form options");
        }
        return this;
    }

    public Optional<ExternalItemTarget> itemOverride() {
        return Optional.ofNullable(itemOverride);
    }

    public Optional<ExternalBlockTarget> blockOverride() {
        return Optional.ofNullable(blockOverride);
    }

    @SuppressWarnings("unchecked")
    <D> Optional<D> data(MaterialDataType<D> type) {
        MaterialDataUse<?> use = data.get(type);
        return use == null ? Optional.empty() : Optional.of((D) use.data());
    }

    Collection<MaterialDataUse<?>> dataUses() {
        return data.values();
    }
}
