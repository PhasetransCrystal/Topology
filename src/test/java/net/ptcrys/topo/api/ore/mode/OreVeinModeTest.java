package net.ptcrys.topo.api.ore.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OreVeinModeTest {

    @Test
    void unrelatedCollectionChannelsDefaultToNoOp() {
        OreVeinMode.Strategy strategy = placement -> {};
        assertDoesNotThrow(() -> strategy.collectGrid(null, null));
        assertDoesNotThrow(() -> strategy.collectFeature(null, null));
    }
}
