package net.ptcrys.topodemo.api.equipment.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.machine.component.MachineWorkControl;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Objects;

public record RegulatorBehavior(LangKey feedback) implements MachineItemBehavior {

    public RegulatorBehavior {
        Objects.requireNonNull(feedback, "feedback");
    }

    @Override
    public InteractionResult useOnMachine(MachineBlockEntity machine, Player player, ItemStack stack,
                                          InteractionHand hand, BlockHitResult hit) {
        var controls = machine.machineComponents().services(MachineWorkControl.KEY, null);
        if (controls.isEmpty()) return InteractionResult.PASS;
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        for (var control : controls) control.value().toggleWorkMode();
        player.sendOverlayMessage(feedback.getComponent());
        return InteractionResult.CONSUME;
    }
}
