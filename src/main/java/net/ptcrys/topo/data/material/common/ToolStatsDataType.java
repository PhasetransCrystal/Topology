package net.ptcrys.topo.data.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ToolStatsDataType extends MaterialDataType<ToolStatsData> {

    public ToolStatsDataType(Identifier id) {
        super(id);
    }

    /** See {@link ToolStatsData} for parameter semantics; values are validated at this line. */
    public MaterialDataUse<ToolStatsData> stats(int durability, float miningSpeed, float attackBonus,
                                                int enchantmentValue, TagKey<Block> incorrectForDrops) {
        return use(new ToolStatsData(durability, miningSpeed, attackBonus, enchantmentValue,
                incorrectForDrops));
    }
}
