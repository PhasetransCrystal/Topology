package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.multiblock.ui.MultiblockPreviewPage;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
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

/** Topo 统一结构分类；控制器按预览条目建立查询索引，不作为整个分类的通用工作方块。 */
public final class MultiblockJeiCategory extends ModularUIRecipeCategory<MachineDefinition> {

    public static final IRecipeType<MachineDefinition> TYPE = IRecipeType.create(IdHelper.topo("multiblock"), MachineDefinition.class);
    private final IDrawable icon;

    MultiblockJeiCategory(MachineDefinition icon, IGuiHelper gui) {
        super(MultiblockPreviewPage::build);
        this.icon = gui.createDrawableItemStack(new ItemStack(icon.registeredBlock().get()));
    }

    @Override
    public IRecipeType<MachineDefinition> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return TopoApiLang.UI_MULTIBLOCK_CATEGORY.getComponent();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return MachineUiComponentStyle.INSTANCE.getJeiMultiblockPanelWidth();
    }

    @Override
    public int getHeight() {
        return MachineUiComponentStyle.INSTANCE.getJeiMultiblockPanelHeight();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineDefinition machine, IFocusGroup focuses) {
        super.setRecipe(builder, machine, focuses);
        // R 和 U 均定位到本机结构；分类级工作方块会让 U 展开整个分类。
        var controller = machine.registeredBlock().get();
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).add(controller);
        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION).add(controller);
    }

    @Override
    public Identifier getIdentifier(MachineDefinition machine) {
        return machine.id();
    }
}
