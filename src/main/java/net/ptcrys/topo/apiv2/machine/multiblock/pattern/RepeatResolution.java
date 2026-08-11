package net.ptcrys.topo.apiv2.machine.multiblock.pattern;

import java.util.Arrays;

/**
 * The repeat count chosen for every blueprint segment during a single {@link StructureEngine} pass.
 *
 * <p>
 * A {@link Blueprint} is an ordered list of segments; each segment carries a
 * {@link Blueprint.RepeatRange}. The engine's grid walk picks one concrete count per segment
 * (between its range's {@code min} and {@code max}, greedy-maximal for optional copies) and records
 * it on the {@link RecognitionResult}, so the formed footprint and the targeted-invalidation math
 * agree on exactly the same expansion.
 *
 * <p>
 * Counts are indexed by segment position (the order segments were declared on the builder).
 */
public final class RepeatResolution {

    private final int[] counts;

    public RepeatResolution(int[] counts) {
        this.counts = counts.clone();
    }

    /** The chosen repeat count for the segment at {@code segmentIndex}. */
    public int count(int segmentIndex) {
        return counts[segmentIndex];
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof RepeatResolution other && Arrays.equals(counts, other.counts));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(counts);
    }

    @Override
    public String toString() {
        return "RepeatResolution" + Arrays.toString(counts);
    }
}
