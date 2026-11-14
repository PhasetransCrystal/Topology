# 分桶机制

面向框架开发者。调度实现以源码为准。

同一 `(TickKind, interval)` 下的所有回调共享一个桶：

```
桶 (SYNC, 20):  [Furnace-A.tick, Furnace-B.tick, Generator-C.tick, ...]
桶 (SYNC, 5):   [快速机器.tick, ...]
桶 (ASYNC, 40): [路径计算.tick, ...]
```

每秒 20 个 tick → 引擎对 `(SYNC, 20)` 桶每 tick 检查 `gameTime % 20 == 0`，命中一次遍历调用所有回调。百台机器共享一次取模，而非百次。
