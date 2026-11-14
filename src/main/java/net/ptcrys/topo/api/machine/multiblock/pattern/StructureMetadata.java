package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.data.DataKey;
import net.ptcrys.topo.api.machine.data.DataScope;
import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.helper.IdHelper;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 控制器元数据域的组合适配器。正式值归机器 DataScope 管理，检测只使用隔离工作区。 */
public final class StructureMetadata {

    /** 控制器的整体变换，包含镜像；-1 表示尚未探测。序号对应 Orientations.all()。 */
    public static final DataKey<Integer> ORIENTATION = DataKey.immutable(IdHelper.topo("structure/orientation"), Codec.INT, -1);
    /** 本体使用 body，模块使用 module/ID；副本数按各自蓝图的段声明顺序排列。 */
    public static final DataKey<Map<String, List<Integer>>> REPEATS = new DataKey<>(IdHelper.topo("structure/repeats"), Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()), Map.of(), value -> {
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        value.forEach((key, counts) -> copy.put(key, List.copyOf(counts)));
        return Map.copyOf(copy);
    });
    private final DataScope scope;
    private final DataSnapshot defaults;

    public StructureMetadata(DataScope scope, List<Blueprint> blueprints) {
        this.scope = scope;
        this.defaults = defaults(blueprints);
        for (DataKey<?> key : defaults.keys()) declare(scope, key);
    }

    private static <T> void declare(DataScope scope, DataKey<T> key) {
        scope.valueField(key).persisted().syncNone().done();
    }

    public DataSnapshot snapshot() {
        return scope.snapshotValues();
    }

    public DataSnapshot defaults() {
        return defaults;
    }

    public void commit(DataSnapshot snapshot) {
        scope.commitValues(snapshot);
    }

    public void invalidate() {
        scope.commitValues(defaults);
    }

    public static DataSnapshot defaults(List<Blueprint> blueprints) {
        List<DataKey<?>> keys = new ArrayList<>(List.of(ORIENTATION, REPEATS));
        for (Blueprint blueprint : blueprints) keys.addAll(blueprint.metadataKeys());
        return DataSnapshot.defaults(keys);
    }

    /** 框架几何事实由引擎维护，不允许各模块独立改变整体镜像。 */
    static void geometry(DataTransaction metadata, Orientation orientation, RepeatResolution repeats, String part) {
        int index = Orientations.all().indexOf(orientation);
        metadata.set(ORIENTATION, index);
        Map<String, List<Integer>> counts = new LinkedHashMap<>(metadata.get(REPEATS));
        counts.put(part, Arrays.stream(repeats.countsCopy()).boxed().toList());
        metadata.set(REPEATS, Map.copyOf(counts));
    }
}
