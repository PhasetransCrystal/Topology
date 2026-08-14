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

```java
new TopoRecipe.Builder<>(type, "mymod:path")
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
// registerRecipeTypes（最晚 registerRecipes；ProductionLines.freeze() 在钩子 8 末）：
ProductionLine macerating = recipe.productionLine("macerating");

// 配方引用：
new TopoRecipe.Builder<>(type, "mymod:iron_dust")
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
| `recipe.productionLine(path)`                                                                                                           | 生产线（钩子 2/8，freeze 在 8 末） |
| `TopoRecipe.Builder(type, id)` + `.input/.output/.tickInput/.tickOutput/.duration/.productionLine/.replacesImported/.buildRecipe/.save` | 配方构建                           |
