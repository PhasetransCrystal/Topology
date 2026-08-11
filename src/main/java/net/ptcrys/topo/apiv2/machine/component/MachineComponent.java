package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.data.DataScope;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContribution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base runtime behaviour unit mounted on a {@link MachineBlockEntity}.
 *
 * <p>
 * The lifecycle is explicit:
 * <ol>
 * <li><b>instantiate</b>: a {@link ComponentMount} creates the instance with a {@link ComponentContext};
 * {@link #key()} and {@link #machine()} are complete inside the constructor.</li>
 * <li><b>resolve dependencies</b>: {@link #resolveDependencies(MachineComponents)} runs once after
 * every trait is mounted. This phase is for structural dependencies only: required sibling
 * dependencies are verified here via {@link MachineComponents#require(ComponentKey)} or
 * {@link MachineComponents#optional(ComponentKey)}. It
 * must not read restored values that depend on other traits; plain data fields may be
 * declared during construction and are populated by the owning machine data domain.</li>
 * <li><b>onMachineLoad</b>: {@link #onMachineLoad()} runs once the owner enters the world.</li>
 * </ol>
 *
 * <p>
 * Ticking is modeled by the tick system rather than this base class, so
 * machines can opt into scheduling lanes, intervals, alerting, and async behavior explicitly.
 */
public abstract class MachineComponent {

    private final ComponentKey<? extends MachineComponent> key;
    private final MachineBlockEntity machine;
    private final DataScope data;

    protected MachineComponent(ComponentContext<? extends MachineComponent> context) {
        Objects.requireNonNull(context, "trait context");
        this.key = context.key();
        this.machine = context.machine();
        this.data = context.data();
    }

    /** The stable per-machine trait instance key; this drives lookup and persistence. */
    public final ComponentKey<? extends MachineComponent> key() {
        return key;
    }

    /** The stable trait id; this is persisted and must not be renamed across save-compatible versions. */
    public final Identifier id() {
        return key.id();
    }

    /** The owning machine block entity. Dependency lookup still belongs in {@link #resolveDependencies}. */
    protected final MachineBlockEntity machine() {
        return machine;
    }

    protected final DataScope data() {
        return data;
    }

    /**
     * Resolve structural dependencies on sibling traits exactly once. Occasional runtime reads may
     * look up siblings at the use site; hot paths should cache dependencies explicitly. This method
     * must not read persisted state.
     */
    public void resolveDependencies(MachineComponents traits) {}

    /**
     * Contribute machine UI structure via {@link MachineUiContribution}
     * ({@code mainPage} / {@code leftPanel} / {@code rightPanel} / {@code bottomStrip}).
     * Default is empty.
     */
    public void collectMachineUi(MachineUiContribution contribution) {}

    /** Expose an automation-facing block capability resource handler, if present. */
    protected <R extends Resource> @Nullable ResourceHandler<R> transferHandler(
                                                                                MachineResourceType<R> resourceType,
                                                                                @Nullable Direction side) {
        return null;
    }

    /** Expose a same-machine recipe resource handler for the requested role, if present. */
    protected <R extends Resource> @Nullable ResourceHandler<R> recipeResourceHandler(
                                                                                      MachineResourceType<R> resourceType,
                                                                                      RecipeRole recipeIo) {
        return null;
    }

    /**
     * Contributes zero or more recipe handlers for aggregation. Default forwards
     * {@link #recipeResourceHandler(MachineResourceType, RecipeRole)} with this component's pool
     * metadata. Pattern providers with per-slot buffers override to emit one contribution per slot.
     */
    protected <R extends Resource> void collectRecipeResourceHandlers(
                                                                      MachineResourceType<R> resourceType,
                                                                      RecipeRole recipeIo,
                                                                      Consumer<RecipeResourceContribution<R>> out) {
        ResourceHandler<R> handler = recipeResourceHandler(resourceType, recipeIo);
        if (handler != null) {
            out.accept(new RecipeResourceContribution<>(
                    recipeSearchPoolId(), recipePoolScoped(), handler));
        }
    }

    /**
     * Resolved recipe search-pool identity for this component. Always present — configuration sites
     * resolve raw settings into a concrete id; consumers never see “missing”.
     */
    public RecipeSearchPoolId recipeSearchPoolId() {
        return RecipeSearchPoolId.DEFAULT;
    }

    /**
     * When {@code true}, this component's recipe handlers participate only in
     * {@link #recipeSearchPoolId()}'s pool for input <em>and</em> output aggregation. When
     * {@code false} (energy and other global resources), handlers are included in every pool.
     */
    public boolean recipePoolScoped() {
        return false;
    }

    /**
     * One recipe-side handler contribution with a concrete pool id (always present).
     *
     * @param poolScoped when false, included in every pool's input/output view (e.g. energy)
     */
    public record RecipeResourceContribution<R extends Resource>(
                                                                 RecipeSearchPoolId poolId,
                                                                 boolean poolScoped,
                                                                 ResourceHandler<R> handler) {

        public RecipeResourceContribution {
            Objects.requireNonNull(poolId, "pool id");
            Objects.requireNonNull(handler, "handler");
        }
    }

    /** Called once when the owning machine enters the world after resolve and data-domain load. */
    public void onMachineLoad() {}

    /**
     * Called when the machine block is destroyed or replaced in-world (player break, explosion,
     * {@code setBlock} swap) — never on chunk unload or same-block state changes, and never on the
     * client. Fired from the vanilla {@code preRemoveSideEffects} window: traits and machine data
     * are still fully alive, and the machine's own block drops have not spawned yet. Runs before
     * {@link #onMachineUnload()}. Default: no behavior.
     */
    public void onMachineDestroyed(ServerLevel level, BlockPos pos, BlockState state) {}

    /** Called when the owning machine is removed from the world. */
    public void onMachineUnload() {}
}
