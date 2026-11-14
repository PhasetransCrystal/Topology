# JEI 配方分类与运行时查询

面向框架开发者。说明 Topo 配方类型、机器定义和矿脉如何投影为 JEI 分类，以及运行时查询桥何时安装。公开的下游扩展方式见 [`docs-content/machine/machine-ui.md`](../../docs-content/machine/machine-ui.md) 与配方公开文档。

## 注册期分类目录

`TopoJeiPlugin` 在 JEI 生命周期中注册类别、配方、催化剂与 transfer handler。`TopoJeiCategoryRegistry` 从已注册 `TopoRecipeType` 构造分类 spec，并从已注册 `MachineDefinition` 建立配方类型到机器方块的催化剂索引。分类标题来自配方类型显示名；同一机器可以提供多个配方类型。没有机器作为催化剂时使用回退图标。

Vanilla-facing 的 Topo recipe type 不创建空壳 Topo 分类，而由对应导出/原版配方分类呈现。普通 Topo 类别的配方收集依赖 JEI 当前上下文：存在 server/level 时使用可显示的配方视图；无本地 server 时退回已注册配方列表。多方块与矿脉分类分别从已注册控制器和矿脉表提供数据。

分类注册需要对象绑定完成后才能取得机器方块等注册对象；不要把这些查询提前到插件构造期。配方能力到 JEI preview 的转换由配方 UI 工厂完成，不应在类别注册器复制一套能力解释逻辑。

## 运行时查询与交互桥

JEI runtime 可用时，插件安装 `XeiRecipeLookup` 的 JEI opener 和机器物品幽灵拖放 bridge；runtime 不可用时卸载。配方查询按 `TopoRecipeType` 单例身份映射到相应 JEI type，未注册为 JEI 分类的类型会被跳过；如果没有可映射类别，查询不触碰 JEI runtime。

AE2 样板 transfer handler 在同一插件生命周期注册，但职责独立：它把配方能力内容编码为 AE2 样板输入/输出，不负责分类建立或配方搜索。转换规则见[AE 样板内容转换](ae-pattern-conversion.md)。

## 为什么这样设计

- 分类由冻结后的 Topo 类型目录和机器声明派生，避免下游或 JEI 层重新扫描、解释机器内部结构。
- Vanilla-facing 配方沿用原版展示面，避免生成重复的 Topo 分类。
- runtime opener 按 JEI 可用期安装，使核心机器 UI 契约不直接依赖 JEI 生命周期。

## 常见坑

- 用机器名代替配方类型做类别：一个机器可以执行多种配方类型。
- 在运行时未就绪时访问 JEI runtime：只在插件的 runtime 回调安装/使用 bridge。
- 将 transfer handler 和分类注册混为一个功能：它们在生命周期上同处插件，数据责任不同。
- 认为下游配方类型自动拥有 Topo 类别：Vanilla-facing 类型会走原版分类投影。

## 实现位置

- `integration/jei/TopoJeiPlugin.java`：JEI 生命周期接线。
- `integration/jei/TopoJeiCategoryRegistry.java`、`TopoRecipeCategory.java`：分类 spec、recipe 与 catalyst。
- `integration/jei/JeiRecipeLookupPlug.java`：运行时类别查询桥。
- `integration/jei/ItemGhostDropJeiBridge.java`：机器物品幽灵拖放。
- `integration/jei/ae2/`：AE2 样板 transfer 专题实现。
