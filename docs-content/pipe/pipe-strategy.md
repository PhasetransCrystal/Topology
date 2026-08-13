---
sidebar_position: 2
---

# PipeDistributionStrategy

策略决定一个抽取端口**这一 tick 往哪些目标送多少**。纯函数式：入参上下文（定义、配置、预算、目标列表），
出参是转移调用。

本页是 [Pipe](../pipe.md) 模块的子页——概念与页面导航见 [Pipe](../pipe.md)。

## 接口

```java
public interface PipeDistributionStrategy {

    Identifier id();                        // 持久化进端口配置——发布后不可改
    LangKey nameLang();
    LangKey descriptionLang();
    LangKey shortNameLang();                // 键路径：pipe.strategy.<ns>.<path>[.desc|.short]

    MapCodec<? extends PipePortStrategyConfig> configCodec();   // 配置持久化 codec
    PipePortStrategyConfig initialPortConfig(PipeDefinition definition, AggregationWindow aggregation);

    default int budget(PipeDefinition definition, PipePortStrategyConfig config) {
        return definition.maxExtractRate();
    }

    void distribute(PipeDistributionContext context);

    default void contributeConfigUi(PipePortUiCollector collector, PipePortAccess access) {}
}
```

## PipeDistributionContext

```java
// 目标已按最近优先排序：
PipeDefinition definition();
PipePortStrategyConfig config();
int budgetRemaining();                  // 本 tick 剩余预算
long sourceAvailable();                 // 源可用量
int destinationCount();
int destinationAcceptance(int index);   // 第 index 个目标还能收多少
int transfer(int index, int maxAmount); // 向目标实际转移
int cursor(); void setCursor(int index);// 跨 tick 轮询游标
```

```java
public final class GreedyStrategy implements PipeDistributionStrategy {
    // id/nameLang/descriptionLang/shortNameLang/configCodec/initialPortConfig ...

    @Override
    public void distribute(PipeDistributionContext context) {
        for (int i = 0; i < context.destinationCount() && context.budgetRemaining() > 0; i++) {
            int space = context.destinationAcceptance(i);
            if (space > 0) context.transfer(i, Math.min(context.budgetRemaining(), space));
        }
    }
}
```

## 生产级结构：注册时绑定语言键

生产级实现把**注册 + 语言键铸造**放在同一个静态方法里——`nameLang()`/`shortNameLang()`/
`descriptionLang()` 的键（`pipe.strategy.<ns>.<path>[.short|.desc]`）只在注册点铸造一次：

```java
public static final PipeDistributionStrategy GREEDY = register(
        new GreedyStrategy(Identifier.fromNamespaceAndPath("mymod", "greedy")),
        "Greedy", "贪婪",          // name en / cn
        "GRDY", "GRDY",            // short en / cn
        "Fill targets in order", "按序填充目标");   // desc en / cn

private static <T extends LangBoundStrategy> T register(
        T strategy,
        String en, String cn, String shortEn, String shortCn, String descEn, String descCn) {
    String baseKey = strategy.id().toLanguageKey("pipe.strategy");
    strategy.bindLang(
            lang.resource(strategy.id(), "pipe.strategy", en, cn),
            lang.absolute(baseKey + ".desc", descEn, descCn),
            lang.absolute(baseKey + ".short", shortEn, shortCn));
    PipeDistributionStrategies.register(strategy);
    return strategy;
}

/** 共享 name/desc/short 键绑定的策略基类。 */
private abstract static class LangBoundStrategy implements PipeDistributionStrategy {

    private LangKey nameLang;
    private LangKey descriptionLang;
    private LangKey shortNameLang;

    void bindLang(LangKey name, LangKey desc, LangKey shortKey) {
        this.nameLang = name;
        this.descriptionLang = desc;
        this.shortNameLang = shortKey;
    }

    @Override public LangKey nameLang() { return nameLang; }
    @Override public LangKey descriptionLang() { return descriptionLang; }
    @Override public LangKey shortNameLang() { return shortNameLang; }

    // 子类只实现 id() / configCodec() / initialPortConfig() / distribute()
}
```

## 注册

```java
// registerMachineFollowUps（PipeDistributionStrategies.freeze() 之前）：
PipeDistributionStrategies.register(new GreedyStrategy());
```

:::caution
策略在 `registerMachineFollowUps` 注册——引擎在 `Pipes.freeze()` 之后才冻结策略表。`id()` 持久化，
发布后不可改。
:::

## 挂到管道

```java
Pipes.register("item_pipe_t1")
        ...
        .strategy(GREEDY, new AggregationWindow(1, 16, 4, 4))   // minInterval, maxInterval, initialInterval, coarseStep
        .build();
```

## 贡献配置 UI — contributeConfigUi

策略可以为自己的配置参数提供 UI 行（端口配置屏自动收集）：

```java
default void contributeConfigUi(PipePortUiCollector collector, PipePortAccess access) {}
```

- `PipePortUiCollector.addRow(element)` / `rows()` — 行收集
- `PipePortUiCollector.caption(id, text)` / `row(id, children...)` / `labeledRow(id, caption, controls...)` — 行构建
- `PipePortUiCollector.intChoiceButton(id, text, selected, value, labelSink)` — 整数选择按钮
- `PipePortAccess.definition()` / `currentConfig()` / `updateConfig(UnaryOperator)` — 配置读写
- `PipePortAccess.bindInt(id, initial, serverGetter, serverSetter, clientApply)` — 双向整数值绑定

```java
@Override
public void contributeConfigUi(PipePortUiCollector collector, PipePortAccess access) {
    collector.addRow(collector.labeledRow("greedy_mode",
            collector.caption("greedy_mode_caption", Component.literal("Mode")),
            /* 你的控件 */));
}
```

管道注册与交互见 [Pipe](../pipe.md)。

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `PipeDistributionStrategy.id()`        | 持久化身份（发布后不可改）      |
| `nameLang()` / `.shortNameLang()` / `.descriptionLang()` | 三键     |
| `configCodec()` / `initialPortConfig(def, window)` | 配置持久化      |
| `distribute(context)`                  | 分发执行                        |
| `contributeConfigUi(collector, access)` | 策略配置 UI 行               |
| `PipeDistributionStrategies.register(strategy)` | 注册（钩子 12）        |
| `PipeDistributionContext.budgetRemaining()` / `.sourceAvailable()` | 预算/源量 |
| `context.destinationCount()` / `.destinationAcceptance(i)` | 目标状态    |
| `context.transfer(i, amount)`          | 向目标转移                      |
| `context.cursor()` / `.setCursor(i)`   | 跨 tick 轮询游标                |
