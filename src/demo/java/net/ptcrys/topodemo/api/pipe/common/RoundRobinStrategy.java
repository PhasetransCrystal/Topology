package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.pipe.PipeDistributionContext;

import net.minecraft.resources.Identifier;

public final class RoundRobinStrategy extends RateStrategy {

    public RoundRobinStrategy(Identifier id) {
        super(id);
    }

    @Override
    public void distribute(PipeDistributionContext context) {
        int count = context.destinationCount();
        if (count <= 0) {
            return;
        }
        int start = Math.floorMod(context.cursor(), count);
        boolean served = false;
        for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
            int index = (start + offset) % count;
            int moved = context.transfer(index, context.budgetRemaining());
            if (moved > 0 && !served) {
                served = true;
                context.setCursor(index + 1);
            }
        }
        if (!served) {
            context.setCursor(start + 1);
        }
    }
}
