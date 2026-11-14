package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

/**
 * Resolves Topo resources to AE keys. Production traits use AE2 item/fluid keys, while
 * deterministic unit tests can inject small fake keys without booting Minecraft registries.
 */
@FunctionalInterface
public interface AeResourceKeyResolver<R extends Resource> {

    @Nullable
    AEKey toKey(@Nullable R resource);

    static <R extends Resource> AeResourceKeyResolver<R> defaultResolver() {
        return AeKeyResourceAdapter::toKey;
    }
}
