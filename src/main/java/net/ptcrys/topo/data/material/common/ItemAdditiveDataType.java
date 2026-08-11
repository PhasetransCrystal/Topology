package net.ptcrys.topo.data.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * An extra item a material contributes to every step of an additive-aware machine conversion,
 * e.g. a sintering flux for alloys. Materials without the data simply run the plain steps.
 */
public final class ItemAdditiveDataType extends MaterialDataType<ItemAdditiveData> {

    public ItemAdditiveDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<ItemAdditiveData> item(ItemLike item, int count) {
        return use(ItemAdditiveData.create(item.asItem(), count));
    }
}
