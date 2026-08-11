package net.ptcrys.topo.data.machine.multiblock;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.multiblock.ui.PropertyDisplay;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.Map;
import java.util.Objects;

/**
 * Builtin {@link PropertyDisplay} handles via {@link OfficialTopoPlugin#machine()}. Passes catalog
 * {@link LangKey}s; domain converts to components (code-style §3.12.3).
 */
public final class BuiltinTopoPropertyDisplays {

    private static final MachineDomainRegistration MACHINE = OfficialTopoPlugin.INSTANCE.machine();

    /** Both vanilla facing properties (6-way and horizontal) under one "Facing" label. */
    public static final PropertyDisplay FACING = MACHINE.propertyEnum(
            "facing",
            BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING,
            Map.of(
                    Direction.NORTH, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING_NORTH,
                    Direction.SOUTH, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING_SOUTH,
                    Direction.EAST, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING_EAST,
                    Direction.WEST, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING_WEST,
                    Direction.UP, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING_UP,
                    Direction.DOWN, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_FACING_DOWN),
            BlockStateProperties.FACING,
            BlockStateProperties.HORIZONTAL_FACING);

    public static final PropertyDisplay HALF = MACHINE.propertyEnum(
            "half",
            BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_HALF,
            Map.of(
                    Half.TOP, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_HALF_TOP,
                    Half.BOTTOM, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_HALF_BOTTOM),
            BlockStateProperties.HALF);

    public static final PropertyDisplay STAIR_SHAPE = MACHINE.propertyEnum(
            "stair_shape",
            BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE,
            Map.of(
                    StairsShape.STRAIGHT, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_STRAIGHT,
                    StairsShape.INNER_LEFT, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_INNER_LEFT,
                    StairsShape.INNER_RIGHT, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_INNER_RIGHT,
                    StairsShape.OUTER_LEFT, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_OUTER_LEFT,
                    StairsShape.OUTER_RIGHT, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_STAIR_SHAPE_OUTER_RIGHT),
            BlockStateProperties.STAIRS_SHAPE);

    public static final PropertyDisplay AXIS = MACHINE.propertyEnum(
            "axis",
            BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_AXIS,
            Map.of(
                    Direction.Axis.X, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_AXIS_X,
                    Direction.Axis.Y, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_AXIS_Y,
                    Direction.Axis.Z, BuiltinTopoMachineUiLang.UI_MULTIBLOCK_STATE_AXIS_Z),
            BlockStateProperties.AXIS);

    private BuiltinTopoPropertyDisplays() {}

    public static void init() {
        Objects.requireNonNull(AXIS);
    }
}
