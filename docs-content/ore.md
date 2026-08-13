---
sidebar_position: 1
---

# Ore

声明式的矿石世界生成框架。矿脉引用 Material 系统中注册的材料，支持 **Feature**（小型分散）和 **Grid**（大型网格）两种生成模式。

## 关键概念

```
OreVein             ← 一条矿脉（材料成分 + 高度 + 生成参数）
  ├── OreEnvironment     ← 生成环境（维度规则 + 宿主方块规则）
  ├── OreVeinMode        ← 生成通道（feature 散布 / grid 确定性网格）
  ├── OreVeinShape       ← grid 矿体的几何形状
  ├── OreAirExposurePolicy / OreConflictPolicy ← 暴露与冲突规则
  └── OreVeinDisplay     ← JEI 预览渲染器
```

## 页面导航

| 页面 | 内容 | 何时阅读 |
|------|------|----------|
| [Ore Strategies](ore/ore-strategies.md) | 形状/模式/暴露/冲突四个策略接口 | 需要新矿体几何或生成通道 |
| [Ore Display](ore/ore-display.md) | `OreVeinDisplay` JEI 预览（每模式必配） | 注册新生成模式后 |
| **Ore（本页）** | 环境构造、矿脉声明（feature/grid） | 添加矿石生成 |

:::info
库不内置任何环境、模式或形状常量。本章的 `registerOreFoundation` 示例声明了全部前置注册项，照抄即可。
:::

## 前置注册：环境 / 模式 / 形状 / 策略

这些注册项在 `registerOreFoundation` 阶段声明一次，之后所有矿脉引用它们：

```java
@Override
public void registerOreFoundation(OreDomainRegistration ore) {
    // ① 环境：主世界 + 石头/深板岩两种宿主。每个宿主规则把可替换方块标签映射到对应矿石形态。
    ore.shape("oval", (dx, dy, dz, radius) ->
            dx * dx + dy * dy + dz * dz <= radius * radius);           // 球形包含测试

    // ② 模式：feature 散布通道（替换石类方块，size 个方块、每 chunk attempts 次尝试）
    ore.mode("stone_replace", new OreVeinMode.Strategy() {
        @Override
        public void validate(OrePlacement placement) {
            // 只接受 feature 放置配置，拒绝 grid 配置
        }

        @Override
        public void collectGrid(OreVein vein, OreVeinCollector collector) {
            // grid 通道：不参与
        }

        @Override
        public void collectFeature(OreVein vein, OreVeinCollector collector) {
            collector.add(vein);   // 交给原版 feature 数据生成桥
        }
    });

    // ③ 空气暴露策略：永不丢弃 / 总是丢弃 / 概率丢弃
    ore.airExposure("allow", new OreAirExposurePolicy.Strategy() {
        @Override public boolean discardExposed(double discardChance, double randomSample) { return false; }
        @Override public Component describe(double discardChance) { return Component.literal("Exposed"); }
    });
    ore.airExposure("probabilistic", new OreAirExposurePolicy.Strategy() {
        @Override public boolean discardExposed(double discardChance, double randomSample) { return randomSample < discardChance; }
        @Override public Component describe(double discardChance) { return Component.literal("Probabilistic"); }
    });

    // ④ 冲突策略：保留高优先级 / 替换低优先级
    ore.conflict("preserve_higher", (candidate, existing) -> candidate > existing);
    ore.conflict("replace_lower", (candidate, existing) -> candidate >= existing);

    // ⑤ 环境：维度规则 + 宿主规则。显示名 LangKey 通过 plugin.lang().key(...) 铸造。
    OreDimensionRule overworld = new OreDimensionRule(
            Level.OVERWORLD,
            BiomeTags.IS_OVERWORLD,
            plugin.lang().key("ore", "dimension.overworld", "Overworld", "主世界"));
    OreHostRule stoneHost = new OreHostRule(
            BlockTags.STONE_ORE_REPLACEABLES,
            oreForm,    // 石头里生成时用你的 "ore" MaterialForm
            plugin.lang().key("ore", "host.stone", "Stone", "石头"));
    OreHostRule deepslateHost = new OreHostRule(
            BlockTags.DEEPSLATE_ORE_REPLACEABLES,
            deepslateOreForm,
            plugin.lang().key("ore", "host.deepslate", "Deepslate", "深板岩"));

    overworldStone = OreEnvironments.of(overworld, stoneHost);
    overworldStoneAndDeepslate = OreEnvironments.of(overworld, stoneHost, deepslateHost);
}
```

