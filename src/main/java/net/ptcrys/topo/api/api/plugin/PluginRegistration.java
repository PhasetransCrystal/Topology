package net.ptcrys.topo.api.api.plugin;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Configuration window for one plugin. The engine closes it before running any content. */
public final class PluginRegistration {

    private final TopoPlugin plugin;
    private final Set<RegistrationPoint<?>> supported;
    private final Map<RegistrationPoint<?>, Runnable> contributions = new IdentityHashMap<>();
    private boolean sealed;

    PluginRegistration(TopoPlugin plugin, Set<RegistrationPoint<?>> supported) {
        this.plugin = plugin;
        this.supported = Set.copyOf(supported);
    }

    public synchronized <A> void on(RegistrationPoint<A> point, Consumer<A> action) {
        if (sealed) throw new IllegalStateException("Plugin configuration is closed: " + plugin.identity());
        Objects.requireNonNull(action, "contribution");
        if (!supported.contains(point)) {
            throw new IllegalArgumentException("Unsupported registration point '" + point + "' for " + plugin.identity());
        }
        if (contributions.containsKey(point)) {
            throw new IllegalStateException("Duplicate contribution to '" + point + "' from " + plugin.identity());
        }
        contributions.put(point, () -> action.accept(point.api(plugin)));
    }

    synchronized void seal() {
        sealed = true;
    }

    void invoke(RegistrationPoint<?> point) {
        if (!sealed) throw new IllegalStateException("Plugin configuration is still open: " + plugin.identity());
        Runnable action = contributions.get(point);
        if (action == null) return;
        try {
            action.run();
        } catch (RuntimeException | Error error) {
            throw new IllegalStateException("Plugin '" + plugin.identity() + "' failed at '" + point + "'", error);
        }
    }
}
