---
sidebar_position: 3
---

# Embedding（无 topo 宿主）

默认集成依赖库的宿主 Mod `@Mod("topo")`——它注册 API 插件并接线全部框架运行时。
当前仅保证保留 `topo` 宿主的依赖库模式。下列清单说明宿主承担的职责；
完全内嵌还需要迁移共享条目及自动事件订阅，不能通过复制接线完成。

## 完整接线清单

以下列出宿主 `Topology` 构造器及其内部模块承担的接线：

| 调用                                                   | 用途                                        |
|--------------------------------------------------------|---------------------------------------------|
| `TopoPlugins.register(OfficialTopoAPIPlugin.INSTANCE)` | API 插件（附件类型 + API 语言键）           |
| `TopoPlugins.register(你的插件)`                       | 你自己的内容插件                            |
| `TopoPlugins.attachHost(modEventBus)`              | 内容管线、资源能力、运行时钩子及统一冻结点 |
| `CtmClientInit.register(modEventBus)`                  | CTM blockstate-model codec 客户端注册       |
| `MachineDataNetworking.register(modEventBus)`          | 机器数据同步网络通道                        |
| `MachineDataSyncBatcher.register(modEventBus)`         | 同步批量器                                  |
| `TopoAsyncExecutors.register()`                        | 异步执行器                                  |
| `TopoRecipeSearchEvents.register(modEventBus)`         | 配方搜索索引生命周期                        |
| `MultiblockChangeWatcher.register()`                   | 多方块变更监听                              |
| `PipeNetworkEngine.register()`                         | 管道网络引擎                                |
| `PipeSurveyNetworking.register(modEventBus)`           | 管道勘测网络通道                            |
| `PipeSurveyClientRenderer.register()`                  | 勘测客户端渲染                              |
| `TickHeartbeat.register(modEventBus)`                  | tick 心跳                                   |

## 启动管线

宿主只接线一次：

```java
TopoPlugins.attachHost(modEventBus);
```

引擎负责首个 `RegisterEvent` 的 HIGHEST 内容阶段、common setup 的 Plugin 运行时绑定，
以及 load complete 的共享表冻结。不要另外注册 bootstrap 监听器或管道 tooltip 监听器。
所有 Plugin 必须在 Mod 构造期登记完毕。完整顺序见 [Plugin Lifecycle](foundation/lifecycle.md)。

## 共享注册表

当前共享机器 BlockEntity 类型和旧 `Pipes.register` 管道仍归 `Topology.REGISTRY`（`topo` 命名空间）。
`machine.pipe(...)` 和机器方块本体则归各自 Plugin 的 RegistryCore。

**完全移除 topo Mod 仍不是受支持的一步式接入方式。** 仅复制接线清单无法迁移这些共享条目的 namespace，
也无法迁移绑定 `topo` 的自动事件订阅。需要额外适配共享资源和客户端接线；推荐保留默认 topo 宿主。

:::info
保持默认集成（依赖 `topo` mod）时无需读本节——[Getting Started](getting-started.md) 的插件登记即可。
:::