:::note
**环境构造** — `OreEnvironment` 由维度规则（`OreDimensionRule`：维度 key + 生物群系标签 + 显示名）与宿主规则
（`OreHostRule`：可替换方块标签 + 生成的矿石形态 + 显示名）组成。维度规则用
`plugin.lang().key(...)` 铸造显示名；`BlockTags.STONE_ORE_REPLACEABLES` 与
`BlockTags.DEEPSLATE_ORE_REPLACEABLES` 是原版提供的现成标签。
:::

## Feature 模式 — 小型分散矿脉

```java

@Override
public void registerOreVeins(OreDomainRegistration ore) {
    ore.vein("copper_vein")
        .lang("Copper Vein", "铜矿脉")
        .environment(overworldStone)                  // registerOreFoundation 里构造的环境
        .feature(stoneReplace, 33, 16)                // mode, size, attemptsPerChunk
        .entry(MaterialRegistry.require(id("copper")), 80)
        .entry(MaterialRegistry.require(id("iron")), 20)
        .triangularHeight(-16, 112)
        .airExposure(probabilisticDiscard, 0.5)
        .build();
}
```

### Feature 参数

| 参数                          | 说明                                     |
|-------------------------------|------------------------------------------|
| `mode`                        | 生成通道模式（你自定义的 `OreVeinMode`） |
| `size`                        | 矿脉最大方块数                           |
| `attemptsPerChunk`            | 每 chunk 生成尝试次数                    |
| `height(min, max)`            | 均匀分布高度                             |
| `triangularHeight(min, max)`  | 三角分布（中部更密集）                   |
| `airExposure(policy, chance)` | 空气暴露策略 + 丢弃概率                  |
## Grid 模式 — 大型网格矿脉

```java
ore.vein("iron_grid")
    .lang("Iron Grid Vein","铁网格矿脉")
    .environment(overworldStoneAndDeepslate)
    .grid(stoneReplaceGrid, ovalShape)              // mode, shape
    .radius(32)
    .spacing(3, 0)              // gridSizeChunks, randomOffsetBlocks
    .density(0.7)
    .weight(100)
    .priority(1)
    .conflict(replaceLower)
    .entry(MaterialRegistry.require(id("iron")), 100)
    .entry(MaterialRegistry.require(id("nickel")), 10)
    .height(-64, 64)
    .airExposure(allow, 0)
    .build();
```

### Grid 参数

| 参数                          | 说明                                 |
|-------------------------------|--------------------------------------|
| `shape`                       | 矿脉形状（你自定义的 `OreVeinShape`） |
| `radius`                      | 矿脉半径（blocks）                   |
| `spacing(gridChunks, offset)` | 最小 chunk 间距 + 随机偏移（blocks） |
| `density`                     | 0–1，矿脉内部填充密度                |
| `weight`                      | 同区域竞争时的随机权重               |
| `priority`                    | 冲突时高优先覆盖低优                 |
| `conflict`                    | 冲突策略（你自定义的 `OreConflictPolicy`） |

## 查询矿脉

```java
// 查找包含某个材料的所有矿脉
List<OreVein> veins = OreVeins.byMaterial(MaterialRegistry.require(id("copper")));

for (OreVein vein : veins) {
    System.out.println(vein.displayName());
    System.out.println("Y: " + vein.minY() + "~" + vein.maxY());

    for (OreVeinEntry entry : vein.entries()) {
        System.out.println(entry.material().id() + " × " + entry.weight());
    }
}

// 遍历所有矿脉
OreVeins.view().forEach(v -> System.out.println(v.id()));
```

## API 速查

| 类/方法                         | 用途                                                   |
|---------------------------------|--------------------------------------------------------|
| `OreDomainRegistration.shape(path, strategy)` | 注册矿脉形状（`registerOreFoundation`）  |
| `OreDomainRegistration.mode(path, strategy)`  | 注册生成通道模式（`registerOreFoundation`）|
| `OreDomainRegistration.airExposure(path, strategy)` | 注册空气暴露策略           |
| `OreDomainRegistration.conflict(path, strategy)` | 注册冲突策略                 |
| `OreDomainRegistration.vein(path)` | 开始矿脉注册                                       |
| `OreVeins.byMaterial(Material)` | 按材料反向查询                                         |
| `OreVeins.view()`               | 所有已注册矿脉                                         |
| `OreVein.entries()`             | 矿脉的材料+权重组成                                    |
| `OreVein.environment()`         | 生成环境                                               |
| `OreVein.placement()`           | 通过 `instanceof Feature` / `instanceof Grid` 判断模式 |
| `OreEnvironment`                | 维度规则 + 宿主规则的组合                              |
| `OreVeinMode`                   | 生成通道（feature / grid）                             |
| `OreVeinShape`                  | Grid 模式矿脉形状（包含测试函数）                      |
