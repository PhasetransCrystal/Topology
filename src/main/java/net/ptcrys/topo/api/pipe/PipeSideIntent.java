package net.ptcrys.topo.api.pipe;

import net.minecraft.core.Direction;

/**
 * Player intent for one pipe side. Persisted as the authoritative wrench state; the effective
 * behaviour additionally depends on neighbor reality (see {@link PipeSideRole}).
 *
 * <ul>
 * <li>{@link #AUTO}: connect to same-kind pipes and insert into adjacent containers.</li>
 * <li>{@link #DISABLED}: player forced this side off.</li>
 * <li>{@link #EXTRACT}: pull from the adjacent container into the network. Kept even while the
 * neighbor offers no capability so the behaviour restores when the machine returns.</li>
 * </ul>
 *
 * <p>
 * Six sides pack into 12 bits (2 bits per side at {@code direction.ordinal() * 2}), mirroring
 * the machine side-io packing convention.
 */
public enum PipeSideIntent {

    AUTO,
    DISABLED,
    EXTRACT;

    private static final PipeSideIntent[] VALUES = values();
    private static final int SIDE_MASK = 0b11;

    public static PipeSideIntent unpack(int packed, Direction side) {
        int raw = (packed >>> (side.ordinal() * 2)) & SIDE_MASK;
        return raw < VALUES.length ? VALUES[raw] : AUTO;
    }

    public static int pack(int packed, Direction side, PipeSideIntent intent) {
        int shift = side.ordinal() * 2;
        return (packed & ~(SIDE_MASK << shift)) | (intent.ordinal() << shift);
    }

    public static int packAll(PipeSideIntent intent) {
        int packed = 0;
        for (Direction side : Direction.values()) {
            packed = pack(packed, side, intent);
        }
        return packed;
    }
}
