package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.multiblock.ability.ExclusivePartAttachment;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 由世界线程按区块局部性顺序采集最大稳定占地，共用于服务端重检和客户端诊断。
 * 状态写入 CompiledSnapshot 的稳定编号，角色和成员采用稀疏表；未加载区块只记录未知，不强制加载。
 */
public final class StructureCaptures {

    private StructureCaptures() {}

    /** 采集蓝图在指定控制器朝向下的最大稳定占地。 */
    public static CompiledSnapshot capture(
                                           Level level, BlockPos controllerPos, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        CompiledSnapshot.Builder builder = CompiledSnapshot.builder(compiled, controllerPos);
        int captureSize = compiled.captureSize();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ChunkAccess chunk = null;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ = Integer.MIN_VALUE;
        boolean chunkResolved = false;
        for (int i = 0; i < captureSize; i++) {
            long packed = compiled.captureOffsetAt(i);
            cursor.set(
                    controllerPos.getX() + BlockPos.getX(packed),
                    controllerPos.getY() + BlockPos.getY(packed),
                    controllerPos.getZ() + BlockPos.getZ(packed));
            int cx = cursor.getX() >> 4;
            int cz = cursor.getZ() >> 4;
            if (!chunkResolved || cx != chunkX || cz != chunkZ) {
                chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                chunkX = cx;
                chunkZ = cz;
                chunkResolved = true;
            }
            if (chunk == null || level.isOutsideBuildHeight(cursor)) {
                builder.put(compiled.captureCellIndexAt(i), cursor.asLong(), null, null);
                continue;
            }
            BlockState state = chunk.getBlockState(cursor);
            if (state.hasBlockEntity() && chunk.getBlockEntity(cursor) instanceof MachineBlockEntity machine) {
                long packedWorld = cursor.asLong();
                builder.put(compiled.captureCellIndexAt(i), packedWorld, state, new StructureView.Member(cursor, exclusive(machine)));
                builder.roles(packedWorld, partRoles(machine));
            } else {
                builder.put(compiled.captureCellIndexAt(i), cursor.asLong(), state, null);
            }
        }
        return builder.build();
    }

    /** 从成员的角色附件中提取结构角色。 */
    static Set<PartRole> partRoles(MachineBlockEntity machine) {
        Set<PartRole> capabilities = new LinkedHashSet<>();
        for (PartRoleAttachment metadata : machine.definition().metadata(PartRoleAttachment.TYPE)) {
            capabilities.add(metadata.role());
        }
        return capabilities;
    }

    /** 成员是否声明独占部件。 */
    static boolean exclusive(MachineBlockEntity machine) {
        return !machine.definition().metadata(ExclusivePartAttachment.TYPE).isEmpty();
    }

    /** 按完整采集相同的角色和成员语义读取一个位置。 */
    static CellState captureCell(Level level, BlockPos worldPos) {
        if (!level.hasChunkAt(worldPos)) {
            return new CellState(null, Set.of(), null);
        }
        BlockState state = level.getBlockState(worldPos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(worldPos) : null;
        if (blockEntity instanceof MachineBlockEntity machine) {
            return new CellState(state, partRoles(machine), new StructureView.Member(worldPos, exclusive(machine)));
        }
        return new CellState(state, Set.of(), null);
    }

    /** 一个采集位置：状态为 null 表示未加载，同时携带角色和成员声明。 */
    record CellState(
                     @Nullable BlockState state,
                     Set<PartRole> roles,
                     StructureView.@Nullable Member member) {

        CellState {
            roles = Set.copyOf(Objects.requireNonNull(roles, "part roles"));
        }
    }
}
