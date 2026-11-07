---
sidebar_position: 3
---

# Equipment Tooltips

装备物品的悬浮面板——耐久、功能说明行与勘测范围。本页覆盖面板构建与注册。

本页是 [Equipment](../equipment.md) 模块的子页——概念与页面导航见 [Equipment](../equipment.md)。

## 功能说明行 — 在面板中构建

装备工具提示没有独立的 `tooltipLine(...)` 声明 API。功能说明行由你在
`registerRuntime` 注册的 `TopoTooltipUiProvider` 面板中构建，文本用 `LangKey` 铸造；
可参考 Demo 的 `ToolHintPanel` 与 `BuiltinToolPanels`。

## 面板构建 — ItemTooltipUis 注册

面板是 LCD 结构：耐久行（可损耗才有）+ 功能区（父行 `Function | 行数` + 每条说明一行缩进子项）。
在插件的 `registerRuntime(RuntimeDomainRegistration runtime)` 中为本插件生成的装备物品注册（此时物品已经绑定）：

```java
for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
    if (!record.material().id().getNamespace().equals(modId())) continue;
    runtime.tooltip(record.entry().get(), new TopoTooltipUiProvider() {
        @Override
        public UIElement build(ItemStack stack) {
            // 耐久行（可损耗才有）+ 每条功能说明一条缩进子项
            return createPanel(stack, /* 你的策略 */);
        }

        @Override
        public Object cacheKey(ItemStack stack) {
            return stack.getDamageValue();   // 耐久变化自动重建面板
        }
    });
}
```

## 固定标签键

面板骨架的固定标签（耐久/功能/范围）在装备域语言表里集中声明：

```java
TOOLTIP_EQUIPMENT_DURABILITY = lang.key("tooltip", "equipment.durability", "Durability", "耐久");
TOOLTIP_EQUIPMENT_FUNCTION   = lang.key("tooltip", "equipment.function", "Function", "功能");
TOOLTIP_EQUIPMENT_RANGE      = lang.key("tooltip", "equipment.range", "Range", "范围");
TOOLTIP_EQUIPMENT_RANGE_BLOCKS = lang.key("tooltip", "equipment.range_blocks", "%s blocks", "%s 格");
```

:::caution
面板在 common setup 的主线程任务中注册，`ItemTooltipUis` 在 load complete 冻结。
插件不能自行冻结共享表，也不能把声明语言键推迟到运行时绑定。
面板框架细节见 [Tooltip Panels](../foundation/tooltip-panels.md)。
:::

## API 速查

| 类/方法                                            | 用途                       |
|----------------------------------------------------|----------------------------|
| `ItemTooltipUis.register(item, provider)`          | 按物品注册面板             |
| `TopoTooltipUiProvider.build(stack)`               | 构建面板元素树             |
| `TopoTooltipUiProvider.cacheKey(stack)`            | 缓存键（耐久变化用损伤值） |
