package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.pipe.PipeFilterAdapter;
import net.ptcrys.topo.api.pipe.PipePortFilter;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ItemFilter implements PipeFilterAdapter<ItemResource> {

    @Override
    public Predicate<ItemResource> compile(List<String> entries) {
        Set<Item> items = new HashSet<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        for (String entry : entries) {
            boolean tag = PipePortFilter.isTagEntry(entry);
            Identifier id = Identifier.tryParse(tag ? entry.substring(1) : entry);
            if (id == null) continue;
            if (tag) tags.add(TagKey.create(Registries.ITEM, id));
            else BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
        }
        return resource -> items.contains(resource.getItem()) || tags.stream().anyMatch(resource.typeHolder()::is);
    }

    @Override
    public ItemStack displayStack(String entry) {
        Identifier id = Identifier.tryParse(entry);
        return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public List<ItemStack> displayStacks(String entry) {
        if (!PipePortFilter.isTagEntry(entry)) return PipeFilterAdapter.super.displayStacks(entry);
        Identifier id = Identifier.tryParse(entry.substring(1));
        if (id == null) return List.of();
        return BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id))
                .map(HolderSet.ListBacked::stream)
                .map(holders -> holders.map(holder -> new ItemStack(holder.value())).toList()).orElse(List.of());
    }

    @Override
    public @Nullable String entryFromCarried(ItemStack carried) {
        return carried.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
    }
}
