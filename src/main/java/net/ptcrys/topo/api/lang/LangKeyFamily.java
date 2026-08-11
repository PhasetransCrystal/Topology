package net.ptcrys.topo.api.lang;

import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A set of {@link LangKey}s indexed by K. Members are registered through a plugin
 * {@link LangDomainRegistration} (no bare {@link LangRegistry} path).
 */
public final class LangKeyFamily<K> {

    private final Map<K, LangKey> byKey;

    private LangKeyFamily(Map<K, LangKey> byKey) {
        this.byKey = byKey;
    }

    public LangKey get(K k) {
        LangKey handle = byKey.get(k);
        if (handle == null) {
            throw new IllegalArgumentException("no lang key for family member: " + k);
        }
        return handle;
    }

    /**
     * Enum family: one key per constant via {@code lang.key(category, pathFn(e), en, cn)}.
     */
    public static <E extends Enum<E>> LangKeyFamily<E> ofEnum(
                                                              LangDomainRegistration lang,
                                                              String category,
                                                              Class<E> type,
                                                              Function<E, String> pathFn,
                                                              Function<E, String> enFn,
                                                              Function<E, String> cnFn) {
        Map<E, LangKey> map = new LinkedHashMap<>();
        for (E e : type.getEnumConstants()) {
            map.put(e, lang.key(category, pathFn.apply(e), enFn.apply(e), cnFn.apply(e)));
        }
        return new LangKeyFamily<>(map);
    }

    /**
     * Derived family from an already-frozen source registry (call after source freeze).
     */
    public static <K> LangKeyFamily<K> derived(
                                               LangDomainRegistration lang,
                                               String category,
                                               Iterable<K> sources,
                                               Function<K, String> pathFn,
                                               Function<K, String> enFn,
                                               Function<K, String> cnFn) {
        Map<K, LangKey> map = new LinkedHashMap<>();
        for (K source : sources) {
            map.put(source, lang.key(category, pathFn.apply(source), enFn.apply(source), cnFn.apply(source)));
        }
        return new LangKeyFamily<>(map);
    }
}
