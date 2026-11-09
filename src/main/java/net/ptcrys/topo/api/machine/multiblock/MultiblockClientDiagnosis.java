package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.BlueprintLayout;
import net.ptcrys.topo.api.machine.multiblock.pattern.Cell;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.PropertyRule;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;
import net.ptcrys.topo.api.machine.multiblock.ui.BlueprintPreview;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** 客户端结构诊断。使用与服务端相同的检测引擎，读取客户端已有状态，不请求加载区块；将引擎结论转换为问题行、统计和结构显示。 */
public final class MultiblockClientDiagnosis {

    /** 一条诊断：localPos 为空表示整体问题，title 用于行标题，message 用于提示，iconBlocks 保存可轮换的候选方块，图标物品栈由 UI 创建。 */
    public record Reason(
                         @Nullable BlockPos localPos,
                         Kind kind,
                         Component title,
                         Component message,
                         List<Block> iconBlocks, @Nullable CellResult cellResult) {

        public Reason(@Nullable BlockPos localPos, Kind kind, Component title, Component message, List<Block> iconBlocks) {
            this(localPos, kind, title, message, iconBlocks, null);
        }

        public Reason {
            Objects.requireNonNull(kind, "diagnosis kind");
            Objects.requireNonNull(title, "diagnosis title");
            Objects.requireNonNull(message, "diagnosis message");
            iconBlocks = List.copyOf(Objects.requireNonNull(iconBlocks, "diagnosis icon blocks"));
        }
    }

    /** 结构诊断报告。总位置数来自解析布局，匹配数直接来自引擎，partCount 按携带角色的位置去重。formed 保留引擎结论，未知或朝向不支持不能显示全部匹配。 */
    public record Report(
                         List<Reason> shown,
                         int totalProblems,
                         int matchedCells,
                         int totalCells,
                         int partCount,
                         long captureNanos,
                         long scanNanos,
                         boolean formed) {

        public Report {
            shown = List.copyOf(Objects.requireNonNull(shown, "shown reasons"));
        }

        public int hiddenProblems() {
            return Math.max(0, totalProblems - shown.size());
        }

        public boolean allMet() {
            return formed;
        }
    }

    public enum Kind {

        EMPTY_CELL,
        WRONG_BLOCK,
        WRONG_PROPERTY,
        MISSING_PART,
        EXCESS_PART,
        CHUNK_UNLOADED,
        CELL_UNKNOWN,
        CONSTRAINT_FAILED,
        ORIENTATION_UNSUPPORTED;

        /** 状态不符或区块未知使用等待颜色，其余问题使用错误颜色。 */
        public boolean warning() {
            return this == WRONG_PROPERTY || this == CHUNK_UNLOADED || this == CELL_UNKNOWN;
        }

        public LangKey headlineKey() {
            return switch (this) {
                case EMPTY_CELL -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_EMPTY_CELL;
                case WRONG_BLOCK -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_WRONG_BLOCK;
                case WRONG_PROPERTY -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_WRONG_PROPERTY;
                case MISSING_PART -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_MISSING_PART;
                case EXCESS_PART -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_EXCESS_PART;
                case CELL_UNKNOWN -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNKNOWN;
                case CONSTRAINT_FAILED -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_CONSTRAINT;
                case CHUNK_UNLOADED -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNLOADED;
                case ORIENTATION_UNSUPPORTED -> TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNSUPPORTED;
            };
        }

        public LangKey countKey() {
            return this == EXCESS_PART ? TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_EXCESS_COUNT : TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_MISSING_COUNT;
        }
    }

    private MultiblockClientDiagnosis() {}

    /** 在客户端世界线程完成一次诊断，报告、详情及实建图共用同一个检测朝向和视图。 */
    public static Frame capture(MachineBlockEntity controller, Blueprint blueprint) {
        Level level = Objects.requireNonNull(controller.getLevel(), "controller level");
        List<MultiblockModule> modules = controller.definition().metadata(MultiblockControllerMetadata.TYPE).stream().filter(metadata -> metadata.blueprint() == blueprint).findFirst().map(MultiblockControllerMetadata::modules).orElse(List.of());
        return capture(blueprint, modules, orientationOf(controller), (definition, orientation) -> definition.supportsSnapshot() ? StructureCaptures.capture(level, controller.getBlockPos(), definition, orientation) : new MultiblockLiveView(level, controller.getBlockPos()));
    }

