---
sidebar_position: 3
title: 内容插件实作
---

# 内容插件实作

仓库的 `src/demo` 是独立开发模组 TopoDemo，通过公开 API 实现一条铜锡冶金生产链。
只在 Mod 构造期登记两个 Plugin，宿主统一执行生命周期；内容名及注册路径使用正常产品名称。
它不进入 Topo 的发布 JAR。

## 从声明追踪到实现

`api/` 放可复用的类型、策略和组件，`data/` 放具体参数、产品与配方。主要入口如下：

| 阶段钩子 | 实作入口 | 实际效果 |
| --- | --- | --- |
| `registerRecipeFoundation` | `BuiltinResources` | 物品资源类型、数据字段、名称、图标与类型化配方能力一起注册 |
| `registerRecipeTypes` | `BuiltinRecipeTypes` | 粉碎、合金两种配方类型与冶金生产线 |
| `registerMaterialFoundation` | `BuiltinMaterialDataTypes`、`BuiltinMaterialForms`、`BuiltinProcessors` | 质量数据、四种形态、可选粉碎后处理器 |
| `registerMaterials` | `BuiltinMaterials` | 铜、锡、青铜；由材料声明选择形态和处理器 |
| `registerMaterialCreativeTabs` | `BuiltinCreativeTabs` | RegistryLib 直接返回创造栏 `RegistryEntry`，图标延迟读取青铜锭 |
| `registerMaterialFollowUps` | `BuiltinMaterialFollowUps` | 形态生成后补公共物品标签和原版熔炼配方 |
| `registerEquipmentKinds` | `BuiltinEquipment` | 参数化调节器策略为三个材料分别生成工具 |
| `registerMachineFoundation` | `BuiltinMachineTypes` | 类型化模型策略、端口附件类型、输入输出角色和状态显示 |
| `registerMachines` | `BuiltinMachines` | 粉碎机、缓存、总线和多方块；显式挂载配方页与结构诊断页 |
| `registerMachineFollowUps` | `BuiltinPipes`、`BuiltinTools` | 轮询策略、过滤物品管道、勘测工具 |
| `registerOreFoundation` | `BuiltinOreTypes`、`BuiltinEnvironments` | 形状、两种生成通道、暴露和冲突策略、主世界石类宿主 |
| `registerOreDisplays` | `BuiltinVeinDisplays` | 两种通道对应的矿脉预览 |
| `registerOreVeins` | `BuiltinVeins` | 分散铜矿与铜锡混合网格矿床 |
| `registerOreDatagen` | `VanillaOreFeatureBridge` | 为本模组的单材料 Feature 矿脉生成原版世界生成数据 |
| `registerRecipes` | `BuiltinRecipes` | 矿石粉碎和铜锡合金配方，材料后处理器另外生成锭转粉配方 |
| `registerLang` | `ContentLang` | 操作反馈、工具说明；框架统一导出语言数据 |
| `registerRuntime` | `BuiltinToolPanels` | Entry 绑定后挂载工具面板；装备的机器行为由宿主自动绑定 |

## 声明方式

形态与装备遵循对应教程的 `builder().registryPath(...).displayName(en, cn)...build()` 结构。
策略将双语字符串铸造成 `TemplateDisplayName`，注册时通过 `RegistryDisplayLang` 桥接 RegistryLib。
桥接使用 Builder 原生的 `lang(Map)`，条目名称不再经独立语言表二次导出。
管道同样在 `displayName(en, cn)` 一处声明名称，不额外拼接方块语言键。
矿石方块使用 `.item(item -> item.addTab(...)).register()` 一次完成方块及物品注册。

创造栏只保留 `BuiltinCreativeTabs.KEY` 这个 `RegistryEntry`。形态在前置阶段保存
`() -> BuiltinCreativeTabs.KEY.getKey()`，生成内容时读取 key；机器阶段可以直接读取 key。
需要游戏实例的 `.get()` 只出现在延迟的数据生成、图标或运行时回调内。

材料后处理的标签使用 `itemTags().addIds(tag, MaterialHelper.itemId(material, form))`。
RegistryLib 的 `addSuppliers` 会立即调用 Supplier，此阶段不能传入依赖游戏注册表的材料物品 Supplier；
熔炼配方则在 `addRecipeData` 回调内取物品。

