package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

/** A small typed material payload for exercising the foundation/data freeze boundary. */
public final class MassDataType extends MaterialDataType<Integer> {

    public MassDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<Integer> mass(int value) {
        if (value <= 0) throw new IllegalArgumentException("Material mass must be positive");
        return use(value);
    }
}