    /** 本体与模块保留同一次整体检测的结果和视图，不重新识别模块，也不读取服务端专属运行态。 */
    static Frame capture(Blueprint blueprint, List<MultiblockModule> modules, Orientation primary, StructureDetection.Views capture) {
        List<Blueprint> definitions = new ArrayList<>(List.of(blueprint));
        modules.forEach(module -> definitions.add(module.blueprint()));
        long started = System.nanoTime();
        long[] captured = { 0 };
        Map<Blueprint, Map<Orientation, StructureView>> views = new LinkedHashMap<>();
        StructureDetection.Outcome detection = StructureDetection.detect((definition, orientation) -> {
            Map<Orientation, StructureView> byOrientation = views.computeIfAbsent(definition, unused -> new LinkedHashMap<>());
            return byOrientation.computeIfAbsent(orientation, unused -> {
                long before = System.nanoTime();
                StructureView view = capture.get(definition, orientation);
                captured[0] += System.nanoTime() - before;
                return view;
            });
        }, blueprint, modules, primary, StructureMetadata.defaults(definitions), false);
        Frame frame = new Frame(blueprint, views.get(blueprint).get(detection.orientation()), new AsyncStructureService.Outcome(detection.result(), detection.orientation()), captured[0], System.nanoTime() - started - captured[0]);
        frame.modules = modules.stream().map(module -> new Frame(module.blueprint(), views.get(module.blueprint()).get(detection.orientation()),
                new AsyncStructureService.Outcome(detection.modules().results().get(module.id()), detection.orientation()), 0, 0)).toList();
        return frame;
    }

    /** 按需采集主朝向与镜像，朝向优先级与服务端完全一致。采集耗时从检测耗时中扣除。 */
    static Frame capture(Blueprint blueprint, Orientation primary, Function<Orientation, StructureView> capture) {
        long started = System.nanoTime();
        long[] captureNanos = { 0 };
        StructureView[] selectedView = { null };
        Function<Orientation, StructureView> timedCapture = orientation -> {
            long before = System.nanoTime();
            StructureView view = Objects.requireNonNull(capture.apply(orientation), "diagnosis view");
            captureNanos[0] += System.nanoTime() - before;
            return view;
        };
        StructureView primaryView = timedCapture.apply(primary);
        AsyncStructureService.Outcome outcome = AsyncStructureService.recognizePreferringMirror(primaryView, blueprint, primary,
                () -> selectedView[0] = timedCapture.apply(primary.mirrored()));
        StructureView chosen = outcome.orientation().equals(primary) ? primaryView : Objects.requireNonNull(selectedView[0]);
        return new Frame(blueprint, chosen, outcome, captureNanos[0], System.nanoTime() - started - captureNanos[0]);
    }

    /**
     * 一次刷新内共用的诊断上下文。稳定布局持有不可变快照；移动布局持有实时视图，须在同一客户端世界线程及时使用。
     * 布局与朝向来自本次识别，选中位置不会再次扫描整份蓝图，也不会退回主朝向。
     */
    public static final class Frame {

        private final Blueprint blueprint;
        private final StructureView view;
        private final AsyncStructureService.Outcome outcome;
        private final BlueprintLayout layout;
        private final long captureNanos;
        private final long scanNanos;
        private List<Frame> modules = List.of();
        private @Nullable MachineDefinition previewController;
        private @Nullable BlueprintPreview preview;

        private Frame(Blueprint blueprint, StructureView view, AsyncStructureService.Outcome outcome, long captureNanos, long scanNanos) {
            this.blueprint = blueprint;
            this.view = view;
            this.outcome = outcome;
            this.layout = blueprint.layout(outcome.result().repeats());
            this.captureNanos = captureNanos;
            this.scanNanos = scanNanos;
        }

        /** 引擎选择的控制器朝向，可能为镜像；尚未与蓝图声明 frame 合成。 */
        public Orientation orientation() {
            return outcome.orientation();
        }

        private Orientation previewTransform() {
            return BlueprintPreview.previewTransform(blueprint, orientation().mirror());
        }

        private Orientation controllerRotation() {
            return orientation().mirror() ? orientation().mirrored() : orientation();
        }

        private BlockPos declaredPos(BlockPos preview) {
            return previewTransform().inverse().apply(preview, BlockPos.ZERO);
        }

