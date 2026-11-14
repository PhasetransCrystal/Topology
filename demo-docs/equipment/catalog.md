# 装备目录

面向 TopoDemo 维护者。本文说明 Demo 如何用 Topo 的装备策略铸造工具与护甲。

## 使用的 Topo 能力

- `EquipmentStrategy`：`appliesTo(material)` 判断材料是否适用，`register(context, self)` 铸造物品。
- `EquipmentRegistry`：种类与“材料 × 种类”物品记录。
- `MachineItemBehavior`：装备右键机器时的可选行为。

## Demo 如何组合

Demo 在 `src/demo/java/net/ptcrys/topodemo/data/equipment/` 中声明 `BuiltinEquipment`、`BuiltinTools`、`BuiltinToolPanels`；装备策略读取材料数据，生成工具/护甲条目，并为调节器等装备提供机器交互行为。工具面板通过 Topo 的面板贡献接入悬浮信息。

## 验收状态

旧版装备目录尚未逐项核对迁移；当前入口存在，不能据此认定旧版装备已完整迁入。验收前须对照旧版目录检查装备条目、工具面板与行为。

## 相关

- 公开契约：[`docs-content/equipment.md`](../../docs-content/equipment.md)
- 框架内部：[`docs/equipment/README.md`](../../docs/equipment/README.md)
