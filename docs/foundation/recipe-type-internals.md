# 配方类型内部职责

面向框架开发者，适用于当前配方类型的实现维护。

内部实现按职责拆分：`RecipeCatalog` 管理本地声明/去重/替换，`RecipeImports` 管理外部导入导出投影，
`RecipeSearchState` 管理运行时搜索索引；`TopoRecipeType` 只保留公共句柄与配置入口。

