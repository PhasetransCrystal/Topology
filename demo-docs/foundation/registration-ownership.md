# Demo 注册归属

面向 TopoDemo 维护者。本文说明 Demo 如何通过 Topo 的插件与领域注册入口组织内容。

## 使用的 Topo 能力

- `TopoPlugin`、`TopoPlugins.register(plugin)`：在 Mod 构造期登记插件。
- 各领域 `*DomainRegistration`：在对应钩子中声明类型与内容。
- 统一 `RegistryCore`：`TopoDemo.REGISTRY` 承载 Demo 条目与命名空间。

## Demo 如何组合

`TopoDemoApiPlugin` 声明 Demo 的基础类型：材料数据类型、材料形态、机器类型、矿脉基础类型和 API 侧语言键。`TopoDemoPlugin` 声明具体内容：资源家族、端口元数据、材料、装备、机器、管道、矿脉及配方。两个插件共享同一个 `topodemo` 注册核心，但通过各自的领域入口提交贡献。

材料形态通过 `DemoCreativeTabKeys` 引用页签身份，实际页签及图标由内容插件创建；API 基础类型不依赖具体页签实例。物品端口及其元数据、材料研磨处理器和原版矿石数据生成器属于内容侧。机器部件角色依赖具体资源家族，也在内容侧机器基础阶段注册。

配方能力与机器资源类型由 `BuiltinTopoResourceIntegrations` 在配方基础阶段一起声明；该阶段早于配方类型和机器基础阶段。具体端口元数据在机器基础阶段登记，机器目录随后构建，管道目录在机器后续阶段登记。不要因为代码编译通过就推断注册成功：静态字段初始化过早可能越过冻结边界或触发注册重入。

旧版 `OfficialTopoPlugin` 同时拥有框架及官方内容；迁移时按实现职责分别迁入 Topo 和 Demo。当前生产线标识及管道策略已部分迁入，但旧配方、材料、装备和机器产品目录仍须逐项核对；不能将空的 `BuiltinRecipes.init()` 视为旧版配方注册完成。

## 验收状态

注册阶段与冻结边界已按当前实现记录；实际注册成功与否、运行行为与游戏内容仍需按验证文档手动执行。

## 模块文档

- [物品配方能力](../recipe/item-capability.md)、[原版配方链](../recipe/vanilla-chain.md)
- [装备目录](../equipment/catalog.md)
- [管道资源与策略](../pipe/resources-and-strategies.md)
- [矿脉与矿石方块](../ore/veins-and-blocks.md)、[矿脉 JEI 面板](../ore/vein-display.md)、[原版矿石替换](../ore/vanilla-ore-replacement.md)
- [方块标签](../material/block-tags.md)
- [锅炉燃料](../machine/boiler-fuel.md)、[多方块结构与回归](../machine/multiblock-verification.md)
- [集成适配](../integration/adapters.md)
