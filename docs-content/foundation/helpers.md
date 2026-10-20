---
sidebar_position: 6
---

# Helpers

库附带的消费者工具类——各策略实现里的常用杂务。

## 路径、资源标识与注册表键

| 类型 | 所表达的信息 | 使用场景 |
| --- | --- | --- |
| `String` | 由入口限定含义的文本或局部路径 | `registry.item("copper_ingot")`、`type.recipe("copper/to_dust")`、翻译键和名称 |
| `Identifier` | 完整的 `namespace:path`，不包含注册表身份 | 纹理位置、Topo 自有领域表 ID、进入已确定注册表的底层 API |
| `ResourceKey<T>` | 注册表身份与其中条目的完整 `Identifier` | `ResourceKey<Item>`、`ResourceKey<CreativeModeTab>`、`ResourceKey<Recipe<?>>` 的引用和匹配 |

配方声明路径由 `type.recipeKey(path)` 转成完整的 `ResourceKey<Recipe<?>>`；跨声明、加载与运行时匹配保留该键。
同一个 `Identifier` 可分别命名方块与物品，其注册表键不同。`Registries.ITEM` 本身是
`ResourceKey<Registry<Item>>`，标识注册表；具体物品用 `ResourceKey.create(Registries.ITEM, itemId)`。
翻译键是普通字符串，不能按 `Identifier` 的路径规则解析。

这些标识都不代表对象已存在或已绑定；取实例仍须通过已绑定的 Entry、注册表或当前世界的 lookup。
只有需要底层 API 的局部路径时才取 `getPath()`，不能拿它替代完整身份作跨命名空间比较。

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
`item()` 在物品未绑定时返回空，`requireItem()` 会抛异常。后处理器和 FollowUps 仍属于声明阶段：
标签使用 `itemId()`，需要物品实例的数据生成或运行时回调可保存 `materialItemSupplier()`，
但不能在当前阶段调用它的 `get()`。传递 Supplier 本身不保证接收方延迟求值。
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

| 类/方法                                                     | 用途                           |
|-------------------------------------------------------------|--------------------------------|
| `IdHelper.id(path)`                                         | topo 命名空间 id 铸造          |
| `MaterialHelper.item(material, form)`                       | 表单物品解析（含覆盖目标）     |
| `MaterialHelper.itemId(material, form)`                     | 物品注册 id（含覆盖分支）      |
| `MaterialHelper.itemPath(material, form)`                   | 注册路径                       |
| `MaterialHelper.requireItem(material, form)`                | 必须存在的物品                 |
| `ResourceFilterHelper.itemTag(tag)` / `.fluidTag(tag)`      | 标签过滤                       |
| `ResourceFilterHelper.itemExact(resource)`                  | 精确物品过滤                   |
| `ResourceFilterHelper.any()`                                | 放行一切                       |
| `TagHelper.item(group)` / `.block(group)` / `.fluid(group)` | c: 标签 key                    |
| `TagHelper.itemMaterial(group, material)`                   | 材质标签 key（c:copper_dusts） |
| `TopoCompactNumber.formatCompact(value)`                    | 大数字缩写                     |
| `TopoCompactNumber.formatCompactBuckets(mb)`                | 流体桶格式化                   |
