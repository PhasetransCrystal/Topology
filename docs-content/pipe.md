---
sidebar_position: 1
---

# Pipe

管道系统提供可视化物流网络。管道自动形成连接图、执行分发策略，通过统一网络引擎处理传输。

## 关键概念

```
PipeDefinition       ← 一种管道（资源类型 × 等级）
  ├── PipeBlockTemplate   ← 外观/模型模板
  ├── PipeDistributionStrategy ← 分发策略（决定每 tick 送多少到哪些目标）
  └── PipeSurveyTool      ← 勘测器物品（网络吞吐可视化）
```

## 页面导航

| 页面                                  | 内容                                                     | 何时阅读                    |
|---------------------------------------|----------------------------------------------------------|-----------------------------|
| [PipeStrategy](pipe/pipe-strategy.md) | 继承 `PipeDistributionStrategy` 自定义分发逻辑 + 配置 UI | 需要轮询/均分之外的分发行为 |
| [Pipe Surveyor](pipe/pipe-survey.md)  | `PipeSurveyTool` 标记接口 + 勘测行为                     | 需要网络勘测工具            |
| **Pipe（本页）**                      | `Pipes.register` 声明管道、交互规则                      | 添加传输管道                |

## 注册管道

```java
Pipes.register("item_pipe_t1")
    .resource(myItemProfile)                            // PipeResourceProfile 实例
    .blockTemplate(new MyPipeBlockTemplate())            // 外观/模型模板
    .displayName("Item Pipe Tier 1")
    .maxExtractRate(64)                                  // 每 tick 最大提取
    .nodeThroughput(128)                                 // 每节点每 tick 吞吐
    .filter(PipeFilterSettings.NONE)                     // 无需过滤
    .strategy(myRoundRobin, new AggregationWindow(1, 16, 4, 4))  // 你的分发策略 + 聚合窗口
    .build();
```

### 参数说明

| 参数                              | 说明                                       |
|-----------------------------------|--------------------------------------------|
| `resource(profile)`               | 传输资源类型（`PipeResourceProfile` 实例） |
| `blockTemplate(template)`         | 驱动方块/物品注册和模型数据生成            |
| `maxExtractRate(n)`               | 从相邻容器每 tick 提取上限                 |
| `nodeThroughput(n)`               | 每个网络节点每 tick 传输上限               |
| `filter(settings)`                | 白名单/黑名单过滤，无需时用 `NONE`         |
| `strategy(strategy, aggregation)` | 分发策略 + 聚合窗口，至少一个              |

## PipeBlockTemplate

```java
public class MyPipeBlockTemplate implements PipeBlockTemplate {
    @Override
    public BlockBehaviour.Properties styleBlockProperties(BlockBehaviour.Properties properties) {
        return properties.strength(1.5f).sound(SoundType.METAL);
    }

    @Override
    public void configureBlock(BlockBuilder<PipeBlock, RegistryCore> builder, PipeDefinition def) {
        // 方块接线：属性、标签、战利品、blockstate/model 数据生成
    }

    @Override
    public void configureItem(ItemBuilder<BlockItem, ?> item, PipeDefinition def) {
        // 物品接线：展示模型绑定 + 物品数据生成
    }
}
```

## 管道交互

- **Shift + 右键** → 循环端口意图（INPUT / OUTPUT / NONE）
- **普通右键** → 打开提取端口配置 UI
- 任何标记为 `c:tools/wrench` 的物品均可操作
- 管道放置/移除时自动加入/离开最近的兼容网络段

## 勘测器

管道网络勘测工具（`PipeSurveyTool` 标记接口 + `PipeSurveyManager` 行为驱动）——
物品实现接口即成为勘测器，详情见 [Pipe Surveyor](pipe/pipe-survey.md)。

## 分发策略

策略由 `PipeDistributionStrategy` 接口定义——库不内置具体策略，继承实现后通过
`PipeDistributionStrategies.register(strategy)` 注册。接口签名、上下文方法与注册时机见
[PipeStrategy](pipe/pipe-strategy.md)。

## API 速查

| 类/方法                                     | 用途                                  |
|---------------------------------------------|---------------------------------------|
| `Pipes.register(path)`                      | 开始管道注册                          |
| `.resource(profile)`                        | 设置资源类型（必填）                  |
| `.blockTemplate(template)`                  | 设置外观模板（必填）                  |
| `.maxExtractRate(n)` / `.nodeThroughput(n)` | 吞吐配置（必填）                      |
| `.filter(settings)`                         | 过滤配置（必填）                      |
| `.strategy(strategy, window)`               | 分发策略（至少一个）                  |
| `.build()`                                  | 注册并返回 `PipeDefinition`           |
| `Pipes.require(id)`                         | 按 ID 查询                            |
| `PipeDefinition.resourceType()`             | 查询传输类型                          |
| `PipeSideVisual`                            | 管道的面视觉状态（NONE/PIPE/EXTRACT） |
