package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import org.jspecify.annotations.NonNull;

public final class OIRecipeCategory extends ModularUIRecipeCategory<OIRecipe> {

    private final OIRecipeType<?> recipeType;
    private final IRecipeType<OIRecipe> jeiRecipeType;
    private final IDrawable icon;
    private final Component title;

    public OIRecipeCategory(
                            OIRecipeType<?> recipeType,
                            IRecipeType<OIRecipe> jeiRecipeType,
                            ItemStack iconStack,
                            Component title,
                            IGuiHelper guiHelper) {
        super(recipe -> OIRecipeJeiUiFactory.buildPreview(recipeType, recipe));
        this.recipeType = recipeType;
        this.jeiRecipeType = jeiRecipeType;
        this.icon = guiHelper.createDrawableItemStack(iconStack);
        this.title = title;
    }

    @Override
    public @NonNull IRecipeType<OIRecipe> getRecipeType() {
        return jeiRecipeType;
    }

    @Override
    public @NonNull Component getTitle() {
        return title;
    }

    @Override
    public @NonNull IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return OIRecipeJeiUiFactory.jeiPanelWidth(recipeType);
    }

    @Override
    public int getHeight() {
        return OIRecipeJeiUiFactory.jeiPanelHeight(recipeType);
    }
}
