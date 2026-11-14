# 配方模块内部

面向框架开发者。配方模块以能力型 I/O 描述资源，通过配方类型、生产线与搜索/执行逻辑驱动机器。

## 职责与边界

- 负责：配方能力、配方类型、配方实例、生产线、搜索索引、导入/导出投影。
- 不负责：具体资源端口与机器执行；`RecipeLogic` 只调用能力契约。
- 配方不直接操作机器存储；I/O 通过 `RecipeCapability` 与资源端口对接。

## 关键类型与数据流

- `TopoRecipeType` / `TopoRecipeTypes`：类型句柄与冻结表。
- `TopoRecipe` / `TopoRecipeInput` / `TopoRecipeSerializer`：不可变配方与编解码。
- `RecipeCatalog` / `RecipeImports` / `RecipeSearchState`：本地声明、外部投影、运行时搜索。
- `ProductionLine` / `RecipePreviewPlan`：生产线与预览布局。
- 数据流：搜索匹配 → 检查逐 tick I/O 与输出空间 → 消耗一次性输入 → 逐 tick I/O → 完成产出。

## 为什么这样设计

- 能力把“资源种类”和“配方 I/O”解耦，新增资源类型不需要改配方类型。
- 本地声明/导入/搜索拆开，避免导入表与手写配方在同一结构里互相污染。
- 导入是运行时投影而不是复制 JSON，来源更新后不需要重新生成副本。
- 导出只有 `exportRecipe` 一个写出口，防止产品代码绕过适配器直接写外来 JSON。

## 与其他模块的联动

- 材料：提供输入/输出物品与后处理器批量配方；配方不关心材料匹配。
- 机器：`RecipeLogic` 挂载类型并执行；机器提供端口与资源类型。
- JEI/AE：能力分组决定样板槽与配方展示；导出/导入适配器桥接原版表。
- 语言：配方类型/生产线显示名走语言模块；配方本身通常不单独出键。

## 扩展规范

- 新能力继承 `RecipeCapability`/`SlottedRecipeCapability`，实现匹配、消耗、传输与序列化。
- 新类型用 `recipe.recipeType(...)` 声明；生产线最晚在 `registerRecipes` 声明。
- 具体配方在 `registerRecipes`；类型配置（导入/导出/显示）在 freeze 前完成。
- 导出用 `ExportedRecipeAdapter` + `ExportHints`；提示与配方内容由调用方保持一致。
- 补 `docs-content/recipe/*` 契约与 `docs/recipe/` 内部文档。

## 常见坑与防止

- 能力声明支持标签但未实现匹配/消耗：能力必须完整实现，不是只改类型签名。
- 认为 `.replacesImported()` 会删除来源配方：它只处理当前类型内冲突。
- 导出提示与实际产物不一致：框架不核对，调用方负责。
- 导出适配器返回 null：已安排导出时是硬失败，不是静默跳过。
- 旧版导入键编码：不猜测迁移，保留来源边界。

## 文档

- [配方能力组合与 I/O 聚合](capability-composition.md)：能力行为、配方条目聚合、事务与机器端口边界。
- [配方类型内部职责](type-internals.md)
- [配方导出投影边界](export-projection.md)
- 公开契约见 [`docs-content/recipe.md`](../../docs-content/recipe.md)。
