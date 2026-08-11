package net.ptcrys.topo.api.machine.multiblock.ui;

import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The two {@link PropertyDisplay} shapes (enum and boolean, backing the typed
 * {@link PropertyDisplayRegistry} registration doors) plus the {@link #compare} driver that turns a
 * pinned blueprint state into player-readable expected-vs-current rows. Display text is content
 * handed in as components — never derived from identifiers.
 */
public final class PropertyDisplays {

    private PropertyDisplays() {}

    /**
     * Enum display factory backing {@link PropertyDisplayRegistry#registerEnum}: enum constants are
     * the map keys (the compiler rules out typos), components are the final texts. Construction
     * fails fast when any possible value of any governed property lacks a text, or when a text's key
     * is a value no governed property allows.
     */
    @SafeVarargs
    static <T extends Enum<T> & StringRepresentable> PropertyDisplay ofEnum(
                                                                            Identifier id, Component label, Map<T, Component> valueTexts, EnumProperty<T>... properties) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(valueTexts, "value texts");
        Objects.requireNonNull(properties, "properties");
        if (properties.length == 0) {
            throw new IllegalArgumentException(
                    "Property display '" + id + "' needs at least one governed property");
        }
        Map<T, Component> texts = Map.copyOf(valueTexts);
        Set<T> possibleValues = new HashSet<>();
        for (EnumProperty<T> property : properties) {
            possibleValues.addAll(property.getPossibleValues());
        }
        for (T value : possibleValues) {
            if (!texts.containsKey(value)) {
                throw new IllegalArgumentException("Property display '" + id + "' is missing the text for value '" + value.getSerializedName() + "'");
            }
        }
        for (T key : texts.keySet()) {
            if (!possibleValues.contains(key)) {
                throw new IllegalArgumentException("Property display '" + id + "' has a text for value '" + key.getSerializedName() + "' that no governed property allows");
            }
        }
        return new EnumDisplay<>(List.of(properties), label, texts);
    }

    /**
     * Boolean display factory backing {@link PropertyDisplayRegistry#registerBoolean}: the true text
     * is tinted {@link MachineUiComponentStyle#getLedOutput() green}, the false text
     * {@link MachineUiComponentStyle#getLedError() red}, replacing any caller-supplied color.
     */
    static PropertyDisplay ofBoolean(
                                     Identifier id, Component label, Component whenTrue, Component whenFalse, BooleanProperty... properties) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(whenTrue, "true text");
        Objects.requireNonNull(whenFalse, "false text");
        Objects.requireNonNull(properties, "properties");
        if (properties.length == 0) {
            throw new IllegalArgumentException(
                    "Property display '" + id + "' needs at least one governed property");
        }
        return new BooleanDisplay(
                List.of(properties),
                label,
                tinted(whenTrue, MachineUiComponentStyle.INSTANCE.getLedOutput()),
                tinted(whenFalse, MachineUiComponentStyle.INSTANCE.getLedError()));
    }

    private static Component tinted(Component text, int argb) {
        return text.copy().withStyle(style -> style.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
    }

    /**
     * The enum-property display shape: one text per enum constant, shared across every governed
     * property. Accessors hand out copies — UI surfaces restyle the returned components in place
     * (e.g. the structure page's expected/current tinting), so sharing the templates would let one
     * caller pollute the next.
     */
    static final class EnumDisplay<T extends Enum<T> & StringRepresentable> implements PropertyDisplay {

        private final List<Property<?>> properties;
        private final Component label;
        private final Map<T, Component> texts;

        private EnumDisplay(List<Property<?>> properties, Component label, Map<T, Component> texts) {
            this.properties = properties;
            this.label = label;
            this.texts = texts;
        }

        @Override
        public List<Property<?>> properties() {
            return properties;
        }

        @Override
        public Component label() {
            return label.copy();
        }

        @Override
        public Component value(Property<?> property, Comparable<?> value) {
            Component text = texts.get(value);
            if (text == null) {
                throw new IllegalArgumentException(
                        "Display has no text for value '" + value + "' of property '" + property + "'");
            }
            return text.copy();
        }
    }

    /**
     * The boolean-property display shape: a green true text and a red false text (tinted at
     * construction); the structure page's comparison table deliberately re-tints values with its
     * own goal/fix semantics on top.
     */
    static final class BooleanDisplay implements PropertyDisplay {

        private final List<Property<?>> properties;
        private final Component label;
        private final Component whenTrue;
        private final Component whenFalse;

        private BooleanDisplay(
                               List<Property<?>> properties, Component label, Component whenTrue, Component whenFalse) {
            this.properties = properties;
            this.label = label;
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }

        @Override
        public List<Property<?>> properties() {
            return properties;
        }

        @Override
        public Component label() {
            return label.copy();
        }

        @Override
        public Component value(Property<?> property, Comparable<?> value) {
            return Boolean.TRUE.equals(value) ? whenTrue.copy() : whenFalse.copy();
        }
    }

    /**
     * One expected-vs-current comparison of a displayable property: {@code actual} is null when the
     * world block carries no comparable value (empty cell, different block); {@code matches} is true
     * when there is nothing left to fix on this aspect.
     */
    public record Comparison(Component label, Component expected, @Nullable Component actual, boolean matches) {

        public Comparison {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(expected, "expected");
        }
    }

    /**
     * Compares every displayable property the expected state pins against the actual world state
     * (pass null when the cell is empty or holds a different block — all rows then report
     * "matches" with no actual value, i.e. nothing to contrast, just the goal).
     */
    public static List<Comparison> compare(BlockState expected, @Nullable BlockState actual) {
        Objects.requireNonNull(expected, "expected state");
        boolean comparable = actual != null && actual.getBlock() == expected.getBlock();
        List<Comparison> comparisons = new ArrayList<>();
        for (PropertyDisplay display : PropertyDisplayRegistry.handlesView()) {
            for (Property<?> property : display.properties()) {
                if (!expected.hasProperty(property)) {
                    continue;
                }
                Component expectedValue = display.value(property, expected.getValue(property));
                if (comparable && actual.hasProperty(property) && !actual.getValue(property).equals(expected.getValue(property))) {
                    comparisons.add(new Comparison(
                            display.label(),
                            expectedValue,
                            display.value(property, actual.getValue(property)),
                            false));
                } else {
                    comparisons.add(new Comparison(display.label(), expectedValue, null, true));
                }
            }
        }
        return comparisons;
    }
}
