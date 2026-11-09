---
sidebar_position: 5
---

# 多方块能力

多方块部件通过 `PartRole` 声明资源类型与配方输入输出角色，通过 `PropertyDisplay` 注册方块状态的可读标签。
这些基础声明位于 `registerMachineFoundation`，机器与蓝图在 `registerMachines` 中引用。

本页是 [Machine](../machine.md) 的子页。结构定义与检测见 [多方块蓝图](multiblock.md)。

## 部件角色

通过机器注册入口声明 `PartRole`：

```java
PartRole itemInput = machine.partRole(
    "item_input", "Item Input", "物品输入",
    itemResourceType, RecipeRole.INPUT);
```

角色包含注册 ID、显示名、`MachineResourceType` 和 `RecipeRole`。
它既是蓝图谓词的匹配依据，也是 `Blueprint.count(...)` 的计数键。
资源端口本身负责存储和资源操作，角色声明负责标记其结构用途。

以 TopoDemo 的 `ItemPort` 为例，将角色附加到输入端口挂载：

```java
machine.machine("input_bus")
    .displayName("Item Input Bus", "物品输入总线")
    .component(ItemPort.mount(ItemPort.INPUT, 3, PortAccess.input())
        .role(itemInput))
    .build();
```

`ItemPort.mount(...)` 返回 `PartRoleMount<ItemPort>`。不调用 `role` 时可以直接作为普通端口挂载；
调用 `role` 后返回带 `PartRoleAttachment` 的挂载。框架在构造挂载时校验宿主的资源类型与配方角色是否匹配。
`ItemPort` 是 Demo 自定义端口，不是 Topo 内置端口；自己的端口可使用相同挂载方式。

蓝图通过角色谓词接纳部件：

```java
Blueprint blueprint = Blueprint.of()
    .aisle("@PP")
    .where('P', CellPredicates.anyOf(
        CellPredicates.block(Blocks.IRON_BLOCK),
        CellPredicates.role(itemInput)))
    .count(itemInput, 1, 2)
    .build();
```

两个 `P` 位置可以放铁块或输入部件，数量约束要求至少一个、最多两个输入角色。
控制器成型后将成员部件的资源处理器贡献到配方资源视图；模块中的成员只在该模块满足时接入。

## 独占部件

普通部件可以共享。需要由一个成型控制器独占时，在部件机器定义上额外挂载标记：

```java
machine.machine("exclusive_input_bus")
    .component(ItemPort.mount(ItemPort.INPUT, 3, PortAccess.input())
        .role(itemInput))
    .component(ExclusivePartMount.of())
    .build();
```

`ExclusivePartMount` 是与端口并列的声明，可用于不同资源类型的部件。
独占预留在成型时取得，在结构解体或控制器卸载时释放。
主结构的独占部件冲突会阻止成型；仅模块发生冲突时，对应模块及依赖它的模块失效。

## 方块状态显示

属性显示注册把结构诊断中的状态值转换为可读标签。标签和值文本均传入 `LangKey`：

```java
machine.propertyEnum("facing", facingLabel, facingValueTexts,
    BlockStateProperties.FACING,
    BlockStateProperties.HORIZONTAL_FACING);

machine.propertyBoolean("waterlogged", waterloggedLabel,
    wetText, dryText, BlockStateProperties.WATERLOGGED);
```

`propertyEnum` 的值类型须为实现 `StringRepresentable` 的枚举，值文本参数为 `Map<T, LangKey>`。
`propertyBoolean` 分别接收 true、false 的文本。
状态显示与结构匹配是独立的声明；旋转和状态比较使用的 `PropertyRule` 见 [多方块蓝图](multiblock.md)。

## API 速查

| 方法 | 用途 |
|---|---|
| `machine.partRole(path, en, cn, resourceType, recipeIo)` | 注册部件角色 |
| `PartRoleMount.role(role)` | 向端口挂载附加经校验的角色 |
| `CellPredicates.role(role)` | 在蓝图中匹配角色 |
| `Blueprint.Builder.count(role, min, max)` | 约束匹配角色数量 |
| `ExclusivePartMount.of()` | 标记独占部件 |
| `machine.propertyEnum(path, label, valueTexts, properties...)` | 注册枚举属性显示 |
| `machine.propertyBoolean(path, label, whenTrue, whenFalse, properties...)` | 注册布尔属性显示 |
