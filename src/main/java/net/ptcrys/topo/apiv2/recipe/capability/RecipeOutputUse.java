package net.ptcrys.topo.apiv2.recipe.capability;

/**
 * One declared recipe output content, minted by its owning {@link RecipeCapability}.
 *
 * <p>
 * KHSD use token; see {@link RecipeInputUse}.
 */
public final class RecipeOutputUse<O> {

    private final RecipeCapability<?, O> capability;
    private final O content;

    RecipeOutputUse(RecipeCapability<?, O> capability, O content) {
        this.capability = capability;
        this.content = content;
    }

    public RecipeCapability<?, O> capability() {
        return capability;
    }

    public O content() {
        return content;
    }
}
