# 内部包与扩展边界

面向框架开发者。记录插件相关包的分工与下游扩展边界；这些类型不是下游接入契约。

| 包 | 职责 |
|---|---|
| `api.api.plugin` | `TopoPlugin`、`TopoPlugins`、领域 API 工厂与贡献标记 |
| `plugin.hook` | 按领域组织的命名钩子，基类提供全部内置接口 |
| `plugin.internal` | 不可变快照、依赖计划、阶段状态及 NeoForge 事件适配 |
| `plugin.internal.module` | 各领域的声明调度、生成、校验与冻结责任 |
| `api.api.builtin` | 各领域 `*DomainRegistration` 与官方 API 插件 |
| `api.api.registration` | 外部物品/方块/流体 target |
| `api.api.infrastructure` | 可冻结策略表与外部 UI 探针接口 |
| `api.api.async` | 框架异步执行器 |
| `api.api.annotation` | 内部语法糖与注解 |
| `api.api.util` | 仓储访问等通用接口 |

`internal` 中为跨子包组合而公开的类型仍是框架实现，不是下游接入契约。内部包及宿主接线、底层 `begin` / `freeze` 方法使用 `@ApiStatus.Internal` 标记；引擎和快照本身为包内可见。

插件基类、命名钩子及领域策略保留原有继承和覆写能力。当前仅保证 Topo 单宿主的静态加载；不支持热重载，也不承诺可移除 Topo Mod 的完全嵌入。

## 相关

- [注册内核](README.md)
- [宿主接线](host-wiring.md)
- [新增注册领域](registration-domain-extension.md)
