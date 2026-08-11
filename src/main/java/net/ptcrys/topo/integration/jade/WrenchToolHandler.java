package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.apiv2.machine.MachineBlock;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import snownee.jade.addon.harvest.ToolHandler;

import java.util.List;

/**
 * Jade harvest-tool handler that draws a wrench icon for OI's wrench-mineable blocks (anything
 * carrying {@link MachineBlock#MINEABLE_WITH_WRENCH}). Machines are wrench-only, so this is the
 * sole icon Jade shows for them; pipes also stay {@code mineable/pickaxe}, so Jade shows the
 * built-in pickaxe alongside this wrench. Registered through {@code HarvestToolProvider}, which
 * aggregates every handler whose {@link #test} returns a non-empty stack.
 *
 * <p>
 * The icon is a representative wrench (the iron wrench), resolved lazily after item
 * registration and cached; if no wrench is registered the handler contributes no icon.
 */
public final class WrenchToolHandler implements ToolHandler {

    public static final WrenchToolHandler INSTANCE = new WrenchToolHandler();

    private static final Identifier UID = IdHelper.oi("wrench");
    private static final Identifier ICON_ITEM = IdHelper.oi("iron_wrench");

    private ItemStack icon = ItemStack.EMPTY;

    private WrenchToolHandler() {}

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public ItemStack test(BlockState state, Level level, BlockPos pos) {
        return state.is(MachineBlock.MINEABLE_WITH_WRENCH) ? icon() : ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> getTools() {
        ItemStack stack = icon();
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    /** Lazily resolves the representative wrench stack once items are bound; empty if none exists. */
    private ItemStack icon() {
        if (icon.isEmpty()) {
            Item wrench = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            if (wrench != Items.AIR) {
                icon = new ItemStack(wrench);
            }
        }
        return icon;
    }
}
