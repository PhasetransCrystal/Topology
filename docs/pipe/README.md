# 管道模块内部

面向框架开发者。管道模块由管道定义、方块模板、资源类型、分发策略、过滤与网络勘测组成。

## 职责与边界

- 负责：管道定义、方块/物品注册、资源类型与 block capability、分发策略与批次、过滤、网络连接与勘测。
- 不负责：具体容器实现；通过资源类型声明的 capability 查询相邻容器。
- 库不内置轮询/均分等具体策略；策略由产品注册。

## 关键类型与数据流

- `PipeDefinition` / `Pipes`：管道句柄与冻结注册表。
- `PipeBlockTemplate` / `PipeBlock`：方块/物品与模型接线。
- `PipeResourceProfile`：传输资源类型与 capability。
- `PipeDistributionStrategy` / `PipeDistributionContext` / `AggregationWindow`：分发与批次。
- `PipeFilterSettings` / `PipePortSettings` / `PipePortStrategyConfig`：过滤与端口配置。
- 勘测：`PipeSurveyTool` 标记接口、`PipeSurveyManager`、客户端渲染。

## 为什么这样设计

- 分发策略可插拔，因为不同资源/机器的吞吐与公平性目标不同。
- 聚合窗口把多次小额请求合并成批次，避免每 tick 频繁访问容器。
- 管道只依赖资源类型的 block capability，不按模组名识别容器。
- 网络按兼容资源段连接，避免不同资源管道错误合并。

## 与其他模块的联动

- 机器：资源类型与端口由机器模块声明；管道引用同一 capability。
- 客户端：勘测状态与渲染；见 [client/README](../client/README.md)。
- Jade：管道 provider 与资源条；见 [integration/README](../integration/README.md)。
- 语言：管道显示名与策略 UI 文本走语言模块。
- 材料：管道方块/物品可由产品注册，不强制走材料形态。

## 扩展规范

- 新资源/等级：声明 `PipeResourceProfile`、方块模板、吞吐与过滤，再注册策略。
- 新策略实现 `PipeDistributionStrategy`，在 `PipeDistributionStrategies` 注册；明确 `budget`/`distribute`/配置编解码。
- 新过滤用 `PipeFilterSettings`/适配器，不要改网络引擎。
- 保留旧 `Pipes.register` 的 `topo` 命名空间；新内容用插件领域入口。
- 补 `docs-content/pipe/*` 契约与勘测/网络验收。

## 常见坑与防止

- 认为注册策略会立即执行 `distribute`：它在传输时触发。
- 策略不处理 `budget`/剩余需求：会造成超发或丢弃。
- 容器没有对应 block capability：管道不会识别；需要产品适配。
- 用模组名判断容器：应使用资源类型与 capability。
- 只做数据生成验证：模型、策略切换、跨资源连接需要游戏内验收。

## 文档

- [管道网络运行时](network-runtime.md)：保存节点、维度协调器、连通分量和失效路径。
- [管道勘测回归](pipe-survey-regression.md)
- 公开契约见 [`docs-content/pipe.md`](../../docs-content/pipe.md)。
