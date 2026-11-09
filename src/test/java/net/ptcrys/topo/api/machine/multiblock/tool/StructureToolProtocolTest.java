package net.ptcrys.topo.api.machine.multiblock.tool;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StructureToolProtocolTest {

    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    @Test
    void fullFourByFourBySixReportRoundTripsAllPositionsStatesAndStatuses() {
        List<StructureToolPlan.Target> targets = new ArrayList<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 4; z++) targets.add(new StructureToolPlan.Target(new BlockPos(x + 15, y + 65, z - 17), y == 5 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), StructureToolPlan.Status.values()[(x + y + z) % StructureToolPlan.Status.values().length]));
        var snapshot = new StructureToolSnapshot(DIMENSION, new BlockPos(15, 65, -17), Component.literal("96 cells"), List.of(Component.literal("missing")), targets);
        var buffer = buffer();
        try {
            StructureToolNetworking.Snapshot.CODEC.encode(buffer, new StructureToolNetworking.Snapshot(snapshot));
            assertEquals(snapshot, StructureToolNetworking.Snapshot.CODEC.decode(buffer).snapshot());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void listBoundsAreRejectedBeforeReadingEntries() {
        for (int size : new int[] { -1, StructureToolPlan.MAX_CELLS + 1, Integer.MAX_VALUE }) {
            var buffer = buffer();
            try {
                buffer.writeIdentifier(DIMENSION);
                buffer.writeBlockPos(BlockPos.ZERO);
                ComponentSerialization.STREAM_CODEC.encode(buffer, Component.empty());
                buffer.writeVarInt(0);
                buffer.writeVarInt(size);
                assertThrows(DecoderException.class, () -> StructureToolSnapshot.read(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void rejectsTrailingDataAndInvalidBlockStates() {
        var buffer = buffer();
        var trailing = buffer;
        try {
            new StructureToolSnapshot(DIMENSION, BlockPos.ZERO, Component.empty(), List.of(), List.of()).write(buffer);
            buffer.writeByte(1);
            assertThrows(DecoderException.class, () -> StructureToolNetworking.Snapshot.CODEC.decode(trailing));
        } finally {
            buffer.release();
        }
        buffer = buffer();
        var invalid = buffer;
        try {
            buffer.writeIdentifier(DIMENSION);
            buffer.writeBlockPos(BlockPos.ZERO);
            ComponentSerialization.STREAM_CODEC.encode(buffer, Component.empty());
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(DecoderException.class, () -> StructureToolSnapshot.read(invalid));
        } finally {
            buffer.release();
        }
    }
}
