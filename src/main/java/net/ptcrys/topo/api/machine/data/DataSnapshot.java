package net.ptcrys.topo.api.machine.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 通用值字段的隔离快照。仅保存工作数据，不替代 DataScope 的正式存储；可交给异步计算。 */
public final class DataSnapshot {

    private final Map<DataKey<?>, Object> values;
    private final Set<DataKey<?>> assigned;
    private final Map<DataKey<?>, Object> requirements;

    DataSnapshot(Map<DataKey<?>, Object> values, Set<DataKey<?>> assigned) {
        this(values, assigned, Map.of());
    }

    DataSnapshot(Map<DataKey<?>, Object> values, Set<DataKey<?>> assigned, Map<DataKey<?>, Object> requirements) {
        this.assigned = Set.copyOf(assigned);
        Map<DataKey<?>, Object> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> copied.put(key, copy(key, value)));
        this.values = Collections.unmodifiableMap(copied);
        Map<DataKey<?>, Object> copiedRequirements = new LinkedHashMap<>();
        requirements.forEach((key, value) -> copiedRequirements.put(key, copy(key, value)));
        this.requirements = Collections.unmodifiableMap(copiedRequirements);
    }

    public static DataSnapshot defaults(Iterable<DataKey<?>> keys) {
        Map<DataKey<?>, Object> values = new LinkedHashMap<>();
        for (DataKey<?> key : keys) {
            for (DataKey<?> existing : values.keySet()) {
                if (existing.id().equals(key.id()) && existing != key) throw new IllegalArgumentException("Conflicting data key: " + key.id());
            }
            values.put(key, key.initialValue());
        }
        return new DataSnapshot(values, Set.of(), Map.of());
    }

    public Set<DataKey<?>> assignedKeys() {
        return assigned;
    }

    /** 调用方给定的配置要求；与已探测事实分开保存。 */
    public Set<DataKey<?>> requiredKeys() {
        return requirements.keySet();
    }

    public boolean required(DataKey<?> key) {
        return requirements.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T requirement(DataKey<T> key) {
        Object value = requirements.get(key);
        if (value == null) throw new IllegalArgumentException("No requirement for data key: " + key.id());
        return key.copy((T) value);
    }

    public Set<DataKey<?>> keys() {
        return values.keySet();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DataKey<T> key) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Undeclared data key: " + key.id());
        Object observed = values.get(key);
        Object value = assigned.contains(key) ? observed : requirements.getOrDefault(key, observed);
        return key.copy((T) value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DataSnapshot snapshot && values.equals(snapshot.values) && assigned.equals(snapshot.assigned) && requirements.equals(snapshot.requirements);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(values, assigned, requirements);
    }

    Map<DataKey<?>, Object> sharedValues() {
        return new LinkedHashMap<>(values);
    }

    Map<DataKey<?>, Object> sharedRequirements() {
        return new LinkedHashMap<>(requirements);
    }

    public DataTransaction edit() {
        return new DataTransaction(this, false);
    }

    /** 验证工作区允许读取或重写相同值，拒绝用新事实覆盖已确认的基线。 */
    public DataTransaction verify() {
        return new DataTransaction(this, true);
    }

    @SuppressWarnings("unchecked")
    static <T> Object copy(DataKey<T> key, Object value) {
        return key.copy((T) value);
    }
}
