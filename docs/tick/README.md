# Tick 模块内部

面向框架开发者。Tick 模块把大量机器 tick 摊到不同 tick，避免单 tick 峰值，并提供心跳与句柄抽象。

## 职责与边界

- 负责：分桶调度、心跳、tick 句柄、条件 tick。
- 不负责：机器业务逻辑；机器组件决定 tick 做什么。
- 分桶只改变执行时机，不改变注册/冻结顺序。

## 关键类型与数据流

- `TickHub` / `TickHeartbeat` / `TickHandle` / `TickHook` / `TickKind`。
- `ConditionalTicker` / `NoopTickHandle`。
- `MachineTicker`：机器侧 tick 句柄。

## 为什么这样设计

- 大量机器同 tick 工作会造成峰值；分桶把工作摊开，保持平均吞吐。
- 句柄抽象让机器不直接依赖全局调度器，便于替换/停用。
- 心跳提供统一的时间基准，避免各模块自建计时器。

## 与其他模块的联动

- 机器：组件通过 `MachineTicker`/tick 句柄执行工作。
- 异步执行器：耗时任务交给异步设施，不阻塞 tick。
- 运行时：共享运行时表在 load complete 冻结，tick 不写注册表。

## 扩展规范

- 新 tick 任务通过句柄/分桶注册，不新增全局监听器。
- tick 回调保持短小；耗时工作转异步或分帧。
- 不在 tick 中注册内容或冻结表。
- 补 `docs-content/foundation/tick-system.md` 的下游说明（如适用）。

## 常见坑与防止

- 在 tick 中 `.get()` 未绑定对象：启动早期会失败。
- 所有机器同 tick 执行：使用分桶。
- tick 回调抛异常：可能中断调度；明确失败边界。
- 用 tick 修改已冻结表：不允许。

## 文档

- [Tick 分桶](tick-buckets.md)
- 公开说明见 [`docs-content/foundation/tick-system.md`](../../docs-content/foundation/tick-system.md)。
