package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * 一组具体重复次数下的蓝图局部布局。负责控制器锚点、段推移、副本位置及位置反查，
 * 不包含世界状态和旋转，也不展开保存每个副本的单元格。
 * 声明展开、检测、预览、占地和快照均通过此对象取得规范坐标。
 */
public final class BlueprintLayout {

    private final Blueprint blueprint;
    private final RepeatResolution repeats;
    private final int[] offsetsX;
    private final int[] offsetsY;
    private final int[] offsetsZ;
    private final int cellCount;

    BlueprintLayout(Blueprint blueprint, RepeatResolution repeats) {
        this.blueprint = blueprint;
        this.repeats = Objects.requireNonNull(repeats, "repeat resolution");
        blueprint.validateResolution(repeats);
        List<Blueprint.Brick> bricks = blueprint.bricksInternal();
        offsetsX = new int[bricks.size()];
        offsetsY = new int[bricks.size()];
        offsetsZ = new int[bricks.size()];
        int[] counts = repeats.countsCopy();
        int[] anchor = blueprint.controllerDecl();
        int[] controllerShift = blueprint.shiftOf(bricks.get(blueprint.controllerBrick()), counts);
        int total = 0;
        for (int b = 0; b < bricks.size(); b++) {
            Blueprint.Brick brick = bricks.get(b);
            int[] shift = blueprint.shiftOf(brick, counts);
            offsetsX[b] = Math.subtractExact(shift[0], Math.addExact(anchor[0], controllerShift[0]));
            offsetsY[b] = Math.subtractExact(shift[1], Math.addExact(anchor[1], controllerShift[1]));
            offsetsZ[b] = Math.subtractExact(shift[2], Math.addExact(anchor[2], controllerShift[2]));
            total = Math.addExact(total, Math.multiplyExact(brick.cellCount(), copies(b)));
        }
        cellCount = total;
    }

    /** 本布局采用的重复段副本数，按声明顺序索引。 */
    public RepeatResolution repeats() {
        return repeats;
    }

    /** 本布局包含的单元格数量，不包含声明中的空格。 */
    public int cellCount() {
        return cellCount;
    }

    /**
     * 按声明、副本和组内顺序访问局部单元格；访问者返回 false 可提前结束。
     * index 使用最大副本数下的稳定编号，缩短重复段不会重排其他单元格的编号。
     */
    public boolean forEachCell(CellVisitor visitor) {
        Objects.requireNonNull(visitor, "cell visitor");
        for (int b = 0; b < offsetsX.length; b++) {
            for (int copy = 0; copy < copies(b); copy++) {
                if (!forEachCopy(b, copy, visitor)) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean forEachCopy(int b, int copy, CellVisitor visitor) {
        Blueprint.Brick brick = blueprint.bricksInternal().get(b);
        int dx = offsetsX[b];
        int dy = offsetsY[b];
        int dz = offsetsZ[b];
        if (brick.section() >= 0) {
            Blueprint.RepeatSection section = blueprint.section(brick.section());
            int delta = Math.multiplyExact(copy, section.stride());
            switch (section.axis()) {
                case X -> dx = Math.addExact(dx, delta);
                case Y -> dy = Math.addExact(dy, delta);
                case Z -> dz = Math.addExact(dz, delta);
            }
        }
        for (int k = 0; k < brick.cellCount(); k++) {
            if (!visitor.visit(brick.indexBase() + copy * brick.cellCount() + k, brick.definitions()[k],
                    Math.addExact(brick.xs()[k], dx), Math.addExact(brick.ys()[k], dy), Math.addExact(brick.zs()[k], dz))) {
                return false;
            }
        }
        return true;
    }

    Blueprint blueprint() {
        return blueprint;
    }

    int copies(int brickIndex) {
        int section = blueprint.bricksInternal().get(brickIndex).section();
        return section < 0 ? 1 : repeats.count(section);
    }

    /** 局部位置对应的稳定单元格编号；不属于本布局时返回 -1。 */
    public int cellIndexAt(int x, int y, int z) {
        List<Blueprint.Brick> bricks = blueprint.bricksInternal();
        for (int b = 0; b < bricks.size(); b++) {
            Blueprint.Brick brick = bricks.get(b);
            long dx = (long) x - offsetsX[b];
            long dy = (long) y - offsetsY[b];
            long dz = (long) z - offsetsZ[b];
            int copy = 0;
            if (brick.section() >= 0) {
                Blueprint.RepeatSection section = blueprint.section(brick.section());
                long coordinate = switch (section.axis()) {
                    case X -> dx;
                    case Y -> dy;
                    case Z -> dz;
                };
                long relative = coordinate - section.bandStart();
                if (relative < 0 || relative >= (long) copies(b) * section.stride()) {
                    continue;
                }
                copy = (int) (relative / section.stride());
                long delta = (long) copy * section.stride();
                switch (section.axis()) {
                    case X -> dx -= delta;
                    case Y -> dy -= delta;
                    case Z -> dz -= delta;
                }
            }
            long packed = BlockPos.asLong((int) dx, (int) dy, (int) dz);
            if (BlockPos.getX(packed) != dx || BlockPos.getY(packed) != dy || BlockPos.getZ(packed) != dz) {
                continue;
            }
            long ref = blueprint.declaredRefAt(packed);
            if (ref >= 0 && Blueprint.refBrick(ref) == b) {
                return brick.indexBase() + copy * brick.cellCount() + Blueprint.refMember(ref);
            }
        }
        return -1;
    }

    /** 不持有可变位置对象的局部单元格访问接口。 */
    @FunctionalInterface
    public interface CellVisitor {

        boolean visit(int index, int definition, int x, int y, int z);
    }
}
