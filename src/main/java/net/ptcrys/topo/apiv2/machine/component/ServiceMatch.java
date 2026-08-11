package net.ptcrys.topo.apiv2.machine.component;

import java.util.Objects;

/** A mounted trait capability value, preserving the provider trait's stable mounted key. */
public record ServiceMatch<A>(ComponentKey<?> key, A value) {

    public ServiceMatch {
        Objects.requireNonNull(key, "trait key");
        Objects.requireNonNull(value, "trait capability value");
    }
}
