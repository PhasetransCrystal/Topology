package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Canonical registry owner for {@link PartRole}.
 *
 * <p>
 * Registration is single-threaded bootstrap work guarded by {@code synchronized}, and
 * {@link #freeze()} publishes the immutable snapshot consulted at runtime.
 * The handle is also the strategy (H == S): the {@link PartRole} owns its own identity and
 * backend expectation, so no separate strategy object is needed.
 */
public final class PartRoleRegistry {

    private static final FreezableStrategyRegistry<Identifier, PartRole, PartRole> REGISTRY = FreezableStrategyRegistry.create("part capabilities");

    private PartRoleRegistry() {}

    /**
     * Single write entry for {@link MachineDomainRegistration#partRole}.
     * Product code must not call this.
     */
    public static synchronized PartRole begin(
                                              Identifier id, Component displayName, MachineResourceType<?> resourceType, RecipeRole recipeIo) {
        PartRole capability = new PartRole(id, displayName, resourceType, recipeIo);
        return REGISTRY.register(id, capability, capability);
    }

    public static PartRole require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static synchronized void freeze() {
        REGISTRY.freeze();
    }
}
