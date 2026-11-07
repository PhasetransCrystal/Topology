package net.ptcrys.topo.api.machine.data;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 一次计算的可回滚工作区。调用方显式提交到所属数据域，失败试探只需丢弃副本。 */
public final class DataTransaction {

    private Map<DataKey<?>, Object> values;
    private final boolean verifying;
    private Set<DataKey<?>> assigned;
    private Set<DataKey<?>> refreshed = Set.of();

    DataTransaction(DataSnapshot source, boolean verifying) {
        this.values = source.sharedValues();
        this.assigned = new HashSet<>(source.assignedKeys());
        this.verifying = verifying;
    }

    private DataTransaction(DataTransaction source) {
        this.values = new LinkedHashMap<>(source.values);
        this.assigned = new HashSet<>(source.assigned);
        this.verifying = source.verifying;
        this.refreshed = source.refreshed;
    }

    public boolean verifying() {
        return verifying;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DataKey<T> key) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Undeclared data key: " + key.id());
        return key.copy((T) values.get(key));
    }

    public <T> void set(DataKey<T> key, T value) {
        T previous = get(key);
        if (verifying && !refreshed.contains(key) && assigned.contains(key) && !Objects.equals(previous, value)) throw new Conflict(key.id().toString(), previous, value);
        values.put(key, key.copy(value));
        assigned.add(key);
    }

    public DataSnapshot snapshot() {
        return new DataSnapshot(values, assigned);
    }

    public DataTransaction fork() {
        return new DataTransaction(this);
    }

    /** 是否已有探测事实；声明默认值本身不代表已经探测。 */
    public boolean assigned(DataKey<?> key) {
        return assigned.contains(key);
    }

    /** 复制工作区并允许刷新指定的派生字段，其余已探测事实仍受基线约束。 */
    public DataTransaction forkRefreshing(Set<DataKey<?>> keys) {
        if (!values.keySet().containsAll(keys)) throw new IllegalArgumentException("Undeclared refresh key");
        DataTransaction branch = fork();
        Set<DataKey<?>> allowed = new HashSet<>(refreshed);
        allowed.addAll(keys);
        branch.refreshed = Set.copyOf(allowed);
        return branch;
    }

    /** 接纳成功分支；键集合必须完全一致。 */
    public void accept(DataTransaction branch) {
        if (verifying != branch.verifying || !values.keySet().equals(branch.values.keySet())) throw new IllegalArgumentException("Unrelated data transaction");
        values = new LinkedHashMap<>(branch.values);
        assigned = new HashSet<>(branch.assigned);
    }

    /** 验证尝试改变基线时，由检测引擎转换为结构化错误。 */
    public static final class Conflict extends RuntimeException {

        public final String key;
        public final Object expected;
        public final Object actual;

        Conflict(String key, Object expected, Object actual) {
            super("Metadata baseline changed: " + key);
            this.key = key;
            this.expected = expected;
            this.actual = actual;
        }
    }
}
