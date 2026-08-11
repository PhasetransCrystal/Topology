package net.ptcrys.topo.api.recipe.capability;

/**
 * One declared recipe input content, minted by its owning {@link RecipeCapability}.
 *
 * <p>
 * KHSD use token: the package-private constructor plus the {@code protected}
 * {@link RecipeCapability#inputUse} entry guarantee a caller can never pair a capability with a
 * foreign content value. Recipe builders accept only minted uses.
 */
public final class RecipeInputUse<I> {

    private final RecipeCapability<I, ?> capability;
    private final I content;

    RecipeInputUse(RecipeCapability<I, ?> capability, I content) {
        this.capability = capability;
        this.content = content;
    }

    public RecipeCapability<I, ?> capability() {
        return capability;
    }

    public I content() {
        return content;
    }
}
