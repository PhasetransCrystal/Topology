# 集成适配

面向 TopoDemo 维护者。本文说明 Demo 如何在内容侧使用 Topo 的 AE2、Jade 与 JEI 集成。

## 使用的 Topo 能力

- AE2：`AeStorageAccess`、`AeStockingPort`、`AePatternProvider` 等资源/仓储/样板适配。
- Jade：机器/管道 provider 与面板贡献。
- JEI：配方分类、配方查询、幽灵拖放与 AE2 样板转移。
- 客户端/网络：端口同步、配置页与 UI。

## Demo 如何组合

入口位于 `src/demo/java/net/ptcrys/topodemo/integration/`：`AeStockingItemPort` / `AeStockingFluidPort`、`AePatternProvider` 及同步类接入 AE2；`DemoJadePlugin` 提供 Jade provider；`ContentJeiPlugin` / `ContentCategory` 提供 JEI 分类与转移。

## 验收状态

这些是 Demo 的集成适配，不属于框架契约；运行行为、JEI 分类展示与 Jade 面板仍需游戏内验收。

## 相关

- 框架集成边界：[`docs/integration/README.md`](../../docs/integration/README.md)
- 公开兼容说明：[`docs-content/compatibility/`](../../docs-content/compatibility/)
