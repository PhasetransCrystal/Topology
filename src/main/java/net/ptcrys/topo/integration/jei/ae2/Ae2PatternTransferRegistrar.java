package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.integration.jei.OIJeiCategoryRegistry;
import net.ptcrys.topo.integration.jei.OIMultiblockJeiCategory;
import net.ptcrys.topo.integration.jei.OIMultiblockJeiUiFactory;

import mezz.jei.api.registration.IRecipeTransferRegistration;

/**
 * Registration entry for the JEI shift-encode-to-AE2 feature: one
 * {@link Ae2OIPatternTransferHandler} per recipe category (so JEI's per-{@code (containerClass,
 * recipeType)} lookup short-circuits AE2's own universal handler before it can misread the recipe
 * slot view), plus the {@link MultiblockPatternTransferHandler} that opens the pattern builder
 * popup from the multiblock structure category.
 *
 * <p>
 * The category specs are read from {@link OIJeiCategoryRegistry#recipeCategories()} — the same
 * cached list the category registration consumes — so the handlers see the exact
 * {@code IRecipeType} instances JEI registered.
 *
 * <p>
 * AE2 is declared required in {@code neoforge.mods.toml}; if AE2 were absent the mod would
 * refuse to load entirely, so there is no optional-mod guard here.
 */
public final class Ae2PatternTransferRegistrar {

    private Ae2PatternTransferRegistrar() {}

    public static void register(IRecipeTransferRegistration registration) {
        register(registration, DefaultAe2PatternEncoder.INSTANCE);
    }

    /**
     * Package-private overload that takes an explicit encoder, so tests can inject a spy without
     * triggering AE2's real client-side packet path.
     */
    static void register(IRecipeTransferRegistration registration, Ae2PatternEncoder encoder) {
        var helper = registration.getTransferHelper();
        for (OIJeiCategoryRegistry.RecipeCategorySpec spec : OIJeiCategoryRegistry.recipeCategories()) {
            registration.addRecipeTransferHandler(
                    new Ae2OIPatternTransferHandler(helper, spec.jeiRecipeType(), encoder),
                    spec.jeiRecipeType());
        }

        // Multiblock structure page → pattern builder popup. The category itself is only
        // registered when controllers exist; mirror that condition here.
        if (!OIMultiblockJeiUiFactory.controllerDefinitions().isEmpty()) {
            registration.addRecipeTransferHandler(
                    new MultiblockPatternTransferHandler(helper, OIMultiblockJeiCategory.TYPE),
                    OIMultiblockJeiCategory.TYPE);
        }
    }
}
