package net.ptcrys.topo.datav2.machine.multiblock;

import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.PropertyRule;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.PropertyRules;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.util.Collection;
import java.util.List;

/**
 * Builtin {@link PropertyRule} strong handles. Rules are <b>plain values</b> (direct singletons by
 * the charter's declaration-institution chain): nobody addresses them by id or enumerates "all
 * rules" — blueprint declarations reference these fields directly, so there is no registry, no
 * freeze window, and no activation ordering. Each handle is built from the parameterized
 * {@link PropertyRules} factories (or, for block-family-specific behaviour like
 * {@link #STAIR_SHAPE}, implemented right here — concrete rule content stays in data, never in the
 * api engine).
 *
 * <p>
 * The recognition engine consumes these rules: {@code StructureEngine.orientExpected} folds a
 * cell's <b>COVARIANT</b> rules ({@link #FACING}, {@link #HALF}, {@link #AXIS}) forward over its
 * canonical expected state for the actual orientation before comparing it against the world, and the
 * recognition entry refuses orientations a rule cannot represent
 * ({@link PropertyRule#supports(Orientation)} pre-check per blueprint &times; orientation).
 * <b>DERIVED</b> rules ({@link #STAIR_SHAPE}, {@link #CONNECTION}) are identity transforms whose
 * masked properties are excluded from the verbatim state compare — vanilla recomputes them from
 * neighbours via {@code updateShape}, so recognition masks them. A rule may additionally publish
 * {@link PropertyRule#alternateEncodings(BlockState)} for redundantly-encoded geometry; the engine
 * accepts the oriented expected state or any published alternate.
 */
public final class BuiltinOIPropertyRules {

    /**
     * Facing rule governing both the 6-way {@code FACING} and the 4-way {@code HORIZONTAL_FACING}.
     * Because {@code supports} is the conjunction over both governed properties, this handle refuses
     * pitch orientations outright (the 4-way property cannot represent them); a cell that must form
     * vertically should carry {@code PropertyRules.direction(BlockStateProperties.FACING)} instead.
     */
    public static final PropertyRule FACING = PropertyRules.direction(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING);

    /** Top/bottom half rule; supports yaw orientations and the up-down flip, refuses pitch. */
    public static final PropertyRule HALF = PropertyRules.half(BlockStateProperties.HALF);

    /** Full three-value axis rule; a signed-permutation orientation always maps an axis onto an axis. */
    public static final PropertyRule AXIS = PropertyRules.axis(BlockStateProperties.AXIS);

    /**
     * Stairs rule: masks the neighbour-recomputed {@code STAIRS_SHAPE} out of the recognition
     * compare like a plain derived rule, and additionally publishes vanilla's redundant corner
     * spelling via {@link PropertyRule#alternateEncodings} — the same physical corner is legal both
     * as {@code INNER_LEFT(f)} and as {@code INNER_RIGHT(f.getCounterClockWise())} (outer corners
     * alike), whichever the player's look direction produced at placement, so visually identical
     * placements match either way. Alternates derive from the pinned expected state only; the world
     * block's (possibly unsettled) shape is never consulted. The dual-encoding arithmetic presumes
     * an upright, unmirrored frame, so {@code supports} admits the four yaw orientations only;
     * mirrored or tipped passes are refused up front ({@code ORIENTATION_UNSUPPORTED}) instead of
     * comparing subtly wrong.
     */
    public static final PropertyRule STAIR_SHAPE = new StairShapeRule();

    /**
     * Masks the six neighbour-recomputed connection booleans (fences, panes, walls' up flag, &hellip;)
     * out of the recognition compare.
     */
    public static final PropertyRule CONNECTION = PropertyRules.derived(
            BlockStateProperties.NORTH,
            BlockStateProperties.EAST,
            BlockStateProperties.SOUTH,
            BlockStateProperties.WEST,
            BlockStateProperties.UP,
            BlockStateProperties.DOWN);

    private BuiltinOIPropertyRules() {}

    /** See {@link #STAIR_SHAPE}; the concrete stairs knowledge lives here, not in the api engine. */
    private static final class StairShapeRule implements PropertyRule {

        @Override
        public BlockState apply(Orientation o, BlockState s) {
            return s;
        }

        @Override
        public Classification classification() {
            return Classification.DERIVED;
        }

        @Override
        public boolean supports(Orientation o) {
            return !o.mirror() && o.apply(Direction.UP) == Direction.UP;
        }

        @Override
        public Collection<Property<?>> maskedProperties() {
            return List.of(BlockStateProperties.STAIRS_SHAPE);
        }

        @Override
        public Collection<BlockState> alternateEncodings(BlockState expected) {
            if (!expected.hasProperty(BlockStateProperties.STAIRS_SHAPE) || !expected.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return List.of();
            }
            Direction facing = expected.getValue(BlockStateProperties.HORIZONTAL_FACING);
            // _LEFT(f) == _RIGHT(f.getCounterClockWise()): one physical corner, two spellings.
            return switch (expected.getValue(BlockStateProperties.STAIRS_SHAPE)) {
                case INNER_LEFT -> List.of(expected
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getCounterClockWise())
                        .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_RIGHT));
                case INNER_RIGHT -> List.of(expected
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise())
                        .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT));
                case OUTER_LEFT -> List.of(expected
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getCounterClockWise())
                        .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.OUTER_RIGHT));
                case OUTER_RIGHT -> List.of(expected
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise())
                        .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.OUTER_LEFT));
                default -> List.of();
            };
        }
    }
}
