package net.ptcrys.topo.data.machine.common.component;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.component.RecipeModifierDisplay;
import net.ptcrys.topo.api.machine.ui.LcdData;
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Temporary UI-preview RecipeModifiers (identity {@link #modify}). Mounted on the macerator only so
 * multi-entry spacing in the recipe-modifier list can be reviewed in-game; remove when layout is
 * settled.
 */
public final class PreviewRecipeModifierComponent extends MachineComponent implements RecipeModifier {

    public static final ComponentKey<PreviewRecipeModifierComponent> PARALLEL = key("preview_recipe_modifier_parallel");
    public static final ComponentKey<PreviewRecipeModifierComponent> OVERCLOCK = key("preview_recipe_modifier_overclock");
    public static final ComponentKey<PreviewRecipeModifierComponent> CATALYST = key("preview_recipe_modifier_catalyst");

    private final Component title;
    private final Component description;
    private final Supplier<UIElement> detailsFactory;

    private PreviewRecipeModifierComponent(
                                           ComponentContext<PreviewRecipeModifierComponent> context,
                                           Component title,
                                           Component description,
                                           Supplier<UIElement> detailsFactory) {
        super(context);
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.detailsFactory = Objects.requireNonNull(detailsFactory, "detailsFactory");
    }

    /** 2-row LCD: parallelism + mode. */
    public static ComponentMount<PreviewRecipeModifierComponent> parallel() {
        Component title = BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_PARALLEL_TITLE.getComponent();
        Component description = RecipeModifierDescriptionLine.single(
                BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_ATTR_PARALLEL.getComponent(),
                2.0d,
                RecipeModifierDescriptionLine.primaryRgb());
        return PARALLEL.mount(
                context -> new PreviewRecipeModifierComponent(
                        context, title, description, PreviewRecipeModifierComponent::parallelLcd),
                new RecipeModifierDisplay(title, description));
    }

    /** 3-row LCD: longer block to stress vertical gap. */
    public static ComponentMount<PreviewRecipeModifierComponent> overclock() {
        Component title = BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_OVERCLOCK_TITLE.getComponent();
        Component description = RecipeModifierDescriptionLine.consumer(0.5d, 4.0d, true);
        return OVERCLOCK.mount(
                context -> new PreviewRecipeModifierComponent(
                        context, title, description, PreviewRecipeModifierComponent::overclockLcd),
                new RecipeModifierDisplay(title, description));
    }

    /** 1-row LCD: short block. */
    public static ComponentMount<PreviewRecipeModifierComponent> catalyst() {
        Component title = BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_CATALYST_TITLE.getComponent();
        Component description = RecipeModifierDescriptionLine.singleValue(
                BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_ATTR_BONUS.getComponent(),
                Component.literal("+10%"),
                RecipeModifierDescriptionLine.tertiaryRgb());
        return CATALYST.mount(
                context -> new PreviewRecipeModifierComponent(
                        context, title, description, PreviewRecipeModifierComponent::catalystLcd),
                new RecipeModifierDisplay(title, description));
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public Component description() {
        return description;
    }

    @Override
    public UIElement createDetailsUi() {
        return detailsFactory.get();
    }

    @Override
    public TopoRecipe modify(TopoRecipe recipe) {
        return recipe;
    }

    private static ComponentKey<PreviewRecipeModifierComponent> key(String path) {
        return ComponentKey.id(path, PreviewRecipeModifierComponent.class)
                .service(RecipeModifier.KEY, (trait, unused) -> trait);
    }

    private static UIElement parallelLcd() {
        return MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_ATTR_PARALLEL.getComponent(),
                        Component.literal("×2"),
                        LcdData.LED_RUNNING)
                .addStaticEntry(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_ATTR_MODE.getComponent(),
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_PARALLEL_MODE.getComponent(),
                        LcdData.LED_TEXT);
    }

    private static UIElement overclockLcd() {
        return MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_ATTR_OC.getComponent(),
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_OVERCLOCK_MODE.getComponent(),
                        LcdData.LED_WAITING)
                .addStaticEntry(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                        Component.literal("×0.5"),
                        LcdData.LED_RUNNING)
                .addStaticEntry(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_POWER.getComponent(),
                        Component.literal("×4"),
                        LcdData.LED_RUNNING);
    }

    private static UIElement catalystLcd() {
        return MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_PREVIEW_ATTR_BONUS.getComponent(),
                        Component.literal("+10%"),
                        LcdData.LED_OUTPUT);
    }
}
