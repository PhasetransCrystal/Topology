package net.ptcrys.topo.api.api.tick;

import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/** Permanently-cancelled handle used for direct test/manual ticking without a live hub. */
public final class NoopTickHandle implements TickHandle {

    public static final NoopTickHandle INSTANCE = new NoopTickHandle();

    private NoopTickHandle() {}

    @Override
    public @Nullable Level level() {
        return null;
    }

    @Override
    public TickKind kind() {
        return TickKind.SYNC;
    }

    @Override
    public int interval() {
        return 1;
    }

    @Override
    public void setInterval(int interval) {}

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public void suspend() {}

    @Override
    public void resume() {}

    @Override
    public void alert() {}

    @Override
    public boolean isCancelled() {
        return true;
    }

    @Override
    public void unsubscribe() {}
}
