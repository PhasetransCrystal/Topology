package net.ptcrys.topo.api.api.plugin;

/** Ordered, non-reentrant startup; failed mutations cannot safely be retried. */
final class PluginLifecycle {

    enum Phase {
        RECIPE,
        MATERIAL,
        EQUIPMENT,
        MACHINE,
        ORE,
        LANG,
        RUNTIME,
        READY
    }

    private int completed;
    private Phase running;
    private Throwable failure;

    synchronized void run(Phase phase, Runnable action) {
        if (failure != null) {
            throw new IllegalStateException("TopoPluginEngine: startup failed during " + running, failure);
        }
        if (running != null) {
            throw new IllegalStateException("TopoPluginEngine: cannot enter " + phase + " while running " + running);
        }
        if (phase.ordinal() < completed) {
            return;
        }
        if (phase.ordinal() != completed) {
            throw new IllegalStateException("TopoPluginEngine: " + Phase.values()[completed] + " must run before " + phase);
        }
        running = phase;
        try {
            action.run();
            completed++;
            running = null;
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        }
    }
}
