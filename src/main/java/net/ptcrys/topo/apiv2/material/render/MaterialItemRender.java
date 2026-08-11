package net.ptcrys.topo.apiv2.material.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.data.MaterialDataType;

import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Item-model contribution of a material form. A render is a plain value composed at the form
 * declaration site — it is not id-addressed, so it has no registry; implementations validate their
 * own configuration at construction time.
 */
public interface MaterialItemRender {

    /** Material data types every material declaring the owning form must provide. */
    List<MaterialDataType<?>> requiredMaterialData();

    /** Applies model and tint generation for {@code material}'s item to {@code builder}. */
    void apply(ItemBuilder<Item, RegistryCore> builder, Material material);
}
