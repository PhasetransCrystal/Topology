package net.ptcrys.topo.datav2.material.common;

import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class RgbColorDataType extends MaterialDataType<RgbColorData> {

    public RgbColorDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<RgbColorData> rgb(int rgb) {
        return use(RgbColorData.create(rgb));
    }
}
