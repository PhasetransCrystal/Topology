package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Objects;

/**
 * Parameterized factories for the built-in {@link PropertyRule} kinds (stateless {@code Xxxs}
 * namespace). The factories are content-free: the governed vanilla {@link Property} constants are
 * passed in as arguments, so this class names no builtin instance — the named strong handles live
 * in data ({@code BuiltinTopoPropertyRules}).
 *
 * <p>
 * The covariant factories ({@link #direction}, {@link #axis}, {@link #half}) build rules whose
 * forward transform rides {@link Orientation#apply(Direction)}. They are partial over restricted
 * value spaces and report that via {@link PropertyRule#supports(Orientation)} so the recognition
 * entry can refuse the orientation up front. {@link #derived} builds an identity rule whose
 * {@link PropertyRule#maskedProperties()} are excluded from the recognition compare.
 */
public final class PropertyRules {

    private PropertyRules() {}

    /**
     * COVARIANT rule for facing-direction properties. A block state carries at most one of the given
     * {@code properties}; the first one present is sent through {@link Orientation#apply(Direction)}.
     *
     * <p>
     * {@link PropertyRule#supports(Orientation)} is the <b>conjunction</b> over every governed
     * property: the rule supports an orientation only when each property's whole value space is closed
     * under it. A handle governing a 4-way horizontal property therefore refuses pitch orientations
     * even for cells whose state carries a full 6-way property — declare a narrower
     * {@code direction(...)} rule on cells that must form vertically.
     */
    @SafeVarargs
    public static PropertyRule direction(EnumProperty<Direction>... properties) {
        Objects.requireNonNull(properties, "direction properties");
        if (properties.length == 0) {
            throw new IllegalArgumentException("A direction rule must govern at least one property");
        }
        return new DirectionRule(List.of(properties));
    }

    /**
     * COVARIANT rule for an axis property ({@code X}/{@code Y}/{@code Z} or a restricted subset). The
     * axis is carried by its positive unit direction through {@link Orientation#apply(Direction)} and
     * read back as an axis. A full three-value axis property supports every orientation; a restricted
     * one (e.g. a horizontal-only axis) reports pitch orientations as unsupported.
     */
    public static PropertyRule axis(EnumProperty<Direction.Axis> property) {
        return new AxisRule(Objects.requireNonNull(property, "axis property"));
    }

    /**
     * COVARIANT rule for a {@link Half} property ({@code TOP}/{@code BOTTOM}). Half co-varies with the
     * vertical axis: an orientation that keeps {@code UP} up leaves the half alone, one that sends
     * {@code UP} to {@code DOWN} swaps it, and one that tips {@code UP} onto a horizontal facing cannot
     * represent a top/bottom half — such orientations are reported unsupported.
     */
    public static PropertyRule half(EnumProperty<Half> property) {
        return new HalfRule(Objects.requireNonNull(property, "half property"));
    }

    /**
     * DERIVED rule: identity transform masking {@code maskedProperties} out of the recognition
     * compare. Vanilla recomputes these properties from neighbours via {@code updateShape} (stair
     * shape, fence/pane connection booleans, &hellip;), so recognition must not pin them: the engine
     * compares the oriented expected state against the world verbatim <em>except</em> for the union of
     * every derived rule's masked properties. Building lets vanilla resettle them; export drops them.
     */
    public static PropertyRule derived(Property<?>... maskedProperties) {
        Objects.requireNonNull(maskedProperties, "masked properties");
        if (maskedProperties.length == 0) {
            throw new IllegalArgumentException("A derived rule must mask at least one property");
        }
        return new DerivedRule(List.of(maskedProperties));
    }

    private record DirectionRule(List<EnumProperty<Direction>> properties) implements PropertyRule {

        @Override
        public BlockState apply(Orientation o, BlockState s) {
            return rotate(o, s);
        }

        @Override
        public Classification classification() {
            return Classification.COVARIANT;
        }

        @Override
        public boolean supports(Orientation o) {
            for (EnumProperty<Direction> property : properties) {
                for (Direction value : property.getPossibleValues()) {
                    if (!property.getPossibleValues().contains(o.apply(value))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private BlockState rotate(Orientation o, BlockState s) {
            for (EnumProperty<Direction> property : properties) {
                if (s.hasProperty(property)) {
                    Direction transformed = o.apply(s.getValue(property));
                    return property.getPossibleValues().contains(transformed) ? s.setValue(property, transformed) : s;
                }
            }
            return s;
        }
    }

    private record AxisRule(EnumProperty<Direction.Axis> property) implements PropertyRule {

        @Override
        public BlockState apply(Orientation o, BlockState s) {
            return transform(o, s);
        }

        @Override
        public Classification classification() {
            return Classification.COVARIANT;
        }

        @Override
        public boolean supports(Orientation o) {
            for (Direction.Axis value : property.getPossibleValues()) {
                if (!property.getPossibleValues().contains(transformAxis(o, value))) {
                    return false;
                }
            }
            return true;
        }

        private BlockState transform(Orientation o, BlockState s) {
            if (!s.hasProperty(property)) {
                return s;
            }
            Direction.Axis transformed = transformAxis(o, s.getValue(property));
            return property.getPossibleValues().contains(transformed) ? s.setValue(property, transformed) : s;
        }

        private static Direction.Axis transformAxis(Orientation o, Direction.Axis axis) {
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            return o.apply(positive).getAxis();
        }
    }

    private record HalfRule(EnumProperty<Half> property) implements PropertyRule {

        @Override
        public BlockState apply(Orientation o, BlockState s) {
            return transform(o, s);
        }

        @Override
        public Classification classification() {
            return Classification.COVARIANT;
        }

        @Override
        public boolean supports(Orientation o) {
            return o.apply(Direction.UP).getAxis() == Direction.Axis.Y;
        }

        private BlockState transform(Orientation o, BlockState s) {
            if (!s.hasProperty(property)) {
                return s;
            }
            Direction up = o.apply(Direction.UP);
            if (up == Direction.UP) {
                return s;
            }
            if (up == Direction.DOWN) {
                Half half = s.getValue(property);
                return s.setValue(property, half == Half.TOP ? Half.BOTTOM : Half.TOP);
            }
            // UP maps onto a horizontal facing: a top/bottom half is unrepresentable here.
            return s;
        }
    }

    private record DerivedRule(List<Property<?>> maskedProperties) implements PropertyRule {

        @Override
        public BlockState apply(Orientation o, BlockState s) {
            return s;
        }

        @Override
        public Classification classification() {
            return Classification.DERIVED;
        }

        /** Covariant override of {@link PropertyRule#maskedProperties()} via the record accessor. */
        @Override
        public List<Property<?>> maskedProperties() {
            return maskedProperties;
        }
    }
}
