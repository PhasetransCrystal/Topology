# 方块标签

面向 TopoDemo 维护者。本文说明 Demo 如何通过材料形态策略为生成方块附加标签。

## 使用的 Topo 能力

- `MaterialFormStrategy` / `MaterialFormPlan`：材料形态的内容生成入口。
- `MaterialContentContext`：形态生成时可用的材料、RegistryCore 与注册上下文。
- `TagHelper` 与 RegistryLib 方块 Builder：把标签加入数据生成。

## Demo 如何组合

Demo 的 `BlockForm` 是材料形态策略，在生成方块时先加入形态默认的组标签/材料标签，再通过 `blockTag(TagKey<Block>)` 追加声明标签。`BuiltinMaterialForms` 用该入口为矿石方块声明 `MINEABLE_WITH_PICKAXE` 与 `NEEDS_IRON_TOOL`。

## 验收状态

该能力只负责把标签加入数据生成；工具等级与实际掉落仍须运行验收。

## 相关

- 矿脉宿主与矿石形态：[矿脉与矿石方块](../ore/veins-and-blocks.md)
- 框架内部：[`docs/material/README.md`](../../docs/material/README.md)
