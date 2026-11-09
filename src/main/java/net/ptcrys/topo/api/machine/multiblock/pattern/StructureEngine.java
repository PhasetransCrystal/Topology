package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 多方块检测引擎。先解析重复次数，再通过 {@link BlueprintLayout} 访问最终布局；
 * 完整诊断与快速维护共用坐标、谓词及角色计数逻辑，只采用不同的结果收集方式。
 * 异步调用必须传入不可变快照；实时视图只能在世界所属线程使用。
 */
public final class StructureEngine {

    /** 诊断只保留有界缺失样本，总缺失数另行统计。 */
    public static final int MISSING_SAMPLE_CAP = 64;

    private StructureEngine() {}

    /** 完整检测，返回成员、计数、具体布局、边界和最多 64 个缺失样本。 */
    public static RecognitionResult recognize(StructureView view, Blueprint blueprint, Orientation orientation) {
        return recognize(view, blueprint, orientation, StructureMetadata.defaults(List.of(blueprint)).edit(), "body");
    }

    /** 使用整个结构共享的工作元数据；模块传入 module/ID，失败结果由上层决定是否接纳。 */
    public static RecognitionResult recognize(StructureView view, Blueprint blueprint, Orientation orientation, DataTransaction metadata, String part) {
        validate(view, blueprint, orientation);
        if (!blueprint.supports(orientation)) return RecognitionResult.orientationUnsupported(blueprint.minRepeats());
        DataTransaction work = metadata.forkRefreshing(Set.of(StructureMetadata.ORIENTATION, StructureMetadata.REPEATS));
        Pass pass = new Pass(view, blueprint.compiled(orientation), work, part);
        BlueprintLayout layout = pass.resolve();
        RecognitionResult result = recognize(pass, layout);
        metadata.accept(work);
        return result;
    }

    /** 按指定重复次数检测完整目标布局，不探测增长、不根据未完成的世界缩短重复段；用于施工、虚影与目标诊断。 */
    public static RecognitionResult recognize(StructureView view, Blueprint blueprint, Orientation orientation, RepeatResolution repeats) {
        return recognize(view, blueprint, orientation, repeats, StructureMetadata.defaults(List.of(blueprint)).edit(), "body");
    }

    /** 在已有元数据下校验明确的目标布局；不替代放置候选选择或实际施工契约。 */
    public static RecognitionResult recognize(StructureView view, Blueprint blueprint, Orientation orientation, RepeatResolution repeats, DataTransaction metadata, String part) {
        validate(view, blueprint, orientation);
        BlueprintLayout layout = blueprint.layout(repeats);
        if (!blueprint.supports(orientation)) return RecognitionResult.orientationUnsupported(repeats);
        DataTransaction work = metadata.forkRefreshing(Set.of(StructureMetadata.ORIENTATION, StructureMetadata.REPEATS));
        RecognitionResult result = recognize(new Pass(view, blueprint.compiled(orientation), work, part), layout);
        metadata.accept(work);
        return result;
    }

    private static RecognitionResult recognize(Pass pass, BlueprintLayout layout) {
        StructureMetadata.geometry(pass.metadata, pass.compiled.orientation(), layout.repeats(), pass.part);
        Tally tally = new Tally(pass.compiled, true);
        pass.scan(layout, tally);
        int unloaded = pass.unloadedCount();
        int unknown = pass.unknownCount();
        boolean formed = unknown == 0 && !tally.mismatch && tally.countsSatisfied();
        return new RecognitionResult(formed, tally.matched, tally.missing, unloaded, unknown,
                tally.samples, tally.unknownSamples, tally.roles, tally.parts, tally.members, layout.repeats(), tally.bounds(),
                unloaded > 0 ? RecognitionResult.FailureReason.CHUNK_UNLOADED : unknown > 0 ? RecognitionResult.FailureReason.CELL_UNKNOWN : RecognitionResult.FailureReason.NONE, pass.metadata.snapshot());
    }

