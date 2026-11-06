package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One optional overlay blueprint on a multiblock controller.
 *
 * <p>
 * A module is an <b>independent blueprint</b> anchored at the main controller's {@code @}: it uses the
 * full blueprint DSL (frame, repeat sections, predicates, count requirements) and is recognized by
 * the same {@link net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine} as the main
 * structure. Modules may overlap the main blueprint and each other; their dimensions do not need to
 * match.
 *
 * <p>
 * Every module is optional: it never gates the main structure. A module is either fully satisfied or
 * considered absent (a partial build counts as absent). {@link #dependencies()} are presence
 * prerequisites: a module can only be satisfied when every dependency is satisfied. The controller
 * connects a module's machine parts only when the module and all its prerequisites are satisfied.
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
        if (blueprint.cells().size() <= 1 && blueprint.sectionCount() == 0) {
            throw new IllegalArgumentException(
                    "Multiblock module '" + id + "' must contain cells besides its controller anchor");
        }
    }

    /** An optional module blueprint that depends on the given module ids. */
    public static MultiblockModule of(String id, Blueprint blueprint, String... dependsOn) {
        return new MultiblockModule(id, blueprint, List.of(dependsOn));
    }
}
