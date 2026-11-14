package net.ptcrys.topo.helper;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.datagen.ProviderType;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/**
 * Builds conventional ({@code c:}) item and block {@link TagKey}s from an explicit group name.
 *
 * <p>
 * This helper performs no derivation: the caller declares the conventional group (e.g.
 * {@code "ingots"}, {@code "ores"}, {@code "storage_blocks"}) directly. There is no suffix parsing
 * and no per-material list. The two shapes produced are:
 * <ul>
 * <li>{@code c:<group>} — the group tag (e.g. {@code c:ingots});</li>
 * <li>{@code c:<group>/<material>} — the per-material tag (e.g. {@code c:ingots/iron}).</li>
 * </ul>
 */
public final class TagHelper {

    private static final String CONVENTIONAL_NAMESPACE = "c";

    private TagHelper() {}

    /** {@code c:<group>} item group tag, e.g. {@code c:ingots}. */
    public static TagKey<Item> item(String group) {
        return TagKey.create(Registries.ITEM, conventional(group));
    }

    /** {@code c:<group>/<material>} per-material item tag, e.g. {@code c:ingots/iron}. */
    public static TagKey<Item> itemMaterial(String group, String material) {
        return TagKey.create(Registries.ITEM, conventional(group + "/" + material));
    }

    /** {@code c:<group>} block group tag, e.g. {@code c:ores}. */
    public static TagKey<Block> block(String group) {
        return TagKey.create(Registries.BLOCK, conventional(group));
    }

    /** {@code c:<group>/<material>} per-material block tag, e.g. {@code c:ores/iron}. */
    public static TagKey<Block> blockMaterial(String group, String material) {
        return TagKey.create(Registries.BLOCK, conventional(group + "/" + material));
    }

    /**
     * Adds an item tag entry whose id is resolved only when the tag provider runs. This is the
     * safe path for external targets whose item is not bound when the form content is declared.
     */
    public static void addItemTag(RegistryCore core, TagKey<Item> tag, Supplier<Identifier> id) {
        if (!core.doDatagen()) {
            return;
        }
        core.addDataGenerator(
                ProviderType.ITEM_TAGS,
                provider -> provider.rawBuilder(tag).add(TagEntry.element(id.get())));
    }

    /** Block counterpart of {@link #addItemTag}. */
    public static void addBlockTag(RegistryCore core, TagKey<Block> tag, Supplier<Identifier> id) {
        if (!core.doDatagen()) {
            return;
        }
        core.addDataGenerator(
                ProviderType.BLOCK_TAGS,
                provider -> provider.rawBuilder(tag).add(TagEntry.element(id.get())));
    }

    /** Fluid counterpart of {@link #addItemTag}. */
    public static void addFluidTag(RegistryCore core, TagKey<Fluid> tag, Supplier<Identifier> id) {
        if (!core.doDatagen()) {
            return;
        }
        core.addDataGenerator(
                ProviderType.FLUID_TAGS,
                provider -> provider.rawBuilder(tag).add(TagEntry.element(id.get())));
    }

    /** {@code c:<group>} fluid group tag, e.g. {@code c:solutions}. */
    public static TagKey<Fluid> fluid(String group) {
        return TagKey.create(Registries.FLUID, conventional(group));
    }

    /** {@code c:<group>/<material>} per-material fluid tag, e.g. {@code c:solutions/iron}. */
    public static TagKey<Fluid> fluidMaterial(String group, String material) {
        return TagKey.create(Registries.FLUID, conventional(group + "/" + material));
    }

    private static Identifier conventional(String path) {
        return Identifier.fromNamespaceAndPath(CONVENTIONAL_NAMESPACE, path);
    }
}
