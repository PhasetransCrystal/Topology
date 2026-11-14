---
sidebar_position: 1
---

# Machine

Machine API 是 Topology 的自动化核心。通过 Builder 模式声明机器、挂载行为组件，框架处理 BlockEntity 生命周期、持久化、网络同步和
tick 调度。

## 架构

{/* 图片预留：机器定义、组件和资源端口之间的关系图。 */}

```
MachineDefinition  ← 注册后的不可变句柄
  ├── MachineBlock        ← 方块层（含 machineId）
  ├── MachineBlockEntity  ← 组装 MachineComponents，驱动生命周期
  └── MachineComponents   ← 可挂载的行为 trait 容器
        ├── RecipeLogic          ← 配方搜索与执行
        ├── MachineWorkControl   ← 工作状态控制
        ├── MachineWorkView      ← 工作状态只读视图
        ├── MultiblockController ← 多方块结构控制器
        └── 你的自定义组件...
```

## 页面导航

| 页面                                                  | 内容                                                          | 何时阅读                   |
|-------------------------------------------------------|---------------------------------------------------------------|----------------------------|
| [MachineComponent](machine/machine-component.md)      | 继承 `MachineComponent` 实现行为 trait、tick、数据字段        | 写自定义机器组件           |
| [MachineRender](machine/machine-render.md)            | 继承 `MachineRenderComponent` 自定义渲染 + 自定义方块渲染类型 | 机器需要专属外观/动态渲染  |
| [Machine Resource Ports](machine/machine-resource.md) | 继承 `ResourcePort` 实现资源端口、`PortAccess` 侧配置         | 机器需要物品/流体/能量存取 |
| [Multiblock Abilities](machine/multiblock-ability.md) | PartRole 部件角色、PropertyDisplay 状态显示                   | 构建多方块舱口/诊断显示    |
| [Multi-block Patterns](machine/multiblock.md)         | Blueprint 声明式结构匹配                                      | 构建多方块机器             |
| [Machine UI](machine/machine-ui.md)                   | `collectMachineUi` 面板贡献、配方页构建块、XEI 集成           | 机器需要自定义界面         |
| **Machine（本页）**                                   | `machine.machine` 声明机器、挂载组件                           | 添加任何机器               |

## 完整示例：注册一台机器

```java
@Override
public void registerMachines(MachineDomainRegistration m) {
    m.machine("electric_furnace")
            .displayName("Electric Furnace", "电炉")
            .creativeTab(MY_CREATIVE_TAB)
            .render(myShellRender.shell(new MachineShellMaterial(
                    /* bottom */, /* side */, /* top */)))          // MachineBlockRenderUse
            .component(MyItemPort.mount(MyItemPort.KEY, 2))         // 你的自定义端口组件
            .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, recipeType))  // 配方执行
            .build();
}
```

:::note
`.component(...)` 接受 `ComponentMount`（key + 工厂）或 `ComponentContribution`（如 `PartRoleMount`，
可链式附加多方块部件角色）。同一 key 挂两次在声明期失败。
:::

## MachineComponent — 行为 Trait

所有机器能力通过 `MachineComponent` 子类提供。库**不内置任何端口/能量组件**——全部继承实现：

```java
public class MyComponent extends MachineComponent {

    public MyComponent(ComponentContext<MyComponent> context) {
        super(context);
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        // 只做结构校验，禁止读持久化状态
        other = traits.require(OtherComponent.KEY);
        optional = traits.optional(OptionalComponent.KEY);
    }

    @Override
    public void onMachineLoad() { /* 机器进入世界 */ }

    @Override
    public void collectMachineUi(MachineUiContribution ui) {
        // mainPage / leftPanel / rightPanel / bottomStrip
    }
}
```

关键方法、DataField 两轴、MachineTicker、资源 I/O 面的完整签名见
[MachineComponent](machine/machine-component.md)。

## 常用内置组件

库本体提供以下组件（Key 为库命名空间 `topo:*`）：

| 组件                     | Key                     | 说明                    |
|--------------------------|-------------------------|-------------------------|
| `RecipeLogic`            | `recipe_logic_*`        | 配方搜索与执行          |
| `MachineWorkControl`     | `work_control`          | 工作状态控制            |
| `MachineWorkView`        | `work_view`             | 工作状态只读视图        |
| `MultiblockController`   | `multiblock_controller` | 多方块结构控制器        |
| `MachineRenderComponent` | 自定义                  | 自定义 BlockEntity 渲染 |

资源端口由你的 Mod 自己实现：继承 `ResourcePort<S, R>` 基类，见
[Machine Resource Ports](machine/machine-resource.md)。

## 覆盖 Block / BlockEntity 子类

```java
// 在 registerMachines(machine) 中：
machine.machine("custom_machine")
    .displayName("Custom", "自定义")
    .block(MyMachineBlock::new)
    .blockEntity(MyBlockEntity::new)
    .component(...)
    .build();
```

## 为什么这样设计

组件组合替代“每台机器一个巨大 BlockEntity”；端口与资源类型由产品实现；声明期只排方块/BE，实例创建时才执行工厂回调。

## 与其他模块联动

材料提供机器方块/物品；配方通过 `RecipeLogic` 执行；管道引用资源类型；多方块工具通过 `StorageAccess` 取料；客户端与 JEI/Jade 读取状态/UI。

## 常见坑

- 在 `resolveDependencies` 读取持久化状态。
- 把共享 BlockEntity 类型注册当成每台实例已创建。
- 认为注册策略对象会立即执行全部回调。

## 扩展约定

- 新组件继承 `MachineComponent`，用 `ComponentMount` 挂载；同一 key 不可重复。
- 新端口继承 `ResourcePort` 并声明资源类型与端口元数据。
- 渲染/UI 用策略与 `collectMachineUi` 贡献，不在方块类写全局分支。

## API 速查

| 类/方法                                     | 用途                           |
|---------------------------------------------|--------------------------------|
| `machine.machine(path)`                     | 在 registerMachines 中开始机器注册 |
| `.displayName(en, cn)`                      | 双语机器名（必填）             |
| `.component(ComponentMount)`                | 挂载行为组件                   |
| `.block(factory)` / `.blockEntity(factory)` | 覆盖 Block/BE 子类             |
| `.render(MachineBlockRenderUse)`            | 选择渲染类型                   |
| `.creativeTab(tab)`                         | 创造模式物品栏                 |
| `.build()`                                  | 注册并返回 `MachineDefinition` |
| `MachineComponents.require(key)`            | 组件依赖查找                   |
| `MachineComponents.optional(key)`           | 组件可选查找                   |
