package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.data.DataKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 内置检测算法与 Cell 内容声明工厂。算法为无绑定、可重入的实例，具体内容保存在返回的 CellBinding 中。 */
public final class CellPredicates {

    private CellPredicates() {}

    private enum Unit {
        INSTANCE
    }

    private static CellResult result(boolean matches, String reason, Object expected, CellContext context) {
        if (matches) return CellResult.pass();
        if (context.actual() == null) return CellResult.unknown();
        return CellResult.failure(context.actual() == null || context.actual().isAir() ? "empty" : reason, expected, context.actual());
    }

    private static final CellPredicate<Unit> CONTROLLER = (input, context, metadata) -> result(context.controller(), "controller", "controller", context);
    private static final CellPredicate<List<Block>> BLOCK = (blocks, context, metadata) -> result(context.actual() != null && blocks.contains(context.actual().getBlock()), "block", blocks, context);
    private static final CellPredicate<Supplier<? extends Block>> LAZY_BLOCK = (supplier, context, metadata) -> BLOCK.evaluate(List.of(Objects.requireNonNull(supplier.get())), context, metadata);
    private static final CellPredicate<Unit> AIR = (input, context, metadata) -> result(context.actual() != null && context.actual().isAir(), "block", "air", context);
    private static final CellPredicate<TagKey<Block>> TAG = (tag, context, metadata) -> result(context.actual() != null && context.actual().is(tag), "block", tag, context);
    private static final CellPredicate<BlockState> STATE = (state, context, metadata) -> result(context.actual() == state, context.actual() != null && context.actual().getBlock() == state.getBlock() ? "property" : "block", state, context);
    private static final CellPredicate<PropertyValue<?>> PROPERTY = (input, context, metadata) -> result(input.matches(context.actual()), "property", input, context);
    private static final CellPredicate<Unit> MEMBER = (input, context, metadata) -> result(context.info().isMember(), "member", "machine member", context);
    private static final CellPredicate<Unit> REPLACEABLE = (input, context, metadata) -> result(context.actual() != null && context.actual().canBeReplaced(), "block", "replaceable", context);
    private static final CellPredicate<PartRole> ROLE = (role, context, metadata) -> result(context.info().roles().contains(role), "role", role.id(), context);
    private static final CellPredicate<CellBinding> NOT = (binding, context, metadata) -> {
        CellResult child = binding.evaluate(context, metadata.fork());
        return child.status() == CellResult.Status.UNKNOWN ? child : child.passed() ? CellResult.failure("not", "not matching", context.actual()) : CellResult.pass();
    };
    private static final CellPredicate<List<CellBinding>> ALL = (bindings, context, metadata) -> {
        List<CellResult> issues = new ArrayList<>();
        for (CellBinding binding : bindings) {
            CellResult child = binding.evaluate(context, metadata);
            if (!child.passed()) {
                issues.add(child);
            }
        }
        if (issues.isEmpty()) return CellResult.pass();
        boolean failed = issues.stream().anyMatch(issue -> issue.status() == CellResult.Status.FAIL);
        return CellResult.combine("all", failed ? CellResult.Status.FAIL : CellResult.Status.UNKNOWN, issues);
    };
    private static final CellPredicate<List<CellBinding>> ANY = (bindings, context, metadata) -> {
        List<CellResult> issues = new ArrayList<>();
        for (CellBinding binding : bindings) {
            CellResult child = binding.evaluate(context, metadata);
            if (child.passed()) return child;
            issues.add(child);
        }
        return CellResult.combine("any", issues.stream().anyMatch(issue -> issue.status() == CellResult.Status.UNKNOWN) ? CellResult.Status.UNKNOWN : CellResult.Status.FAIL, issues);
    };

    /** @ 自动使用的位置锚点检测。 */
    public static CellBinding controller() {
        return CellBinding.of(CONTROLLER, Unit.INSTANCE);
    }

