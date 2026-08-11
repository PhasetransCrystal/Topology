package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Per-{@link IRecipeType} JEI handler that encodes an {@link TopoRecipe} into the open AE2
 * {@link PatternEncodingTermMenu} as a processing pattern.
 *
 * <p>
 * JEI's {@code RecipeTransferManager} looks up handlers by {@code (containerClass,
 * specificRecipeType)} first and only falls back to the universal table when that misses — so
 * registering one of these per Topo recipe type shadows AE2's own universal
 * {@code EncodePatternTransferHandler} for our recipe pages. (See JEI 29.x source
 * {@code mezz.jei.library.recipes.RecipeTransferManager#getRecipeTransferHandler}.)
 *
 * <p>
 * The handler is stateless and reusable across menu instances; ownership of the AE2 encoder
 * seam is held in the constructor-injected {@link Ae2PatternEncoder} for testability.
 */
public final class Ae2TopoPatternTransferHandler
                                                 implements IRecipeTransferHandler<PatternEncodingTermMenu, TopoRecipe> {

    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeType<TopoRecipe> jeiRecipeType;
    private final Ae2PatternEncoder encoder;

    public Ae2TopoPatternTransferHandler(
                                         IRecipeTransferHandlerHelper helper,
                                         IRecipeType<TopoRecipe> jeiRecipeType,
                                         Ae2PatternEncoder encoder) {
        this.helper = helper;
        this.jeiRecipeType = jeiRecipeType;
        this.encoder = encoder;
    }

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.of(PatternEncodingTermMenu.TYPE);
    }

    @Override
    public IRecipeType<TopoRecipe> getRecipeType() {
        return jeiRecipeType;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
                                                         PatternEncodingTermMenu menu,
                                                         TopoRecipe recipe,
                                                         IRecipeSlotsView slotsView,
                                                         Player player,
                                                         boolean maxTransfer,
                                                         boolean doTransfer) {
        return transferConverted(menu, TopoRecipeToAe2StackConverter.convert(recipe), doTransfer);
    }

    /**
     * Branch logic isolated from {@link TopoRecipe} construction so JUnit can exercise the three
     * observable outcomes (empty &rarr; error, hover &rarr; null, encode &rarr; encoder call)
     * without standing up the project's recipe-type registry.
     *
     * <p>
     * Package-private deliberately — production code must always go through
     * {@link #transferRecipe} which performs the conversion.
     */
    @Nullable
    IRecipeTransferError transferConverted(
                                           PatternEncodingTermMenu menu,
                                           TopoRecipeToAe2StackConverter.Converted converted,
                                           boolean doTransfer) {
        if (converted.isEmpty()) {
            return helper.createUserErrorWithTooltip(
                    BuiltinTopoMachineUiLang.UI_JEI_TRANSFER_NO_ENCODABLE_IO.getComponent());
        }
        if (!doTransfer) {
            return null;
        }
        encoder.encode(menu, converted.inputs(), converted.outputs());
        return null;
    }
}
