---
sidebar_position: 1
---

# Plugin Lifecycle

`TopoPlugin` 的钩子由 `TopoPluginEngine` 按固定顺序驱动。继承任何组件前，先确认**你的类在哪个钩子里注册**——这决定它能访问什么、以及它写入的注册表何时冻结。

## 钩子全表

| #  | 钩子                                     | 冻结于此钩子之后                                                                         | 此时可访问                                                     |
|----|------------------------------------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| 1  | `registerRecipeFoundation(recipe)`       | `RecipeCapabilities`                                                                     | 机器资源类型、配方能力                                         |
| 2  | `registerRecipeTypes(recipe)`            | `TopoRecipeTypes`                                                                        | 已冻结的配方能力                                               |
| 3  | `registerMaterialFoundation(material)`   | 材质四表（data/form/post-processor）                                                     | 尚未有材质实例                                                 |
| 4  | `registerMaterials(material)`            | `MaterialRegistry`                                                                       | 已冻结的表单、数据类型、后处理器                               |
| 5  | `registerMaterialCreativeTabs(material)` | —                                                                                        | 已冻结的材质                                                   |
| 6  | （引擎内部）                             | —                                                                                        | 为每个材质铸造表单物品；先跑全部 `validate` 再跑全部 `process` |
| 7  | `registerMaterialFollowUps(material)`    | —                                                                                        | 全部表单物品已存在                                             |
| 8  | `registerRecipes(recipe)`                | `ProductionLines`                                                                        | 全部材质 + 表单物品                                            |
| 9  | `registerEquipmentKinds(equipment)`      | `EquipmentRegistry`                                                                      | 全部材质（含数据）；之后引擎按 (材料 × 种类) 铸造物品          |
| 10 | `registerMachineFoundation(machine)`     | 机器六表（resource type / UI icon / render / part role / property display / attachment） | 尚未有机器定义                                                 |
| 11 | `registerMachines(machine)`              | `Machines`                                                                               | 已冻结的机器基础表；之后引擎铸造方块/BE 并安装预览计划         |
| 12 | `registerMachineFollowUps(machine)`      | `PipeDistributionStrategies`、`Pipes`                                                    | 全部机器定义（方块/BE 已铸造）                                 |
| 13 | `registerOreFoundation(ore)`             | 矿石四表（shape / mode / air exposure / conflict）                                       | 尚未有矿脉                                                     |
| 14 | `registerOreDisplays(ore)`               | `OreVeinDisplays`                                                                        | 已冻结的模式表；每种模式必须有对应显示                         |
| 15 | `registerOreVeins(ore)`                  | `OreVeins`                                                                               | 已冻结的矿石基础表                                             |
| 16 | `registerOreDatagen(ore)`                | —                                                                                        | 全部矿脉                                                       |
| 17 | `registerLang(lang)`                     | `LangRegistry`                                                                           | 全部                                                           |

## 加载与事件顺序

默认依赖模式下，只有 Topo 宿主接线 `TopoPluginEngine.register(modEventBus)`：

| 时机 | 执行内容 | 边界 |
|------|----------|------|
| 所有 Mod 构造期 | `TopoPlugins.register(plugin)` | 只登记插件；不要手工提前启动引擎 |
| 首个 `RegisterEvent`，`HIGHEST` | 锁定插件表并执行上表钩子 1–17 | 完成所有游戏内容的 RegistryLib 排队 |
| `RegisterEvent`，RegistryLib `LOW` | 将排队条目注册到游戏注册表 | 此前只能传递 Entry / Supplier，不能提前 `.get()` |
| `FMLCommonSetupEvent.enqueueWork` | 宿主绑定管道面板；依赖方在自己的事件任务中绑定 provider / behavior | 主线程，游戏条目可用；不同 Mod 任务间无先后承诺 |
| sided setup 的 deferred work | 完成客户端专用绑定 | 同样必须在 load complete 前完成 |
| `RegisterCapabilitiesEvent` | 为共享机器 BE 安装已声明资源类型的能力 | 由宿主接线，资源类型表已经冻结 |
| `FMLLoadCompleteEvent.enqueueWork` | 引擎冻结 `ItemTooltipUis`、`MachineItemBehaviors` | 此后不能再注册 provider / behavior |

NeoForge 的 `RegisterEvent` 按优先级跨 Mod 派发，因此 `HIGHEST` 的内容排队先于
RegistryLib 的 `LOW`。common setup 属于并行生命周期事件，不能靠另一个 Mod 的监听器
优先级保证其 deferred work 先执行；共享运行时表必须在后续生命周期事件中冻结。

