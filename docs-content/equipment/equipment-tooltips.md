---
sidebar_position: 3
---

# Equipment Tooltips

装备物品的悬浮面板——耐久、功能说明行与勘测范围。本页覆盖面板构建与注册。

本页是 [Equipment](../equipment.md) 模块的子页——概念与页面导航见 [Equipment](../equipment.md)。

## 功能说明行 — tooltipLine

策略 Builder 的 `tooltipLine(langKey, nameEn, nameCn, descEn, descCn)` 声明一条「功能名 | 说明」行。
语言键在注册点铸造（`tooltip.<modid>.<langKey>.name` / `.desc`），调用方绝不拼字符串：

```java
equipment.kind("wrench",
        ToolEquipment.builder()
                .registryPath("%s_wrench")
                .displayName("%s Wrench", "%s扳手")
                .tooltipLine("equipment.wrench.machines", "Dismantle", "拆卸", "Fast & safe", "快速且安全")
                .tooltipLine("equipment.wrench.ports", "Ports", "端口", "Right-click to configure", "右键点击以配置")
                .build());
```

每条说明行是 `EquipmentTooltipLine(nameLang, descriptionLang)` 记录——名称与描述键在注册时一次铸造，
面板渲染只读句柄。

## 面板构建 — ItemTooltipUis 注册

面板是 LCD 结构：耐久行（可损耗才有）+ 功能区（父行 `Function | 行数` + 每条说明一行缩进子项）。
物品绑定后（`FMLCommonSetup.enqueueWork`）为每个装备物品注册：

```java
for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
    ItemTooltipUis.register(record.entry().get(), new TopoTooltipUiProvider() {
        @Override
        public UIElement build(ItemStack stack) {
            // 耐久行（可损耗才有）+ 每个 tooltipLine 一条缩进子项
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
面板注册必须**先于** `PipeSpecTooltips.register(...)`——它的 setup 任务会 freeze `ItemTooltipUis`，
而 `enqueueWork` 任务按提交序执行。面板框架细节见
[Tooltip Panels](../foundation/tooltip-panels.md)。
:::

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `Builder.tooltipLine(key, en, cn, descEn, descCn)` | 声明功能说明行       |
| `ItemTooltipUis.register(item, provider)` | 按物品注册面板              |
| `TopoTooltipUiProvider.build(stack)`   | 构建面板元素树                  |
| `TopoTooltipUiProvider.cacheKey(stack)`| 缓存键（耐久变化用损伤值）      |
