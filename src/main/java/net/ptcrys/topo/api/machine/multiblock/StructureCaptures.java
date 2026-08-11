package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;

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
 * Chunk-locality snapshot capture into the {@link CompiledSnapshot dense} form, shared by the
 * server recheck path and the client diagnosis.
 *
 * <p>
 * Walks the compiled blueprint's {@link CompiledBlueprint#captureOffsets() chunk-sorted capture
 * order} writing each state straight into its canonical cell slot, and pins one
 * {@link ChunkAccess} per chunk run — a footprint spanning hundreds of chunks costs one chunk-map
 * lookup per <em>chunk</em> instead of one per <em>cell</em>. Block entities are consulted only for
 * states that declare one ({@link BlockState#hasBlockEntity()}) — plain casing cells never pay a
 * block-entity lookup. Unloaded chunks record {@code null} states (recognition treats them as not
 * formed); chunks are never force-loaded.
 */
public final class StructureCaptures {

    private StructureCaptures() {}

    /** Captures the maximal footprint of {@code blueprint} under {@code orientation}. */
    public static CompiledSnapshot capture(
                                           Level level, BlockPos controllerPos, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        CompiledSnapshot.Builder builder = CompiledSnapshot.builder(compiled, controllerPos);
        long[] offsets = compiled.captureOffsets();
        int[] cellIndex = compiled.captureCellIndex();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ChunkAccess chunk = null;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ = Integer.MIN_VALUE;
        boolean chunkResolved = false;
        for (int i = 0; i < offsets.length; i++) {
            long packed = offsets[i];
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
                builder.put(cellIndex[i], cursor.asLong(), null, false);
                continue;
            }
            BlockState state = chunk.getBlockState(cursor);
            if (state.hasBlockEntity() && chunk.getBlockEntity(cursor) instanceof MachineBlockEntity machine) {
                long packedWorld = cursor.asLong();
                builder.put(cellIndex[i], packedWorld, state, true);
                builder.roles(packedWorld, partRoles(machine));
            } else {
                builder.put(cellIndex[i], cursor.asLong(), state, false);
            }
        }
        return builder.build();
    }

    /** Structural capabilities a member exposes, derived from its declared part-capability metadata. */
    static Set<PartRole> partRoles(MachineBlockEntity machine) {
        Set<PartRole> capabilities = new LinkedHashSet<>();
        for (PartRoleAttachment metadata : machine.definition().metadata(PartRoleAttachment.TYPE)) {
            capabilities.add(metadata.role());
        }
        return capabilities;
    }

    /** Single-cell live read with the same member/capability semantics as a full capture. */
    static CellState captureCell(Level level, BlockPos worldPos) {
        if (!level.hasChunkAt(worldPos)) {
            return new CellState(null, Set.of(), false);
        }
        BlockState state = level.getBlockState(worldPos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(worldPos) : null;
        if (blockEntity instanceof MachineBlockEntity machine) {
            return new CellState(state, partRoles(machine), true);
        }
        return new CellState(state, Set.of(), false);
    }

    /** One captured cell: its state ({@code null} = unloaded), part capabilities, and membership. */
    record CellState(
                     @Nullable BlockState state,
                     Set<PartRole> roles,
                     boolean member) {

        CellState {
            roles = Set.copyOf(Objects.requireNonNull(roles, "part roles"));
        }
    }
}