    /** 方块集合是 Cell 的声明输入，不保存在检测算法中。 */
    public static CellBinding block(Block... blocks) {
        List<Block> content = List.of(blocks);
        return CellBinding.of(BLOCK, content).candidates(() -> content);
    }

    /** 延迟读取注册内容；供应者必须在注册完成后稳定且可并发读取。 */
    public static CellBinding block(Supplier<? extends Block> supplier) {
        var content = new LazyBlock(Objects.requireNonNull(supplier));
        return CellBinding.of(LAZY_BLOCK, content).candidates(() -> List.of(content.get()));
    }

    public static CellBinding air() {
        return CellBinding.of(AIR, Unit.INSTANCE);
    }

    public static CellBinding blockTag(TagKey<Block> tag) {
        return CellBinding.of(TAG, tag);
    }

    public static CellBinding state(BlockState state) {
        return CellBinding.of(STATE, state).candidates(() -> List.of(state.getBlock()));
    }

    public static <T extends Comparable<T>> CellBinding property(Property<T> property, T value) {
        return CellBinding.of(PROPERTY, new PropertyValue<>(property, value));
    }

    public static CellBinding member() {
        return CellBinding.of(MEMBER, Unit.INSTANCE);
    }

    public static CellBinding replaceable() {
        return CellBinding.of(REPLACEABLE, Unit.INSTANCE);
    }

    /** 取反子分支始终丢弃写入，未知不会变成成功。 */
    public static CellBinding not(CellBinding binding) {
        return CellBinding.of(NOT, binding).metadata(binding.metadataKeys().toArray(DataKey<?>[]::new));
    }

    public static CellBinding allOf(CellBinding... bindings) {
        return combine(true, bindings);
    }

    public static CellBinding anyOf(CellBinding... bindings) {
        return combine(false, bindings);
    }

    public static CellBinding role(PartRole role) {
        return CellBinding.of(ROLE, role).roles(List.of(role)).candidates(() -> {
            List<Block> blocks = new ArrayList<>();
            for (MachineDefinition definition : Machines.registered()) {
                for (PartRoleAttachment attachment : definition.metadata(PartRoleAttachment.TYPE)) {
                    if (attachment.role() == role) {
                        blocks.add(definition.registeredBlock().get());
                        break;
                    }
                }
            }
            return List.copyOf(blocks);
        });
    }

    private static CellBinding combine(boolean all, CellBinding[] bindings) {
        if (bindings.length < 2) throw new IllegalArgumentException("Combination needs at least two bindings");
        List<CellBinding> children = List.of(bindings);
        List<PartRole> roles = children.stream().flatMap(child -> child.partRoles().stream()).distinct().toList();
        return CellBinding.of(all ? ALL : ANY, children).roles(roles.stream().distinct().toList())
                .metadata(children.stream().flatMap(child -> child.metadataKeys().stream()).distinct().toArray(DataKey<?>[]::new))
                .candidates(() -> children.stream().flatMap(child -> child.candidateBlocks().stream()).distinct().toList());
    }

    /** 注册内容的惰性缓存属于声明输入，不放入复用算法；volatile 发布保证快照工作线程读取同一方块。 */
    private static final class LazyBlock implements Supplier<Block> {

        private final Supplier<? extends Block> source;
        private volatile Block cached;

        LazyBlock(Supplier<? extends Block> source) {
            this.source = source;
        }

        @Override
        public Block get() {
            Block result = cached;
            if (result == null) {
                result = Objects.requireNonNull(source.get(), "blueprint block");
                cached = result;
            }
            return result;
        }
    }

    private record PropertyValue<T extends Comparable<T>>(Property<T> property, T value) {

        PropertyValue {
            Objects.requireNonNull(property);
            Objects.requireNonNull(value);
        }

        boolean matches(BlockState state) {
            return state != null && state.hasProperty(property) && value.equals(state.getValue(property));
        }
    }
}
