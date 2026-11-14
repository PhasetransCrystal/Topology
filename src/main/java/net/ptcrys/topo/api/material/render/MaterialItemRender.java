package net.ptcrys.topo.api.material.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.form.MaterialForm;

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

    /**
     * Applies model and tint generation for {@code material}'s item to {@code builder}. The form is
     * provided so form-scoped data can override material-level values for this form only.
     */
    void apply(ItemBuilder<Item, RegistryCore> builder, Material material, MaterialForm form);
}
