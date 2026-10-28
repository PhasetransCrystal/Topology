package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.pipe.PipeDistributionContext;

import net.minecraft.resources.Identifier;

public final class EqualSplitStrategy extends RateStrategy {

    public EqualSplitStrategy(Identifier id) {
        super(id);
    }

    /**
     * True per-batch equal split among destinations that can actually receive: shares are
     * sized from {@code min(budget, source supply) / liveCount}, where dead destinations
     * (full buffers, output-only views, unloaded) are excluded — they must not eat a share
     * (e.g. 30 over 3 live + 1 dead lands as a stable 10/10/10/0, not 7s plus leftovers).
     * The indivisible remainder goes to whoever accepts first in the leftover pass. The
     * iteration start still rotates per batch for the one case a single batch cannot split:
     * a saturated shared path segment, whose per-transfer allowance goes to the head of the
     * line.
     */
    @Override
    public void distribute(PipeDistributionContext context) {
        int count = context.destinationCount();
        if (count <= 0) {
            return;
        }
        long liveMask = liveMask(context, count);
        int live = count <= 64 ? Long.bitCount(liveMask) : count;
        if (live == 0) {
            return;
        }
        int start = Math.floorMod(context.cursor(), count);
        int deliverable = Math.min(context.budgetRemaining(), context.sourceAvailable());
        int share = Math.max(1, deliverable / live);
        for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
            int index = (start + offset) % count;
            if (isLive(liveMask, count, index)) {
                context.transfer(index, Math.min(share, context.budgetRemaining()));
            }
        }
        // Greedy second pass hands the indivisible remainder to whoever still accepts.
        for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
            int index = (start + offset) % count;
            if (isLive(liveMask, count, index)) {
                context.transfer(index, context.budgetRemaining());
            }
        }
        context.setCursor(start + 1);
    }

    /** Bitmask of destinations that can accept right now; beyond 64 everyone counts as live. */
    private static long liveMask(PipeDistributionContext context, int count) {
        if (count > 64) {
            return -1L;
        }
        long mask = 0;
        for (int i = 0; i < count; i++) {
            if (context.destinationAcceptance(i) > 0) {
                mask |= 1L << i;
            }
        }
        return mask;
    }

    private static boolean isLive(long liveMask, int count, int index) {
        return count > 64 || (liveMask & (1L << index)) != 0;
    }
}
