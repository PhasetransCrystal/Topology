# 物品配方能力

面向 TopoDemo 维护者。本文说明 Demo 如何实现 Topo 的配方能力契约。

## 使用的 Topo 能力

- `SlottedRecipeCapability`：槽位型配方的 I/O、匹配、传输与序列化基类。
- `TopoItemInput` / `TopoItemOutput`：物品资源输入/输出。
- `RecipeInputUse` / `RecipeOutputUse`：配方声明侧的铸造入口。

## Demo 如何组合

当前注册使用 `ItemRecipeCapability`，只接受精确物品输入；旧版物品配方支持标签、替代物与不消耗输入。新增的 `FlexibleItemRecipeCapability` 已实现标签、催化剂、不消耗输入与联合 IO 规划，但尚未被任何配方类型引用。

迁移须在接入后验收，不能通过枚举部分物品或改窄输入语义冒充完整导入。

## 验收状态

能力实现存在；接入配方类型、替换当前精确物品能力与游戏内执行仍需完成和验收。

## 相关

- 公开契约：[`docs-content/recipe.md`](../../docs-content/recipe.md)、[RecipeCapability](../../docs-content/recipe/recipe-capability.md)
- 框架内部：[`docs/recipe/README.md`](../../docs/recipe/README.md)
