package net.ptcrys.topo.api.pipe.survey;

import net.minecraft.network.FriendlyByteBuf;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipeSurveySnapshotTest {

    @Test
    void overlongLaneDoesNotShiftFollowingLaneOrCapacity() {
        long[] longPath = positions(2049);
        long[] shortPath = { 11L, 22L, 33L };
        var snapshot = snapshot(List.of(
                new PipeSurveySnapshot.Lane(longPath, 42),
                new PipeSurveySnapshot.Lane(shortPath, 1)));

        // Before the fix, the unread 2049th long is decoded as later fields, leaving trailing bytes.
        var decoded = roundTrip(snapshot);
        assertTrue(decoded.truncated());
        assertEquals(1, decoded.lanes().size());
        assertArrayEquals(shortPath, decoded.lanes().getFirst().positions());
        assertEquals(1, decoded.lanes().getFirst().bottleneckOrdinal());
        assertEquals(73, decoded.pairCapacity());
        assertEquals(11L, decoded.pointA());
        assertEquals(33L, decoded.pointB());
    }

    @Test
    void allOverlongLanesAreOmittedWithoutLosingCapacity() {
        var decoded = roundTrip(snapshot(List.of(new PipeSurveySnapshot.Lane(positions(2049), 0))));
        assertTrue(decoded.truncated());
        assertTrue(decoded.lanes().isEmpty());
        assertEquals(73, decoded.pairCapacity());
    }

    @Test
    void emptyAndMaximumLengthPathsRoundTripWithoutTruncation() {
        for (int length : new int[] { 0, 2048 }) {
            long[] positions = positions(length);
            int bottleneck = length == 0 ? -1 : length - 1;
            var decoded = roundTrip(snapshot(List.of(new PipeSurveySnapshot.Lane(positions, bottleneck))));
            assertFalse(decoded.truncated());
            assertArrayEquals(positions, decoded.lanes().getFirst().positions());
            assertEquals(bottleneck, decoded.lanes().getFirst().bottleneckOrdinal());
            assertEquals(73, decoded.pairCapacity());
        }
    }

    @Test
    void maximumNodeTableRoundTripsAllParallelColumns() {
        long[] positions = positions(2048);
        int[] throughput = new int[2048];
        long[] used = new long[2048];
        short[] ports = new short[2048];
        Arrays.fill(throughput, 128);
        Arrays.fill(used, 63L);
        Arrays.fill(ports, (short) 0x081);
        var decoded = roundTrip(new PipeSurveySnapshot(1L, 20, true,
                positions, throughput, used, ports, 11L, 33L, List.of(), 73));
        assertArrayEquals(positions, decoded.nodePos());
        assertArrayEquals(throughput, decoded.nodeThroughput());
        assertArrayEquals(used, decoded.nodeUsed());
        assertArrayEquals(ports, decoded.nodePortBits());
        assertTrue(decoded.truncated());
    }

    @Test
    void emptySnapshotRoundTripsAsClearInstruction() {
        var decoded = roundTrip(PipeSurveySnapshot.empty());
        assertFalse(decoded.active());
        assertFalse(decoded.truncated());
        assertTrue(decoded.lanes().isEmpty());
        assertEquals(0, decoded.pairCapacity());
    }

    @Test
    void legalFramesKeepTheExistingWireLayout() {
        var expected = buffer();
        var actual = buffer();
        try {
            writeHeader(expected, 1);
            expected.writeLong(11L);
            expected.writeVarInt(128);
            expected.writeVarLong(63L);
            expected.writeShort(0x081);
            writeEndpoints(expected, 1);
            expected.writeVarInt(2);
            expected.writeLong(11L);
            expected.writeLong(33L);
            expected.writeVarInt(1);
            expected.writeVarInt(73);
            snapshot(List.of(new PipeSurveySnapshot.Lane(new long[] { 11L, 33L }, 1))).write(actual);
            assertEquals(expected, actual);
        } finally {
            expected.release();
            actual.release();
        }
    }

    @Test
    void laneCountIsBoundedAndMarksOmittedPaths() {
        var lane = new PipeSurveySnapshot.Lane(new long[] { 11L, 33L }, 0);
        var decoded = roundTrip(snapshot(Collections.nCopies(16, lane)));
        assertEquals(16, decoded.lanes().size());
        assertFalse(decoded.truncated());
        decoded = roundTrip(snapshot(Collections.nCopies(17, lane)));
        assertEquals(16, decoded.lanes().size());
        assertTrue(decoded.truncated());
    }

    @Test
    void rejectsNegativeAndOverLimitNodeCountsBeforeReadingNodes() {
        for (int count : new int[] { -1, 2049, Integer.MAX_VALUE }) {
            var buffer = buffer();
            try {
                writeHeader(buffer, count);
                assertThrows(DecoderException.class, () -> PipeSurveySnapshot.read(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void rejectsNegativeAndOverLimitLaneCountsBeforeReadingLanes() {
        for (int count : new int[] { -1, 17, Integer.MAX_VALUE }) {
            var buffer = buffer();
            try {
                writeHeader(buffer, 0);
                writeEndpoints(buffer, count);
                assertThrows(DecoderException.class, () -> PipeSurveySnapshot.read(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void rejectsNegativeAndOverLimitPathLengthsBeforeReadingPositions() {
        for (int length : new int[] { -1, 2049, Integer.MAX_VALUE }) {
            var buffer = buffer();
            try {
                writeHeader(buffer, 0);
                writeEndpoints(buffer, 1);
                buffer.writeVarInt(length);
                assertThrows(DecoderException.class, () -> PipeSurveySnapshot.read(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void rejectsIncompletePathData() {
        var buffer = buffer();
        try {
            writeHeader(buffer, 0);
            writeEndpoints(buffer, 1);
            buffer.writeVarInt(2);
            buffer.writeLong(11L);
            assertThrows(IndexOutOfBoundsException.class, () -> PipeSurveySnapshot.read(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsMismatchedNodeColumnsAndOversizedNodeTablesAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new PipeSurveySnapshot(
                1L, 20, false, new long[1], new int[0], new long[1], new short[1],
                11L, 33L, List.of(), 73));
        assertThrows(IllegalArgumentException.class, () -> new PipeSurveySnapshot(
                1L, 20, false, new long[2049], new int[2049], new long[2049], new short[2049],
                11L, 33L, List.of(), 73));
    }

    @Test
    void rejectsInvalidWindowCapacityAndLaneOrdinals() {
        assertThrows(IllegalArgumentException.class, () -> new PipeSurveySnapshot(
                1L, 0, false, new long[0], new int[0], new long[0], new short[0],
                PipeSurveySnapshot.NO_POINT, PipeSurveySnapshot.NO_POINT, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new PipeSurveySnapshot(
                1L, 1, false, new long[0], new int[0], new long[0], new short[0],
                PipeSurveySnapshot.NO_POINT, PipeSurveySnapshot.NO_POINT, List.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                List.of(new PipeSurveySnapshot.Lane(new long[] { 11L, 33L }, 2))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                List.of(new PipeSurveySnapshot.Lane(new long[] { 11L, 33L }, -2))));
    }

    private static PipeSurveySnapshot snapshot(List<PipeSurveySnapshot.Lane> lanes) {
        return new PipeSurveySnapshot(1L, 20, false,
                new long[] { 11L }, new int[] { 128 }, new long[] { 63L }, new short[] { 0x081 },
                11L, 33L, lanes, 73);
    }

    private static long[] positions(int length) {
        long[] positions = new long[length];
        Arrays.fill(positions, 1L);
        return positions;
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static void writeHeader(FriendlyByteBuf buffer, int nodeCount) {
        buffer.writeLong(1L);
        buffer.writeVarInt(20);
        buffer.writeBoolean(false);
        buffer.writeVarInt(nodeCount);
    }

    private static void writeEndpoints(FriendlyByteBuf buffer, int laneCount) {
        buffer.writeLong(11L);
        buffer.writeLong(33L);
        buffer.writeVarInt(laneCount);
    }

    private static PipeSurveySnapshot roundTrip(PipeSurveySnapshot snapshot) {
        var buffer = buffer();
        try {
            snapshot.write(buffer);
            var decoded = PipeSurveySnapshot.read(buffer);
            assertEquals(0, buffer.readableBytes(), "The frame must be consumed without field misalignment");
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
