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

## build 后跨插件注入

`build()` 发布材料句柄，不会立刻冻结材料数据。所有插件共享 `registerMaterials` 的注册窗口，
在该窗口内可直接给其他插件已构建的材料补充已注册的数据：

```java
@Override
public void registerMaterials(MaterialDomainRegistration material) {
    OtherMaterials.COPPER.data(
            toolStats.stats(200, 4.5f, 1.5f, 14, ItemTags.INCORRECT_FOR_IRON_TOOL));
}
```

上例假设目标已经完成 `build()`。模组依赖隐式确认跨插件依赖关系，但不能保证对方同一钩子内的内容已经初始化；
应按提供方的初始化或回调约定取得就绪句柄，不能靠插件登记顺序。完整边界见
[跨插件补数据](../compatibility/topo-plugins.md#5-给已经构建的材料补数据)。

直接操作目标插件公开的真实 `Material` 对象，无需通过当前插件的领域入口转交。
`Material.data(use)` 返回材料自身，可连续补充不同类型的数据。
Builder 从创建起便持有该策略；构建前后使用同一份数据存储与重复校验，不在 `build()` 时复制出第二份。
`Material.data(use)` 直接写入内部策略，不经过注册表转发；策略的写方法不对开发者开放。
数据类型仍须在 `registerMaterialFoundation` 注册；注入只补充数据值，不改变目标材料归属。

- 所有插件完成 `registerMaterials` 后才执行 `appliesTo`，注入数据也参与形态匹配、策略校验、装备与后处理。
- 对同一材料重复写入同一数据类型会报错，包括覆盖其构建时声明的数据；不采用后写覆盖。
- 不同数据类型可以分别由多个插件补充，材料句柄及命名空间保持不变。
- 形态匹配开始前关闭写入；匹配 Lambda、校验、内容生成及运行时均不能注入。
- 注入目标必须已经 `build()`，通过目标插件公开的材料句柄访问；此接口不会提前构建或创建材料。

## 校验

领域入口 `material.dataType(path, handle)` 使用默认无校验策略。产品的数据值约束应放在 payload record 的构造器或
类型公开的工厂方法中；与某个形态有关的数据依赖在 `MaterialFormStrategy.validateMaterial` 中检查。
底层 `MaterialDataRegistry.begin` 支持框架内部校验器，但不是产品接入入口。
材料冻结时仍会统一执行已安装的校验策略，包含补充的数据。

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
| `targetMaterial.data(use)`            | 直接向已构建材料补充数据        |
