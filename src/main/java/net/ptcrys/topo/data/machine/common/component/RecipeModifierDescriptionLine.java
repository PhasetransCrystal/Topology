package net.ptcrys.topo.data.machine.common.component;

import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * One-line recipe-modifier summaries for machine item tooltips: {@code 耗时×0.5 功率×2} with
 * per-segment colors. Label muted gray; free-param values use the same LED role hues as the machine
 * UI style palette (running blue / waiting amber / output green) without loading UI texture classes.
 */
public final class RecipeModifierDescriptionLine {

    // RGB only — keep in sync with MachineUiComponentStyle palette (muted / running / waiting / output).
    private static final int LABEL_RGB = 0x8B96A3;
    private static final int PRIMARY_RGB = 0x3F94B5;
    private static final int SECONDARY_RGB = 0xE89E47;
    private static final int TERTIARY_RGB = 0x7FBF35;

    private RecipeModifierDescriptionLine() {}

    /** Consumer free params: duration (if applied) + power. */
    public static Component consumer(double durationFactor, double powerFactor, boolean scaleDuration) {
        Component power = param(
                BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_POWER.getComponent(),
                powerFactor,
                scaleDuration ? SECONDARY_RGB : PRIMARY_RGB);
        if (!scaleDuration) {
            return power;
        }
        return join(
                param(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                        durationFactor,
                        PRIMARY_RGB),
                power);
    }

    /** Producer free params: duration + rate label (generation / heat out). */
    public static Component producer(Component rateLabel, double durationFactor, double yieldFactor) {
        return join(
                param(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                        durationFactor,
                        PRIMARY_RGB),
                param(rateLabel, yieldFactor, SECONDARY_RGB));
    }

    /** Converter free params: duration + power + yield. */
    public static Component converter(double durationFactor, double powerFactor, double yieldFactor) {
        return join(
                param(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_DURATION.getComponent(),
                        durationFactor,
                        PRIMARY_RGB),
                param(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_POWER.getComponent(),
                        powerFactor,
                        SECONDARY_RGB),
                param(
                        BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_ATTR_YIELD.getComponent(),
                        yieldFactor,
                        TERTIARY_RGB));
    }

    /** Single {@code label×value} segment (e.g. preview parallel). */
    public static Component single(Component label, double value, int valueRgb) {
        return param(label, value, valueRgb & 0xFFFFFF);
    }

    /** Exact long-valued counterpart used for resource and parallel limits. */
    public static Component single(Component label, long value, int valueRgb) {
        return Component.empty()
                .append(label.copy().withColor(LABEL_RGB))
                .append(factor(value).copy().withColor(valueRgb & 0xFFFFFF));
    }

    /** Single segment with a pre-styled value component (e.g. {@code +10%}). */
    public static Component singleValue(Component label, Component value, int valueRgb) {
        return Component.empty()
                .append(label.copy().withColor(LABEL_RGB))
                .append(value.copy().withColor(valueRgb & 0xFFFFFF));
    }

    public static int primaryRgb() {
        return PRIMARY_RGB;
    }

    public static int secondaryRgb() {
        return SECONDARY_RGB;
    }

    public static int tertiaryRgb() {
        return TERTIARY_RGB;
    }

    private static Component param(Component label, double value, int valueRgb) {
        return Component.empty()
                .append(label.copy().withColor(LABEL_RGB))
                .append(factor(value).copy().withColor(valueRgb));
    }

    private static Component join(Component... parts) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(Component.literal(" "));
            }
            result.append(parts[i]);
        }
        return result;
    }

    private static Component factor(double value) {
        return BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_FACTOR.getComponent(formatFactor(value));
    }

    private static Component factor(long value) {
        return BuiltinTopoMachineUiLang.UI_RECIPE_MODIFIER_FACTOR.getComponent(Long.toString(value));
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
