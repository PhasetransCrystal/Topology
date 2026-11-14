package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import org.jspecify.annotations.NonNull;

public final class TopoRecipeCategory extends ModularUIRecipeCategory<TopoRecipe> {

    private final TopoRecipeType<?> recipeType;
    private final IRecipeType<TopoRecipe> jeiRecipeType;
    private final IDrawable icon;
    private final Component title;

    public TopoRecipeCategory(
                              TopoRecipeType<?> recipeType,
                              IRecipeType<TopoRecipe> jeiRecipeType,
                              ItemStack iconStack,
                              Component title,
                              IGuiHelper guiHelper) {
        super(recipe -> TopoRecipeJeiUiFactory.buildPreview(recipeType, recipe));
        this.recipeType = recipeType;
        this.jeiRecipeType = jeiRecipeType;
        this.icon = guiHelper.createDrawableItemStack(iconStack);
        this.title = title;
    }

    @Override
    public @NonNull IRecipeType<TopoRecipe> getRecipeType() {
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
        return TopoRecipeJeiUiFactory.jeiPanelWidth(recipeType);
    }

    @Override
    public int getHeight() {
        return TopoRecipeJeiUiFactory.jeiPanelHeight(recipeType);
    }
}
