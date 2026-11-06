package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
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

/**
 * Pure, block-entity-free module evaluation shared by the controller and tests. Each module is
 * recognized by the same {@link StructureEngine} as the main structure; the controller cell is the
 * module anchor and is excluded from the reported matched/total. Modules may overlap the main
 * blueprint and each other. The class is deliberately a stateless collaborator (the machine system's
 * component pattern: heavy logic lives beside a thin component), so a unit test needs no level.
 */
public final class MultiblockModuleEvaluator {

    private MultiblockModuleEvaluator() {}

    /**
     * Evaluates every module for one orientation in dependency order. A module is {@code satisfied}
     * only when its own cells match <b>and</b> every dependency is satisfied; a partial or blocked
     * module is reported {@code ABSENT}. Unloaded cells mark the pass {@link Evaluation#unloaded()}.
     */
    public static Evaluation evaluateOverlays(
                                              StructureView view,
                                              List<MultiblockModule> moduleSpecs,
                                              Orientation orientation) {
        if (moduleSpecs.isEmpty()) {
            return new Evaluation(Map.of(), List.of(), Map.of(), List.of(), false);
        }
        List<MultiblockModule> ordered = topologicalOrder(moduleSpecs);
        Map<String, RecognitionResult.ModuleReport> computed = new LinkedHashMap<>();
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
            RecognitionResult result = null;
            boolean ownFormed = false;
            if (!(orientation.mirror() && !moduleBlueprint.allowMirror())) {
                result = StructureEngine.recognize(view, moduleBlueprint, orientation);
                if (result.failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) {
                    unloaded = true;
                }
                ownFormed = result.formed();
            }
            int matched;
            int total;
            RepeatResolution moduleRepeats;
            RecognitionResult.Bounds moduleBounds;
            if (result == null) {
                matched = 0;
                total = Math.max(0, moduleBlueprint.cellCount(moduleBlueprint.minRepeats()) - 1);
                moduleRepeats = moduleBlueprint.minRepeats();
                moduleBounds = RecognitionResult.Bounds.empty();
            } else {
                matched = Math.max(0, result.matchedCount() - 1);
                total = Math.max(0, result.matchedCount() + result.missingCount() + result.unloadedCount() - 1);
                moduleRepeats = result.repeats();
                moduleBounds = result.bounds();
            }
            boolean satisfied = ownFormed && blockedBy.isEmpty();
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
        return new Evaluation(reports, List.copyOf(members), membersByModule, order, unloaded);
    }

    /** Validates ids, dependency references and cycles; called at machine-definition time. */
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

    /** Result of evaluating every module for one orientation. */
    public record Evaluation(
                             Map<String, RecognitionResult.ModuleReport> reports,
                             List<BlockPos> members,
                             Map<String, List<BlockPos>> membersByModule,
                             List<String> order,
                             boolean unloaded) {

        public Evaluation {
            reports = Map.copyOf(Objects.requireNonNull(reports, "module reports"));
            members = List.copyOf(Objects.requireNonNull(members, "module members"));
            Map<String, List<BlockPos>> moduleMembers = new LinkedHashMap<>();
            for (Map.Entry<String, List<BlockPos>> entry : Objects.requireNonNull(membersByModule, "module members by module").entrySet()) {
                moduleMembers.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            membersByModule = Map.copyOf(moduleMembers);
            order = List.copyOf(Objects.requireNonNull(order, "module order"));
        }
    }
}
