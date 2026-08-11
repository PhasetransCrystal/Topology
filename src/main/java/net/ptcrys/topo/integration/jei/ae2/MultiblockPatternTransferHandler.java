package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.apiv2.machine.MachineDefinition;

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
 * JEI transfer handler that bridges the multiblock structure category to the AE2
 * {@link PatternEncodingTermMenu}. When the user clicks the "+" button on a multiblock structure
 * page, this handler opens the {@link PatternBuilderPopup} instead of directly encoding a pattern —
 * the popup lets the user choose which hatches fill the structure's anyOf cells before committing
 * the pattern.
 *
 * <p>
 * Registered for {@code OIMultiblockJeiCategory.TYPE} (whose recipe objects are the controller
 * {@link MachineDefinition}s themselves) so it shadows AE2's own universal handler for this
 * category — the same shadowing strategy {@link Ae2OIPatternTransferHandler} uses for OI recipe
 * types.
 */
public final class MultiblockPatternTransferHandler
                                                    implements IRecipeTransferHandler<PatternEncodingTermMenu, MachineDefinition> {

    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeType<MachineDefinition> jeiRecipeType;

    public MultiblockPatternTransferHandler(
                                            IRecipeTransferHandlerHelper helper,
                                            IRecipeType<MachineDefinition> jeiRecipeType) {
        this.helper = helper;
        this.jeiRecipeType = jeiRecipeType;
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
    public IRecipeType<MachineDefinition> getRecipeType() {
        return jeiRecipeType;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
                                                         PatternEncodingTermMenu menu,
                                                         MachineDefinition definition,
                                                         IRecipeSlotsView slotsView,
                                                         Player player,
                                                         boolean maxTransfer,
                                                         boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }
        PatternBuilderPopup.openFromTransfer(definition, menu);
        return null;
    }
}
