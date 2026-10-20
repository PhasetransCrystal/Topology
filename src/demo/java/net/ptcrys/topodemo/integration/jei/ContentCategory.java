package net.ptcrys.topodemo.integration.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;

import java.util.function.Function;

/** JEI owns category registration; LDLib2 owns rendering and ingredient slot extraction. */
final class ContentCategory<T> extends ModularUIRecipeCategory<T> {

    private final IRecipeType<T> type;
    private final Component title;
    private final IDrawable icon;
    private final int height;

    ContentCategory(IRecipeType<T> type, Component title, ItemStack icon, int height,
                    Function<T, ModularUI> preview, IGuiHelper gui) {
        super(preview::apply);
        this.type = type;
        this.title = title;
        this.icon = gui.createDrawableItemStack(icon);
        this.height = height;
    }

    @Override
    public IRecipeType<T> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return ContentRecipePanels.WIDTH;
    }

    @Override
    public int getHeight() {
        return height;
    }
}
