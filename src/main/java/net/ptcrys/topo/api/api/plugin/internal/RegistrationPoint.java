package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.api.plugin.RegistrationDomain;
import net.ptcrys.topo.api.api.plugin.TopoContribution;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Internal typed dispatch: binds a domain hook to its API and its module-owned schedule. */
public final class RegistrationPoint<P extends TopoContribution, A> {

    private final String id;
    private final Class<P> contributor;
    private final RegistrationDomain<A> domain;
    private final BiConsumer<P, A> hook;

    private RegistrationPoint(String id, Class<P> contributor, RegistrationDomain<A> domain, BiConsumer<P, A> hook) {
        this.id = Objects.requireNonNull(id, "registration point id");
        if (id.isBlank()) throw new IllegalArgumentException("registration point id must not be blank");
        this.contributor = Objects.requireNonNull(contributor, "contributor interface");
        if (!contributor.isInterface()) throw new IllegalArgumentException("contributor must be an interface");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.hook = Objects.requireNonNull(hook, "domain hook");
    }

    public static <P extends TopoContribution, A> RegistrationPoint<P, A> of(
                                                                             String id, Class<P> contributor, RegistrationDomain<A> domain, BiConsumer<P, A> hook) {
        return new RegistrationPoint<>(id, contributor, domain, hook);
    }

    public String id() {
        return id;
    }

    Class<P> contributor() {
        return contributor;
    }

    void invoke(TopoPlugin plugin) {
        if (contributor.isInstance(plugin)) hook.accept(contributor.cast(plugin), plugin.domain(domain));
    }

    @Override
    public String toString() {
        return id;
    }
}
