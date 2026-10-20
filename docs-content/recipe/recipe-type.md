---
sidebar_position: 3
---

# TopoRecipeType

配方类型大多不需要子类——`recipe.recipeType(path)` 用默认工厂即可。只有需要**特殊配方载体**（额外状态、
专用序列化）时才继承。

本页是 [Recipe](../recipe.md) 模块的子页——概念与页面导航见 [Recipe](../recipe.md)。

## 基类

```java
public class TopoRecipeType<R extends TopoRecipe> {

    protected TopoRecipeType(Identifier id, RecipeFactory<R> recipeFactory);  // 默认工厂 = TopoRecipe::new

    public TopoRecipeType<R> displayName(String en, String cn);
    public TopoRecipeType<R> progressBar(Identifier texture);                 // 还有 FillDirection/宽高重载
    public TopoRecipeType<R> importRecipesFrom(Function<..., ImportedRecipeAdapter<R>>, RecipeType<?>... types);
    public TopoRecipeType<R> exportRecipesTo(..., RecipeType<?> foreignType);
    public TopoRecipe.Builder<R> recipe(String recipeName);
    public ResourceKey<Recipe<?>> recipeKey(String recipeName);
    public TopoRecipeType<R> addRecipe(String recipeName, R recipe);
    public @Nullable RecipeHolder<R> findRecipe(MachineBlockEntity machine);

    @FunctionalInterface
    public interface RecipeFactory<R extends TopoRecipe> {
        R create(TopoRecipeType<R> type,
                 TopoRecipe.InputEntry<?>[] inputs, TopoRecipe.OutputEntry<?>[] outputs,
                 TopoRecipe.InputEntry<?>[] tickInputs, TopoRecipe.OutputEntry<?>[] tickOutputs,
                 int duration, List<ProductionLine> productionLines);
    }
}
```

```java
// 自定义载体：工厂负责从条目数组重建实例（序列化入口）
public final class SpellRecipeType extends TopoRecipeType<SpellRecipe> {
    public SpellRecipeType(Identifier id) { super(id, SpellRecipe::new); }
}
```

## 注册

```java
// registerRecipeTypes：
recipe.recipeType("spell_crafting")
        .displayName("Spell Crafting", "魔法合成")
        .progressBar(/* texture */);

// 自定义子类版本——id 必须与 path 匹配：
recipe.recipeType("spell_crafting", new SpellRecipeType(plugin.recipe().id("spell_crafting")))
        .displayName("Spell Crafting", "魔法合成")
        .progressBar(/* texture */);
```

框架自动完成 RegistryLib 配方类型注册、序列化器与流编解码绑定。只有导入/导出外部配方表
（原版、其他 Mod）时才需要 `ImportedRecipeAdapter` / `ExportedRecipeAdapter`。

## TopoRecipe.Builder — 完整链

`recipe(name)` 和 `addRecipe(name, ...)` 接受该类型下的路径，不能含 `modid:`。
RegistryLib 自动加上配方类型路径与所属命名空间：类型 `mymod:grinding` 的 `copper/to_dust`
会输出为 `data/mymod/recipe/grinding/copper/to_dust.json`。非法路径在声明时拒绝。
`type.recipeKey("copper/to_dust")` 返回完整的配方注册表键；声明去重、导入替换和运行时引用使用这个键，
不会再从已加载配方的键拆出局部 path 来判断身份。

导入配方的运行时键使用 `imports/<类型路径长度>/<类型路径>/<来源 namespace>/<来源完整 path>`，
例如 `mymod:imports/8/grinding/minecraft/iron_ingot`。类型路径可嵌套，长度字段明确其边界；来源 namespace 与 path 保留独立边界；
与已有配方键碰撞时直接报错。旧版下划线编码无法可靠还原来源，不做猜测迁移；旧版活动导入配方
进入既有的缺失配方状态并保留输入和进度，旧引用需显式迁移。默认进度条由框架提供，指向 `topo:textures/gui/progress_bar/arrow.png`；
下游仅在需要自定义外观时覆盖纹理。

```java
type.recipe("path")
        .input(use) / .output(use)          // 启动 I/O（RecipeInputUse/RecipeOutputUse）
        .tickInput(use) / .tickOutput(use)  // 每 tick I/O
        .duration(ticks)                    // 默认 100
        .productionLine(line)               // ProductionLine 句柄
        .productionLines(lines...)
        .buildRecipe()                      // 返回 R（不保存）
        .save()                             // 保存进配方表
```

## ProductionLine — 生产线

```java
// registerRecipeTypes（最晚 registerRecipes；ProductionLines.freeze() 在钩子 16 末）：
ProductionLine macerating = recipe.productionLine("macerating");

// 配方引用：
type.recipe("iron_dust")
        .productionLine(macerating)
        ...
        .save();
```

查询：`ProductionLines.require(id)` / `ProductionLines.registered()`。

## API 速查

| 类/方法                                                                                                                                 | 用途                               |
|-----------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| `TopoRecipeType(id, factory)`                                                                                                           | 自定义配方类型（默认工厂够用）     |
| `displayName(en, cn)` / `progressBar(...)`                                                                                              | 显示与进度条                       |
| `importRecipesFrom(...)` / `exportRecipesTo(...)`                                                                                       | 外部表桥接                         |
| `recipeType.recipe(name)` / `.addRecipe(name, recipe)`                                                                                  | 配方写入                           |
| `recipeType.findRecipe(machine)`                                                                                                        | 机器侧查找                         |
| `recipe.recipeType(path[, customType])`                                                                                                 | 领域注册（钩子 2）                 |
| `recipe.productionLine(path)`                                                                                                           | 生产线（钩子 2/16，freeze 在 16 末） |
| `type.recipe(id)` + `.input/.output/.tickInput/.tickOutput/.duration/.productionLine/.replacesImported/.buildRecipe/.save` | 配方构建                           |
