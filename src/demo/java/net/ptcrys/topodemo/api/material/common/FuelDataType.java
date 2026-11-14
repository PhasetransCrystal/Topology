package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class FuelDataType extends MaterialDataType<FuelDataType.FuelData> {

    public record FuelData(int burnTicks, int burnTemperature) {

        public FuelData {
            if (burnTicks <= 0) {
                throw new IllegalArgumentException("fuel burnTicks must be positive");
            }
            if (burnTemperature < 0) {
                throw new IllegalArgumentException("fuel burnTemperature must not be negative");
            }
        }
    }

    public FuelDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<FuelData> fuel(int burnTicks, int burnTemperature) {
        return use(new FuelData(burnTicks, burnTemperature));
    }
}
