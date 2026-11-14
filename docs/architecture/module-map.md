# 模块地图

面向框架开发者。按代码包列出模块职责与内部入口。依赖方向为内核 → 领域 → 集成；Demo 是独立内容 Mod，不参与框架依赖。

| 模块 | 代码 | 职责 | 内部入口 |
|---|---|---|---|
| 注册内核 | `api/api` | 插件登记、领域模块、步骤依赖、注册点、冻结与阶段状态；含注解、异步、基础设施与工具子包 | [kernel/README](../kernel/README.md) |
| 语言与显示名 | `api/api/lang`、`api/lang` | 语言键注册、键族、中文转换；条目显示名来源与模板 | [lang/README](../lang/README.md) |
| 材料 | `api/material` | 材料、形态、数据类型、后处理器、内容查找 | [material/README](../material/README.md) |
| 装备 | `api/equipment` | 装备种类 × 材料铸造、机器交互行为 | [equipment/README](../equipment/README.md) |
| 机器 | `api/machine` | 机器定义、组件、资源类型与端口、渲染、UI、多方块控制器 | [machine/README](../machine/README.md) |
| 配方 | `api/recipe` | 能力型 I/O、配方类型、生产线、搜索、导入/导出 | [recipe/README](../recipe/README.md) |
| 矿脉 | `api/ore` | 环境、矿脉、生成模式、形状、暴露/冲突策略、JEI 显示 | [ore/README](../ore/README.md) |
| 管道 | `api/pipe` | 管道定义、网络引擎、分发策略、勘测 | [pipe/README](../pipe/README.md) |
| 仓储 | `api/api/util`、`integration/ae2` | `StorageAccess`、批量操作、组合与 AE 适配 | [storage/README](../storage/README.md) |
| Tick | `api/api/tick`、`api/tick` | 分桶调度、心跳与机器 tick 句柄 | [tick/README](../tick/README.md) |
| 视觉/CTM | `api/api/visual` | 连接纹理方块、模型族、数据生成与宿主 | [visual/README](../visual/README.md) |
| 客户端 | `client` | 机器渲染、UI tooltip、结构工具、管道勘测渲染、开发探针 | [client/README](../client/README.md) |
| 配置 | `config` | NeoForge 配置与配置语言键 | [config/README](../config/README.md) |
| 辅助 | `helper` | ID、材料、标签、过滤、紧凑数字等无状态工具 | [helper/README](../helper/README.md) |
| 集成 | `integration` | AE2 资源/仓储/样板，Jade provider，JEI 分类与查询 | [integration/README](../integration/README.md) |
| Mixin | `mixin` | JEI、矿脉区块生成、游戏测试的定点补丁 | [mixin/README](../mixin/README.md) |
| 验证 | 测试与发布流程 | 测试启动、回归、发布与手动验收 | [verification/README](../verification/README.md) |
| Demo | `src/demo` | TopoDemo 内容实现与验证 | [demo-docs](../../demo-docs/README.md) |

## 依赖关系

- 注册内核不依赖领域模块；领域模块只声明真实依赖，不依赖组合清单顺序。
- 机器、配方、矿脉依赖材料；装备依赖材料；管道依赖机器的资源类型；多方块检测属于机器。
- 集成依赖 API，不被领域模块反向依赖；缺少某个集成时核心内容管线仍应可用。
- `docs-content` 是下游用法；`docs` 是内部细节；`demo-docs` 是 Demo 如何组合 Topo 能力。

## 边界

- `api.api.plugin.internal`、宿主接线和底层 `begin` / `freeze` 使用 `@ApiStatus.Internal`，不是下游接入契约。
- 库不内置玩法内容：材料形态、数据类型、资源端口、装备种类、管道策略都需要产品实现。
- 仓储接口 `StorageAccess` 已存在于 `api.api.util`，但统一试算、适配发现与稳定公开契约尚未完成；AE 实现是已配置输入窗口的适配。
- 支持单宿主静态加载；不支持热重载或移除 `topo` Mod 的完全内嵌。
