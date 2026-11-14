package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.api.visual.ConnectedTextureProperties;
import net.ptcrys.topo.api.machine.FormedMachineBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-side visual synchronization for multiblock members and the controller block. */
final class MultiblockVisuals {

    private MultiblockVisuals() {}

    static void writeMemberCtmActive(ServerLevel level, List<BlockPos> previousMembers, List<BlockPos> currentMembers) {
        if (previousMembers == currentMembers) {
            return;
        }
        Set<BlockPos> current = currentMembers.isEmpty() ? Set.of() : new HashSet<>(currentMembers);
        for (BlockPos member : previousMembers) {
            if (!current.contains(member)) {
                ConnectedTextureProperties.setActive(level, member, false);
            }
        }
        Set<BlockPos> previous = previousMembers.isEmpty() ? Set.of() : new HashSet<>(previousMembers);
        for (BlockPos member : currentMembers) {
            if (!previous.contains(member)) {
                ConnectedTextureProperties.setActive(level, member, true);
            }
        }
    }

    static void writeFormedBlockState(ServerLevel level, BlockPos pos, boolean formed) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(FormedMachineBlock.FORMED) || state.getValue(FormedMachineBlock.FORMED) == formed) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(FormedMachineBlock.FORMED, formed));
    }
}
