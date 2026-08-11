package net.ptcrys.topo.api.pipe;

import net.minecraft.core.Direction;

/**
 * Effective role of one pipe side, derived from {@link PipeSideIntent} and neighbor reality on
 * server events and persisted alongside the node record. The runtime network graph, extractor
 * list and destination list are all read from these roles without touching world blocks, which
 * keeps topology available across unloaded chunks.
 */
public enum PipeSideRole {

    /** Not connected. */
    NONE,
    /** Connected to a same-kind pipe; a graph edge when both facing sides agree. */
    LINK,
    /** Normal container connection: the network may insert into the neighbor. */
    DESTINATION,
    /** Extraction connection: the network pulls from the neighbor. */
    EXTRACT;

    private static final PipeSideRole[] VALUES = values();
    private static final int SIDE_MASK = 0b11;

    public PipeSideVisual visual() {
        return switch (this) {
            case NONE -> PipeSideVisual.NONE;
            case LINK, DESTINATION -> PipeSideVisual.PIPE;
            case EXTRACT -> PipeSideVisual.EXTRACT;
        };
    }

    public static PipeSideRole unpack(int packed, Direction side) {
        int raw = (packed >>> (side.ordinal() * 2)) & SIDE_MASK;
        return raw < VALUES.length ? VALUES[raw] : NONE;
    }

    public static int pack(int packed, Direction side, PipeSideRole role) {
        int shift = side.ordinal() * 2;
        return (packed & ~(SIDE_MASK << shift)) | (role.ordinal() << shift);
    }
}
