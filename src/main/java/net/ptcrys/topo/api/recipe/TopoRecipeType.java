package net.ptcrys.topo.api.recipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.util.entry.RecipeTypeEntry;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.recipe.content.TopoFluidIngredient;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.api.recipe.search.TopoRecipeSearchIndex;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStackTemplate;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
    private volatile @Nullable TopoRecipeSearchIndex<R> searchIndex;
    private volatile @Nullable RecipePreviewPlan previewPlan;
    private final AtomicLong searchRevision = new AtomicLong();
    private final Map<String, DuplicateAwareRecipeFactory<R>> recipesByName = new LinkedHashMap<>();
    /** 导入来源表（GTCEu {@code proxyRecipes}）；按声明序投影。 */
    private final List<RecipeType<?>> importedRecipeTypes = new ArrayList<>();
    private @Nullable ImportedRecipeAdapter<R> importedRecipeAdapter;
    /** 手写配方名 → 声明 replacesImported，索引期剔除冲突的导入项。 */
    private final Set<String> recipesReplacingImported = new LinkedHashSet<>();
    private @Nullable LangKey displayName;
    /** Owning plugin lang; set by {@link TopoRecipeTypes#begin} before displayName is called. */
    @Nullable
    LangDomainRegistration lang;
    /**
     * Vanilla-facing types (crafting/cooking tables) are product authoring types that export to
     * foreign serializers; they must not mount on machines or appear as Topo JEI categories.
     */
    private boolean vanillaFacing;
    private @Nullable ExportedRecipeAdapter<R> exportedRecipeAdapter;
    private @Nullable RecipeType<?> exportedForeignType;

    protected TopoRecipeType(Identifier id, RecipeFactory<R> recipeFactory) {
        this.id = Objects.requireNonNull(id, "recipe type id");
        this.recipeFactory = Objects.requireNonNull(recipeFactory, "recipe factory");
        this.progressBarTexture = Identifier.fromNamespaceAndPath(
                id.getNamespace(), "textures/gui/progress_bar/arrow.png");
    }

    /** Package write entry used by {@link TopoRecipeTypes#begin}. */
    static <R extends TopoRecipe, T extends TopoRecipeType<R>> T bind(
                                                                      RegistryCore core, T recipeType, @Nullable LangDomainRegistration lang) {
        recipeType.bindLang(lang);
        recipeType.bindEntry(core);
        return recipeType;
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
                                               java.util.function.Function<? super TopoRecipeType<R>, ? extends ImportedRecipeAdapter<R>> adapterFactory,
                                               RecipeType<?>... types) {
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
        Objects.requireNonNull(adapter, "imported recipe adapter");
        Objects.requireNonNull(types, "imported recipe types");
        if (types.length == 0) {
            throw new IllegalArgumentException(
                    "importRecipesFrom requires at least one foreign RecipeType for " + id);
        }
        if (importedRecipeAdapter != null && importedRecipeAdapter != adapter) {
            throw new IllegalStateException(
                    "Recipe type " + id + " already imports with a different adapter; one type, one projection");
        }
        importedRecipeAdapter = adapter;
        for (RecipeType<?> type : types) {
            importedRecipeTypes.add(Objects.requireNonNull(type, "imported recipe type"));
        }
        return this;
    }

    /**
     * Marks this type as vanilla-facing: product authoring for workbench/furnace tables, default
     * export, no machine mount, no Topo JEI category.
     */
    public TopoRecipeType<R> vanillaFacing() {
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
        Objects.requireNonNull(adapterFactory, "exported recipe adapter factory");
        Objects.requireNonNull(foreignType, "foreign recipe type");
        ExportedRecipeAdapter<R> adapter = Objects.requireNonNull(
                adapterFactory.apply(this), "exported recipe adapter");
        if (exportedRecipeAdapter != null && exportedRecipeAdapter != adapter) {
            throw new IllegalStateException(
                    "Recipe type " + id + " already exports with a different adapter");
        }
        this.exportedRecipeAdapter = adapter;
        this.exportedForeignType = foreignType;
        return this;
    }

    public TopoRecipeType<R> exportRecipesTo(ExportedRecipeAdapter<R> adapter, RecipeType<?> foreignType) {
        Objects.requireNonNull(adapter, "exported recipe adapter");
        Objects.requireNonNull(foreignType, "foreign recipe type");
        if (exportedRecipeAdapter != null && exportedRecipeAdapter != adapter) {
            throw new IllegalStateException(
                    "Recipe type " + id + " already exports with a different adapter");
        }
        this.exportedRecipeAdapter = adapter;
        this.exportedForeignType = foreignType;
        return this;
    }

    public @Nullable ExportedRecipeAdapter<R> exportedRecipeAdapter() {
        return exportedRecipeAdapter;
    }

    public @Nullable RecipeType<?> exportedForeignType() {
        return exportedForeignType;
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
        Objects.requireNonNull(recipeName, "recipeName");
        Objects.requireNonNull(hints, "hints");
        ExportedRecipeAdapter<R> adapter = exportedRecipeAdapter;
        if (adapter == null) {
            if (vanillaFacing) {
                throw new IllegalStateException(
                        "Recipe type " + id + " is vanilla-facing but has no exportRecipesTo adapter");
            }
            return;
        }
        Identifier foreignId = adapter.scheduleExport(recipeName, recipe, hints);
        if (foreignId == null) {
            throw new IllegalStateException(
                    "Export adapter returned null for " + id + " / " + recipeName + " (foreign path " + hints.foreignPath() + ")");
        }
        ExportRegistry.register(foreignId, id);
    }

    public List<RecipeType<?>> importedRecipeTypes() {
        return Collections.unmodifiableList(importedRecipeTypes);
    }

    public @Nullable ImportedRecipeAdapter<R> importedRecipeAdapter() {
        return importedRecipeAdapter;
    }

    final void markReplacesImported(String recipeName) {
        recipesReplacingImported.add(Objects.requireNonNull(recipeName, "recipe name"));
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

    public TopoRecipe.Builder<R> recipe(String recipeName) {
        return createRecipeBuilder(recipeName);
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
        duplicateAwareRecipe(recipeName).addDirect(recipe);
        return this;
    }

    public TopoRecipeType<R> addRecipe(String recipeName, Function<HolderLookup.Provider, R> recipeFactory) {
        duplicateAwareRecipe(recipeName).add(recipeFactory);
        return this;
    }

    public List<R> registeredRecipes() {
        List<R> recipes = new ArrayList<>(recipesByName.size());
        for (DuplicateAwareRecipeFactory<R> factory : recipesByName.values()) {
            R recipe = factory.registeredRecipe();
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return List.copyOf(recipes);
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

    public @Nullable RecipeHolder<R> findRecipe(net.ptcrys.topo.api.machine.MachineBlockEntity machine) {
        if (vanillaFacing) {
            // Hard guard: vanilla-facing mirrors are not machine-runnable (unordered bag / no grid).
            return null;
        }
        Level level = machine.getLevel();
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return null;
        }
        // Optimization: delegate to the per-type capability-aware search index instead of copying
        // and linearly scanning RecipeManager.byType() on every machine tick. Principle: the index
        // pays recipe-list traversal once per server recipe-set generation, then searches by live
        // machine resource keys and runs precise capability matching only on likely candidates.
        return searchIndex(level.getServer()).findRecipe(machine);
    }

    /** Exact recheck for a pool-local historical candidate; callers must validate revision first. */
    public boolean matchesRememberedRecipe(
                                           net.ptcrys.topo.api.machine.MachineBlockEntity machine,
                                           RecipeHolder<? extends TopoRecipe> remembered) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(remembered, "remembered recipe");
        return remembered.value().recipeType() == this && remembered.value().matchInputs(machine);
    }

    public long searchRevision() {
        return searchRevision.get();
    }

    @SuppressWarnings("unchecked")
    public @Nullable RecipeHolder<R> resolveRecipe(MinecraftServer server, ResourceKey<Recipe<?>> recipeId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(recipeId, "recipe id");
        var resolved = server.getRecipeManager().byKey(recipeId);
        if (resolved.isPresent() && resolved.get().value() instanceof TopoRecipe recipe && recipe.recipeType() == this) {
            return (RecipeHolder<R>) resolved.get();
        }
        // 导入配方使用合成 id，不在 RecipeManager 本类型表内。
        for (RecipeHolder<R> holder : collectSearchHolders(server)) {
            if (holder.id().equals(recipeId)) {
                return holder;
            }
        }
        return null;
    }

    public boolean resourceVersionSearchCacheable(net.ptcrys.topo.api.machine.MachineBlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return false;
        }
        // Optimization safety: RecipeLogic may reuse a failed search only when the index
        // has proven that every start-input capability in this recipe type is invalidated by the
        // machine resource-content version.
        return searchIndex(level.getServer()).resourceVersionSearchCacheable();
    }

    public synchronized void rebuildSearchIndex(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        // Optimization: bind the snapshot to the current server identity without retaining a
        // strong MinecraftServer reference. Principle: lazy rebuilds remain safe across test
        // servers or integrated-server restarts, and ServerStopped can still release holders.
        // 本类型 datapack ∪ 导入投影 − replacesImported 剔除（对齐 GTCEu initDB + proxy）。
        searchIndex = TopoRecipeSearchIndex.build(collectSearchHolders(server), System.identityHashCode(server));
        // Optimization safety: failed-search caches in machine logic key on this revision.
        // Principle: recipe reloads or lazy rebuilds must invalidate "no recipe" answers even
        // when the machine inventory itself did not change.
        searchRevision.incrementAndGet();
    }

    /**
     * 搜索/展示用的完整 holder 列表：本类型 datapack + 适配后的导入配方。
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<RecipeHolder<R>> collectSearchHolders(MinecraftServer server) {
        List<RecipeHolder<R>> own = new ArrayList<>(server.getRecipeManager().recipeMap().byType(vanillaType()));
        appendMissingDeclaredGameTestRecipes(own);
        if (importedRecipeTypes.isEmpty()) {
            return own;
        }
        // importRecipesFrom always sets adapter with at least one type; keep invariant check.
        if (importedRecipeAdapter == null) {
            throw new IllegalStateException(
                    "Recipe type " + id + " has import sources but no adapter (broken importRecipesFrom)");
        }

        Set<Object> ownKeys = new HashSet<>();
        Set<Object> replacedImportKeys = new HashSet<>();
        for (RecipeHolder<R> holder : own) {
            Set<Object> keys = itemConflictKeys(holder.value());
            if (recipesReplacingImported.contains(ownRecipeName(holder))) {
                replacedImportKeys.addAll(keys);
            } else {
                ownKeys.addAll(keys);
            }
        }

        List<RecipeHolder<R>> merged = new ArrayList<>(own);
        Set<ResourceKey<Recipe<?>>> seenIds = new HashSet<>();
        for (RecipeHolder<R> holder : own) {
            seenIds.add(holder.id());
        }

        for (RecipeType<?> importedType : importedRecipeTypes) {
            for (RecipeHolder<?> source : holdersOf(server, importedType)) {
                // R1': skip only foreign ids exported by *this* type (same-type re-entry).
                // Cross-type exports (e.g. SMELTING → ELECTRIC_FURNACE) must remain importable.
                if (ExportRegistry.isExportedBy(source.id(), id)) {
                    continue;
                }
                R adapted = importedRecipeAdapter.adapt(source.id().identifier(), source.value());
                if (adapted == null) {
                    continue;
                }
                Set<Object> keys = itemConflictKeys(adapted);
                if (!keys.isEmpty() && !Collections.disjoint(keys, replacedImportKeys)) {
                    // 手写 replacesImported：剔除冲突的导入项。
                    continue;
                }
                if (!keys.isEmpty() && !Collections.disjoint(keys, ownKeys)) {
                    throw new IllegalStateException(
                            "Recipe type " + id + ": imported " + source.id().identifier() + " conflicts with a written recipe on item-input keys " + keys + ". Mark the written recipe with replacesImported(), or change inputs.");
                }
                ResourceKey<Recipe<?>> importedId = importedHolderId(source.id());
                if (!seenIds.add(importedId)) {
                    continue;
                }
                merged.add(new RecipeHolder<>(importedId, adapted));
            }
        }
        return merged;
    }

    /**
     * GameTest fixture declarations are intentionally excluded from release jars and may be absent
     * from a fresh developer checkout before datagen runs. In fixture-enabled processes only,
     * expose missing declared recipes directly while preserving RecipeManager/datapack entries as
     * authoritative whenever the same id is present.
     */
    private void appendMissingDeclaredGameTestRecipes(List<RecipeHolder<R>> recipes) {
        if (!Boolean.getBoolean("oi.gametest.fixtures")) {
            return;
        }
        Set<ResourceKey<Recipe<?>>> seenIds = new HashSet<>();
        for (RecipeHolder<R> holder : recipes) {
            seenIds.add(holder.id());
        }
        for (Map.Entry<String, DuplicateAwareRecipeFactory<R>> entry : recipesByName.entrySet()) {
            R declared = entry.getValue().registeredRecipe();
            if (declared == null) {
                continue;
            }
            ResourceKey<Recipe<?>> recipeId = ResourceKey.create(
                    Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(
                            id.getNamespace(), id.getPath() + "/" + entry.getKey()));
            if (seenIds.add(recipeId)) {
                recipes.add(new RecipeHolder<>(recipeId, declared));
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Collection<RecipeHolder<?>> holdersOf(MinecraftServer server, RecipeType<?> type) {
        return (Collection) server.getRecipeManager().recipeMap().byType((RecipeType) type);
    }

    /**
     * 导入配方合成 id：{@code <本类型>/<来源前缀><原 path>}（path 一律小写；大写仅用于 Java 句柄常量名）。
     * <ul>
     * <li>原版（{@code minecraft}）→ {@code vanilla_}，如 {@code electric_furnace/vanilla_iron_ingot}</li>
     * <li>其它模组 → {@code <modid>_}，如 {@code electric_furnace/create_crushed_iron}</li>
     * </ul>
     */
    private ResourceKey<Recipe<?>> importedHolderId(ResourceKey<Recipe<?>> sourceId) {
        Identifier source = sourceId.identifier();
        String prefixedPath = id.getPath() + "/" + importSourcePrefix(source.getNamespace()) + source.getPath();
        return ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath(id.getNamespace(), prefixedPath));
    }

    /**
     * 导入来源前缀（小写）：原版固定 {@code vanilla_}，其它模组用其 namespace + {@code _}。
     */
    static String importSourcePrefix(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "vanilla_";
        }
        return namespace + "_";
    }

    private String ownRecipeName(RecipeHolder<R> holder) {
        String path = holder.id().identifier().getPath();
        String prefix = id.getPath() + "/";
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }

    /**
     * 物品起始输入冲突键：用于导入/手写去重与冲突检测（熔炼类单物品足够）。
     */
    private static Set<Object> itemConflictKeys(TopoRecipe recipe) {
        Set<Object> keys = new HashSet<>();
        for (TopoRecipe.InputEntry<?> entry : recipe.inputs()) {
            for (Object content : entry.contents()) {
                if (content instanceof TopoItemInput itemInput) {
                    addItemConflictKeys(itemInput, keys);
                }
            }
        }
        return keys;
    }

    private static void addItemConflictKeys(TopoItemInput input, Set<Object> keys) {
        switch (input) {
            case TopoItemInput.Resource resource -> keys.add(resource.template().item().value());
            case TopoItemInput.Tag tag -> keys.add(tag.tag());
            case TopoItemInput.AnyOf anyOf -> {
                for (ItemStackTemplate template : anyOf.templates()) {
                    keys.add(template.item().value());
                }
            }
            case TopoItemInput.Unconsumed unconsumed -> keys.add(unconsumed.template().item().value());
        }
    }

    public void invalidateSearchIndex() {
        searchIndex = null;
        // Optimization safety: invalidation is also a semantic recipe-set boundary for machines
        // that skipped searching after a previous miss.
        searchRevision.incrementAndGet();
    }

    private TopoRecipeSearchIndex<R> searchIndex(MinecraftServer server) {
        int serverToken = System.identityHashCode(server);
        TopoRecipeSearchIndex<R> index = searchIndex;
        if (index != null && index.serverToken() == serverToken) {
            return index;
        }
        // Optimization: lazy rebuild is a safety net for tests and unusual server lifecycles.
        // Principle: normal play builds at server-start/reload boundaries, but correctness should
        // not depend on event ordering when a test calls findRecipe directly.
        synchronized (this) {
            index = searchIndex;
            if (index == null || index.serverToken() != serverToken) {
                rebuildSearchIndex(server);
                index = Objects.requireNonNull(searchIndex, "rebuilt recipe search index");
            }
            return index;
        }
    }

    private RecipeTypeEntry<R> requireEntry() {
        RecipeTypeEntry<R> current = entry;
        if (current == null) {
            throw new IllegalStateException("Recipe type " + id + " is detached from RegistryLib");
        }
        return current;
    }

    private DuplicateAwareRecipeFactory<R> duplicateAwareRecipe(String recipeName) {
        Objects.requireNonNull(recipeName, "recipe name");
        DuplicateAwareRecipeFactory<R> duplicateAware = recipesByName.get(recipeName);
        if (duplicateAware == null) {
            duplicateAware = new DuplicateAwareRecipeFactory<>(this, recipeName);
            recipesByName.put(recipeName, duplicateAware);
            entry().addRecipe(recipeName, duplicateAware::createAndValidate);
        }
        return duplicateAware;
    }

    private JsonElement recipeJson(R recipe) {
        return TopoRecipeSerializer.codecFor(this)
                .codec()
                .encodeStart(JsonOps.INSTANCE, recipe)
                .getOrThrow();
    }

    private static final class DuplicateAwareRecipeFactory<R extends TopoRecipe> {

        private final TopoRecipeType<R> recipeType;
        private final String recipeName;
        private final List<Function<HolderLookup.Provider, R>> factories = new ArrayList<>();
        private @Nullable R registeredRecipe;

        private DuplicateAwareRecipeFactory(TopoRecipeType<R> recipeType, String recipeName) {
            this.recipeType = recipeType;
            this.recipeName = recipeName;
        }

        private void add(Function<HolderLookup.Provider, R> factory) {
            factories.add(Objects.requireNonNull(factory, "recipe factory"));
        }

        private void addDirect(R recipe) {
            Objects.requireNonNull(recipe, "recipe");
            add(_provider -> recipe);
            registeredRecipe = merge(registeredRecipe, recipe);
        }

        private R createAndValidate(HolderLookup.Provider provider) {
            R primary = factories.getFirst().apply(provider);
            LinkedHashSet<ProductionLine> productionLines = new LinkedHashSet<>(primary.productionLines());
            for (int i = 1; i < factories.size(); i++) {
                R duplicate = factories.get(i).apply(provider);
                productionLines.addAll(duplicate.productionLines());
                if (!sameRecipeContents(primary, duplicate)) {
                    throw new IllegalStateException(
                            "Duplicate recipe " + recipeType.id() + "/" + recipeName + " has conflicting contents: first=" + recipeType.recipeJson(primary) + ", duplicate=" + recipeType.recipeJson(duplicate));
                }
            }
            R resolved = recipeType.createRecipe(
                    primary.inputs(),
                    primary.outputs(),
                    primary.tickInputs(),
                    primary.tickOutputs(),
                    primary.duration(),
                    List.copyOf(productionLines));
            registeredRecipe = resolved;
            return resolved;
        }

        private @Nullable R registeredRecipe() {
            return registeredRecipe;
        }

        private R merge(@Nullable R current, R incoming) {
            if (current == null) {
                return incoming;
            }
            if (!sameRecipeContents(current, incoming)) {
                throw new IllegalStateException(
                        "Duplicate recipe " + recipeType.id() + "/" + recipeName + " has conflicting contents: first=" + recipeType.recipeJson(current) + ", duplicate=" + recipeType.recipeJson(incoming));
            }
            LinkedHashSet<ProductionLine> productionLines = new LinkedHashSet<>(current.productionLines());
            productionLines.addAll(incoming.productionLines());
            return recipeType.createRecipe(
                    current.inputs(),
                    current.outputs(),
                    current.tickInputs(),
                    current.tickOutputs(),
                    current.duration(),
                    List.copyOf(productionLines));
        }

        private boolean sameRecipeContents(R first, R second) {
            return first.duration() == second.duration() && sameInputs(first.inputs(), second.inputs()) && sameOutputs(first.outputs(), second.outputs()) && sameInputs(first.tickInputs(), second.tickInputs()) && sameOutputs(first.tickOutputs(), second.tickOutputs());
        }

        private static boolean sameInputs(TopoRecipe.InputEntry<?>[] first, TopoRecipe.InputEntry<?>[] second) {
            if (first.length != second.length) {
                return false;
            }
            for (int i = 0; i < first.length; i++) {
                if (first[i].capability() != second[i].capability() || !sameContents(first[i].contents(), second[i].contents())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean sameOutputs(TopoRecipe.OutputEntry<?>[] first, TopoRecipe.OutputEntry<?>[] second) {
            if (first.length != second.length) {
                return false;
            }
            for (int i = 0; i < first.length; i++) {
                if (first[i].capability() != second[i].capability() || !sameContents(first[i].contents(), second[i].contents())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean sameContents(List<?> first, List<?> second) {
            if (first.size() != second.size()) {
                return false;
            }
            for (int i = 0; i < first.size(); i++) {
                if (!Objects.equals(contentKey(first.get(i)), contentKey(second.get(i)))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Identity key for recipe-content equality. Exhaustive on known content families; unknown
         * types throw so a new content shape cannot silently degrade to reference/equals drift.
         */
        private static Object contentKey(Object content) {
            return switch (content) {
                case null -> null;
                case TopoItemInput item -> switch (item) {
                    case TopoItemInput.Resource resource -> resource.contentKey();
                    case TopoItemInput.Unconsumed unconsumed -> unconsumed.contentKey();
                    case TopoItemInput.Tag tag -> tag;
                    case TopoItemInput.AnyOf anyOf -> anyOf;
                };
                case TopoItemOutput output -> output.contentKey();
                case TopoFluidIngredient ingredient -> ingredient.contentKey();
                case Long amount -> amount;
                case Object[] objects -> Arrays.asList(objects);
                default -> {
                    if (content.getClass().isArray()) {
                        // Primitive arrays: identity is rare; keep stable component-wise list.
                        int length = java.lang.reflect.Array.getLength(content);
                        List<Object> list = new ArrayList<>(length);
                        for (int i = 0; i < length; i++) {
                            list.add(java.lang.reflect.Array.get(content, i));
                        }
                        yield list;
                    }
                    throw new IllegalArgumentException(
                            "unsupported recipe content for equality key: " + content.getClass().getName());
                }
            };
        }
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
