package net.ptcrys.topo.api.machine.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 通用值字段的隔离快照。仅保存工作数据，不替代 DataScope 的正式存储；可交给异步计算。 */
public final class DataSnapshot {

    private final Map<DataKey<?>, Object> values;
    private final Set<DataKey<?>> assigned;

    DataSnapshot(Map<DataKey<?>, Object> values, Set<DataKey<?>> assigned) {
        this.assigned = Set.copyOf(assigned);
        Map<DataKey<?>, Object> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> copied.put(key, copy(key, value)));
        this.values = java.util.Collections.unmodifiableMap(copied);
    }

    public static DataSnapshot defaults(Iterable<DataKey<?>> keys) {
        Map<DataKey<?>, Object> values = new LinkedHashMap<>();
        for (DataKey<?> key : keys) {
            for (DataKey<?> existing : values.keySet()) {
                if (existing.id().equals(key.id()) && existing != key) throw new IllegalArgumentException("Conflicting data key: " + key.id());
            }
            values.put(key, key.initialValue());
        }
        return new DataSnapshot(values, Set.of());
    }

    public Set<DataKey<?>> assignedKeys() {
        return assigned;
    }

    public Set<DataKey<?>> keys() {
        return values.keySet();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DataKey<T> key) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Undeclared data key: " + key.id());
        return key.copy((T) values.get(key));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DataSnapshot snapshot && values.equals(snapshot.values) && assigned.equals(snapshot.assigned);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(values, assigned);
    }

    Map<DataKey<?>, Object> sharedValues() {
        return new LinkedHashMap<>(values);
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
