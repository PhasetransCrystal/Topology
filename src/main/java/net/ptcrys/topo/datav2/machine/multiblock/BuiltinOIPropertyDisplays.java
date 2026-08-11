package net.ptcrys.topo.datav2.machine.multiblock;

import net.ptcrys.topo.apiv2.machine.multiblock.ui.PropertyDisplay;
import net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineUiLang;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.Map;
import java.util.Objects;

/**
 * Builtin {@link PropertyDisplay} handles via {@link OfficialOIPlugin#machine()}. Passes catalog
 * {@link net.ptcrys.topo.api.lang.LangKey}s; domain converts to components (code-style §3.12.3).
 */
public final class BuiltinOIPropertyDisplays {

    private static final MachineDomainRegistration MACHINE = OfficialOIPlugin.INSTANCE.machine();

    /** Both vanilla facing properties (6-way and horizontal) under one "Facing" label. */
    public static final PropertyDisplay FACING = MACHINE.propertyEnum(
            "facing",
            BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING,
            Map.of(
                    Direction.NORTH, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING_NORTH,
                    Direction.SOUTH, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING_SOUTH,
                    Direction.EAST, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING_EAST,
                    Direction.WEST, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING_WEST,
                    Direction.UP, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING_UP,
                    Direction.DOWN, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_FACING_DOWN),
            BlockStateProperties.FACING,
            BlockStateProperties.HORIZONTAL_FACING);

    public static final PropertyDisplay HALF = MACHINE.propertyEnum(
            "half",
            BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_HALF,
            Map.of(
                    Half.TOP, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_HALF_TOP,
                    Half.BOTTOM, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_HALF_BOTTOM),
            BlockStateProperties.HALF);

    public static final PropertyDisplay STAIR_SHAPE = MACHINE.propertyEnum(
            "stair_shape",
            BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE,
            Map.of(
                    StairsShape.STRAIGHT, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_STRAIGHT,
                    StairsShape.INNER_LEFT, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_INNER_LEFT,
                    StairsShape.INNER_RIGHT, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_INNER_RIGHT,
                    StairsShape.OUTER_LEFT, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_OUTER_LEFT,
                    StairsShape.OUTER_RIGHT, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_OUTER_RIGHT),
            BlockStateProperties.STAIRS_SHAPE);

    public static final PropertyDisplay AXIS = MACHINE.propertyEnum(
            "axis",
            BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_AXIS,
            Map.of(
                    Direction.Axis.X, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_AXIS_X,
                    Direction.Axis.Y, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_AXIS_Y,
                    Direction.Axis.Z, BuiltinOIMachineUiLang.UI_MULTIBLOCK_STATE_AXIS_Z),
            BlockStateProperties.AXIS);

    private BuiltinOIPropertyDisplays() {}

    public static void init() {
        Objects.requireNonNull(AXIS);
    }
}
