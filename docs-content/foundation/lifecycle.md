---
sidebar_position: 1
---

# Plugin Lifecycle

`TopoPlugin` 的钩子由 `TopoPluginEngine` 按固定顺序驱动。继承任何组件前，先确认**你的类在哪个钩子里注册**——这决定它能访问什么、以及它写入的注册表何时冻结。

## 钩子全表

| # | 钩子 | 冻结于此钩子之后 | 此时可访问 |
|---|------|------------------|------------|
| 1 | `registerRecipeFoundation(recipe)` | `RecipeCapabilities` | 机器资源类型、配方能力 |
| 2 | `registerRecipeTypes(recipe)` | `TopoRecipeTypes` | 已冻结的配方能力 |
| 3 | `registerMaterialFoundation(material)` | 材质四表（data/form/post-processor） | 尚未有材质实例 |
| 4 | `registerMaterials(material)` | `MaterialRegistry` | 已冻结的表单、数据类型、后处理器 |
| 5 | `registerMaterialCreativeTabs(material)` | — | 已冻结的材质 |
| 6 | （引擎内部） | — | 为每个材质铸造表单物品；先跑全部 `validate` 再跑全部 `process` |
| 7 | `registerMaterialFollowUps(material)` | — | 全部表单物品已存在 |
| 8 | `registerRecipes(recipe)` | `ProductionLines` | 全部材质 + 表单物品 |
| 9 | `registerEquipmentKinds(equipment)` | `EquipmentRegistry` | 全部材质（含数据）；之后引擎按 (材料 × 种类) 铸造物品 |
| 10 | `registerMachineFoundation(machine)` | 机器六表（resource type / UI icon / render / part role / property display / attachment） | 尚未有机器定义 |
| 11 | `registerMachines(machine)` | `Machines` | 已冻结的机器基础表；之后引擎铸造方块/BE 并安装预览计划 |
| 12 | `registerMachineFollowUps(machine)` | `PipeDistributionStrategies`、`Pipes` | 全部机器定义（方块/BE 已铸造） |
| 13 | `registerOreFoundation(ore)` | 矿石四表（shape / mode / air exposure / conflict） | 尚未有矿脉 |
| 14 | `registerOreDisplays(ore)` | `OreVeinDisplays` | 已冻结的模式表；每种模式必须有对应显示 |
| 15 | `registerOreVeins(ore)` | `OreVeins` | 已冻结的矿石基础表 |
| 16 | `registerOreDatagen(ore)` | — | 全部矿脉 |
| 17 | `registerLang(lang)` | `LangRegistry` | 全部 |

## 引擎顺序

宿主 `@Mod` 构造器注册插件后，在首个 `RegisterEvent`（HIGHEST 优先级）上调用：

```java
TopoPluginEngine.prepare();          // 钩子 1–9（内部锁定 TopoPlugins）
TopoPluginEngine.bootstrapMachine(); // 钩子 10–12
TopoPluginEngine.bootstrapOre();     // 钩子 13–16
TopoPluginEngine.bootstrapLang();    // 钩子 17
```

:::caution
`prepare()` 会锁定插件表——此后 `TopoPlugins.register(...)` 直接抛异常。所有插件必须在 `@Mod` 构造器里注册完毕。
:::

:::caution
冻结点后写入抛异常。例如自定义 `MaterialForm` 必须放 `registerMaterialFoundation`（钩子 3），
`registerMaterials`（钩子 4）里只能引用。
:::

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `TopoPlugins.register(plugin)`         | 注册插件（引擎启动前）          |
| `TopoPlugins.view()` / `isLocked()`    | 插件列表 / 锁状态               |
| `TopoPluginEngine.prepare()`           | 钩子 1–9（锁定插件表）          |
| `TopoPluginEngine.bootstrapMachine()`  | 钩子 10–12                      |
| `TopoPluginEngine.bootstrapOre()`      | 钩子 13–16                      |
| `TopoPluginEngine.bootstrapLang()`     | 钩子 17                         |
