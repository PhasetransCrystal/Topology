package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * 指定控制器朝向下的蓝图编译结果。局部布局由 {@link BlueprintLayout} 统一解析，
 * 本类只负责旋转、状态规则编译、稳定编号和世界坐标适配。
 * 快照采集顺序按需生成，实时检测与占地监听不会提前分配最大副本的采集数组。
 */
public final class CompiledBlueprint {

    static final int MASK_EXPANSION_LIMIT = 64;
    private final Blueprint blueprint;
    private final Orientation machineOrientation;
    private final Orientation orientation;
    private final Orientation inverse;
    private final CompiledDefinition[] definitions;
    private final BlueprintLayout maxLayout;
    private final int[][] anyLayoutBoxes;
    private final PartRoleOrdinals countedRoles;
    private volatile @Nullable CapturePlan capturePlan;

    private CompiledBlueprint(Blueprint blueprint, Orientation machineOrientation, Orientation orientation, CompiledDefinition[] definitions) {
        this.blueprint = blueprint;
        this.machineOrientation = machineOrientation;
        this.orientation = orientation;
        this.inverse = orientation.inverse();
        this.definitions = definitions;
        this.maxLayout = blueprint.layout(blueprint.maxRepeats());
        this.anyLayoutBoxes = buildAnyLayoutBoxes(blueprint);
        this.countedRoles = PartRoleOrdinals.of(blueprint);
    }

    static CompiledBlueprint compile(Blueprint blueprint, Orientation machineOrientation, Orientation orientation) {
        CompiledDefinition[] definitions = new CompiledDefinition[blueprint.symbolDefinitions().size()];
        for (int i = 0; i < definitions.length; i++) {
            Cell prototype = blueprint.cellOf(i, BlockPos.ZERO);
            BlockState oriented = StructureEngine.orientExpected(prototype, orientation);
            definitions[i] = new CompiledDefinition(prototype, oriented, oriented == null ? null : acceptableStates(prototype, oriented));
        }
        return new CompiledBlueprint(blueprint, machineOrientation, orientation, definitions);
    }

    /** 请求编译时的控制器朝向，尚未复合蓝图 frame。 */
    public Orientation orientation() {
        return machineOrientation;
    }

    /** 实际用于位置和状态的旋转：控制器朝向 ∘ frame。 */
    public Orientation effectiveOrientation() {
        return orientation;
    }

