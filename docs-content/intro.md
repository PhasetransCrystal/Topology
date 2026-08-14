---
sidebar_position: 1
slug: /
---

# Topology

面向 [NeoForge](https://neoforged.net/) 的 **Minecraft 基础库 Mod**。为下游模组提供机器、材料、管道、配方、矿石生成等基础设施，
**自身不添加任何玩法内容**。

## 为什么选择 Topology

每个 Mod 都要重写机器框架、多方块匹配、物流管道、材料系统……Topology 把这些收拢成一套 API，你声明 **做什么**，框架负责
**怎么做**。

| 场景         | 不用 Topology                       | 用 Topology                                  |
|--------------|-------------------------------------|----------------------------------------------|
| 声明一个材料 | 手写物品/方块/纹理/数据生成         | `material("copper").form(ingot).build()`     |
| 加一台机器   | 自建 BlockEntity / 配方 / UI / 同步 | `Machines.begin(...).component(...).build()` |
| 加多方块     | 手写结构匹配引擎                    | `Blueprint.builder().layer(...).build()`     |
| 加管道       | 自写网络图 + 传输逻辑               | `Pipes.register(...).build()`                |

## 纯库设计

Topology **只分发框架 API，不分发任何玩法内容**：

```
net.ptcrys.topo.api.* → 库本体：全部继承扩展点（MachineComponent、MaterialForm、
                        RecipeCapability、EquipmentStrategy、PipeDistributionStrategy……）
```

- 所有功能通过**继承框架基类 + 实现自己的策略**获得
- 文档示例全部以「你自己的 Mod 命名空间（`mymod:*`）」编写，可直接照抄
- 学习模式时参考 [Extension Points](foundation/extension-points.md) 里的继承模板

## 快速开始

```gradle
repositories { 
    maven {
        name = "ptcrysReleases"
        url = uri("https://maven.ptcrys.net/releases")
    }
}
dependencies {
    implementation "net.ptcrys:Topology:${topology_version}"
}
```

```java
TopoPlugins.register(new MyModPlugin()); // 实现 TopoPlugin，注册你的全部内容
```

详见 [Getting Started](getting-started.md)。

## 文档导航

| 分组                | 文档                                                                                                                                                                                                                                                                                                                                 | 何时阅读             |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| **Getting Started** | [Intro](intro.md), [Getting Started](getting-started.md), [Embedding](embedding.md)                                                                                                                                                                                                                                                  | 首次集成必读         |
| **Recipe**          | [Recipe](recipe.md), [RecipeCapability](recipe/recipe-capability.md), [RecipeType](recipe/recipe-type.md)                                                                                                                                                                                                                            | 配方构建、能力、类型 |
| **Material**        | [Material](material.md), [MaterialForm](material/material-form.md), [MaterialDataType](material/material-data.md), [MaterialPostProcessor](material/material-post-processor.md)                                                                                                                                                      | 新材料与形态         |
| **Equipment**       | [Equipment](equipment.md), [Behavior](equipment/equipment-behavior.md), [Tooltips](equipment/equipment-tooltips.md)                                                                                                                                                                                                                  | 工具/护甲种类        |
| **Machine**         | [Machine](machine.md), [MachineComponent](machine/machine-component.md), [MachineRender](machine/machine-render.md), [MachineResource](machine/machine-resource.md), [Multiblock](machine/multiblock.md), [Multiblock Abilities](machine/multiblock-ability.md), [Machine UI](machine/machine-ui.md)                                 | 添加机器与多方块     |
| **Pipe**            | [Pipe](pipe.md), [PipeStrategy](pipe/pipe-strategy.md), [PipeSurveyor](pipe/pipe-survey.md)                                                                                                                                                                                                                                          | 管道与分发策略       |
| **Ore**             | [Ore](ore.md), [Ore Strategies](ore/ore-strategies.md), [Ore Display](ore/ore-display.md)                                                                                                                                                                                                                                            | 矿脉与生成           |
| **Lang**            | [Localization](localization.md), [Keys & Families](localization/keys-and-families.md), [Display Names](localization/display-names.md)                                                                                                                                                                                                | 翻译键机制           |
| **Foundation**      | [Lifecycle](foundation/lifecycle.md), [Extension Points](foundation/extension-points.md), [Tick System](foundation/tick-system.md), [Tooltip Panels](foundation/tooltip-panels.md), [Connected Textures](foundation/connected-textures.md), [UI Instrumentation](foundation/ui-instrumentation.md), [Helpers](foundation/helpers.md) | 需要时查阅           |

## 设计原则

- **不添加玩法** — 不在创造模式物品栏、JEI 中注册内容
- **接口驱动** — 核心 API 以接口暴露，下游按需实现
- **冻结注册表** — 各阶段之间有明确冻结点
- **版本稳定** — 基础 API 向后兼容

## 相关链接

- [Ptcrys](https://ptcrys.net)
- [Maven](https://maven.ptcrys.net)
- [GitHub](https://github.com/PhasetransCrystal/Topology)
- [NeoForge](https://docs.neoforged.net/)
