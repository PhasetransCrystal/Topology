---
sidebar_position: 3
---

# MaterialDataType

材质数据 = 附加在 `Material` 上的类型化属性（颜色、质量、工具/护甲属性……）。装备策略与表单校验依据
材质数据决定行为。

本页是 [Material](../material.md) 模块的子页——概念与页面导航见 [Material](../material.md)。

## 基类

```java
public abstract class MaterialDataType<D> {

    protected MaterialDataType(Identifier id);      // 身份

    protected final MaterialDataUse<D> use(D data); // 铸造权——子类公开工厂方法包装
}
```

**生产级结构**：数据类型是薄壳（工厂方法 + `use()`），数据本体是带校验的 record：

```java
/** 数据本体：record + 紧凑构造校验（非法值在声明行直接抛异常）。 */
public record ToolStatsData(int durability, float miningSpeed, float attackBonus,
                            int enchantmentValue, TagKey<Block> incorrectForDrops) {

    public ToolStatsData {
        if (durability <= 0) throw new IllegalArgumentException("durability must be positive");
        if (miningSpeed <= 0) throw new IllegalArgumentException("mining speed must be positive");
        if (attackBonus < 0) throw new IllegalArgumentException("attack bonus must not be negative");
        Objects.requireNonNull(incorrectForDrops, "incorrectForDrops");
    }
}
```

```java
/** 数据类型：公开工厂铸造 MaterialDataUse，调用方不能直接 new。 */
public final class ToolStatsDataType extends MaterialDataType<ToolStatsData> {

    public ToolStatsDataType(Identifier id) { super(id); }

    /** 参数语义见 ToolStatsData；值在这一行被校验。 */
    public MaterialDataUse<ToolStatsData> stats(int durability, float miningSpeed, float attackBonus,
                                                int enchantmentValue, TagKey<Block> incorrectForDrops) {
        return use(new ToolStatsData(durability, miningSpeed, attackBonus,
                enchantmentValue, incorrectForDrops));
    }
}
```

## 注册

```java
// registerMaterialFoundation：
ToolStatsDataType toolStats = material.dataType("tool_stats", new ToolStatsDataType(plugin.material().id("tool_stats")));
```

## 消费

```java
// 策略/表单侧（如 EquipmentStrategy.appliesTo 或 MaterialFormStrategy.validateMaterial）：
Optional<ToolStatsData> stats = material.strategy().data(toolStats);   // MaterialStrategy.data(type)

// 材料声明侧附加数据（registerMaterials）：
m.material("copper").data(toolStats.stats(200, 4.5f, 1.5f, 14, ItemTags.INCORRECT_FOR_IRON_TOOL)).build();
```

## 校验

领域入口 `material.dataType(path, handle)` 使用默认无校验策略；需要校验时，把校验器传给注册表入口
（材质每次声明数据时执行）。领域注册 API 是唯一写入入口，产品代码不要直接调用 `*Registry.begin`。

:::info
数据类型与表单一样有冻结点：在 `registerMaterialFoundation` 注册，`registerMaterials` 只能引用。
:::

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `MaterialDataType(Identifier)`         | 数据类型基类构造                |
| `MaterialDataType.use(data)`           | 铸造权（protected，子类公开包装）|
| `material.dataType(path, handle)`      | 领域注册入口（钩子 3）          |
| `MaterialStrategy.data(type)`          | 消费侧读取 → `Optional<D>`      |
| `m.material(...).data(use)`            | 材料声明侧附加                  |