    /** 快速维护检查，不构造成员或缺失样本集合；遇到阻塞单元格即结束最终布局扫描。 */
    public static Verification verify(StructureView view, Blueprint blueprint, Orientation orientation) {
        return verify(view, blueprint, orientation, StructureMetadata.defaults(List.of(blueprint)).edit());
    }

    /** 保留已确认的元数据基线进行周期校验，不把新事实写回正式数据域。 */
    public static Verification verify(StructureView view, Blueprint blueprint, Orientation orientation, DataTransaction metadata) {
        validate(view, blueprint, orientation);
        if (!blueprint.supports(orientation)) return new Verification(false, blueprint.minRepeats(), null, false);
        Pass pass = new Pass(view, blueprint.compiled(orientation), metadata.forkRefreshing(Set.of(StructureMetadata.ORIENTATION, StructureMetadata.REPEATS)), "body");
        BlueprintLayout layout = pass.resolve();
        StructureMetadata.geometry(pass.metadata, orientation, layout.repeats(), "body");
        Tally tally = new Tally(pass.compiled, false);
        pass.scan(layout, tally);
        boolean unloaded = pass.unknownCount() > 0;
        return new Verification(!unloaded && !tally.mismatch && tally.countsSatisfied(), layout.repeats(), tally.firstMismatch, unloaded);
    }

    private static void validate(StructureView view, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(view, "structure view");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        if (view instanceof CompiledSnapshot snapshot && (snapshot.compiled().blueprint() != blueprint || !snapshot.compiled().orientation().equals(orientation))) {
            throw new IllegalArgumentException("Snapshot belongs to another blueprint or orientation");
        }
    }

    /** 一次检测的局部工作区；不把游标、探测状态或计数缓存发布给其他线程。 */
    private static final class Pass {

        private final StructureView view;
        private final CompiledBlueprint compiled;
        private final Blueprint blueprint;
        private final BlockPos origin;
        private final Orientation orientation;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private @Nullable LongOpenHashSet unloaded;
        private @Nullable LongOpenHashSet unknownCells;
        private DataTransaction metadata;
        private final String part;

        Pass(StructureView view, CompiledBlueprint compiled, DataTransaction metadata, String part) {
            this.metadata = metadata;
            this.part = part;
            this.view = view;
            this.compiled = compiled;
            this.blueprint = compiled.blueprint();
            this.origin = view.controllerPos();
            this.orientation = compiled.effectiveOrientation();
        }

        private StructureView.@Nullable CellInfo read(int x, int y, int z) {
            cursor.set(origin.getX() + orientation.transformX(x, y, z), origin.getY() + orientation.transformY(x, y, z), origin.getZ() + orientation.transformZ(x, y, z));
            StructureView.CellInfo info = view.cellInfo(cursor);
            if (info == null) {
                if (unloaded == null) unloaded = new LongOpenHashSet();
                unloaded.add(cursor.asLong());
            }
            return info;
        }

        int unloadedCount() {
            return unloaded == null ? 0 : unloaded.size();
        }

        int unknownCount() {
            return unloadedCount() + (unknownCells == null ? 0 : unknownCells.size());
        }

        private boolean probeCopy(BlueprintLayout layout, int brick, int copy) {
            return attempt(() -> layout.forEachCopy(brick, copy, (index, definition, x, y, z) -> {
                StructureView.CellInfo info = read(x, y, z);
                return info != null && check(info, compiled.definition(definition)).passed();
            }));
        }

        BlueprintLayout resolve() {
            DataTransaction scanData = metadata;
            metadata = metadata.fork();
            StructureMetadata.geometry(metadata, compiled.orientation(), blueprint.minRepeats(), part);
            // 先采集不会随增长移动的固定主体和最少重复副本。顶盖等依赖段只在候选事务中试读。
            if (!blueprint.metadataKeys().isEmpty()) seedMinimum();
            try {
                return resolveLayout();
            } finally {
                metadata = scanData;
            }
        }