上表的能力注册事件位置按项目当前 NeoForge 26.1.2.107 的 `CommonModLoader.load` /
`RegistrationEvents.init` 实现核对：发生在 common / sided setup 之后、IMC 和 load complete 之前。

每个钩子先遍历**全部插件**，再冻结对应表，随后进入下一个钩子。
运行时与 datagen 共用内容管线；datagen 不依赖 common setup 或 load complete 才完成资源声明。
语言键通过声明它的 `LangDomainRegistration` 所属 RegistryCore 导出。
仅运行 `--mod mymod` 会生成 mymod 拥有的键；Topo 自身 API 文案由 Topo 的资源包提供。

## 插件顺序

插件只需调用 `TopoPlugins.register(plugin)`，无需分配插件 ID 或声明依赖。
引擎在每个阶段遍历全部插件后再冻结该阶段注册表；foundation 钩子因此总是先于对应内容钩子，
不需要 API Plugin 与内容 Plugin 之间建立依赖。引擎按 Mod ID 和插件类名稳定排序，仅用于让独立贡献
和诊断结果可复现；插件不得依赖同一阶段其他插件先执行。模组构造器到达顺序不属于 API 契约。

```java
TopoPlugins.register(new MyModPlugin());
```

插件的领域 API 通过 `XDomainRegistration.of(this)` 创建一次并保存在字段中。
Plugin 的 `modId()`、RegistryCore 的 namespace 和所有领域 API 的归属必须一致。
材质没有所属 Plugin 时直接报错，不能退回任意其他 Plugin 的 RegistryCore。
在 `registerMachineFollowUps(machine)` 中使用 `machine.pipe("copper_pipe")`，管道及共享资源生成
使用该 Plugin 的 RegistryCore。旧 `Pipes.register("...")` 保留 `topo` 命名空间和宿主归属，
新插件应迁移到领域入口。

## 阶段状态与失败

引擎严格按 recipe → material → equipment → machine → ore → lang → runtime → ready 执行。
已完成的阶段可重复调用，不重复生成内容；越序和重入会报错。
阶段执行异常后保留原始原因并终止后续启动，不能重试已经部分修改的注册表。
插件钩子的异常包含插件类名、Mod ID 和钩子名称；修正后必须重启进程。

冻结点后写入会抛异常。例如 `MaterialForm` 必须在 `registerMaterialFoundation` 声明，
`registerMaterials` 只能引用已经声明的形态。静态字段不会因锁定插件表而自动初始化，
每个声明类都应在所属钩子内显式触发 `init()`。

## 接入与迁移

依赖方只在构造期注册插件，移除重复的引擎事件接线。
旧的 `prepare()`、`bootstrapMachine()`、`bootstrapOre()`、`bootstrapLang()` 保留，
供兼容代码按上述顺序调用；推荐宿主统一使用 `register(modEventBus)`。

tooltip 和物品行为继续在依赖方自己的 `FMLCommonSetupEvent.enqueueWork` 中注册，
移除各内容模块的 `freeze()`。客户端专用绑定也可使用 client setup 的 deferred work。
不再单独调用 `PipeSpecTooltips.register`；宿主引擎负责注册管道面板。
所有运行时注册最迟在 common / sided setup 完成，禁止延迟到 load complete 才开始贡献。
跨 Mod 的运行时绑定应独立完成；存在依赖的任务应由参与方明确协调，不能靠 Plugin 排序或监听器到达顺序推断。

## 引擎职责与扩展边界

- `TopoPlugins`：登记内容插件、校验归属，并在启动时稳定排序；不声明插件间依赖。
- `PluginLifecycle`：阶段成功后才标记完成，拒绝越序、重入和失败后续跑，不处理领域内容。
- `TopoPluginEngine`：把 NeoForge 事件映射到阶段；保留既有显式 bootstrap 入口并统一管理运行时冻结。
- `TopoContentBootstrap`：执行各领域的声明、校验、内容生成和冻结，不监听事件。

新增领域时，领域实现与冻结点放在内容编排层，由引擎接入明确阶段；冻结必须跟在所有插件贡献之后。
只有新的内容声明语义才需要考虑增加 Plugin 钩子；排序、宿主事件和冻结职责不再加入 Plugin 接口。
当前仍采用固定领域管线，不开放第三方任意插入冻结步骤：注册表之间存在真实依赖，开放任意步骤会削弱加载契约。
若将来需要第三方领域模块，应先定义它依赖的阶段、允许写入的表和冻结责任，再设计模块扩展协议。
现有共享机器 BE 仍由 Topo 宿主持有，当前拆分不等于已经支持多宿主、热重载或移除 Topo Mod 的完全嵌入。
