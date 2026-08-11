package net.ptcrys.topo.api.machine;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What a held item does when used on a machine block. The machine domain owns this socket
 * ({@link MachineBlock#useItemOn} is the single dispatcher); consumer domains (equipment) register
 * their implementations per item via {@link MachineItemBehaviors} once items are bound.
 *
 * <p>
 * Dispatch runs on both sides: implementations decide "would I handle this" identically on the
 * client (traits are synced) and return {@code SUCCESS} there without mutating; the server performs
 * the change. Returning {@code PASS} falls through to the machine's default interaction (its UI).
 */
@FunctionalInterface
public interface MachineItemBehavior {

    InteractionResult useOnMachine(MachineBlockEntity machine, Player player, ItemStack stack,
                                   InteractionHand hand, BlockHitResult hit);
}
