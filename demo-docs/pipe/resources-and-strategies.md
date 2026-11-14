# 管道资源与策略

面向 TopoDemo 维护者。本文说明 Demo 如何用 Topo 的管道定义、资源类型与分发策略注册传输网络。

## 使用的 Topo 能力

- `PipeResourceProfile`：资源类型与 block capability。
- `PipeBlockTemplate` / `PipeBlock`：方块/物品注册与模型数据生成。
- `PipeDistributionStrategy`：`budget`、`distribute` 与配置 UI。
- `AggregationWindow`、`PipeFilterSettings`：批次与过滤。
- `PipeSurveyTool` 标记接口与勘测行为。

## Demo 如何组合

`BuiltinPipes` / `BuiltinTieredPipes` 声明 15 个资源与等级组合；`PipeBlockTemplates` 提供方块模板；`ByDistanceStrategy`、`EqualSplitStrategy`、`RateStrategy`、`RoundRobinStrategy` 提供分发策略；`BuiltinPipeFilterAdapters` 接入过滤。旧版引用的管道材质文件未在参考仓库源码中找到，当前使用原版方块纹理。

## 验收状态

数据生成及游戏中的实际模型、策略切换和跨资源连接仍需运行验收。

## 相关

- 公开契约：[`docs-content/pipe.md`](../../docs-content/pipe.md)
- 框架内部：[`docs/pipe/README.md`](../../docs/pipe/README.md)
