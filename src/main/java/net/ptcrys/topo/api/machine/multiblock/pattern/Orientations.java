package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Objects;

/**
 * 固定的立方体朝向集合，不使用注册表。rotations 返回 24 个正旋转，all 返回含镜像的 48 个变换。
 * forFacing 将规范前向 SOUTH 映射到给定方向：水平面只绕 Y 轴旋转、UP 保持不变；竖直前向绕 X 轴转动、EAST 保持不变，UP 前向时原 UP 变为 NORTH，DOWN 前向时原 UP 变为 SOUTH。
 */
public final class Orientations {

    private Orientations() {}

    /** 恒等旋转。 */
    public static final Orientation IDENTITY = Orientation.fromColumns(Direction.EAST, Direction.UP, Direction.SOUTH);

    /** 按 Direction 序号索引的预设控制器旋转。 */
    private static final Orientation[] FOR_FACING = buildForFacing();

    /** 将规范前向 SOUTH 映射到 facing 的预设正旋转，滚转约定见类注释。 */
    public static Orientation forFacing(Direction facing) {
        Objects.requireNonNull(facing, "facing");
        return FOR_FACING[facing.ordinal()];
    }

    /** 不可变的 24 种正旋转。 */
    public static List<Orientation> rotations() {
        return Orientation.ROTATIONS;
    }

    /** 不可变的 48 种正交变换，含镜像。 */
    public static List<Orientation> all() {
        return Orientation.ALL;
    }

    /** 取得当前变换的局部 X 镜像版本。 */
    public static Orientation mirrorOf(Orientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        return orientation.mirrored();
    }

    /**
     * 用于显示和投影的通用状态旋转：变换 Direction 和 Axis 类型属性，其他属性保持原值。
     * 属性值域无法表达结果时保留原值；本方法不处理半格、形状或连接等非方向编码。检测使用显式 PropertyRule，不使用此显示辅助方法。
     */
    @SuppressWarnings("unchecked")
    public static BlockState rotated(Orientation orientation, BlockState state) {
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(state, "block state");
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() == Direction.class) {
                result = rotateDirection(orientation, result, (Property<Direction>) property);
            } else if (property.getValueClass() == Direction.Axis.class) {
                result = rotateAxis(orientation, result, (Property<Direction.Axis>) property);
            }
        }
        return result;
    }

    private static BlockState rotateDirection(
                                              Orientation orientation, BlockState state, Property<Direction> property) {
        Direction transformed = orientation.apply(state.getValue(property));
        return property.getPossibleValues().contains(transformed) ? state.setValue(property, transformed) : state;
    }

    private static BlockState rotateAxis(
                                         Orientation orientation, BlockState state, Property<Direction.Axis> property) {
        Direction positive = Direction.fromAxisAndDirection(state.getValue(property), Direction.AxisDirection.POSITIVE);
        Direction.Axis transformed = orientation.apply(positive).getAxis();
        return property.getPossibleValues().contains(transformed) ? state.setValue(property, transformed) : state;
    }

    private static Orientation[] buildForFacing() {
        Orientation[] forFacing = new Orientation[6];
        forFacing[Direction.SOUTH.ordinal()] = IDENTITY;
        forFacing[Direction.WEST.ordinal()] = fromYaw(Rotation.CLOCKWISE_90);
        forFacing[Direction.NORTH.ordinal()] = fromYaw(Rotation.CLOCKWISE_180);
        forFacing[Direction.EAST.ordinal()] = fromYaw(Rotation.COUNTERCLOCKWISE_90);
        // Pitch about the east-west (X) axis, holding EAST fixed.
        forFacing[Direction.UP.ordinal()] = Orientation.fromColumns(Direction.EAST, Direction.NORTH, Direction.UP);
        forFacing[Direction.DOWN.ordinal()] = Orientation.fromColumns(Direction.EAST, Direction.SOUTH, Direction.DOWN);
        return forFacing;
    }

    /** 与原版 Y 轴 Rotation 等效的正交旋转。 */
    private static Orientation fromYaw(Rotation rotation) {
        return Orientation.fromColumns(
                rotation.rotate(Direction.EAST),
                rotation.rotate(Direction.UP),
                rotation.rotate(Direction.SOUTH));
    }
}
