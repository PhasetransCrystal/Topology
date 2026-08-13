---
sidebar_position: 2
---

# Extension Points

Topology 的每个领域都遵循同一个扩展模式：**框架提供基类/接口，你继承实现，通过领域注册 API 挂载**。
本文是总览——每个扩展点的关键方法与精确签名在各领域专题页。

## 通用约定

- **KHS 句柄模式** — 所有注册项都是「句柄（Handle）+ 策略（Strategy）」成对出现。句柄只携带
  `Identifier` 身份，行为全部委托给策略实例。
- **冻结注册表** — 每个注册表都有冻结点（`freeze()`）。冻结由引擎在阶段之间自动执行，冻结后写入抛异常。
  **在错误的生命周期阶段注册 = 启动崩溃**。完整钩子顺序见 [Plugin Lifecycle](lifecycle.md)。
- **领域注册 API 是唯一写入入口** — 不直接调用 `*Registry.begin`，而是通过插件持有的
  `MaterialDomainRegistration` / `MachineDomainRegistration` 等，框架负责校验 modId 与 RegistryCore 一致。
- **en + cn 双语文案必填** — 所有用户可见名称必须同时提供英文与中文。
- **库只分发 API，不分发内容** — 不存在可引用的内置材料/机器/端口类；一切从继承开始。

## 扩展点索引（按钩子域）

| 继承什么 | 得到什么 | 注册钩子 | 专题页 |
|----------|----------|----------|--------|
| `RecipeCapability<I, O>` / `SlottedRecipeCapability` | 新资源种类的配方 I/O 能力 | 1 `registerRecipeFoundation` | [RecipeCapability](../recipe/recipe-capability.md) |
| `TopoRecipeType<R>` | 自定义配方类型（可选，默认工厂够用） | 2 `registerRecipeTypes` | [RecipeType](../recipe/recipe-type.md) |
| `MaterialFormStrategy` | 自定义材质形态 | 3 `registerMaterialFoundation` | [MaterialForm](../material/material-form.md) |
| `MaterialDataType<D>` | 材质附加属性 | 3 `registerMaterialFoundation` | [MaterialDataType](../material/material-data.md) |
| `MaterialPostProcessor` | 批量派生配方链 | 3 `registerMaterialFoundation` | [MaterialPostProcessor](../material/material-post-processor.md) |
| `EquipmentStrategy` | 装备种类（种类 × 材料 = 物品） | 9 `registerEquipmentKinds` | [Equipment](../equipment.md) |
| `MachineBlockRenderType<D>` + Strategy | 自定义机器方块渲染类型 | 10 `registerMachineFoundation` | [MachineRender](../machine/machine-render.md) |
| `PartRole` / `PropertyDisplay` | 多方块部件角色、状态显示 | 10 `registerMachineFoundation` | [Multiblock Abilities](../machine/multiblock-ability.md) |
| `MachineComponent` / `MachineTicker` | 机器行为 trait、tick、数据字段 | 11 `registerMachines` | [MachineComponent](../machine/machine-component.md) |
| `ResourcePort<S, R>` | 资源端口组件 | 11 `registerMachines` | [Machine Resource Ports](../machine/machine-resource.md) |
| `PipeDistributionStrategy` | 管道分发策略 | 12 `registerMachineFollowUps` | [PipeStrategy](../pipe/pipe-strategy.md) |
| `OreVeinShape` / `OreVeinMode` / 两个 Policy | 矿脉几何、生成通道、暴露/冲突规则 | 13 `registerOreFoundation` | [Ore Strategies](../ore/ore-strategies.md) |
| `OreVeinDisplay.Strategy` | 矿脉 JEI 预览 | 14 `registerOreDisplays` | [Ore Display](../ore/ore-display.md) |

横向主题页（不属于某个注册钩子）：

| 主题 | 内容 | 专题页 |
|------|------|--------|
| 机器 UI | `MachineUiContribution` 面板贡献、配方页构建块、XEI 查看器插座 | [Machine UI](../machine/machine-ui.md) |
| 悬浮面板 | `TopoTooltipUiProvider` / `ItemTooltipUis` 按物品注册 | [Tooltip Panels](tooltip-panels.md) |
| 连接纹理 | CTM codec 注册、`ConnectedTextureHost` 方块族 | [Connected Textures](connected-textures.md) |
| UI 仪表 | `instrument(tag, root)` 钩子接管库 UI 元素树 | [UI Instrumentation](ui-instrumentation.md) |
| 工具类 | `MaterialHelper` / `ResourceFilterHelper` / `TagHelper` / `TopoCompactNumber` | [Helpers](helpers.md) |

## 起点建议

第一次集成按这个顺序读：

1. [Plugin Lifecycle](lifecycle.md) —— 知道什么钩子里能注册什么
2. [MachineComponent](../machine/machine-component.md) —— 最核心的扩展点
3. [RecipeCapability](../recipe/recipe-capability.md) + [RecipeType](../recipe/recipe-type.md) —— 配方回路
4. 其余按需
