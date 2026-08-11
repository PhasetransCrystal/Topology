package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.resource.DirectSlotResourceAccess;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import com.google.common.primitives.Ints;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Per-pattern input buffer used when an internal pattern provider runs in separation mode.
 * Each slot owns a concrete {@link RecipeSearchPoolId} (always present).
 */
final class AePatternSlotBuffer {

    static final int ITEM_SLOTS = 9;
    static final int FLUID_TANKS = 4;
    static final int FLUID_CAPACITY = 64_000;

    private RecipeSearchPoolId poolId;
    private final ItemStacksResourceHandler items;
    private final FluidStacksResourceHandler fluids;

    AePatternSlotBuffer(RecipeSearchPoolId poolId) {
        this(poolId, () -> {});
    }

    AePatternSlotBuffer(RecipeSearchPoolId poolId, Runnable onContentsChanged) {
        this.poolId = Objects.requireNonNull(poolId, "pool id");
        Runnable callback = Objects.requireNonNull(onContentsChanged, "contents changed callback");
        this.items = new ObservableItemHandler(callback);
        this.fluids = new ObservableFluidHandler(callback);
    }

    RecipeSearchPoolId poolId() {
        return poolId;
    }

    void setPoolId(RecipeSearchPoolId poolId) {
        this.poolId = Objects.requireNonNull(poolId, "pool id");
    }

    static boolean isValidSlotPoolId(RecipeSearchPoolId poolId) {
        return !poolId.isDefault() && !poolId.isUniversal() && RecipeSearchPoolId.isValidCustomToken(poolId.value());
    }

    ResourceHandler<ItemResource> items() {
        return items;
    }

    ResourceHandler<FluidResource> fluids() {
        return fluids;
    }

    boolean hasContent() {
        return AePatternProvider.hasAnyContent(items) || AePatternProvider.hasAnyContent(fluids);
    }

    void clear() {
        try (Transaction tx = Transaction.openRoot()) {
            clearInto(tx);
            tx.commit();
        }
    }

    private void clearInto(TransactionContext tx) {
        for (int i = 0; i < items.size(); i++) {
            ItemResource resource = items.getResource(i);
            long amount = items.getAmountAsLong(i);
            if (!resource.isEmpty() && amount > 0L) {
                items.extract(i, resource, Ints.saturatedCast(amount), tx);
            }
        }
        for (int i = 0; i < fluids.size(); i++) {
            FluidResource resource = fluids.getResource(i);
            long amount = fluids.getAmountAsLong(i);
            if (!resource.isEmpty() && amount > 0L) {
                fluids.extract(i, resource, Ints.saturatedCast(amount), tx);
            }
        }
    }

    void write(ValueOutput output) {
        output.putString("pool", poolId.value());
        ValueOutput.ValueOutputList itemList = output.childrenList("items");
        for (int i = 0; i < items.size(); i++) {
            ItemResource resource = items.getResource(i);
            long amount = items.getAmountAsLong(i);
            if (resource.isEmpty() || amount <= 0L) {
                continue;
            }
            ValueOutput child = itemList.addChild();
            child.putInt("i", i);
            child.store("s", ItemStack.CODEC, resource.toStack(Ints.saturatedCast(amount)));
        }
        ValueOutput.ValueOutputList fluidList = output.childrenList("fluids");
        for (int i = 0; i < fluids.size(); i++) {
            FluidResource resource = fluids.getResource(i);
            long amount = fluids.getAmountAsLong(i);
            if (resource.isEmpty() || amount <= 0L) {
                continue;
            }
            ValueOutput child = fluidList.addChild();
            child.putInt("i", i);
            child.store("s", FluidStack.CODEC, resource.toStack(Ints.saturatedCast(amount)));
        }
    }

    void read(ValueInput input, @Nullable Set<RecipeSearchPoolId> used) {
        RecipeSearchPoolId parsed = RecipeSearchPoolId.parse(input.getStringOr("pool", ""));
        if (!isValidSlotPoolId(parsed) || (used != null && used.contains(parsed))) {
            parsed = used == null ? RecipeSearchPoolId.generateCustom() : RecipeSearchPoolId.generateUnique(used);
        }
        if (used != null) {
            used.add(parsed);
        }
        poolId = parsed;

        try (Transaction tx = Transaction.openRoot()) {
            clearInto(tx);
            input.childrenList("items").ifPresent(list -> {
                for (ValueInput child : list) {
                    int index = child.getIntOr("i", -1);
                    if (index < 0 || index >= items.size()) {
                        continue;
                    }
                    child.read("s", ItemStack.CODEC).ifPresent(stack -> {
                        if (!stack.isEmpty()) {
                            items.insert(index, ItemResource.of(stack), stack.getCount(), tx);
                        }
                    });
                }
            });
            input.childrenList("fluids").ifPresent(list -> {
                for (ValueInput child : list) {
                    int index = child.getIntOr("i", -1);
                    if (index < 0 || index >= fluids.size()) {
                        continue;
                    }
                    child.read("s", FluidStack.CODEC).ifPresent(stack -> {
                        if (!stack.isEmpty()) {
                            fluids.insert(index, FluidResource.of(stack), stack.getAmount(), tx);
                        }
                    });
                }
            });
            tx.commit();
        }
    }

    private static final class ObservableItemHandler extends ItemStacksResourceHandler
                                                     implements DirectSlotResourceAccess<ItemResource> {

        private final Runnable onChanged;

        private ObservableItemHandler(Runnable onChanged) {
            super(ITEM_SLOTS);
            this.onChanged = onChanged;
        }

        @Override
        public void directSet(int index, ItemResource resource, int amount) {
            set(index, resource, amount);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            onChanged.run();
        }
    }

    private static final class ObservableFluidHandler extends FluidStacksResourceHandler
                                                      implements DirectSlotResourceAccess<FluidResource> {

        private final Runnable onChanged;

        private ObservableFluidHandler(Runnable onChanged) {
            super(FLUID_TANKS, FLUID_CAPACITY);
            this.onChanged = onChanged;
        }

        @Override
        public void directSet(int index, FluidResource resource, int amount) {
            set(index, resource, amount);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            onChanged.run();
        }
    }
}
