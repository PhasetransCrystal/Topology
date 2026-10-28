package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.api.plugin.RegistrationDomain;
import net.ptcrys.topo.api.api.plugin.TopoContribution;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Internal typed dispatch: binds a domain hook to its API and its module-owned schedule. */
public final class RegistrationPoint<P extends TopoContribution, A> {

    private final Identifier id;
    private final Class<P> contributor;
    private final RegistrationDomain<A> domain;
    private final BiConsumer<P, A> hook;

    private RegistrationPoint(Identifier id, Class<P> contributor, RegistrationDomain<A> domain, BiConsumer<P, A> hook) {
        this.id = Objects.requireNonNull(id, "registration point id");
        this.contributor = Objects.requireNonNull(contributor, "contributor interface");
        if (!contributor.isInterface()) throw new IllegalArgumentException("contributor must be an interface");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.hook = Objects.requireNonNull(hook, "domain hook");
    }

    public static <P extends TopoContribution, A> RegistrationPoint<P, A> of(Identifier id, Class<P> contributor, RegistrationDomain<A> domain, BiConsumer<P, A> hook) {
        return new RegistrationPoint<>(id, contributor, domain, hook);
    }

    public Identifier id() {
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
        return id.toString();
    }
}
