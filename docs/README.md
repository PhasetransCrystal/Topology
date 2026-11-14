# 框架内部文档

面向框架开发者。本目录记录 Topology 的内部实现、边界与验证方式；下游使用说明在 [`docs-content/`](../docs-content/)，Demo 如何组合 Topo 能力在 [`demo-docs/`](../demo-docs/)。

每个模块目录以 `README.md` 为入口；单点主题文档放在所属模块目录内。公开契约不在本目录重复，只链接 `docs-content`。

模块 README 固定包含：职责与边界、关键类型与数据流、为什么这样设计、与其他模块的联动、扩展规范、常见坑与防止、文档入口。总规则见[文档编写规范](architecture/documentation-conventions.md)与[模块扩展规范](architecture/module-extension-conventions.md)。

## 模块

| 模块 | 入口 | 内容 |
|---|---|---|
| 架构与总览 | [architecture/README.md](architecture/README.md) | 项目定位、模块地图、设计原则、支撑模块 |
| 注册内核 | [kernel/README.md](kernel/README.md) | 插件调度、注册计划、领域模块、冻结 |
| 语言与显示名 | [lang/README.md](lang/README.md) | 语言键、键族、显示名 |
| 材料 | [material/README.md](material/README.md) | 材料、形态、数据类型、后处理器、内容查找 |
| 装备 | [equipment/README.md](equipment/README.md) | 装备种类 × 材料铸造与行为 |
| 机器 | [machine/README.md](machine/README.md) | 机器定义、组件、资源端口、渲染、UI、多方块 |
| 配方 | [recipe/README.md](recipe/README.md) | 能力型 I/O、类型、生产线、导入导出 |
| 矿脉 | [ore/README.md](ore/README.md) | 环境、矿脉、模式、策略、显示、数据生成 |
| 管道 | [pipe/README.md](pipe/README.md) | 管道定义、网络、分发策略、勘测 |
| 仓储 | [storage/README.md](storage/README.md) | StorageAccess 设计、资源、批量操作 |
| 集成 | [integration/README.md](integration/README.md) | AE2、Jade、JEI 适配边界 |
| 客户端 | [client/README.md](client/README.md) | 渲染、UI tooltip、结构预览、勘测渲染、探针 |
| 配置 | [config/README.md](config/README.md) | NeoForge 配置与配置语言键 |
| 辅助 | [helper/README.md](helper/README.md) | ID、材料、标签、过滤、紧凑数字工具 |
| Tick | [tick/README.md](tick/README.md) | 分桶调度、心跳、机器 tick 句柄 |
| 视觉/CTM | [visual/README.md](visual/README.md) | 连接纹理方块、模型族、数据生成 |
| Mixin | [mixin/README.md](mixin/README.md) | 定点补丁及边界 |
| 验证 | [verification/README.md](verification/README.md) | 测试启动、回归、发布与手动验收 |

## 阅读路径

- 第一次接触框架：[architecture/overview.md](architecture/overview.md) → [architecture/module-map.md](architecture/module-map.md) → [architecture/composition-model.md](architecture/composition-model.md) → [kernel/README.md](kernel/README.md)。
- 改某个领域：对应模块 README → `docs-content` 公开契约 → 源码。
- 改 Demo：`demo-docs/`；Demo 的状态与证据在 Obsidian 项目知识库。
