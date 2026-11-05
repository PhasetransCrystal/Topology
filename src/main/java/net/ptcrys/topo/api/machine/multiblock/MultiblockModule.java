package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;

import java.util.Objects;

/**
 * One named overlay blueprint on a multiblock controller. Modules share the controller anchor:
 * their local cells are overlaid on the same world positions, may use the same blocks as the main
 * blueprint or other modules, and are evaluated independently. A required module gates formation;
 * optional modules may be absent. Module order is declaration order and optional modules form a
 * prefix: once an optional module is absent, a later module may not be present.
 */
public record MultiblockModule(String id, boolean required, Blueprint blueprint) {

    public MultiblockModule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Multiblock module id must not be blank");
        }
        Objects.requireNonNull(blueprint, "module blueprint");
        if (blueprint.cells().size() <= 1) {
            throw new IllegalArgumentException(
                    "Multiblock module '" + id + "' must contain cells besides its controller anchor");
        }
    }

    public static MultiblockModule optional(String id, Blueprint blueprint) {
        return new MultiblockModule(id, false, blueprint);
    }

    public static MultiblockModule required(String id, Blueprint blueprint) {
        return new MultiblockModule(id, true, blueprint);
    }
}
