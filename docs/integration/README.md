# 集成模块内部

面向框架开发者。集成模块把 Topo 的资源、配方与机器接入 AE2、Jade 和 JEI；它是可选适配层。

## 职责与边界

- 负责：AE2 资源/仓储/样板适配、Jade provider、JEI 分类与查询、必要 mixin。
- 不负责：核心内容注册与运行时；缺少某个集成时框架仍应可用。
- 集成不能反向成为领域模块的必需依赖。

## 关键类型与数据流

- AE2：`AeKeyResourceAdapter`、`AeStorageAccess`、`AeStockingResourceHandler`、`AePatternProvider`。
- Jade：机器/管道 provider、资源条、时序面板。
- JEI：`TopoJeiCategoryRegistry`、配方查询、幽灵拖放、AE2 样板转移。
- 数据流：领域公开契约 → 集成读取资源/配方/机器状态 → 只做展示或外部搬运。

## 为什么这样设计

- 第三方 API 变化频繁，隔离在 `integration` 内，领域模块只依赖自己的契约。
- 显示与行为分开：Jade/JEI 只展示，AE2 搬运才涉及真实资源变更。
- 可选适配：没有某个模组时，核心内容管线仍能启动。

## 与其他模块的联动

- 机器/配方/矿脉/管道：提供分类、provider、配方能力分组与资源条所需数据。
- 客户端：渲染与 UI 由客户端模块处理；集成只提供数据/面板贡献。
- 语言：Jade/JEI 文本走语言模块；外部配置键用 `absolute`。
- 仓储：AE 适配是仓储访问的一种实现，不是通用契约。

## 扩展规范

- 新集成放在 `integration/<mod>`，用可选加载/条件注册隔离。
- 只读展示不要修改领域状态；需要搬运时明确事务/回滚边界。
- 新 JEI 分类/配方转移先确认公开契约，不在分类器里解析机器内部。
- 新增 mixin 仅用于没有公开扩展点的位置，并记录目标版本。
- 补 `docs-content/compatibility/` 的下游说明与验证项。

## 常见坑与防止

- 把 AE `MODULATE` 当原子事务：失败项状态可能未知，不能自动重试。
- 在 JEI/Jade provider 里读取未绑定对象：等对应生命周期/绑定完成。
- 让核心模块直接引用第三方类：缺模组时会启动失败。
- 用 mixin 替代公开扩展点：优先 API/能力/事件。
- 只验证编译：JEI/Jade 分类、样板转移与网络行为需要游戏内验收。

## 文档

- [AE2 资源访问与配置输入窗口](ae2-resource-and-stocking.md)：AE key 映射、定向网络存取与输入窗口事务。
- [AE 样板内容转换](ae-pattern-conversion.md)
- [JEI 配方分类与运行时查询](jei-integration.md)：类型分类、机器催化剂与 runtime opener。
- [Jade 服务端数据与客户端面板](jade-data-pipeline.md)：机器/管道 payload 的所有权和展示边界。
- 通用仓储设计见[仓储模块](../storage/README.md)；AE2 具体适配边界见上方专题。
- 下游兼容说明见 [`docs-content/compatibility/`](../../docs-content/compatibility/)。
