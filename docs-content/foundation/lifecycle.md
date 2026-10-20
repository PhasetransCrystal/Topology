---
sidebar_position: 1
---

# Plugin Lifecycle

`TopoPlugin` 的钩子由 `TopoPluginEngine` 按领域模块声明的依赖驱动。继承任何组件前，先确认**你的类在哪个钩子里注册**——这决定它能访问什么、以及它写入的注册表何时冻结。

## 钩子与冻结责任

| 索引 | 钩子                                     | 冻结于此钩子之后                                                                         | 此时可访问                                                     |
|----|------------------------------------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| 1  | `registerRecipeFoundation(recipe)`       | `RecipeCapabilities`                                                                     | 机器资源类型、配方能力                                         |
| 2  | `registerRecipeTypes(recipe)`            | `TopoRecipeTypes`                                                                        | 已冻结的配方能力                                               |
| 3  | `registerMaterialFoundation(material)`   | 材质四表（data/form/post-processor）                                                     | 尚未有材质实例                                                 |
| 4  | `registerMaterials(material)`            | `MaterialRegistry`                                                                       | 已冻结的表单、数据类型、后处理器                               |
| 5  | `registerMaterialCreativeTabs(material)` | —                                                                                        | 已冻结的材质                                                   |
| 6  | （引擎内部）                             | —                                                                                        | 为每个材质铸造表单物品；先跑全部 `validate` 再跑全部 `process` |
| 7  | `registerMaterialFollowUps(material)`    | —                                                                                        | 全部表单物品已排队；可用稳定 ID，不能读取游戏实例                 |
| 8  | `registerEquipmentKinds(equipment)`      | `EquipmentRegistry`                                                                      | 全部材质（含数据）；之后引擎按 (材料 × 种类) 铸造物品          |
| 9  | `registerMachineFoundation(machine)`     | 机器六表（resource type / UI icon / render / part role / property display / attachment） | 尚未有机器定义                                                 |
| 10 | `registerMachines(machine)`              | `Machines`                                                                               | 已冻结的机器基础表；之后引擎铸造方块/BE 并安装预览计划         |
| 11 | `registerMachineFollowUps(machine)`      | `PipeDistributionStrategies`、`Pipes`                                                    | 全部机器定义（方块/BE 已铸造）                                 |
| 12 | `registerOreFoundation(ore)`             | 矿石四表（shape / mode / air exposure / conflict）                                       | 尚未有矿脉                                                     |
| 13 | `registerOreDisplays(ore)`               | `OreVeinDisplays`                                                                        | 已冻结的模式表；每种模式必须有对应显示                         |
| 14 | `registerOreVeins(ore)`                  | `OreVeins`                                                                               | 已冻结的矿石基础表                                             |
| 15 | `registerOreDatagen(ore)`                | —                                                                                        | 全部矿脉                                                       |
| 16 | `registerRecipes(recipe)`                | `ProductionLines`                                                                        | 全部领域定义及已排队的 RegistryLib Entry；游戏对象仍未绑定      |
| 17 | `registerLang(lang)`                     | `LangRegistry`                                                                           | 全部                                                           |

表中索引用于查找，不是跨领域的依赖声明。矿石基础只依赖材质内容生成，机器与矿石可以独立编排；
全部 CONTENT 步骤完成后才进入具体配方和语言阶段。不要依赖无依赖边步骤之间的偶然次序。

条目显示名由生成它的 RegistryLib Builder 统一持有，通过 `lang(Map)` 提交各语言数据；
LANGUAGE 阶段只导出独立的 Topo `LangKey`。Topo 生成条目名称与 `LangKey` 的冲突在声明时拒绝，
与先后顺序及是否运行 datagen 无关。直接绕过 Topo 使用 RegistryLib 的链仍由 RegistryLib 校验。

## 加载与事件顺序

默认依赖模式下，只有 Topo 宿主接线 `TopoPlugins.attachHost(modEventBus)`：

| 时机 | 执行内容 | 边界 |
|------|----------|------|
| 所有 Mod 构造期 | `TopoPlugins.register(plugin)` | 只登记插件；不要手工提前启动引擎 |
| 首个 `RegisterEvent`，`HIGHEST` | 锁定插件表并执行上表钩子 1–17 | 完成所有游戏内容的 RegistryLib 排队 |
| `RegisterEvent`，RegistryLib `LOW` | 将排队条目注册到游戏注册表 | 此前只能传递 Entry / Supplier，不能提前 `.get()` |
| `FMLCommonSetupEvent.enqueueWork` | 宿主绑定管道面板与装备行为，再调用全部插件的 `registerRuntime(runtime)` | 主线程，游戏条目可用；不同 Mod 任务间无先后承诺 |
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

