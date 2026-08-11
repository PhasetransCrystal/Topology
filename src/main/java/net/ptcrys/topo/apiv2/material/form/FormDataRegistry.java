package net.ptcrys.topo.apiv2.material.form;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

/**
 * Form payload-type table (query + freeze). Product writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.MaterialDomainRegistration#formDataType}.
 */
public final class FormDataRegistry {

    private static final FreezableStrategyRegistry<Identifier, FormDataType<?>, FormDataType<?>> REGISTRY = FreezableStrategyRegistry.create("form data types");

    private FormDataRegistry() {}

    /** Single write entry for the material domain plugin API. */
    public static <D, H extends FormDataType<D>> H begin(Identifier id, H handle) {
        if (!id.equals(handle.id())) {
            throw new IllegalArgumentException(
                    "form data type id mismatch: registry key " + id + " != handle id " + handle.id());
        }
        REGISTRY.register(id, handle, handle);
        return handle;
    }

    public static FormDataType<?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    static <D> FormDataUse<D> use(FormDataType<D> type, D data) {
        REGISTRY.verifyOwnedHandle(type.id(), type);
        return new FormDataUse<>(type, data);
    }
}
