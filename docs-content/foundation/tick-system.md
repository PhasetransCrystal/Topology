---
sidebar_position: 2
---

# Tick System

Topology 的集中 tick 调度系统。按 `(TickKind, interval)` 分桶，每 tick 一次取模检查 → 遍历桶内所有注册回调。避免所有机器各自做取模运算的开销。

## 何时阅读

当你需要编写自定义的非标准 tick 驱动组件时。大多数情况下 Machine 和 Pipe 框架已内部集成，无需直接使用此 API。

## 基本用法

```java
public class MyBlockEntity extends BlockEntity {

    private TickHandle handle;

    @Override
    public void onLoad() {
        super.onLoad();
        TickHub hub = TickHub.of(level);
        if (hub == null) return;

        handle = hub.register(
                TickKind.SYNC,      // 同步（游戏线程）
                20,                  // 每 20 tick = 1 秒
                gameTime -> tick()   // 回调
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

## TickHandle 控制

```java
handle.suspend();        // 暂停（如 chunk 卸载）
handle.resume();         // 恢复
handle.setInterval(10);  // 改间隔为 10 tick
handle.alert();          // 排队一次立即回调
handle.unsubscribe();    // 永久取消
boolean paused = handle.isSuspended();
boolean done = handle.isCancelled();
```

## Sync vs Async

| TickKind | 执行线程   | 适合工作                             |
|----------|------------|--------------------------------------|
| `SYNC`   | 游戏主线程 | 世界修改、BlockState、UI（绝大多数） |
| `ASYNC`  | 调度器线程 | 纯计算（路径搜索、网络图计算）       |

:::caution 异步 Tick
`ASYNC` 回调中 **禁止**访问或修改 Minecraft 世界状态（方块、实体、BlockEntity）。只用于纯数据计算。
:::

## 分桶机制

同一 `(TickKind, interval)` 下的所有回调共享一个桶：

```
桶 (SYNC, 20):  [Furnace-A.tick, Furnace-B.tick, Generator-C.tick, ...]
桶 (SYNC, 5):   [快速机器.tick, ...]
桶 (ASYNC, 40): [路径计算.tick, ...]
```

每秒 20 个 tick → 引擎对 `(SYNC, 20)` 桶每 tick 检查 `gameTime % 20 == 0`，命中一次遍历调用所有回调。百台机器共享一次取模，而非百次。

## API 速查

| 类/方法                                       | 用途                    |
|-----------------------------------------------|-------------------------|
| `TickHub.of(Level)`                           | 获取/创建关卡的 TickHub |
| `TickHub.register(kind, interval, hook)`      | 注册回调 → `TickHandle` |
| `TickHandle.suspend()` / `.resume()`          | 暂停/恢复               |
| `TickHandle.alert()`                          | 排队一次性立即回调      |
| `TickHandle.setInterval(n)`                   | 修改间隔                |
| `TickHandle.unsubscribe()`                    | 永久取消                |
| `TickHandle.isSuspended()` / `.isCancelled()` | 查询状态                |
| `TickKind.SYNC` / `TickKind.ASYNC`            | 执行线程选择            |
