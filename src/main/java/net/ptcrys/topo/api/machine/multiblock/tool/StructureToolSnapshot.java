package net.ptcrys.topo.api.machine.multiblock.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import io.netty.handler.codec.DecoderException;

import java.util.*;

/** 服务端确认的工具报告与虚影。协议明确限制位置数和诊断行数，不截断后冒充完整结构。 */
public record StructureToolSnapshot(Identifier dimension, BlockPos anchor, Component summary, List<Component> details, List<StructureToolPlan.Target> targets) {

    public static final int MAX_DETAILS = 10;

    public StructureToolSnapshot {
        Objects.requireNonNull(dimension);
        anchor = anchor.immutable();
        Objects.requireNonNull(summary);
        details = List.copyOf(details);
        targets = List.copyOf(targets);
        if (details.size() > MAX_DETAILS || targets.size() > StructureToolPlan.MAX_CELLS) throw new IllegalArgumentException("Structure tool snapshot too large");
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeIdentifier(dimension);
        buffer.writeBlockPos(anchor);
        ComponentSerialization.STREAM_CODEC.encode(buffer, summary);
        buffer.writeVarInt(details.size());
        for (Component detail : details) ComponentSerialization.STREAM_CODEC.encode(buffer, detail);
        buffer.writeVarInt(targets.size());
        for (var target : targets) {
            buffer.writeBlockPos(target.pos());
            buffer.writeVarInt(Block.getId(target.state()));
            buffer.writeEnum(target.status());
        }
    }

    public static StructureToolSnapshot read(RegistryFriendlyByteBuf buffer) {
        Identifier dimension = buffer.readIdentifier();
        BlockPos anchor = buffer.readBlockPos();
        Component summary = ComponentSerialization.STREAM_CODEC.decode(buffer);
        int lines = bounded(buffer.readVarInt(), MAX_DETAILS);
        List<Component> details = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) details.add(ComponentSerialization.STREAM_CODEC.decode(buffer));
        int count = bounded(buffer.readVarInt(), StructureToolPlan.MAX_CELLS);
        List<StructureToolPlan.Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buffer.readBlockPos();
            int stateId = buffer.readVarInt();
            var state = Block.stateById(stateId);
            if (stateId < 0 || Block.getId(state) != stateId) throw new DecoderException("Invalid structure tool block state");
            targets.add(new StructureToolPlan.Target(pos, state, buffer.readEnum(StructureToolPlan.Status.class)));
        }
        return new StructureToolSnapshot(dimension, anchor, summary, details, targets);
    }

    private static int bounded(int count, int max) {
        if (count < 0 || count > max) throw new DecoderException("Invalid structure tool list size: " + count);
        return count;
    }
}
