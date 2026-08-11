package net.ptcrys.topo.apiv2.machine.resource;

import net.ptcrys.topo.apiv2.machine.component.Attachment;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Common declaration-time metadata view for one machine resource port: type-specific storage
 * geometry (declared by the implementing record), the access {@link #policy()}, and an optional
 * content {@link #resourceFilter()}. Policy-derived views are default methods — implementations
 * only declare what actually varies.
 */
public interface ResourcePortMetadata extends Attachment {

    MachineResourceType<? extends Resource> resourceType();

    /**
     * Whether this resource family participates in recipe-pool isolation. Slotted resources use
     * the machine's configured pool by default; globally shared scalar resources override this to
     * {@code false} so energy, heat, and similar lanes remain visible from every concrete pool.
     */
    default boolean recipePoolIsolatable() {
        return true;
    }

    /** The full declaration-time access policy (record implementations expose their component for free). */
    PortAccess policy();

    /** Content constraint on what the port stores; {@code null} accepts every resource. */
    default @Nullable Predicate<Resource> resourceFilter() {
        return null;
    }

    /** Whether {@code resource} passes {@link #resourceFilter()} (vacuously true without one). */
    default boolean accepts(Resource resource) {
        Predicate<Resource> filter = resourceFilter();
        return filter == null || filter.test(resource);
    }

    default RecipeRole recipeIo() {
        return policy().recipeIo();
    }

    default AutomationIo automationIo() {
        return policy().automationIo();
    }

    /**
     * Whether automation may attach on the world-absolute {@code side} given the owning block's
     * current {@code facing} ({@code null} for blocks without a {@code FACING} property, which
     * degrades to world-absolute interpretation). See {@link PortAccess} for the
     * block-facing-relative side frame.
     */
    default boolean allowsTransferSide(@Nullable Direction side, @Nullable Direction facing) {
        return policy().allowsTransferSide(side, facing);
    }

    default PlayerAccess playerSlotAccess() {
        return policy().playerSlotAccess();
    }
}
