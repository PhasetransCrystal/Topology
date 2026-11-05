package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Factories for the built-in {@link CellPredicate} kinds. These return small immutable
 * implementations directly; predicates are plain values referenced from blueprint declarations,
 * never registry entries.
 *
 * <p>
 * {@link #controller()} and {@link #role(PartRole)} resolve against the immutable
 * {@link StructureView}: no world or block entity lookups happen inside predicates.
 */
public final class CellPredicates {

    private CellPredicates() {}

    /** Marks the controller origin cell ({@code '@'}); the origin is located structurally. */
    public static CellPredicate controller() {
        return ControllerPredicate.INSTANCE;
    }

    /** Matches any of the given blocks by block identity. */
    public static CellPredicate block(Block... blocks) {
        Objects.requireNonNull(blocks, "blocks");
        return new BlockPredicate(List.of(blocks));
    }

    /**
     * Matches a lazily-resolved block by identity. Blueprints are built during bootstrap, before the
     * block registry event has constructed modded blocks, so a blueprint cell referencing a mod block
     * passes its registry entry ({@code DENSE_STRUCTURE_CASING} is a {@code Supplier<Block>}) and resolution
     * happens on first recognition/candidate query — long after registration.
     */
    public static CellPredicate block(Supplier<? extends Block> block) {
        return new LazyBlockPredicate(Objects.requireNonNull(block, "block supplier"));
    }

    /** Matches an air block (empty blueprint space that must remain clear). */
    public static CellPredicate air() {
        return AirPredicate.INSTANCE;
    }

    /** Matches any block in {@code tag}. */
    public static CellPredicate blockTag(TagKey<Block> tag) {
        return new BlockTagPredicate(Objects.requireNonNull(tag, "block tag"));
    }

    /** Matches an exact block state, including every property value. */
    public static CellPredicate state(BlockState state) {
        return new StatePredicate(Objects.requireNonNull(state, "block state"));
    }

    /** Matches a state carrying {@code property = value}. */
    public static <T extends Comparable<T>> CellPredicate property(Property<T> property, T value) {
        return new PropertyPredicate<>(Objects.requireNonNull(property, "property"), Objects.requireNonNull(value, "property value"));
    }

    /** Matches a cell occupied by any machine member (not necessarily a particular part role). */
    public static CellPredicate member() {
        return MemberPredicate.INSTANCE;
    }

    /** Matches a block that can be replaced (air, plants, snow layers, …). */
    public static CellPredicate replaceable() {
        return ReplaceablePredicate.INSTANCE;
    }

    /** Inverts a predicate. Capability candidates are intentionally not inherited. */
    public static CellPredicate not(CellPredicate predicate) {
        return new NotPredicate(Objects.requireNonNull(predicate, "predicate"));
    }

    /** Matches only when every predicate matches (logical AND). */
    public static CellPredicate allOf(CellPredicate... predicates) {
        Objects.requireNonNull(predicates, "cell predicates");
        if (predicates.length < 2) {
            throw new IllegalArgumentException("allOf needs at least two predicates");
        }
        return new AllOfPredicate(List.of(predicates));
    }

    /** Matches a placed part that exposes the given {@link PartRole} (strong key). */
    public static CellPredicate role(PartRole capability) {
        return new CapabilityPredicate(Objects.requireNonNull(capability, "part capability"));
    }

    /**
     * Matches when <b>any</b> of the given predicates matches (logical OR). Capability candidates and
     * {@link CellPredicate#partRoles()} are the union of all branches, so blueprint count
     * validation and JEI display see every alternative — the classic "casing or hatch" wall cell.
     */
    public static CellPredicate anyOf(CellPredicate... predicates) {
        Objects.requireNonNull(predicates, "cell predicates");
        if (predicates.length < 2) {
            throw new IllegalArgumentException("anyOf needs at least two alternatives");
        }
        return new AnyOfPredicate(List.of(predicates));
    }

    private enum ControllerPredicate implements CellPredicate {

        INSTANCE;

        @Override
        public boolean test(StructureView view, BlockPos worldPos, Cell expected) {
            return view.isController(worldPos);
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return true;
        }
    }

    private record BlockPredicate(List<Block> blocks) implements CellPredicate {

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && blocks.contains(actual.getBlock());
        }

        @Override
        public List<ItemStack> candidates() {
            List<ItemStack> stacks = new ArrayList<>(blocks.size());
            for (Block block : blocks) {
                stacks.add(new ItemStack(block));
            }
            return stacks;
        }
    }

    private static final class LazyBlockPredicate implements CellPredicate {

        private final Supplier<? extends Block> supplier;
        /** Resolved on first query; registries are frozen well before recognition runs. */
        private volatile @Nullable Block resolved;

        private LazyBlockPredicate(Supplier<? extends Block> supplier) {
            this.supplier = supplier;
        }

        private Block block() {
            Block block = resolved;
            if (block == null) {
                block = Objects.requireNonNull(supplier.get(), "lazily-resolved blueprint block");
                resolved = block;
            }
            return block;
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && actual.getBlock() == block();
        }

        @Override
        public List<ItemStack> candidates() {
            return List.of(new ItemStack(block()));
        }
    }

    private enum AirPredicate implements CellPredicate {

        INSTANCE;

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && actual.isAir();
        }
    }

    private record BlockTagPredicate(TagKey<Block> tag) implements CellPredicate {

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && actual.is(tag);
        }
    }

    private record StatePredicate(BlockState state) implements CellPredicate {

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual == state;
        }

        @Override
        public List<ItemStack> candidates() {
            return List.of(new ItemStack(state.getBlock()));
        }
    }

    private record PropertyPredicate<T extends Comparable<T>>(Property<T> property, T value) implements CellPredicate {

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && actual.hasProperty(property) && value.equals(actual.getValue(property));
        }
    }

    private enum MemberPredicate implements CellPredicate {

        INSTANCE;

        @Override
        public boolean test(StructureView view, BlockPos worldPos, Cell expected) {
            return view.isMember(worldPos);
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return false;
        }
    }

    private enum ReplaceablePredicate implements CellPredicate {

        INSTANCE;

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && actual.canBeReplaced();
        }
    }

    private record NotPredicate(CellPredicate delegate) implements CellPredicate {

        @Override
        public boolean test(StructureView view, BlockPos worldPos, Cell expected) {
            return !delegate.test(view, worldPos, expected);
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return !delegate.test(actual, expected);
        }
    }

    private record AllOfPredicate(List<CellPredicate> predicates) implements CellPredicate {

        @Override
        public boolean test(StructureView view, BlockPos worldPos, Cell expected) {
            for (CellPredicate predicate : predicates) {
                if (!predicate.test(view, worldPos, expected)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            for (CellPredicate predicate : predicates) {
                if (!predicate.test(actual, expected)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public List<PartRole> partRoles() {
            List<PartRole> union = new ArrayList<>();
            for (CellPredicate predicate : predicates) {
                union.addAll(predicate.partRoles());
            }
            return List.copyOf(union);
        }

        @Override
        public List<ItemStack> candidates() {
            List<ItemStack> union = new ArrayList<>();
            for (CellPredicate predicate : predicates) {
                union.addAll(predicate.candidates());
            }
            return List.copyOf(union);
        }
    }

    private record CapabilityPredicate(PartRole capability) implements CellPredicate {

        @Override
        public boolean test(StructureView view, BlockPos worldPos, Cell expected) {
            return view.hasCapability(worldPos, capability);
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            return actual != null && expected != null && actual.getBlock() == expected.getBlock();
        }

        @Override
        public List<PartRole> partRoles() {
            return List.of(capability);
        }

        /**
         * Every registered part machine carrying this capability, in registration order — resolved
         * at query time (UI/JEI), long after the machine registry froze, so the listing always
         * reflects the final content set.
         */
        @Override
        public List<ItemStack> candidates() {
            List<ItemStack> stacks = new ArrayList<>();
            for (MachineDefinition definition : Machines.registered()) {
                for (PartRoleAttachment metadata : definition.metadata(PartRoleAttachment.TYPE)) {
                    if (metadata.role() == capability) {
                        stacks.add(new ItemStack(definition.registeredBlock().get()));
                        break;
                    }
                }
            }
            return stacks;
        }
    }

    private record AnyOfPredicate(List<CellPredicate> alternatives) implements CellPredicate {

        @Override
        public boolean test(StructureView view, BlockPos worldPos, Cell expected) {
            for (CellPredicate alternative : alternatives) {
                if (alternative.test(view, worldPos, expected)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean test(@Nullable BlockState actual, @Nullable BlockState expected) {
            for (CellPredicate alternative : alternatives) {
                if (alternative.test(actual, expected)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<PartRole> partRoles() {
            List<PartRole> union = new ArrayList<>();
            for (CellPredicate alternative : alternatives) {
                union.addAll(alternative.partRoles());
            }
            return List.copyOf(union);
        }

        @Override
        public List<ItemStack> candidates() {
            List<ItemStack> union = new ArrayList<>();
            for (CellPredicate alternative : alternatives) {
                union.addAll(alternative.candidates());
            }
            return List.copyOf(union);
        }
    }
}
