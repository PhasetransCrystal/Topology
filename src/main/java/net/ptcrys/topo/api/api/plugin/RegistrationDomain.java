package net.ptcrys.topo.api.api.plugin;

import java.util.Objects;
import java.util.function.Function;

/** A typed, plugin-owned domain API. Each plugin creates one instance per domain. */
public final class RegistrationDomain<A> {

    private final Function<TopoPlugin, A> factory;

    private RegistrationDomain(Function<TopoPlugin, A> factory) {
        this.factory = Objects.requireNonNull(factory, "domain factory");
    }

    public static <A> RegistrationDomain<A> of(Function<TopoPlugin, A> factory) {
        return new RegistrationDomain<>(factory);
    }

    A create(TopoPlugin plugin) {
        return Objects.requireNonNull(factory.apply(plugin), "domain API");
    }
}
