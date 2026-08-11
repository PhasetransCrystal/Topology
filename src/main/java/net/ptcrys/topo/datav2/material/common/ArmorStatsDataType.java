package net.ptcrys.topo.datav2.material.common;

import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

public final class ArmorStatsDataType extends MaterialDataType<ArmorStatsData> {

    public ArmorStatsDataType(Identifier id) {
        super(id);
    }

    /** See {@link ArmorStatsData} for parameter semantics; values are validated at this line. */
    public MaterialDataUse<ArmorStatsData> stats(int durabilityMultiplier, int helmetDefense,
                                                 int chestplateDefense, int leggingsDefense, int bootsDefense, float toughness,
                                                 float knockbackResistance, int enchantmentValue) {
        return use(new ArmorStatsData(durabilityMultiplier, helmetDefense, chestplateDefense,
                leggingsDefense, bootsDefense, toughness, knockbackResistance, enchantmentValue));
    }
}
