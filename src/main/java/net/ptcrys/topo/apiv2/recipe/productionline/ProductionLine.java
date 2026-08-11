package net.ptcrys.topo.apiv2.recipe.productionline;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record ProductionLine(Identifier id) {

    public ProductionLine {
        Objects.requireNonNull(id, "production line id");
    }
}
