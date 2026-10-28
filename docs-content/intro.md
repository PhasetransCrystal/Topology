---
sidebar_position: 1
slug: /
---

# 使用 Topology 注册内容

Topology 是面向 NeoForge 的内容开发框架，提供材料、装备、机器、管道、配方和矿脉的注册与运行机制。
你的模组声明类型、策略和具体内容，Topo 宿主负责在对应阶段调用插件、生成游戏条目并冻结注册表。
Topo 本体不提供铜、锡、粉碎机等玩法内容；仓库中的 TopoDemo 是独立开发模组，不随框架发布。

## 按目标开始

| 你要做什么 | 从哪里开始 | 完成后得到什么 |
|---|---|---|
| 在自己的模组里注册第一项内容 | [快速开始](getting-started.md) | 一个接入宿主的 TopoPlugin，以及从材料数据生成的物品 |
| 组织材料、机器、配方等完整内容链 | [内容注册实作](foundation/content-plugin.md) | 按钩子分工的声明、策略和数据生成 |
| 使用原版或其他模组的物品、方块、配方与能力 | [兼容其他模组](compatibility/other-mods.md) | 引用现有条目、覆盖材料形态、接入配方和资源传输 |
| 复用或扩展另一个 TopoPlugin 的类型与内容 | [兼容其他 TopoPlugin](compatibility/topo-plugins.md) | 共享数据类型、跨插件形态匹配与配方贡献 |
| 查某个注册窗口或可用时点 | [插件生命周期](foundation/lifecycle.md) | 钩子、冻结点及游戏对象绑定顺序 |

## 内容通过什么入口注册

```java
TopoPlugins.register(new MyContentPlugin(myRegistry));
```

在模组构造期登记插件。插件继承 `TopoPlugin`，覆写需要的钩子，使用钩子参数提供的领域入口：

| 内容 | 常用入口 | 注册位置 |
|---|---|---|
| 材料数据类型、形态、后处理器 | `material.dataType` / `.form` / `.postProcessor` | `registerMaterialFoundation` |
| 材料及其属性 | `material.material` | `registerMaterials` |
| 装备种类 | `equipment.kind` | `registerEquipmentKinds` |
| 机器 | `machine.machine` | `registerMachines` |
| 管道 | `machine.pipe` | `registerMachineFollowUps` |
| 配方能力与类型 | `recipe.capability` / `.recipeType` | `registerRecipeFoundation` / `registerRecipeTypes` |
| 具体配方 | 已声明的 `recipeType.recipe` | `registerRecipes` |
| 矿脉 | `ore.vein` | `registerOreVeins` |
| 独立翻译、运行时绑定 | `lang.key` / `runtime.tooltip` / `.itemBehavior` | `registerLang` / `registerRuntime` |

领域入口接收裸路径，例如 `material.material("tin")` 自动归属当前插件的 `mymod:tin`。
普通物品、方块和创造栏可在合适的插件钩子中使用同一个 RegistryCore 注册。
完整可运行的声明方式见[快速开始](getting-started.md)，各领域的策略写法见下表。

## 按领域查用法

| 领域 | 注册与扩展 |
|---|---|
| 材料 | [材料](material.md)、[形态策略](material/material-form.md)、[数据类型](material/material-data.md)、[后处理器](material/material-post-processor.md) |
| 装备 | [装备种类](equipment.md)、[机器交互行为](equipment/equipment-behavior.md)、[悬浮信息](equipment/equipment-tooltips.md) |
| 机器 | [机器注册](machine.md)、[组件](machine/machine-component.md)、[资源端口](machine/machine-resource.md)、[渲染](machine/machine-render.md)、[UI](machine/machine-ui.md) |
| 多方块 | [蓝图](machine/multiblock.md)、[部件角色与状态显示](machine/multiblock-ability.md) |
| 管道 | [管道注册](pipe.md)、[分发策略](pipe/pipe-strategy.md)、[网络勘测](pipe/pipe-survey.md) |
| 配方 | [具体配方](recipe.md)、[配方类型](recipe/recipe-type.md)、[输入输出能力](recipe/recipe-capability.md) |
| 矿脉 | [矿脉注册](ore.md)、[生成策略](ore/ore-strategies.md)、[预览](ore/ore-display.md) |
| 文本与工具 | [本地化](localization.md)、[条目显示名](localization/display-names.md)、[工具类](foundation/helpers.md)、[测试与验证](foundation/testing.md) |

跨 TopoPlugin 的依赖通过所属模组之间的依赖关系隐式确认，插件无需再声明一份依赖图。
扩展其他模组时先确定依赖和内容归属，再按生命周期使用对方公开的句柄；详见两篇兼容指南。