        /** 将本次结果转换为有界的问题列表。 */
        public Report report(int displayMax) {
            return MultiblockClientDiagnosis.report(blueprint, outcome.result(), displayMax, captureNanos, scanNanos, previewTransform());
        }

        /** 独立检测选中位置，缺失样本的 64 项上限不影响详情。 */
        public @Nullable Reason diagnoseCell(BlockPos localPos) {
            BlockPos declared = declaredPos(localPos);
            Reason own = layout.cellIndexAt(declared.getX(), declared.getY(), declared.getZ()) < 0 ? null :
                    MultiblockClientDiagnosis.diagnoseCell(view, blueprint, orientation(), outcome.result().repeats(), declared, outcome.result().metadata().verify(), localPos);
            if (own != null) return own;
            for (Frame module : modules) if (module.containsCell(localPos)) {
                Reason reason = module.diagnoseCell(localPos);
                if (reason != null) return reason;
            }
            return null;
        }

        /** 选中位置是否仍属于本次识别的布局。 */
        public boolean containsCell(BlockPos localPos) {
            BlockPos declared = declaredPos(localPos);
            return layout.cellIndexAt(declared.getX(), declared.getY(), declared.getZ()) >= 0 || modules.stream().anyMatch(module -> module.containsCell(localPos));
        }

        /** 读取已搭建结构，空间属性按本次有效旋转折回声明坐标；未知与空气位置不显示。 */
        public Map<BlockPos, BlockState> detectedBlocks() {
            Map<BlockPos, BlockState> detected = new LinkedHashMap<>(detectedBlocks(outcome.result().repeats()));
            for (Frame module : modules) module.detectedBlocks().forEach(detected::putIfAbsent);
            return Map.copyOf(detected);
        }

        /** 在指定预览布局内读取实建状态，允许场景固定自身的显示范围，朝向仍使用本次识别结论。 */
        public Map<BlockPos, BlockState> detectedBlocks(RepeatResolution resolution) {
            Map<BlockPos, BlockState> detected = new LinkedHashMap<>();
            Orientation effective = blueprint.effectiveOrientation(orientation());
            Orientation inverse = controllerRotation().inverse();
            BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
            blueprint.layout(resolution).forEachCell((index, definition, x, y, z) -> {
                BlockPos localPos = new BlockPos(x, y, z);
                effective.apply(localPos, view.controllerPos(), worldPos);
                BlockState state = view.isLoaded(worldPos) ? view.blockState(worldPos) : null;
                if (state != null && !state.isAir()) detected.put(previewTransform().apply(localPos, BlockPos.ZERO), localPos.equals(BlockPos.ZERO) ? BlueprintPreview.controllerPreviewStateFrom(state) : Orientations.rotated(inverse, state));
                return true;
            });
            return detected;
        }

        /** 全部实际解析位置，包含空气；模块使用各自 frame 和重复次数，共享整体镜像。 */
        public Set<BlockPos> positions() {
            Set<BlockPos> positions = new LinkedHashSet<>();
            layout.forEachCell((index, definition, x, y, z) -> {
                positions.add(previewTransform().apply(new BlockPos(x, y, z), BlockPos.ZERO));
                return true;
            });
            for (Frame module : modules) positions.addAll(module.positions());
            return Set.copyOf(positions);
        }

        /** 所需图采用本次识别的本体及全部模块布局，和实建图的范围保持一致。 */
        public BlueprintPreview requiredPreview(MachineDefinition controller) {
            if (preview != null && previewController == controller) return preview;
            List<BlueprintPreview> previews = new ArrayList<>();
            previews.add(BlueprintPreview.of(blueprint, controller, outcome.result().repeats(), orientation().mirror()));
            for (Frame module : modules) previews.add(module.requiredPreview(controller));
            previewController = controller;
            preview = BlueprintPreview.combine(previews);
            return preview;
        }

        /** 取得已旋转的固定预期状态，优先显示与实际状态相符的等价编码。 */
        public @Nullable BlockState expectedStateAt(BlockPos localPos) {
            BlockPos declared = declaredPos(localPos);
            int index = layout.cellIndexAt(declared.getX(), declared.getY(), declared.getZ());
            if (index < 0) {
                for (Frame module : modules) {
                    BlockState expected = module.expectedStateAt(localPos);
                    if (expected != null) return expected;
                }
                return null;
            }
            if (!blueprint.supports(orientation())) return null;
            CompiledBlueprint.CompiledDefinition definition = blueprint.compiled(orientation()).definitionAt(index);
            BlockState expected = definition.orientedExpected();
            return expected == null ? null : bestEncodingFor(definition.prototype(), expected, foundBlockAt(localPos));
        }

