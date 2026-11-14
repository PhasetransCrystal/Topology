# 配方导出投影边界

面向框架开发者，说明 Topo 配方导出到外部配方表的实现边界。

## 单一写出口

`TopoRecipeType.exportRecipesTo` 为一个类型绑定唯一的 `ExportedRecipeAdapter` 和一个外来 `RecipeType`。
适配器工厂在配置期调用，freeze 后不可再改；同一类型重复绑定不同适配器直接报错。

`TopoRecipeType.exportRecipe(name, recipe, hints)` 是唯一写出口。适配器返回 foreign id 后登记到
`ExportRegistry`。导出已安排（绑定了适配器或类型为 `vanillaFacing()`）时必须得到 foreign id：
适配器缺失或返回 `null` 都会抛 `IllegalStateException`；未绑定且非 vanilla-facing 时无操作。

## 提示与配方分离

适配器只调度 RegistryLib 的数据生成（配方 JSON 与解锁进度），不创建 Topo 镜像配方，也不写入配方目录。
`ExportHints` 描述外来格式所需的布局、产物、分类与解锁条件，`TopoRecipe` 描述机器侧内容；框架不核对两者是否一致。

这是有意的边界：适配器不必解析 `TopoRecipe` 的条目结构，外来格式所需的布局由声明方显式提供。
代价是调用方必须自行保证提示与配方一致。

## 导入互斥

`ExportRegistry` 记录 foreign id 与导出类型的对应关系。导入时只跳过**同一类型**导出的配方，其他类型仍可导入
（例如 `SMELTING` 的导出仍可供 `ELECTRIC_FURNACE` 导入）。同一 foreign id 被不同类型登记时报错，
避免两个类型对同一条原版配方重复负责。

原版适配器用 `domain.id(foreignPath)` 生成 id，导出目标固定在本插件命名空间；这也限制了跨命名空间写入。
