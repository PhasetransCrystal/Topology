package net.ptcrys.topo.data.recipe.special;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.content.TopoFluidIngredient;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Boiler recipes are datapack recipes plus a virtual furnace-fuel overlay.
 *
 * <p>
 * The direct-fuel heat path uses the same burn-tick baseline as the combustion generator:
 * 10 burn ticks plus 1 mB water become 20 heat/t for 10 ticks. Fuels whose burn duration is not a
 * full 10-tick batch waste the remainder rather than declaring fractional fluid amounts.
 */
public final class BoilerRecipeType extends TopoRecipeType<TopoRecipe> {

    public static final int BURN_TICKS_PER_WATER_MB = 10;
    public static final int HEAT_PER_TICK = CombustionGeneratorRecipeType.ENERGY_PER_BURN_TICK;
    private static final String VIRTUAL_FUEL_PREFIX = "boiler/fuel/";
    private static final List<ProductionLine> HEAT_LINE = List.of(BuiltinTopoProductionLines.HEAT);

    public BoilerRecipeType(Identifier id) {
        super(id, TopoRecipe::new);
    }

    @Override
    public @Nullable RecipeHolder<TopoRecipe> findRecipe(MachineBlockEntity machine) {
        RecipeHolder<TopoRecipe> datapackRecipe = super.findRecipe(machine);
        return datapackRecipe != null ? datapackRecipe : findVirtualRecipe(machine);
    }

    @Override
    public boolean matchesRememberedRecipe(
                                           MachineBlockEntity machine, RecipeHolder<? extends TopoRecipe> remembered) {
        if (itemFromVirtualFuelId(remembered.id().identifier()) == null) {
            return super.matchesRememberedRecipe(machine, remembered);
        }
        if (!super.matchesRememberedRecipe(machine, remembered)) {
            return false;
        }
        RecipeHolder<TopoRecipe> current = findVirtualRecipe(machine);
        return current != null && current.id().equals(remembered.id()) && current.value().duration() == remembered.value().duration();
    }

    private @Nullable RecipeHolder<TopoRecipe> findVirtualRecipe(MachineBlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide()) {
            return null;
        }
        ResourceHandler<ItemResource> handler = itemInputHandler(machine);
        if (handler == null) {
            return null;
        }
        FuelValues fuels = level.fuelValues();
        for (int index = 0; index < handler.size(); index++) {
            ItemResource resource = handler.getResource(index);
            if (resource.isEmpty() || handler.getAmountAsLong(index) <= 0L) {
                continue;
            }
            ItemStack fuel = resource.toStack(1);
            int burnTicks = fuel.getBurnTime(RecipeType.SMELTING, fuels);
            RecipeHolder<TopoRecipe> holder = burnTicks > 0 ? fuelHolder(fuel, burnTicks) : null;
            if (holder != null && holder.value().matchInputs(machine)) {
                return holder;
            }
        }
        return null;
    }

    @Override
    public Collection<TopoRecipe> displayRecipes(@Nullable MinecraftServer server, @Nullable Level level) {
        List<TopoRecipe> recipes = new ArrayList<>(super.displayRecipes(server, level));
        FuelValues fuels = fuelValues(server, level);
        if (fuels == null) {
            return List.copyOf(recipes);
        }
        for (Item item : fuels.fuelItems()) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack fuel = new ItemStack(item);
            int burnTicks = fuel.getBurnTime(RecipeType.SMELTING, fuels);
            TopoRecipe recipe = burnTicks > 0 ? fuelRecipe(fuel, burnTicks) : null;
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return List.copyOf(recipes);
    }

    @Override
    public @Nullable RecipeHolder<TopoRecipe> resolveRecipe(MinecraftServer server, ResourceKey<Recipe<?>> recipeId) {
        RecipeHolder<TopoRecipe> datapackRecipe = super.resolveRecipe(server, recipeId);
        if (datapackRecipe != null) {
            return datapackRecipe;
        }
        Item item = itemFromVirtualFuelId(recipeId.identifier());
        if (item == null) {
            return null;
        }
        ItemStack fuel = new ItemStack(item);
        int burnTicks = fuel.getBurnTime(RecipeType.SMELTING, server.fuelValues());
        return burnTicks > 0 ? fuelHolder(fuel, burnTicks) : null;
    }

    private static @Nullable FuelValues fuelValues(@Nullable MinecraftServer server, @Nullable Level level) {
        if (level != null) {
            return level.fuelValues();
        }
        return server == null ? null : server.fuelValues();
    }

    private @Nullable ResourceHandler<ItemResource> itemInputHandler(MachineBlockEntity machine) {
        return machine.machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinTopoResourceIntegrations.ITEM.resourceType(), RecipeRole.INPUT);
    }

    private @Nullable RecipeHolder<TopoRecipe> fuelHolder(ItemStack fuel, int burnTicks) {
        TopoRecipe recipe = fuelRecipe(fuel, burnTicks);
        if (recipe == null) {
            return null;
        }
        return new RecipeHolder<>(
                ResourceKey.create(Registries.RECIPE, fuelRecipeId(fuel.getItem())),
                recipe);
    }

    private @Nullable TopoRecipe fuelRecipe(ItemStack fuel, int burnTicks) {
        int duration = usableDuration(burnTicks);
        if (duration <= 0) {
            return null;
        }
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        FluidRecipeCapability fluids = BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
        ScalarRecipeCapability heat = BuiltinTopoResourceIntegrations.HEAT.recipeCapability();
        return createRecipe(
                new TopoRecipe.InputEntry<?>[] {
                        new TopoRecipe.InputEntry<>(items, List.of(TopoItemInput.of(fuel.copyWithCount(1)))),
                        new TopoRecipe.InputEntry<>(
                                fluids,
                                List.of(new TopoFluidIngredient(
                                        new FluidStackTemplate(Fluids.WATER, waterForDuration(duration)))))
                },
                TopoRecipe.EMPTY_OUTPUTS,
                TopoRecipe.EMPTY_INPUTS,
                new TopoRecipe.OutputEntry<?>[] {
                        new TopoRecipe.OutputEntry<>(heat, List.of((long) HEAT_PER_TICK))
                },
                duration,
                HEAT_LINE);
    }

    private static int usableDuration(int burnTicks) {
        return burnTicks - Math.floorMod(burnTicks, BURN_TICKS_PER_WATER_MB);
    }

    private static int waterForDuration(int duration) {
        return duration / BURN_TICKS_PER_WATER_MB;
    }

    private Identifier fuelRecipeId(Item item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item, "item"));
        return Identifier.fromNamespaceAndPath(
                id().getNamespace(),
                VIRTUAL_FUEL_PREFIX + itemId.getNamespace() + "/" + itemId.getPath());
    }

    private @Nullable Item itemFromVirtualFuelId(Identifier recipeId) {
        if (!recipeId.getNamespace().equals(id().getNamespace())) {
            return null;
        }
        String path = recipeId.getPath();
        if (!path.startsWith(VIRTUAL_FUEL_PREFIX)) {
            return null;
        }
        String itemPath = path.substring(VIRTUAL_FUEL_PREFIX.length());
        int separator = itemPath.indexOf('/');
        if (separator <= 0 || separator >= itemPath.length() - 1) {
            return null;
        }
        Identifier itemId = Identifier.fromNamespaceAndPath(
                itemPath.substring(0, separator),
                itemPath.substring(separator + 1));
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .filter(item -> item != Items.AIR)
                .orElse(null);
    }
}
