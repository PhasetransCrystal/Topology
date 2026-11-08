---
sidebar_position: 2
---

# Ore Strategies

面向需要扩展矿脉生成行为的模组开发者。矿脉系统提供四类策略：**形状**（几何）、**模式**（生成通道）、**空气暴露**、**冲突**。
全部在 `registerOreFoundation` 注册。

本页是 [Ore](../ore.md) 模块的子页——概念与页面导航见 [Ore](../ore.md)。

## OreVeinShape — 几何包含测试

判断矿脉局部坐标是否位于网格矿体内；使用方法见[矿脉形状](shape.md)。

## OreVeinMode — 生成通道

校验放置配置并将矿脉交给网格或 feature 通道；使用方法见[矿脉生成模式](mode.md)。

## OreAirExposurePolicy — 空气暴露

决定暴露位置是否丢弃，并提供显示文本；使用方法见[空气暴露策略](air-exposure.md)。

## OreConflictPolicy — 矿脉冲突

决定网格矿脉重叠时能否替换已有规划；使用方法见[矿脉冲突策略](conflict.md)。

## OreVeinDisplay — 矿脉显示（JEI 预览）

每种模式必须有一个对应显示（引擎在 `OreVeinDisplays.freeze()` 后校验），注册细节见
[Ore Display](ore-display.md)。

## 注册时机速查

| 策略     | 注册入口                          | 钩子                     |
|----------|-----------------------------------|--------------------------|
| 形状     | `ore.shape(path, strategy)`       | 12 registerOreFoundation |
| 模式     | `ore.mode(path, strategy)`        | 12                       |
| 空气暴露 | `ore.airExposure(path, strategy)` | 12                       |
| 冲突     | `ore.conflict(path, strategy)`    | 12                       |
| 显示     | `ore.display(modeId, strategy)`   | 13 registerOreDisplays   |

矿脉声明与环境构造见 [Ore](../ore.md)。

## API 速查

| 类/方法                                                        | 用途                    |
|----------------------------------------------------------------|-------------------------|
| `ore.shape(path, strategy)`                                    | 注册矿脉形状（钩子 12） |
| `ore.mode(path, strategy)`                                     | 注册生成通道            |
| `ore.airExposure(path, strategy)`                              | 注册空气暴露策略        |
| `ore.conflict(path, strategy)`                                 | 注册冲突策略            |
| `OreVeinShape.Strategy.contains(dx, dy, dz, radius)`           | 几何包含测试            |
| `OreVeinMode.Strategy.validate(placement)`                     | 通道校验（sealed 区分） |
| `OreVeinMode.Strategy.collectGrid(vein, collector)`            | 网格通道收集            |
| `OreVeinMode.Strategy.collectFeature(vein, collector)`         | feature 通道收集        |
| `OreAirExposurePolicy.Strategy.discardExposed(chance, sample)` | 暴露判定                |
| `OreAirExposurePolicy.Strategy.describe(chance)`               | 标签                    |
| `OreConflictPolicy.Strategy.canReplace(candidate, existing)`   | 冲突判定                |
