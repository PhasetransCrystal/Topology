---
sidebar_position: 3
---

# Equipment

设备系统基于 Material 上声明的 **MaterialData** 自动生成工具、武器、护甲等物品。一种设备种类 × 一种材料 = 一个物品，全自动。

## 何时阅读

需要基于材料系统生成工具/护甲时。前提：已声明好 Material 并附加了对应的 MaterialData。

## 工作原理

```
Material "copper"  +  toolStatsDataType.stats(...)
                    ↓
Equipment "pickaxe" +  Material "copper"  →  铜镐物品
Equipment "sword"   +  Material "copper"  →  铜剑物品
Equipment "helmet"  +  Material "copper"  →  铜头盔物品
```

## 注册设备种类

```java

@Override
public void registerEquipmentKinds(EquipmentDomainRegistration equipment) {
    equipment.kind("pickaxe", new PickaxeStrategy());
    equipment.kind("axe", new AxeStrategy());
    equipment.kind("shovel", new ShovelStrategy());
    equipment.kind("hoe", new HoeStrategy());
    equipment.kind("sword", new SwordStrategy());
    equipment.kind("helmet", new HelmetStrategy());
    equipment.kind("chestplate", new ChestplateStrategy());
    equipment.kind("leggings", new LeggingsStrategy());
    equipment.kind("boots", new BootsStrategy());
}
```

:::info
`EquipmentDomainRegistration.kind(path, strategy)` 返回 `Equipment`，直接注册，无需 `.build()`。
:::

## 查询已注册设备

```java
Equipment pickaxe = EquipmentRegistry.require(Identifier.fromNamespaceAndPath("mymod", "pickaxe"));

// 遍历所有已生成的设备物品
for(EquipmentItemRecord record :EquipmentRegistry.itemRecords()){
    Equipment kind = record.equipment();
    Material mat = record.material();
    Item item = record.entry().get();
}
```

## API 速查

| 类/方法                                            | 用途               |
|----------------------------------------------------|--------------------|
| `EquipmentDomainRegistration.kind(path, strategy)` | 注册设备种类       |
| `EquipmentRegistry.require(id)`                    | 按 ID 查找设备种类 |
| `EquipmentRegistry.itemRecords()`                  | 所有已生成设备物品 |
| `EquipmentStrategy.appliesTo(material)`            | 判断该材料是否适用 |
| `EquipmentStrategy.register(context, self)`        | 为指定材料生成物品 |
