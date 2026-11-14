---
sidebar_position: 2
---

# PipeDistributionStrategy

面向需要为管道定义分发策略的模组开发者。策略决定一个抽取端口的当前批次向哪些目标转移多少资源；
它通过上下文发起实际转移，并可维护本会话内的轮询游标。

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
int budgetRemaining();                  // 本批剩余预算
int sourceAvailable();                  // 本批源可用量
int destinationCount();
int destinationAcceptance(int index);   // 第 index 个目标还能收多少
int transfer(int index, int maxAmount); // 向目标实际转移
int cursor(); void setCursor(int index);// 跨 tick 轮询游标
```

```java
public final class GreedyStrategy extends LangBoundStrategy {
    private final Identifier id;

    public GreedyStrategy(Identifier id) { this.id = id; }
    @Override public Identifier id() { return id; }
    // 还需实现 configCodec 和 initialPortConfig；语言键由下方基类提供。

    @Override
    public void distribute(PipeDistributionContext context) {
        for (int i = 0; i < context.destinationCount() && context.budgetRemaining() > 0; i++) {
            int space = context.destinationAcceptance(i);
            if (space > 0) context.transfer(i, Math.min(context.budgetRemaining(), space));
        }
    }
}
```

`transfer` 返回实际转移量，可能小于请求量；它会扣减本批剩余预算。
按比例分配时，先用 `min(budgetRemaining(), sourceAvailable())` 确定可分配总量，避免源资源不足时首个目标占用全部资源。

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

在 `registerMachineFollowUps` 中调用上面的 `register(...)`，并保存返回的 `GREEDY` 句柄。
该辅助方法已经调用 `PipeDistributionStrategies.register(strategy)`，不要再次注册相同策略。

:::caution
策略在 `registerMachineFollowUps` 注册——引擎先冻结策略表，再执行 `Pipes.freeze()`。`id()` 持久化，
发布后不可改。
:::

## 挂到管道

```java
plugin.machine().pipe("item_pipe_t1")
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

| 类/方法                                                            | 用途                       |
|--------------------------------------------------------------------|----------------------------|
| `PipeDistributionStrategy.id()`                                    | 持久化身份（发布后不可改） |
| `nameLang()` / `.shortNameLang()` / `.descriptionLang()`           | 三键                       |
| `configCodec()` / `initialPortConfig(def, window)`                 | 配置持久化                 |
| `distribute(context)`                                              | 分发执行                   |
| `contributeConfigUi(collector, access)`                            | 策略配置 UI 行             |
| `PipeDistributionStrategies.register(strategy)`                    | 注册（钩子 11）            |
| `PipeDistributionContext.budgetRemaining()` / `.sourceAvailable()` | 预算/源量                  |
| `context.destinationCount()` / `.destinationAcceptance(i)`         | 目标状态                   |
| `context.transfer(i, amount)`                                      | 向目标转移                 |
| `context.cursor()` / `.setCursor(i)`                               | 跨 tick 轮询游标           |
