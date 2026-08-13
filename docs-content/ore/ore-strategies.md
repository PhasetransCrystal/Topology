---
sidebar_position: 2
---

# Ore Strategies

矿脉系统有四个可继承的策略接口：**形状**（几何）、**模式**（生成通道）、**空气暴露**、**冲突**。
全部在 `registerOreFoundation` 注册。

本页是 [Ore](../ore.md) 模块的子页——概念与页面导航见 [Ore](../ore.md)。

## OreVeinShape — 几何包含测试

```java
@FunctionalInterface
public interface Strategy { boolean contains(int dx, int dy, int dz, int radius); }
// 以矿脉中心为原点；radius 由矿脉声明提供

ore.shape("oval", (dx, dy, dz, radius) ->
        dx * dx + dy * dy + dz * dz <= radius * radius);
```

## OreVeinMode — 生成通道

```java
public interface Strategy {
    void validate(OrePlacement placement);      // 拒绝不属于本通道的放置配置
    void collectGrid(OreVein vein, OreVeinCollector collector);     // 网格通道：写入每 chunk 确定性放置计划
    void collectFeature(OreVein vein, OreVeinCollector collector);  // feature 通道：交给原版 feature 数据生成桥
}
```

`OrePlacement` 是 sealed 接口（`Feature` / `Grid` 两种）——validate 用 `instanceof` 区分。
模式必须与显示成对：`registerOreDisplays` 里 `ore.display(modeId, strategy)`（引擎校验每种模式都有显示）。

```java
ore.mode("stone_replace", new OreVeinMode.Strategy() {
    @Override public void validate(OrePlacement placement) {
        if (!(placement instanceof OrePlacement.Feature)) throw new IllegalArgumentException(...);
    }
    @Override public void collectGrid(OreVein vein, OreVeinCollector collector) { /* 不参与 */ }
    @Override public void collectFeature(OreVein vein, OreVeinCollector collector) { collector.add(vein); }
});
```

## OreAirExposurePolicy — 空气暴露

```java
public interface Strategy {
    boolean discardExposed(double discardChance, double randomSample);
    Component describe(double discardChance);   // JEI / 信息卡标签
}

ore.airExposure("probabilistic", new OreAirExposurePolicy.Strategy() {
    @Override public boolean discardExposed(double discardChance, double randomSample) {
        return randomSample < discardChance;
    }
    @Override public Component describe(double discardChance) { return Component.literal("Probabilistic"); }
});
```

## OreConflictPolicy — 矿脉冲突

```java
public interface Strategy {
    boolean canReplace(int candidatePriority, int existingPriority);
}

ore.conflict("replace_lower", (candidate, existing) -> candidate >= existing);
```

## OreVeinDisplay — 矿脉显示（JEI 预览）

每种模式必须有一个对应显示（引擎在 `OreVeinDisplays.freeze()` 后校验），注册细节见
[Ore Display](ore-display.md)。

## 注册时机速查

| 策略 | 注册入口 | 钩子 |
|------|----------|------|
| 形状 | `ore.shape(path, strategy)` | 13 registerOreFoundation |
| 模式 | `ore.mode(path, strategy)` | 13 |
| 空气暴露 | `ore.airExposure(path, strategy)` | 13 |
| 冲突 | `ore.conflict(path, strategy)` | 13 |
| 显示 | `ore.display(modeId, strategy)` | 14 registerOreDisplays |

矿脉声明与环境构造见 [Ore](../ore.md)。

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `ore.shape(path, strategy)`            | 注册矿脉形状（钩子 13）         |
| `ore.mode(path, strategy)`             | 注册生成通道                    |
| `ore.airExposure(path, strategy)`      | 注册空气暴露策略                |
| `ore.conflict(path, strategy)`         | 注册冲突策略                    |
| `OreVeinShape.Strategy.contains(dx, dy, dz, radius)` | 几何包含测试   |
| `OreVeinMode.Strategy.validate(placement)` | 通道校验（sealed 区分）    |
| `OreVeinMode.Strategy.collectGrid(vein, collector)` | 网格通道收集        |
| `OreVeinMode.Strategy.collectFeature(vein, collector)` | feature 通道收集  |
| `OreAirExposurePolicy.Strategy.discardExposed(chance, sample)` | 暴露判定 |
| `OreAirExposurePolicy.Strategy.describe(chance)` | 标签            |
| `OreConflictPolicy.Strategy.canReplace(candidate, existing)` | 冲突判定 |
