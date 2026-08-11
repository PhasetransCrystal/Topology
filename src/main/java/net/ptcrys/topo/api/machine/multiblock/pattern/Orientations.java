package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Objects;

/**
 * Value holder for {@link Orientation} (no registry — an intentional exception to the
 * {@code Xxxs} accessor convention, since the octahedral group is a fixed, fully precomputed set).
 *
 * <p>
 * Exposes the interned group: {@link #all()} (48), {@link #rotations()} (24 proper rotations),
 * {@link #IDENTITY}, the canonical {@link #forFacing(Direction)} mapping and the {@link #mirrorOf}
 * helper.
 *
 * <h2>Roll convention of {@link #forFacing(Direction)}</h2>
 *
 * <p>
 * The canonical controller front is {@code SOUTH} ({@code +Z}). {@code forFacing(f)} returns the
 * unique proper rotation that maps {@code SOUTH} onto {@code f} with this roll:
 *
 * <ul>
 * <li><b>Horizontal facings</b> ({@code NORTH/EAST/SOUTH/WEST}): a pure yaw rotation about the up
 * ({@code +Y}) axis ({@code UP} stays {@code UP}). It is built directly from the vanilla
 * {@link Rotation} that maps {@code SOUTH} onto the facing, so for any direction
 * {@code forFacing(f).apply(d)} equals {@code rotation.rotate(d)} — it agrees with vanilla
 * {@link Rotation} about {@code Y}. ({@code SOUTH->NONE}, {@code WEST->CLOCKWISE_90},
 * {@code NORTH->CLOCKWISE_180}, {@code EAST->COUNTERCLOCKWISE_90}.)</li>
 * <li><b>Vertical facings</b> ({@code UP/DOWN}): a pitch rotation about the east-west ({@code +X})
 * axis, holding {@code EAST} fixed. For {@code UP} the original top {@code +Y} rotates to
 * {@code NORTH}; for {@code DOWN} it rotates to {@code SOUTH}.</li>
 * </ul>
 *
 * @see Orientation
 */
public final class Orientations {

    private Orientations() {}

    /** The identity orientation. */
    public static final Orientation IDENTITY = Orientation.IDENTITY;

    /** Canonical orientation per facing, indexed by {@link Direction#ordinal()}. */
    private static final Orientation[] FOR_FACING = buildForFacing();

    /**
     * The proper rotation mapping the canonical front {@code SOUTH} onto {@code facing} with the roll
     * documented on this class.
     */
    public static Orientation forFacing(Direction facing) {
        Objects.requireNonNull(facing, "facing");
        return FOR_FACING[facing.ordinal()];
    }

    /** The 24 proper rotations ({@code det == +1}); immutable. */
    public static List<Orientation> rotations() {
        return Orientation.ROTATIONS;
    }

    /** All 48 members of the octahedral group {@code O_h}, rotations and mirrors; immutable. */
    public static List<Orientation> all() {
        return Orientation.ALL;
    }

    /** The mirrored variant of {@code orientation} (see {@link Orientation#mirrored()}). */
    public static Orientation mirrorOf(Orientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        return orientation.mirrored();
    }

    /**
     * Rigid-body rotation of a block state: every property whose value space is a set of
     * {@link Direction}s or {@link Direction.Axis} axes is sent through
     * {@link Orientation#apply(Direction)}, the rest of the state is untouched. Generic by value
     * type — no property constant is named, so any vanilla or modded spatial property co-rotates.
     *
     * <p>
     * Partial like the covariant {@code PropertyRules}: a property whose value space does not
     * contain the image (a horizontal-only facing under a pitch orientation) keeps its original
     * value. Properties encoding geometry without a direction/axis value type (stair shape, half,
     * connection booleans) are not transformed; under pure yaw orientations they are
     * frame-invariant, which covers every orientation a horizontal controller front can produce.
     *
     * <p>
     * This is a display/projection helper (e.g. folding an as-built structure back into the
     * blueprint's canonical frame with {@link Orientation#inverse()}); recognition compares
     * through {@link PropertyRule}s instead.
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
        forFacing[Direction.SOUTH.ordinal()] = Orientation.IDENTITY;
        forFacing[Direction.WEST.ordinal()] = fromYaw(Rotation.CLOCKWISE_90);
        forFacing[Direction.NORTH.ordinal()] = fromYaw(Rotation.CLOCKWISE_180);
        forFacing[Direction.EAST.ordinal()] = fromYaw(Rotation.COUNTERCLOCKWISE_90);
        // Pitch about the east-west (X) axis, holding EAST fixed.
        forFacing[Direction.UP.ordinal()] = Orientation.fromColumns(Direction.EAST, Direction.NORTH, Direction.UP);
        forFacing[Direction.DOWN.ordinal()] = Orientation.fromColumns(Direction.EAST, Direction.SOUTH, Direction.DOWN);
        return forFacing;
    }

    /** The orientation whose linear action equals the vanilla yaw {@link Rotation} (about {@code Y}). */
    private static Orientation fromYaw(Rotation rotation) {
        return Orientation.fromColumns(
                rotation.rotate(Direction.EAST),
                rotation.rotate(Direction.UP),
                rotation.rotate(Direction.SOUTH));
    }
}
