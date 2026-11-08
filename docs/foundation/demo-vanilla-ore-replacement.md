# TopoDemo 原版矿石替换

面向框架开发者；这是 Demo 的内容策略，不是 Topology 对下游模组的默认行为。


TopoDemo 的数据生成会把原版主世界矿石 configured feature 的 target 替换为 Topo 原生矿石方块；
原版 placed feature 与生物群系分布保持不变，因此不需要重建矿脉分布。铜是例外：原版铜
placed feature 被移除，继续由 Demo 的自定义铜矿脉生成，避免重复生成。

原生矿石的掉落由 `ORE_DROP` 数据声明：粗金属掉 1–4 个粗矿，煤/宝石/红石等掉对应形态，
没有 `ORE_DROP` 的矿石退回掉落自身。下界石英、下界金矿和远古残骸的 configured feature 也走同一替换流程。

