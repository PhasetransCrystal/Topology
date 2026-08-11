package net.ptcrys.topo.api.material.data;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.material.Material;

import net.minecraft.resources.Identifier;

/**
 * Material data-type table (query + freeze). Product writes only via
 * {@link MaterialDomainRegistration#dataType}.
 */
public final class MaterialDataRegistry {

    private static final FreezableStrategyRegistry<Identifier, MaterialDataType<?>, MaterialDataStrategy<?>> REGISTRY = FreezableStrategyRegistry.create("material data types");

    /** No-op validator for data types that only store values. */
    @SuppressWarnings("unchecked")
    public static <D> MaterialDataStrategy<D> noValidation() {
        return (MaterialDataStrategy<D>) NO_VALIDATION;
    }

    private static final MaterialDataStrategy<Object> NO_VALIDATION = (material, data) -> {};

    private MaterialDataRegistry() {}

    /**
     * Single write entry. Used by {@link MaterialDomainRegistration} and
     * post-processor pairing. Pass {@link #noValidation()} when no value checks are needed.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <D, H extends MaterialDataType<D>> H begin(
                                                             Identifier id, H handle, MaterialDataStrategy<D> strategy) {
        if (!id.equals(handle.id())) {
            throw new IllegalArgumentException(
                    "material data type id mismatch: registry key " + id + " != handle id " + handle.id());
        }
        REGISTRY.register(id, handle, (MaterialDataStrategy) strategy);
        return handle;
    }

    public static MaterialDataType<?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static MaterialDataStrategy<?> strategy(Identifier id) {
        return REGISTRY.strategy(id);
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void validate(Material material, MaterialDataUse<?> use) {
        MaterialDataStrategy strategy = REGISTRY.strategy(use.type().id());
        if (strategy == null) {
            throw new IllegalStateException("no material data strategy for " + use.type().id());
        }
        strategy.validate(material, use.data());
    }

    static <D> MaterialDataUse<D> use(MaterialDataType<D> type, D data) {
        REGISTRY.verifyOwnedHandle(type.id(), type);
        return new MaterialDataUse<>(type, data);
    }
}
