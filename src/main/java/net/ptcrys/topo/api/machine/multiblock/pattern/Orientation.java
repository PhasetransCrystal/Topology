package net.ptcrys.topo.api.machine.multiblock.pattern;

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
 * 立方体正交变换的不可变值：24 个正旋转，加上 24 个镜像变换。
 * 使用元素为 -1/0/1 的有符号置换矩阵，变换和复合均为精确整数运算；所有合法元素预先驻留并复用。
 */
public final class Orientation {

    /** 行优先的 3×3 矩阵，每行每列恰有一个非零项，值为 ±1。 */
    private final int[] m;

    /** 行列式：正旋转为 +1，镜像为 -1。 */
    private final int det;

    /** 九个矩阵元素各编码为两位的身份键。 */
    private final int key;

    private static final Map<Integer, Orientation> BY_KEY = new HashMap<>();

    /** 全部 48 个正交变换。 */
    static final List<Orientation> ALL;

    /** 24 个行列式为 +1 的正旋转。 */
    static final List<Orientation> ROTATIONS;

    /** 反转局部 X 轴，保持局部 Y/Z 轴。 */
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

        REFLECT_X = of(new int[] { -1, 0, 0, 0, 1, 0, 0, 0, 1 });
    }

    private Orientation(int[] matrix) {
        this.m = matrix;
        this.det = matrix[0] * (matrix[4] * matrix[8] - matrix[5] * matrix[7]) - matrix[1] * (matrix[3] * matrix[8] - matrix[5] * matrix[6]) + matrix[2] * (matrix[3] * matrix[7] - matrix[4] * matrix[6]);
        this.key = keyOf(matrix);
    }

    /** 返回与输入矩阵对应的驻留变换，不保留输入数组。 */
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

    /** 以三个方向单位向量为矩阵列，分别指定局部 X/Y/Z 轴的映射；三轴必须正交。 */
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

    /** 将方向单位向量按矩阵变换，不分配新方向对象。 */
    public Direction apply(Direction direction) {
        int x = direction.getStepX();
        int y = direction.getStepY();
        int z = direction.getStepZ();
        int nx = m[0] * x + m[1] * y + m[2] * z;
        int ny = m[3] * x + m[4] * y + m[5] * z;
        int nz = m[6] * x + m[7] * y + m[8] * z;
        return Objects.requireNonNull(Direction.getNearest(nx, ny, nz, null), "non-axis transform result");
    }

    /** 无分配计算变换后的位置 X 分量。 */
    public int transformX(int x, int y, int z) {
        return m[0] * x + m[1] * y + m[2] * z;
    }

    /** 无分配计算变换后的位置 Y 分量。 */
    public int transformY(int x, int y, int z) {
        return m[3] * x + m[4] * y + m[5] * z;
    }

    /** 无分配计算变换后的位置 Z 分量。 */
    public int transformZ(int x, int y, int z) {
        return m[6] * x + m[7] * y + m[8] * z;
    }

    /** 将 origin + M×local 写入可复用位置 out；local 为局部偏移，origin 通常是控制器世界位置。 */
    public void apply(BlockPos local, BlockPos origin, BlockPos.MutableBlockPos out) {
        int lx = local.getX();
        int ly = local.getY();
        int lz = local.getZ();
        out.set(
                m[0] * lx + m[1] * ly + m[2] * lz + origin.getX(),
                m[3] * lx + m[4] * ly + m[5] * lz + origin.getY(),
                m[6] * lx + m[7] * ly + m[8] * lz + origin.getZ());
    }

    /** 返回 origin + M×local，分配一个不可变位置对象。 */
    public BlockPos apply(BlockPos local, BlockPos origin) {
        int lx = local.getX();
        int ly = local.getY();
        int lz = local.getZ();
        return new BlockPos(
                m[0] * lx + m[1] * ly + m[2] * lz + origin.getX(),
                m[3] * lx + m[4] * ly + m[5] * lz + origin.getY(),
                m[6] * lx + m[7] * ly + m[8] * lz + origin.getZ());
    }

    /** 是否为行列式 -1 的镜像变换。 */
    public boolean mirror() {
        return det < 0;
    }

    /** 逆变换；正交矩阵的逆等于转置。 */
    public Orientation inverse() {
        int[] transpose = new int[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                transpose[3 * row + col] = m[3 * col + row];
            }
        }
        return of(transpose);
    }

    /** 复合顺序：a.compose(b) 先应用 b，再应用 a。 */
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

    /** 在当前变换右侧复合局部 X 反射；连续调用两次恢复原变换。 */
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
