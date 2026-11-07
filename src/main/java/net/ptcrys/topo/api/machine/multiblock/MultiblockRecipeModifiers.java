package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.component.ServiceMatch;
import net.ptcrys.topo.api.machine.ui.LcdData;
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.api.recipe.TopoRecipe;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Recipe-modifier bridge from formed member traits to the controller's RecipeLogic. */
final class MultiblockRecipeModifiers {

    private final MachineBlockEntity controller;
    private final FormedRuntime runtime;

    MultiblockRecipeModifiers(MachineBlockEntity controller, FormedRuntime runtime) {
        this.controller = Objects.requireNonNull(controller, "multiblock controller");
        this.runtime = Objects.requireNonNull(runtime, "formed runtime");
    }

    RecipeModifier memberFoldModifier(@Nullable RecipeLogic context) {
        List<ServiceMatch<RecipeModifier>> memberModifiers = runtime.services(RecipeModifier.KEY, context);
        return new RecipeModifier() {

            @Override
            public Component title() {
                return TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_NAME.getComponent();
            }

            @Override
            public Component description() {
                return TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_DESC.getComponent();
            }

            @Override
            public UIElement createDetailsUi() {
                return MachineUiContainerTemplate.INSTANCE
                        .createLcdData(LcdData.Orientation.VERTICAL)
                        .addStaticEntry(
                                TopoApiLang.UI_RECIPE_MODIFIER_ATTR_SCOPE.getComponent(),
                                TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_SCOPE.getComponent(),
                                LcdData.LED_TEXT)
                        .addStaticEntry(
                                TopoApiLang.UI_RECIPE_MODIFIER_ATTR_EFFECT.getComponent(),
                                TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_EFFECT.getComponent(),
                                LcdData.LED_RUNNING);
            }

            @Override
            public @NonNull TopoRecipe modify(@NonNull TopoRecipe recipe) {
                return fold(recipe, memberModifiers);
            }

            @Override
            public @NonNull TopoRecipe modify(@NonNull TopoRecipe recipe, @Nullable MachineBlockEntity host) {
                return fold(recipe, memberModifiers);
            }

            @Override
            public long parallelCap() {
                return maxParallelCap(memberModifiers);
            }
        };
    }

    private TopoRecipe fold(TopoRecipe recipe, List<ServiceMatch<RecipeModifier>> modifiers) {
        TopoRecipe modified = recipe;
        for (ServiceMatch<RecipeModifier> match : modifiers) {
            modified = match.value().modify(modified, controller);
        }
        return modified;
    }

    private static long maxParallelCap(List<ServiceMatch<RecipeModifier>> modifiers) {
        long cap = 1L;
        for (ServiceMatch<RecipeModifier> match : modifiers) {
            cap = Math.max(cap, match.value().parallelCap());
        }
        return cap;
    }
}
