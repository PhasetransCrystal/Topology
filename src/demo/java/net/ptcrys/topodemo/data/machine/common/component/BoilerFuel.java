package net.ptcrys.topodemo.data.machine.common.component;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;
import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.machine.DemoComponentKeys;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.machine.MachineTier;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** 将锅炉燃料独立转换为可持久化的燃烧时间储备。 */
public final class BoilerFuel extends MachineTicker {

    public static final ComponentKey<BoilerFuel> KEY = DemoComponentKeys.id("boiler_fuel", BoilerFuel.class);
    private static final int HYDROGEN_AMOUNT = 100;
    private static final int HYDROGEN_BURN_TICKS = 20;
    private final int minimumReserve;
    private ItemResourcePort items;
    private FluidResourcePort fluids;
    private ScalarResourcePort reserve;

    private BoilerFuel(ComponentContext<BoilerFuel> context, MachineTier tier) {
        super(context);
        this.minimumReserve = 1 << (tier.level() - 1);
    }

    public static ComponentMount<BoilerFuel> mount(MachineTier tier) {
        return KEY.mount(context -> new BoilerFuel(context, tier));
    }

    @Override
    public void resolveDependencies(MachineComponents components) {
        items = components.require(ItemResourcePort.ITEM_INPUT_1);
        fluids = components.require(FluidResourcePort.FLUID_INPUT_1);
        reserve = components.require(ScalarResourcePort.BURN_TIME_INPUT);
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        if (reserve.storedAmount() >= minimumReserve) return;
        Level level = machine().getLevel();
        if (level == null || level.isClientSide()) return;
        if (!loadItemFuel(level)) loadHydrogen();
    }

    private boolean loadItemFuel(Level level) {
        ResourceHandler<ItemResource> handler = items.handler();
        for (int index = 0; index < handler.size(); index++) {
            ItemResource resource = handler.getResource(index);
            if (resource.isEmpty() || handler.getAmountAsLong(index) <= 0) continue;
            ItemStack fuel = resource.toStack(1);
            int burnTicks = fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
            burnTicks -= Math.floorMod(burnTicks, 10);
            if (burnTicks <= 0) continue;
            ItemStackTemplate remainder = fuel.getCraftingRemainder();
            try (Transaction transaction = Transaction.openRoot()) {
                if (ResourceHandlerLongOps.extract(handler, resource, 1, transaction) != 1) continue;
                if (remainder != null && ResourceHandlerLongOps.insert(handler, ItemResource.of(remainder.create()), remainder.count(), transaction) != remainder.count()) continue;
                if (ResourceHandlerLongOps.insert(reserve.handler(), reserve.resource(), burnTicks, transaction) != burnTicks) continue;
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    private void loadHydrogen() {
        FluidResource hydrogen = FluidResource.of(new FluidStack(
                MaterialHelper.requireFluid(BuiltinMaterials.HYDROGEN, BuiltinMaterialForms.GAS), HYDROGEN_AMOUNT));
        try (Transaction transaction = Transaction.openRoot()) {
            if (ResourceHandlerLongOps.extract(fluids.handler(), hydrogen, HYDROGEN_AMOUNT, transaction) != HYDROGEN_AMOUNT) return;
            if (ResourceHandlerLongOps.insert(reserve.handler(), BuiltinTopoResourceIntegrations.BURN_TIME.recipeCapability().resource(), HYDROGEN_BURN_TICKS, transaction) != HYDROGEN_BURN_TICKS) return;
            transaction.commit();
        }
    }
}
