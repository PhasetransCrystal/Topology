---
sidebar_position: 3
---

# Recipe

Topology 配方系统基于 **能力**（capability）I/O 模型，支持启动消耗、每 tick I/O、并行执行和配方缩放。

## 何时阅读

为机器添加配方处理逻辑时。配方通过 `RecipeCapability` 连接机器的资源端口。

## 注册配方类型

```java

@Override
public void registerRecipeTypes(RecipeDomainRegistration recipe) {
    recipe.recipeType("macerating")
            .name("Macerating", "粉碎")
            .build();

    recipe.recipeType("smelting")
            .name("Smelting", "冶炼")
            .build();
}
```

## 创建配方

```java
// 在 registerRecipes 阶段，通过 RecipeCapability 的输入/输出工厂方法
TopoRecipe recipe = new TopoRecipe.Builder<>(maceratingType, "mymod:iron_dust")
                .input(itemCapability.in(Items.IRON_ORE, 1))      // 启动消耗：1 铁矿石
                .output(itemCapability.out(IRON_DUST, 2))           // 启动产出：2 铁粉
                .tickInput(scalarCapability.in(scalar(128)))        // 每 tick 消耗 128 EU
                .duration(200)
                .productionLine(ProductionLines.MACERATING)
                .save();
```

## Builder 方法一览

```java
new TopoRecipe.Builder<>(recipeType,"modid:path")
    .input(capability.in(...))            // 启动消耗
    .output(capability.out(...))          // 启动产出
    .tickInput(capability.in(...))        // 每 tick 消耗
    .tickOutput(capability.out(...))      // 每 tick 产出
    .duration(200)                         // 配方时间（tick），默认 100
    .productionLine(line)                  // 关联生产线
    .replacesImported()                    // 覆盖 datapack 配方
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

`TopoRecipe` 缩放方法返回 **新实例**（不可变）：

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
