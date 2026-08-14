---
sidebar_position: 7
---

# Machine UI

机器界面是**声明式**的：组件在 `collectMachineUi` 里描述要什么面板，框架负责布局、分页与生命周期。
本页覆盖自定义机器屏幕的三个层次。

本页是 [Machine](../machine.md) 模块的子页——概念与页面导航见 [Machine](../machine.md)。

## 1. 面板贡献 — MachineUiContribution

组件贡献 UI 的唯一入口（在 `collectMachineUi` 里调用）：

```kotlin
override fun collectMachineUi(contribution: MachineUiContribution) {
    // 主区分页：body 用 MachineUiLayoutScope 构建
    contribution.mainPage("my_page", title) {
        // 布局元素……
    }

    // 左右侧栏卡片（pageKey 指定挂到哪个分页，null = 全局）
    contribution.rightPanel("my_panel", title, pageKey = "my_page") { /* ... */ }
    contribution.leftPanel("side_io", title, pageKey = "my_page", element = uiElement)

    // 底部条（资源条等，不包卡片）
    contribution.bottomStrip("resource_bars", Component.empty(), pageKey = "my_page") {
        // ResourceBar 等
    }
}
```

要点：

- `mainPage(key, title, build)` — 键稳定（持久化打开的分页）
- `rightPanel` / `leftPanel` 有 build 重载与 element 重载（后者可带 `visibleWhen` 动态隐藏）
- 框架自动包装侧栏卡片（`MachineUiContainerTemplate.createCard`）；底条不包

## 2. 配方页构建块 — RecipeUiLayout / LiveRecipeSlots

自定义配方页组件可复用框架的布局工厂：

```kotlin
// 按 SlotPlan 生成输入/输出槽列 + 进度条（绑定制）
RecipeUiLayout.buildRecipeIoRow(
    recipeType, slotCounts, slotFactory, progressBinder)

RecipeUiLayout.buildProgressBar(recipeType, progressBinder)

// 运行时槽位：从机器资源视图推导真实 SlotPlan，绑定活槽
LiveRecipeSlots.forMachine(machine)                 // Map<SlottedRecipeCapability, SlotPlan>
LiveRecipeSlots.createMachineSlot(machine, slotCounts, io, globalIndex)
```

库内置的 `RecipeUi` / `MultiblockUi` 组件就是这些工厂的组装范例。

## 3. 配方查看器集成 — XeiRecipeLookup

进度条点击「查看配方」时打开外部查看器（JEI/REI/EMI）。查看器**由集成方安装**，库只提供插座：

```kotlin
// 集成 Mod 的客户端初始化：
XeiRecipeLookup.install { recipeTypes ->
    // 打开你的查看器并聚焦这些配方类型；成功返回 true
    myJeiPlugin.showCategories(recipeTypes)
}
```

API：

| 方法                                       | 用途                                   |
|--------------------------------------------|----------------------------------------|
| `XeiRecipeLookup.install(Opener)`          | 安装查看器打开器（覆盖旧值）           |
| `XeiRecipeLookup.uninstall()`              | 移除                                   |
| `XeiRecipeLookup.isAvailable()`            | 查看器是否就绪（进度条提示依赖此状态） |
| `XeiRecipeLookup.showRecipes(recipeTypes)` | 框架 UI 触发打开                       |

未安装查看器时，`isAvailable()` 为 false——配方 UI 自动隐藏「查看配方」悬浮提示。

## 相关

- 机器组件与挂载：[MachineComponent](machine-component.md)
- 工具提示面板：[Tooltip Panels](../foundation/tooltip-panels.md)
- 资源条与侧配置：[Machine Resource Ports](machine-resource.md)

## API 速查

| 类/方法                                                              | 用途               |
|----------------------------------------------------------------------|--------------------|
| `MachineUiContribution.mainPage(key, title, build)`                  | 主区分页           |
| `MachineUiContribution.leftPanel(...)` / `.rightPanel(...)`          | 侧栏卡片           |
| `MachineUiContribution.bottomStrip(...)`                             | 底条（资源条等）   |
| `RecipeUiLayout.buildRecipeIoRow(type, counts, slotFactory, binder)` | 配方 I/O 行        |
| `RecipeUiLayout.buildProgressBar(type, binder)`                      | 进度条             |
| `LiveRecipeSlots.forMachine(machine)`                                | 真实 SlotPlan 推导 |
| `LiveRecipeSlots.createMachineSlot(machine, counts, io, index)`      | 活槽绑定           |
| `XeiRecipeLookup.install(opener)` / `.uninstall()`                   | 查看器插座         |
| `XeiRecipeLookup.isAvailable()` / `.showRecipes(types)`              | 查看器状态/触发    |
