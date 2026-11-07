package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Objects;

/** 内置属性规则工厂。规则持有显式传入的属性，不依赖特定内容注册；协变规则通过 Orientation 旋转，派生规则声明比较屏蔽项。 */
public final class PropertyRules {

    private PropertyRules() {}

    /**
     * 方向协变规则：旋转状态中第一个存在的给定方向属性。
     * supports 要求每个受管属性的整个合法值域在该旋转下封闭；包含四向水平属性的规则会拒绝侧倾，即使当前状态使用六向属性。
     */
    @SafeVarargs
    public static PropertyRule direction(EnumProperty<Direction>... properties) {
        Objects.requireNonNull(properties, "direction properties");
        if (properties.length == 0) {
            throw new IllegalArgumentException("A direction rule must govern at least one property");
        }
        return new DirectionRule(List.of(properties));
    }

    /** 轴协变规则：将轴的正向单位方向旋转后读回轴。完整 XYZ 属性支持所有朝向，受限值域会拒绝无法表达的朝向。 */
    public static PropertyRule axis(EnumProperty<Direction.Axis> property) {
        return new AxisRule(Objects.requireNonNull(property, "axis property"));
    }

    /** 上下半格协变规则：UP 保持时不变，UP 翻到 DOWN 时交换 TOP/BOTTOM；侧倾到水平轴时无法表达，拒绝该朝向。 */
    public static PropertyRule half(EnumProperty<Half> property) {
        return new HalfRule(Objects.requireNonNull(property, "half property"));
    }

    /** 派生属性规则：不变换状态，在检测比较中屏蔽指定属性。适用于邻居更新决定的形状、连接等属性，避免固定尚未稳定的派生值。 */
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

        @Override
        public List<Property<?>> governedProperties() {
            return List.copyOf(properties);
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

        @Override
        public List<Property<?>> governedProperties() {
            return List.of(property);
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

        @Override
        public List<Property<?>> governedProperties() {
            return List.of(property);
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

        /** 通过记录访问器返回声明的屏蔽属性。 */
        @Override
        public List<Property<?>> maskedProperties() {
            return maskedProperties;
        }
    }
}
