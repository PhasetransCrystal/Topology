package net.ptcrys.topo.api.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VanillaFacingPathsTest {

    @Test
    void normalizesOneOrTwoKindPrefixesWithoutChangingOtherFolders() {
        assertEquals("iron", VanillaFacingPaths.stripKindFolder("smelting/iron", "smelting"));
        assertEquals("iron", VanillaFacingPaths.stripKindFolder("smelting/smelting/iron", "smelting"));
        assertEquals("smelting/iron", VanillaFacingPaths.foreignPath("smelting/smelting/iron", "smelting"));
        assertEquals("other/iron", VanillaFacingPaths.stripKindFolder("other/iron", "smelting"));
        assertThrows(IllegalArgumentException.class, () -> VanillaFacingPaths.foreignPath("smelting/smelting/", "smelting"));
    }
}
