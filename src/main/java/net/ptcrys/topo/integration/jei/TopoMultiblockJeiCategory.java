package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.multiblock.ui.MultiblockPreviewPage;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * JEI category listing every multiblock controller with a rotatable, zoomable 3D preview of its
 * blueprint (see {@link TopoMultiblockJeiUiFactory}). One JEI "recipe" per controller definition;
 * controllers are also registered as their own craft stations, so looking up a controller block
 * shows its structure.
 */
public final class TopoMultiblockJeiCategory extends ModularUIRecipeCategory<MachineDefinition> {

    public static final IRecipeType<MachineDefinition> TYPE = IRecipeType.create(IdHelper.topo("multiblock"), MachineDefinition.class);

    private final IDrawable icon;
    private final Component title;

    public TopoMultiblockJeiCategory(IGuiHelper guiHelper, List<MachineDefinition> controllers) {
        super(MultiblockPreviewPage::build);
        ItemStack iconStack = new ItemStack(controllers.getFirst().registeredBlock().get());
        this.icon = guiHelper.createDrawableItemStack(iconStack);
        this.title = TopoApiLang.UI_MULTIBLOCK_CATEGORY.getComponent();
    }

    @Override
    public @NonNull IRecipeType<MachineDefinition> getRecipeType() {
        return TYPE;
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
        return TopoMultiblockJeiUiFactory.PANEL_WIDTH;
    }

    @Override
    public int getHeight() {
        return TopoMultiblockJeiUiFactory.PANEL_HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineDefinition machine, IFocusGroup focuses) {
        super.setRecipe(builder, machine, focuses);
        var controller = machine.registeredBlock().get();
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).add(controller);
        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION).add(controller);
    }

    @Override
    public Identifier getIdentifier(MachineDefinition machine) {
        return machine.id();
    }
}
