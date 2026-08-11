package net.ptcrys.topo.datav2.equipment.common.behavior;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineItemBehavior;
import net.ptcrys.topo.apiv2.machine.component.MachineWorkControl;
import net.ptcrys.topo.apiv2.machine.component.MachineWorkControl.WorkMode;
import net.ptcrys.topo.apiv2.machine.component.ServiceMatch;
import net.ptcrys.topo.datav2.equipment.BuiltinOIEquipmentLang;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The regulator's machine interaction: right-click toggles every work-control capable trait on
 * the machine between RUNNING and HALTED. Halting freezes recipe progress and per-tick input
 * draw and prevents new recipes from starting; resuming continues where the work stopped.
 *
 * <p>
 * Both sides evaluate "does this machine expose work control" identically (traits are present
 * client-side); only the server mutates, charges durability and reports. A machine with several
 * recipe traits toggles each one — feedback reports the last resulting mode.
 */
public final class RegulatorBehavior implements MachineItemBehavior {

    public static final RegulatorBehavior INSTANCE = new RegulatorBehavior();

    private RegulatorBehavior() {}

    @Override
    public InteractionResult useOnMachine(MachineBlockEntity machine, Player player, ItemStack stack,
                                          InteractionHand hand, BlockHitResult hit) {
        List<ServiceMatch<MachineWorkControl>> controls = machine.machineComponents().services(MachineWorkControl.KEY, null);
        if (controls.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        WorkMode result = WorkMode.RUNNING;
        for (ServiceMatch<MachineWorkControl> control : controls) {
            result = control.value().toggleWorkMode();
        }
        stack.hurtAndBreak(1, player, hand);
        machine.getLevel().playSound(null, machine.getBlockPos(),
                SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f,
                result == WorkMode.HALTED ? 0.6f : 0.9f);
        player.sendOverlayMessage((result == WorkMode.HALTED ? BuiltinOIEquipmentLang.EQUIPMENT_REGULATOR_HALTED : BuiltinOIEquipmentLang.EQUIPMENT_REGULATOR_RESUMED).getComponent());
        return InteractionResult.CONSUME;
    }
}
