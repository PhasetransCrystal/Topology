# 矿脉 JEI 面板

面向 TopoDemo 维护者。本文说明 Demo 如何实现 Topo 的矿脉显示契约。

## 使用的 Topo 能力

- `OreVeinDisplay.Strategy.buildPreview(vein)`：为 JEI 构建矿脉预览。
- `OreVein`、`OrePlacement`：读取成分、宿主、高度与放置参数。
- `MachineUiLayout` / `MachineUiContainerTemplate` / `MachineUiComponentTemplate`：构建 LDLib2 面板与物品槽。
- `ItemSlot.JEISupport`：向 JEI 提供产物。

## Demo 如何组合

`OreVeinPanels` 是对象单例，按 `OrePlacement` 的 `Feature` / `Grid` 分支选择预览元素；信息卡展示维度、高度、宿主、大小、间距与暴露策略；成分列表按权重排序并向 JEI 提供各宿主规则可用的矿石形态。`BuiltinVeinDisplays` 把同一个 builder 注册给 feature 与 grid 两个模式，`BuiltinOreDisplayLang` 提供标签键。

## 验收状态

面板布局、多宿主产物索引与 JEI 展示仍需游戏内验收。

## 相关

- 矿脉声明：[矿脉与矿石方块](veins-and-blocks.md)
- 公开契约：[`docs-content/ore/ore-display.md`](../../docs-content/ore/ore-display.md)