        private void seedMinimum() {
            BlueprintLayout minimum = blueprint.layout(blueprint.minRepeats());
            List<Blueprint.Brick> bricks = blueprint.bricksInternal();
            for (int b = 0; b < bricks.size(); b++) {
                Blueprint.Brick brick = bricks.get(b);
                if (brick.section() < 0 && brick.dependencies().length > 0) continue;
                int copies = brick.section() < 0 ? 1 : minimum.repeats().count(brick.section());
                for (int copy = 0; copy < copies; copy++) minimum.forEachCopy(b, copy, (index, definition, x, y, z) -> {
                    StructureView.CellInfo info = read(x, y, z);
                    if (info != null) check(info, compiled.definition(definition));
                    return true;
                });
            }
        }

        private BlueprintLayout resolveLayout() {
            int[] counts = blueprint.minRepeats().countsCopy();
            List<Blueprint.Brick> bricks = blueprint.bricksInternal();
            int controller = blueprint.controllerBrick();
            // 控制器前方的段增长会改变声明锚点，先迭代至计数稳定。
            boolean changed;
            do {
                changed = false;
                for (int b = 0; b < controller; b++) {
                    int sectionIndex = bricks.get(b).section();
                    if (sectionIndex < 0) continue;
                    Blueprint.RepeatSection section = blueprint.section(sectionIndex);
                    while (counts[sectionIndex] < section.maxCount()) {
                        counts[sectionIndex]++;
                        BlueprintLayout candidate = blueprint.layout(new RepeatResolution(counts));
                        final int candidateBrick = b;
                        if (attempt(() -> {
                            StructureMetadata.geometry(metadata, compiled.orientation(), candidate.repeats(), part);
                            return probeCopy(candidate, candidateBrick, 0) && probeDependents(candidate, candidateBrick);
                        })) changed = true;
                        else {
                            counts[sectionIndex]--;
                            break;
                        }
                    }
                }
            } while (changed);
            // 控制器后的段按声明顺序贪心扩展，局部锚点不再随扩展改变。
            for (int b = controller + 1; b < bricks.size(); b++) {
                int sectionIndex = bricks.get(b).section();
                if (sectionIndex < 0) continue;
                Blueprint.RepeatSection section = blueprint.section(sectionIndex);
                while (counts[sectionIndex] < section.maxCount()) {
                    BlueprintLayout current = blueprint.layout(new RepeatResolution(counts));
                    final int candidateBrick = b;
                    final int copy = counts[sectionIndex];
                    counts[sectionIndex]++;
                    BlueprintLayout candidate = blueprint.layout(new RepeatResolution(counts));
                    if (!attempt(() -> {
                        StructureMetadata.geometry(metadata, compiled.orientation(), candidate.repeats(), part);
                        return probeCopy(current, candidateBrick, copy) && probeDependents(candidate, candidateBrick);
                    })) {
                        counts[sectionIndex]--;
                        break;
                    }
                }
            }
            return blueprint.layout(new RepeatResolution(counts));
        }

        /** 防止与重复单元同材质的固定顶盖被误吞为额外副本。 */
        private boolean probeDependents(BlueprintLayout candidate, int sectionBrick) {
            DataTransaction previous = metadata;
            metadata = metadata.fork();
            try {
                return probeDependentCells(candidate, sectionBrick);
            } finally {
                metadata = previous;
            }
        }

        private boolean probeDependentCells(BlueprintLayout candidate, int sectionBrick) {
            List<Blueprint.Brick> bricks = blueprint.bricksInternal();
            int sectionIndex = bricks.get(sectionBrick).section();
            Blueprint.RepeatSection section = blueprint.section(sectionIndex);
            boolean post = section.bandStart() >= 0;
            for (int b = 0; b < bricks.size(); b++) {
                Blueprint.Brick brick = bricks.get(b);
                if (brick.section() >= 0) continue;
                boolean otherDependency = false;
                for (int dependency : brick.dependencies()) {
                    if (dependency != sectionIndex) {
                        otherDependency = true;
                        break;
                    }
                }
                if (otherDependency) continue;
                final Blueprint.Brick fixed = brick;
                final int fixedBrick = b;
                boolean matched = attempt(() -> candidate.forEachCopy(fixedBrick, 0, (index, definition, x, y, z) -> {
                    int member = index - fixed.indexBase();
                    int declaredY = fixed.ys()[member];
                    if (post ? declaredY < section.bandEnd() : declaredY >= section.bandStart()) return true;
                    StructureView.CellInfo info = read(x, y, z);
                    return info != null && check(info, compiled.definition(definition)).passed();
                }));
                if (!matched) {
                    // 候选顶盖处也可能仍是更高结构的下一个重复单元。
                    return probeCopy(candidate, sectionBrick, post ? candidate.repeats().count(sectionIndex) : 0);
                }
            }
            return true;
        }