    /** 按最大布局中的稳定编号取得匹配定义，采用分段二分查找。 */
    public CompiledDefinition definitionAt(int cellIndex) {
        Objects.checkIndex(cellIndex, cellCapacity());
        List<Blueprint.Brick> bricks = blueprint.bricksInternal();
        int low = 0, high = bricks.size();
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (bricks.get(middle).indexBase() <= cellIndex) low = middle;
            else high = middle;
        }
        Blueprint.Brick brick = bricks.get(low);
        return definitions[brick.definitions()[(cellIndex - brick.indexBase()) % brick.cellCount()]];
    }

    /** 最大布局的稳定编号容量。 */
    public int cellCapacity() {
        return blueprint.maxCellCount();
    }

    /** 快照采集条目数。采集计划仅适用于 supportsSnapshot() 为真的蓝图。 */
    public int captureSize() {
        return captures().offsets().length;
    }

    /** 按区块局部性排列的控制器相对世界偏移，使用 BlockPos 编码。 */
    public long captureOffsetAt(int order) {
        return captures().offsets()[order];
    }

    /** 与采集条目对应的稳定单元格编号。 */
    public int captureCellIndexAt(int order) {
        return captures().indices()[order];
    }

    public PartRoleOrdinals countedRoles() {
        return countedRoles;
    }

    /** 查询最大布局中的稳定编号，不属于布局时返回 -1。 */
    public int cellIndexOfWorld(BlockPos worldPos, BlockPos controllerPos) {
        return cellIndexOf(worldPos, controllerPos, maxLayout);
    }

    /** 查询指定重复次数布局中的稳定编号，不属于布局时返回 -1。 */
    public int cellIndexOfWorld(BlockPos worldPos, BlockPos controllerPos, RepeatResolution repeats) {
        return cellIndexOf(worldPos, controllerPos, blueprint.layout(repeats));
    }

    /** 复用已解析的布局进行反查；布局必须来自本编译结果所属的蓝图。 */
    public int cellIndexOfWorld(BlockPos worldPos, BlockPos controllerPos, BlueprintLayout layout) {
        Objects.requireNonNull(layout, "blueprint layout");
        if (layout.blueprint() != blueprint) throw new IllegalArgumentException("Layout belongs to another blueprint");
        return cellIndexOf(worldPos, controllerPos, layout);
    }

    public boolean containsWorld(BlockPos worldPos, BlockPos controllerPos, RepeatResolution repeats) {
        return cellIndexOfWorld(worldPos, controllerPos, repeats) >= 0;
    }

    private int cellIndexOf(BlockPos worldPos, BlockPos controllerPos, BlueprintLayout layout) {
        int dx = worldPos.getX() - controllerPos.getX();
        int dy = worldPos.getY() - controllerPos.getY();
        int dz = worldPos.getZ() - controllerPos.getZ();
        return layout.cellIndexAt(inverse.transformX(dx, dy, dz), inverse.transformY(dx, dy, dz), inverse.transformZ(dx, dy, dz));
    }

    /** 各种重复计数可能占据位置的保守并集，用于变更路由。 */
    public boolean containsWorldAnyLayout(BlockPos worldPos, BlockPos controllerPos) {
        int dx = worldPos.getX() - controllerPos.getX();
        int dy = worldPos.getY() - controllerPos.getY();
        int dz = worldPos.getZ() - controllerPos.getZ();
        int x = inverse.transformX(dx, dy, dz), y = inverse.transformY(dx, dy, dz), z = inverse.transformZ(dx, dy, dz);
        for (int[] box : anyLayoutBoxes) {
            if (x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5]) return true;
        }
        return false;
    }

    /** 覆盖所有重复计数的世界轴对齐边界，数组顺序为 minX/minY/minZ/maxX/maxY/maxZ。 */
    public int[] worldBounds(BlockPos controllerPos) {
        int[] bounds = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (int[] box : anyLayoutBoxes) {
            int x1 = orientation.transformX(box[0], box[1], box[2]), y1 = orientation.transformY(box[0], box[1], box[2]), z1 = orientation.transformZ(box[0], box[1], box[2]);
            int x2 = orientation.transformX(box[3], box[4], box[5]), y2 = orientation.transformY(box[3], box[4], box[5]), z2 = orientation.transformZ(box[3], box[4], box[5]);
            bounds[0] = Math.min(bounds[0], Math.min(x1, x2));
            bounds[3] = Math.max(bounds[3], Math.max(x1, x2));
            bounds[1] = Math.min(bounds[1], Math.min(y1, y2));
            bounds[4] = Math.max(bounds[4], Math.max(y1, y2));
            bounds[2] = Math.min(bounds[2], Math.min(z1, z2));
            bounds[5] = Math.max(bounds[5], Math.max(z1, z2));
        }
        for (int axis = 0; axis < 3; axis++) {
            int origin = axis == 0 ? controllerPos.getX() : axis == 1 ? controllerPos.getY() : controllerPos.getZ();
            bounds[axis] += origin;
            bounds[axis + 3] += origin;
        }
        return bounds;
    }

    /** 遍历具体布局的世界占地，不生成逐格集合。 */
    public void forEachWorldPos(BlockPos controllerPos, RepeatResolution repeats, LongConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        blueprint.layout(repeats).forEachCell((index, definition, x, y, z) -> {
            consumer.accept(BlockPos.asLong(controllerPos.getX() + orientation.transformX(x, y, z), controllerPos.getY() + orientation.transformY(x, y, z), controllerPos.getZ() + orientation.transformZ(x, y, z)));
            return true;
        });
    }

    Blueprint blueprint() {
        return blueprint;
    }

    CompiledDefinition definition(int ordinal) {
        return definitions[ordinal];
    }

    private CapturePlan captures() {
        if (!blueprint.supportsSnapshot()) throw new IllegalStateException("This blueprint requires a live structure view");
        CapturePlan cached = capturePlan;
        if (cached == null) {
            synchronized (this) {
                cached = capturePlan;
                if (cached == null) capturePlan = cached = captureOrder();
            }
        }
        return cached;
    }

    private CapturePlan captureOrder() {
        int total = cellCapacity();
        long[] offsets = new long[total];
        maxLayout.forEachCell((index, definition, x, y, z) -> {
            int ox = orientation.transformX(x, y, z), oy = orientation.transformY(x, y, z), oz = orientation.transformZ(x, y, z);
            long packed = BlockPos.asLong(ox, oy, oz);
            if (BlockPos.getX(packed) != ox || BlockPos.getY(packed) != oy || BlockPos.getZ(packed) != oz) {
                throw new IllegalArgumentException("Structure offset exceeds BlockPos encoding range");
            }
            offsets[index] = packed;
            return true;
        });
        int[] order = new int[total];
        for (int i = 0; i < total; i++) order[i] = i;
        sortByChunk(offsets, order);
        long[] sorted = new long[total];
        for (int i = 0; i < total; i++) sorted[i] = offsets[order[i]];
        return new CapturePlan(sorted, order);
    }

    private record CapturePlan(long[] offsets, int[] indices) {}

    private static int[][] buildAnyLayoutBoxes(Blueprint blueprint) {
        List<Blueprint.Brick> bricks = blueprint.bricksInternal();
        int[] anchor = blueprint.controllerDecl();
        Blueprint.Brick controller = bricks.get(blueprint.controllerBrick());
        int[] controllerMin = blueprint.minShiftOf(controller), controllerMax = blueprint.maxShiftOf(controller);
        int[][] boxes = new int[bricks.size()][];
        for (int b = 0; b < bricks.size(); b++) {
            Blueprint.Brick brick = bricks.get(b);
            int[] minShift = blueprint.minShiftOf(brick), maxShift = blueprint.maxShiftOf(brick);
            int[] box = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
            for (int k = 0; k < brick.cellCount(); k++) {
                for (int axis = 0; axis < 3; axis++) {
                    int declaration = axis == 0 ? brick.xs()[k] : axis == 1 ? brick.ys()[k] : brick.zs()[k];
                    int low = declaration + minShift[axis] - anchor[axis] - controllerMax[axis];
                    int high = declaration + maxShift[axis] - anchor[axis] - controllerMin[axis];
                    if (brick.section() >= 0 && blueprint.section(brick.section()).axis().ordinal() == axis) {
                        Blueprint.RepeatSection section = blueprint.section(brick.section());
                        high += (section.maxCount() - 1) * section.stride();
                    }
                    box[axis] = Math.min(box[axis], low);
                    box[axis + 3] = Math.max(box[axis + 3], high);
                }
            }
            boxes[b] = box;
        }
        return boxes;
    }

    /** 按区块局部性对编号数组原地堆排序，使用基本类型避免装箱。 */
    private static void sortByChunk(long[] keys, int[] order) {
        int size = order.length;
        for (int i = size / 2 - 1; i >= 0; i--) {
            siftDown(keys, order, i, size - 1);
        }
        for (int end = size - 1; end > 0; end--) {
            int swap = order[0];
            order[0] = order[end];
            order[end] = swap;
            siftDown(keys, order, 0, end - 1);
        }
    }

    private static void siftDown(long[] keys, int[] order, int root, int end) {
        while (true) {
            int child = root * 2 + 1;
            if (child > end) {
                return;
            }
            if (child + 1 <= end && compareOffsets(keys[order[child]], keys[order[child + 1]]) < 0) {
                child++;
            }
            if (compareOffsets(keys[order[root]], keys[order[child]]) >= 0) {
                return;
            }
            int swap = order[root];
            order[root] = order[child];
            order[child] = swap;
            root = child;
        }
    }

    private static int compareOffsets(long a, long b) {
        int ax = BlockPos.getX(a);
        int ay = BlockPos.getY(a);
        int az = BlockPos.getZ(a);
        int bx = BlockPos.getX(b);
        int by = BlockPos.getY(b);
        int bz = BlockPos.getZ(b);
        int result = Integer.compare(ax >> 4, bx >> 4);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(az >> 4, bz >> 4);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(ay >> 4, by >> 4);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(ay, by);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(az, bz);
        return result != 0 ? result : Integer.compare(ax, bx);
    }

    // ------------------------------------------------------------------------------------------
    // Acceptable-state expansion.
    // ------------------------------------------------------------------------------------------

    /** 合并已旋转状态、替代编码及屏蔽属性的值组合；超过展开上限时返回 null，检测改用逐项比较。 */
    static @Nullable BlockState[] acceptableStates(Cell cell, BlockState oriented) {
        Set<BlockState> seeds = new LinkedHashSet<>();
        seeds.add(oriented);
        for (PropertyRule rule : cell.rules()) {
            seeds.addAll(rule.alternateEncodings(oriented));
        }
        Set<BlockState> accepted = new LinkedHashSet<>();
        for (BlockState seed : seeds) {
            if (!expandMasked(seed, cell.maskedProperties(), accepted)) {
                return null;
            }
        }
        return accepted.toArray(new BlockState[0]);
    }

    /** 展开屏蔽属性的所有值组合；超过上限时返回 false。 */
    private static boolean expandMasked(BlockState seed, Set<Property<?>> masked, Set<BlockState> out) {
        List<BlockState> worklist = new ArrayList<>();
        worklist.add(seed);
        for (Property<?> property : masked) {
            if (!seed.hasProperty(property)) {
                continue;
            }
            List<BlockState> next = new ArrayList<>(worklist.size() * 2);
            for (BlockState state : worklist) {
                expandProperty(state, property, next);
                if (next.size() > MASK_EXPANSION_LIMIT) {
                    return false;
                }
            }
            worklist = next;
        }
        out.addAll(worklist);
        return out.size() <= MASK_EXPANSION_LIMIT;
    }

    private static <T extends Comparable<T>> void expandProperty(
                                                                 BlockState state, Property<T> property, List<BlockState> out) {
        for (T value : property.getPossibleValues()) {
            out.add(state.setValue(property, value));
        }
    }

    /** 通过驻留状态的引用判断实际状态是否被接受。 */
    static boolean acceptedState(BlockState[] acceptable, BlockState actual) {
        for (BlockState state : acceptable) {
            if (state == actual) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Value types.
    // ------------------------------------------------------------------------------------------

    /** 同一符号共享的编译匹配规则；内部状态数组不向调用方暴露。 */
    public static final class CompiledDefinition {

        private final Cell prototype;
        private final @Nullable BlockState orientedExpected;
        private final BlockState @Nullable [] acceptable;

        private CompiledDefinition(Cell prototype, @Nullable BlockState orientedExpected, BlockState @Nullable [] acceptable) {
            this.prototype = prototype;
            this.orientedExpected = orientedExpected;
            this.acceptable = acceptable;
        }

        public Cell prototype() {
            return prototype;
        }

        public @Nullable BlockState orientedExpected() {
            return orientedExpected;
        }

        BlockState @Nullable [] acceptable() {
            return acceptable;
        }
    }

    /** 数量约束中的角色及其紧凑编号；快速维护仅统计这些角色，避免构建完整角色映射。 */
    public static final class PartRoleOrdinals {

        private final PartRole[] capabilities;
        private final int[] mins;
        private final int[] maxs;

        private PartRoleOrdinals(PartRole[] capabilities, int[] mins, int[] maxs) {
            this.capabilities = capabilities;
            this.mins = mins;
            this.maxs = maxs;
        }

        private static PartRoleOrdinals of(Blueprint blueprint) {
            Map<PartRole, Blueprint.CountRequirement> requirements = blueprint.countRequirements();
            PartRole[] capabilities = new PartRole[requirements.size()];
            int[] mins = new int[requirements.size()];
            int[] maxs = new int[requirements.size()];
            int i = 0;
            for (Blueprint.CountRequirement requirement : requirements.values()) {
                capabilities[i] = requirement.role();
                mins[i] = requirement.min();
                maxs[i] = requirement.max();
                i++;
            }
            return new PartRoleOrdinals(capabilities, mins, maxs);
        }

        public int size() {
            return capabilities.length;
        }

        public int ordinalOf(PartRole capability) {
            for (int i = 0; i < capabilities.length; i++) {
                if (capabilities[i] == capability) {
                    return i;
                }
            }
            return -1;
        }

        public boolean satisfied(int[] counts) {
            for (int i = 0; i < capabilities.length; i++) {
                if (counts[i] < mins[i] || counts[i] > maxs[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
