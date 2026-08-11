package net.ptcrys.topo.datav2.material.common;

import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class MassDataType extends MaterialDataType<MassData> {

    public MassDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<MassData> mass(int mass) {
        return use(MassData.create(mass));
    }
}
