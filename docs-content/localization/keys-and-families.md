---
sidebar_position: 2
---

# Keys & Families

自定义 UI 文本的注册——`LangDomainRegistration` 的三种铸造形态与 `LangKeyFamily` 分组。

本页是 [Localization](../localization.md) 模块的子页——概念与页面导航见 [Localization](../localization.md)。

## 铸造形态一：key — 嵌套路径

```java
// 注册 category.modId.path → (en, cn)。category 不含点，path 可含点嵌套：
LangKey idle = lang.key("ui", "recipe.state.idle", "Idle", "空闲");
// → ui.mymod.recipe.state.idle
```

## 铸造形态二：resource — 注册项派生

从已注册项（材质、形态、矿脉、管道策略）的 id 派生键——id 命名空间必须匹配插件 modId：

```java
LangKey strategyName = lang.resource(strategyId, "pipe.strategy", "Round Robin", "轮询");
// → pipe.strategy.mymod.round_robin（经 Identifier.toLanguageKey）
```

## 铸造形态三：absolute — 外部约定

第三方集成（如 Jade 配置 id）要求的非标准键形——完整键必须仍包含插件 modId：

```java
LangKey jadeConfig = lang.absolute("config.jade.plugin_mymod.pipe", "Pipe Network", "管道网络");
```

## LangKeyFamily — 分组

### 枚举族

每个枚举常量一条键，`pathFn/enFn/cnFn` 参数化：

```java
LangKeyFamily<SideIoMode> modes = LangKeyFamily.ofEnum(
        lang,
        "ui",
        SideIoMode.class,
        mode -> "side_io.mode." + mode.getSerializedName(),   // pathFn
        mode -> switch (mode) {                               // enFn
            case NONE -> "Closed";
            case INSERT -> "Input";
            case EXTRACT -> "Output";
            case BOTH -> "Input + Output";
        },
        mode -> switch (mode) {                               // cnFn
            case NONE -> "关闭";
            case INSERT -> "输入";
            case EXTRACT -> "输出";
            case BOTH -> "输入 + 输出";
        });

LangKey insert = modes.get(SideIoMode.INSERT);   // 按枚举取值；未知成员抛异常
```

### 派生族

从已冻结的注册表派生（在源表冻结后调用）：

```java
LangKeyFamily<Material> formNames = LangKeyFamily.derived(
        lang,
        "form",
        MaterialRegistry.registered(),
        material -> material.id().getPath(),
        material -> material.displayNameEn(),
        material -> material.displayNameCn());
```

:::caution
`LangKeyFamily.ofEnum` 可在任何 `registerLang` 之前的阶段调用；`derived` 必须在源注册表冻结后
（例如 `MaterialRegistry.freeze()` 之后的钩子里）。
:::

## 固定标签键

面板骨架的固定标签（如装备耐久/功能行）集中在一个语言表类里声明，供多个面板复用：

```java
public final class MyUiLang {

    private static final LangDomainRegistration LANG = MyModPlugin.INSTANCE.lang();

    public static final LangKey TOOLTIP_DURABILITY =
            LANG.key("tooltip", "equipment.durability", "Durability", "耐久");
    public static final LangKey TOOLTIP_FUNCTION =
            LANG.key("tooltip", "equipment.function", "Function", "功能");

    private MyUiLang() {}

    public static void init() {}   // 类加载即注册；在 registerLang 里调用 init() 保证时序
}
```

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `lang.key(category, path, en, cn)`     | 嵌套路径键 → `category.modId.path` |
| `lang.resource(id, category, en, cn)`  | 注册项派生键（id 命名空间须匹配）|
| `lang.absolute(fullKey, en, cn)`       | 外部约定键形（须含 modId）      |
| `LangKeyFamily.ofEnum(lang, category, type, pathFn, enFn, cnFn)` | 枚举族 |
| `LangKeyFamily.derived(lang, category, sources, pathFn, enFn, cnFn)` | 派生族（源表冻结后） |
| `family.get(k)`                        | 取值（未知成员抛异常）          |
