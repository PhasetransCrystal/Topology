package net.ptcrys.topo.apiv2.machine.multiblock.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A0 transform core (L2 engine layer): a single element of the cube's symmetry group, the full
 * octahedral group {@code O_h}.
 *
 * <p>
 * An orientation is a {@code 3x3} signed-permutation integer matrix — exactly one nonzero entry
 * ({@code +1} or {@code -1}) in every row and every column. There are exactly {@code 3! * 2^3 = 48}
 * such matrices; together they <em>are</em> {@code O_h}. {@code det == +1} marks a proper rotation
 * (24 of them); {@code det == -1} marks a reflected (mirror) orientation (the other 24).
 *
 * <p>
 * The matrix acts on column vectors as {@code out = M * v} (vanilla coordinates: {@code +X} east,
 * {@code +Y} up, {@code +Z} south). Because it is an integer orthonormal matrix the image of any unit
 * axis vector is again a unit axis vector, so {@link #apply(Direction)} is total. Its inverse is its
 * transpose ({@link #inverse()}) and group multiplication is matrix multiplication ({@link #compose}).
 *
 * <p>
 * All 48 instances are precomputed once and interned, so every derived orientation
 * ({@link #compose}, {@link #inverse}, {@link #mirrored}) returns one of those singletons and
 * reference equality coincides with mathematical equality. {@link #apply(Direction)} and the mutable
 * {@link #apply(BlockPos, BlockPos, BlockPos.MutableBlockPos)} variant allocate nothing.
 *
 * <p>
 * This type is vanilla-only and pure (no Minecraft bootstrap required); it never references the
 * {@code net.ptcrys.topo.data} package, per the api &perp; data rule.
 *
 * @see Orientations
 */
public final class Orientation {

    /**
     * Row-major flattened matrix: {@code m[3 * row + col]}; {@code out[row] = sum_col m[3*row+col] * in[col]}.
     * Exactly one nonzero (&plusmn;1) per row and per column. Private and never exposed — effectively immutable.
     */
    private final int[] m;

    /** Determinant: {@code +1} for a proper rotation, {@code -1} for a reflected orientation. */
    private final int det;

    /** Packed identity key: each of the 9 entries (in {@code {-1,0,1}}) encoded in 2 bits. */
    private final int key;

    private static final Map<Integer, Orientation> BY_KEY = new HashMap<>();

    /** All 48 members of {@code O_h}. */
    static final List<Orientation> ALL;

    /** The 24 proper rotations ({@code det == +1}). */
    static final List<Orientation> ROTATIONS;

    /** The identity orientation. */
    static final Orientation IDENTITY;

    /**
     * Reflection across the plane spanned by the up ({@code Y}) and front-back ({@code Z}) axes, i.e.
     * negation of the left-right ({@code X}) axis ({@code diag(-1, +1, +1)}); the building block of
     * {@link #mirrored()}.
     */
    static final Orientation REFLECT_X;

    static {
        // Enumerate every signed permutation: 6 column permutations x 8 sign vectors = 48 = O_h.
        int[][] permutations = {
                { 0, 1, 2 }, { 0, 2, 1 }, { 1, 0, 2 }, { 1, 2, 0 }, { 2, 0, 1 }, { 2, 1, 0 }
        };
        List<Orientation> all = new ArrayList<>(48);
        for (int[] permutation : permutations) {
            for (int signMask = 0; signMask < 8; signMask++) {
                int[] matrix = new int[9];
                for (int row = 0; row < 3; row++) {
                    int sign = ((signMask >> row) & 1) == 0 ? 1 : -1;
                    matrix[3 * row + permutation[row]] = sign;
                }
                Orientation orientation = new Orientation(matrix);
                BY_KEY.put(orientation.key, orientation);
                all.add(orientation);
            }
        }
        ALL = Collections.unmodifiableList(all);

        List<Orientation> rotations = new ArrayList<>(24);
        for (Orientation orientation : all) {
            if (orientation.det > 0) {
                rotations.add(orientation);
            }
        }
        ROTATIONS = Collections.unmodifiableList(rotations);

        IDENTITY = of(new int[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 });
        REFLECT_X = of(new int[] { -1, 0, 0, 0, 1, 0, 0, 0, 1 });
    }

    private Orientation(int[] matrix) {
        this.m = matrix;
        this.det = matrix[0] * (matrix[4] * matrix[8] - matrix[5] * matrix[7]) - matrix[1] * (matrix[3] * matrix[8] - matrix[5] * matrix[6]) + matrix[2] * (matrix[3] * matrix[7] - matrix[4] * matrix[6]);
        this.key = keyOf(matrix);
    }

    /** Returns the interned group member equal to {@code matrix}; the array is not retained. */
    private static Orientation of(int[] matrix) {
        Orientation orientation = BY_KEY.get(keyOf(matrix));
        return Objects.requireNonNull(orientation, "matrix is not a member of the octahedral group");
    }

    private static int keyOf(int[] matrix) {
        int key = 0;
        for (int i = 0; i < 9; i++) {
            key |= (matrix[i] + 1) << (2 * i);
        }
        return key;
    }

    /**
     * Builds the orientation whose matrix has the given direction unit vectors as its columns, i.e. the
     * linear map sending {@code +X -> columnX}, {@code +Y -> columnY}, {@code +Z -> columnZ}. The three
     * directions must lie on mutually distinct axes (the result is then a signed-permutation matrix).
     */
    static Orientation fromColumns(Direction columnX, Direction columnY, Direction columnZ) {
        int[] matrix = new int[9];
        setColumn(matrix, 0, columnX);
        setColumn(matrix, 1, columnY);
        setColumn(matrix, 2, columnZ);
        return of(matrix);
    }

    private static void setColumn(int[] matrix, int column, Direction direction) {
        matrix[column] = direction.getStepX();
        matrix[3 + column] = direction.getStepY();
        matrix[6 + column] = direction.getStepZ();
    }

    /**
     * Transforms a facing by applying the matrix to its unit vector. Total and zero-allocation: the image
     * of a unit axis vector under a signed-permutation matrix is always exactly one of the six facings.
     */
    public Direction apply(Direction direction) {
        int x = direction.getStepX();
        int y = direction.getStepY();
        int z = direction.getStepZ();
        int nx = m[0] * x + m[1] * y + m[2] * z;
        int ny = m[3] * x + m[4] * y + m[5] * z;
        int nz = m[6] * x + m[7] * y + m[8] * z;
        return Objects.requireNonNull(Direction.getNearest(nx, ny, nz, null), "non-axis transform result");
    }

    /** The X component of {@code M · (x, y, z)} — allocation-free component transforms. */
    public int transformX(int x, int y, int z) {
        return m[0] * x + m[1] * y + m[2] * z;
    }

    /** The Y component of {@code M · (x, y, z)}. */
    public int transformY(int x, int y, int z) {
        return m[3] * x + m[4] * y + m[5] * z;
    }

    /** The Z component of {@code M · (x, y, z)}. */
    public int transformZ(int x, int y, int z) {
        return m[6] * x + m[7] * y + m[8] * z;
    }

    /**
     * Zero-allocation positional transform: writes {@code origin + M * local} into {@code out}. {@code local}
     * is a blueprint-local offset, {@code origin} the world anchor (typically the controller position).
     */
    public void apply(BlockPos local, BlockPos origin, BlockPos.MutableBlockPos out) {
        int lx = local.getX();
        int ly = local.getY();
        int lz = local.getZ();
        out.set(
                m[0] * lx + m[1] * ly + m[2] * lz + origin.getX(),
                m[3] * lx + m[4] * ly + m[5] * lz + origin.getY(),
                m[6] * lx + m[7] * ly + m[8] * lz + origin.getZ());
    }

    /** Allocating counterpart of {@link #apply(BlockPos, BlockPos, BlockPos.MutableBlockPos)}. */
    public BlockPos apply(BlockPos local, BlockPos origin) {
        int lx = local.getX();
        int ly = local.getY();
        int lz = local.getZ();
        return new BlockPos(
                m[0] * lx + m[1] * ly + m[2] * lz + origin.getX(),
                m[3] * lx + m[4] * ly + m[5] * lz + origin.getY(),
                m[6] * lx + m[7] * ly + m[8] * lz + origin.getZ());
    }

    /** {@code true} iff this is a reflected (mirror) orientation, i.e. {@code det == -1}. */
    public boolean mirror() {
        return det < 0;
    }

    /** The inverse orientation. For an orthonormal matrix the inverse is the transpose. */
    public Orientation inverse() {
        int[] transpose = new int[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                transpose[3 * row + col] = m[3 * col + row];
            }
        }
        return of(transpose);
    }

    /**
     * Group multiplication. {@code a.compose(b)} is the orientation that applies {@code b} first and then
     * {@code a}: {@code a.compose(b).apply(v) == a.apply(b.apply(v))} (matrix product {@code M_a * M_b}).
     */
    public Orientation compose(Orientation other) {
        int[] product = new int[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += m[3 * row + k] * other.m[3 * k + col];
                }
                product[3 * row + col] = sum;
            }
        }
        return of(product);
    }

    /**
     * This orientation composed with {@link #REFLECT_X} (negation of the left-right {@code X} axis): the
     * mirrored variant of this orientation. Always flips {@link #mirror()} / the determinant sign.
     */
    public Orientation mirrored() {
        return compose(REFLECT_X);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof Orientation other && other.key == this.key);
    }

    @Override
    public int hashCode() {
        return key;
    }

    @Override
    public String toString() {
        return "Orientation[" + (det > 0 ? "rotation" : "mirror") + " " + Arrays.toString(m) + "]";
    }
}
