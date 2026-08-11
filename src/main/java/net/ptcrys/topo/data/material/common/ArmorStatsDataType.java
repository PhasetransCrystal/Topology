package net.ptcrys.topo.data.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

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
