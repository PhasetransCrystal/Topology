package net.ptcrys.topo.api.api.tick;

import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Conditional tick wrapper that keeps a hook suspended while its active predicate is false, and
 * alerts it when external state changes make work possible.
 */
public final class ConditionalTicker implements TickHook {

    private final BooleanSupplier active;
    private final TickHook work;
    private @Nullable TickHandle handle;

    public ConditionalTicker(BooleanSupplier active, TickHook work) {
        this.active = active;
        this.work = work;
    }

    public void attach(TickHandle handle) {
        this.handle = handle;
        if (active.getAsBoolean()) {
            handle.resume();
        } else {
            handle.suspend();
        }
    }

    public void reevaluate() {
        TickHandle h = handle;
        if (h == null || h.isCancelled()) {
            return;
        }
        if (active.getAsBoolean()) {
            if (h.isSuspended()) {
                h.resume();
            }
            h.alert();
        } else {
            h.suspend();
        }
    }

    @Override
    public void tick(long gameTime, TickHandle h) {
        if (!active.getAsBoolean()) {
            h.suspend();
            return;
        }
        work.tick(gameTime, h);
        if (active.getAsBoolean()) {
            if (h.isSuspended()) {
                h.resume();
            }
        } else {
            h.suspend();
        }
    }
}
