package net.ptcrys.topo.data.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class RgbColorDataType extends MaterialDataType<RgbColorData> {

    public RgbColorDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<RgbColorData> rgb(int rgb) {
        return use(RgbColorData.create(rgb));
    }
}
