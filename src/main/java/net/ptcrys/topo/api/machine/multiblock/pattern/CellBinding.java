package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.data.DataKey;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Cell 的内容声明：组合检测算法与输入，并持有候选、角色和字段声明；它不是另一套检测接口。 */
public final class CellBinding {

    @FunctionalInterface
    private interface BoundInput {

        CellResult evaluate(CellContext context, DataTransaction metadata);
    }

    private final BoundInput bound;
    private final Supplier<List<Block>> candidates;
    private final List<PartRole> roles;
    private final List<DataKey<?>> keys;

    private CellBinding(BoundInput bound, Supplier<List<Block>> candidates, List<PartRole> roles, List<DataKey<?>> keys) {
        this.bound = bound;
        this.candidates = candidates;
        this.roles = List.copyOf(roles);
        this.keys = List.copyOf(keys);
    }

    /** 输入须为不可变声明；算法须可重入。 */
    public static <I> CellBinding of(CellPredicate<I> predicate, I input) {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(input);
        return new CellBinding((context, metadata) -> predicate.evaluate(input, context, metadata), List::of, List.of(), List.of());
    }

    public CellBinding candidates(Supplier<List<Block>> candidates) {
        return new CellBinding(bound, Objects.requireNonNull(candidates), roles, keys);
    }

    public CellBinding roles(List<PartRole> roles) {
        return new CellBinding(bound, candidates, roles, keys);
    }

    /** 在控制器构造期预声明；多个 Cell 共享同一个键实例。 */
    public CellBinding metadata(DataKey<?>... keys) {
        return new CellBinding(bound, candidates, roles, Stream.concat(this.keys.stream(), Stream.of(keys)).distinct().toList());
    }

    public List<DataKey<?>> metadataKeys() {
        return keys;
    }

    public List<Block> candidateBlocks() {
        return List.copyOf(candidates.get());
    }

    public List<PartRole> partRoles() {
        return roles;
    }

    /** 单元级事务边界：失败、未知或元数据冲突时，不泄漏本单元的写入。 */
    public CellResult evaluate(CellContext context, DataTransaction metadata) {
        DataTransaction branch = metadata.fork();
        CellResult result;
        try {
            result = Objects.requireNonNull(bound.evaluate(context, branch));
        } catch (DataTransaction.Conflict conflict) {
            return CellResult.fail(IdHelper.topo("cell/metadata_conflict"), Map.of("key", conflict.key, "value", String.valueOf(conflict.expected)), Map.of("key", conflict.key, "value", String.valueOf(conflict.actual)));
        }
        if (result.passed()) metadata.accept(branch);
        return result;
    }
}
