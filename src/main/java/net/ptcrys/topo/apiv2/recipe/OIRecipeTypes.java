package net.ptcrys.topo.apiv2.recipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Recipe-type table (query + freeze). Product writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration#recipeType}.
 */
public final class OIRecipeTypes {

    private static final FreezableStrategyRegistry<Identifier, OIRecipeType<?>, OIRecipeType<?>> REGISTRY = FreezableStrategyRegistry.create("oi recipe types");

    private OIRecipeTypes() {}

    /**
     * Internal write entry for {@link net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration}. Binds the
     * type to RegistryLib on the given core and records it in the freezable table.
     */
    public static OIRecipeType<OIRecipe> begin(
                                               RegistryCore core, Identifier id, LangDomainRegistration lang) {
        return begin(core, new OIRecipeType<>(id, OIRecipe::new), lang);
    }

    /**
     * Internal write entry for custom {@link OIRecipeType} subclasses.
     * {@code lang} may be null only for detached unit-test types that never call
     * {@link OIRecipeType#displayName(String, String)}.
     */
    public static <R extends OIRecipe, T extends OIRecipeType<R>> T begin(
                                                                          RegistryCore core, T recipeType, @org.jspecify.annotations.Nullable LangDomainRegistration lang) {
        OIRecipeType.bind(core, recipeType, lang);
        REGISTRY.register(recipeType.id(), recipeType, recipeType);
        return recipeType;
    }

    public static OIRecipeType<?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<OIRecipeType<?>> registered() {
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
    public static void installPreviewPlans(Function<OIRecipeType<?>, RecipePreviewPlan> planner) {
        Objects.requireNonNull(planner, "preview plan planner");
        if (!REGISTRY.isFrozen()) {
            throw new IllegalStateException(
                    "oi recipe types: preview plans must install after the recipe type freeze");
        }
        for (OIRecipeType<?> recipeType : registered()) {
            recipeType.installPreviewPlan(
                    Objects.requireNonNull(planner.apply(recipeType), "preview plan for " + recipeType.id()));
        }
    }

    public static void invalidateSearchIndexes() {
        for (OIRecipeType<?> recipeType : registered()) {
            recipeType.invalidateSearchIndex();
        }
    }

    public static void buildSearchIndexes(MinecraftServer server) {
        for (OIRecipeType<?> recipeType : registered()) {
            recipeType.rebuildSearchIndex(server);
        }
    }
}
