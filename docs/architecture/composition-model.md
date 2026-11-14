# 组合式设计与运行时容器地图

面向刚接触框架的开发者和维护跨模块能力的框架开发者。本文给出 Topology 中“选择实现、组合声明、由框架执行”的共同模式，以及各模块容器负责的范围；具体契约和算法见链接专题。

## 共同模式

Topology 的组合式设计不要求所有模块使用同一种容器。共同点是：调用方在声明或输入组装处选择能力实现，框架在统一流程中依赖较窄的接口执行它；容器只索引、协调其明确拥有的一组对象。

```text
插件 / 产品声明
      │ 选择策略、能力或组件实现
      ▼
定义与注册表 ──冻结──> 不可变句柄 / 计划
      │                           │
      └──────────────运行时宿主────┘
                         │
                 对组合对象统一分发
```

阅读代码时先区分四类对象：

1. **策略/能力契约**：描述某种变化点的行为，例如 `MaterialFormStrategy`、`RecipeCapability`、`PipeDistributionStrategy`。
2. **声明/挂载对象**：把实现与稳定身份、配置或静态元数据关联，例如 `ComponentMount`、材料形态注册项、管道定义。
3. **容器/目录**：限制查找和聚合范围，例如一个机器实例的 `MachineComponents`、单类型配方的 `RecipeCatalog`、一个维度的 `PipeLevelRuntime`。
4. **执行计划/视图**：将声明转成一次冻结后执行的数据，例如 `MaterialFormPlan`、`MachineResourceViews`、配方预览计划。

这几类职责不能互换：容器不是万能依赖注入器；声明不等于运行时对象已创建；策略注册也不意味着策略立即执行。

## 模块中的具体组合点

| 能力变化点 | 选择/声明位置 | 框架组合或容器 | 主要边界 |
|---|---|---|---|
| 装备如何适用于材料 | `EquipmentStrategy.appliesTo(material)` 与 `register(...)` | 装备注册阶段遍历冻结材料 × 装备种类并发布物品记录 | 策略判断和铸造分开；行为通过可选 `MachineItemBehavior` 接机器 |
| 材料有哪些形态内容 | `MaterialFormRegistry` 注册形态策略，材料数据/选项提供输入 | `MaterialFormResolver` 统一匹配，`MaterialFormPlan` 冻结后由 executor 执行 | 匹配不读其他自动形态；计划构造、校验、注册分阶段 |
| 机器有哪些行为 | `Machines.Builder.component(...)` 追加 `ComponentMount` | 每个 `MachineBlockEntity` 持有一个 `MachineComponents` | key 找组件实例，service key 找能力；声明元数据不等于运行状态 |
| 配方如何处理资源 | 配方内容由 capability 铸造，类型定义内容集合 | `TopoRecipe` 按 capability 聚合输入/输出；`RecipeSearchState` 建运行时索引 | 配方能力不拥有机器端口；机器提供 handler，配方契约决定如何匹配和提交 |
| 矿脉如何适应生成环境 | 矿脉声明组合 environment、placement 和策略 ID | foundation 冻结策略，Feature datagen bridge 或 Grid planner 执行 | Feature 与 Grid 是不同执行通道；显示策略须与模式配对 |
| 管道如何分配传输 | `PipeDefinition` 选择分发策略与聚合窗口 | 每维度 `PipeLevelRuntime` 协调保存图，`PipeNetwork` 承载同类连通分量运行态 | 策略决定分配；网络维护拓扑、预算和流量账本 |
| 多个存储如何访问 | 访问方选择适配器或组合顺序 | `CompositeStorageAccess` 按序执行并汇总实际结果 | 不提供组合试算、跨后端事务或同底层目标去重；公开稳定性仍待确定 |

## 怎样选择入口

- 改跨模块依赖原则或要快速了解模式：先看本页和[框架设计原则](principles.md)。
- 改单个能力的公开扩展：从 `docs-content/<模块>/` 读契约，再对照对应 `docs/<模块>/README.md`。
- 改容器生命周期、缓存或路由：读下方对应的内部专题与源码；不要从公共使用示例推断内部时序。
- 需要真实组合案例：看 `demo-docs/<模块>/`，分清 Demo 自身选择与框架保证的契约。

## 组合容器专题

- 机器 mount 与实例容器：[能力声明式组合](../machine/component-composition.md)、[MachineComponents 运行时容器](../machine/machine-components-container.md)。
- 材料形态匹配与执行计划：[形态解析和计划冻结](../material/form-resolution-and-plans.md)。
- 装备按“种类 × 材料”铸造，见[装备模块](../equipment/README.md)。
- 配方能力及 `TopoRecipe` 聚合：[配方能力组合与 I/O](../recipe/capability-composition.md)。
- 矿脉策略和生成通道：[矿脉模块](../ore/README.md)、[Grid 规划内部流程](../ore/grid-planning-internals.md)及下游[策略契约](../../docs-content/ore/ore-strategies.md)。
- 管道保存图与运行网络：[管道网络运行时](../pipe/network-runtime.md)。
- 仓储访问组合状态：[StorageAccess 设计目录](../storage/README.md)、[组合器行为与边界](../storage/storage-composition.md)。

## 设计理由与常见误区

- 先选择实现再统一调用，能让通用流程避免检查具体插件、后端或游戏模式；若分支参数只对少数实现有意义，应归入扩展契约。
- 容器按生命周期和所有权设界：机器容器不跨机器共享，维度管道运行时不作为注册表，配方目录不持有运行时搜索缓存。
- 不能因为出现 `Builder`、`Registry` 或 `Context` 就认定完成了组合。需要继续核对谁拥有状态、何时冻结、容器暴露何种查询、运行时谁负责失效。
- 不要把未验收结构工具方案或 Demo 示例描述成框架已实现契约；仓储组合已实现的行为与尚未提供的试算/事务能力分别见专题状态和当前源码。

## 后续编写规范

新增组合/容器文档时，限定一个问题并说明：对象归属与生命周期、选择实现的位置、容器索引什么、不索引什么、调用时序、变更如何传播/失效，以及与相邻模块的契约。把公开调用步骤写入 `docs-content/`，内部缓存/调度理由写入 `docs/`，具体产品拼装与验收状态写入 `demo-docs/`。
