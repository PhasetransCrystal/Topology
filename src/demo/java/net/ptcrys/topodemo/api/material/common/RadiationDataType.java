package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class RadiationDataType extends MaterialDataType<RadiationDataType.RadiationData> {

    public record RadiationData(int intensity) {

        public RadiationData {
            if (intensity < 0) {
                throw new IllegalArgumentException("radiation intensity must not be negative");
            }
        }
    }

    public RadiationDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<RadiationData> radiation(int intensity) {
        return use(new RadiationData(intensity));
    }
}
