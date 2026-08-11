package net.ptcrys.topo.data.pipe;

import net.ptcrys.topo.api.pipe.PipeFilterAdapter;
import net.ptcrys.topo.api.pipe.PipePortFilter;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builtin {@link PipeFilterAdapter}s for the two non-scalar pipe kinds. Compilation resolves
 * plain ids into reference sets and {@code #} entries into {@link TagKey}s once; the per-resource
 * test is one identity-set lookup plus a short holder tag loop, allocation-free. Unresolvable id
 * entries never match but stay listed (the player sees and removes them).
 */
public final class BuiltinOIPipeFilterAdapters {

    public static final PipeFilterAdapter<ItemResource> ITEM = new ItemAdapter();
    public static final PipeFilterAdapter<FluidResource> FLUID = new FluidAdapter();

    private BuiltinOIPipeFilterAdapters() {}

    private static final class ItemAdapter implements PipeFilterAdapter<ItemResource> {

        @Override
        public Predicate<ItemResource> compile(List<String> entries) {
            Set<Item> ids = new ReferenceOpenHashSet<>();
            List<TagKey<Item>> tags = new ArrayList<>();
            for (String entry : entries) {
                if (PipePortFilter.isTagEntry(entry)) {
                    Identifier tagId = Identifier.tryParse(entry.substring(1));
                    if (tagId != null) {
                        tags.add(TagKey.create(Registries.ITEM, tagId));
                    }
                } else {
                    Identifier id = Identifier.tryParse(entry);
                    if (id != null) {
                        BuiltInRegistries.ITEM.getOptional(id).ifPresent(ids::add);
                    }
                }
            }
            TagKey<Item>[] tagArray = toArray(tags);
            return resource -> {
                if (ids.contains(resource.getItem())) {
                    return true;
                }
                for (TagKey<Item> tag : tagArray) {
                    if (resource.typeHolder().is(tag)) {
                        return true;
                    }
                }
                return false;
            };
        }

        @Override
        public ItemStack displayStack(String idEntry) {
            Identifier id = Identifier.tryParse(idEntry);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        }

        @Override
        public List<ItemStack> displayStacks(String entry) {
            if (!PipePortFilter.isTagEntry(entry)) {
                ItemStack stack = displayStack(entry);
                return stack.isEmpty() ? List.of() : List.of(stack);
            }
            Identifier id = Identifier.tryParse(entry.substring(1));
            if (id == null) {
                return List.of();
            }
            return BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id))
                    .map(HolderSet.ListBacked::stream)
                    .map(holders -> holders
                            .map(holder -> new ItemStack(holder.value()))
                            .filter(stack -> !stack.isEmpty())
                            .toList())
                    .orElse(List.of());
        }

        @Override
        public @Nullable String entryFromCarried(ItemStack carried) {
            if (carried.isEmpty()) {
                return null;
            }
            return BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        }
    }

    private static final class FluidAdapter implements PipeFilterAdapter<FluidResource> {

        @Override
        public Predicate<FluidResource> compile(List<String> entries) {
            Set<Fluid> ids = new ReferenceOpenHashSet<>();
            List<TagKey<Fluid>> tags = new ArrayList<>();
            for (String entry : entries) {
                if (PipePortFilter.isTagEntry(entry)) {
                    Identifier tagId = Identifier.tryParse(entry.substring(1));
                    if (tagId != null) {
                        tags.add(TagKey.create(Registries.FLUID, tagId));
                    }
                } else {
                    Identifier id = Identifier.tryParse(entry);
                    if (id != null) {
                        BuiltInRegistries.FLUID.getOptional(id).ifPresent(ids::add);
                    }
                }
            }
            TagKey<Fluid>[] tagArray = toArray(tags);
            return resource -> {
                if (ids.contains(resource.getFluid())) {
                    return true;
                }
                for (TagKey<Fluid> tag : tagArray) {
                    if (resource.typeHolder().is(tag)) {
                        return true;
                    }
                }
                return false;
            };
        }

        @Override
        public ItemStack displayStack(String idEntry) {
            Identifier id = Identifier.tryParse(idEntry);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            return BuiltInRegistries.FLUID.getOptional(id)
                    .map(fluid -> new ItemStack(fluid.getBucket()))
                    .orElse(ItemStack.EMPTY);
        }

        @Override
        public List<ItemStack> displayStacks(String entry) {
            if (!PipePortFilter.isTagEntry(entry)) {
                ItemStack stack = displayStack(entry);
                return stack.isEmpty() ? List.of() : List.of(stack);
            }
            Identifier id = Identifier.tryParse(entry.substring(1));
            if (id == null) {
                return List.of();
            }
            return BuiltInRegistries.FLUID.get(TagKey.create(Registries.FLUID, id))
                    .map(HolderSet.ListBacked::stream)
                    .map(holders -> {
                        Set<Item> seenBuckets = new ReferenceOpenHashSet<>();
                        List<ItemStack> stacks = new ArrayList<>();
                        holders.forEach(holder -> {
                            Item bucket = holder.value().getBucket();
                            if (bucket != Items.AIR && seenBuckets.add(bucket)) {
                                stacks.add(new ItemStack(bucket));
                            }
                        });
                        return List.copyOf(stacks);
                    })
                    .orElse(List.of());
        }

        @Override
        public List<FluidStack> displayFluidStacks(String entry) {
            if (!PipePortFilter.isTagEntry(entry)) {
                Identifier id = Identifier.tryParse(entry);
                if (id == null) {
                    return List.of();
                }
                return BuiltInRegistries.FLUID.getOptional(id)
                        .map(fluid -> new FluidStack(fluid, 1000))
                        .filter(stack -> !stack.isEmpty())
                        .map(stack -> List.of(stack))
                        .orElse(List.of());
            }
            Identifier id = Identifier.tryParse(entry.substring(1));
            if (id == null) {
                return List.of();
            }
            return BuiltInRegistries.FLUID.get(TagKey.create(Registries.FLUID, id))
                    .map(HolderSet.ListBacked::stream)
                    .map(holders -> {
                        Set<Fluid> seenFluids = new ReferenceOpenHashSet<>();
                        List<FluidStack> stacks = new ArrayList<>();
                        holders.forEach(holder -> {
                            FluidStack stack = new FluidStack(holder.value(), 1000);
                            if (!stack.isEmpty() && seenFluids.add(stack.getFluid())) {
                                stacks.add(stack);
                            }
                        });
                        return List.copyOf(stacks);
                    })
                    .orElse(List.of());
        }

        /** Probe the carried container's fluid (pure read, AE config page idiom). */
        @Override
        public @Nullable String entryFromCarried(ItemStack carried) {
            if (carried.isEmpty()) {
                return null;
            }
            ResourceHandler<FluidResource> handler = ItemAccess.forStack(carried).oneByOne().getCapability(Capabilities.Fluid.ITEM);
            if (handler == null) {
                return null;
            }
            for (int index = 0; index < handler.size(); index++) {
                FluidResource resource = handler.getResource(index);
                if (!resource.isEmpty() && handler.getAmountAsLong(index) > 0) {
                    return BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
                }
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> TagKey<T>[] toArray(List<TagKey<T>> tags) {
        return tags.toArray(new TagKey[0]);
    }
}
