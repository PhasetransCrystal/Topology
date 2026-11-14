# 机器模块内部

面向框架开发者。机器模块把机器定义、方块/BlockEntity、行为组件、资源端口、渲染与 UI 组合成可注册句柄。

## 职责与边界

- 负责：机器声明与校验、方块/BE 组装、组件 trait、资源 I/O 面、渲染/UI 贡献、多方块控制器。
- 不负责：具体资源端口、能量语义、配方类型；端口与资源类型由产品实现。
- 库不内置物品/流体/能量端口，避免把某种资源模型强加给下游。

## 关键类型与数据流

- `MachineDefinition` / `Machines` / `MachineValidator`：声明、注册表与校验。
- `MachineBlock` / `MachineBlockEntity` / `MachineBlockEntityFactory`：方块与运行时组装。
- `MachineComponent`：行为 trait、数据字段、tick、资源 I/O、工作状态。
- `ResourcePort` / `MachineResourceType` / 端口元数据：资源存取面。
- 渲染/UI：`MachineBlockRenderStrategy`、`collectMachineUi`、XEI 贡献。

## 为什么这样设计

- 用组件组合机器能力，避免为每种机器写一个巨大的 BlockEntity 分支。
- 端口与资源类型由产品实现，让物品/流体/能量/自定义资源共享同一机器骨架。
- 声明与运行时分离：注册期只排方块/BE 与预览计划，实例创建时才执行工厂回调。
- 多方块作为组件组合进机器，而不是独立于机器的另一套注册体系。

## 与其他模块的联动

- 材料：机器方块/物品通常由材料形态提供；端口资源类型由产品声明。
- 配方：`RecipeLogic` 挂载配方类型，执行能力型 I/O；配方模块不依赖具体机器类。
- 管道：管道引用机器资源类型的 block capability；端口实现决定连接语义。
- 仓储：多方块工具通过 `StorageAccess` 取用/存回材料，不向仓储接口传结构元数据。
- 客户端：渲染、UI tooltip、结构预览；见 [client/README](../client/README.md)。
- 集成：JEI 工作方块栏、Jade 机器面板；见 [integration/README](../integration/README.md)。

## 扩展规范

- 新组件继承 `MachineComponent`，用 `ComponentMount` 挂载；同一 key 不可重复挂载。
- `resolveDependencies` 只做结构校验；状态读取放到 `onMachineLoad`/运行时回调。
- 新端口继承 `ResourcePort`，声明 `MachineResourceType` 与端口元数据；不要改公共机器流程。
- 新渲染/UI 用策略与 `collectMachineUi` 贡献，不在方块类里写全局分支。
- 多方块用蓝图 DSL 与控制器组件；补 `docs/machine/` 设计与验收文档。

## 常见坑与防止

- 在 `resolveDependencies` 读持久化状态或世界数据：此时只应做结构校验。
- 把共享 BE 类型注册当成每台机器实例已创建：工厂在实例创建时执行。
- 认为注册策略对象会立即执行全部回调：匹配、渲染、I/O 各有触发时点。
- 在机器类里硬编码玩家模式/资源类型分支：用组件、端口与能力组合。
- 未完成的结构工具方案当成公开 API：先补设计/验收文档。

## 文档

- [能力声明式组合](component-composition.md)：mount、注册期元数据与机器定义的边界。
- [运行时组件容器](machine-components-container.md)：`MachineComponents` 的索引、依赖解析、服务发现与资源聚合。
- [机器数据域与字段生命周期](data-scope-lifecycle.md)：字段 schema、读写阶段、存档持久化和客户端同步。
- [多方块设计目录](design-index.md)、[蓝图知识](multiblock-blueprint-knowledge.md)、[检测实现](multiblock-detection-implementation.md)。
- 公开契约见 [`docs-content/machine.md`](../../docs-content/machine.md)。
