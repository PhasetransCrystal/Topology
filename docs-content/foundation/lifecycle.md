---
sidebar_position: 1
---

# Plugin Lifecycle

`TopoPlugin` 的钩子由 `TopoPluginEngine` 按领域模块声明的依赖驱动。继承任何组件前，先确认**你的类在哪个钩子里注册**——这决定它能访问什么、以及它写入的注册表何时冻结。

首次接入先读[快速开始](../getting-started.md)与[内容注册实作](content-plugin.md)。
跨 TopoPlugin 的依赖由所属模组依赖隐式确认，但被引用内容未必初始化完成；
使用时点与常见兼容方式见[跨插件兼容](../compatibility/topo-plugins.md)。本页保留完整调度与冻结参考。

## 关键边界

面向编写 TopoPlugin 的模组开发者。框架先在模组构造期收集插件，再按领域钩子执行声明并关闭写入；内容排队不等于游戏对象已经绑定。

- 先在各 foundation 钩子声明类型，再在对应内容钩子引用；全部插件的同一钩子完成后才进入依赖它的步骤。
- 材料构建后可在 `registerMaterialContributions` 补充数据，所有贡献完成后才统一匹配形态并冻结；`build()` 不等于冻结。
- 注册声明中保留 Entry、ID 或延迟解析的 Supplier。仅在对象绑定后适合读取的回调中调用 `.get()`。
- `registerRuntime` 在 common setup 的 deferred work 中调用；共享运行时表在 load complete 冻结。
- 下列索引便于定位钩子，不代表所有无依赖关系的步骤之间均有顺序承诺。

### 策略回调的调用粒度

以下列出实现策略时会影响调用方的回调时点。注册策略不意味着其全部方法会在启动时执行。

| 场景 | 回调与实际顺序 |
|------|----------------|
| 材料匹配 | 形态可用 `appliesToData` 声明匹配所需的数据类型，再由 `MaterialForm.appliesTo` 作最终判断。只有返回 true 的形态入选，配置覆盖不会跳过规则。全部结果计算完成后统一发布，求值期间形态集合均为空；发布完成后才统一构建 `MaterialFormPlan`，plan 构造期可读取完整形态表。 |
| 材料校验 | **逐材料**先校验全局 data，再逐个匹配形态校验其作用域 data（`MaterialDataType.validate`）并调用 `MaterialFormPlan.validate`；激活的后处理器在同一冻结阶段调用一次 `MaterialPostProcessor.validate`。 |
| 材料生成 | 先为全部材料／形态调用 `MaterialFormPlan.register`；随后只对已激活材料／后处理器组合调用一次 `MaterialPostProcessor.process`，不再重复 validate。 |
| 装备生成 | **逐材料／装备种类组合**调用 `EquipmentStrategy.appliesTo`，仅当其返回 true 时立即调用该组合的 `register`。装备表此时已冻结。 |
| 机器生成 | 对有渲染声明的每台机器，依次调用 `MachineBlockRenderStrategy.validate`、`applyBlockModel` 配置 Builder；随后排队方块及共享 BE 类型。策略内提交的模型 datagen lambda 仍在 datagen 时执行。 |
| 配方预览计划 | 对每个配方类型调用安装器的 planner（当前为 `RecipePreviewPlans.canonical`）；计算槽位布局时调用 `SlottedRecipeCapability.countPreviewSlots`。此时配方类型、机器表已冻结，具体配方尚未注册。 |
| 管道生成 | 逐管道调用 `PipeBlockTemplate.configureBlock`、`configureItem` 并排队；全部管道排队后，通过 `sharedGroup` 按所属 RegistryCore 和共享组去重，再调用 `contributeShared`。 |
| 矿脉声明 | `OreVeins.Builder.build` 内调用 `OreVeinMode.Strategy.validate`；这是声明 hook 内的校验，发生在矿脉表冻结之前。 |
| 条目绑定 | RegistryLib 创建游戏对象时执行工厂及 `properties` 链；机器的 `blockFactory.apply` 和管道的 `PipeBlockTemplate.styleBlockProperties` 位于这里。`configureBlock`／`configureItem` 早在内容排队时已执行。 |
| common deferred | 宿主先对已生成的装备条目调用 `EquipmentStrategy.machineBehavior` 并挂接非空行为，再执行全部插件的 `registerRuntime`。返回的行为对象此时仅被安装，其游戏交互方法不会因此执行。 |
| 能力注册事件 | `MachineResourceTypes.registerBlockEntityCapabilities` 安装能力 provider；provider 中读取机器侧资源 handler 的 lambda 要到能力查询时才执行。 |

