package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.apiv2.OfficialOIAPIPlugin;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Stable identity for one mounted component instance on a machine.
 *
 * <p>
 * The id is the semantic identity: it is used for duplicate detection, typed lookup, and component
 * persistence. The type token is the runtime safety check for callers that resolve the component.
 */
public final class ComponentKey<T extends MachineComponent> {

    private final Identifier id;
    private final Class<T> type;
    private final List<ServiceBinding<T, ?, ?>> services;

    private ComponentKey(Identifier id, Class<T> type) {
        this(id, type, List.of());
    }

    private ComponentKey(
                         Identifier id,
                         Class<T> type,
                         List<ServiceBinding<T, ?, ?>> services) {
        this.id = Objects.requireNonNull(id, "component key id");
        this.type = Objects.requireNonNull(type, "component type");
        this.services = List.copyOf(services);
    }

    public static <T extends MachineComponent> ComponentKey<T> of(Identifier id, Class<T> type) {
        return new ComponentKey<>(id, type);
    }

    /**
     * OI-host component key under the official API plugin namespace. Third-party mods must use
     * {@link #of(Identifier, Class)} with their own plugin {@code machine().id(path)}.
     */
    public static <T extends MachineComponent> ComponentKey<T> oi(String path, Class<T> type) {
        return of(OfficialOIAPIPlugin.INSTANCE.machine().id(path), type);
    }

    public static <T extends MachineComponent> ComponentKey<T> oi(
                                                                  String path, Class<T> type, String langEn, String langCn) {
        ComponentKey<T> key = oi(path, type);
        OfficialOIAPIPlugin.INSTANCE.lang().resource(key.id(), "trait", langEn, langCn);
        return key;
    }

    public Identifier id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    public List<? extends ServiceMetadata<?, ?>> services() {
        return services;
    }

    List<ServiceBinding<T, ?, ?>> serviceBindings() {
        return services;
    }

    public <A, C> ComponentKey<T> service(
                                          ServiceKey<A, C> service,
                                          ServiceProvider<? super T, A, C> provider) {
        ServiceBinding<T, A, C> binding = new ServiceBinding<>(service, provider);
        List<ServiceBinding<T, ?, ?>> updated = new ArrayList<>(services);
        updated.add(binding);
        return new ComponentKey<>(id, type, updated);
    }

    public ComponentMount<T> mount(Function<? super ComponentContext<T>, ? extends T> factory) {
        return new ComponentMount<>(this, factory);
    }

    public ComponentMount<T> mount(
                                   Function<? super ComponentContext<T>, ? extends T> factory,
                                   Attachment... metadata) {
        return new ComponentMount<>(this, factory, List.of(metadata));
    }

    T cast(MachineComponent component) {
        if (!type.isInstance(component)) {
            throw new IllegalStateException(
                    "Component '" + id + "' is " + component.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(component);
    }

    /** Read-only public metadata view for a component service binding. */
    public interface ServiceMetadata<A, C> {

        ServiceKey<A, C> service();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ComponentKey<?> other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + "<" + type.getSimpleName() + ">";
    }
}
