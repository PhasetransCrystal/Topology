package net.ptcrys.topo.data.recipe.special;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Combustion generator recipes are datapack recipes plus a virtual furnace-fuel overlay.
 *
 * <p>
 * The overlay follows the current world's {@link FuelValues}, so item fuel data maps and
 * NeoForge fuel hooks from other mods are honored without generating one JSON file per fuel item.
 */
public final class CombustionGeneratorRecipeType extends TopoRecipeType<TopoRecipe> {

    public static final int ENERGY_PER_BURN_TICK = 20;
    private static final String VIRTUAL_FUEL_PREFIX = "combustion_generator/fuel/";
    private static final List<ProductionLine> ENERGY_LINE = List.of(BuiltinTopoProductionLines.ENERGY);

    public CombustionGeneratorRecipeType(Identifier id) {
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
            if (burnTicks > 0) {
                return fuelHolder(fuel, burnTicks);
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
            if (burnTicks > 0) {
                recipes.add(fuelRecipe(fuel, burnTicks));
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

    private RecipeHolder<TopoRecipe> fuelHolder(ItemStack fuel, int burnTicks) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, fuelRecipeId(fuel.getItem())),
                fuelRecipe(fuel, burnTicks));
    }

    private TopoRecipe fuelRecipe(ItemStack fuel, int burnTicks) {
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        return createRecipe(
                new TopoRecipe.InputEntry<?>[] {
                        new TopoRecipe.InputEntry<>(items, List.of(TopoItemInput.of(fuel.copyWithCount(1))))
                },
                TopoRecipe.EMPTY_OUTPUTS,
                TopoRecipe.EMPTY_INPUTS,
                new TopoRecipe.OutputEntry<?>[] {
                        new TopoRecipe.OutputEntry<>(energy, List.of((long) ENERGY_PER_BURN_TICK))
                },
                burnTicks,
                ENERGY_LINE);
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