        private boolean attempt(BooleanSupplier probe) {
            DataTransaction previous = metadata;
            metadata = metadata.fork();
            boolean passed = false;
            try {
                passed = probe.getAsBoolean();
                return passed;
            } finally {
                if (passed) previous.accept(metadata);
                metadata = previous;
            }
        }

        private CellResult check(StructureView.CellInfo info, CompiledBlueprint.CompiledDefinition definition) {
            CellResult result = evaluateCompiled(info, view, cursor, definition, metadata);
            if (result.status() == CellResult.Status.UNKNOWN) {
                if (unknownCells == null) unknownCells = new LongOpenHashSet();
                unknownCells.add(cursor.asLong());
            }
            return result;
        }

        void scan(BlueprintLayout layout, Tally tally) {
            layout.forEachCell((index, definition, x, y, z) -> {
                StructureView.CellInfo info = read(x, y, z);
                tally.touch(cursor);
                CompiledBlueprint.CompiledDefinition expected = compiled.definition(definition);
                if (info == null) {
                    tally.unknown(cursor, x, y, z, expected.prototype(), null, CellResult.unloaded());
                    return tally.full;
                }
                CellResult result = check(info, expected);
                if (result.passed()) tally.matched(info);
                else if (result.status() == CellResult.Status.UNKNOWN) {
                    tally.unknown(cursor, x, y, z, expected.prototype(), info.state(), result);
                    if (!tally.full) return false;
                } else tally.missing(cursor, x, y, z, expected.prototype(), info.state(), result);
                return tally.full || !tally.mismatch;
            });
        }
    }

    /** 使用统一单元结果诊断指定位置；未加载不会被任何逻辑取反转换成成功。 */
    public static CellResult evaluateCompiled(StructureView view, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition, DataTransaction metadata) {
        StructureView.CellInfo info = view.cellInfo(worldPos);
        return info == null ? CellResult.unloaded() : evaluateCompiled(info, view, worldPos, definition, metadata);
    }

    private static CellResult evaluateCompiled(StructureView.CellInfo info, StructureView view, BlockPos worldPos,
                                               CompiledBlueprint.CompiledDefinition definition, DataTransaction metadata) {
        DataTransaction branch = metadata.fork();
        Cell cell = definition.prototype();
        CellResult predicate = cell.binding().evaluate(new CellContext(worldPos, info, view.isController(worldPos), definition.orientedExpected()), branch);
        if (!predicate.passed()) return predicate;
        BlockState[] acceptable = definition.acceptable();
        boolean stateMatches = acceptable == null ? matchesState(cell, definition.orientedExpected(), info.state()) : CompiledBlueprint.acceptedState(acceptable, info.state());
        if (!stateMatches) {
            BlockState actual = info.state();
            if (actual == null) return CellResult.unknown();
            String reason = actual == null || actual.isAir() ? "empty" : definition.orientedExpected() != null && actual.getBlock() == definition.orientedExpected().getBlock() ? "property" : "block";
            return CellResult.failure(reason, definition.orientedExpected(), actual);
        }
        metadata.accept(branch);
        return CellResult.pass();
    }

    /** 只将 COVARIANT 规则应用到旋转前的规范状态。FIXED 保持原值，DERIVED 的属性在比较时屏蔽；未固定状态时返回 null。 */
    static @Nullable BlockState orientExpected(Cell cell, Orientation orientation) {
        BlockState expected = cell.expectedState();
        if (expected == null) {
            return null;
        }
        for (PropertyRule rule : cell.rules()) {
            if (rule.classification() == PropertyRule.Classification.COVARIANT) {
                expected = rule.apply(orientation, expected);
            }
        }
        return expected;
    }

