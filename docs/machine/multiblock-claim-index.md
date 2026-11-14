# 多方块占地与独占索引

面向框架开发者；来源为现有多方块蓝图文档，适用于占地监听与独占预留维护。

`MultiblockClaimIndex` 只管理占地、监听和独占预留，不持有世界或生产快照；世界采集由 `StructureCaptures` 负责。
`tryClaim(...)` 返回 `ClaimUpdate`：`accepted=false` 时旧预留完全保留；成功替换且 `released` 非空时，控制器唤醒相交监听者。
释放范围包含主占地之外的模块独占部件，模块退出而主体仍成型时也会释放并唤醒竞争者。

