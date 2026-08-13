---
sidebar_position: 3
---

# Display Names

双语显示名的铸造与注册表桥——策略层接收产品表的裸 `(en, cn)` 字符串后，用本页的类型完成
「显示名 → 物品/方块语言条目」的闭环。

本页是 [Localization](../localization.md) 模块的子页——概念与页面导航见 [Localization](../localization.md)。

## 两种显示名

```java
// 完整标签（机器名、资源族名、UI 键）：无占位符
FixedDisplayName fixed = DisplayNames.fixed("Electric Furnace", "电炉");

// 模板（材质/部件按来源代入）：恰好一个 %s
TemplateDisplayName template = DisplayNames.template("%s Ingot", "%s锭");
```

- 双语言必填、不可为空白——`DisplayNames` 铸造时校验
- 模板构造时校验**恰好一个** `%s`
- `template.resolve(subject)` 代入 `DisplayNameSource`（材质/部件）的名字，产出 `FixedDisplayName`

:::caution
产品表**不得直接调用** `DisplayNames`——只在领域 API 处传裸 `(en, cn)` 字符串，由策略层负责铸造。
跨 API 边界传递构造好的 `FixedDisplayName` / `TemplateDisplayName` 违反约定。
:::

## RegistryDisplayLang — 注册表桥

策略在铸造物品/方块时，把显示名桥接到 RegistryLib 的语言条目（en + zh_cn + 自动 zh_tw）：

```java
// 物品：builder.lang(en) + datagen 时补 zh_cn / zh_tw
RegistryDisplayLang.applyItem(itemBuilder, core, entryPath, fixedName);

// 方块同理：
RegistryDisplayLang.applyBlock(blockBuilder, core, entryPath, fixedName);
```

zh_tw 由框架内部经 `ChineseConvert.s2t` 自动派生——桥使用者不碰转换。

## 完整链路示例

```java
// ① 产品表：只传裸字符串（MaterialDomainRegistration）
m.material("copper").lang("Copper", "铜").form(ingotForm).build();

// ② 策略内部：形态注册时铸造模板名
TemplateDisplayName name = DisplayNames.template("%s Ingot", "%s锭");

// ③ 策略内部：为每个材质代入并桥接
FixedDisplayName resolved = name.resolve(material);
RegistryDisplayLang.applyItem(
        core.item("copper_ingot"), core, "copper_ingot", resolved);
```

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `DisplayNames.fixed(en, cn)`           | 完整双语标签                    |
| `DisplayNames.template(en, cn)`        | 单 `%s` 模板（材质/部件代入）   |
| `TemplateDisplayName.resolve(subject)` | 代入来源名 → `FixedDisplayName` |
| `RegistryDisplayLang.applyItem(...)`   | 桥接物品语言条目（含 zh_tw）    |
| `RegistryDisplayLang.applyBlock(...)`  | 桥接方块语言条目                |
