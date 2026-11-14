# 支撑模块

面向框架开发者。客户端、配置、辅助、集成与 mixin 不参与内容声明，但承担运行时接线；它们不是下游接入契约。

## 客户端 [`client`](../client/README.md)

- 机器方块与 BlockEntity 渲染、机器 UI tooltip 注入。
- 结构工具客户端预览、管道勘测客户端状态与渲染。
- 开发期探针：tooltip、JEI 查询、UI 几何转储。
- 只处理客户端表现；内容注册和持久化仍在公共 API 与机器运行时。

## 配置 [`config`](../config/README.md)

- NeoForge 配置定义与配置语言键；不注册内容领域。
- 配置项语义应通过 `TopoConfigLang` 暴露，避免在业务代码散落裸键。

## 辅助 [`helper`](../helper/README.md)

- `IdHelper`、`MaterialHelper`、`TagHelper`、`ResourceFilterHelper`、`TopoCompactNumber`。
- 无状态工具；不持有注册表，不替代领域入口。

## 集成 [`integration`](../integration/README.md)

- AE2：`AeKeyResourceAdapter` 保留资源身份，`AeStorageAccess` 做网络存取，`AeStockingResourceHandler` 实现已配置输入窗口；另有样板与端口适配。
- Jade：机器/管道 provider、资源条与时序面板，只负责显示。
- JEI：配方分类、查询、幽灵拖放与 AE2 样板转移。
- 集成是可选适配层；核心内容管线不依赖某个集成存在。

## Mixin [`mixin`](../mixin/README.md)

- JEI 配方 GUI 逻辑、矿脉区块生成、游戏测试服务端静默等定点补丁。
- 只用于没有公开扩展点的位置；新增 mixin 应限定目标与失败边界，不作为常规扩展方式。

## 边界

- 这些包位于框架内部；下游应使用 `docs-content` 记录的公开 API 或兼容机制。
- `StorageAccess` 接口已存在于 `api.api.util`，但统一试算、适配发现与稳定公开契约尚未完成；AE 实现是已配置输入窗口的适配，不是通用仓储契约。
