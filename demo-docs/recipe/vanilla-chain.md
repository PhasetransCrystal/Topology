# 原版配方链

面向 TopoDemo 维护者。本文说明 Demo 计划如何用 Topo 的导入/导出适配器把配方接到原版表。

## 使用的 Topo 能力

- `TopoRecipeType.exportRecipesTo`：绑定唯一 `ExportedRecipeAdapter`。
- `VanillaExportedRecipeAdapters`、`ExportHints`：生成原版合成/烹饪 JSON 与解锁进度。
- `importRecipesFrom`：电炉从原版烧炼表导入并添加能耗。
- `ExportRegistry`：记录导出归属，避免同一类型重复导入。

## Demo 如何组合

旧版的有序合成、无序合成和烹饪类型同时建立 Topo 配方与原版配方导出；电炉还从原版烧炼配方表导入并添加能耗。

当前 `BuiltinMaterialFollowUps` 只为部分材料排队原版熔炼数据，`BuiltinRecipeTypes.ELECTRIC_FURNACE` 尚未声明原版配方导入。通用导出适配器已在 Topo 侧提供，但 Demo 配方类型尚未配置 `exportRecipesTo`，也没有调用 `exportRecipe`。

## 验收状态

整条链尚未接通。验收时须同时检查 Topo 配方、原版配方 JSON 与电炉导入，不能以数据生成回调代替整条链路。

## 相关

- 公开契约：[原版配方导出](../../docs-content/recipe/vanilla-export.md)
- 框架内部：[配方导出投影](../../docs/recipe/export-projection.md)
