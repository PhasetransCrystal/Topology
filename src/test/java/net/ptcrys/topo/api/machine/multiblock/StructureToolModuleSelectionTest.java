package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureToolModuleSelectionTest {

    @Test
    void zeroIsBodyAndModulesFollowDeclarationOrderRatherThanDependencyOrder() {
        Blueprint body = chamber(Blocks.STONE), gold = chamber(Blocks.GOLD_BLOCK), diamond = chamber(Blocks.DIAMOND_BLOCK);
        var metadata = new MultiblockControllerMetadata(body, List.of(MultiblockModule.of("diamond", diamond, "gold"), MultiblockModule.of("gold", gold)));
        assertSame(body, metadata.blueprint(0));
        assertSame(diamond, metadata.blueprint(1));
        assertSame(gold, metadata.blueprint(2));
        assertThrows(IllegalArgumentException.class, () -> metadata.blueprint(-1));
        assertThrows(IllegalArgumentException.class, () -> metadata.blueprint(3));
    }

    private static Blueprint chamber(Block block) {
        return Blueprint.of().aisle("@CCC", "CCCC", "CCCC", "CCCC").aisle("CCCC", "CCCC", "CCCC", "CCCC").aisle("CCCC", "CCCC", "CCCC", "CCCC").aisle("CCCC", "CCCC", "CCCC", "CCCC").aisle("CCCC", "CCCC", "CCCC", "CCCC").aisle("CCCC", "CCCC", "CCCC", "CCCC").where('C', block).build();
    }
}
