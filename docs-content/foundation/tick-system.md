---
sidebar_position: 3
---

# Tick System

面向需要调度自定义周期任务的模组开发者。Topology 提供按间隔执行的集中 tick 调度。

## 何时阅读

当你需要编写自定义的非标准 tick 驱动组件时。大多数情况下 Machine 和 Pipe 框架已内部集成，无需直接使用此 API。

## 基本用法

```java
public class MyBlockEntity extends BlockEntity {

    private TickHandle handle;

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide()) return;
        TickHub hub = TickHub.of(level);
        if (hub == null) return;

        handle = hub.register(
                TickKind.SYNC,      // 同步（游戏线程）
                20,                  // 每 20 个服务端 tick 执行一次
                (gameTime, handle) -> tick()   // 回调
        );
    }

    @Override
    public void setRemoved() {
        if (handle != null && !handle.isCancelled()) {
            handle.unsubscribe();
        }
        super.setRemoved();
    }

    private void tick() {
        // 你的周期性逻辑
    }
}
```

`TickHub.of(level)` 返回已附着到该世界的调度器，暂不可用时返回 `null`；它不会创建调度器。
间隔必须至少为 1，服务端 tick 可能受游戏负载影响，不能当作准确的墙上时间。

## TickHandle 控制

```java
handle.suspend();        // 暂停（如 chunk 卸载）
handle.resume();         // 恢复
handle.setInterval(10);  // 改间隔为 10 tick
handle.alert();          // 在该通道的下一次心跳排队执行一次
handle.unsubscribe();    // 永久取消
boolean paused = handle.isSuspended();
boolean done = handle.isCancelled();
```

## Sync vs Async

| TickKind | 执行线程   | 适合工作                             |
|----------|------------|--------------------------------------|
| `SYNC`   | 服务端线程 | 世界修改、BlockState 等服务端操作 |
| `ASYNC`  | 调度器线程 | 纯计算（路径搜索、网络图计算）       |

:::caution
**异步 Tick** — `ASYNC` 回调中 **禁止**访问或修改 Minecraft 世界状态（方块、实体、BlockEntity）。只用于纯数据计算。
:::

## API 速查

| 类/方法                                       | 用途                    |
|-----------------------------------------------|-------------------------|
| `TickHub.of(Level)`                           | 获取已附着的 TickHub，可能为 null |
| `TickHub.register(kind, interval, hook)`      | 注册回调 → `TickHandle` |
| `TickHandle.suspend()` / `.resume()`          | 暂停/恢复               |
| `TickHandle.alert()`                          | 排队一次性立即回调      |
| `TickHandle.setInterval(n)`                   | 修改间隔                |
| `TickHandle.unsubscribe()`                    | 永久取消                |
| `TickHandle.isSuspended()` / `.isCancelled()` | 查询状态                |
| `TickKind.SYNC` / `TickKind.ASYNC`            | 执行线程选择            |
