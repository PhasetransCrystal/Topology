package net.ptcrys.topo.client.apiv2.machine.ui.tooltip;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.apiv2.machine.ui.tooltip.ItemTooltipUis;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import com.mojang.datafixers.util.Either;

import java.util.List;

/**
 * Entrance B of the tooltip-UI system: injects registered tooltip UIs into the vanilla tooltip
 * pipeline for items that cannot override {@code getTooltipImage} themselves (foreign mod /
 * vanilla items). Resolves through the same {@link ItemTooltipUis} cache as entrance A
 * ({@code OIMachineBlockItem}) and skips stacks whose component is already gathered — identity
 * comparison is exact because both entrances hand out the same cached instance.
 */
@EventBusSubscriber(modid = Topology.MODID, value = Dist.CLIENT)
public final class OiTooltipComponentInjector {

    private OiTooltipComponentInjector() {}

    @SubscribeEvent
    static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        TooltipComponent ours = ItemTooltipUis.componentFor(stack);
        if (ours == null) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        for (Either<FormattedText, TooltipComponent> element : elements) {
            if (element.right().filter(existing -> existing == ours).isPresent()) {
                return;
            }
        }
        // 与原版 getTooltipImage 的 gather 同位(名后 index 1):面板紧贴物品名,压在
        // 模组名/创造页签等尾部行之上,而不是吊在整个 tooltip 末尾。
        elements.add(elements.isEmpty() ? 0 : 1, Either.right(ours));
    }
}
