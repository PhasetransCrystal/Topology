# TopoDemo 文档

面向 TopoDemo 维护者。TopoDemo 是独立内容 Mod，用 Topology 的公开入口演示并验证下游如何组合这些能力；它不随框架发布，也不属于框架契约。

每页回答三件事：

1. **使用的 Topo 能力**：引用了哪些公开契约、注册域或运行时接口。
2. **Demo 如何组合**：Demo 侧如何声明类型、策略、组件和数据。
3. **验收状态**：已接线、待验证或尚未迁移。

框架内部细节见 [`docs/`](../docs/)，下游用法见 [`docs-content/`](../docs-content/)。

## 复用注意

- Demo 是内容 Mod，不是框架契约；不要把 Demo 的数据类型、机器类或策略当成 Topo 公共 API。
- 复用前确认注册阶段与冻结边界；Demo 中可用不代表下游在任意时机可用。
- Demo 页面的“未接线/待验收”只说明示例进度，不是框架能力的最终结论。
- 世界生成、JEI/Jade/AE、渲染与游戏交互仍需按验证文档手动执行。

## foundation

- [注册归属与迁移现状](foundation/registration-ownership.md)：两个 Demo 插件如何使用 TopoPlugin 与领域注册。

## equipment

- [装备目录](equipment/catalog.md)：如何使用 `EquipmentStrategy` 铸造装备。

## material

- [方块标签](material/block-tags.md)：如何用材料形态策略为生成方块附加标签。

## ore

- [矿脉与矿石方块](ore/veins-and-blocks.md)：如何使用环境、宿主规则与矿脉声明。
- [矿脉 JEI 面板](ore/vein-display.md)：如何实现 `OreVeinDisplay.Strategy`。
- [原版矿石替换](ore/vanilla-ore-replacement.md)：如何用数据生成替换原版矿石。

## machine

- [锅炉燃料与配方](machine/boiler-fuel.md)：如何用组件、端口与配方视图组合燃料储备。
- [多方块结构与回归](machine/multiblock-verification.md)：如何使用蓝图、检测与结构工具。

## pipe

- [管道资源与策略](pipe/resources-and-strategies.md)：如何使用资源类型、方块模板与分发策略。

## recipe

- [物品配方能力](recipe/item-capability.md)：如何实现 `SlottedRecipeCapability`。
- [原版配方链](recipe/vanilla-chain.md)：如何组合 Topo 配方、原版导出与电炉导入。

## integration

- [集成适配](integration/adapters.md)：如何在内容侧接入 AE2、Jade 与 JEI。
