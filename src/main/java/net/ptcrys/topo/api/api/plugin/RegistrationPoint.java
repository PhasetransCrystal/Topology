package net.ptcrys.topo.api.api.plugin;

import java.util.Objects;

/** Typed contribution slot. Its module owns scheduling and the freeze after all contributions. */
public final class RegistrationPoint<A> {

    private final String id;
    private final RegistrationDomain<A> domain;

    private RegistrationPoint(String id, RegistrationDomain<A> domain) {
        this.id = Objects.requireNonNull(id, "registration point id");
        if (id.isBlank()) throw new IllegalArgumentException("registration point id must not be blank");
        this.domain = Objects.requireNonNull(domain, "registration domain");
    }

    public static <A> RegistrationPoint<A> of(String id, RegistrationDomain<A> domain) {
        return new RegistrationPoint<>(id, domain);
    }

    public String id() {
        return id;
    }

    A api(TopoPlugin plugin) {
        return plugin.domain(domain);
    }

    @Override
    public String toString() {
        return id;
    }
}