### 不属于启动注册窗口的策略回调

注册策略对象与执行其所有方法是两个时点。以下回调由使用场景触发，不会重新开放已经冻结的表。

| 模块 | 回调 | 触发点 |
|------|------|--------|
| 机器 | `MachineBlockEntityFactory.create`、`ResourceDataFieldFactory.create` | 世界创建机器 BE、机器实例配置资源数据字段时；注册共享 BE 类型不会提前创建每个机器实例。 |
| 管道 | `PipeDistributionStrategy.initialPortConfig`、`budget`、`distribute`、`contributeConfigUi`、`configCodec` | 分别在端口初始化／切换／恢复、传输预算计算、批次分配、配置界面构建、配置编解码时使用。分配策略表冻结只关闭声明。 |
| 矿脉 | `OreVeinMode.Strategy.collectGrid`、`OreVeinShape.Strategy.contains`、`OreAirExposurePolicy.Strategy.discardExposed`、`OreConflictPolicy.Strategy.canReplace` | 世界矿脉规划与放置时。 |
| 矿脉展示 | `OreVeinDisplay.Strategy.buildPreview`、`OreAirExposurePolicy.Strategy.describe` | 客户端矿脉预览／说明构建时。 |
| 矿脉 feature 桥 | `OreVeinMode.Strategy.collectFeature` | 框架 `VanillaOreFeatureBridge.featureVeins` 在 configured feature、placed feature、biome modifier 的 datagen bootstrap 内调用；`ore.featureDatagen()` 仅安装这些 bootstrap 回调。 |
| 配方 UI | `SlottedRecipeCapability.createPreviewInputSlotWidget`、`createPreviewOutputSlotWidget`、`createLiveSlotWidget`、`previewInputSlotAssignment` | 构建预览／实况 UI 及分配预览内容时；与启动时的 `countPreviewSlots` 区分。 |
| 配方执行 | `RecipeCapability` 的匹配、传输及 `DirectTickIoBinding` 回调 | 配方搜索、开始执行和工作 tick 时；注册能力本身不执行配方。 |
| 资源生成 | 向 RegistryLib 提交的模型、标签、配方等 provider 回调 | datagen 对应 provider 运行时；声明 provider 的 hook 可以已经结束。 |

## 钩子与冻结责任

