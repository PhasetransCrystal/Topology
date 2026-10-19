package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One namespace owner and its typed contributions. Subclasses only configure the registration
 * points they use; the host owns event wiring, ordering, content generation and freeze fences.
 */
public abstract class TopoPlugin {

    private final RegistryCore registry;
    private final Map<RegistrationDomain<?>, Object> domains = new IdentityHashMap<>();

    protected TopoPlugin(RegistryCore registry) {
        this.registry = Objects.requireNonNull(registry, "plugin registry");
    }

    public final String modId() {
        return registry.getModid();
    }

    public final RegistryCore registry() {
        return registry;
    }

    /** Only describe contributions here; content writes belong inside the registered callbacks. */
    protected abstract void configure(PluginRegistration registration);

    /** Additional domains use this same entry, without adding methods to the plugin contract. */
    @SuppressWarnings("unchecked")
    public final synchronized <A> A domain(RegistrationDomain<A> domain) {
        Objects.requireNonNull(domain, "domain");
        Object api = domains.get(domain);
        if (api == null) {
            api = domain.create(this);
            domains.put(domain, api);
        }
        return (A) api;
    }

    public final MaterialDomainRegistration material() {
        return domain(MaterialDomainRegistration.DOMAIN);
    }

    public final EquipmentDomainRegistration equipment() {
        return domain(EquipmentDomainRegistration.DOMAIN);
    }

    public final RecipeDomainRegistration recipe() {
        return domain(RecipeDomainRegistration.DOMAIN);
    }

    public final MachineDomainRegistration machine() {
        return domain(MachineDomainRegistration.DOMAIN);
    }

    public final OreDomainRegistration ore() {
        return domain(OreDomainRegistration.DOMAIN);
    }

    public final LangDomainRegistration lang() {
        return domain(LangDomainRegistration.DOMAIN);
    }

    final String identity() {
        return modId() + ":" + getClass().getName();
    }
}
