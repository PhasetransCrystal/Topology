---
sidebar_position: 6
---

# Multi-block Patterns

多方块结构通过声明式 `Blueprint` 定义。定义形状 → 挂载到机器 → 框架自动处理结构匹配、成型验证和异步诊断。

本页是 [Machine](../machine.md) 模块的子页——概念与页面导航见 [Machine](../machine.md)。

## 何时阅读

需要构建多方块机器时。前提：已理解 Machine API 基础。多方块是 Machine 的扩展功能——通过
`MultiblockController.mount(blueprint)` 一次挂载即可。

## 完整示例：定义一个 3×3×2 多方块

```java
// 1. 定义 Blueprint
Blueprint blueprint = Blueprint.of()
    .aisle(              // 底层：3×3 实心外壳
        "CCC",
        "CCC",
        "CCC"
    )
    .aisle(              // 顶层：3×3 空心（中心是控制器 @）
        "CAC",
        "C@C",
        "CAC"
    )
    .where('C', CellPredicates.block(MyBlocks.CASING))    // 外壳方块
    .where('A', state -> state.isAir())                    // 空气
    .build();

// 2. 注册为机器
Machines.begin(Identifier.fromNamespaceAndPath("mymod", "my_multiblock"),registry)
    .displayName("My Multiblock","大型多方块机器")
    .component(MultiblockController.mount(blueprint))
    .component(MyItemPort.mount(MyItemPort.KEY, 2))          // 你的自定义端口组件
    .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, recipeType))
    .build();
```

:::info
蓝图中的 `@` 字符标记控制器位置，框架自动扫描定位。
:::

## CellPredicate — 方块匹配规则

```java
// 精确匹配方块
CellPredicates.block(Blocks.IRON_BLOCK);

// 角色匹配
CellPredicates.role(somePartRole);   // PartRole 注册项

// 控制器方块
CellPredicates.controller();

// 任意匹配（or 组合）
CellPredicates.anyOf(predicateA, predicateB);

// 自定义谓词
CellPredicate custom = (state, expected) -> state.is(BlockTags.MINEABLE_WITH_PICKAXE);
```

`CellPredicate` 接口提供两个方法：`test(StructureView, BlockPos, Cell)` 和 `test(BlockState, BlockState)`。

## PropertyRule — 方块状态约束

通过 `where` 的重载形式附加状态约束（第 3 参是旋转前的**规范状态**，第 4 参是旋转协变规则）：

```java
Blueprint.of()
    .aisle("C C")
    .where('C', CellPredicates.block(casingBlock),
        casingBlock.defaultBlockState(),                       // expectedState（预旋转）
        PropertyRules.direction(BlockDirectionProperties.FACING))   // FACING 随结构旋转协变
    .build();
```

`PropertyRules` 提供的工厂方法：

- `PropertyRules.direction(EnumProperty<Direction>...)` — 朝向属性（水平 4 向拒绝俯仰旋转）
- `PropertyRules.axis(EnumProperty<Direction.Axis>)` — 轴属性
- `PropertyRules.half(EnumProperty<Half>)` — 半格属性（TOP/BOTTOM）
- `PropertyRules.derived(Property<?>...)` — 派生属性（如楼梯 shape，屏蔽比较 + 备用编码）

:::info
部件角色（舱口/总线）与方块状态显示注册见 [Multiblock Abilities](multiblock-ability.md)。
:::

## 运行时过程

MultiblockController 自动管理：

1. **变更检测** — `MultiblockChangeWatcher` 只重新检查已知结构位置
2. **结构匹配** — `StructureEngine.recognize()` 执行完整诊断
3. **异步验证** — 大型结构使用 `AsyncStructureService` 异步计算
4. **成型传播** — formed 状态通过 `MachineWorkView` 暴露给 RecipeLogic 等组件
5. **客户端诊断** — `MultiblockClientDiagnosis` 可视化缺失方块

## API 速查

| 类/方法                                                   | 用途                    |
|-----------------------------------------------------------|-------------------------|
| `Blueprint.of()`                                          | 开始 Blueprint 定义     |
| `.aisle(lines...)`                                        | 定义一个 Y 层的字符网格 |
| `.where(char, predicate)`                                 | 字符→方块映射           |
| `.where(char, predicate, rules...)`                       | 映射 + 属性规则约束     |
| `.build()`                                                | 构建 Blueprint          |
| `CellPredicates.block(block)`                             | 精确方块匹配            |
| `CellPredicates.controller()`                             | 控制器方块匹配          |
| `CellPredicates.role(partRole)`                           | PartRole 成员匹配       |
| `CellPredicates.anyOf(predicates...)`                     | 多谓词 OR 组合          |
| `MultiblockController.mount(blueprint)`                   | 一行挂载多方块到机器    |
| `StructureEngine.recognize(view, blueprint, orientation)` | 完整诊断                |
| `StructureEngine.verify(view, blueprint, orientation)`    | 快速验证                |
