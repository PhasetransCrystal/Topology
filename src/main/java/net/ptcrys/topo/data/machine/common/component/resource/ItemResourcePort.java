package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.machine.resource.ResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public final class ItemResourcePort extends ResourcePort<ItemStack, ItemResource> {

    public static final ComponentKey<ItemResourcePort> ITEM_INPUT_1 = ComponentKey.id("item_input_1", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_INPUT_2 = ComponentKey.id("item_input_2", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_INPUT_3 = ComponentKey.id("item_input_3", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_INPUT_4 = ComponentKey.id("item_input_4", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_OUTPUT_1 = ComponentKey.id("item_output_1", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_OUTPUT_2 = ComponentKey.id("item_output_2", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_OUTPUT_3 = ComponentKey.id("item_output_3", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_OUTPUT_4 = ComponentKey.id("item_output_4", ItemResourcePort.class);
    public static final ComponentKey<ItemResourcePort> ITEM_STORAGE = ComponentKey.id("item_storage", ItemResourcePort.class);
    /**
     * Domain identity for a process-die / catalyst slot (persistence + UI). Content rules and
     * access are declared at the mount site via {@link PortAccess} + {@link ResourceFilterHelper}, not
     * via a die-specific factory.
     */
    public static final ComponentKey<ItemResourcePort> ITEM_DIE_1 = ComponentKey.id("item_die_1", ItemResourcePort.class, "Die Slot", "模具槽");

    private ItemResourcePort(
                             ComponentContext<ItemResourcePort> context,
                             ItemResourcePortMetadata metadata) {
        this(context, new ObservableItemResourceHandler(metadata.slots(), metadata::accepts), metadata);
    }

    private ItemResourcePort(
                             ComponentContext<ItemResourcePort> context,
                             ObservableItemResourceHandler handler,
                             ItemResourcePortMetadata metadata) {
        super(
                context,
                BuiltinTopoResourceIntegrations.ITEM.resourceType(),
                handler,
                metadata);
        handler.setOnChanged(this::onStorageChanged);
    }

    /**
     * Port with a caller-supplied {@link PortAccess} — side-restricted or otherwise
     * customized ports compose the policy here (e.g. {@code PortAccess.input(UP)}); chain
     * {@link PartRoleMount#role} to additionally expose it as a multiblock part port.
     */
    public static PartRoleMount<ItemResourcePort> mount(
                                                        ComponentKey<ItemResourcePort> key,
                                                        int slots,
                                                        PortAccess policy) {
        return mount(key, slots, policy, null);
    }

    public static PartRoleMount<ItemResourcePort> mount(
                                                        ComponentKey<ItemResourcePort> key,
                                                        int slots,
                                                        PortAccess policy,
                                                        @Nullable Predicate<Resource> resourceFilter) {
        ItemResourcePortMetadata metadata = new ItemResourcePortMetadata(slots, policy, resourceFilter);
        return new PartRoleMount<>(
                key.mount(context -> new ItemResourcePort(context, metadata), metadata));
    }

    public static PartRoleMount<ItemResourcePort> input(ComponentKey<ItemResourcePort> key, int slots) {
        return mount(key, slots, PortAccess.input());
    }

    public static PartRoleMount<ItemResourcePort> output(ComponentKey<ItemResourcePort> key, int slots) {
        return mount(key, slots, PortAccess.output());
    }

    public static PartRoleMount<ItemResourcePort> storage(ComponentKey<ItemResourcePort> key, int slots) {
        return mount(key, slots, PortAccess.storage());
    }

    /** 被破坏时同原版容器:槽内物品全部以掉落物形式落地(经事务提取,空槽跳过)。 */
    @Override
    public void onMachineDestroyed(ServerLevel level, BlockPos pos, BlockState state) {
        StacksResourceHandler<ItemStack, ItemResource> handler = handler();
        try (Transaction transaction = Transaction.openRoot()) {
            for (int index = 0; index < handler.size(); index++) {
                ItemResource resource = handler.getResource(index);
                long amount = handler.getAmountAsLong(index);
                if (resource.isEmpty() || amount <= 0L) {
                    continue;
                }
                int extracted = handler.extract(index, resource, (int) Math.min(amount, Integer.MAX_VALUE), transaction);
                if (extracted > 0) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resource.toStack(extracted));
                }
            }
            transaction.commit();
        }
    }

    /** Sum of every stored stack. Empty slots contribute zero, so no per-slot emptiness check is needed. */
    public int computeItemTotal() {
        long total = 0L;
        for (int index = 0; index < handler().size(); index++) {
            total += handler().getAmountAsLong(index);
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }
}
