---
sidebar_position: 1
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

## 快速开始

```gradle
repositories { maven { url = uri("https://maven.ptcrys.net/releases") } }
dependencies { implementation "net.ptcrys:Topology:${topology_version}" }
```

```java
TopoPlugins.register(new MyModPlugin()); // 实现 TopoPlugin，注册你的全部内容
```

详见 [Getting Started](getting-started)。

## 文档导航

| 分组                  | 文档                                                                                                                                        | 何时阅读                                           |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------|
|                       | **Getting Started**                                                                                                                         | [Intro](intro), [Getting Started](getting-started) | 首次集成必读 |
| **Materials & World** | [Material](materials-world/material), [Ore](materials-world/ore), [Equipment](materials-world/equipment)                                    | 添加新材料、矿脉、工具                             |
| **Automation**        | [Machine](automation/machine), [Multi-block Patterns](automation/multiblock-patterns), [Recipe](automation/recipe), [Pipe](automation/pipe) | 添加机器、配方、管道                               |
| **Foundation**        | [Localization](foundation/localization), [Tick System](foundation/tick-system)                                                              | 需要时查阅                                         |

## 设计原则

- **不添加玩法** — 不在创造模式物品栏、JEI 中注册内容
- **接口驱动** — 核心 API 以接口暴露，下游按需实现
- **冻结注册表** — 各阶段之间有明确冻结点
- **版本稳定** — 基础 API 向后兼容

## 相关链接

- [Ptcrys](https://ptcrys.net) · [Maven](https://maven.ptcrys.net) · [GitHub](https://github.com/PhasetransCrystal/Topology) · [NeoForge](https://docs.neoforged.net/)
