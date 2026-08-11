package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.ore.display.OreVeinDisplays;
import net.ptcrys.topo.data.ore.BuiltinTopoOreLang;
import net.ptcrys.topo.data.ore.common.display.OreVeinPanels;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import org.jspecify.annotations.NonNull;

/**
 * JEI category listing every registered ore vein. One recipe per {@link OreVein}; preview is built by
 * the mode-paired {@link OreVeinDisplays} plug — the JEI factory never branches on mode itself.
 */
public final class TopoOreVeinJeiCategory extends ModularUIRecipeCategory<OreVein> {

    public static final IRecipeType<OreVein> TYPE = IRecipeType.create(IdHelper.oi("ore_vein"), OreVein.class);

    private final IDrawable icon;
    private final Component title;

    public TopoOreVeinJeiCategory(IGuiHelper guiHelper) {
        super(TopoOreVeinJeiCategory::buildPreview);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Items.IRON_ORE));
        this.title = BuiltinTopoOreLang.CATEGORY.getComponent();
    }

    private static ModularUI buildPreview(OreVein vein) {
        return ModularUI.of(UI.of(
                OreVeinDisplays.require(vein.mode().id()).buildPreview(vein),
                StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)));
    }

    @Override
    public @NonNull IRecipeType<OreVein> getRecipeType() {
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
        return OreVeinPanels.PANEL_WIDTH;
    }

    @Override
    public int getHeight() {
        return OreVeinPanels.PANEL_HEIGHT;
    }
}