        /** 使用本次选定的朝向读取实际状态，布局外或未加载位置返回 null。 */
        public @Nullable BlockState foundBlockAt(BlockPos localPos) {
            BlockPos declared = declaredPos(localPos);
            if (layout.cellIndexAt(declared.getX(), declared.getY(), declared.getZ()) < 0) {
                for (Frame module : modules) if (module.containsCell(localPos)) return module.foundBlockAt(localPos);
                return null;
            }
            // 预览坐标已经应用各自 frame；转回世界只需控制器的旋转，不能再次套用本体 frame。
            BlockPos worldPos = controllerRotation().apply(localPos, view.controllerPos());
            return view.isLoaded(worldPos) ? view.blockState(worldPos) : null;
        }
    }

    /** 将引擎结果转换为诊断，不把未知位置当作匹配，也不将额外增长探测加入布局总数。 */
    static Report report(Blueprint blueprint, RecognitionResult result, int displayMax, long captureNanos, long scanNanos) {
        return report(blueprint, result, displayMax, captureNanos, scanNanos, blueprint.frame());
    }

    private static Report report(Blueprint blueprint, RecognitionResult result, int displayMax, long captureNanos, long scanNanos, Orientation previewTransform) {
        if (displayMax < 0) throw new IllegalArgumentException("Display limit must be non-negative");
        List<Reason> all = new ArrayList<>();
        for (RecognitionResult.CellIssue missing : result.missingCells()) all.add(cellReason(new RecognitionResult.CellIssue(
                previewTransform.apply(missing.localPos(), BlockPos.ZERO), missing.worldPos(), missing.expected(), missing.actual(), missing.result())));
        int countProblems = 0;
        if (result.failureReason() == RecognitionResult.FailureReason.NONE) {
            for (Blueprint.CountRequirement requirement : blueprint.countRequirements().values()) {
                int count = result.roleCounts().getOrDefault(requirement.role(), 0);
                if (count < requirement.min()) {
                    all.add(countReason(Kind.MISSING_PART, requirement.role(), count, requirement.min()));
                    countProblems++;
                } else if (count > requirement.max()) {
                    all.add(countReason(Kind.EXCESS_PART, requirement.role(), count, requirement.max()));
                    countProblems++;
                }
            }
        }
        int blockers = 0;
        if (result.failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED || result.failureReason() == RecognitionResult.FailureReason.ORIENTATION_UNSUPPORTED) {
            all.add(blockedReason(result.failureReason(), result.unloadedCount(), null));
            blockers = 1;
        }
        int externalUnknown = result.unknownCount() - result.unloadedCount();
        if (externalUnknown > 0) {
            int before = all.size();
            for (var issue : result.unknownCells()) if (!issue.result().hasCode("unloaded")) {
                all.add(new Reason(previewTransform.apply(issue.localPos(), BlockPos.ZERO), Kind.CELL_UNKNOWN,
                        Kind.CELL_UNKNOWN.headlineKey().getComponent(), describe(issue.result()), List.of(), issue.result()));
            }
            if (all.size() == before) all.add(blockedReason(RecognitionResult.FailureReason.CELL_UNKNOWN, externalUnknown, null));
            blockers += externalUnknown;
        }
        return new Report(sample(all, displayMax), result.missingCount() + countProblems + blockers,
                result.matchedCount(), blueprint.cellCount(result.repeats()), result.partCount(), captureNanos, scanNanos, result.formed());
    }

    private static Reason blockedReason(RecognitionResult.FailureReason reason, int unloaded, @Nullable BlockPos localPos) {
        Kind kind = reason == RecognitionResult.FailureReason.CHUNK_UNLOADED ? Kind.CHUNK_UNLOADED : reason == RecognitionResult.FailureReason.CELL_UNKNOWN ? Kind.CELL_UNKNOWN : Kind.ORIENTATION_UNSUPPORTED;
        Component message = kind == Kind.CHUNK_UNLOADED ? TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNLOADED_COUNT.getComponent(unloaded) : kind == Kind.CELL_UNKNOWN ? TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNKNOWN.getComponent() : TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNSUPPORTED_DETAIL.getComponent();
        return new Reason(localPos, kind, kind.headlineKey().getComponent(), message, List.of());
    }

