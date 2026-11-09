package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class ToolDataType extends MaterialDataType<ToolDataType.ToolData> {

    public record ToolData(int miningLevel, int durabilityBase) {

        public ToolData {
            if (miningLevel < 0) {
                throw new IllegalArgumentException("tool miningLevel must not be negative");
            }
            if (durabilityBase <= 0) {
                throw new IllegalArgumentException("tool durabilityBase must be positive");
            }
        }
    }

    public ToolDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<ToolData> tool(int miningLevel, int durabilityBase) {
        return use(new ToolData(miningLevel, durabilityBase));
    }
}
