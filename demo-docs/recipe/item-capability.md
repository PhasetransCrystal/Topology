# 物品配方能力

面向 TopoDemo 维护者。本文说明 Demo 如何实现 Topo 的配方能力契约。

## 使用的 Topo 能力

- `SlottedRecipeCapability`：槽位型配方的 I/O、匹配、传输与序列化基类。
- `TopoItemInput` / `TopoItemOutput`：物品资源输入/输出。
- `RecipeInputUse` / `RecipeOutputUse`：配方声明侧的铸造入口。

## Demo 如何组合

当前注册使用 `ItemRecipeCapability`，只接受精确物品输入；旧版物品配方支持标签、替代物与不消耗输入。`FlexibleItemRecipeCapability` 包含这些输入及联合 IO 规划的迁移实现，但尚未注册、接入配方类型或完成运行验证。

迁移须在接入后验收，不能通过枚举部分物品或改窄输入语义冒充完整导入。

## 验收状态

**待完成：**确定与现有精确物品能力的组合和迁移方式，接入配方类型，并验证匹配、事务和游戏内执行。当前框架要求一个槽位资源类型只绑定一个槽位配方能力，不能将两种能力直接注册到同一个物品资源类型；直接注册会导致启动失败。不得未经设计就覆盖现有精确物品能力。

## 相关

- 公开契约：[`docs-content/recipe.md`](../../docs-content/recipe.md)、[RecipeCapability](../../docs-content/recipe/recipe-capability.md)
- 框架内部：[`docs/recipe/README.md`](../../docs/recipe/README.md)
