package net.ptcrys.topodemo.data.machine.common.component;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.ui.LcdData;
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topodemo.api.machine.BuiltinTopoMachineUiLang;
import net.ptcrys.topodemo.api.machine.DemoComponentKeys;
import net.ptcrys.topodemo.data.machine.MachineTier;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.Arrays;
import java.util.List;

/** 仅为运行期配方视图附加燃烧时间消耗，不修改登记的配方。 */
public final class BurnTimeModifier extends MachineComponent implements RecipeModifier {

    public static final ComponentKey<BurnTimeModifier> KEY = DemoComponentKeys.id("burn_time_modifier", BurnTimeModifier.class)
            .service(RecipeModifier.KEY, (modifier, ignored) -> modifier);
    private final long perTick;

    private BurnTimeModifier(ComponentContext<BurnTimeModifier> context, long perTick) {
        super(context);
        this.perTick = perTick;
    }

    public static ComponentMount<BurnTimeModifier> of(MachineTier tier) {
        long perTick = 1L << (tier.level() - 1);
        return KEY.mount(context -> new BurnTimeModifier(context, perTick));
    }

    @Override
    public Component title() {
        return BuiltinTopoResourceIntegrations.BURN_TIME.recipeCapability().displayName();
    }

    @Override
    public Component description() {
        return Component.literal(perTick + "/t");
    }

    @Override
    public UIElement createDetailsUi() {
        return MachineUiContainerTemplate.INSTANCE.createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_POWER.getComponent(),
                        description(), LcdData.LED_RUNNING);
    }

    @Override
    public TopoRecipe modify(TopoRecipe recipe) {
        TopoRecipe.InputEntry<?>[] existing = recipe.tickInputs();
        TopoRecipe.InputEntry<?>[] inputs = Arrays.copyOf(existing, existing.length + 1);
        inputs[existing.length] = new TopoRecipe.InputEntry<>(BuiltinTopoResourceIntegrations.BURN_TIME.recipeCapability(), List.of(perTick));
        return recipe.recipeType().createRecipe(recipe.inputs(), recipe.outputs(), inputs, recipe.tickOutputs(),
                recipe.duration(), recipe.productionLines());
    }
}
