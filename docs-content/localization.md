---
sidebar_position: 1
---

# Localization

面向需要为模组内容和界面提供本地化文本的开发者。Topology 提供双语显示名与强类型翻译键，
并将声明的文本接入数据生成（`zh_tw` 从 `zh_cn` 自动派生）。

## 关键概念

```
LangKey           ← 一个翻译键句柄（key + en + cn），注册后不可变
  ├── LangDomainRegistration ← 键铸造的唯一入口（key / resource / absolute 三种形态）
  ├── LangKeyFamily          ← 键分组（枚举族 / 派生族）
  ├── DisplayNames           ← 显示名模板（fixed / template）
  └── RegistryDisplayLang    ← 显示名 → RegistryLib 物品/方块 lang 的桥
```

- **普通键格式**：`lang.key` 生成 `category.modId.path`，调用方不把 modId 重复放进 path；`resource` 和 `absolute` 的规则见[键与键族](localization/keys-and-families.md)
- **冻结点**：`registerLang`（钩子 17）后 `LangRegistry.freeze()`，不可再注册
- **隐式 vs 显式**：大多数键由注册调用隐式创建（`.lang(...)` / `.displayName(...)`），只有自定义 UI 文本需要显式注册

## 页面导航

| 页面                                                 | 内容                                                        | 何时阅读            |
|------------------------------------------------------|-------------------------------------------------------------|---------------------|
| **Localization（本页）**                             | 隐式键、LangKey 句柄、生命周期                              | 理解机制            |
| [Keys & Families](localization/keys-and-families.md) | `LangDomainRegistration` 三种铸造形态、`LangKeyFamily` 分组 | 注册自定义 UI 文本  |
| [Display Names](localization/display-names.md)       | `DisplayNames` 模板、`RegistryDisplayLang` 桥               | 自定义物品/方块命名 |

## 基本用法

绝大多数翻译文本通过注册调用隐式创建，无需单独处理：

```java
// Material
m.material("copper").lang("Copper", "铜").build();          // → material.mymod.copper

// Machine（方块名经 RegistryLib 铸造为 block.*）
machine.machine("furnace").displayName("Furnace", "电炉").build(); // → block.mymod.furnace

// Ore
ore.vein("copper").lang("Copper Vein", "铜矿脉").build();    // → ore_vein.mymod.copper
```

## LangKey — 强类型翻译键

```java
LangKey key = material.nameLang();   // 获取已注册键

String en      = key.en();           // "Copper"
String cn      = key.cn();           // "铜"
String fullKey = key.key();          // "material.mymod.copper"

// 生成 Minecraft Component（带格式化参数）
MutableComponent msg = key.getComponent();     // Component.translatable(key)
```

## 生命周期

内容声明阶段和 `registerLang` 中都可以声明 `LangKey`；`registerLang` 完成后语言表冻结并导出到数据生成，冻结后不能再注册新键。物品、方块的注册表显示名由其 Builder 接入资源生成，见[显示名](localization/display-names.md)。

## API 速查

| 类/方法                             | 用途                        |
|-------------------------------------|-----------------------------|
| `LangKey.en()` / `.cn()` / `.key()` | 获取翻译文本                |
| `LangKey.getComponent(args...)`     | 生成 Component.translatable |
| `ChineseConvert.s2t(s)`             | 简→繁转换（仅 datagen）     |
