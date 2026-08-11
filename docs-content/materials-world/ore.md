---
sidebar_position: 2
---

# Ore

声明式的矿石世界生成框架。矿脉引用 Material 系统中注册的材料，支持 **Feature**（小型分散）和 **Grid**（大型网格）两种生成模式。

## 何时阅读

需要向世界添加矿石生成时。前提：已注册好对应的 Material。

## Feature 模式 — 小型分散矿脉

```java

@Override
public void registerOreVeins(OreDomainRegistration ore) {
    ore.vein("copper_vein")
        .lang("Copper Vein", "铜矿脉")
        .environment(OreEnvironments.STONE)
        .feature(OreVeinModes.STONE_REPLACE, 33, 16)  // mode, size, attemptsPerChunk
        .entry(material("copper"), 80)
        .entry(material("iron"), 20)
        .triangularHeight(-16, 112)
        .airExposure(OreAirExposurePolicies.DISCARD, 0.5)
        .build();
}
```

### Feature 参数

| 参数                          | 说明                                     |
|-------------------------------|------------------------------------------|
| `mode`                        | 替换策略（`STONE_REPLACE` 替换石类方块） |
| `size`                        | 矿脉最大方块数                           |
| `attemptsPerChunk`            | 每 chunk 生成尝试次数                    |
| `height(min, max)`            | 均匀分布高度                             |
| `triangularHeight(min, max)`  | 三角分布（中部更密集）                   |
| `airExposure(policy, chance)` | 空气暴露策略 + 丢弃概率                  |

## Grid 模式 — 大型网格矿脉

```java
ore.vein("iron_grid")
    .lang("Iron Grid Vein","铁网格矿脉")
    .environment(OreEnvironments.STONE)
    .grid(OreVeinModes.STONE_REPLACE, OreVeinShapes.OVAL)
    .radius(32)
    .spacing(3,0)              // gridSizeChunks, randomOffsetBlocks
    .density(0.7)
    .weight(100)
    .priority(1)
    .conflict(OreConflictPolicies.REPLACE)
    .entry(material("iron"), 100)
    .entry(material("nickel"), 10)
    .height(-64,64)
    .airExposure(OreAirExposurePolicies.ALLOW, 0)
    .build();
```

### Grid 参数

| 参数                          | 说明                                 |
|-------------------------------|--------------------------------------|
| `shape`                       | 矿脉形状（`OVAL` 等）                |
| `radius`                      | 矿脉半径（blocks）                   |
| `spacing(gridChunks, offset)` | 最小 chunk 间距 + 随机偏移（blocks） |
| `density`                     | 0–1，矿脉内部填充密度                |
| `weight`                      | 同区域竞争时的随机权重               |
| `priority`                    | 冲突时高优先覆盖低优                 |
| `conflict`                    | 冲突策略：`REPLACE` 或 `SKIP`        |

## 查询矿脉

```java
// 查找包含某个材料的所有矿脉
List<OreVein> veins = OreVeins.byMaterial(MaterialRegistry.require(id("copper")));

for(OreVein vein :veins){
    System.out.println(vein.displayName());
    System.out.println("Y: "+vein.minY() +"~"+vein.maxY());

    for(OreVeinEntry entry :vein.entries()){
        System.out.println(entry.material().id() +" × "+entry.weight());
    }
}

// 遍历所有矿脉
OreVeins.view().forEach(v ->System.out.println(v.id()));
```

## API 速查

| 类/方法                         | 用途                                                   |
|---------------------------------|--------------------------------------------------------|
| `OreVeins.begin(id, lang)`      | 开始矿脉注册                                           |
| `OreVeins.byMaterial(Material)` | 按材料反向查询                                         |
| `OreVeins.view()`               | 所有已注册矿脉                                         |
| `OreVein.entries()`             | 矿脉的材料+权重组成                                    |
| `OreVein.environment()`         | 生成环境                                               |
| `OreVein.placement()`           | 通过 `instanceof Feature` / `instanceof Grid` 判断模式 |
| `OreEnvironment`                | 环境类型（Stone/Netherrack/EndStone/自定义）           |
| `OreVeinMode`                   | 替换策略                                               |
| `OreVeinShape`                  | Grid 模式矿脉形状                                      |
