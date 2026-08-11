package net.ptcrys.topo.api.machine.component;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.recipe.TopoRecipe;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jspecify.annotations.Nullable;

/**
 * Generic recipe hook for data-defined roles such as tier scaling, parallelism, or overclocking.
 *
 * <p>
 * Contract: when a recipe run starts, every capability-provided modifier is folded over the
 * matched recipe in deterministic trait-mount order, and the resulting modified view is honored
 * across the whole run — start gates, per-tick I/O, output emission, and duration. Persistence
 * stores only the original recipe id (plus any run locks such as parallel factor on
 * {@link RecipeLogic}); pure modifiers re-fold identically on reload. Context-aware folds use
 * {@link #modify(TopoRecipe, MachineBlockEntity)}. Dynamic parallel is declared separately through
 * {@link #parallelCap()} so the logic can apply it once and persist the selected factor.
 *
 * <p>
 * Player-facing metadata:
 * <ul>
 * <li>{@link #title()} — entry heading in the recipe-UI list and left column of the machine item
 * tooltip row</li>
 * <li>{@link #description()} — short MC {@link Component} summary; right column of the machine
 * item tooltip row (with title)</li>
 * <li>{@link #createDetailsUi()} — body under the title in the recipe-UI list (typically an
 * {@code LcdData} or any custom widget tree)</li>
 * </ul>
 * The list container only stacks title + body and does not interpret structured fields — layout and
 * content are owned by each modifier. Declaration-time copies of title/description for item
 * tooltips live on {@link RecipeModifierDisplay} attached to the mount.
 */
public interface RecipeModifier {

    ServiceKey<RecipeModifier, RecipeLogic> KEY = ServiceKey.oi("recipe_modifier", RecipeModifier.class, RecipeLogic.class);

    /** Entry title above this modifier's detail UI / left cell of the machine tooltip row. */
    Component title();

    /**
     * One-line player-facing summary of free parameters (e.g. {@code 耗时×0.5 功率×2}), typically with
     * per-segment colors. Shown with {@link #title()} on the corresponding machine item tooltip
     * (title | description).
     */
    Component description();

    /**
     * Builds the detail panel under the title. Called when the machine UI is assembled; return a
     * fresh element tree each call. Prefer content-sized layouts (the list uses max-width only).
     */
    UIElement createDetailsUi();

    /**
     * Pure fold (no world reads). Prefer this for tier curves and other inventory-independent
     * transforms. Default dynamic path delegates here.
     */
    TopoRecipe modify(TopoRecipe recipe);

    /**
     * Context-aware fold. Dynamic parallel probes machine inputs here. Default: {@link #modify(TopoRecipe)}.
     */
    default TopoRecipe modify(TopoRecipe recipe, @Nullable MachineBlockEntity machine) {
        return modify(recipe);
    }

    /**
     * Declaration-time upper bound for input-limited parallel. The recipe logic takes the maximum
     * contribution, probes inputs only for a new run, and applies the selected factor after every
     * ordinary modifier fold. Implementations that return more than 1 must not scale parallel in
     * either {@code modify} overload.
     */
    default long parallelCap() {
        return 1L;
    }
}
