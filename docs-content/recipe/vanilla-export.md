---
sidebar_position: 4
---

# 原版配方导出

面向需要把 Topo 配方输出为原版配方数据的模组开发者。导出是**写投影**：在数据生成阶段写入原版配方 JSON 与解锁进度，
不创建 Topo 镜像配方，也不把原版配方表导入当前类型。

本页是 [Recipe](../recipe.md) 模块的子页。

## 绑定导出适配器

在 `registerRecipeTypes` 中为配方类型绑定唯一的导出适配器，并声明目标原版配方类型：

```java
craftingType = recipe.recipeType("crafting")
        .displayName("Crafting", "合成")
        .exportRecipesTo(
                VanillaExportedRecipeAdapters.shaped(recipe),   // recipe：当前 RecipeDomainRegistration
                RecipeType.CRAFTING);
```

`VanillaExportedRecipeAdapters` 提供三种工厂：

| 工厂                                | 输出的原版配方                                                |
|-------------------------------------|---------------------------------------------------------------|
| `shaped(domain)`                    | 有序合成                                                      |
| `shapeless(domain)`                 | 无序合成                                                      |
| `cooking(domain, kind)`             | 烹饪；`kind` 为 `SMELTING` / `BLASTING` / `SMOKING` / `CAMPFIRE` |

一个类型只能绑定一个导出适配器；重复绑定不同适配器、或在 freeze 后配置都会报错。

## 提供 ExportHints

适配器不读取 Topo 配方内容，只按 `ExportHints` 生成原版数据。声明配方后调用 `exportRecipe` 交出提示：

```java
TopoRecipe gear = craftingType.recipe("copper_gear")
        .input(itemCapability.in(copperIngot, 4))       // 与下面的 "##","##" 保持一致
        .output(itemCapability.out(gearItem, 1))
        .duration(200)
        .save();

craftingType.exportRecipe("copper_gear", gear, ExportHints.shaped(
        "copper_gear",                                      // 本插件命名空间下的路径
        RecipeCategory.MISC,
        null,                                               // group
        new ExportHints.Unlock.ItemUnlock(() -> copperIngot),
        () -> gearItem,                                     // 原版产物
        1,                                                  // 原版产物数量
        List.of("##", "##"),
        List.of(new ExportHints.ShapedKey(
                '#',
                new ExportHints.IngredientSpec.ItemLikeSupplier(() -> copperIngot)))));
```

三种提示工厂与关键内容：

| 工厂                                                                                                              | 用途     | 关键内容                                         |
|-------------------------------------------------------------------------------------------------------------------|----------|--------------------------------------------------|
| `ExportHints.shaped(foreignPath, category, group, unlock, result, resultCount, pattern, keys)`                     | 有序合成 | `pattern` 每行一个字符串；`keys` 的字符不能是空格 |
| `ExportHints.shapeless(foreignPath, category, group, unlock, result, resultCount, ingredients)`                    | 无序合成 | `CountedIngredient` 列表，数量必须为正            |
| `ExportHints.cooking(foreignPath, category, cookingCategory, group, unlock, result, input, experience, cookingTime)` | 烹饪     | 产物数量固定为 1；经验非负、时间必须为正         |

`foreignPath` 不能为空，最终配方 id 为 `domain.id(foreignPath)`，因此只能写入本插件命名空间。
`unlock` 为空时默认用原版产物解锁；烹饪在没有显式解锁时改用配方输入解锁。

## 导出失败

适配器按提示中的布局选择导出路径：

- 类型绑定了 `shaped` / `shapeless`，但提示没有对应布局：返回 `null`，`exportRecipe` 抛 `IllegalStateException`。
- 类型绑定了 `cooking`，但提示没有烹饪信息或产物数量不是 1：同样视为不可导出。
- 同一个原版配方 id 已被其他类型导出：登记时报错。

导出成功后，框架记录该 id 的归属类型。该类型不会把这条原版配方再导入成自己的配方；其他类型仍可正常导入它。

## API 速查

| 类/方法                                                     | 用途             |
|-------------------------------------------------------------|------------------|
| `exportRecipesTo(adapter, RecipeType)`                      | 绑定唯一导出投影 |
| `VanillaExportedRecipeAdapters.shaped/shapeless/cooking`    | 原版配方适配器   |
| `type.exportRecipe(name, recipe, hints)`                    | 提交导出提示     |
| `ExportHints.shaped/shapeless/cooking`                      | 构造提示         |
| `ExportHints.Unlock.ItemUnlock/Tag`                         | 解锁进度条件     |
| `ExportHints.IngredientSpec.ItemLikeSupplier/Tag/IngredientRaw` | 提示中的输入身份 |
