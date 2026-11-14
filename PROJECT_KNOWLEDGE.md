# 项目知识索引

面向框架开发者；模块 `README.md` 是入口，单点主题文档放在模块目录内。下游用法见 [docs-content](docs-content/)，Demo 如何组合 Topo 能力见 [demo-docs](demo-docs/README.md)。

## 总览与架构

- [架构与总览](docs/architecture/README.md)
- [项目总览](docs/architecture/overview.md)：定位、解决什么问题、分层与阅读路径。
- [模块地图](docs/architecture/module-map.md)：代码模块、职责、依赖与文档入口。
- [框架设计原则](docs/architecture/principles.md)：接口与能力组合约束。
- [支撑模块](docs/architecture/support-modules.md)：客户端、配置、辅助、集成、Mixin。
- [文档编写规范](docs/architecture/documentation-conventions.md)：三端放置、模块 README 结构与更新检查。
- [模块扩展规范](docs/architecture/module-extension-conventions.md)：新增领域/内容/集成步骤、联动与踩坑防护。
- [组合式设计与容器地图](docs/architecture/composition-model.md)：机器、材料、配方、管道和仓储组合边界及阅读路径。

## 模块入口

| 模块 | README | 职责 |
|---|---|---|
| 注册内核 | [kernel/README.md](docs/kernel/README.md) | 插件调度、注册计划、领域模块、冻结 |
| 语言与显示名 | [lang/README.md](docs/lang/README.md) | 语言键、键族、显示名 |
| 材料 | [material/README.md](docs/material/README.md) | 材料、形态、数据类型、后处理器、查找 |
| 装备 | [equipment/README.md](docs/equipment/README.md) | 装备种类 × 材料铸造与行为 |
| 机器 | [machine/README.md](docs/machine/README.md) | 机器、组件、端口、渲染、UI、多方块 |
| 配方 | [recipe/README.md](docs/recipe/README.md) | 能力型 I/O、类型、生产线、导入导出 |
| 矿脉 | [ore/README.md](docs/ore/README.md) | 环境、矿脉、模式、策略、显示、数据生成 |
| 管道 | [pipe/README.md](docs/pipe/README.md) | 管道定义、网络、分发、勘测 |
| 仓储 | [storage/README.md](docs/storage/README.md) | StorageAccess、组合器状态、资源与批量操作 |
| 集成 | [integration/README.md](docs/integration/README.md) | AE2、Jade、JEI 适配边界 |
| 客户端 | [client/README.md](docs/client/README.md) | 渲染、UI tooltip、预览、探针 |
| 配置 | [config/README.md](docs/config/README.md) | NeoForge 配置与配置语言键 |
| 辅助 | [helper/README.md](docs/helper/README.md) | ID、材料、标签、过滤、紧凑数字 |
| Tick | [tick/README.md](docs/tick/README.md) | 分桶调度、心跳、机器 tick 句柄 |
| 视觉/CTM | [visual/README.md](docs/visual/README.md) | 连接纹理、模型族、数据生成 |
| Mixin | [mixin/README.md](docs/mixin/README.md) | 定点补丁与边界 |
| 验证 | [verification/README.md](docs/verification/README.md) | 测试启动、回归、发布与手动验收 |

## 关键主题

- 注册内核：[内部包与扩展边界](docs/kernel/api-packages.md)、[宿主接线](docs/kernel/host-wiring.md)、[注册管线时序](docs/kernel/registration-timeline.md)、[新增注册领域](docs/kernel/registration-domain-extension.md)。
- 材料：[形态解析和计划冻结](docs/material/form-resolution-and-plans.md)、[材料注册性能](docs/material/material-registration-performance.md)。
- 机器组合与容器：[能力声明式组合](docs/machine/component-composition.md)、[运行时组件容器](docs/machine/machine-components-container.md)、[数据域与字段生命周期](docs/machine/data-scope-lifecycle.md)；多方块：[设计目录](docs/machine/design-index.md)、[蓝图知识](docs/machine/multiblock-blueprint-knowledge.md)、[检测实现](docs/machine/multiblock-detection-implementation.md)、[结构工具约定](docs/machine/multiblock-tool-agreements.md)、[周期重检](docs/machine/multiblock-recheck.md)、[占地与独占索引](docs/machine/multiblock-claim-index.md)、[JEI 工作方块栏](docs/machine/multiblock-jei-integration.md)。
- 配方：[能力组合与 I/O 聚合](docs/recipe/capability-composition.md)、[类型内部职责](docs/recipe/type-internals.md)、[导出投影](docs/recipe/export-projection.md)。
- 矿脉：[Grid 规划内部流程](docs/ore/grid-planning-internals.md)。
- 管道：[网络运行时](docs/pipe/network-runtime.md)、[勘测回归](docs/pipe/pipe-survey-regression.md)。
- 仓储：[通用边界](docs/storage/storage-boundaries.md)、[组合器行为与边界](docs/storage/storage-composition.md)；AE2 适配：[资源访问与输入窗口](docs/integration/ae2-resource-and-stocking.md)。
- 集成：[AE2 资源访问](docs/integration/ae2-resource-and-stocking.md)、[JEI 配方分类与查询](docs/integration/jei-integration.md)、[Jade 数据与面板](docs/integration/jade-data-pipeline.md)、[AE 样板内容转换](docs/integration/ae-pattern-conversion.md)。
- 客户端：[悬浮面板缓存](docs/client/tooltip-cache.md)。
- Tick：[Tick 分桶](docs/tick/tick-buckets.md)。
- 验证：[NeoForge 测试启动](docs/verification/neo-forge-tests.md)、[插件回归](docs/verification/plugin-regression.md)、[发布验证](docs/verification/release-verification.md)、[结构工具验证](docs/verification/structure-tool-verification.md)。
