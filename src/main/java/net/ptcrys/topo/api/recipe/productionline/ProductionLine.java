package net.ptcrys.topo.api.recipe.productionline;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record ProductionLine(Identifier id) {

    public ProductionLine {
        Objects.requireNonNull(id, "production line id");
    }
}
