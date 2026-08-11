package net.ptcrys.topo.api.api.infrastructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * KHS registry.
 *
 * <p>
 * K is the stable key, H is the registered handle, and S is the strategy that can execute
 * with the handle alone.
 *
 * <p>
 * Registration is single-threaded (bootstrap) and mutates {@link #registration}. {@link #freeze()}
 * publishes an immutable {@link Frozen} snapshot through a volatile field; runtime reads are then
 * served from that snapshot without per-read allocation. Before freeze, the views materialize a
 * copy of the live registration (bootstrap-only cold path) so callers never observe a silently
 * empty registry.
 *
 * <p>
 * For use-token registries (a handle H publishes per-use data D), D provenance is guaranteed at
 * compile time by package-private use constructors and the {@code protected} {@code use(D)} entry
 * on each handle; {@link #verifyOwnedHandle} adds the runtime check that a use may only be
 * published from the exact handle registered under the matching key.
 */
public final class FreezableStrategyRegistry<K, H, S> {

    private final String name;
    private final Map<K, Entry<K, H, S>> registration = new LinkedHashMap<>();
    private volatile Frozen<K, H, S> frozen;

    private FreezableStrategyRegistry(String name) {
        this.name = name;
    }

    public static <K, H, S> FreezableStrategyRegistry<K, H, S> create(String name) {
        return new FreezableStrategyRegistry<>(name);
    }

    public H register(K key, H handle, S strategy) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(strategy, "strategy");
        if (frozen != null) {
            throw new IllegalStateException(name + ": cannot register '" + key + "' after freeze");
        }
        if (registration.containsKey(key)) {
            throw new IllegalStateException(name + ": duplicate key '" + key + "'");
        }
        registration.put(key, new Entry<>(key, handle, strategy, -1));
        return handle;
    }

    public H get(K key) {
        Entry<K, H, S> entry = entry(key);
        return entry == null ? null : entry.handle();
    }

    public H require(K key) {
        H handle = get(key);
        if (handle == null) {
            throw new IllegalStateException(name + ": no entry for key '" + key + "'");
        }
        return handle;
    }

    public Optional<H> optional(K key) {
        return Optional.ofNullable(get(key));
    }

    public S strategy(K key) {
        Entry<K, H, S> entry = entry(key);
        return entry == null ? null : entry.strategy();
    }

    public Entry<K, H, S> entry(K key) {
        Frozen<K, H, S> snapshot = frozen;
        return (snapshot != null ? snapshot.byKey() : registration).get(key);
    }

    public List<Entry<K, H, S>> entriesView() {
        Frozen<K, H, S> snapshot = frozen;
        return snapshot != null ? snapshot.entries() : List.copyOf(registration.values());
    }

    public List<H> handlesView() {
        Frozen<K, H, S> snapshot = frozen;
        if (snapshot != null) {
            return snapshot.handles();
        }
        List<H> handles = new ArrayList<>(registration.size());
        for (Entry<K, H, S> entry : registration.values()) {
            handles.add(entry.handle());
        }
        return Collections.unmodifiableList(handles);
    }

    public Set<K> keysView() {
        Frozen<K, H, S> snapshot = frozen;
        return snapshot != null ? snapshot.keys() : Collections.unmodifiableSet(new LinkedHashSet<>(registration.keySet()));
    }

    public boolean isFrozen() {
        return frozen != null;
    }

    public void freeze() {
        if (frozen != null) {
            return;
        }
        Map<K, Entry<K, H, S>> byKey = new LinkedHashMap<>();
        List<Entry<K, H, S>> entries = new ArrayList<>(registration.size());
        List<H> handles = new ArrayList<>(registration.size());
        int index = 0;
        for (Entry<K, H, S> entry : registration.values()) {
            Entry<K, H, S> frozenEntry = new Entry<>(entry.key(), entry.handle(), entry.strategy(), index++);
            byKey.put(frozenEntry.key(), frozenEntry);
            entries.add(frozenEntry);
            handles.add(frozenEntry.handle());
        }
        Set<K> keys = Collections.unmodifiableSet(new LinkedHashSet<>(byKey.keySet()));
        this.frozen = new Frozen<>(
                Collections.unmodifiableMap(byKey),
                Collections.unmodifiableList(entries),
                Collections.unmodifiableList(handles),
                keys);
        registration.clear();
    }

    /**
     * Verifies {@code handle} is the exact handle registered in this registry under {@code key}
     * before a use is published. Throws if the key is unregistered or the handle is a foreign
     * instance, so a caller cannot fabricate a handle/data pairing against this registry.
     */
    public void verifyOwnedHandle(K key, H handle) {
        Objects.requireNonNull(handle, "handle");
        if (require(key) != handle) {
            throw new IllegalArgumentException(
                    "handle for key '" + key + "' does not belong to this registry");
        }
    }

    public record Entry<K, H, S>(K key, H handle, S strategy, int runtimeIndex) {}

    private record Frozen<K, H, S>(
                                   Map<K, Entry<K, H, S>> byKey,
                                   List<Entry<K, H, S>> entries,
                                   List<H> handles,
                                   Set<K> keys) {}
}
