package net.ptcrys.topo.data.material.common;

/**
 * Armor-grade numbers a material contributes to its four armor pieces. Declaring this data on a
 * material is what makes the material's armor exist; the equipment domain derives one vanilla
 * {@code ArmorMaterial} per material from these numbers at registration time.
 *
 * @param durabilityMultiplier vanilla per-slot durability multiplier (iron = 15)
 * @param helmetDefense        defense points for the helmet slot
 * @param chestplateDefense    defense points for the chestplate slot
 * @param leggingsDefense      defense points for the leggings slot
 * @param bootsDefense         defense points for the boots slot
 * @param toughness            armor toughness shared by all pieces
 * @param knockbackResistance  knockback resistance shared by all pieces
 * @param enchantmentValue     vanilla enchantment value (iron = 9)
 */
public record ArmorStatsData(int durabilityMultiplier, int helmetDefense, int chestplateDefense,
                             int leggingsDefense, int bootsDefense, float toughness, float knockbackResistance,
                             int enchantmentValue) {

    public ArmorStatsData {
        if (durabilityMultiplier <= 0) {
            throw new IllegalArgumentException(
                    "armor durability multiplier must be positive: " + durabilityMultiplier);
        }
        requireNotNegative(helmetDefense, "helmet defense");
        requireNotNegative(chestplateDefense, "chestplate defense");
        requireNotNegative(leggingsDefense, "leggings defense");
        requireNotNegative(bootsDefense, "boots defense");
        if (toughness < 0) {
            throw new IllegalArgumentException("armor toughness must not be negative: " + toughness);
        }
        if (knockbackResistance < 0) {
            throw new IllegalArgumentException(
                    "armor knockback resistance must not be negative: " + knockbackResistance);
        }
        requireNotNegative(enchantmentValue, "armor enchantment value");
    }

    private static void requireNotNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
    }
}