普通物品、创造栏、模型、标签和熔炼使用 RegistryLib 原生能力；领域内容通过 Plugin 的领域 API 声明。
管道使用 `plugin.machine().pipe(path)`，让注册项归属下游命名空间及其 RegistryCore。

## 客户端展示接线

配方逻辑和界面是独立组件：`BuiltinMachines` 为粉碎机挂载 `RecipeLogic` + `RecipeUi`，
为合金混合器挂载 `MultiblockController` + `MultiblockUi` + 带成型门控的 `RecipeUi`。
蓝图中的 `@` 既标识控制器原点，也须用 `.where('@', CellPredicates.controller())` 声明匹配。

`integration/jei/ContentJeiPlugin` 使用已存在的 JEI/LDLib2 依赖注册两类机器配方和矿脉分类，
并将 `XeiRecipeLookup` 接到 JEI 运行时；关闭 JEI 运行时会卸下插头。
机器配方预览复用冻结的规范机布局；仅有总线供料的多方块没有本地规范机时，按实际配方内容显示槽位。
矿脉分类通过模式对应的 `OreVeinDisplay` 构造预览，矿石槽位登记为可检索的生成结果。
单人世界从服务端配方表读取；多人客户端展示本内容在两端一致的直接声明。
该客户端回退不反映仅在远端通过数据包修改的配方，远端配方同步须另行接线。
这些客户端适配不负责游戏内容注册，也不在专用服务端加载。

## 手动验证

先执行静态检查，再手动生成资源及启动游戏：

```bash
./gradlew demoClasses compileTestJava spotlessCheck
./gradlew runClientData
./gradlew runServerData
./gradlew runClient
```

普通数据生成入口会先执行对应的 `runContentClientData` / `runContentServerData`，
为 `topodemo` 输出至 `src/generated/topodemo/client` 或 `src/generated/topodemo/server`，
再为 `topo` 输出至 `src/generated/resources` 或 `src/generated/serverResources`。
这些生成目录分别加入对应 source set 的资源目录；`src/demo/resources` 仅保留手写资源与模组元数据。
生成目录必须与手写资源隔离，客户端和服务端也使用独立目录，避免数据生成缓存清理其他文件。
也可以单独执行两个 `runContent*Data` 任务。RegistryLib 当前监听 `GatherDataEvent.Client`，
其模型、语言、标签与配方均须执行 `runClientData` 才会生成；`runServerData` 不能替代它。
生成前启动游戏尚无这些内容的模型、语言、标签和原版配方。

创造栏「材料与机器」应有 20 项：6 个锭/粉、4 个矿石、3 个调节器、5 台机器、1 根管道、1 个勘测仪。

1. 在粉碎机中放入铜锭：40 tick 产生 1 铜粉；普通/深层铜矿石在 80 tick 产生 2 铜粉。
   锡和青铜锭同样支持锭转粉；将粉熔炼回锭验证 RegistryLib 配方数据。
2. 合金混合器底层按 `输入总线 / 控制器 / 输出总线` 排列，上层放 3 个铁块。
   打开结构页确认成型，再切换配方页；输入 3 铜粉和 1 锡粉，100 tick 输出 4 青铜锭。
   缺少任一输入不得部分扣料；堵塞输出后应等待可用空间。
3. 用调节器右键配方机器验证暂停/继续；悬浮查看说明面板。
   机器和缓存内放入物品后重载世界，验证存储保留；拆除机器应掉落存储物品。
4. 用物品管道连接缓存与机器。持调节器潜行右键设置提取/输入端，普通右键配置批量、间隔、白名单和黑名单。
   点击配方页进度条应能定位 JEI 对应分类。
   多目标验证轮询；勘测仪验证锚定、端点选择和右键空气清除。
5. 在新生成的主世界区块检查普通/深层矿石；JEI 中检查两种矿脉预览和八张机器配方。
   原版 Feature 桥显式只支持单材料矿脉；加权铜锡混矿走 Grid 通道。
6. 切换中英文检查名称和反馈；再用 `./gradlew runServer` 检查独立服务端加载。

物品配方能力使用精确物品输入和具体物品输出，模拟及执行均通过 NeoForge 事务处理。
标签替代、催化剂、流体和标量资源可按配方能力教程扩展，不属于这条冶金链的配方语义。
