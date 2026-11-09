package net.ptcrys.topo.mixin.jei;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.integration.jei.TopoMultiblockJeiCategory;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;
import java.util.stream.Stream;

/** 只调整 Topo 结构分类的原生工作方块栏，按当前可见预览更新；JEI 未安装时跳过目标。 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
public abstract class JeiRecipeGuiLogicMixin {

    @Shadow
    private ILookupState state;

    @Shadow
    private @Nullable IRecipeLayoutList cachedRecipeLayoutsWithButtons;

    @Inject(method = "getCraftingStations", at = @At("HEAD"), cancellable = true)
    private void topo$visibleStructureStations(CallbackInfoReturnable<Stream<Consumer<IIngredientAcceptor<?>>>> callback) {
        if (!(state.getFocusedRecipes().getRecipeCategory() instanceof TopoMultiblockJeiCategory)) return;
        if (cachedRecipeLayoutsWithButtons == null) {
            callback.setReturnValue(Stream.empty());
            return;
        }
        callback.setReturnValue(state.getVisible(cachedRecipeLayoutsWithButtons).stream()
                .map(layout -> layout.getRecipeLayout().getRecipe())
                .filter(MachineDefinition.class::isInstance)
                .map(MachineDefinition.class::cast)
                .distinct()
                .map(machine -> acceptor -> acceptor.add(machine.registeredBlock().get())));
    }
}
