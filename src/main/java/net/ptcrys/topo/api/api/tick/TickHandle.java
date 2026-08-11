package net.ptcrys.topo.api.api.tick;

import net.minecraft.world.level.Level;

import com.lowdragmc.lowdraglib2.syncdata.ISubscription;

/** Control handle returned after a {@link TickHook} is registered with a {@link TickHub}. */
public interface TickHandle extends ISubscription {

    Level level();

    TickKind kind();

    int interval();

    void setInterval(int interval);

    boolean isSuspended();

    void suspend();

    void resume();

    /** Queue this hook to run once on the next heartbeat for its kind, without changing interval. */
    void alert();

    boolean isCancelled();

    @Override
    void unsubscribe();
}
