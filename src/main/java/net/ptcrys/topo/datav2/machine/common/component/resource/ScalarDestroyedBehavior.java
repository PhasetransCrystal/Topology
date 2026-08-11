package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * World-side strategy fired when a machine holding this scalar kind is destroyed in-world.
 * Bound once at the {@link ScalarResource} identity definition point ({@code register}); the
 * port trait dispatches with its live stored amount and declared capacity. Implementations run
 * inside the vanilla {@code preRemoveSideEffects} window — server thread, block already swapped,
 * machine drops not yet spawned.
 *
 * <p>
 * The strategy performs its world effect (discharge / heat flash) <em>and</em> returns a
 * {@link MachineDestroyedReport} describing how severe it was and which chat line explains it; the
 * dispatch layer turns that report into nearby-player feedback (chat + harmful-tier shockwave).
 */
@FunctionalInterface
public interface ScalarDestroyedBehavior {

    /** No world effect, nothing to report; used by the {@code EMPTY} sentinel and hazard-free kinds. */
    ScalarDestroyedBehavior NONE = (level, pos, stored, capacity) -> MachineDestroyedReport.SILENT;

    MachineDestroyedReport onDestroyed(ServerLevel level, BlockPos pos, long stored, long capacity);
}
