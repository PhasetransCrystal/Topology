package net.ptcrys.topo.datav2.machine.common.component;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.MachineComponent;
import net.ptcrys.topo.apiv2.machine.component.RecipeModifier;
import net.ptcrys.topo.apiv2.machine.component.RecipeModifierDisplay;
import net.ptcrys.topo.apiv2.machine.ui.LcdData;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineUiLang;
import net.ptcrys.topo.datav2.machine.MachineTier;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.Objects;

/**
 * Per-scalar-capability performance RecipeModifiers and fixed parallel batching (nested types share this
 * file).
 *
 * <p>
 * Each tier modifier only scales its own {@link ScalarRecipeCapability}. Duration is applied at most
 * once (the {@code of} factory); companion {@code amounts} factories scale that capability's amounts only.
 * Dual-cost machines mount e.g. {@link EnergyTierModifier} + {@link HeatTierModifier#amounts} so
 * duration is not multiplied twice.
 *
 * <p>
 * {@link ParallelModifier} caps content parallel at a declaration-time maximum, then asks each
 * input capability for its side-effect-free quantity bound. Duration is unchanged; materials/s scales
 * with the actual parallel. Mount alongside tier modifiers (e.g. large grinder = T3 energy +
 * max 4 parallel).
 */
public final class PerformanceRecipeModifiers {

    private PerformanceRecipeModifiers() {}

    // ── shared tables (T1 / T2 / T3) ──────────────────────────────────────────

    private static final double[] CONSUMER_DURATION = { 1.0d, 0.5d, 0.25d };
    private static final double[] CONSUMER_POWER = { 1.0d, 3.0d, 9.0d };

    private static final double[] PRODUCER_DURATION = { 1.0d, 0.5d, 0.25d };
    private static final double[] PRODUCER_YIELD = { 1.0d, 2.5d, 6.25d };

    private static final double[] CONVERTER_DURATION = { 1.0d, 0.5d, 0.25d };
    private static final double[] CONVERTER_POWER = { 1.0d, 2.0d, 4.0d };
    private static final double[] CONVERTER_YIELD = { 0.7d, 0.85d, 0.95d };

    // ── ENERGY consumer ──────────────────────────────────────────────────────