    /** 比较已变换的规范状态及规则提供的替代编码；单元检测和状态校验均成功才接纳元数据。 */
    private static boolean matchesState(Cell cell, @Nullable BlockState orientedExpected, @Nullable BlockState actual) {
        if (orientedExpected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (statesMatch(orientedExpected, actual, cell.maskedProperties())) {
            return true;
        }
        for (PropertyRule rule : cell.rules()) {
            for (BlockState alternate : rule.alternateEncodings(orientedExpected)) {
                if (statesMatch(alternate, actual, cell.maskedProperties())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 比较除 masked 集合之外的状态属性；集合在蓝图构建时预计算，本方法不分配屏蔽集合。 */
    public static boolean statesMatch(BlockState expected, BlockState actual, Set<Property<?>> masked) {
        if (expected.getBlock() != actual.getBlock()) {
            return false;
        }
        for (Property<?> property : expected.getProperties()) {
            if (masked.contains(property)) {
                continue;
            }
            if (!actual.hasProperty(property) || !expected.getValue(property).equals(actual.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    /** 快速检查结果。无单元格不匹配时 firstMismatchPos 为 null，世界原点是合法错误位置。 */
    public record Verification(boolean formed, RepeatResolution repeats, @Nullable BlockPos firstMismatchPos, boolean unloaded) {}

    /** 两种扫描模式共享计数；只有完整模式创建诊断集合。 */
    private static final class Tally {

        private final boolean full;
        private final CompiledBlueprint.PartRoleOrdinals counted;
        private final int[] counts;
        private final List<RecognitionResult.CellIssue> samples;
        private final List<RecognitionResult.CellIssue> unknownSamples;
        private final Map<PartRole, Integer> roles;
        private final List<BlockPos> members;
        private int matched;
        private int parts;
        private int missing;
        private boolean mismatch;
        private @Nullable BlockPos firstMismatch;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        Tally(CompiledBlueprint compiled, boolean full) {
            this.full = full;
            this.counted = compiled.countedRoles();
            this.counts = new int[counted.size()];
            this.samples = full ? new ArrayList<>() : List.of();
            this.unknownSamples = full ? new ArrayList<>() : List.of();
            this.roles = full ? new LinkedHashMap<>() : Map.of();
            this.members = full ? new ArrayList<>() : List.of();
        }

        void touch(BlockPos pos) {
            if (!full) return;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        void matched(StructureView.CellInfo info) {
            matched++;
            if (!info.roles().isEmpty()) parts++;
            for (PartRole role : info.roles()) {
                int ordinal = counted.ordinalOf(role);
                if (ordinal >= 0) counts[ordinal]++;
                if (full) roles.merge(role, 1, Integer::sum);
            }
            if (full && info.member() != null) members.add(info.member().pos());
        }

        void missing(BlockPos world, int x, int y, int z, Cell prototype, @Nullable BlockState actual, CellResult result) {
            mismatch = true;
            missing++;
            if (firstMismatch == null) firstMismatch = world.immutable();
            if (full && samples.size() < MISSING_SAMPLE_CAP) {
                BlockPos local = new BlockPos(x, y, z);
                Cell expected = new Cell(local, prototype.expectedState(), prototype.rules(), prototype.binding(), prototype.maskedProperties());
                samples.add(new RecognitionResult.CellIssue(local, world, expected, actual, result));
            }
        }

        void unknown(BlockPos world, int x, int y, int z, Cell prototype, @Nullable BlockState actual, CellResult result) {
            if (full && unknownSamples.size() < MISSING_SAMPLE_CAP) {
                BlockPos local = new BlockPos(x, y, z);
                unknownSamples.add(new RecognitionResult.CellIssue(local, world, prototype, actual, result));
            }
        }

        boolean countsSatisfied() {
            return counted.satisfied(counts);
        }

        RecognitionResult.Bounds bounds() {
            return minX > maxX ? RecognitionResult.Bounds.empty() : new RecognitionResult.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
