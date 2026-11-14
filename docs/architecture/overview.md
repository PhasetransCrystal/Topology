# 项目总览

面向框架开发者。Topology 是 NeoForge **基础库 Mod**，不是内容 Mod：下游模组通过 `TopoPlugin` 声明材料、装备、机器、配方、矿脉和管道，框架负责注册调度、表冻结、内容铸造与运行时接线。仓库中的 `src/demo` 是独立开发内容 Mod TopoDemo，用来验证公开入口，不进入框架发布包。

它解决的是下游科技模组反复实现注册调度、内容冻结、延迟绑定、机器/配方/矿脉运行时与跨模组兼容的问题：这些能力收敛为统一内容管线与领域契约，插件只声明内容与策略，官方内容与第三方内容走同一条路径。

## 分层

| 层 | 内容 | 入口 |
|---|---|---|
| 内容声明 | 插件与各领域钩子 | `TopoPlugin`、各 `*DomainRegistration` |
| 注册内核 | 插件表、领域模块、步骤依赖、冻结 | `net.ptcrys.topo.api.api.plugin` |
| 领域实现 | 材料、装备、机器、配方、矿脉、管道 | `net.ptcrys.topo.api.*` |
| 集成 | RegistryLib、LDLib2、JEI、Jade、AE2 | `net.ptcrys.topo.integration` |
| 开发内容 | TopoDemo | `src/demo`，文档在 `demo-docs/` |

## 设计约束

- 框架封装复杂逻辑，调用方只依赖完成自身职责所需的契约。
- 扩展能力通过接口继承表达；公共方法不携带只供少数分支使用的参数。
- 输入组装处选择实现，执行流程通过多态与能力组合减少分支。
- 内容声明保留 Entry、ID 或延迟解析的 Supplier；游戏对象绑定后才调用 `.get()`。
- 表在对应钩子结束后冻结；`build()` 只发布句柄，不是冻结点。

## 阅读路径

- 下游接入：[使用 Topology 注册内容](../../docs-content/intro.md) → [快速开始](../../docs-content/getting-started.md) → [插件生命周期](../../docs-content/foundation/lifecycle.md)。
- 改注册内核：[注册内核](../kernel/README.md) → [模块地图](module-map.md) → [注册管线时序](../kernel/registration-timeline.md)。
- 改领域实现：[模块地图](module-map.md) 对应行 → `docs-content` 公开契约 → 源码。
- 改开发内容：[TopoDemo 文档](../../demo-docs/README.md)。
