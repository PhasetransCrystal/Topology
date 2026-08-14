---
sidebar_position: 1
---

# Recipe

Topology 配方系统基于**能力**（capability）I/O 模型：配方不直接操作机器存储，而是通过 `RecipeCapability`
声明的 I/O 面与机器资源端口对接。配方支持启动消耗、每 tick I/O、并行执行与配方缩放。

## 关键概念

```
RecipeCapability  ← 资源种类 × 配方 I/O 的桥梁（物品/流体/标量……）
    ├── TopoRecipeType    ← 配方类型（机器的一种工作模式）
    ├── TopoRecipe        ← 配方实例（输入 → 输出 + 耗时 + 生产线）
    ├── ProductionLine    ← 生产线（同族配方分组，JEI 展示用）
    └── RecipeLogic       ← 机器组件：搜索、匹配、执行
```

- **启动 I/O**：开始时一次性消耗/产出（`input` / `output`）
- **每 tick I/O**：运行期间持续消耗/产出（`tickInput` / `tickOutput`）
- **配方缩放**：`withParallel(n)` 并行执行、`withScaledDuration(f)` 调整耗时——返回新实例（不可变）
- **导入/导出**：`importRecipesFrom(...)` 吸收原版/外部配方表，`exportRecipesTo(...)` 反向导出

## 页面导航

| 页面                                            | 内容                                             | 何时阅读                       |
|-------------------------------------------------|--------------------------------------------------|--------------------------------|
| [RecipeCapability](recipe/recipe-capability.md) | 继承 `RecipeCapability` 定义新资源种类的配方 I/O | 添加物品/流体/能量之外的新资源 |
| [RecipeType](recipe/recipe-type.md)             | 注册配方类型与生产线、自定义配方载体             | 声明机器的配方类型             |
| **Recipe（本页）**                              | `TopoRecipe.Builder` 配方构建、运行时执行、缩放  | 编写具体配方                   |

## 注册配方类型

```java

@Override
public void registerRecipeTypes(RecipeDomainRegistration recipe) {
    recipe.recipeType("macerating")
            .displayName("Macerating", "粉碎");

    recipe.recipeType("smelting")
            .displayName("Smelting", "冶炼");

    // 生产线在同阶段声明（最晚 registerRecipes，见钩子 8）：
    maceratingLine = recipe.productionLine("macerating");
}
```

自定义配方类型子类见 [RecipeType](recipe/recipe-type.md)。

## 创建配方

```java
// 在 registerRecipes 阶段。itemCapability / energyCapability 是你的 RecipeCapability 子类，
// 公开铸造包装（基类 inputUse/outputUse 是 protected）：
TopoRecipe recipe = new TopoRecipe.Builder<>(maceratingType, "mymod:iron_dust")
                .input(itemCapability.in(Items.IRON_ORE, 1))      // 启动消耗：1 铁矿石
                .output(itemCapability.out(IRON_DUST, 2))         // 启动产出：2 铁粉
                .tickInput(energyCapability.in(128))              // 每 tick 消耗 128 EU
                .duration(200)
                .productionLine(maceratingLine)
                .save();
```

能力继承与铸造权见 [RecipeCapability](recipe/recipe-capability.md)。

## Builder 方法一览

```java
new TopoRecipe.Builder<>(recipeType, "modid:path")
    .input(capability.in(...))            // 启动消耗
    .output(capability.out(...))          // 启动产出
    .tickInput(capability.in(...))        // 每 tick 消耗
    .tickOutput(capability.out(...))      // 每 tick 产出
    .duration(200)                        // 配方时间（tick），默认 100
    .productionLine(line)                 // 关联生产线（ProductionLine 句柄）
    .replacesImported()                   // 手写配方覆盖 datapack/导入配方
    .save();
```

## 运行时执行流程

RecipeLogic 在机器中按以下顺序执行配方：

```
matchInputs → 检查启动消耗是否满足
   ↓
consumeInputs → 扣除启动消耗
   ↓
每 tick: checkTickIo → 检查逐 tick I/O
   ↓
每 tick: handleTickIo → 执行逐 tick I/O
   ↓
进度完成 → emitOutputs → 产出结果
```

## 配方缩放

`TopoRecipe` 缩放方法返回**新实例**（不可变）：

```java
TopoRecipe faster = recipe.withScaledDuration(0.5);
TopoRecipe doubled = recipe.withScaledAllContents(2.0);
TopoRecipe x4 = recipe.withParallel(4);
TopoRecipe scaled = recipe.withScaledCapabilityPerformance(
        cap, durationFactor, amountFactor);
```

速率计算：

```java
double itemsPerSec = TopoRecipe.contentPerSecond(baseCount, parallelCount, durationTicks);
```

## API 速查

| 类/方法                                | 用途              |
|----------------------------------------|-------------------|
| `TopoRecipe.Builder(type, id)`         | 配方构建器        |
| `.input(use)` / `.output(use)`         | 启动 I/O          |
| `.tickInput(use)` / `.tickOutput(use)` | 每 tick I/O       |
| `.duration(ticks)` / `.save()`         | 时间 + 注册       |
| `TopoRecipe.matchInputs(machine)`      | 检查消耗          |
| `TopoRecipe.consumeInputs(machine)`    | 执行消耗          |
| `TopoRecipe.handleTickIo(machine)`     | 执行一次 tick I/O |
| `TopoRecipe.emitOutputs(machine)`      | 产出结果          |
| `TopoRecipe.withParallel(n)`           | 并行缩放          |
| `TopoRecipe.contentPerSecond(...)`     | 计算速率          |
