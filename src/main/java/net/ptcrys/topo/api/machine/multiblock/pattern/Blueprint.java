package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.FormedMachineBlock;
import net.ptcrys.topo.api.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.api.machine.data.DataKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 不可变的多方块蓝图，使用以控制器 {@code @} 为原点的局部坐标系。
 * {@link Builder#aisle} 声明 XZ 字符层，连续层沿声明 +Y 堆叠；{@link Builder#frame} 将整个声明旋转到控制器局部坐标。
 * 有效旋转为控制器朝向与 frame 的复合，共支持 24 种正交旋转，并可显式启用镜像。
 * <p>
 * {@code @} 自动表示控制器，{@code #} 自动表示空气，空格忽略位置。其他字符通过 where 绑定谓词及可选的规范状态、属性规则。
 * 连续多层可作为一个重复组；解析出的副本数由 {@link RepeatResolution} 保存，后续内容随副本数推移。
 * 具体位置统一由 {@link BlueprintLayout} 计算，检测、预览、占地与反查使用相同布局。
 * 位置随重复计数变化的蓝图须使用实时视图；稳定布局可使用最大占地的不可变快照。
 */
public final class Blueprint {

    private static final char AIR_SYMBOL = '#';
    private static final char CONTROLLER_SYMBOL = '@';
    private static final PropertyRule[] NO_RULES = new PropertyRule[0];
    private static final long NO_DECLARED_CELL = -1L;

    /** 会随机器运行改变的属性。固定规范状态必须屏蔽这些属性，防止机器启动时被误判解体。 */
    private static final List<Property<?>> RUNTIME_MACHINE_STATE_PROPERTIES = List.of(FormedMachineBlock.FORMED, OrientedActiveMachineBlock.ACTIVE);

    private final List<Brick> bricks;
    private final List<RepeatSection> sections;
    private final Map<Character, CellDefinition> definitions;
    private final List<Character> symbolOrder;
    private final int controllerBrick;
    private final int[] controllerDecl;
    private final Orientation frame;
    private final boolean frameStable;
    private final int maxCellCount;
    private final Map<PartRole, CountRequirement> countRequirements;
    private final Long2LongOpenHashMap declaredRefs;
    private final Set<PropertyRule> orientationSensitiveRules;
    private final Map<Orientation, Boolean> orientationSupport = new ConcurrentHashMap<>();
    private final Map<Orientation, CompiledBlueprint> compiled = new ConcurrentHashMap<>();
    /** 按需展开并缓存的最小布局，仅供预览等需要 Cell 对象的调用者使用。 */
    private volatile @Nullable List<Cell> minCells;

    private Blueprint(
                      List<DeclaredCell> cells,
                      List<Brick> bricks,
                      List<RepeatSection> sections,
                      Map<Character, CellDefinition> definitions,
                      List<Character> symbolOrder,
                      int controllerCell,
                      int controllerBrick,
                      Orientation frame,
                      boolean frameStable,
                      int maxCellCount,
                      Map<PartRole, CountRequirement> countRequirements) {
        this.bricks = List.copyOf(bricks);
        this.sections = List.copyOf(sections);
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        this.symbolOrder = List.copyOf(symbolOrder);
        this.controllerBrick = controllerBrick;
        DeclaredCell controller = cells.get(controllerCell);
        this.controllerDecl = new int[] { controller.x(), controller.y(), controller.z() };
        this.frame = Objects.requireNonNull(frame, "blueprint frame");
        this.frameStable = frameStable;
        this.maxCellCount = maxCellCount;
        this.countRequirements = Map.copyOf(countRequirements);
        this.declaredRefs = buildDeclaredRefs(this.bricks);
        this.orientationSensitiveRules = collectOrientationSensitiveRules(this.definitions);
        validateCountRequirements();
        validateRuntimeStateProperties(this.definitions);
    }

    /** 所有 Cell 能力预声明的元数据字段；字段值不属于蓝图。 */
    public List<DataKey<?>> metadataKeys() {
        return definitions.values().stream().flatMap(definition -> definition.binding().metadataKeys().stream()).distinct().toList();
    }

    public static Builder of() {
        return new Builder();
    }

    // ------------------------------------------------------------------------------------------
    // Public surface — declaration data and layout.
    // ------------------------------------------------------------------------------------------

    /** 重复段数量；RepeatResolution 使用相同的声明顺序索引。 */
    public int sectionCount() {
        return sections.size();
    }

    /** 按声明顺序取得一个重复段。 */
    public RepeatSection section(int index) {
        return sections.get(index);
    }

    /** 按声明顺序返回所有重复段。 */
    public List<RepeatSection> sections() {
        return sections;
    }

    /** 各段取最小副本数时的规范单元格。会逐格分配对象；检测热路径使用 BlueprintLayout。 */
    public List<Cell> cells() {
        List<Cell> cached = minCells;
        if (cached == null) {
            cached = cells(minRepeats());
            minCells = cached;
        }
        return cached;
    }

    /** 按具体副本数展开规范单元格。坐标相对控制器，尚未应用 frame 或控制器朝向。 */
    public List<Cell> cells(RepeatResolution resolution) {
        BlueprintLayout layout = layout(resolution);
        List<Cell> out = new ArrayList<>(layout.cellCount());
        layout.forEachCell((index, definition, x, y, z) -> {
            out.add(cellOf(definition, new BlockPos(x, y, z)));
            return true;
        });
        return List.copyOf(out);
    }

    /** 为具体重复次数建立局部布局；计数必须位于每段声明的范围内。 */
    public BlueprintLayout layout(RepeatResolution resolution) {
        return new BlueprintLayout(this, resolution);
    }

    /** 返回具体布局的单元格数量，不展开副本。 */
    public int cellCount(RepeatResolution resolution) {
        return layout(resolution).cellCount();
    }

    /** 最大副本数下的检测位置数，供快照容量和异步阈值使用。 */
    public int maxCellCount() {
        return maxCellCount;
    }

    /** 每个重复段均取最小副本数。 */
    public RepeatResolution minRepeats() {
        int[] counts = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            counts[i] = sections.get(i).minCount();
        }
        return new RepeatResolution(counts);
    }

    /** 每个重复段均取最大副本数。 */
    public RepeatResolution maxRepeats() {
        int[] counts = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            counts[i] = sections.get(i).maxCount();
        }
        return new RepeatResolution(counts);
    }

    /** 所有单元格的控制器相对位置是否独立于重复计数。仅稳定布局可使用最大占地快照；移动布局须读取实时视图。 */
    public boolean supportsSnapshot() {
        return frameStable;
    }

    public Map<PartRole, CountRequirement> countRequirements() {
        return countRequirements;
    }

    /** 声明坐标到控制器局部坐标的旋转 frame。 */
    public Orientation frame() {
        return frame;
    }

    /** 实际用于检测的旋转：先应用 frame，再应用控制器朝向。 */
    public Orientation effectiveOrientation(Orientation machineOrientation) {
        return Objects.requireNonNull(machineOrientation, "orientation").compose(frame);
    }

    /** 状态规则能否表达该有效旋转。按蓝图和朝向缓存；受限的水平方向属性可能拒绝侧倾旋转。 */
    public boolean supports(Orientation machineOrientation) {
        Objects.requireNonNull(machineOrientation, "orientation");
        return orientationSupport.computeIfAbsent(machineOrientation, this::computeSupports);
    }

    /** 按控制器朝向缓存编译结果。编译符号状态规则并复合 frame，快照采集数组在需要时才生成。 */
    public CompiledBlueprint compiled(Orientation machineOrientation) {
        Objects.requireNonNull(machineOrientation, "orientation");
        return compiled.computeIfAbsent(machineOrientation, orientation -> CompiledBlueprint.compile(this, orientation, orientation.compose(frame)));
    }

    // ------------------------------------------------------------------------------------------
    // Package-private surface for the compiled form and the engine.
    // ------------------------------------------------------------------------------------------

    List<Brick> bricksInternal() {
        return bricks;
    }

    List<CellDefinition> symbolDefinitions() {
        List<CellDefinition> ordered = new ArrayList<>(symbolOrder.size());
        for (Character symbol : symbolOrder) {
            ordered.add(definitions.get(symbol));
        }
        return ordered;
    }

    int controllerBrick() {
        return controllerBrick;
    }

    int[] controllerDecl() {
        return controllerDecl.clone();
    }

    /** 前置重复段在给定计数下对本块产生的声明坐标推移。 */
    int[] shiftOf(Brick brick, int[] counts) {
        int[] shift = new int[3];
        for (int dep : brick.dependencies()) {
            RepeatSection section = sections.get(dep);
            int delta = Math.multiplyExact(counts[dep] - 1, section.stride());
            int axis = section.axis().ordinal();
            shift[axis] = Math.addExact(shift[axis], delta);
        }
        return shift;
    }

    int[] minShiftOf(Brick brick) {
        int[] shift = new int[3];
        for (int dep : brick.dependencies()) {
            RepeatSection section = sections.get(dep);
            shift[section.axis().ordinal()] += (section.minCount() - 1) * section.stride();
        }
        return shift;
    }

    int[] maxShiftOf(Brick brick) {
        int[] shift = new int[3];
        for (int dep : brick.dependencies()) {
            RepeatSection section = sections.get(dep);
            shift[section.axis().ordinal()] += (section.maxCount() - 1) * section.stride();
        }
        return shift;
    }

    /** 声明位置对应的压缩引用：高 32 位为块编号，低 32 位为组内编号；不存在时为 -1。 */
    long declaredRefAt(long position) {
        return declaredRefs.get(position);
    }

    static long packRef(int brick, int member) {
        return ((long) brick << 32) | (member & 0xFFFFFFFFL);
    }

    static int refBrick(long ref) {
        return (int) (ref >>> 32);
    }

    static int refMember(long ref) {
        return (int) ref;
    }

    Cell cellOf(int symbolOrdinal, BlockPos localPos) {
        CellDefinition definition = definitions.get(symbolOrder.get(symbolOrdinal));
        return new Cell(
                localPos,
                definition.expectedState(),
                definition.rules(),
                definition.binding(),
                definition.maskedProperties());
    }

    void validateResolution(RepeatResolution resolution) {
        Objects.requireNonNull(resolution, "repeat resolution");
        if (resolution.size() != sections.size()) {
            throw new IllegalArgumentException("Repeat resolution does not match the blueprint's sections");
        }
        for (int i = 0; i < sections.size(); i++) {
            RepeatSection section = sections.get(i);
            int count = resolution.count(i);
            if (count < section.minCount() || count > section.maxCount()) {
                throw new IllegalArgumentException("Repeat count outside declared range for section " + i + ": " + count);
            }
        }
    }

    private boolean computeSupports(Orientation machineOrientation) {
        Orientation effective = machineOrientation.compose(frame);
        for (PropertyRule rule : orientationSensitiveRules) {
            if (!rule.supports(effective)) {
                return false;
            }
        }
        return true;
    }

    /** 固定了规范状态的定义所使用的去重规则集合。 */
    private static Set<PropertyRule> collectOrientationSensitiveRules(Map<Character, CellDefinition> definitions) {
        Set<PropertyRule> sensitive = new LinkedHashSet<>();
        for (CellDefinition definition : definitions.values()) {
            if (definition.expectedState() != null) {
                sensitive.addAll(definition.rules());
            }
        }
        return Set.copyOf(sensitive);
    }

    private static Long2LongOpenHashMap buildDeclaredRefs(List<Brick> bricks) {
        Long2LongOpenHashMap refs = new Long2LongOpenHashMap();
        refs.defaultReturnValue(NO_DECLARED_CELL);
        for (int b = 0; b < bricks.size(); b++) {
            Brick brick = bricks.get(b);
            for (int k = 0; k < brick.cellCount(); k++) {
                long key = BlockPos.asLong(brick.xs()[k], brick.ys()[k], brick.zs()[k]);
                refs.put(key, packRef(b, k));
            }
        }
        return refs;
    }

    private static void validateRuntimeStateProperties(Map<Character, CellDefinition> definitions) {
        for (CellDefinition definition : definitions.values()) {
            BlockState expected = definition.expectedState();
            if (expected == null) {
                continue;
            }
            for (Property<?> property : RUNTIME_MACHINE_STATE_PROPERTIES) {
                if (expected.hasProperty(property) && !definition.maskedProperties().contains(property)) {
                    throw new IllegalStateException(
                            "Blueprint symbol '" + definition.symbol() + "' pins an expected state carrying" + " the runtime machine property '" + property.getName() + "', which flips while the machine runs and would unform the structure:" + " drop the property from the expected state, or mask it with a" + " PropertyRules.derived(...) rule on the same cell");
                }
            }
        }
    }

    /** 按定义与副本数校验数量约束，不展开单元格对象。 */
    private void validateCountRequirements() {
        for (CountRequirement requirement : countRequirements.values()) {
            PartRole capability = requirement.role();
            int maxAchievable = 0;
            for (Brick brick : bricks) {
                int multiplicity = brick.section() < 0 ? 1 : sections.get(brick.section()).maxCount();
                for (int k = 0; k < brick.cellCount(); k++) {
                    if (definitions.get(symbolOrder.get(brick.definitions()[k])).binding().partRoles().contains(capability)) {
                        maxAchievable += multiplicity;
                    }
                }
            }
            if (maxAchievable == 0) {
                throw new IllegalStateException(
                        "Count requirement " + capability.id() + " has no matching capability cell");
            }
            if (requirement.min() > maxAchievable) {
                throw new IllegalArgumentException(
                        "Count requirement " + capability.id() + " demands a minimum of " + requirement.min() + " but at most " + maxAchievable + " blueprint cell(s) can ever carry that capability");
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Value types.
    // ------------------------------------------------------------------------------------------

    /** 重复段的闭区间副本数范围；min 等于 max 表示固定次数。 */
    public record RepeatRange(int min, int max) {

        public RepeatRange {
            if (min < 0) {
                throw new IllegalArgumentException("Repeat minimum must be non-negative");
            }
            if (max < min) {
                throw new IllegalArgumentException("Repeat maximum must be >= minimum");
            }
        }

        public boolean fixed() {
            return min == max;
        }
    }

    /** 一个连续重复组。bandStart/bandEnd 是控制器归一化后的声明层区间（左闭右开），stride 是每份副本的层数。 */
    public record RepeatSection(
                                Direction.Axis axis,
                                RepeatRange repeat,
                                int bandStart,
                                int bandEnd,
                                int stride,
                                int cellCount) {

        public RepeatSection {
            Objects.requireNonNull(axis, "repeat axis");
            Objects.requireNonNull(repeat, "repeat range");
            if (bandEnd <= bandStart) {
                throw new IllegalArgumentException("Repeat section band must be non-empty");
            }
            if (stride != bandEnd - bandStart) {
                throw new IllegalArgumentException("Repeat section stride must equal the band length");
            }
            if (cellCount <= 0) {
                throw new IllegalArgumentException("Repeat section must contain at least one cell");
            }
        }

        public int minCount() {
            return repeat.min();
        }

        public int maxCount() {
            return repeat.max();
        }

        public boolean repeatable() {
            return !repeat.fixed();
        }
    }

    public record CountRequirement(PartRole role, int min, int max) {

        public CountRequirement {
            role = Objects.requireNonNull(role, "part role");
            if (min < 0) {
                throw new IllegalArgumentException("Minimum count must be non-negative");
            }
            if (max < min) {
                throw new IllegalArgumentException("Maximum count must be >= minimum count");
            }
        }

        public boolean accepts(int count) {
            return count >= min && count <= max;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Internal declaration model.
    // ------------------------------------------------------------------------------------------

    record DeclaredCell(int x, int y, int z, char symbol, int section) {}

    /** 紧凑遍历块：一段连续固定单元格或一个重复组。平行数组保存符号定义与规范坐标，dependencies 记录会推移本块的前置重复段。 */
    record Brick(
                 int section,
                 int indexBase,
                 int[] dependencies,
                 int[] definitions,
                 int[] xs,
                 int[] ys,
                 int[] zs) {

        int cellCount() {
            return definitions.length;
        }
    }

    record CellDefinition(
                          char symbol,
                          CellBinding binding,
                          @Nullable BlockState expectedState,
                          Set<PropertyRule> rules,
                          Set<Property<?>> maskedProperties) {

        CellDefinition {
            binding = Objects.requireNonNull(binding, "cell binding");
            rules = Set.copyOf(Objects.requireNonNull(rules, "property rules"));
            maskedProperties = Set.copyOf(Objects.requireNonNull(maskedProperties, "masked properties"));
        }

        CellDefinition(
                       char symbol,
                       CellBinding binding,
                       @Nullable BlockState expectedState,
                       Set<PropertyRule> rules) {
            this(symbol, binding, expectedState, rules, Cell.maskedPropertiesOf(rules, expectedState));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Builder.
    // ------------------------------------------------------------------------------------------

    public static final class Builder {

        private final List<Layer> layers = new ArrayList<>();
        private final List<SectionDraft> sections = new ArrayList<>();
        private final List<DeclaredCell> cells = new ArrayList<>();
        private final Map<Character, CellDefinition> definitions = new LinkedHashMap<>();
        private final Map<PartRole, CountRequirement> countRequirements = new LinkedHashMap<>();
        private Orientation frame = Orientation.IDENTITY;
        private boolean frameSet;
        private boolean inSection;
        private @Nullable SectionDraft currentSection;
        private int layerWidth = -1;
        private int layerHeight = -1;

        private Builder() {}

        // ---- frame ----------------------------------------------------------------------------

        /** 设置整个声明相对控制器的旋转：+Y 映射到 up，+Z 映射到 front，+X 映射到 up × front。两方向必须垂直，默认 UP/SOUTH；仅允许声明一次。 */
        public Builder frame(Direction up, Direction front) {
            ensureNotInSection("set the frame");
            Objects.requireNonNull(up, "frame up");
            Objects.requireNonNull(front, "frame front");
            if (frameSet) {
                throw new IllegalStateException("frame(...) may be declared only once");
            }
            if (up.getAxis() == front.getAxis()) {
                throw new IllegalArgumentException("frame up and front must lie on different axes: " + up + " / " + front);
            }
            int rx = up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY();
            int ry = up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ();
            int rz = up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX();
            Direction right = Objects.requireNonNull(Direction.getNearest(rx, ry, rz, null), "frame right direction");
            this.frame = Orientation.fromColumns(right, up, front);
            this.frameSet = true;
            return this;
        }

        // ---- layers ---------------------------------------------------------------------------

        /** 声明一个矩形 XZ 层：字符沿 +X、行沿 +Z、层沿 +Y。所有层尺寸必须相同，构建器保存行数组的副本。 */
        public Builder aisle(String... rows) {
            ensureNotInSection("declare a layer");
            addLayer(rows, null);
            return this;
        }

        // ---- repeat sections ------------------------------------------------------------------

        /** 开始一个连续多层重复组。组内只允许声明层，endRepeatable 返回主构建器；不能嵌套或包含控制器。 */
        public SectionBuilder beginRepeatable(int min, int max) {
            ensureNotInSection("begin a repeat section");
            RepeatRange range = new RepeatRange(min, max);
            if (range.max() < 1) {
                throw new IllegalArgumentException("A repeat section must be able to appear at least once (max >= 1)");
            }
            SectionDraft draft = new SectionDraft(range, layers.size(), sections.size());
            sections.add(draft);
            inSection = true;
            currentSection = draft;
            return new SectionBuilder(this, draft);
        }

        /** 在回调中声明重复组，回调返回后自动闭合；等价于 beginRepeatable/endRepeatable。 */
        public Builder repeat(int min, int max, Consumer<SectionBuilder> body) {
            Objects.requireNonNull(body, "repeat section body");
            SectionBuilder section = beginRepeatable(min, max);
            body.accept(section);
            return section.endRepeatable();
        }

        // ---- definitions ----------------------------------------------------------------------

        public Builder where(char symbol, CellBinding binding) {
            return where(symbol, binding, null, NO_RULES);
        }

        public Builder where(char symbol, CellBinding binding, @Nullable BlockState expectedState) {
            return where(symbol, binding, expectedState, NO_RULES);
        }

        /** 定义普通字符的谓词、旋转前的规范状态及属性规则。@ 与 # 自动定义，禁止重绑定；同一属性不能被多个协变规则重复变换。 */
        public Builder where(
                             char symbol,
                             CellBinding binding,
                             @Nullable BlockState expectedState,
                             PropertyRule... rules) {
            Objects.requireNonNull(binding, "cell binding");
            Objects.requireNonNull(rules, "property rules");
            ensureNotInSection("define a symbol");
            validateSymbol(symbol);
            validateRules(symbol, rules);
            CellDefinition definition = new CellDefinition(
                    symbol,
                    binding,
                    expectedState,
                    new LinkedHashSet<>(Arrays.asList(rules)));
            CellDefinition previous = definitions.putIfAbsent(symbol, definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate blueprint symbol definition: '" + symbol + "'");
            }
            return this;
        }

        /** 按方块类型匹配，不固定状态属性。 */
        public Builder where(char symbol, Block block) {
            return where(symbol, CellPredicates.block(Objects.requireNonNull(block, "block")));
        }

        /** 按方块类型匹配，并将默认状态固定为规范状态；所给规则随有效旋转处理该状态。 */
        public Builder where(char symbol, Block block, PropertyRule... rules) {
            Objects.requireNonNull(block, "block");
            return where(symbol, CellPredicates.block(block), block.defaultBlockState(), rules);
        }

        /** 按方块类型匹配，并固定显式给定的规范状态。 */
        public Builder where(char symbol, Block block, BlockState expectedState, PropertyRule... rules) {
            Objects.requireNonNull(block, "block");
            return where(symbol, CellPredicates.block(block), expectedState, rules);
        }

        public Builder count(PartRole capability, int min, int max) {
            ensureNotInSection("declare a count requirement");
            CountRequirement requirement = new CountRequirement(capability, min, max);
            CountRequirement previous = countRequirements.putIfAbsent(capability, requirement);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate count requirement for " + capability.id());
            }
            return this;
        }

        // ---- build ----------------------------------------------------------------------------

        public Blueprint build() {
            if (inSection) {
                throw new IllegalStateException("A repeat section is still open; call endRepeatable() first");
            }
            if (layers.isEmpty()) {
                throw new IllegalStateException("Blueprint must declare at least one aisle");
            }

            int controllerLayer = -1;
            int controllerRow = -1;
            int controllerChar = -1;
            int controllerCount = 0;
            for (int w = 0; w < layers.size(); w++) {
                Layer layer = layers.get(w);
                for (int v = 0; v < layer.rows().length; v++) {
                    String row = layer.rows()[v];
                    for (int u = 0; u < row.length(); u++) {
                        if (row.charAt(u) == CONTROLLER_SYMBOL) {
                            controllerLayer = w;
                            controllerRow = v;
                            controllerChar = u;
                            controllerCount++;
                        }
                    }
                }
            }
            if (controllerCount != 1) {
                throw new IllegalStateException("Blueprint must contain exactly one '@' controller cell");
            }
            if (layers.get(controllerLayer).section() >= 0) {
                throw new IllegalStateException("The controller cell cannot be inside a repeat section");
            }

            cells.clear();
            int[] sectionFirst = new int[sections.size()];
            Arrays.fill(sectionFirst, -1);
            int[] sectionCells = new int[sections.size()];
            for (int w = 0; w < layers.size(); w++) {
                Layer layer = layers.get(w);
                int section = layer.section();
                if (section >= 0 && sectionFirst[section] < 0) {
                    sectionFirst[section] = cells.size();
                }
                for (int v = 0; v < layer.rows().length; v++) {
                    String row = layer.rows()[v];
                    for (int u = 0; u < row.length(); u++) {
                        char symbol = row.charAt(u);
                        if (symbol == ' ') {
                            continue;
                        }
                        cells.add(new DeclaredCell(
                                u - controllerChar,
                                w - controllerLayer,
                                v - controllerRow,
                                symbol,
                                section));
                        if (section >= 0) {
                            sectionCells[section]++;
                        }
                    }
                }
            }
            for (int s = 0; s < sections.size(); s++) {
                SectionDraft draft = sections.get(s);
                draft.firstCell = sectionFirst[s];
                draft.cellCount = sectionCells[s];
                if (draft.layerCount <= 0) {
                    throw new IllegalStateException("Repeat section #" + s + " contains no layers");
                }
                if (draft.cellCount <= 0) {
                    throw new IllegalStateException("Repeat section #" + s + " contains no cells");
                }
            }
            rejectDuplicatePositions();

            int controllerCell = -1;
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).symbol() == CONTROLLER_SYMBOL) {
                    controllerCell = i;
                    break;
                }
            }
            if (controllerCell < 0) {
                throw new IllegalStateException("Controller cell is missing from the generated cell list");
            }

            List<Character> symbolOrder = validateDefinitions();
            symbolOrder.add(CONTROLLER_SYMBOL);
            symbolOrder.add(AIR_SYMBOL);

            List<RepeatSection> sectionInfo = buildSectionInfo(controllerLayer);
            validateSectionLayout(sectionInfo);

            List<Brick> bricks = buildBricks(sectionInfo, symbolOrder);
            int controllerBrick = controllerBrick(bricks, controllerCell);
            boolean frameStable = computeFrameStable(sectionInfo);
            int maxCellCount = 0;
            for (Brick brick : bricks) {
                int multiplicity = brick.section() < 0 ? 1 : sectionInfo.get(brick.section()).maxCount();
                maxCellCount = Math.addExact(maxCellCount, Math.multiplyExact(brick.cellCount(), multiplicity));
            }

            // '@' is structural: its definition is always the controller binding.
            CellDefinition controllerDefinition = new CellDefinition(
                    CONTROLLER_SYMBOL, CellPredicates.controller(), null, Set.of());
            Map<Character, CellDefinition> finalDefinitions = new LinkedHashMap<>(definitions);
            finalDefinitions.put(CONTROLLER_SYMBOL, controllerDefinition);
            finalDefinitions.put(AIR_SYMBOL, new CellDefinition(AIR_SYMBOL, CellPredicates.air(), null, Set.of()));

            return new Blueprint(
                    cells,
                    bricks,
                    sectionInfo,
                    finalDefinitions,
                    symbolOrder,
                    controllerCell,
                    controllerBrick,
                    frame,
                    frameStable,
                    maxCellCount,
                    countRequirements);
        }

        private void addLayer(String[] rows, @Nullable SectionDraft draft) {
            validateRows(rows);
            int height = rows.length;
            int width = rows[0].length();
            if (layers.isEmpty()) {
                layerWidth = width;
                layerHeight = height;
            } else if (width != layerWidth || height != layerHeight) {
                throw new IllegalArgumentException(
                        "Every blueprint layer must have the same dimensions: expected " + layerWidth + "x" + layerHeight + ", got " + width + "x" + height);
            }
            if (draft != null) {
                if (!inSection || currentSection != draft) {
                    throw new IllegalStateException("Repeat section is not open");
                }
            } else if (inSection) {
                throw new IllegalStateException("Cannot declare a blueprint layer inside an open repeat section; use the section builder");
            }
            layers.add(new Layer(rows.clone(), draft == null ? -1 : draft.ordinal));
            if (draft != null) {
                draft.layerCount++;
            }
        }

        private void endSection(SectionDraft draft) {
            if (!inSection || currentSection != draft) {
                throw new IllegalStateException("Repeat section is not open");
            }
            if (draft.layerCount <= 0) {
                throw new IllegalStateException("A repeat section must contain at least one layer");
            }
            inSection = false;
            currentSection = null;
        }

        private void ensureNotInSection(String action) {
            if (inSection) {
                throw new IllegalStateException("Cannot " + action + " while a repeat section is open; call endRepeatable() first");
            }
        }

        private void rejectDuplicatePositions() {
            Map<Long, DeclaredCell> seen = new LinkedHashMap<>();
            for (DeclaredCell cell : cells) {
                DeclaredCell previous = seen.put(BlockPos.asLong(cell.x(), cell.y(), cell.z()), cell);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate blueprint cell at (" + cell.x() + "," + cell.y() + "," + cell.z() + "): symbols '" + previous.symbol() + "' and '" + cell.symbol() + "'");
                }
            }
        }

        private List<Character> validateDefinitions() {
            Set<Character> used = new LinkedHashSet<>();
            for (DeclaredCell cell : cells) {
                if (cell.symbol() != CONTROLLER_SYMBOL && cell.symbol() != AIR_SYMBOL) {
                    used.add(cell.symbol());
                }
            }
            if (definitions.containsKey(CONTROLLER_SYMBOL)) {
                throw new IllegalArgumentException(
                        "The controller symbol '@' is reserved and defined automatically as the controller cell; remove its where(...) definition");
            }
            for (Character symbol : used) {
                if (!definitions.containsKey(symbol)) {
                    throw new IllegalStateException("Missing blueprint definition for symbol '" + symbol + "'");
                }
            }
            for (Character symbol : definitions.keySet()) {
                if (!used.contains(symbol)) {
                    throw new IllegalStateException("Blueprint definition for symbol '" + symbol + "' is unused");
                }
            }
            return new ArrayList<>(definitions.keySet());
        }

        private List<RepeatSection> buildSectionInfo(int controllerLayer) {
            List<RepeatSection> info = new ArrayList<>(sections.size());
            for (SectionDraft draft : sections) {
                int start = draft.startLayer - controllerLayer;
                int end = start + draft.layerCount;
                info.add(new RepeatSection(Direction.Axis.Y, draft.repeat, start, end, draft.layerCount, draft.cellCount));
            }
            return info;
        }

        private void validateSectionLayout(List<RepeatSection> info) {
            for (int s = 0; s < info.size(); s++) {
                RepeatSection section = info.get(s);
                SectionDraft draft = sections.get(s);
                int first = draft.firstCell;
                int last = first + draft.cellCount - 1;
                Direction.Axis axis = section.axis();

                for (int i = 0; i < cells.size(); i++) {
                    DeclaredCell cell = cells.get(i);
                    if (cell.section() == s) {
                        continue;
                    }
                    int coordinate = coordinate(cell, axis);
                    if (coordinate >= section.bandStart() && coordinate < section.bandEnd()) {
                        throw new IllegalStateException(
                                "Blueprint cell (" + cell.x() + "," + cell.y() + "," + cell.z() + ") lies inside the band of repeat section #" + s + " (" + axis + " " + section.bandStart() + ".." + (section.bandEnd() - 1) + "); every cell in a repeat band must belong to the section");
                    }
                    if (i < first && coordinate >= section.bandStart()) {
                        throw new IllegalStateException(
                                "Blueprint cell (" + cell.x() + "," + cell.y() + "," + cell.z() + ") is declared before repeat section #" + s + " but lies at or after its band; repeat sections only grow forward, so declare the cell after the section");
                    }
                }

                // A later section must lie entirely before or entirely beyond this band: a section
                // cannot be split by another section's growth.
                for (int other = 0; other < info.size(); other++) {
                    if (other == s) {
                        continue;
                    }
                    SectionDraft otherDraft = sections.get(other);
                    if (otherDraft.firstCell <= last) {
                        continue; // declared before; already covered by the cell rule above
                    }
                    RepeatSection later = info.get(other);
                    int min = Integer.MAX_VALUE;
                    int max = Integer.MIN_VALUE;
                    for (DeclaredCell cell : cells) {
                        if (cell.section() != other) {
                            continue;
                        }
                        int coordinate = coordinate(cell, axis);
                        min = Math.min(min, coordinate);
                        max = Math.max(max, coordinate);
                    }
                    if (min < section.bandStart() && max >= section.bandEnd()) {
                        throw new IllegalStateException(
                                "Repeat section #" + other + " straddles the band of repeat section #" + s + " along " + axis + "; a section must be entirely before or entirely after another band");
                    }
                }
            }
        }

        private List<Brick> buildBricks(List<RepeatSection> info, List<Character> symbolOrder) {
            List<Brick> bricks = new ArrayList<>();
            int[] indexBase = { 0 };
            int cellIndex = 0;
            int sectionOrdinal = 0;
            while (cellIndex < cells.size()) {
                int section = cells.get(cellIndex).section();
                if (section < 0) {
                    int end = cellIndex + 1;
                    while (end < cells.size() && cells.get(end).section() < 0) {
                        end++;
                    }
                    bricks.add(buildBrick(-1, cellIndex, end, info, symbolOrder, indexBase));
                    cellIndex = end;
                    continue;
                }
                if (section != sectionOrdinal) {
                    throw new IllegalStateException("Repeat section cells must be contiguous");
                }
                int end = cellIndex;
                while (end < cells.size() && cells.get(end).section() == section) {
                    end++;
                }
                bricks.add(buildBrick(section, cellIndex, end, info, symbolOrder, indexBase));
                cellIndex = end;
                sectionOrdinal++;
            }
            if (sectionOrdinal != info.size()) {
                throw new IllegalStateException("Repeat section declarations do not match the cell list");
            }
            return bricks;
        }

        private Brick buildBrick(
                                 int section,
                                 int firstCell,
                                 int endCell,
                                 List<RepeatSection> info,
                                 List<Character> symbolOrder,
                                 int[] indexBase) {
            int size = endCell - firstCell;
            int[] defs = new int[size];
            int[] xs = new int[size];
            int[] ys = new int[size];
            int[] zs = new int[size];
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int k = 0; k < size; k++) {
                DeclaredCell cell = cells.get(firstCell + k);
                defs[k] = symbolOrder.indexOf(cell.symbol());
                xs[k] = cell.x();
                ys[k] = cell.y();
                zs[k] = cell.z();
                minX = Math.min(minX, cell.x());
                minY = Math.min(minY, cell.y());
                minZ = Math.min(minZ, cell.z());
                maxX = Math.max(maxX, cell.x());
                maxY = Math.max(maxY, cell.y());
                maxZ = Math.max(maxZ, cell.z());
            }
            List<Integer> dependencies = new ArrayList<>();
            for (int s = 0; s < info.size(); s++) {
                RepeatSection earlier = info.get(s);
                // Only sections declared before this brick can shift it.
                SectionDraft draft = sections.get(s);
                if (draft.firstCell + draft.cellCount > firstCell) {
                    continue;
                }
                int brickMin = switch (earlier.axis()) {
                    case X -> minX;
                    case Y -> minY;
                    case Z -> minZ;
                };
                if (brickMin >= earlier.bandEnd()) {
                    dependencies.add(s);
                }
            }
            int[] deps = new int[dependencies.size()];
            for (int i = 0; i < deps.length; i++) {
                deps[i] = dependencies.get(i);
            }
            int base = indexBase[0];
            int multiplicity = section < 0 ? 1 : info.get(section).maxCount();
            indexBase[0] = Math.addExact(base, Math.multiplyExact(size, multiplicity));
            return new Brick(section, base, deps, defs, xs, ys, zs);
        }

        private int controllerBrick(List<Brick> bricks, int controllerCell) {
            int cellIndex = 0;
            for (int b = 0; b < bricks.size(); b++) {
                Brick brick = bricks.get(b);
                if (controllerCell >= cellIndex && controllerCell < cellIndex + brick.cellCount()) {
                    return b;
                }
                cellIndex += brick.cellCount();
            }
            throw new IllegalStateException("Controller cell is not part of any brick");
        }

        private boolean computeFrameStable(List<RepeatSection> info) {
            for (int s = 0; s < info.size(); s++) {
                RepeatSection section = info.get(s);
                // 固定次数只产生常量位移，不会让后续单元格或控制器锚点随解析结果移动。
                if (!section.repeatable()) continue;
                SectionDraft draft = sections.get(s);
                int last = draft.firstCell + draft.cellCount - 1;
                for (int i = last + 1; i < cells.size(); i++) {
                    DeclaredCell cell = cells.get(i);
                    if (cell.section() == s) {
                        continue;
                    }
                    if (coordinate(cell, section.axis()) >= section.bandEnd()) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static void validateRows(String[] rows) {
            Objects.requireNonNull(rows, "plane rows");
            if (rows.length == 0) {
                throw new IllegalArgumentException("A layer must contain at least one row");
            }
            Objects.requireNonNull(rows[0], "layer row");
            int width = rows[0].length();
            if (width == 0) {
                throw new IllegalArgumentException("Layer rows must not be empty");
            }
            for (String row : rows) {
                Objects.requireNonNull(row, "layer row");
                if (row.length() != width) {
                    throw new IllegalArgumentException("Every row in a layer must have the same width");
                }
            }
        }

        private static void validateSymbol(char symbol) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("Space is reserved for empty blueprint cells");
            }
            if (symbol == CONTROLLER_SYMBOL || symbol == AIR_SYMBOL) {
                throw new IllegalArgumentException(
                        "Blueprint symbols '@' and '#' are reserved and defined automatically");
            }
        }

        private static void validateRules(char symbol, PropertyRule[] rules) {
            Set<Property<?>> governed = new LinkedHashSet<>();
            for (PropertyRule rule : rules) {
                Objects.requireNonNull(rule, "property rule");
                if (rule.classification() != PropertyRule.Classification.COVARIANT) {
                    continue;
                }
                for (Property<?> property : rule.governedProperties()) {
                    if (!governed.add(property)) {
                        throw new IllegalArgumentException(
                                "Blueprint symbol '" + symbol + "' has more than one covariant rule governing property '" + property.getName() + "'");
                    }
                }
            }
        }

        private static int coordinate(DeclaredCell cell, Direction.Axis axis) {
            return switch (axis) {
                case X -> cell.x();
                case Y -> cell.y();
                case Z -> cell.z();
            };
        }

        private record Layer(String[] rows, int section) {}

        private static final class SectionDraft {

            private final RepeatRange repeat;
            private final int startLayer;
            private final int ordinal;
            private int layerCount;
            private int firstCell;
            private int cellCount;

            private SectionDraft(RepeatRange repeat, int startLayer, int ordinal) {
                this.repeat = repeat;
                this.startLayer = startLayer;
                this.ordinal = ordinal;
            }
        }
    }

    /** 重复组的受限构建入口，只声明组内的连续层。 */
    public static final class SectionBuilder {

        private final Builder builder;
        private final Builder.SectionDraft draft;

        private SectionBuilder(Builder builder, Builder.SectionDraft draft) {
            this.builder = builder;
            this.draft = draft;
        }

        /** 在当前重复组中声明一层。 */
        public SectionBuilder aisle(String... rows) {
            builder.addLayer(rows, draft);
            return this;
        }

        /** 闭合当前重复组并返回蓝图构建器。 */
        public Builder endRepeatable() {
            builder.endSection(draft);
            return builder;
        }
    }
}