    /** 有界分类采样：先保留每个类别的首项，再按原顺序补齐，不改变最终相对顺序。 */
    private static List<Reason> sample(List<Reason> all, int displayMax) {
        if (all.size() <= displayMax) {
            return all;
        }
        boolean[] picked = new boolean[all.size()];
        Set<Kind> seenKinds = EnumSet.noneOf(Kind.class);
        int remaining = displayMax;
        for (int i = 0; i < all.size() && remaining > 0; i++) {
            if (seenKinds.add(all.get(i).kind())) {
                picked[i] = true;
                remaining--;
            }
        }
        for (int i = 0; i < all.size() && remaining > 0; i++) {
            if (!picked[i]) {
                picked[i] = true;
                remaining--;
            }
        }
        List<Reason> shown = new ArrayList<>(displayMax);
        for (int i = 0; i < all.size(); i++) {
            if (picked[i]) {
                shown.add(all.get(i));
            }
        }
        return shown;
    }

    private static Reason countReason(Kind kind, PartRole capability, int have, int bound) {
        Component name = capabilityName(capability);
        return new Reason(
                null,
                kind,
                name,
                kind.countKey().getComponent(name, have, bound),
                CellPredicates.role(capability).candidateBlocks());
    }

    static @Nullable Reason diagnoseCell(StructureView view, Blueprint blueprint, Orientation orientation, RepeatResolution resolution, BlockPos localPos) {
        return diagnoseCell(view, blueprint, orientation, resolution, localPos, StructureEngine.recognize(view, blueprint, orientation, resolution).metadata().verify(), localPos);
    }

    private static @Nullable Reason diagnoseCell(StructureView view, Blueprint blueprint, Orientation orientation, RepeatResolution resolution, BlockPos localPos, DataTransaction metadata, BlockPos displayPos) {
        int index = blueprint.layout(resolution).cellIndexAt(localPos.getX(), localPos.getY(), localPos.getZ());
        if (index < 0) return null;
        if (!blueprint.supports(orientation)) return blockedReason(RecognitionResult.FailureReason.ORIENTATION_UNSUPPORTED, 0, displayPos);
        BlockPos worldPos = blueprint.effectiveOrientation(orientation).apply(localPos, view.controllerPos());
        if (!view.isLoaded(worldPos)) return blockedReason(RecognitionResult.FailureReason.CHUNK_UNLOADED, 1, displayPos);
        CompiledBlueprint.CompiledDefinition definition = blueprint.compiled(orientation).definitionAt(index);
        CellResult result = StructureEngine.evaluateCompiled(view, worldPos, definition, metadata);
        if (result.passed()) return null;
        if (result.status() == CellResult.Status.UNKNOWN) return new Reason(displayPos, Kind.CELL_UNKNOWN, Kind.CELL_UNKNOWN.headlineKey().getComponent(), describe(result), List.of(), result);
        Cell prototype = definition.prototype();
        Cell expected = new Cell(localPos, prototype.expectedState(), prototype.rules(), prototype.binding(), prototype.maskedProperties());
        return cellReason(new RecognitionResult.CellIssue(displayPos, worldPos, expected, view.blockState(worldPos), result));
    }

    /** 在实际状态已匹配某种替代编码时显示该编码，屏蔽属性的比较与引擎共用实现。 */
    private static BlockState bestEncodingFor(Cell cell, BlockState expected, @Nullable BlockState actual) {
        if (actual == null || StructureEngine.statesMatch(expected, actual, cell.maskedProperties())) {
            return expected;
        }
        for (PropertyRule rule : cell.rules()) {
            for (BlockState alternate : rule.alternateEncodings(expected)) {
                if (StructureEngine.statesMatch(alternate, actual, cell.maskedProperties())) {
                    return alternate;
                }
            }
        }
        return expected;
    }

    /** 预期方块名称：固定状态优先，否则使用首个候选方块名称。无需访问物品组件。 */
    private static Component expectedName(Cell expected) {
        BlockState pinned = expected.expectedState();
        if (pinned != null) {
            return pinned.getBlock().getName();
        }
        List<Block> candidates = expected.binding().candidateBlocks();
        if (!candidates.isEmpty()) {
            return candidates.getFirst().getName();
        }
        return TopoApiLang.UI_MULTIBLOCK_NONE.getComponent();
    }

