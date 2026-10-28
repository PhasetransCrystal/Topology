---
sidebar_position: 3
title: 内容注册实作
---

# 按内容链组织注册代码

先用[快速开始](../getting-started.md) 接通一个 TopoPlugin，再按本页组织完整的材料、机器和配方。
仓库的 `src/demo` 提供独立模组 TopoDemo：它和下游模组使用相同公开入口，不进入 Topo 发布 JAR。
文中的 `Builtin*`、`ItemForm`、`BlockForm` 等是 Demo 的产品实现，使用时需要在自己的模组实现或复用合作模组的公开 API。

## 1. 把类型与具体内容分开

建议将代码分成三个部分，规模较小时也可以放在一个插件内：

| 部分 | 放什么 | 示例路径 |
|---|---|---|
| 可复用 API | 数据类型、形态策略、机器组件、资源能力、生成策略 | `src/demo/java/net/ptcrys/topodemo/api/` |
| 具体内容 | 材料参数、机器组合、管道、矿脉和具体配方 | `src/demo/java/net/ptcrys/topodemo/data/` |
| 外部适配 | 可选模组接入、JEI 展示、其他插件的内容桥接 | `src/demo/java/net/ptcrys/topodemo/integration/` |

TopoDemo 在构造期登记 `TopoDemoApiPlugin` 与 `TopoDemoPlugin`，二者共用 `TopoDemo.REGISTRY`。
拆成两个插件便于组织代码；初始化时机仍由各钩子决定。
跨插件依赖按所属模组的依赖关系隐式确认，不再添加 Plugin 依赖配置；
模组存在并不保证某个静态字段或内容已经初始化，详见[跨插件兼容](../compatibility/topo-plugins.md)。

Demo 将声明放在静态字段中，并从所属钩子显式调用对应类的 `init()` 触发初始化。
这是 Demo 的组织方式，不是框架自动扫描机制。不要在模组构造器或更早的钩子访问这些字段，
否则可能在依赖未就绪时触发整组声明。

## 2. 按需要覆写注册钩子

| 注册位置 | 你的代码应做什么 | Demo 入口 |
|---|---|---|
| `registerRecipeFoundation` | 声明资源族和配方 I/O 能力；用同一资源类型连接机器端口与配方能力 | `BuiltinResources` |
| `registerRecipeTypes` | 声明机器配方类型、需要提前被后处理器引用的生产线 | `BuiltinRecipeTypes` |
| `registerMaterialFoundation` | 声明数据类型、形态策略和后处理器 | `BuiltinMaterialDataTypes`、`BuiltinMaterialForms`、`BuiltinProcessors` |
| `registerMaterials` | 创建材料，填写属性和外部形态覆盖；必要时给已构建材料补数据 | `BuiltinMaterials` |
| `registerMaterialCreativeTabs` | 注册需要读取最终材料集合的创造栏等内容，图标保持延迟取值 | `BuiltinCreativeTabs` |
| `registerMaterialFollowUps` | 对全部已匹配形态派生标签、原版配方等资源声明 | `BuiltinMaterialFollowUps` |
| `registerEquipmentKinds` | 声明装备策略，由框架按材料属性生成物品 | `BuiltinEquipment` |
| `registerMachineFoundation` / `registerMachines` | 先声明机器基础类型，再组合组件、端口、渲染和 UI | `BuiltinMachineTypes`、`BuiltinMachines` |
| `registerMachineFollowUps` | 声明管道、分发策略及配套工具 | `BuiltinPipes`、`BuiltinTools` |
| 矿脉相关钩子 | 先声明生成类型与显示，再创建矿脉、安装 datagen 桥 | `BuiltinOreTypes`、`BuiltinVeinDisplays`、`BuiltinVeins`、`VanillaOreFeatureBridge` |
| `registerRecipes` | 使用所有内容领域的句柄组装具体配方 | `BuiltinRecipes` |
| `registerLang` / `registerRuntime` | 分别声明独立文本、绑定真实物品的面板和交互 | `ContentLang`、`BuiltinToolPanels` |

每个钩子遍历所有插件后再执行该阶段的完成动作。只使用实际需要的钩子；
一般内容插件无需接触 `plugin.internal`、底层 `begin` 或手动冻结。
[生命周期](lifecycle.md) 提供完整时点，领域教程提供策略实现。

## 3. 声明材料，让规则决定生成内容

产品先定义共享数据，例如 Demo 的 `KIND`、`ORE_HOST`、`ORE_PRODUCT`，再用 `appliesTo` 选择形态。
材料注册行填写数据和覆盖选项：

