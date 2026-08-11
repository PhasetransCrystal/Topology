package net.ptcrys.topo.api.tick;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.api.tick.TickHook;
import net.ptcrys.topo.api.api.tick.TickHub;
import net.ptcrys.topo.api.api.tick.TickKind;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.MachineComponent;

import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/** Trait subtype that registers itself with the level {@link TickHub} while its machine is loaded. */
public abstract class MachineTicker extends MachineComponent implements TickHook {

    private @Nullable TickHandle handle;
    private int profileSlot = -1;

    protected MachineTicker(ComponentContext<? extends MachineTicker> context) {
        super(context);
    }

    public TickKind kind() {
        return TickKind.SYNC;
    }

    public int tickInterval() {
        return 1;
    }

    @Override
    public abstract void tick(long gameTime, TickHandle handle);

    public final void runProfiledTick(long gameTime, TickHandle handle) {
        MachineBlockEntity machine = machine();
        if (!machine.isPerformanceMonitoringActive(gameTime)) {
            try {
                tick(gameTime, handle);
            } finally {
                machine.afterTickerTick(gameTime);
            }
            return;
        }
        long start = System.nanoTime();
        try {
            tick(gameTime, handle);
        } finally {
            // Framework time (afterTickerTick) is sampled into the snapshot's self bucket so the
            // timing panel reports the full per-tick machine cost, not just the trait body.
            long afterTick = System.nanoTime();
            try {
                machine.afterTickerTick(gameTime);
            } finally {
                long end = System.nanoTime();
                machine.recordPerformanceSample(gameTime, afterTick - start, end - afterTick, profileSlot);
            }
        }
    }

    public final int profileSlot() {
        return profileSlot;
    }

    public final void assignProfileSlot(int slot) {
        this.profileSlot = slot;
    }

    public final @Nullable TickHandle handle() {
        return handle;
    }

    public final void attachToHub() {
        if (handle != null) {
            return;
        }
        TickKind tickKind = kind();
        if (tickKind.async()) {
            throw new IllegalStateException("MachineTicker " + getClass().getName() + " cannot use async tick kind '" + tickKind + "'. Machine data, capabilities, and BlockEntity state must be applied on the server thread.");
        }
        Level level = machine().getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        TickHub hub = TickHub.of(level);
        if (hub == null) {
            return;
        }
        handle = hub.register(tickKind, tickInterval(), this);
        onAttached(handle);
    }

    public final void detachFromHub() {
        TickHandle h = handle;
        if (h == null) {
            return;
        }
        handle = null;
        onDetaching(h);
        h.unsubscribe();
    }

    protected void onAttached(TickHandle handle) {}

    protected void onDetaching(TickHandle handle) {}
}
