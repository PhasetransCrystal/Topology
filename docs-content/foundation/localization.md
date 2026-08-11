---
sidebar_position: 1
---

# Localization

Topology 为所有用户可见文本提供 **强类型双语翻译键**。注册项自带 `en` + `cn` 文本，框架自动收集、冻结、桥接到数据生成管线。

## 何时阅读

需要理解 `.lang("English", "中文")` 背后机制，或需要注册自定义 UI 文本时。

## 基本用法

绝大多数翻译文本通过注册调用隐式创建，无需单独处理：

```java
// Material
m.material("copper").lang("Copper", "铜").build();          // → material.mymod.copper

// Machine
m.begin(id, registry).displayName("Furnace", "电炉").build(); // → machine.mymod.furnace

// Ore
ore.vein("copper").lang("Copper Vein", "铜矿脉").build();    // → ore.mymod.copper_vein
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

## LangKeyFamily — 分组自定义文本

当需要注册非自动生成的 UI 文本时：

```java
LangKeyFamily family = LangKeyFamily.of("machine.mymod.electric_furnace");
family.add("tooltip.energy", "Energy Stored: %s EU", "已存储能量: %s EU");
family.add("tooltip.status",  "Status: %s",          "状态: %s");
family.add("button.start",    "Start",               "启动");
```

生成翻译键：

```
machine.mymod.electric_furnace.tooltip.energy → "已存储能量: %s EU"
machine.mymod.electric_furnace.tooltip.status → "状态: %s"
machine.mymod.electric_furnace.button.start   → "启动"
```

## 繁简转换

```java
String tw = ChineseConvert.toTraditional("铜矿脉");  // → "銅礦脈"
```

适用于自动生成繁体中文语言文件。

## 生命周期

所有 `LangKey` 在 `registerLang` 阶段统一收集 → 冻结 → 桥接到 RegistryLib 数据生成。冻结后不可注册新键。

## API 速查

| 类/方法                             | 用途                        |
|-------------------------------------|-----------------------------|
| `LangKey.en()` / `.cn()` / `.key()` | 获取翻译文本                |
| `LangKey.getComponent(args...)`     | 生成 Component.translatable |
| `LangKeyFamily.of(prefix)`          | 创建翻译键分组              |
| `LangKeyFamily.add(path, en, cn)`   | 添加一条翻译                |
| `ChineseConvert.toTraditional(s)`   | 简→繁转换                   |
