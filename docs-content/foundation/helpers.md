---
sidebar_position: 6
---

# Helpers

库附带的消费者工具类——各策略实现里的常用杂务。

## IdHelper

```java
// 库命名空间（topo）下的 id 铸造。你的内容 id 用自己插件的 machine().id(path)：
Identifier id = IdHelper.oi("by_distance");
```

## MaterialHelper

材质表单 → 物品解析（含覆盖目标）：

```java
Optional<Item> item = MaterialHelper.item(material, form);   // 立即解析；含 itemOverride 覆盖
Identifier itemId = MaterialHelper.itemId(material, form);  // 物品注册 id（含覆盖分支）
String itemPath = MaterialHelper.itemPath(material, form);  // 注册路径（"%s_ingot" 代入后）
Item required = MaterialHelper.requireItem(material, form); // 找不到就抛异常
```

:::caution
`item()` 在物品未绑定时返回空（例如后处理器 validate 阶段）。要拿到必须存在的物品用 `requireItem()`。
:::

## ResourceFilterHelper

端口内容过滤谓词（`ResourcePortMetadata.resourceFilter` 用）：

```java
Predicate<Resource> tag = ResourceFilterHelper.itemTag(TagKey<Item>);     // 物品标签
Predicate<Resource> exact = ResourceFilterHelper.itemExact(ItemResource); // 精确物品
Predicate<Resource> fluid = ResourceFilterHelper.fluidTag(TagKey<Fluid>); // 流体标签
Predicate<Resource> any = ResourceFilterHelper.any();                     // 放行一切
```

## TagHelper

常用标签 key 铸造：

```java
TagKey<Item> dusts = TagHelper.item("dusts");                       // c:dusts
TagKey<Item> copperDusts = TagHelper.itemMaterial("dusts", "copper"); // c:copper_dusts
TagKey<Block> ores = TagHelper.block("ores");                       // c:ores
TagKey<Fluid> acids = TagHelper.fluid("acids");                     // c:acids
```

## TopoCompactNumber

槽位角标格式化（大数字缩写）：

```java
String text = TopoCompactNumber.formatCompact(12500);            // 12.5k 级缩写
String wide = TopoCompactNumber.formatCompact(12500, /* maxLen */);   // 带长度上限
String mb = TopoCompactNumber.formatCompactBuckets(amountMb);     // 流体桶（mB）格式化
```

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `IdHelper.oi(path)`                    | topo 命名空间 id 铸造           |
| `MaterialHelper.item(material, form)`  | 表单物品解析（含覆盖目标）      |
| `MaterialHelper.itemId(material, form)`| 物品注册 id（含覆盖分支）       |
| `MaterialHelper.itemPath(material, form)` | 注册路径                     |
| `MaterialHelper.requireItem(material, form)` | 必须存在的物品            |
| `ResourceFilterHelper.itemTag(tag)` / `.fluidTag(tag)` | 标签过滤       |
| `ResourceFilterHelper.itemExact(resource)` | 精确物品过滤              |
| `ResourceFilterHelper.any()`           | 放行一切                        |
| `TagHelper.item(group)` / `.block(group)` / `.fluid(group)` | c: 标签 key     |
| `TagHelper.itemMaterial(group, material)` | 材质标签 key（c:copper_dusts）|
| `TopoCompactNumber.formatCompact(value)`| 大数字缩写                      |
| `TopoCompactNumber.formatCompactBuckets(mb)` | 流体桶格式化             |
