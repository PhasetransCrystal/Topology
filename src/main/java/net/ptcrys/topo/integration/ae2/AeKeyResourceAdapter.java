package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

/**
 * Lossless adapter between AE2 keys and the NeoForge transfer resources used by Topo machines.
 */
public final class AeKeyResourceAdapter {

    private AeKeyResourceAdapter() {}

    public static @Nullable AEKey toKey(@Nullable Resource resource) {
        if (resource == null || resource.isEmpty()) {
            return null;
        }
        if (resource instanceof ItemResource itemResource) {
            return AEItemKey.of(itemResource);
        }
        if (resource instanceof FluidResource fluidResource) {
            return AEFluidKey.of(fluidResource);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <R extends Resource> @Nullable R toResource(@Nullable AEKey key, Class<R> resourceType) {
        if (key == null || resourceType == null) {
            return null;
        }
        if (resourceType == ItemResource.class && key instanceof AEItemKey itemKey) {
            return (R) itemKey.toResource();
        }
        if (resourceType == FluidResource.class && key instanceof AEFluidKey fluidKey) {
            return (R) fluidKey.toResource();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <R extends Resource> R emptyResource(Class<R> resourceType) {
        if (resourceType == ItemResource.class) {
            return (R) ItemResource.EMPTY;
        }
        if (resourceType == FluidResource.class) {
            return (R) FluidResource.EMPTY;
        }
        throw new IllegalArgumentException("Unsupported AE2 resource type: " + resourceType);
    }
}
