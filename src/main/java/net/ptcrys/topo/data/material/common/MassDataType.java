package net.ptcrys.topo.data.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class MassDataType extends MaterialDataType<MassData> {

    public MassDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<MassData> mass(int mass) {
        return use(MassData.create(mass));
    }
}
