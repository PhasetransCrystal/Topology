package net.ptcrys.topodemo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.*;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.api.machine.resource.*;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Persisted, observable storage; player UI and automation use the same committed buffer. */
public final class ItemPort extends ResourcePort<ItemStack, ItemResource> {

    public static final ComponentKey<ItemPort> INPUT = key("item_input");
    public static final ComponentKey<ItemPort> OUTPUT = key("item_output");
    public static final ComponentKey<ItemPort> STORAGE = key("item_storage");

    private static ComponentKey<ItemPort> key(String path) {
        return ComponentKey.of(TopoDemoApiPlugin.INSTANCE.machine().id(path), ItemPort.class);
    }

    public record Metadata(int slots, PortAccess policy) implements ResourcePortMetadata {

        public static final AttachmentType<Metadata> TYPE = TopoDemoPlugin.INSTANCE.machine()
                .attachmentType("item_port", Metadata.class);

        public static void init() {}

        public Metadata {
            if (slots < 1 || slots > 9) throw new IllegalArgumentException("Item ports support 1..9 slots");
            Objects.requireNonNull(policy);
        }

        @Override
        public AttachmentType<Metadata> type() {
            return TYPE;
        }

        @Override
        public MachineResourceType<ItemResource> resourceType() {
            return BuiltinTopoResourceIntegrations.ITEM.resourceType();
        }
    }

    private ItemPort(ComponentContext<ItemPort> context, Metadata metadata) {
        this(context, metadata, new Buffer(metadata.slots()));
    }

    private ItemPort(ComponentContext<ItemPort> context, Metadata metadata, Buffer buffer) {
        super(context, BuiltinTopoResourceIntegrations.ITEM.resourceType(), buffer, metadata);
        buffer.changed = this::onStorageChanged;
    }

    public static PartRoleMount<ItemPort> mount(ComponentKey<ItemPort> key, int slots, PortAccess access) {
        Metadata metadata = new Metadata(slots, access);
        return new PartRoleMount<>(key.mount(context -> new ItemPort(context, metadata), metadata));
    }

    @Override
    public void onMachineDestroyed(ServerLevel level, BlockPos pos, BlockState state) {
        var drops = new ArrayList<ItemStack>();
        try (Transaction transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < handler().size(); slot++) {
                ItemResource resource = handler().getResource(slot);
                int count = (int) handler().getAmountAsLong(slot);
                if (resource.isEmpty() || count == 0) continue;
                int extracted = handler().extract(slot, resource, count, transaction);
                if (extracted > 0) drops.add(resource.toStack(extracted));
            }
            transaction.commit();
        }
        drops.forEach(stack -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack));
    }

    private static final class Buffer extends ItemStacksResourceHandler {

        private BiConsumer<Integer, ItemStack> changed = (slot, previous) -> {};

        private Buffer(int slots) {
            super(slots);
        }

        @Override
        protected void onContentsChanged(int slot, ItemStack previous) {
            changed.accept(slot, previous);
        }
    }
}
