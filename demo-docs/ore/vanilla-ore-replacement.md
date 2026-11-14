# 原版矿石替换

面向 TopoDemo 维护者。本文说明 Demo 如何用 Topo 的数据生成与材料数据替换原版矿石。

## 使用的 Topo 能力

- `MaterialDataType`（Demo 的 `ORE_DROP`）：声明矿石掉落。
- RegistryLib / 原版数据生成：修改 configured feature 的 target。
- 材料形态生成：提供替换后的原生矿石方块。

## Demo 如何组合

`VanillaOreDatagenProvider` 把原版主世界矿石 configured feature 的 target 替换为 Topo 原生矿石方块；原版 placed feature 与生物群系分布保持不变，因此不需要重建矿脉分布。铜是例外：原版铜 placed feature 被移除，继续由 Demo 的自定义铜矿脉生成，避免重复生成。

原生矿石的掉落由 `ORE_DROP` 数据声明：粗金属掉 1–4 个粗矿，煤/宝石/红石等掉对应形态，没有 `ORE_DROP` 的矿石退回掉落自身。下界石英、下界金矿和远古残骸的 configured feature 也走同一替换流程。

## 验收状态

数据生成输出、世界生成与掉落仍需按验证文档检查；这是 Demo 的内容策略，不是 Topo 的默认行为。

## 相关

- 公开契约：[`docs-content/ore.md`](../../docs-content/ore.md)
- 框架内部：[`docs/ore/README.md`](../../docs/ore/README.md)
