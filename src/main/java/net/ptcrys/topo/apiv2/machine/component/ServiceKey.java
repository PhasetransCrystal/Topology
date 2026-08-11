package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.apiv2.OfficialOIAPIPlugin;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Stable typed API-view handle exposed by mounted traits. */
public final class ServiceKey<A, C> {

    private final Identifier id;
    private final Class<A> apiType;
    private final Class<C> contextType;

    private ServiceKey(Identifier id, Class<A> apiType, Class<C> contextType) {
        this.id = Objects.requireNonNull(id, "trait capability id");
        this.apiType = Objects.requireNonNull(apiType, "trait capability API type");
        this.contextType = Objects.requireNonNull(contextType, "trait capability context type");
    }

    public static <A, C> ServiceKey<A, C> of(Identifier id, Class<A> apiType, Class<C> contextType) {
        return new ServiceKey<>(id, apiType, contextType);
    }

    /**
     * OI-host service key under the official API plugin namespace. Third-party mods must use
     * {@link #of(Identifier, Class, Class)} with their own plugin {@code machine().id(path)}.
     */
    public static <A, C> ServiceKey<A, C> oi(String path, Class<A> apiType, Class<C> contextType) {
        return of(OfficialOIAPIPlugin.INSTANCE.machine().id(path), apiType, contextType);
    }

    public Identifier id() {
        return id;
    }

    public Class<A> apiType() {
        return apiType;
    }

    public Class<C> contextType() {
        return contextType;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ServiceKey<?, ?> other && id.equals(other.id) && apiType.equals(other.apiType) && contextType.equals(other.contextType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, apiType, contextType);
    }

    A cast(Object value) {
        if (!apiType.isInstance(value)) {
            String actualType = value == null ? "null" : value.getClass().getName();
            throw new IllegalStateException(
                    "Trait capability '" + id + "' expected " + apiType.getName() + " but got " + actualType);
        }
        return apiType.cast(value);
    }
}
