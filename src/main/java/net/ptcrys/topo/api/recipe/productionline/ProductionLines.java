package net.ptcrys.topo.api.recipe.productionline;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Production-line table (query + freeze). Product writes only via
 * {@link RecipeDomainRegistration#productionLine(String)}.
 *
 * <p>
 * Freeze builds one immutable view per line in a single recipe pass. Before freeze the live view
 * remains available so recipe declarations can be queried while the table is still open.
 */
public final class ProductionLines {

    private static final FreezableStrategyRegistry<Identifier, ProductionLine, ProductionLineView> REGISTRY = FreezableStrategyRegistry.create("production lines");

    private static volatile Map<ProductionLine, ProductionLineView> frozenViews = Map.of();

    private ProductionLines() {}

    @ApiStatus.Internal
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
        ProductionLine line = REGISTRY.require(id);
        ProductionLineView frozen = frozenViews.get(line);
        return frozen != null ? frozen : Objects.requireNonNull(REGISTRY.strategy(id), "production line view " + id);
    }

    @ApiStatus.Internal
    public static void freeze() {
        if (REGISTRY.isFrozen()) {
            return;
        }
        REGISTRY.freeze();
        Map<ProductionLine, List<TopoRecipe>> recipesByLine = recipesByLine();
        Map<ProductionLine, ProductionLineView> views = new LinkedHashMap<>();
        for (ProductionLine line : registered()) {
            views.put(line, build(line, recipesByLine.getOrDefault(line, List.of())));
        }
        frozenViews = Collections.unmodifiableMap(views);
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    private static ProductionLineView build(ProductionLine line) {
        return build(line, filterRecipes(recipe -> recipe.productionLines().contains(line)));
    }

    private static ProductionLineView build(ProductionLine line, List<TopoRecipe> recipes) {
        List<TopoRecipeType<?>> recipeTypes = collectRecipeTypes(recipes);
        return new Snapshot(
                line,
                recipes,
                recipeTypes,
                collectMachines(recipeTypes),
                collectInputs(recipes),
                collectOutputs(recipes),
                collectTickInputs(recipes),
                collectTickOutputs(recipes));
    }

    /** One pass over every declared recipe; used only at the freeze boundary. */
    private static Map<ProductionLine, List<TopoRecipe>> recipesByLine() {
        Map<ProductionLine, List<TopoRecipe>> index = new LinkedHashMap<>();
        for (ProductionLine line : registered()) {
            index.put(line, new ArrayList<>());
        }
        for (TopoRecipeType<?> recipeType : TopoRecipeTypes.registered()) {
            for (TopoRecipe recipe : recipeType.registeredRecipes()) {
                for (ProductionLine line : recipe.productionLines()) {
                    List<TopoRecipe> matches = index.get(line);
                    if (matches == null) {
                        throw new IllegalStateException(
                                "recipe " + recipeType.id() + " references unregistered production line " + line.id());
                    }
                    matches.add(recipe);
                }
            }
        }
        return index;
    }

    private static List<TopoRecipe> filterRecipes(Predicate<TopoRecipe> predicate) {
        List<TopoRecipe> result = new ArrayList<>();
        for (TopoRecipeType<?> recipeType : TopoRecipeTypes.registered()) {
            for (TopoRecipe recipe : recipeType.registeredRecipes()) {
                if (predicate.test(recipe)) {
                    result.add(recipe);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<TopoRecipeType<?>> collectRecipeTypes(List<TopoRecipe> recipes) {
        LinkedHashSet<TopoRecipeType<?>> result = new LinkedHashSet<>();
        for (TopoRecipe recipe : recipes) {
            result.add(recipe.recipeType());
        }
        return List.copyOf(result);
    }

    private static List<MachineDefinition> collectMachines(List<TopoRecipeType<?>> recipeTypes) {
        LinkedHashSet<MachineDefinition> result = new LinkedHashSet<>();
        for (TopoRecipeType<?> recipeType : recipeTypes) {
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
    private static boolean machineImportsForeignOf(MachineDefinition machine, TopoRecipeType<?> sourceType) {
        var foreign = sourceType.exportedForeignType();
        if (foreign == null) {
            return false;
        }
        for (TopoRecipeType<?> mounted : machine.recipeTypes()) {
            if (mounted.importedRecipeTypes().contains(foreign)) {
                return true;
            }
        }
        return false;
    }

    private static List<TopoRecipe.InputEntry<?>> collectInputs(List<TopoRecipe> recipes) {
        LinkedHashSet<TopoRecipe.InputEntry<?>> result = new LinkedHashSet<>();
        for (TopoRecipe recipe : recipes) {
            result.addAll(List.of(recipe.inputs()));
        }
        return List.copyOf(result);
    }

    private static List<TopoRecipe.OutputEntry<?>> collectOutputs(List<TopoRecipe> recipes) {
        LinkedHashSet<TopoRecipe.OutputEntry<?>> result = new LinkedHashSet<>();
        for (TopoRecipe recipe : recipes) {
            result.addAll(List.of(recipe.outputs()));
        }
        return List.copyOf(result);
    }

    private static List<TopoRecipe.InputEntry<?>> collectTickInputs(List<TopoRecipe> recipes) {
        LinkedHashSet<TopoRecipe.InputEntry<?>> result = new LinkedHashSet<>();
        for (TopoRecipe recipe : recipes) {
            result.addAll(List.of(recipe.tickInputs()));
        }
        return List.copyOf(result);
    }

    private static List<TopoRecipe.OutputEntry<?>> collectTickOutputs(List<TopoRecipe> recipes) {
        LinkedHashSet<TopoRecipe.OutputEntry<?>> result = new LinkedHashSet<>();
        for (TopoRecipe recipe : recipes) {
            result.addAll(List.of(recipe.tickOutputs()));
        }
        return List.copyOf(result);
    }

    private record Snapshot(
                            ProductionLine line,
                            List<TopoRecipe> recipes,
                            List<TopoRecipeType<?>> recipeTypes,
                            List<MachineDefinition> machines,
                            List<TopoRecipe.InputEntry<?>> inputs,
                            List<TopoRecipe.OutputEntry<?>> outputs,
                            List<TopoRecipe.InputEntry<?>> tickInputs,
                            List<TopoRecipe.OutputEntry<?>> tickOutputs)
            implements ProductionLineView {

        private Snapshot {
            Objects.requireNonNull(line, "production line");
            recipes = List.copyOf(recipes);
            recipeTypes = List.copyOf(recipeTypes);
            machines = List.copyOf(machines);
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            tickInputs = List.copyOf(tickInputs);
            tickOutputs = List.copyOf(tickOutputs);
        }
    }

    /** Live view used before freeze; after freeze the immutable snapshot map is authoritative. */
    private static final class View implements ProductionLineView {

        private final ProductionLine line;
        private volatile ProductionLineView snapshot;

        private View(ProductionLine line) {
            this.line = line;
        }

        private ProductionLineView snapshot() {
            ProductionLineView current = snapshot;
            if (current != null) {
                return current;
            }
            if (!REGISTRY.isFrozen()) {
                return build(line);
            }
            current = build(line);
            snapshot = current;
            return current;
        }

        @Override
        public ProductionLine line() {
            return line;
        }

        @Override
        public List<TopoRecipe> recipes() {
            return snapshot().recipes();
        }

        @Override
        public List<TopoRecipeType<?>> recipeTypes() {
            return snapshot().recipeTypes();
        }

        @Override
        public List<MachineDefinition> machines() {
            return snapshot().machines();
        }

        @Override
        public List<TopoRecipe.InputEntry<?>> inputs() {
            return snapshot().inputs();
        }

        @Override
        public List<TopoRecipe.OutputEntry<?>> outputs() {
            return snapshot().outputs();
        }

        @Override
        public List<TopoRecipe.InputEntry<?>> tickInputs() {
            return snapshot().tickInputs();
        }

        @Override
        public List<TopoRecipe.OutputEntry<?>> tickOutputs() {
            return snapshot().tickOutputs();
        }
    }
}
