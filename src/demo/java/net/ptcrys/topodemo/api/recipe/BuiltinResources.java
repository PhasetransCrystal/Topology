package net.ptcrys.topodemo.api.recipe;

import net.ptcrys.topo.api.machine.data.DataScope;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.ui.MachineUiIcons;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.recipe.common.ItemRecipeCapability;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;

/** Resource type, recipe capability, name and UI icon are declared together, before capability freeze. */
public final class BuiltinResources {

    public static final MachineResourceType<ItemResource> ITEMS;
    public static final MachineResourceType<FluidResource> FLUIDS;
    public static final ItemRecipeCapability ITEM;
    static {
        var plugin = TopoDemoApiPlugin.INSTANCE;
        ITEMS = plugin.machine().resourceTypeWithDataField("item", ItemResource.class, Capabilities.Item.BLOCK, DataScope::itemResourceHandler);
        plugin.machine().bindResourceName(ITEMS, "Items", "物品");
        MachineUiIcons.register(ITEMS, () -> new ColorRectTexture(0xFFC98C50));
        ITEM = plugin.recipe().capability(new ItemRecipeCapability(ITEMS));
        FLUIDS = plugin.machine().resourceTypeWithDataField("fluid", FluidResource.class, Capabilities.Fluid.BLOCK, DataScope::fluidResourceHandler);
        plugin.machine().bindResourceName(FLUIDS, "Fluids", "流体");
        MachineUiIcons.register(FLUIDS, () -> new ColorRectTexture(0xFF4C9FC7));
    }

    private BuiltinResources() {}

    public static void init() {}
}