    /** Scales ENERGY capability only. Use {@link #of} alone, or with {@link HeatTierModifier#amounts}. */
    public static final class EnergyTierModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<EnergyTierModifier> KEY = ComponentKey.oi(
                "energy_tier_modifier", EnergyTierModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private final Component title;
        private final Component description;
        private final double durationFactor;
        private final double powerFactor;
        private final boolean scaleDuration;

        private EnergyTierModifier(
                                   ComponentContext<EnergyTierModifier> context,
                                   Component title,
                                   Component description,
                                   double durationFactor,
                                   double powerFactor,
                                   boolean scaleDuration) {
            super(context);
            this.title = title;
            this.description = description;
            this.durationFactor = durationFactor;
            this.powerFactor = powerFactor;
            this.scaleDuration = scaleDuration;
        }

        public static ComponentMount<EnergyTierModifier> of(MachineTier tier) {
            return mount(tier, true);
        }

        /** Same power curve, no duration —for dual-cost companions. */
        public static ComponentMount<EnergyTierModifier> amounts(MachineTier tier) {
            return mount(tier, false);
        }

        private static ComponentMount<EnergyTierModifier> mount(MachineTier tier, boolean scaleDuration) {
            int i = tierIndex(tier);
            int level = tier.level();
            double durationFactor = CONSUMER_DURATION[i];
            double powerFactor = CONSUMER_POWER[i];
            Component title = BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_CONSUMER_ENERGY_NAME.getComponent(level);
            Component description = RecipeModifierDescriptionLine.consumer(durationFactor, powerFactor, scaleDuration);
            // Always keep tier duration for UI-derived totals; scaleDuration only gates modify().
            return KEY.mount(
                    ctx -> new EnergyTierModifier(
                            ctx, title, description, durationFactor, powerFactor, scaleDuration),
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
            return consumerLcd(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_SCALAR_ENERGY.getComponent(),
                    durationFactor,
                    powerFactor,
                    scaleDuration,
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_ENERGY.getComponent());
        }

        @Override
        public OIRecipe modify(OIRecipe recipe) {
            ScalarRecipeCapability capability = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
            if (scaleDuration) {
                return recipe.withScaledCapabilityPerformance(capability, durationFactor, powerFactor);
            }
            return recipe.withScaledCapabilityAmounts(capability, powerFactor);
        }
    }

    // ── HEAT consumer ────────────────────────────────────────────────────────

    /**
     * HEAT capability. {@link #of} / {@link #amounts} = 耗热; {@link #outputAmounts} = 产热 companion
     * (same consumer power curve, output-facing labels — e.g. resistive heater).
     */
    public static final class HeatTierModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<HeatTierModifier> KEY = ComponentKey.oi(
                "heat_tier_modifier", HeatTierModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private enum Role {
            /** Full consume: duration + heat I/O × power. */
            CONSUME,
            /** Companion consume amounts only (chemical reactor heat cost). */
            CONSUME_AMOUNTS,
            /** Companion output amounts only (heater heat release). */
            OUTPUT_AMOUNTS
        }

        private final Component title;
        private final Component description;
        private final double durationFactor;
        private final double powerFactor;
        private final Role role;

        private HeatTierModifier(
                                 ComponentContext<HeatTierModifier> context,
                                 Component title,
                                 Component description,
                                 double durationFactor,
                                 double powerFactor,
                                 Role role) {
            super(context);
            this.title = title;
            this.description = description;
            this.durationFactor = durationFactor;
            this.powerFactor = powerFactor;
            this.role = role;
        }

        public static ComponentMount<HeatTierModifier> of(MachineTier tier) {
            return mount(tier, Role.CONSUME);
        }

        /** Heat cost companion: no duration (e.g. chemical reactor with EnergyTierModifier.of). */
        public static ComponentMount<HeatTierModifier> amounts(MachineTier tier) {
            return mount(tier, Role.CONSUME_AMOUNTS);
        }

        /**
         * Heat output companion on the consumer power curve (e.g. resistive heater: energy in + heat
         * out). UI is 产热, not 耗热.
         */
        public static ComponentMount<HeatTierModifier> outputAmounts(MachineTier tier) {
            return mount(tier, Role.OUTPUT_AMOUNTS);
        }

        private static ComponentMount<HeatTierModifier> mount(MachineTier tier, Role role) {
            int i = tierIndex(tier);
            int level = tier.level();
            double durationFactor = CONSUMER_DURATION[i];
            double powerFactor = CONSUMER_POWER[i];
            Component title = switch (role) {
                case CONSUME, CONSUME_AMOUNTS -> BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_CONSUMER_HEAT_NAME.getComponent(level);
                case OUTPUT_AMOUNTS -> BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_PRODUCER_HEAT_NAME.getComponent(level);
            };
            Component description = switch (role) {
                case CONSUME -> RecipeModifierDescriptionLine.consumer(durationFactor, powerFactor, true);
                case CONSUME_AMOUNTS -> RecipeModifierDescriptionLine.consumer(durationFactor, powerFactor, false);
                case OUTPUT_AMOUNTS -> RecipeModifierDescriptionLine.single(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_HEAT_OUT.getComponent(),
                        powerFactor,
                        RecipeModifierDescriptionLine.primaryRgb());
            };
            return KEY.mount(
                    ctx -> new HeatTierModifier(
                            ctx, title, description, durationFactor, powerFactor, role),
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
            boolean scaleDuration = role == Role.CONSUME;
            return switch (role) {
                case CONSUME, CONSUME_AMOUNTS -> consumerLcd(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_SCALAR_HEAT.getComponent(),
                        durationFactor,
                        powerFactor,
                        scaleDuration,
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_HEAT_COST.getComponent());
                case OUTPUT_AMOUNTS -> outputAmountsLcd(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_SCALAR_HEAT.getComponent(),
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_HEAT_OUT.getComponent(),
                        durationFactor,
                        powerFactor,
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_HEAT.getComponent());
            };
        }

        @Override
        public OIRecipe modify(OIRecipe recipe) {
            ScalarRecipeCapability capability = BuiltinOIResourceIntegrations.HEAT.recipeCapability();
            return switch (role) {
                case CONSUME -> recipe.withScaledCapabilityPerformance(capability, durationFactor, powerFactor);
                case CONSUME_AMOUNTS -> recipe.withScaledCapabilityAmounts(capability, powerFactor, 1.0d);
                case OUTPUT_AMOUNTS -> recipe.withScaledCapabilityAmounts(capability, 1.0d, powerFactor);
            };
        }
    }

    // ── ADVANCED_ENERGY consumer ─────────────────────────────────────────────

    public static final class AdvancedEnergyTierModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<AdvancedEnergyTierModifier> KEY = ComponentKey.oi(
                "advanced_energy_tier_modifier", AdvancedEnergyTierModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private final Component title;
        private final Component description;
        private final double durationFactor;
        private final double powerFactor;
        private final boolean scaleDuration;

        private AdvancedEnergyTierModifier(
                                           ComponentContext<AdvancedEnergyTierModifier> context,
                                           Component title,
                                           Component description,
                                           double durationFactor,
                                           double powerFactor,
                                           boolean scaleDuration) {
            super(context);
            this.title = title;
            this.description = description;
            this.durationFactor = durationFactor;
            this.powerFactor = powerFactor;
            this.scaleDuration = scaleDuration;
        }

        public static ComponentMount<AdvancedEnergyTierModifier> of(MachineTier tier) {
            return mount(tier, true);
        }

        public static ComponentMount<AdvancedEnergyTierModifier> amounts(MachineTier tier) {
            return mount(tier, false);
        }

        private static ComponentMount<AdvancedEnergyTierModifier> mount(
                                                                        MachineTier tier, boolean scaleDuration) {
            int i = tierIndex(tier);
            int level = tier.level();
            double durationFactor = CONSUMER_DURATION[i];
            double powerFactor = CONSUMER_POWER[i];
            Component title = BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_CONSUMER_ADV_ENERGY_NAME.getComponent(level);
            Component description = RecipeModifierDescriptionLine.consumer(durationFactor, powerFactor, scaleDuration);
            return KEY.mount(
                    ctx -> new AdvancedEnergyTierModifier(
                            ctx, title, description, durationFactor, powerFactor, scaleDuration),
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
            return consumerLcd(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_SCALAR_ADVANCED_ENERGY.getComponent(),
                    durationFactor,
                    powerFactor,
                    scaleDuration,
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_ADV_ENERGY.getComponent());
        }

        @Override
        public OIRecipe modify(OIRecipe recipe) {
            ScalarRecipeCapability capability = BuiltinOIResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
            if (scaleDuration) {
                return recipe.withScaledCapabilityPerformance(capability, durationFactor, powerFactor);
            }
            return recipe.withScaledCapabilityAmounts(capability, powerFactor);
        }
    }

    // ── ENERGY producer ──────────────────────────────────────────────────────

    public static final class EnergyGenerationTierModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<EnergyGenerationTierModifier> KEY = ComponentKey.oi(
                "energy_generation_tier_modifier", EnergyGenerationTierModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private final Component title;
        private final Component description;
        private final double durationFactor;
        private final double yieldFactor;

        private EnergyGenerationTierModifier(
                                             ComponentContext<EnergyGenerationTierModifier> context,
                                             Component title,
                                             Component description,
                                             double durationFactor,
                                             double yieldFactor) {
            super(context);
            this.title = title;
            this.description = description;
            this.durationFactor = durationFactor;
            this.yieldFactor = yieldFactor;
        }

        public static ComponentMount<EnergyGenerationTierModifier> of(MachineTier tier) {
            int i = tierIndex(tier);
            int level = tier.level();
            double durationFactor = PRODUCER_DURATION[i];
            double yieldFactor = PRODUCER_YIELD[i];
            Component title = BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_PRODUCER_ENERGY_NAME.getComponent(level);
            Component description = RecipeModifierDescriptionLine.producer(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_GENERATION.getComponent(),
                    durationFactor,
                    yieldFactor);
            return KEY.mount(
                    ctx -> new EnergyGenerationTierModifier(
                            ctx, title, description, durationFactor, yieldFactor),
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
            return producerLcd(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_GENERATION.getComponent(),
                    durationFactor,
                    yieldFactor,
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_GENERATION.getComponent());
        }

        @Override
        public OIRecipe modify(OIRecipe recipe) {
            // Outputs only: fuel items/fluids stay; energy out × yield, duration scaled.
            ScalarRecipeCapability capability = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
            return recipe.withScaledDuration(durationFactor)
                    .withScaledCapabilityAmounts(capability, 1.0d, yieldFactor);
        }
    }

    // ── HEAT producer ────────────────────────────────────────────────────────

    public static final class HeatGenerationTierModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<HeatGenerationTierModifier> KEY = ComponentKey.oi(
                "heat_generation_tier_modifier", HeatGenerationTierModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private final Component title;
        private final Component description;
        private final double durationFactor;
        private final double yieldFactor;

        private HeatGenerationTierModifier(
                                           ComponentContext<HeatGenerationTierModifier> context,
                                           Component title,
                                           Component description,
                                           double durationFactor,
                                           double yieldFactor) {
            super(context);
            this.title = title;
            this.description = description;
            this.durationFactor = durationFactor;
            this.yieldFactor = yieldFactor;
        }

        public static ComponentMount<HeatGenerationTierModifier> of(MachineTier tier) {
            int i = tierIndex(tier);
            int level = tier.level();
            double durationFactor = PRODUCER_DURATION[i];
            double yieldFactor = PRODUCER_YIELD[i];
            Component title = BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_PRODUCER_HEAT_NAME.getComponent(level);
            Component description = RecipeModifierDescriptionLine.producer(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_HEAT_OUT.getComponent(),
                    durationFactor,
                    yieldFactor);
            return KEY.mount(
                    ctx -> new HeatGenerationTierModifier(
                            ctx, title, description, durationFactor, yieldFactor),
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
            return producerLcd(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_HEAT_OUT.getComponent(),
                    durationFactor,
                    yieldFactor,
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_HEAT.getComponent());
        }

        @Override
        public OIRecipe modify(OIRecipe recipe) {
            ScalarRecipeCapability capability = BuiltinOIResourceIntegrations.HEAT.recipeCapability();
            return recipe.withScaledDuration(durationFactor)
                    .withScaledCapabilityAmounts(capability, 1.0d, yieldFactor);
        }
    }

    // ── ENERGY —ADVANCED_ENERGY converter ───────────────────────────────────

    public static final class EnergyConversionTierModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<EnergyConversionTierModifier> KEY = ComponentKey.oi(
                "energy_conversion_tier_modifier", EnergyConversionTierModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private final Component title;
        private final Component description;
        private final double durationFactor;
        private final double powerFactor;
        private final double yieldFactor;

        private EnergyConversionTierModifier(
                                             ComponentContext<EnergyConversionTierModifier> context,
                                             Component title,
                                             Component description,
                                             double durationFactor,
                                             double powerFactor,
                                             double yieldFactor) {
            super(context);
            this.title = title;
            this.description = description;
            this.durationFactor = durationFactor;
            this.powerFactor = powerFactor;
            this.yieldFactor = yieldFactor;
        }

        public static ComponentMount<EnergyConversionTierModifier> of(MachineTier tier) {
            int i = tierIndex(tier);
            int level = tier.level();
            double durationFactor = CONVERTER_DURATION[i];
            double powerFactor = CONVERTER_POWER[i];
            double yieldFactor = CONVERTER_YIELD[i];
            Component title = BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_CONVERTER_NAME.getComponent(level);
            Component description = RecipeModifierDescriptionLine.converter(durationFactor, powerFactor, yieldFactor);
            return KEY.mount(
                    ctx -> new EnergyConversionTierModifier(
                            ctx, title, description, durationFactor, powerFactor, yieldFactor),
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
            double speed = 1.0d / durationFactor;
            double inCraft = durationFactor * powerFactor;
            double outCraft = inCraft * yieldFactor;
            return MachineUiContainerTemplate.INSTANCE
                    .createLcdData(LcdData.Orientation.VERTICAL)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                            factor(durationFactor),
                            LcdData.LED_RUNNING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_POWER.getComponent(),
                            factor(powerFactor),
                            LcdData.LED_RUNNING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_YIELD.getComponent(),
                            factor(yieldFactor),
                            LcdData.LED_RUNNING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_SPEED.getComponent(),
                            factor(speed),
                            LcdData.LED_WAITING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_INPUT.getComponent(),
                            factor(inCraft),
                            LcdData.LED_WAITING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_TOTAL_OUTPUT.getComponent(),
                            factor(outCraft),
                            LcdData.LED_WAITING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_OUTPUT_RATE.getComponent(),
                            factor(powerFactor * yieldFactor),
                            LcdData.LED_WAITING);
        }

        @Override
        public OIRecipe modify(OIRecipe recipe) {
            RecipeCapability<?, ?> energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
            RecipeCapability<?, ?> adv = BuiltinOIResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
            return recipe.withScaledCapabilityConversion(
                    energy, adv, durationFactor, powerFactor, powerFactor * yieldFactor);
        }
    }

    // ── dynamic parallel (input-limited, capped) ─────────────────────────────

    /**
     * Content parallel capped at a declaration-time maximum. At run start (and when no run lock is
     * held), each lane calculates its maximum directly from one input snapshot, then
     * {@link OIRecipe#withParallel(long)} applies the smallest bound. Duration is unchanged —
     * materials/s therefore scales with actual parallel, not a forced ×N.
     *
     * <p>
     * {@link RecipeLogic} persists the applied factor for the active run so reload does not
     * re-probe mid-craft.
     */
    public static final class ParallelModifier extends MachineComponent implements RecipeModifier {

        public static final ComponentKey<ParallelModifier> KEY = ComponentKey.oi(
                "parallel_modifier", ParallelModifier.class)
                .service(RecipeModifier.KEY, (t, u) -> t);

        private final Component title;
        private final Component description;
        private final long maxParallels;

        private ParallelModifier(
                                 ComponentContext<ParallelModifier> context,
                                 Component title,
                                 Component description,
                                 long maxParallels) {
            super(context);
            this.title = title;
            this.description = description;
            this.maxParallels = maxParallels;
        }

        public static ComponentMount<ParallelModifier> of(long maxParallels) {
            if (maxParallels < 2) {
                throw new IllegalArgumentException("maxParallels must be >= 2 (was " + maxParallels + ")");
            }
            Component title = BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_PARALLEL_TITLE.getComponent();
            Component description = RecipeModifierDescriptionLine.single(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_PARALLEL.getComponent(),
                    maxParallels,
                    RecipeModifierDescriptionLine.primaryRgb());
            return KEY.mount(
                    ctx -> new ParallelModifier(ctx, title, description, maxParallels),
                    new RecipeModifierDisplay(title, description));
        }

        /** Declaration-time upper bound (not the last applied run value). */
        public long maxParallels() {
            return maxParallels;
        }

        /** @deprecated use {@link #maxParallels()} */
        @Deprecated
        public long parallels() {
            return maxParallels;
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
            return MachineUiContainerTemplate.INSTANCE
                    .createLcdData(LcdData.Orientation.VERTICAL)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_PARALLEL.getComponent(),
                            factor(maxParallels),
                            LcdData.LED_RUNNING)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_EFFECT.getComponent(),
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_PARALLEL_EFFECT.getComponent(),
                            LcdData.LED_TEXT)
                    .addStaticEntry(
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_THROUGHPUT.getComponent(),
                            BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_PARALLEL_THROUGHPUT.getComponent(
                                    maxParallels),
                            LcdData.LED_TEXT);
        }

        /** Parallel is selected and applied once by {@link RecipeLogic}. */
        @Override
        public OIRecipe modify(OIRecipe recipe) {
            return recipe;
        }

        @Override
        public long parallelCap() {
            return maxParallels;
        }
    }

    // ── shared UI / utils ────────────────────────────────────────────────────

    private static UIElement consumerLcd(
                                         Component scalarName,
                                         double durationFactor,
                                         double powerFactor,
                                         boolean scaleDuration,
                                         Component perCraftLabel) {
        // durationFactor is always the tier curve (for derived totals). scaleDuration only means
        // this modifier applies it in modify(); companions still use it to show 单次 = d×p correctly.
        double speed = 1.0d / durationFactor;
        double perCraft = durationFactor * powerFactor;
        LcdData lcd = MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_AFFECTS.getComponent(),
                        scalarName,
                        LcdData.LED_TEXT);
        if (scaleDuration) {
            lcd.addStaticEntry(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                    factor(durationFactor),
                    LcdData.LED_RUNNING);
        }
        lcd.addStaticEntry(
                BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_POWER.getComponent(),
                factor(powerFactor),
                LcdData.LED_RUNNING);
        if (scaleDuration) {
            lcd.addStaticEntry(
                    BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_SPEED.getComponent(),
                    factor(speed),
                    LcdData.LED_WAITING);
        }
        // Always 单次 = 耗时×功率 (amounts-only must not show bare power as "单次").
        lcd.addStaticEntry(perCraftLabel, factor(perCraft), LcdData.LED_WAITING);
        return lcd;
    }

    private static UIElement producerLcd(
                                         Component rateLabel, double durationFactor, double yieldFactor, Component perCraftLabel) {
        double speed = 1.0d / durationFactor;
        double perCraft = durationFactor * yieldFactor;
        return MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                        factor(durationFactor),
                        LcdData.LED_RUNNING)
                .addStaticEntry(rateLabel, factor(yieldFactor), LcdData.LED_RUNNING)
                .addStaticEntry(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_SPEED.getComponent(),
                        factor(speed),
                        LcdData.LED_WAITING)
                .addStaticEntry(perCraftLabel, factor(perCraft), LcdData.LED_WAITING);
    }

    /**
     * Companion output on the consumer power curve: show 产热/t + 单次产热 = d×p (duration owned by
     * the energy-side modifier).
     */
    private static UIElement outputAmountsLcd(
                                              Component scalarName,
                                              Component rateLabel,
                                              double durationFactor,
                                              double powerFactor,
                                              Component perCraftLabel) {
        double perCraft = durationFactor * powerFactor;
        return MachineUiContainerTemplate.INSTANCE
                .createLcdData(LcdData.Orientation.VERTICAL)
                .addStaticEntry(
                        BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_ATTR_AFFECTS.getComponent(),
                        scalarName,
                        LcdData.LED_TEXT)
                .addStaticEntry(rateLabel, factor(powerFactor), LcdData.LED_RUNNING)
                .addStaticEntry(perCraftLabel, factor(perCraft), LcdData.LED_WAITING);
    }

    private static int tierIndex(MachineTier tier) {
        return Objects.requireNonNull(tier, "tier").ordinal();
    }

    private static Component factor(double value) {
        return BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_FACTOR.getComponent(formatFactor(value));
    }

    private static Component factor(long value) {
        return BuiltinOIMachineUiLang.UI_RECIPE_MODIFIER_FACTOR.getComponent(Long.toString(value));
    }

    private static String formatFactor(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Integer.toString((int) value);
        }
        if (Math.abs(value * 100.0d - Math.rint(value * 100.0d)) < 1.0e-9) {
            double rounded = Math.rint(value * 100.0d) / 100.0d;
            if (rounded == Math.rint(rounded)) {
                return Integer.toString((int) rounded);
            }
            return Double.toString(rounded);
        }
        return Double.toString(value);
    }
}
