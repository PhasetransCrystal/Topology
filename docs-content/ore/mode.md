# 矿脉生成模式

面向需要定义矿脉生成通道的模组开发者。`OreVeinMode.Strategy` 校验矿脉的放置配置，并将矿脉交给相应的网格规划或 feature 数据生成通道。

在 `registerOreFoundation` 中注册模式。例如，以下模式只接收 feature 配置：

```java
OreVeinMode featureMode = ore.mode("stone_replace", new OreVeinMode.Strategy() {
    @Override
    public void validate(OrePlacement placement) {
        if (!(placement instanceof OrePlacement.Feature)) {
            throw new IllegalArgumentException("Expected feature placement");
        }
    }

    @Override
    public void collectFeature(OreVein vein, OreVeinCollector collector) {
        collector.add(vein);
    }
});
```

`OrePlacement` 有 `Feature` 和 `Grid` 两种配置。网格模式在 `collectGrid` 中收集矿脉；不使用的通道可以保留默认空实现。注册后在对应矿脉的 `.feature(...)` 或 `.grid(...)` 中引用句柄。

每个已注册模式必须在 `registerOreDisplays` 中配置对应的[矿脉显示](ore-display.md)。feature 通道还需在 `registerOreDatagen` 中安装 `ore.featureDatagen()`；完整声明见[矿脉注册](../ore.md)。
