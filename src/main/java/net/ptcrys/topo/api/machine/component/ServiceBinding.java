package net.ptcrys.topo.api.machine.component;

import java.util.Objects;

record ServiceBinding<T extends MachineComponent, A, C>(
                                                        ServiceKey<A, C> service,
                                                        ServiceProvider<? super T, A, C> provider)
        implements ComponentKey.ServiceMetadata<A, C> {

    ServiceBinding {
        Objects.requireNonNull(service, "component service");
        Objects.requireNonNull(provider, "component service provider");
    }
}
