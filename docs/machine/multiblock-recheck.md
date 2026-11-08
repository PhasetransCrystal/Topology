# 多方块周期重检

面向框架开发者；来源为现有多方块蓝图文档，适用于当前重检流程。

运行时由 `MultiblockController` 与 `MultiblockRecheck` 协作：

1. 监听占地变更，标记控制器待检；已成型结构也按配置周期进行兜底检查。
2. 优先定点重测变更单元格；涉及重复增长、角色计数、成员替换或模块可能占地时执行完整检测。无模块的已成型结构可使用 `verify` 维护。
3. `supportsSnapshot()` 为真时采集最大占地快照；达到 `MULTIBLOCK.asyncCellThreshold` 后可异步识别。其他布局使用实时视图同步检测。
4. 异步结果回到服务端线程后检查任务、朝向及脏标记，再处理独占部件预留、成员接入和成型外观。
5. 遇到 `CHUNK_UNLOADED` 保留当前成型状态，按 `MULTIBLOCK.unloadedRetryIntervalTicks` 延迟重试。

