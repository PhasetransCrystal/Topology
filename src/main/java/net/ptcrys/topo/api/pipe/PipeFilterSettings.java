package net.ptcrys.topo.api.pipe;

/**
 * Registration-time filter envelope of one pipe definition: how many white/black list entries an
 * extraction port of this tier may hold per list, and whether {@code #tag} entries are accepted.
 * Like {@link AggregationWindow} this is declared as explicit literals at every registration site
 * — no omission form, no defaults ({@code standards/pipe-domain.md}).
 *
 * <p>
 * {@link #NONE} (capacity 0) marks tiers without the filter feature; the port screen hides the
 * whole filter section and the engine never compiles a predicate for them.
 */
public record PipeFilterSettings(int entryCapacity, boolean allowTags) {

    /** No filtering on this pipe: zero entries, no tags, filter UI hidden. */
    public static final PipeFilterSettings NONE = new PipeFilterSettings(0, false);

    public PipeFilterSettings {
        if (entryCapacity < 0) {
            throw new IllegalArgumentException("Filter entry capacity must be >= 0, got " + entryCapacity);
        }
        if (entryCapacity == 0 && allowTags) {
            throw new IllegalArgumentException("A filterless pipe (capacity 0) cannot allow tag entries");
        }
    }

    /** True when this pipe supports filtering at all (the UI section and engine hook exist). */
    public boolean enabled() {
        return entryCapacity > 0;
    }
}
