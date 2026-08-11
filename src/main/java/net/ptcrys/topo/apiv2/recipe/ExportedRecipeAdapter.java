package net.ptcrys.topo.apiv2.recipe;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/**
 * Projects an OI recipe (+ export hints) into a foreign vanilla recipe emission schedule.
 *
 * <p>
 * Symmetric to {@link ImportedRecipeAdapter}. Implementations must not mint a second
 * {@link OIRecipe}; they only schedule foreign JSON (+ advancements) via RegistryLib provider
 * callbacks. Returning {@code null} means "cannot export" — for scheduled exports this is a
 * hard datagen failure.
 *
 * @param <R> OI recipe type
 */
@FunctionalInterface
public interface ExportedRecipeAdapter<R extends OIRecipe> {

    /**
     * @param recipeId OI-side recipe name path (without type prefix)
     * @param recipe   already-minted OI recipe
     * @param hints    layout / cooking meta required for foreign serializers
     * @return foreign path that was scheduled, or {@code null} if not exportable
     */
    @Nullable
    Identifier scheduleExport(String recipeId, R recipe, ExportHints hints);
}
