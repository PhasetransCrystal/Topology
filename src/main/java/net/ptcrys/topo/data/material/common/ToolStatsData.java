package net.ptcrys.topo.data.material.common;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Tool-grade numbers a material contributes to every tool kind made from it. Declaring this data
 * on a material is what makes the material's tools exist; the equipment domain derives one vanilla
 * {@code ToolMaterial} per (material, tool kind) from these numbers at registration time.
 *
 * @param durability        base durability before the tool kind's multiplier
 * @param miningSpeed       base mining speed before the tool kind's multiplier
 * @param attackBonus       attack damage bonus over the vanilla base
 * @param enchantmentValue  vanilla enchantment value (iron = 14)
 * @param incorrectForDrops the tier's incorrect-blocks tag (e.g. INCORRECT_FOR_IRON_TOOL)
 */
public record ToolStatsData(int durability, float miningSpeed, float attackBonus,
                            int enchantmentValue, TagKey<Block> incorrectForDrops) {

    public ToolStatsData {
        if (durability <= 0) {
            throw new IllegalArgumentException("tool durability must be positive: " + durability);
        }
        if (miningSpeed <= 0) {
            throw new IllegalArgumentException("tool mining speed must be positive: " + miningSpeed);
        }
        if (attackBonus < 0) {
            throw new IllegalArgumentException("tool attack bonus must not be negative: " + attackBonus);
        }
        if (enchantmentValue < 0) {
            throw new IllegalArgumentException(
                    "tool enchantment value must not be negative: " + enchantmentValue);
        }
        java.util.Objects.requireNonNull(incorrectForDrops, "incorrectForDrops");
    }
}
