---
sidebar_position: 3
---

# MaterialDataType

材质数据 = 附加在 `Material` 上的类型化属性（颜色、质量、工具/护甲属性……）。装备策略与形态校验依据
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
// 策略/形态侧（如 EquipmentStrategy.appliesTo 或 MaterialFormStrategy.validateMaterial）：
Optional<ToolStatsData> stats = material.data(toolStats);   // Material.data(type)

// 能力谓词可直接用于形态匹配：
material.form("tool").appliesTo(toolStats.present());
material.form("ingot").appliesTo(ingotData.present());

// 材料声明侧附加数据（registerMaterials）：
m.material("copper").data(toolStats.stats(200, 4.5f, 1.5f, 14, ItemTags.INCORRECT_FOR_IRON_TOOL)).build();
```

## 跨插件注入能力数据

推荐在 `registerMaterialContributions` 中注入。该阶段在所有插件的 `registerMaterials` 之后、
形态匹配与冻结之前运行，因此目标材料一定已经构建，且注入数据会参与 `appliesTo`、校验和后处理：

```java
@Override
public void registerMaterialContributions(MaterialContributionRegistration contributions) {
    Material copper = contributions.require(Identifier.fromNamespaceAndPath("base", "copper"));
    contributions.data(copper, CORROSION.rating(3));
    contributions.form(copper, INGOT, options -> options.overrideItem(ExternalTargets.mcItem("copper_ingot")));
}
```

- 不依赖插件登记顺序；可遍历 `contributions.materials()` 给全量材料注入。
- 已知目标已构建时，也可在 `registerMaterials` 窗口内直接 `target.data(use)`。
- `Material.data(use)` 返回材料自身，可连续补充不同类型的数据；内部策略的写方法不对开发者开放。
- 数据类型仍须在 `registerMaterialFoundation` 注册；注入只补充数据值，不改变目标材料归属。
- 对同一材料重复写入同一数据类型会报错，包括覆盖其构建时声明的数据；不同数据类型可由多个插件分别补充。
- 形态匹配开始前关闭写入；匹配 Lambda、校验、内容生成及运行时均不能注入。

## 依赖与互斥

属性之间可以声明关系：

```java
// 依赖：注入 TOOL 时必须已经声明 INGOT；顺序由注入方保证。
TOOL.requires(INGOT);

// 互斥：冻结时检查，不能同时声明 GEM 与 INGOT。
GEM.excludes(INGOT);
```

- `requires(...)` 在每次 `data(use)` / Builder `.data(use)` 注入时检查；缺少依赖立即失败。
- `excludes(...)` 在材料冻结时逐条检查（`checkExclusions` 先于 `validate`）；同时声明会终止启动。
- 两者都按 DataType 身份判断，不依赖值相等；新增属性只需声明关系，不改材料核心。

## 校验

数据类型可覆写 `validate(Material, D)`；材料冻结时统一执行已安装的校验，包含补充的数据。
产品的数据值约束也可放在 payload record 的构造器或类型公开的工厂方法中；
与某个形态有关的数据依赖在 `MaterialFormStrategy.validateMaterial` 中检查。

:::info
数据类型与形态一样有冻结点：在 `registerMaterialFoundation` 注册，`registerMaterials` 只能引用。
:::

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `MaterialDataType(Identifier)`         | 数据类型基类构造                |
| `MaterialDataType.use(data)`           | 铸造权（protected，子类公开包装）|
| `MaterialDataType.validate(material, data)` | 可覆写的材料级校验        |
| `MaterialDataType.requires(...)` / `.excludes(...)` | 注入依赖 / 冻结互斥 |
| `MaterialDataType.present()` / `.is(value)` / `.matches(predicate)` | 形态匹配谓词 |
| `material.dataType(path, handle)`      | 领域注册入口（钩子 3）          |
| `Material.data(type)`                  | 消费侧读取 → `Optional<D>`      |
| `m.material(...).data(use)`            | 材料声明侧附加                  |
| `targetMaterial.data(use)`            | 直接向已构建材料补充数据        |
| `contributions.data(material, use)`   | 贡献阶段向任意已构建材料注入    |
