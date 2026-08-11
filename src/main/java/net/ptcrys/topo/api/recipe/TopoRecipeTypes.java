package net.ptcrys.topo.api.recipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Recipe-type table (query + freeze). Product writes only via
 * {@link RecipeDomainRegistration#recipeType}.
 */
public final class TopoRecipeTypes {

    private static final FreezableStrategyRegistry<Identifier, TopoRecipeType<?>, TopoRecipeType<?>> REGISTRY = FreezableStrategyRegistry.create("oi recipe types");

    private TopoRecipeTypes() {}

    /**
     * Internal write entry for {@link RecipeDomainRegistration}. Binds the
     * type to RegistryLib on the given core and records it in the freezable table.
     */
    public static TopoRecipeType<TopoRecipe> begin(
                                                   RegistryCore core, Identifier id, LangDomainRegistration lang) {
        return begin(core, new TopoRecipeType<>(id, TopoRecipe::new), lang);
    }

    /**
     * Internal write entry for custom {@link TopoRecipeType} subclasses.
     * {@code lang} may be null only for detached unit-test types that never call
     * {@link TopoRecipeType#displayName(String, String)}.
     */
    public static <R extends TopoRecipe, T extends TopoRecipeType<R>> T begin(
                                                                              RegistryCore core, T recipeType, @org.jspecify.annotations.Nullable LangDomainRegistration lang) {
        TopoRecipeType.bind(core, recipeType, lang);
        REGISTRY.register(recipeType.id(), recipeType, recipeType);
        return recipeType;
    }

    public static TopoRecipeType<?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<TopoRecipeType<?>> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    /**
     * 为每个已注册配方类型安装冻结期预览槽位计划。planner 是 data 侧行为决策（规范机选择），
     * 本方法只定时机：bootstrap 在 {@code Machines.freeze()} 之后调用一次；重复安装抛错。
     */
    public static void installPreviewPlans(Function<TopoRecipeType<?>, RecipePreviewPlan> planner) {
        Objects.requireNonNull(planner, "preview plan planner");
        if (!REGISTRY.isFrozen()) {
            throw new IllegalStateException(
                    "oi recipe types: preview plans must install after the recipe type freeze");
        }
        for (TopoRecipeType<?> recipeType : registered()) {
            recipeType.installPreviewPlan(
                    Objects.requireNonNull(planner.apply(recipeType), "preview plan for " + recipeType.id()));
        }
    }

    public static void invalidateSearchIndexes() {
        for (TopoRecipeType<?> recipeType : registered()) {
            recipeType.invalidateSearchIndex();
        }
    }

    public static void buildSearchIndexes(MinecraftServer server) {
        for (TopoRecipeType<?> recipeType : registered()) {
            recipeType.rebuildSearchIndex(server);
        }
    }
}
