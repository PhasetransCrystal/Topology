# 矿脉空气暴露策略

面向需要控制暴露于空气的矿石如何处理的模组开发者。`OreAirExposurePolicy.Strategy` 判断暴露位置是否丢弃，并提供配置概率的展示文本。

在 `registerOreFoundation` 中注册策略：

```java
OreAirExposurePolicy probabilistic = ore.airExposure("probabilistic", new OreAirExposurePolicy.Strategy() {
    @Override
    public boolean discardExposed(double discardChance, double randomSample) {
        return randomSample < discardChance;
    }

    @Override
    public Component describe(double discardChance) {
        return Component.literal("Probabilistic");
    }
});
```

在矿脉声明中使用 `.airExposure(probabilistic, 0.5)` 配置策略与概率。是否丢弃由策略决定，展示文本也由策略提供；示例文本可替换为本地化内容。

矿脉的其他声明见[矿脉注册](../ore.md)。