对于身份和声明内容相同的插件集合，改变 `register` 的调用顺序不会改变引擎的钩子顺序、
派生结果或冻结表的条目顺序。引擎只获取一次锁定快照、编译一次计划；领域模块的组合清单
顺序也不决定计划中的执行顺序。钩子内部仍须使用确定的输入与遍历顺序，不应在构造期抢先写入领域注册表。

```java
TopoPlugins.register(new MyModPlugin());
```

插件继承 `TopoPlugin` 并在构造器中 `super(registry)`。基类缓存领域 API，
`modId()` 从 RegistryCore 推导，各领域 API 使用同一归属；无需手工维护 getter。
材质没有所属 Plugin 时直接报错，不能退回任意其他 Plugin 的 RegistryCore。
在 `registerMachineFollowUps(machine)` 中使用 `machine.pipe("copper_pipe")`，管道及共享资源生成
使用该 Plugin 的 RegistryCore。旧 `Pipes.register("...")` 保留 `topo` 命名空间和宿主归属，
新插件应迁移到领域入口。

## 阶段状态与失败

引擎依次执行 CONTENT → DERIVED → LANGUAGE → RUNTIME → READY。
CONTENT 内按领域依赖排序；DERIVED 执行具体配方，LANGUAGE 统一导出翻译。
已完成的阶段可重复调用，不重复生成内容；越序和重入会报错。
阶段执行异常后保留原始原因并终止后续启动，不能重试已经部分修改的注册表。
插件钩子的异常包含插件类名、Mod ID 和钩子名称；修正后必须重启进程。

插件收集器锁定后复用同一个不可变快照，生命周期状态、快照工厂和执行计划归属于同一个引擎实例；
生产环境仍只接线一个宿主。

冻结点后写入会抛异常。例如 `MaterialForm` 必须在 `registerMaterialFoundation` 声明，
`registerMaterials` 只能引用已经声明的形态。静态字段不会因锁定插件表而自动初始化，
每个声明类都应在所属钩子内显式触发 `init()`。

## 接入与迁移

依赖方只在构造期 `TopoPlugins.register(new MyPlugin())`。原有命名钩子继续直接覆写，
无需 `configure` 或手工登记回调。领域对象的字段和 getter 改由基类持有。
宿主只调用一次 `TopoPlugins.attachHost(modEventBus)`；分阶段启动方法属于内部实现，不能提前启动内容管线。

`registerRecipes` 位于全部内容领域之后；被材质后处理器引用的配方类型和生产线应在
`registerRecipeTypes` 声明，其余具体配方在 `registerRecipes` 组装。
此时 Entry 已排队，但 RegistryLib 尚未绑定游戏对象，不能提前 `.get()`。

provider 和额外物品行为放在 `registerRuntime(runtime)`，使用 `runtime.tooltip` /
`runtime.itemBehavior`。装备策略的 `machineBehavior()` 由框架在该钩子前自动绑定。
不再单独接线 `PipeSpecTooltips`，也不要在领域代码中冻结共享运行时表。
客户端专用绑定可使用 client setup；所有共享表贡献最迟在 common / sided setup 完成。

## 包分组与扩展边界

| 包 | 职责 |
|---|---|
| `api.api.plugin` | `TopoPlugin`、`TopoPlugins`、领域 API 工厂与贡献标记 |
| `plugin.hook` | 按领域组织的命名钩子，基类提供全部内置接口 |
| `plugin.internal` | 不可变快照、依赖计划、阶段状态及 NeoForge 事件适配 |
| `plugin.internal.module` | 各领域的声明调度、生成、校验与冻结责任 |

`internal` 中为跨子包组合而公开的类型仍是框架实现，不是下游接入契约。
内部包及宿主接线、底层 `begin` / `freeze` 方法使用 `@ApiStatus.Internal` 标记；引擎和快照本身为包内可见。
插件基类、命名钩子及领域策略保留原有继承和覆写能力。
新增框架领域时：

1. 定义领域 API 和 `RegistrationDomain` 工厂；定义继承 `TopoContribution` 的命名钩子接口。
2. 在领域模块中用 `RegistrationPoint` 绑定接口方法与 API；通过 `RegistrationModule.define` 声明步骤依赖及贡献后的冻结动作。
3. 将模块加入 `TopoContentBootstrap` 的组合清单；使用该领域的插件实现对应接口。

不需要修改引擎或给 `TopoPlugin` 增加调度逻辑；若希望所有插件自动获得新领域的默认钩子，
可让基类继承该接口。模块只声明真实依赖，不依赖组合清单的先后顺序。
计划在执行前拒绝缺失依赖、环、指向更晚阶段的依赖、重复步骤和未安装的贡献接口；
同一依赖层按步骤 ID 确定顺序。新增 CONTENT 领域也会自动处于具体配方与语言导出之前。
贡献接口可以继承、组合和覆写已安装的钩子；新增钩子仍需安装对应领域模块。

当前仅保证 Topo 单宿主的静态加载；不支持热重载，也不承诺可移除 Topo Mod 的完全嵌入。
