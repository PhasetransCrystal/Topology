package net.ptcrys.topo.api.ore.engine;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OreGridMathTest {

    @Test
    void seedsAreStableForTheSameCoordinatesAndSeparatedByIdentity() {
        Identifier dimension = Identifier.parse("minecraft:overworld");
        Identifier vein = Identifier.parse("topo_test_a:copper");

        long first = OreGridMath.seed(42L, dimension, vein, 12, -7);
        assertEquals(first, OreGridMath.seed(42L, dimension, vein, 12, -7));
        assertNotEquals(first, OreGridMath.seed(43L, dimension, vein, 12, -7));
        assertNotEquals(first, OreGridMath.seed(42L, Identifier.parse("minecraft:the_nether"), vein, 12, -7));
        assertNotEquals(first, OreGridMath.seed(42L, dimension, Identifier.parse("topo_test_a:tin"), 12, -7));
    }

    @Test
    void randomIntStaysWithinInclusiveBounds() {
        long seed = OreGridMath.mix(17L);
        for (int i = 0; i < 1_000; i++) {
            int value = OreGridMath.randomInt(seed + i, 3, -4, 9);
            assertTrue(value >= -4 && value <= 9);
        }
        assertEquals(5, OreGridMath.randomInt(seed, 3, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> OreGridMath.randomInt(seed, 3, 9, -4));
        assertThrows(IllegalArgumentException.class, () -> OreGridMath.randomLong(seed, 3, 0L));
    }

    @Test
    void cellOriginsUseFloorDivisionForNegativeChunks() {
        assertEquals(-6, OreGridMath.cellMinChunk(-5, 3));
        assertEquals(0, OreGridMath.cellMinChunk(0, 3));
        assertEquals(3, OreGridMath.cellMinChunk(5, 3));
        assertThrows(IllegalArgumentException.class, () -> OreGridMath.cellMinChunk(0, 0));
    }
}
