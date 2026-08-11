package net.ptcrys.topo.api.machine.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A persist-only field backed by a slotted {@link FluidResource} handler. */
public final class DataFluidResourceHandler extends DataManualDirtyField {

    private static final String SLOTS_TAG = "slots";
    private static final String SLOT_INDEX_TAG = "i";
    private static final String SLOT_RESOURCE_TAG = "r";
    private static final String SLOT_AMOUNT_TAG = "a";
    private static final String LEGACY_STACKS_TAG = "stacks";

    private final ResourceHandler<FluidResource> handler;
    private final SlotLoader loader;
    private final Runnable afterRead;

    DataFluidResourceHandler(
                             DataScope owner,
                             String key,
                             ResourceHandler<FluidResource> handler,
                             SlotLoader loader,
                             Runnable afterRead,
                             FieldPolicy policy) {
        super(owner, key, policy);
        this.handler = Objects.requireNonNull(handler, "handler");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.afterRead = Objects.requireNonNull(afterRead, "afterRead");
    }

    @Override
    void writePersist(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.child(key()).childrenList(SLOTS_TAG);
        for (int index = 0, slots = handler.size(); index < slots; index++) {
            FluidResource resource = handler.getResource(index);
            long amount = handler.getAmountAsLong(index);
            if (resource.isEmpty() || amount <= 0L) {
                continue;
            }
            ValueOutput child = list.addChild();
            child.putInt(SLOT_INDEX_TAG, index);
            child.putLong(SLOT_AMOUNT_TAG, amount);
            child.store(SLOT_RESOURCE_TAG, FluidResource.OPTIONAL_CODEC, resource);
        }
    }

    @Override
    void readPersist(ValueInput input) {
        input.child(key()).ifPresent(child -> readEntries(child).ifPresent(entries -> {
            loader.load(entries);
            afterRead.run();
        }));
    }

    @Override
    void writeSync(RegistryFriendlyByteBuf buffer) {
        throw new UnsupportedOperationException("Fluid resource handler fields do not have a network codec");
    }

    @Override
    Object readSyncValue(RegistryFriendlyByteBuf buffer) {
        throw new UnsupportedOperationException("Fluid resource handler fields do not have a network codec");
    }

    @Override
    void applyDecodedSyncValue(Object value) {
        throw new UnsupportedOperationException("Fluid resource handler fields do not have a network codec");
    }

    @Override
    String schemaTypeId() {
        return "fluid_resource_handler";
    }

    private Optional<List<Entry>> readEntries(ValueInput input) {
        Optional<ValueInput.ValueInputList> indexed = input.childrenList(SLOTS_TAG);
        if (indexed.isPresent()) {
            return Optional.of(readIndexedEntries(indexed.get()));
        }
        Optional<List<FluidStack>> legacyStacks = input.read(LEGACY_STACKS_TAG, FluidStack.OPTIONAL_CODEC.listOf());
        if (legacyStacks.isPresent()) {
            return Optional.of(readLegacyStacks(legacyStacks.get()));
        }
        return Optional.empty();
    }

    private List<Entry> readIndexedEntries(ValueInput.ValueInputList list) {
        List<Entry> result = new ArrayList<>();
        boolean[] seen = new boolean[handler.size()];
        for (ValueInput child : list) {
            int index = child.getIntOr(SLOT_INDEX_TAG, -1);
            if (index < 0 || index >= seen.length || seen[index]) {
                continue;
            }
            long amount = child.getLongOr(SLOT_AMOUNT_TAG, 0L);
            FluidResource resource = child.read(SLOT_RESOURCE_TAG, FluidResource.OPTIONAL_CODEC)
                    .orElse(FluidResource.EMPTY);
            if (amount <= 0L || resource.isEmpty()) {
                continue;
            }
            seen[index] = true;
            result.add(new Entry(index, resource, amount));
        }
        return result;
    }

    private List<Entry> readLegacyStacks(List<FluidStack> stacks) {
        List<Entry> result = new ArrayList<>();
        int limit = Math.min(stacks.size(), handler.size());
        for (int index = 0; index < limit; index++) {
            FluidStack stack = stacks.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            result.add(new Entry(index, FluidResource.of(stack), stack.getAmount()));
        }
        return result;
    }

    static SlotLoader defaultLoader(ResourceHandler<FluidResource> handler) {
        Objects.requireNonNull(handler, "handler");
        return entries -> {
            try (Transaction transaction = Transaction.openRoot()) {
                clear(handler, transaction);
                for (Entry entry : entries) {
                    insertExact(handler, entry, transaction);
                }
                transaction.commit();
            }
        };
    }

    private static void clear(ResourceHandler<FluidResource> handler, TransactionContext transaction) {
        for (int index = 0, slots = handler.size(); index < slots; index++) {
            FluidResource resource = handler.getResource(index);
            long remaining = handler.getAmountAsLong(index);
            while (!resource.isEmpty() && remaining > 0L) {
                int request = Ints.saturatedCast(remaining);
                int extracted = handler.extract(index, resource, request, transaction);
                if (extracted <= 0) {
                    throw new IllegalStateException("Could not clear fluid resource handler slot " + index);
                }
                remaining -= extracted;
            }
        }
    }

    private static void insertExact(
                                    ResourceHandler<FluidResource> handler,
                                    Entry entry,
                                    TransactionContext transaction) {
        long remaining = entry.amount();
        while (remaining > 0L) {
            int request = Ints.saturatedCast(remaining);
            int inserted = handler.insert(entry.index(), entry.resource(), request, transaction);
            if (inserted <= 0) {
                throw new IllegalStateException("Could not restore " + remaining + " fluid into handler slot " + entry.index());
            }
            remaining -= inserted;
        }
    }

    public record Entry(int index, FluidResource resource, long amount) {

        public Entry {
            if (index < 0) {
                throw new IllegalArgumentException("Fluid resource handler slot index must be non-negative");
            }
            Objects.requireNonNull(resource, "resource");
            if (amount <= 0L) {
                throw new IllegalArgumentException("Fluid resource handler amount must be positive");
            }
            if (resource.isEmpty()) {
                throw new IllegalArgumentException("Fluid resource handler resource must not be empty");
            }
        }
    }

    @FunctionalInterface
    public interface SlotLoader {

        void load(List<Entry> entries);
    }
}
