package net.ptcrys.topo.apiv2.recipe.productionline;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.OIRecipeTypes;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Production-line table (query + freeze). Product writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration#productionLine(String)}.
 */
public final class ProductionLines {

    private static final FreezableStrategyRegistry<Identifier, ProductionLine, ProductionLineView> REGISTRY = FreezableStrategyRegistry.create("production lines");

    private ProductionLines() {}

    /**
     * Internal write entry for {@link net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration}. {@code id}
     * namespace must match the owning plugin.
     */
    public static ProductionLine begin(Identifier id) {
        ProductionLine line = new ProductionLine(Objects.requireNonNull(id, "id"));
        REGISTRY.register(id, line, new View(line));
        return line;
    }

    public static ProductionLine require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<ProductionLine> registered() {
        return REGISTRY.handlesView();
    }

    public static ProductionLineView view(Identifier id) {
        REGISTRY.require(id);
        return Objects.requireNonNull(REGISTRY.strategy(id), "production line view " + id);
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    private static final class View implements ProductionLineView {

        private final ProductionLine line;

        private View(ProductionLine line) {
            this.line = line;
        }

        @Override
        public ProductionLine line() {
            return line;
        }

        @Override
        public List<OIRecipe> recipes() {
            return filterRecipes(recipe -> recipe.productionLines().contains(line));
        }

        @Override
        public List<OIRecipeType<?>> recipeTypes() {
            LinkedHashSet<OIRecipeType<?>> result = new LinkedHashSet<>();
            for (OIRecipe recipe : recipes()) {
                result.add(recipe.recipeType());
            }
            return List.copyOf(result);
        }

        @Override
        public List<MachineDefinition> machines() {
            LinkedHashSet<MachineDefinition> result = new LinkedHashSet<>();
            for (OIRecipeType<?> recipeType : recipeTypes()) {
                for (MachineDefinition machine : Machines.registered()) {
                    if (machine.supportsRecipeType(recipeType) || machineImportsForeignOf(machine, recipeType)) {
                        result.add(machine);
                    }
                }
            }
            return List.copyOf(result);
        }

        /**
         * Vanilla-facing cooking types (e.g. {@code SMELTING}) are not machine-mounted; machines
         * that {@code importRecipesFrom} the same foreign table still "run" those recipes.
         */
        private static boolean machineImportsForeignOf(
                                                       MachineDefinition machine, OIRecipeType<?> sourceType) {
            var foreign = sourceType.exportedForeignType();
            if (foreign == null) {
                return false;
            }
            for (OIRecipeType<?> mounted : machine.recipeTypes()) {
                if (mounted.importedRecipeTypes().contains(foreign)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<OIRecipe.InputEntry<?>> inputs() {
            LinkedHashSet<OIRecipe.InputEntry<?>> result = new LinkedHashSet<>();
            for (OIRecipe recipe : recipes()) {
                result.addAll(List.of(recipe.inputs()));
            }
            return List.copyOf(result);
        }

        @Override
        public List<OIRecipe.OutputEntry<?>> outputs() {
            LinkedHashSet<OIRecipe.OutputEntry<?>> result = new LinkedHashSet<>();
            for (OIRecipe recipe : recipes()) {
                result.addAll(List.of(recipe.outputs()));
            }
            return List.copyOf(result);
        }

        @Override
        public List<OIRecipe.InputEntry<?>> tickInputs() {
            LinkedHashSet<OIRecipe.InputEntry<?>> result = new LinkedHashSet<>();
            for (OIRecipe recipe : recipes()) {
                result.addAll(List.of(recipe.tickInputs()));
            }
            return List.copyOf(result);
        }

        @Override
        public List<OIRecipe.OutputEntry<?>> tickOutputs() {
            LinkedHashSet<OIRecipe.OutputEntry<?>> result = new LinkedHashSet<>();
            for (OIRecipe recipe : recipes()) {
                result.addAll(List.of(recipe.tickOutputs()));
            }
            return List.copyOf(result);
        }

        private static List<OIRecipe> filterRecipes(Predicate<OIRecipe> predicate) {
            List<OIRecipe> result = new ArrayList<>();
            for (OIRecipeType<?> recipeType : OIRecipeTypes.registered()) {
                for (OIRecipe recipe : recipeType.registeredRecipes()) {
                    if (predicate.test(recipe)) {
                        result.add(recipe);
                    }
                }
            }
            return List.copyOf(result);
        }
    }
}