| 索引 | 钩子                                     | 冻结于此钩子之后                                                                         | 此时可访问                                                     |
|----|------------------------------------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| 1  | `registerRecipeFoundation(recipe)`       | `RecipeCapabilities`                                                                     | 机器资源类型、配方能力                                         |
| 2  | `registerRecipeTypes(recipe)`            | `TopoRecipeTypes`                                                                        | 已冻结的配方能力                                               |
| 3  | `registerMaterialFoundation(material)`   | 材质四表（data / form-data / form / post-processor）                                      | 尚未有材质实例                                                 |
| 4  | `registerMaterials(material)`            | —                                                                                        | 已冻结的形态、数据类型、后处理器                               |
| 4b | `registerMaterialContributions(contributions)` | `MaterialRegistry` 在全部插件完成后冻结                                            | 全部已构建材料；可补能力 data 与形态局部选项                    |
| 5  | `registerMaterialCreativeTabs(material)` | —                                                                                        | 已冻结的材质                                                   |
| 6  | （引擎内部）                             | —                                                                                        | 为每个材质铸造形态物品；先跑全部 `validate` 再跑全部 `process` |
| 7  | `registerMaterialFollowUps(material)`    | —                                                                                        | 全部形态物品已排队；可用稳定 ID，不能读取游戏实例                 |
| 8  | `registerEquipmentKinds(equipment)`      | `EquipmentRegistry`                                                                      | 全部材质（含数据）；之后引擎按 (材料 × 种类) 铸造物品          |
| 9  | `registerMachineFoundation(machine)`     | 机器六表（resource type / UI icon / render / part role / property display / attachment） | 尚未有机器定义                                                 |
| 10 | `registerMachines(machine)`              | `Machines`                                                                               | 已冻结的机器基础表；之后引擎铸造方块/BE 并安装预览计划         |
| 11 | `registerMachineFollowUps(machine)`      | `PipeDistributionStrategies`、`Pipes`                                                    | 全部机器定义（方块/BE 已铸造）                                 |
| 12 | `registerOreFoundation(ore)`             | 矿石四表（shape / mode / air exposure / conflict）                                       | 尚未有矿脉                                                     |
| 13 | `registerOreDisplays(ore)`               | `OreVeinDisplays`                                                                        | 已冻结的模式表；每种模式必须有对应显示                         |
| 14 | `registerOreVeins(ore)`                  | `OreVeins`                                                                               | 已冻结的矿石基础表                                             |
| 15 | `registerOreDatagen(ore)`                | —                                                                                        | 全部矿脉；通常调用 `ore.featureDatagen()` 安装通用 feature 桥 |
| 16 | `registerRecipes(recipe)`                | `ProductionLines`                                                                        | 全部领域定义及已排队的 RegistryLib Entry；游戏对象仍未绑定      |
| 17 | `registerLang(lang)`                     | `LangRegistry`                                                                           | 全部                                                           |
| 18 | `registerRuntime(runtime)`               | 此时不冻结；load complete 才冻结共享运行时表                                               | 已绑定的游戏对象；common setup deferred work                  |

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

上表的能力注册事件位置按项目当前 NeoForge 26.1.2.125 的 `CommonModLoader.load` /
`RegistrationEvents.init` 实现核对：发生在 common / sided setup 之后、IMC 和 load complete 之前。

每个钩子先遍历**全部插件**，再冻结对应表，随后进入下一个钩子。
运行时与 datagen 共用内容管线；datagen 不依赖 common setup 或 load complete 才完成资源声明。
语言键通过声明它的 `LangDomainRegistration` 所属 RegistryCore 导出。
仅运行 `--mod mymod` 会生成 mymod 拥有的键；Topo 自身 API 文案由 Topo 的资源包提供。

## 插件顺序

插件只需调用 `TopoPlugins.register(plugin)`，无需分配插件 ID 或重复声明一份插件依赖。
跨插件依赖由所属模组的依赖关系隐式确认；依赖存在不等于插件或内容已初始化。
引擎在每个阶段遍历全部插件后再冻结该阶段注册表；foundation 钩子因此总是先于对应内容钩子，
不需要在 API Plugin 与内容 Plugin 之间额外配置依赖。引擎按 Mod ID 和插件类名稳定排序，仅用于让独立贡献
和诊断结果可复现；插件不得依赖同一阶段其他插件先执行。模组构造器到达顺序不属于 API 契约。

同一钩子内不要依赖其他插件的执行顺序；钩子代码应使用确定的输入与遍历顺序，不应在构造期抢先写入领域注册表。

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

冻结点后写入会抛异常。例如 `MaterialForm` 必须在 `registerMaterialFoundation` 声明，
`registerMaterials` 只能引用已经声明的形态。静态字段不会因锁定插件表而自动初始化，
每个声明类都应在所属钩子内显式触发 `init()`。

材料的 `build()` 只发布句柄，不关闭数据写入。同一 `registerMaterials` 窗口内可用
`targetMaterial.data(use)` 直接给其他插件已构建的材料补充数据；全部插件完成 `registerMaterialContributions` 后，框架关闭写入、统一匹配形态并校验，再冻结材料。重复数据类型拒绝覆盖。

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
当前仅保证 Topo 单宿主的静态加载；不支持热重载，也不承诺可移除 Topo Mod 的完全嵌入。
