---
sidebar_position: 5
---

# Multiblock Abilities

多方块的**能力注册门**（钩子 10 `registerMachineFoundation`）：部件角色与方块状态显示。
两者都先于 `Machines.freeze()` 冻结，`registerMachines` 只能引用。

本页是 [Machine](../machine.md) 模块的子页——概念与页面导航见 [Machine](../machine.md)。

## PartRole — 部件角色（舱口/总线）

配方 I/O 住在**部件块**上，控制器本体不代表布局。声明一个部件角色，把它链式挂到端口组件上，
蓝图里用 `CellPredicates.role(...)` 匹配：

```java
// registerMachineFoundation：声明角色（显示名 + 资源类型 + 配方 I/O 方向）
PartRole itemInput = machine.partRole("item_input", "Item Input", "物品输入",
        itemResourceType, RecipeRole.INPUT);
```

```java
// registerMachines：端口挂载 + 附加角色（PartRoleMount 是 ComponentContribution）
Machines.begin(id, registry)
        .component(ItemPort.input(ItemPort.KEY, 2).role(itemInput))   // 链式附加
        .build();
```

```java
// 蓝图：角色谓词匹配任意注册为该角色的部件块
.aisle("CIC")
.where('I', CellPredicates.role(itemInput))
```

框架随后自动处理：结构匹配时按角色识别舱口、成型后把部件端口汇聚进控制器的配方视图。
蓝图使用侧见 [Multi-block Patterns](multiblock.md)。

## PropertyDisplay — 方块状态显示

结构诊断页里把方块状态属性渲染成可读标签：

```java
machine.propertyEnum("facing", /* label LangKey */, /* Map<Facing, LangKey> */,
        BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING);

machine.propertyBoolean("waterlogged", /* label LangKey */, BlockStateProperties.WATERLOGGED);
```

`propertyEnum` 要求枚举实现 `StringRepresentable`；`propertyBoolean` 是 `propertyEnum` 的布尔便捷形式。

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `machine.partRole(path, en, cn, resourceType, recipeIo)` | 注册部件角色 |
| `PartRoleMount.role(capability)`       | 端口挂载链式附加角色            |
| `machine.propertyEnum(path, label, valueTexts, properties...)` | 状态显示   |
| `machine.propertyBoolean(path, label, property)` | 布尔便捷形式         |
