package net.ptcrys.topo.apiv2.ore.engine;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Resolves a (material, ore form) pair to its registered block. Worldgen resolves the base domain;
 * the base domain never references worldgen. Default implementation goes through
 * {@link MaterialHelper#item} so vanilla form overrides resolve correctly.
 */
@FunctionalInterface
public interface OreBlockResolver {

    Optional<Block> block(Material material, MaterialForm form);

    static OreBlockResolver materialHelper() {
        return (material, form) -> MaterialHelper.item(material, form)
                .filter(BlockItem.class::isInstance)
                .map(item -> ((BlockItem) item).getBlock());
    }
}
