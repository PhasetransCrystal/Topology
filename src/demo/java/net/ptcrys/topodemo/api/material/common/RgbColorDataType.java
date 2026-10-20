package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

/** 材料主/副色数据类型:引用它的形态渲染依据该颜色着色。 */
public final class RgbColorDataType extends MaterialDataType<RgbColorData> {

    public RgbColorDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<RgbColorData> rgb(int rgb) {
        return use(RgbColorData.create(rgb));
    }
}
