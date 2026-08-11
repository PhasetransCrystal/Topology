package net.ptcrys.topo.data.machine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Runtime wiring of registered machines: recipe-modifier tooltip panels into
 * {@code ItemTooltipUis}. Items are deferred entries, so registration runs in FMLCommonSetup's
 * enqueueWork (items bound).
 *
 * <p>
 * Ordering: this must register on the mod bus BEFORE {@code PipeSpecTooltips} — that listener's
 * task calls {@code ItemTooltipUis.freeze()}, and enqueueWork tasks run in submission order.
 */
public final class MachineRuntimeBindings {

    private MachineRuntimeBindings() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
                MachineRecipeModifierTooltips::registerAll));
    }
}
