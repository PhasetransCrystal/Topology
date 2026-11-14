package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 共用控制器锚点的可选叠加蓝图。可独立使用 frame、重复组、谓词和数量约束，允许与主体或其他模块重叠，尺寸无需一致。
 * 只有自身完整且所有依赖均满足时才接入成员；部分搭建或依赖缺失均视为模块缺失，不阻止主体成型。
 */
public record MultiblockModule(String id, Blueprint blueprint, List<String> dependencies) {

    public MultiblockModule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Multiblock module id must not be blank");
        }
        Objects.requireNonNull(blueprint, "module blueprint");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "module dependencies"));
        Set<String> seen = new HashSet<>();
        for (String dependency : dependencies) {
            if (dependency == null || dependency.isBlank()) {
                throw new IllegalArgumentException("Multiblock module '" + id + "' has a blank dependency id");
            }
            if (dependency.equals(id)) {
                throw new IllegalArgumentException("Multiblock module '" + id + "' cannot depend on itself");
            }
            if (!seen.add(dependency)) {
                throw new IllegalArgumentException(
                        "Multiblock module '" + id + "' declares dependency '" + dependency + "' more than once");
            }
        }
        if (blueprint.cellCount(blueprint.minRepeats()) <= 1 && blueprint.sectionCount() == 0) {
            throw new IllegalArgumentException(
                    "Multiblock module '" + id + "' must contain cells besides its controller anchor");
        }
    }

    /** 声明一个可选模块，并指定其依赖的模块 ID。 */
    public static MultiblockModule of(String id, Blueprint blueprint, String... dependsOn) {
        return new MultiblockModule(id, blueprint, List.of(dependsOn));
    }
}
