package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Semantic factory for resource-port mounts.
 *
 * <p>
 * Three orthogonal declaration axes:
 * <ul>
 * <li><b>geometry</b> — slots / tanks / capacity</li>
 * <li><b>access</b> — {@link PortAccess} (recipe role, automation, player, sides)</li>
 * <li><b>filter</b> — optional {@link Predicate} over stored resources ({@link ResourceFilterHelper})</li>
 * </ul>
 * Domain roles (die slot, catalyst, …) are compositions of these axes at the call site, not named
 * entry points on this facade.
 */
public final class Ports {

    private Ports() {}

    public static PartRoleMount<ItemResourcePort> item(
                                                       ComponentKey<ItemResourcePort> key, int slots, PortAccess access) {
        return item(key, slots, access, null);
    }

    public static PartRoleMount<ItemResourcePort> item(
                                                       ComponentKey<ItemResourcePort> key,
                                                       int slots,
                                                       PortAccess access,
                                                       @Nullable Predicate<Resource> filter) {
        return ItemResourcePort.mount(key, slots, access, filter);
    }

    public static PartRoleMount<ItemResourcePort> itemIn(ComponentKey<ItemResourcePort> key, int slots) {
        return item(key, slots, PortAccess.input());
    }

    public static PartRoleMount<ItemResourcePort> itemOut(ComponentKey<ItemResourcePort> key, int slots) {
        return item(key, slots, PortAccess.output());
    }

    public static PartRoleMount<FluidResourcePort> fluid(
                                                         ComponentKey<FluidResourcePort> key, int tanks, int capacityMb, PortAccess access) {
        return fluid(key, tanks, capacityMb, access, null);
    }

    public static PartRoleMount<FluidResourcePort> fluid(
                                                         ComponentKey<FluidResourcePort> key,
                                                         int tanks,
                                                         int capacityMb,
                                                         PortAccess access,
                                                         @Nullable Predicate<Resource> filter) {
        return FluidResourcePort.mount(key, tanks, capacityMb, access, filter);
    }

    public static PartRoleMount<ScalarResourcePort> scalar(
                                                           ComponentKey<ScalarResourcePort> key,
                                                           BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                           int capacity,
                                                           PortAccess access) {
        return scalar(key, integration, capacity, access, null);
    }

    public static PartRoleMount<ScalarResourcePort> scalar(
                                                           ComponentKey<ScalarResourcePort> key,
                                                           BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                           int capacity,
                                                           PortAccess access,
                                                           @Nullable Predicate<Resource> filter) {
        return ScalarResourcePort.mount(key, integration, capacity, access, filter);
    }
}