    /** 预期图标的方块声明，固定状态优先；不创建物品栈，也不触发物品组件访问。 */
    private static @Nullable Block expectedIconBlock(Cell expected) {
        BlockState pinned = expected.expectedState();
        if (pinned != null) return pinned.getBlock();
        List<Block> candidates = expected.binding().candidateBlocks();
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private static Orientation orientationOf(MachineBlockEntity controller) {
        BlockState state = controller.getBlockState();
        if (state.hasProperty(DirectionalBlock.FACING)) {
            return Orientations.forFacing(state.getValue(DirectionalBlock.FACING));
        }
        return Orientations.IDENTITY;
    }

    private static Reason cellReason(RecognitionResult.CellIssue missing) {
        Kind kind = classify(missing);
        // Structured emphasis (tooltip sentence): the expected block carries the severity color,
        // the found-block and coordinate parts stay muted.
        int severity = kind.warning() ? MachineUiComponentStyle.INSTANCE.getLedWaiting() : MachineUiComponentStyle.INSTANCE.getLedError();
        Component title = kind == Kind.CONSTRAINT_FAILED ? errorTitle(missing.result()) : expectedName(missing.expected());
        Component expected = tinted(title, severity);
        int muted = MachineUiComponentStyle.INSTANCE.getTextMuted();
        BlockState actual = missing.actual();
        Component message;
        if (kind == Kind.EMPTY_CELL || actual == null) {
            message = tinted(TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_NEED.getComponent(
                    expected,
                    coords(missing.localPos())), muted);
        } else {
            message = tinted(TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_NEED_FOUND.getComponent(
                    expected,
                    actual.getBlock().getName(),
                    coords(missing.localPos())), muted);
        }
        if (kind == Kind.CONSTRAINT_FAILED || kind == Kind.WRONG_PROPERTY) message = message.copy().append("\n").append(describe(missing.result()));
        Block icon = expectedIconBlock(missing.expected());
        return new Reason(
                missing.localPos(), kind, title, message, icon == null ? List.of() : List.of(icon), missing.result());
    }

    /** 外部错误使用约定翻译键；无翻译时保留原错误码，避免丢失扩展能力提供的原因。 */
    private static Component errorTitle(CellResult result) {
        if (!result.code().getNamespace().equals(Topology.MODID)) {
            return Component.translatableWithFallback("multiblock.cell." + result.code().getNamespace() + "." + result.code().getPath().replace('/', '.'), result.code().toString());
        }
        for (CellResult child : result.children()) if (!child.code().getNamespace().equals(Topology.MODID)) return errorTitle(child);
        return TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_CONSTRAINT.getComponent();
    }

    /** 报告与工具共用的结构化错误详情，不重新运行检测器推断错误。 */
    public static Component describe(CellResult result) {
        var text = Component.empty();
        appendIssue(text, result, new int[] { 8 });
        return text;
    }

    private static void appendIssue(MutableComponent text, CellResult result, int[] remaining) {
        if (remaining[0]-- <= 0) return;
        if (!text.getString().isEmpty()) text.append("\n");
        text.append(errorTitle(result)).append(" [" + result.code() + "]");
        if (!result.expected().isEmpty()) text.append(" " + result.expected());
        if (!result.actual().isEmpty()) text.append(" / " + result.actual());
        for (CellResult child : result.children()) appendIssue(text, child, remaining);
    }

    /** 给组件根节点着色，已有独立颜色的翻译参数保留自己的颜色。 */
    private static Component tinted(Component text, int color) {
        return text.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color & 0xFFFFFF)));
    }

    private static Kind classify(RecognitionResult.CellIssue missing) {
        CellResult result = missing.result();
        if (result.hasCode("empty")) return Kind.EMPTY_CELL;
        if (result.hasCode("property")) return Kind.WRONG_PROPERTY;
        if (result.hasCode("block") || result.hasCode("role") || result.hasCode("member") || result.hasCode("controller")) return Kind.WRONG_BLOCK;
        return Kind.CONSTRAINT_FAILED;
    }

    private static Component coords(BlockPos localPos) {
        return Component.literal(localPos.getX() + "," + localPos.getY() + "," + localPos.getZ());
    }

    private static Component capabilityName(PartRole capability) {
        return capability.displayName();
    }
}
