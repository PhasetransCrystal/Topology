package net.ptcrys.topo.api.recipe;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/**
 * Projects an Topo recipe (+ export hints) into a foreign vanilla recipe emission schedule.
 *
 * <p>
 * Symmetric to {@link ImportedRecipeAdapter}. Implementations must not mint a second
 * {@link TopoRecipe}; they only schedule foreign JSON (+ advancements) via RegistryLib provider
 * callbacks. Returning {@code null} means "cannot export" — for scheduled exports this is a
 * hard datagen failure.
 *
 * @param <R> Topo recipe type
 */
@FunctionalInterface
public interface ExportedRecipeAdapter<R extends TopoRecipe> {

    /**
     * @param recipeId Topo-side recipe name path (without type prefix)
     * @param recipe   already-minted Topo recipe
     * @param hints    layout / cooking meta required for foreign serializers
     * @return foreign path that was scheduled, or {@code null} if not exportable
     */
    @Nullable
    Identifier scheduleExport(String recipeId, R recipe, ExportHints hints);
}
