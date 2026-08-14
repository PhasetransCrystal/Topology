---
sidebar_position: 3
---

# Embedding（无 topo 宿主）

默认集成依赖库的宿主 Mod `@Mod("topo")`——它注册 API 插件并接线全部框架运行时。
如果你的 Mod 想**完全内嵌库**（不出现在依赖列表的 `topo` mod），需要自己复制这份接线清单。

## 完整接线清单

以下每一项都在宿主 `Topology` 构造器里执行（`Topology.java:46-62`），嵌入时逐条复制：

| 调用                                                   | 用途                                        |
|--------------------------------------------------------|---------------------------------------------|
| `TopoPlugins.register(OfficialTopoAPIPlugin.INSTANCE)` | API 插件（附件类型 + API 语言键）           |
| `TopoPlugins.register(你的插件)`                       | 你自己的内容插件                            |
| `Machines.registerResourceCapabilities(modEventBus)`   | 机器资源能力的原版 capability 注册          |
| `PipeSpecTooltips.register(modEventBus)`               | 管道规格悬浮面板（freeze `ItemTooltipUis`） |
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

首个 `RegisterEvent` 上以 `HIGHEST` 优先级注册（保证所有 `@Mod` 构造器已跑完，RegistryLib 的
`LOW` 监听器仍能看到队列条目）：

```java
private static void bootstrap(RegisterEvent event) {
    if (bootstrapped) return;
    bootstrapped = true;
    TopoPluginEngine.prepare();          // recipe → material → equipment
    TopoPluginEngine.bootstrapMachine();
    TopoPluginEngine.bootstrapOre();
    TopoPluginEngine.bootstrapLang();
}
```

:::caution
`prepare()` 锁定插件表——管线启动后 `TopoPlugins.register` 抛异常。所有插件（含 API 插件）必须在
`@Mod` 构造器里注册完。
:::

## 共享注册表

引擎把机器的共享 BlockEntity 类型与管道方块注册到 `Topology.REGISTRY`（`topo` 命名空间）——
嵌入时你的 Mod 需要提供该 RegistryCore，或改用默认 topo 宿主。机器方块本体注册在你的插件
RegistryCore 上，不受影响。

:::info
保持默认集成（依赖 `topo` mod）时无需读本节——[Getting Started](getting-started.md) 的三步就够。
:::
