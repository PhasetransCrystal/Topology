package net.ptcrys.topo.api.api.registration;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/** Convenience factories for external item, block, and fluid targets. */
public final class ExternalTargets {

    private ExternalTargets() {}

    public static ExternalItemTarget mcItem(String path) {
        return new ExternalItemTarget(Identifier.withDefaultNamespace(path));
    }

    public static ExternalBlockTarget mcBlock(String path) {
        return new ExternalBlockTarget(Identifier.withDefaultNamespace(path));
    }

    public static ExternalItemTarget item(Identifier id) {
        return new ExternalItemTarget(id);
    }

    public static ExternalItemTarget item(ItemLike item) {
        return ExternalItemTarget.of(item);
    }

    public static ExternalItemTarget item(Identifier id, ItemLike item) {
        return ExternalItemTarget.of(id, item);
    }

    public static ExternalItemTarget itemSupplier(Supplier<? extends ItemLike> item) {
        return ExternalItemTarget.ofSupplier(item);
    }

    public static ExternalItemTarget itemSupplier(Identifier id, Supplier<? extends ItemLike> item) {
        return ExternalItemTarget.ofSupplier(id, item);
    }

    public static ExternalBlockTarget block(Identifier id) {
        return new ExternalBlockTarget(id);
    }

    public static ExternalBlockTarget block(Block block) {
        return ExternalBlockTarget.of(block);
    }

    public static ExternalBlockTarget block(Identifier id, Supplier<? extends Block> block) {
        return ExternalBlockTarget.of(id, block);
    }

    public static ExternalBlockTarget blockSupplier(Supplier<? extends Block> block) {
        return ExternalBlockTarget.ofSupplier(block);
    }

    public static ExternalBlockTarget blockSupplier(Identifier id, Supplier<? extends Block> block) {
        return ExternalBlockTarget.of(id, block);
    }

    public static ExternalBlockTarget block(Identifier id, Block block) {
        return ExternalBlockTarget.of(id, block);
    }

    public static ExternalBlockTarget block(
                                            Identifier id, Supplier<? extends Block> block, Supplier<? extends ItemLike> item) {
        return ExternalBlockTarget.of(id, block, item);
    }

    public static ExternalFluidTarget fluid(Identifier id) {
        return new ExternalFluidTarget(id);
    }

    public static ExternalFluidTarget fluid(Fluid fluid) {
        return ExternalFluidTarget.of(fluid);
    }

    public static ExternalFluidTarget fluid(Identifier id, Fluid fluid) {
        return ExternalFluidTarget.of(id, fluid);
    }

    public static ExternalFluidTarget fluidSupplier(Supplier<? extends Fluid> fluid) {
        return ExternalFluidTarget.ofSupplier(fluid);
    }

    public static ExternalFluidTarget fluidSupplier(Identifier id, Supplier<? extends Fluid> fluid) {
        return ExternalFluidTarget.ofSupplier(id, fluid);
    }

    public static ExternalFluidTarget fluid(
                                            Identifier id, Supplier<? extends Fluid> fluid, Supplier<? extends ItemLike> bucket) {
        return ExternalFluidTarget.of(id, fluid, bucket);
    }
}
