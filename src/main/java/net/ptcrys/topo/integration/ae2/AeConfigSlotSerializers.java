package net.ptcrys.topo.integration.ae2;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.ArrayList;
import java.util.List;

final class AeConfigSlotSerializers {

    private static final String SLOTS_TAG = "slots";
    private static final String RESOURCE_TAG = "resource";
    private static final String TARGET_TAG = "target";

    private AeConfigSlotSerializers() {}

    static <R extends Resource> List<AeConfigSlot<R>> emptySlots(int slots) {
        ArrayList<AeConfigSlot<R>> result = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            result.add(AeConfigSlot.empty());
        }
        return result;
    }

    static <R extends Resource> void write(ValueOutput output, List<AeConfigSlot<R>> slots, Class<R> resourceType) {
        ValueOutput.ValueOutputList list = output.childrenList(SLOTS_TAG);
        for (AeConfigSlot<R> slot : slots) {
            ValueOutput child = list.addChild();
            if (!slot.configured()) continue;
            child.putLong(TARGET_TAG, slot.targetAmount());
            writeResource(child, RESOURCE_TAG, slot.resource(), resourceType);
        }
    }

    @SuppressWarnings("unchecked")
    static <R extends Resource> void read(ValueInput input, List<AeConfigSlot<R>> slots, Class<R> resourceType) {
        input.childrenList(SLOTS_TAG).ifPresent(list -> {
            for (int i = 0; i < slots.size(); i++) {
                slots.set(i, AeConfigSlot.empty());
            }
            int index = 0;
            for (ValueInput child : list) {
                if (index >= slots.size()) break;
                long target = child.getLongOr(TARGET_TAG, 0L);
                R resource = target > 0L ? readResource(child, RESOURCE_TAG, resourceType) : null;
                slots.set(index, resource != null && !resource.isEmpty() && target > 0L ? AeConfigSlot.of(resource, target) : AeConfigSlot.empty());
                index++;
            }
        });
    }

    @SuppressWarnings("unchecked")
    static <R extends Resource> R readResource(ValueInput input, String tag, Class<R> resourceType) {
        if (resourceType == ItemResource.class) {
            return (R) input.read(tag, ItemResource.OPTIONAL_CODEC).orElse(ItemResource.EMPTY);
        }
        if (resourceType == FluidResource.class) {
            return (R) input.read(tag, FluidResource.OPTIONAL_CODEC).orElse(FluidResource.EMPTY);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <R extends Resource> void writeResource(ValueOutput output, String tag, R resource, Class<R> resourceType) {
        if (resource == null || resource.isEmpty()) return;
        if (resourceType == ItemResource.class) {
            output.store(tag, ItemResource.OPTIONAL_CODEC, (ItemResource) resource);
        } else if (resourceType == FluidResource.class) {
            output.store(tag, FluidResource.OPTIONAL_CODEC, (FluidResource) resource);
        }
    }
}
