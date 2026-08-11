---
sidebar_position: 1
---

# Machine

Machine API 是 Topology 的自动化核心。通过 Builder 模式声明机器、挂载行为组件，框架处理 BlockEntity 生命周期、持久化、网络同步和
tick 调度。

## 何时阅读

当你需要添加一个自动化机器——无论是单方块还是多方块。这是 Topology 最核心的子系统。

## 架构

```
MachineDefinition  ← 注册后的不可变句柄
  ├── MachineBlock        ← 方块层（含 machineId）
  ├── MachineBlockEntity  ← 组装 MachineComponents，驱动生命周期
  └── MachineComponents   ← 可挂载的行为 trait 容器
        ├── 能量组件
        ├── 物品端口
        ├── 流体端口
        ├── RecipeLogic
        └── ...
```

## 完整示例：注册一台电炉

```java

@Override
public void registerMachines(MachineDomainRegistration m) {
    Machines.begin(Identifier.fromNamespaceAndPath("mymod", "electric_furnace"), registry)
            .displayName("Electric Furnace", "电炉")
            .creativeTab(MY_CREATIVE_TAB)
            .render(ShellMachineRenderType.INSTANCE)
            .component(EnergyBuffer.key())            // 能量缓冲区
            .component(ItemResourcePort.ITEM_INPUT)   // 物品输入端口
            .component(ItemResourcePort.ITEM_OUTPUT)  // 物品输出端口
            .component(RecipeLogic.mount(recipeTypes)) // 配方执行
            .build();
}
```

## MachineComponent — 行为 Trait

所有机器能力通过 `MachineComponent` 子类提供：

```java
public class MyComponent extends MachineComponent {

    @Override
    public void resolveDependencies(MachineComponents siblings) {
        // 查找兄弟组件
        other = siblings.require(OtherComponent.KEY);
        optional = siblings.optional(OptionalComponent.KEY);
    }

    @Override
    public void onMachineLoad() { /* 机器进入世界 */ }

    @Override
    public void collectMachineUi(MachineUiContribution ui) {
        // 贡献 UI 结构
    }
}
```

### 组件间依赖查找

```java
OtherComponent c = siblings.require(OtherComponent.KEY);        // 必须存在
Optional<OtherComponent> opt = siblings.optional(OtherComponent.KEY); // 可选
```

## DataField — 持久化 & 同步

组件通过 `DataField` 声明需要保存和/或同步的状态：

```java
public class EnergyBuffer extends MachineComponent {
    private final DataInt energy;

    public EnergyBuffer(DataScope data) {
        this.energy = data.intField("energy", 0)
                .persisted()
                .syncToClientAtEndOfDirtyTick()
                .done();
    }
}
```

内置 DataField 类型：`DataItemResourceHandler`（通过 `DataScope.itemResourceHandler(...)`） · `DataFluidResourceHandler`（通过
`DataScope.fluidResourceHandler(...)`）

## 常用内置组件

| 组件                     | Key                     | 说明                    |
|--------------------------|-------------------------|-------------------------|
| `EnergyBuffer`           | `energy_buffer`         | 能量存储 + I/O          |
| `ItemResourcePort`       | 预置 key                | 物品槽端口              |
| `FluidResourcePort`      | 预置 key                | 流体槽端口              |
| `ScalarResourcePort`     | 预置 key                | 标量资源端口            |
| `RecipeLogic`            | `recipe_logic_*`        | 配方搜索与执行          |
| `MachineWorkControl`     | `work_control`          | 工作状态控制            |
| `MachineWorkView`        | `work_view`             | 工作状态只读视图        |
| `MultiblockController`   | `multiblock_controller` | 多方块结构控制器        |
| `MachineRenderComponent` | 自定义                  | 自定义 BlockEntity 渲染 |

## 覆盖 Block / BlockEntity 子类

```java
Machines.begin(id, registry)
    .displayName("Custom","自定义")
    .block(MyMachineBlock::new)
    .blockEntity(MyBlockEntity::new)
    .component(...)
    .build();
```

## API 速查

| 类/方法                                     | 用途                           |
|---------------------------------------------|--------------------------------|
| `Machines.begin(id, registry)`              | 开始机器注册                   |
| `.displayName(en, cn)`                      | 双语机器名（必填）             |
| `.component(ComponentMount)`                | 挂载行为组件                   |
| `.block(factory)` / `.blockEntity(factory)` | 覆盖 Block/BE 子类             |
| `.render(MachineBlockRenderUse)`            | 选择渲染类型                   |
| `.creativeTab(tab)`                         | 创造模式物品栏                 |
| `.build()`                                  | 注册并返回 `MachineDefinition` |
| `MachineComponents.require(key)`            | 组件依赖查找                   |
| `MachineComponents.optional(key)`           | 组件可选查找                   |
| `DataScope.intField(name, initialValue)`    | 声明持久化/同步字段            |
