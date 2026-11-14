# 矿脉与矿石方块

面向 TopoDemo 维护者。本文说明 Demo 如何用 Topo 的环境与矿脉契约声明矿石生成。

## 使用的 Topo 能力

- `OreEnvironment`、`OreDimensionRule`、`OreHostRule`：维度与宿主方块规则。
- `OreVein.Builder`、`OreVeinEntry`：矿脉成分、权重、高度与放置。
- `OreHostDataType`（Demo 数据类型）：记录矿脉可用的宿主集合。
- 形态生成与 `BlockForm.blockTag`：矿石方块与工具标签。

## Demo 如何组合

`BuiltinEnvironments` 声明维度/宿主规则，`BuiltinVeins` 注册锡、铅、镍、锌、铀矿脉；锡、铅和铀同时声明主世界与下界宿主。`OreHostDataType` 用宿主集合，矿石形态通过成员判断选择。矿石方块通过 [方块标签](../material/block-tags.md) 附加镐可挖掘与需铁工具标签。

## 验收状态

工具等级、掉落和实际世界生成仍需验收；矿脉的 feature 注册已迁入 Demo。

## 相关

- 显示：[矿脉 JEI 面板](vein-display.md)
- 公开契约：[`docs-content/ore.md`](../../docs-content/ore.md)
