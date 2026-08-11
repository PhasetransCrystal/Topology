package net.ptcrys.topo.api.api.tick;

import java.util.Locale;

/**
 * Scheduling lane: {@link #SYNC} runs on the server thread each level tick, {@link #ASYNC} on the
 * shared 50ms scheduler thread. {@link TickHub} sizes its bucket table from the constants, so a new
 * constant only makes sense together with new driver semantics in {@code TickHub}.
 */
public enum TickKind {

    SYNC(false),
    ASYNC(true);

    /** Cached for hot-path iteration; {@link #values()} clones on every call. */
    static final TickKind[] VALUES = values();

    private final boolean async;

    TickKind(boolean async) {
        this.async = async;
    }

    public boolean async() {
        return async;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
