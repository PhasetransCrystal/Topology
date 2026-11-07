package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 无世界副作用的模块求值器。每个模块都使用 StructureEngine 识别，控制器 @ 作为共用锚点，但不计入模块匹配数和总数。
 * 模块可重叠，按依赖拓扑顺序求值；输入视图的线程约束与检测引擎相同。
 */
public final class MultiblockModuleEvaluator {

    private MultiblockModuleEvaluator() {}

    /**
     * 在同一控制器朝向下求值所有模块。自身完整且依赖满足才接入；部分或被依赖阻塞的模块为缺失。
     * 总数来自实际解析布局，不包含额外增长探测位置；任一未加载探测使 Evaluation.unloaded 为真。
     */
    public static Evaluation evaluateOverlays(
                                              StructureView view,
                                              List<MultiblockModule> moduleSpecs,
                                              Orientation orientation) {
        return evaluateOverlays(blueprint -> view, moduleSpecs, orientation, StructureMetadata.defaults(moduleSpecs.stream().map(MultiblockModule::blueprint).toList()).edit(), members -> true);
    }

    /** 本体与模块共享元数据；可选模块失败、依赖阻塞或不能接入时，整份模块写入回滚。 */
    public static Evaluation evaluateOverlays(Function<Blueprint, StructureView> views, List<MultiblockModule> moduleSpecs,
                                              Orientation orientation, DataTransaction metadata, Predicate<List<BlockPos>> canAttach) {
        if (moduleSpecs.isEmpty()) {
            return new Evaluation(Map.of(), List.of(), Map.of(), List.of(), false, metadata.snapshot(), Map.of());
        }
        List<MultiblockModule> ordered = topologicalOrder(moduleSpecs);
        Map<String, RecognitionResult.ModuleReport> computed = new LinkedHashMap<>();
        Map<String, RecognitionResult> results = new LinkedHashMap<>();
        Map<String, Boolean> satisfiedById = new HashMap<>();
        Map<String, List<BlockPos>> membersByModule = new LinkedHashMap<>();
        List<BlockPos> members = new ArrayList<>();
        boolean unloaded = false;
        for (MultiblockModule module : ordered) {
            Blueprint moduleBlueprint = module.blueprint();
            List<String> blockedBy = new ArrayList<>();
            for (String dependency : module.dependencies()) {
                if (!satisfiedById.getOrDefault(dependency, false)) {
                    blockedBy.add(dependency);
                }
            }
            DataTransaction branch = metadata.fork();
            RecognitionResult result = StructureEngine.recognize(views.apply(moduleBlueprint), moduleBlueprint, orientation, branch, "module/" + module.id());
            results.put(module.id(), result);
            if (result.unknown()) unloaded = true;
            boolean ownFormed = result.formed();
            int matched = Math.max(0, result.matchedCount() - 1);
            int total = Math.max(0, moduleBlueprint.cellCount(result.repeats()) - 1);
            RepeatResolution moduleRepeats = result.repeats();
            RecognitionResult.Bounds moduleBounds = result.bounds();
            boolean satisfied = ownFormed && blockedBy.isEmpty() && canAttach.test(result.members());
            if (satisfied) metadata.accept(branch);
            computed.put(module.id(), new RecognitionResult.ModuleReport(module.id(), satisfied, matched, total, blockedBy, moduleRepeats, moduleBounds));
            satisfiedById.put(module.id(), satisfied);
            if (satisfied) {
                List<BlockPos> moduleMembers = List.copyOf(result.members());
                membersByModule.put(module.id(), moduleMembers);
                members.addAll(moduleMembers);
            }
        }
        Map<String, RecognitionResult.ModuleReport> reports = new LinkedHashMap<>();
        for (MultiblockModule module : moduleSpecs) {
            reports.put(module.id(), computed.get(module.id()));
        }
        List<String> order = ordered.stream().map(MultiblockModule::id).toList();
        return new Evaluation(reports, List.copyOf(members), membersByModule, order, unloaded, metadata.snapshot(), results);
    }

    /** 在挂载时校验重复 ID、未知依赖和依赖环。 */
    public static void validateGraph(List<MultiblockModule> moduleSpecs) {
        topologicalOrder(moduleSpecs);
    }

    private static List<MultiblockModule> topologicalOrder(List<MultiblockModule> moduleSpecs) {
        Map<String, MultiblockModule> byId = new LinkedHashMap<>();
        for (MultiblockModule module : moduleSpecs) {
            if (byId.put(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate multiblock module id: " + module.id());
            }
        }
        List<MultiblockModule> ordered = new ArrayList<>(moduleSpecs.size());
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (MultiblockModule module : moduleSpecs) {
            visitModule(module, byId, visiting, visited, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visitModule(
                                    MultiblockModule module,
                                    Map<String, MultiblockModule> byId,
                                    Set<String> visiting,
                                    Set<String> visited,
                                    List<MultiblockModule> ordered) {
        if (visited.contains(module.id())) {
            return;
        }
        if (!visiting.add(module.id())) {
            throw new IllegalArgumentException("Multiblock module dependency cycle at '" + module.id() + "'");
        }
        for (String dependency : module.dependencies()) {
            MultiblockModule target = byId.get(dependency);
            if (target == null) {
                throw new IllegalArgumentException(
                        "Multiblock module '" + module.id() + "' depends on unknown module '" + dependency + "'");
            }
            visitModule(target, byId, visiting, visited, ordered);
        }
        visiting.remove(module.id());
        visited.add(module.id());
        ordered.add(module);
    }

    /** 全部模块的不可变求值结果。 */
    public record Evaluation(
                             Map<String, RecognitionResult.ModuleReport> reports,
                             List<BlockPos> members,
                             Map<String, List<BlockPos>> membersByModule,
                             List<String> order,
                             boolean unloaded, DataSnapshot metadata,
                             Map<String, RecognitionResult> results) {

        public Evaluation(Map<String, RecognitionResult.ModuleReport> reports, List<BlockPos> members,
                          Map<String, List<BlockPos>> membersByModule, List<String> order, boolean unloaded, DataSnapshot metadata) {
            this(reports, members, membersByModule, order, unloaded, metadata, Map.of());
        }

        public Evaluation {
            reports = Map.copyOf(Objects.requireNonNull(reports, "module reports"));
            members = List.copyOf(Objects.requireNonNull(members, "module members"));
            Map<String, List<BlockPos>> moduleMembers = new LinkedHashMap<>();
            for (Map.Entry<String, List<BlockPos>> entry : Objects.requireNonNull(membersByModule, "module members by module").entrySet()) {
                moduleMembers.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            membersByModule = Map.copyOf(moduleMembers);
            order = List.copyOf(Objects.requireNonNull(order, "module order"));
            results = Map.copyOf(Objects.requireNonNull(results, "module results"));
        }
    }
}
