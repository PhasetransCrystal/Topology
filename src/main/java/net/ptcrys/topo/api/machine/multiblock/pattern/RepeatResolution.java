package net.ptcrys.topo.api.machine.multiblock.pattern;

import java.util.Arrays;

/**
 * The concrete copy count chosen for every {@link Blueprint.RepeatSection} during one recognition
 * pass.
 *
 * <p>
 * Counts are indexed by section declaration order ({@link Blueprint#section(int)}). The engine
 * resolves sections greedily in declaration order and records the chosen counts so the formed
 * footprint, targeted invalidation, claims and export all agree on exactly the same layout.
 */
public final class RepeatResolution {

    private final int[] counts;

    public RepeatResolution(int[] counts) {
        this.counts = counts.clone();
    }

    /** The chosen copy count of section {@code sectionIndex}. */
    public int count(int sectionIndex) {
        return counts[sectionIndex];
    }

    public int size() {
        return counts.length;
    }

    /** A defensive copy of the raw counts, for the layout engine's hot paths. */
    int[] countsCopy() {
        return counts.clone();
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
