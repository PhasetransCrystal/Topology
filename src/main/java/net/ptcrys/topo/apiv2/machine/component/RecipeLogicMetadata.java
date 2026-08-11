package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.apiv2.recipe.OIRecipeType;

import java.util.List;
import java.util.Objects;

/** Declaration-time metadata for a recipe logic trait mount. */
public record RecipeLogicMetadata(List<OIRecipeType<?>> recipeTypes) implements Attachment {

    public static final AttachmentType<RecipeLogicMetadata> TYPE = net.ptcrys.topo.apiv2.OfficialOIAPIPlugin.INSTANCE
            .machine()
            .attachmentType("recipe_logic", RecipeLogicMetadata.class);

    public RecipeLogicMetadata {
        Objects.requireNonNull(recipeTypes, "recipe types");
        if (recipeTypes.isEmpty()) {
            throw new IllegalArgumentException("Recipe logic metadata requires at least one recipe type");
        }
        recipeTypes = List.copyOf(recipeTypes);
    }

    @Override
    public AttachmentType<RecipeLogicMetadata> type() {
        return TYPE;
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}
}
