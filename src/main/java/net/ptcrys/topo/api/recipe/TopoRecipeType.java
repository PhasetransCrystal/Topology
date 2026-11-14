package net.ptcrys.topo.api.recipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.util.entry.RecipeTypeEntry;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.google.gson.JsonElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Registered Topo machine recipe type.
 *
 * <p>
 * 可 {@link #importRecipesFrom} 其它原版/模组 {@link RecipeType}（GTCEu proxy 语义）：搜索索引与
 * JEI 展示会把来源表经适配器投影成本类型配方，<b>不</b>再 datagen 复制 JSON。来源表与适配器
 * 在一次调用里原子绑定。手写配方与导入冲突时须 {@link TopoRecipe.Builder#replacesImported()}
 * 显式剔除导入项，否则索引构建失败。
 */
public class TopoRecipeType<R extends TopoRecipe> {

    private final Identifier id;
    private final RecipeFactory<R> recipeFactory;
    private final RecipeBookCategory bookCategory = new RecipeBookCategory();
    private Identifier progressBarTexture;
    private int progressBarTextureWidth = 20;
    private int progressBarTextureHeight = 40;
    private FillDirection fillDirection = FillDirection.LEFT_TO_RIGHT;
    private @Nullable RecipeTypeEntry<R> entry;
    private volatile @Nullable RecipePreviewPlan previewPlan;
    private final RecipeCatalog<R> catalog = new RecipeCatalog<>(this);
    private final RecipeImports<R> imports = new RecipeImports<>(this);
    private final RecipeSearchState<R> searchState = new RecipeSearchState<>(this::collectSearchHolders);
    private @Nullable LangKey displayName;
    /** Owning plugin lang; set by {@link TopoRecipeTypes#begin} before displayName is called. */
    @Nullable
    LangDomainRegistration lang;
    /**
     * Vanilla-facing types (crafting/cooking tables) are product authoring types that export to
     * foreign serializers; they must not mount on machines or appear as Topo JEI categories.
     */
    private boolean vanillaFacing;
    private boolean configurationFrozen;

    protected TopoRecipeType(Identifier id, RecipeFactory<R> recipeFactory) {
        this.id = Objects.requireNonNull(id, "recipe type id");
        this.recipeFactory = Objects.requireNonNull(recipeFactory, "recipe factory");
        this.progressBarTexture = IdHelper.topo("textures/gui/progress_bar/arrow.png");
    }

    /** Package write entry used by {@link TopoRecipeTypes#begin}. */
    static <R extends TopoRecipe, T extends TopoRecipeType<R>> T bind(
                                                                      RegistryCore core, T recipeType, @Nullable LangDomainRegistration lang) {
        recipeType.bindLang(lang);
        recipeType.bindEntry(core);
        return recipeType;
    }

    final void freezeConfiguration() {
        configurationFrozen = true;
    }

    private void requireConfigurationOpen() {
        if (configurationFrozen) {
            throw new IllegalStateException(
                    "Recipe type " + id + " configuration is frozen; declare display/import/export before the recipe type freeze");
        }
    }

    final void bindLang(@Nullable LangDomainRegistration lang) {
        this.lang = lang;
    }

    /** Bound by {@link TopoRecipeTypes#begin}; package-private so typed subclass handles compile. */
    final void bindEntry(RegistryCore core) {
        if (entry != null) {
            throw new IllegalStateException("Recipe type " + id + " is already bound to RegistryLib");
        }
        RecipeTypeEntry<R> entry = core.<R>recipeType(id.getPath())
                .serializer(TopoRecipeSerializer.codecFor(this), TopoRecipeSerializer.streamCodecFor(this))
                .register();
        this.entry = entry;
    }

    public Identifier id() {
        return id;
    }

    /**
     * 配方类型显示名（JEI 类别标题等）。须在注册时 {@link #displayName(String, String)} 声明；
     * 未声明时回退为 path 字面量。
     */
    public Component displayName() {
        return displayName != null ? displayName.getComponent() : Component.literal(id.getPath());
    }

    /**
     * 注册中英文显示名，lang key = {@code recipe_type.<namespace>.<path>}。
     * Namespace ownership is the plugin that began this type ({@link LangDomainRegistration}).
     */
    public TopoRecipeType<R> displayName(String en, String cn) {
        requireConfigurationOpen();
        if (lang == null) {
            throw new IllegalStateException(
                    "recipe type " + id + " has no lang domain — register via plugin.recipe().recipeType(...)");
        }
        this.displayName = lang.resource(id, "recipe_type", en, cn);
        return this;
    }

    public RecipeType<R> vanillaType() {
        return requireEntry().get();
    }

    public RecipeSerializer<R> serializer() {
        return requireEntry().getSerializer();
    }

    public RecipeTypeEntry<R> entry() {
        return requireEntry();
    }

    public RecipeBookCategory recipeBookCategory() {
        return bookCategory;
    }

    public Identifier progressBarTexture() {
        return progressBarTexture;
    }

    public int progressBarTextureWidth() {
        return progressBarTextureWidth;
    }

    public int progressBarTextureFrameHeight() {
        return progressBarTextureHeight / 2;
    }

    public FillDirection fillDirection() {
        return fillDirection;
    }

    public TopoRecipeType<R> progressBar(Identifier texture) {
        return progressBar(texture, FillDirection.LEFT_TO_RIGHT);
    }

    public TopoRecipeType<R> progressBar(Identifier texture, FillDirection direction) {
        return progressBar(texture, direction, 20, 40);
    }

    public TopoRecipeType<R> progressBar(
                                         Identifier texture,
                                         FillDirection direction,
                                         int textureWidth,
                                         int textureHeight) {
        requireConfigurationOpen();
        if (textureWidth <= 0) {
            throw new IllegalArgumentException("Progress bar texture width must be positive");
        }
        if (textureHeight <= 0 || textureHeight % 2 != 0) {
            throw new IllegalArgumentException("Progress bar texture height must be a positive even number");
        }
        this.progressBarTexture = Objects.requireNonNull(texture, "progress bar texture");
        this.fillDirection = Objects.requireNonNull(direction, "progress bar fill direction");
        this.progressBarTextureWidth = textureWidth;
        this.progressBarTextureHeight = textureHeight;
        return this;
    }

    /**
     * 在类型声明链上挂外来导入（父子一体）：本类型为父，外来表 + 适配器为子配置。
     *
     * <p>
     * {@code adapterFactory} 收到 {@code this}（机器类型）。烹饪表投影应挂在对应的
     * vanilla-facing cooking 类型上（如 {@code SMELTING::poweredImport}），不要再单独建 Adapter 类。
     *
     * <pre>{@code
     * RECIPE.recipeType("electric_furnace")
     *     .displayName(...)
     *     .importRecipesFrom(SMELTING::poweredImport, SMELTING.foreignRecipeType());
     * }</pre>
     */
    public TopoRecipeType<R> importRecipesFrom(
                                               Function<? super TopoRecipeType<R>, ? extends ImportedRecipeAdapter<R>> adapterFactory,
                                               RecipeType<?>... types) {
        requireConfigurationOpen();
        Objects.requireNonNull(adapterFactory, "imported recipe adapter factory");
        return importRecipesFrom(adapterFactory.apply(this), types);
    }

    /**
     * 原子声明外来配方导入（GTCEu proxy）：来源表 + 投影适配器成套绑定，不写入本类型 datapack
     * JSON。
     *
     * <p>
     * {@code types} 与 {@code adapter} 缺一不可；可多次调用以追加来源表，但适配器必须始终是
     * 同一个实例（一类型一条投影规则）。优先用 {@link #importRecipesFrom(java.util.function.Function,
     * RecipeType[])} 在声明链上配置。
     */
    public TopoRecipeType<R> importRecipesFrom(ImportedRecipeAdapter<R> adapter, RecipeType<?>... types) {
        requireConfigurationOpen();
        imports.importRecipesFrom(adapter, types);
        return this;
    }

    /**
     * Marks this type as vanilla-facing: product authoring for workbench/furnace tables, default
     * export, no machine mount, no Topo JEI category.
     */
    public TopoRecipeType<R> vanillaFacing() {
        requireConfigurationOpen();
        this.vanillaFacing = true;
        return this;
    }

    public boolean isVanillaFacing() {
        return vanillaFacing;
    }

    /**
     * Binds the default write projection for this type (atomic with foreign {@link RecipeType}).
     * Used by vanilla-facing types and optional per-recipe {@code exportAs}.
     */
    public TopoRecipeType<R> exportRecipesTo(
                                             Function<? super TopoRecipeType<R>, ? extends ExportedRecipeAdapter<R>> adapterFactory,
                                             RecipeType<?> foreignType) {
        requireConfigurationOpen();
        Objects.requireNonNull(adapterFactory, "exported recipe adapter factory");
        Objects.requireNonNull(foreignType, "foreign recipe type");
        imports.exportRecipesTo(Objects.requireNonNull(adapterFactory.apply(this), "exported recipe adapter"), foreignType);
        return this;
    }

    public TopoRecipeType<R> exportRecipesTo(ExportedRecipeAdapter<R> adapter, RecipeType<?> foreignType) {
        requireConfigurationOpen();
        imports.exportRecipesTo(adapter, foreignType);
        return this;
    }

    public @Nullable ExportedRecipeAdapter<R> exportedRecipeAdapter() {
        return imports.exportedAdapter();
    }

    public @Nullable RecipeType<?> exportedForeignType() {
        return imports.exportedForeignType();
    }

    /**
     * Write projection after an Topo recipe has been minted. Builder / product code must not emit
     * foreign JSON itself — only this path (via {@link ExportedRecipeAdapter}).
     *
     * <p>
     * Hard-fails when export is scheduled (adapter bound or vanilla-facing) and the adapter
     * returns {@code null}. No-ops when the type has no export adapter and is not vanilla-facing.
     *
     * @param recipeName Topo recipe name (path under this type)
     * @param recipe     minted Topo recipe, or {@code null} if only hints are used at datagen time
     * @param hints      layout / cooking / result side-channel for foreign serializers
     */
    public void exportRecipe(String recipeName, @Nullable R recipe, ExportHints hints) {
        imports.exportRecipe(recipeName, recipe, hints, vanillaFacing);
    }

    public List<RecipeType<?>> importedRecipeTypes() {
        return imports.importedTypes();
    }

    public @Nullable ImportedRecipeAdapter<R> importedRecipeAdapter() {
        return imports.importedAdapter();
    }

    final void markReplacesImported(String recipeName) {
        catalog.markReplacesImported(recipeName);
    }

    final boolean replacesImported(ResourceKey<Recipe<?>> recipeKey) {
        return catalog.replacesImported(recipeKey);
    }

    /**
     * 冻结期安装的预览槽位计划（JEI 面板与机内配方页共用）。bootstrap 在机器冻结后经
     * {@link TopoRecipeTypes#installPreviewPlans} 安装；安装前访问抛错——不存在"静默空计划"。
     */
    public RecipePreviewPlan previewPlan() {
        RecipePreviewPlan plan = previewPlan;
        if (plan == null) {
            throw new IllegalStateException("Recipe type " + id + " has no preview plan yet; TopoRecipeTypes.installPreviewPlans runs after machine freeze");
        }
        return plan;
    }

    final void installPreviewPlan(RecipePreviewPlan plan) {
        Objects.requireNonNull(plan, "preview plan");
        if (this.previewPlan != null) {
            throw new IllegalStateException("Recipe type " + id + " already has a preview plan installed");
        }
        this.previewPlan = plan;
    }

    /** A path under this recipe type; RegistryLib supplies the namespace and type prefix. */
    public TopoRecipe.Builder<R> recipe(String recipeName) {
        recipeKey(recipeName);
        return createRecipeBuilder(recipeName);
    }

    /**
     * Resolves a local declaration path to its complete recipe identity without registry lookup.
     * The resulting key does not imply that its recipe has been loaded or registered.
     */
    public final ResourceKey<Recipe<?>> recipeKey(String recipeName) {
        if (recipeName == null || recipeName.isBlank() || recipeName.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Recipe name for type '" + id + "' must be a nonblank path without namespace: " + recipeName);
        }
        // Validate the final location now, before any factory or datagen callback is queued.
        return ResourceKey.create(Registries.RECIPE,
                id.withSuffix("/" + recipeName));
    }

    protected TopoRecipe.Builder<R> createRecipeBuilder(String recipeName) {
        return new TopoRecipe.Builder<>(this, recipeName);
    }

    public R createRecipe(
                          TopoRecipe.InputEntry<?>[] inputs,
                          TopoRecipe.OutputEntry<?>[] outputs,
                          TopoRecipe.InputEntry<?>[] tickInputs,
                          TopoRecipe.OutputEntry<?>[] tickOutputs,
                          int duration) {
        return createRecipe(inputs, outputs, tickInputs, tickOutputs, duration, List.of());
    }

    public R createRecipe(
                          TopoRecipe.InputEntry<?>[] inputs,
                          TopoRecipe.OutputEntry<?>[] outputs,
                          TopoRecipe.InputEntry<?>[] tickInputs,
                          TopoRecipe.OutputEntry<?>[] tickOutputs,
                          int duration,
                          List<ProductionLine> productionLines) {
        return recipeFactory.create(this, inputs, outputs, tickInputs, tickOutputs, duration, productionLines);
    }

    public TopoRecipeType<R> addRecipe(String recipeName, R recipe) {
        catalog.add(recipeName, recipe);
        return this;
    }

    public TopoRecipeType<R> addRecipe(String recipeName, Function<HolderLookup.Provider, R> recipeFactory) {
        catalog.add(recipeName, recipeFactory);
        return this;
    }

    public List<R> registeredRecipes() {
        return catalog.registered();
    }

    public Collection<R> displayRecipes(@Nullable MinecraftServer server, @Nullable Level level) {
        if (server == null) {
            return List.of();
        }
        List<R> recipes = new ArrayList<>();
        for (RecipeHolder<R> holder : collectSearchHolders(server)) {
            recipes.add(holder.value());
        }
        return List.copyOf(recipes);
    }

    public @Nullable RecipeHolder<R> findRecipe(MachineBlockEntity machine) {
        return vanillaFacing ? null : searchState.findRecipe(machine);
    }

    /** Exact recheck for a pool-local historical candidate; callers must validate revision first. */
    public boolean matchesRememberedRecipe(
                                           MachineBlockEntity machine,
                                           RecipeHolder<? extends TopoRecipe> remembered) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(remembered, "remembered recipe");
        return remembered.value().recipeType() == this && remembered.value().matchInputs(machine);
    }

    public long searchRevision() {
        return searchState.revision();
    }

    @SuppressWarnings("unchecked")
    public @Nullable RecipeHolder<R> resolveRecipe(MinecraftServer server, ResourceKey<Recipe<?>> recipeId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(recipeId, "recipe id");
        var resolved = server.getRecipeManager().byKey(recipeId);
        if (resolved.isPresent() && resolved.get().value() instanceof TopoRecipe recipe && recipe.recipeType() == this) {
            return (RecipeHolder<R>) resolved.get();
        }
        for (RecipeHolder<R> holder : collectSearchHolders(server)) {
            if (holder.id().equals(recipeId)) {
                return holder;
            }
        }
        return null;
    }

    public boolean resourceVersionSearchCacheable(MachineBlockEntity machine) {
        return searchState.resourceVersionSearchCacheable(machine);
    }

    public void rebuildSearchIndex(MinecraftServer server) {
        searchState.rebuild(server);
    }

    List<RecipeHolder<R>> collectSearchHolders(MinecraftServer server) {
        return imports.collectHolders(server, catalog);
    }

    ResourceKey<Recipe<?>> importedHolderId(ResourceKey<Recipe<?>> sourceId) {
        return imports.importedHolderId(sourceId);
    }

    public void invalidateSearchIndex() {
        searchState.invalidate();
    }

    private RecipeTypeEntry<R> requireEntry() {
        RecipeTypeEntry<R> current = entry;
        if (current == null) {
            throw new IllegalStateException("Recipe type " + id + " is detached from RegistryLib");
        }
        return current;
    }

    JsonElement recipeJson(R recipe) {
        return TopoRecipeSerializer.codecFor(this)
                .codec()
                .encodeStart(JsonOps.INSTANCE, recipe)
                .getOrThrow();
    }

    @FunctionalInterface
    public interface RecipeFactory<R extends TopoRecipe> {

        R create(
                 TopoRecipeType<R> recipeType,
                 TopoRecipe.InputEntry<?>[] inputs,
                 TopoRecipe.OutputEntry<?>[] outputs,
                 TopoRecipe.InputEntry<?>[] tickInputs,
                 TopoRecipe.OutputEntry<?>[] tickOutputs,
                 int duration,
                 List<ProductionLine> productionLines);
    }
}
