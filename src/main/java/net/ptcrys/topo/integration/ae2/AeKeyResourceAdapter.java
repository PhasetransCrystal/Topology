package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Lossless adapter between AE2 keys and the NeoForge transfer resources used by Topo machines.
 */
public final class AeKeyResourceAdapter {

    private static final List<Binding<?, ?>> BINDINGS = new CopyOnWriteArrayList<>();

    private AeKeyResourceAdapter() {}

    /** 注册扩展资源的无损键映射，不绑定网络、访问身份或机器资源类型。 */
    public static synchronized <R extends Resource, K extends AEKey> void register(
                                                                                   Class<R> resourceClass, Class<K> keyClass, Function<R, K> toKey, Function<K, R> toResource) {
        Objects.requireNonNull(resourceClass, "resource class");
        Objects.requireNonNull(keyClass, "AE key class");
        Objects.requireNonNull(toKey, "resource to AE key");
        Objects.requireNonNull(toResource, "AE key to resource");
        if (resourceClass == ItemResource.class || resourceClass == FluidResource.class || resourceClass == AeResource.class || resourceClass == Resource.class) {
            throw new IllegalArgumentException("Cannot replace built-in AE resource mapping");
        }
        for (var binding : BINDINGS) {
            if (binding.resourceClass == resourceClass) throw new IllegalArgumentException("Duplicate AE resource mapping");
        }
        BINDINGS.add(new Binding<>(resourceClass, keyClass, toKey, toResource));
    }

    public static @Nullable AEKey toKey(@Nullable Resource resource) {
        if (resource == null || resource.isEmpty()) {
            return null;
        }
        if (resource instanceof AeResource aeResource) return aeResource.key();
        if (resource instanceof ItemResource itemResource) {
            return AEItemKey.of(itemResource);
        }
        if (resource instanceof FluidResource fluidResource) {
            return AEFluidKey.of(fluidResource);
        }
        for (var binding : BINDINGS) {
            if (binding.resourceClass.isInstance(resource)) return binding.key(resource);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <R extends Resource> @Nullable R toResource(@Nullable AEKey key, Class<R> resourceType) {
        if (key == null || resourceType == null) {
            return null;
        }
        if (resourceType == AeResource.class) return resourceType.cast(new AeResource(key));
        if (resourceType == ItemResource.class && key instanceof AEItemKey itemKey) {
            return (R) itemKey.toResource();
        }
        if (resourceType == FluidResource.class && key instanceof AEFluidKey fluidKey) {
            return (R) fluidKey.toResource();
        }
        for (var binding : BINDINGS) {
            if (resourceType == binding.resourceClass && binding.keyClass.isInstance(key)) return resourceType.cast(binding.resource(key));
        }
        if (resourceType == Resource.class) {
            if (key instanceof AEItemKey itemKey) return resourceType.cast(itemKey.toResource());
            if (key instanceof AEFluidKey fluidKey) return resourceType.cast(fluidKey.toResource());
            return resourceType.cast(new AeResource(key));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <R extends Resource> R emptyResource(Class<R> resourceType) {
        if (resourceType == AeResource.class || resourceType == Resource.class) return resourceType.cast(AeResource.EMPTY);
        if (resourceType == ItemResource.class) {
            return (R) ItemResource.EMPTY;
        }
        if (resourceType == FluidResource.class) {
            return (R) FluidResource.EMPTY;
        }
        throw new IllegalArgumentException("Unsupported AE2 resource type: " + resourceType);
    }

    private record Binding<R extends Resource, K extends AEKey>(
                                                                Class<R> resourceClass, Class<K> keyClass, Function<R, K> toKey, Function<K, R> toResource) {

        K key(Resource resource) {
            return toKey.apply(resourceClass.cast(resource));
        }

        R resource(AEKey key) {
            return toResource.apply(keyClass.cast(key));
        }
    }
}