```java
// registerMaterials 内；这些句柄由自己的 registerMaterialFoundation 初始化。
Material copper = material.material("copper")
        .lang("Copper", "铜")
        .data(KIND.kind(METAL))
        .data(PRIMARY_COLOR.rgb(0xB87333))
        .data(GRINDING.activation())
        .form(INGOT, options -> options.overrideItem(ExternalTargets.mcItem("copper_ingot")))
        .build();
```

`KIND` 等分类属于产品代码，Topo 没有统一的“金属”枚举。形态依据公开数据匹配，
这样新的材料和新的形态可以分别由不同插件贡献。
示例中的 `INGOT` 规则必须匹配该材料；配置覆盖本身不会使形态入选。

形态策略决定具体生成方式：

| 策略职责 | Demo 做法 | 复用时需要注意 |
|---|---|---|
| 生成物品／方块 | `ItemForm`、`BlockForm` 使用 `context.core()` 注册 | 命名空间跟随材料，名称交给生成条目的 Builder |
| 生成流体 | `FluidForm` 注册源流体、流动态、桶与方块 | 桶和源流体是不同注册项；`MaterialHelper.requireFluid` 查询源流体 |
| 复用既有条目 | 读取 `optionsFor(form)`，按 item/block override 走覆盖分支 | 原版条目可立即解析；其他模组条目可能尚未绑定 |
| 配置公共标签 | 自有条目通过 Builder 接线，外部条目通过 ID 接线 | 标签相同不等于物品 ID 相同，也不会自动改变配方匹配 |
| 派生后处理配方 | 材料显式声明 `processor.activation()` | 框架只调用激活组合；校验允许重复执行，生成放在 `process` |

外部条目的绑定边界及可选依赖写法见[兼容其他模组](../compatibility/other-mods.md)。

## 4. 组合机器与配方，保持资源句柄一致

机器使用 `machine.machine(path)`，管道使用 `machine.pipe(path)`。
资源端口、配方能力和管道 profile 应引用同一个公开的 `MachineResourceType`，
而不是分别创建同名资源类型。

TopoDemo 的粉碎机将物品端口、`RecipeLogic` 与 `RecipeUi` 挂到同一台机器；
多方块合金混合器另外挂 `MultiblockController` 与结构 UI。
具体配方在 `registerRecipes` 中组合已经声明的材料形态与配方类型：

```java
// items 是产品提供的能力；其 in/out 工厂接收 Supplier，不提前解析物品。
var input = MaterialHelper.materialItemSupplier(copper, INGOT);
var output = MaterialHelper.materialItemSupplier(copper, DUST);
GRINDING_TYPE.recipe("copper/to_dust")
        .input(items.in(input, 1))
        .output(items.out(output, 1))
        .duration(40)
        .productionLine(GRINDING_LINE)
        .save();
```

`copper`、`INGOT`、`DUST` 和配方类型应在前面的阶段就绪。
如果遍历其他插件的材料，先用 `hasForm` 和所需数据判断是否适用，配方路径同时保留材料命名空间，避免同名材料冲突。
TopoDemo 的 `ItemRecipeCapability` 只支持精确物品输入；通用标签输入需要能力实现对应匹配与传输。

## 5. 生成资源，在对象绑定后安装运行时行为

内容声明完成时，Entry 可能只完成排队。名称、模型、标签、原版配方等由资源 provider 生成；
真实物品面板和交互在 `registerRuntime` 绑定。将 `.get()` 放到真正允许读取对象的回调内，
并确认传入 Supplier 的 API 确实延迟调用它。

仓库的手动验证顺序是：

```bash
./gradlew demoClasses compileTestJava spotlessCheck
./gradlew runClientData
./gradlew runServerData
./gradlew runClient
```

`runClientData` / `runServerData` 选择 `topo` 和 `topodemo` 两个 modid，
输出分别位于 `src/generated/resources/<modid>` 和 `src/generated/serverResources/<modid>`。
RegistryLib 当前使用客户端 GatherData 入口，模型、语言、标签和其配方生成需要 `runClientData`；
`runServerData` 不能替代这一步。自己的项目应配置自己的 modid 和资源输出目录。

检查注册表里是否出现预期 ID、复用条目是否重复生成、名称与模型是否完整、配方输入输出是否正确。
兼容验证还要覆盖依赖模组存在／缺席以及专用服务端；详见[测试与验证](testing.md)。
