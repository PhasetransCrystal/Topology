---
sidebar_position: 3
title: 内容插件实作
---

# 内容插件实作

仓库的 `src/demo` 是独立开发模组 TopoDemo，通过公开 API 实现材料形态、原版条目复用及铜锡冶金生产链。
只在 Mod 构造期登记两个 Plugin，宿主统一执行生命周期；内容名及注册路径使用正常产品名称。
它不进入 Topo 的发布 JAR。

## 从声明追踪到实现

`api/` 放可复用的类型、策略和组件，`data/` 放具体参数、产品与配方。主要入口如下：

| 阶段钩子 | 实作入口 | 实际效果 |
| --- | --- | --- |
| `registerRecipeFoundation` | `BuiltinResources` | 物品资源类型、数据字段、名称、图标与类型化配方能力一起注册 |
| `registerRecipeTypes` | `BuiltinRecipeTypes` | 粉碎、合金两种配方类型与冶金生产线 |
| `registerMaterialFoundation` | `BuiltinMaterialDataTypes`、`BuiltinFormDataTypes`、`BuiltinMaterialForms`、`BuiltinProcessors` | 质量与颜色、形态数量载荷、十三种形态、锭／燃料／宝石／碎片粉碎后处理器 |
| `registerMaterials` | `BuiltinMaterials` | 七种金属及煤炭、木炭、红石、钻石、青金石、绿宝石、石英、紫水晶；分别选择形态和处理器 |
| `registerMaterialCreativeTabs` | `BuiltinCreativeTabs` | RegistryLib 注册材料、流体、机器与管道、装备与工具四个创造栏，图标延迟取值 |
| `registerMaterialFollowUps` | `BuiltinMaterialFollowUps` | 形态生成后补原版熔炼配方 |
| `registerEquipmentKinds` | `BuiltinEquipment` | 为具备质量数据与锭形式的金属生成调节器 |
| `registerMachineFoundation` | `BuiltinMachineTypes` | 类型化模型策略、端口附件类型、输入输出角色和状态显示 |
| `registerMachines` | `BuiltinMachines` | 粉碎机、缓存、总线和多方块；显式挂载配方页与结构诊断页 |
| `registerMachineFollowUps` | `BuiltinPipes`、`BuiltinTools` | 轮询策略、过滤物品管道、勘测工具 |
| `registerOreFoundation` | `BuiltinOreTypes`、`BuiltinEnvironments` | 形状、两种生成通道、暴露和冲突策略、主世界石类宿主 |
| `registerOreDisplays` | `BuiltinVeinDisplays` | 两种通道对应的矿脉预览 |
| `registerOreVeins` | `BuiltinVeins` | 分散铜矿与铜锡混合网格矿床 |
| `registerOreDatagen` | `VanillaOreFeatureBridge` | 为本模组的单材料 Feature 矿脉生成原版世界生成数据 |
| `registerRecipes` | `BuiltinRecipes` | 矿石粉碎和铜锡合金配方，材料后处理器另外生成锭、燃料、宝石、碎片转粉配方 |
| `registerLang` | `ContentLang` | 操作反馈、工具说明；框架统一导出语言数据 |
| `registerRuntime` | `BuiltinToolPanels` | Entry 绑定后挂载工具面板；装备的机器行为由宿主自动绑定 |

## 声明方式

形态与装备遵循对应教程的 `builder().registryPath(...).displayName(en, cn)...build()` 结构。
策略将双语字符串铸造成 `TemplateDisplayName`，注册时通过 `RegistryDisplayLang` 桥接 RegistryLib。
桥接使用 Builder 原生的 `lang(Map)`，条目名称不再经独立语言表二次导出。
管道同样在 `displayName(en, cn)` 一处声明名称，不额外拼接方块语言键。
矿石方块使用 `.item(item -> item.addTab(...)).register()` 一次完成方块及物品注册。

创造栏分别保存 `BuiltinCreativeTabs.MATERIALS`、`FLUIDS`、`MACHINES`、`EQUIPMENT` 四个 `RegistryEntry`。
形态与 BK 一致，直接使用 `BuiltinCreativeTabs.MATERIALS.getKey()` 等类型化键；
读取键会触发创造栏声明，不会解析图标或物品实例。装备仍保存延迟读取键的 Supplier。
固体形态进入材料栏，熔融桶进入流体栏，
机器和管道进入机器与管道栏，调节器和勘测仪进入装备与工具栏。
自有条目在绑定前不调用 `.get()`，实例读取放在数据生成、图标或运行时回调内。

参照 BreakdownKernel 当前 `ItemForm`／`BlockForm` 的职责划分，公共标签由形态策略负责，
物品、方块形态采用 BK 的 `commonTag("ingots")`／`commonTag("ores")` 公共组路径入口，
统一通过 `TagHelper` 构造 `c:` 命名空间下的组标签及材料子标签。
新条目通过 Builder 的 `addTag`／`addItemTag` 接线；原版覆盖通过既有 Entry 和 `addSuppliers` 接线。
RegistryLib 的 `addSuppliers` 会立即调用 Supplier，不能传入尚未绑定的条目；
熔炼配方则在 `addRecipeData` 回调内取物品。

普通物品、创造栏、模型、标签和熔炼使用 RegistryLib 原生能力；领域内容通过 Plugin 的领域 API 声明。
管道使用 `plugin.machine().pipe(path)`，让注册项归属下游命名空间及其 RegistryCore。

## 第一阶段：材料与流体

| 材料 | 锭／粒／粗矿／储存块／普通及深层矿石 | 粉／板 | 熔融态 |
| --- | --- | --- | --- |
| 铜、铁、金 | 复用原版的六个条目 | 新注册 | 新注册 |
| 锡 | 新注册六个条目 | 新注册 | 新注册 |
| 青铜、钢 | 新注册锭、粒和储存块，不声明粗矿及矿石 | 新注册 | 新注册 |

非金属及下界合金采用各自的原版形式，不按金属统一补锭、粗矿或流体：

| 材料 | 复用原版条目 | 新增形式 |
| --- | --- | --- |
| 煤炭 | 煤炭、煤炭块、普通／深层煤矿石 | 粉 |
| 木炭 | 木炭；没有储存块和矿石 | 粉 |
| 红石 | 红石粉、红石块、普通／深层红石矿石 | 无 |
| 钻石、青金石、绿宝石 | 宝石、储存块、普通／深层矿石；青金石物品 ID 为 `minecraft:lapis_lazuli` | 粉 |
| 石英 | 下界石英、石英块、下界石英矿石；没有深层矿石 | 粉 |
| 紫水晶 | 紫水晶碎片、紫水晶块；不声明矿石 | 粉 |
| 下界合金 | 下界合金锭、碎片、块及远古残骸 | 粉、板、熔融态 |

`METALS` 仅用于金属粉熔炼回锭；非金属粉不生成这种配方。标签跟随形态注册，不额外维护全材料清单。
燃料、宝石和紫水晶碎片分别显式激活相应粉碎后处理器；红石本身就是粉，不声明自转粉配方。
矿石粉碎按 `ORE_PROCESSING` 清单及实际声明的矿石形式生成；远古残骸不走直接转粉再熔炼成锭的路线，
下界合金碎片的后续合成留在后续阶段。

原版覆盖在材料声明行使用 `ExternalTargets.mcItem`／`mcBlock`。`ItemForm`、`BlockForm`
覆盖分支原样沿用 BK：通过 `existingItem`／`existingBlock` 取得既有条目，
用 `addSuppliers` 补标签、`addExistingSupplierToTab` 加入创造栏；不重复注册或改写原版名称。
这一路径要求覆盖目标在策略执行时已经可解析，运行验证需核对该前提。
物品与方块形式拒绝使用相反种类的覆盖选项。

形态实现由 BreakdownKernel 的 `api/material/common/form` 迁入：`ItemForm`、`BlockForm`、
`FluidForm`、`FormAmountDataType` 与 `NonPlaceableBlockItem`，渲染依赖同时迁入 `TintedTemplate`。
物品与方块通过可选 `render(...)` 配置模型，数据依赖由 Render 声明，不统一要求质量数据。
方块支持 `.block(...)`、`.blockItem(...)`、`.initialProperties(...)`、`.properties(...)`，并生成自身掉落。
`NonPlaceableBlockItem` 可通过物品工厂接入，本阶段的矿石、储存块仍使用普通可放置物品。
形态通过 `.data(...)` 保存自身载荷：锭为 72 单位、粒为 8 单位、储存块为 648 单位。

`FluidForm` 使用 `molten_%s` 作为源流体路径，通过 `FluidBuilder.create(...)` 的默认源流体、
方块与桶构建整组条目。材料通过 `PRIMARY_COLOR.rgb(...)` 声明 RGB 颜色，形态通过 `.tint(PRIMARY_COLOR)` 使用它；
`.properties(...)` 配置 FluidType，`.fluidProperties(...)` 配置流动行为。
熔融形态统一使用 1900 K、密度 7500、黏度 6000，属于验证参数；不再声明逐材料的混合流体属性包。
流体语言沿用 BK 的 Builder 接线，方块和桶由 RegistryLib 默认生成。
桶加入流体创造栏；熔融形态声明 `commonTag("molten_metals")`，流体加入 `c:molten_metals` 及材料子标签，
`FluidForm` 自身不绑定金属分类。纹理入口原样使用 BK 的本命名空间路径，不在本次迁移中补资源文件。
`MaterialHelper.requireFluid` 解析源流体；不能把流体形式交给 `requireItem` 当作桶使用。
本阶段不增加机器流体配方或流体端口。

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
./gradlew test
./gradlew runClientData
./gradlew runServerData
./gradlew runClient
```

`runClientData` / `runServerData` 均通过两个 `--mod` 参数生成 `topo` 和 `topodemo` 的资源。
客户端仍输出至 `src/generated/resources`，NeoForge 自动按 modid 分为 `topo/`、`topodemo/`；
服务端使用 `src/generated/serverResources`，避免独立生成器的缓存清理客户端输出。
`resources.gradle` 将各 modid 的目录接入对应 source set；手写资源与模组元数据仍留在原资源目录。
RegistryLib 当前监听 `GatherDataEvent.Client`，
其模型、语言、标签与配方均须执行 `runClientData` 才会生成；`runServerData` 不能替代它。
生成前启动游戏尚无这些内容的模型、语言、标签和原版配方。

当前开发依赖为包含多 Core locale 身份修复的 RegistryLib `26.43-SNAPSHOT`，本地修改库后需先在
RegistryLib 仓库执行 `./gradlew publishToMavenLocal`。生成后核对两个模组的三种语言、独立文本、
条目名称、创造栏及各领域代表资源；四个创造栏只填写英文和简体中文，
标题通过 `plugin.lang().resource(entry.identifier(), "itemGroup", en, cn)` 接入 Topo 语言管线，
由框架统一派生 `zh_tw`，创造栏不单独转换或注册繁体；
仅显式声明 en/cn 的勘测仪名称不要求 zh_tw。
当前 LDLib2 使用 `26.1.2.42`，与现有 JEI 的 typed ingredient 工厂对接；预览槽须显式传入 INPUT/OUTPUT。

四个创造栏预期共 102 项：材料栏 81 个固体材料条目（其中 48 个复用原版），
流体栏 7 个熔融金属桶，机器与管道栏 5 台机器和 1 根管道，装备与工具栏 7 个调节器和 1 个勘测仪。
该数量、分组及图标须以生成资源后实际启动验证。

1. 在粉碎机中放入铜锭：40 tick 产生 1 铜粉；普通/深层铜矿石在 80 tick 产生 2 铜粉。
   七种金属均支持锭转粉；将粉熔炼回锭，确认铜、铁、金、下界合金输出的是原版锭。
2. 合金混合器底层按 `输入总线 / 控制器 / 输出总线` 排列，上层放 3 个铁块。
   打开结构页确认成型，再切换配方页；输入 3 铜粉和 1 锡粉，100 tick 输出 4 青铜锭。
   缺少任一输入不得部分扣料；堵塞输出后应等待可用空间。
3. 用调节器右键配方机器验证暂停/继续；悬浮查看说明面板。
   机器和缓存内放入物品后重载世界，验证存储保留；拆除机器应掉落存储物品。
4. 用物品管道连接缓存与机器。持调节器潜行右键设置提取/输入端，普通右键配置批量、间隔、白名单和黑名单。
   点击配方页进度条应能定位 JEI 对应分类。
   多目标验证轮询；勘测仪验证锚定、端点选择和右键空气清除。
5. 在新生成的主世界区块检查普通/深层矿石；JEI 中检查两种矿脉预览和三十四张机器配方。
   原版 Feature 桥显式只支持单材料矿脉；加权铜锡混矿走 Grid 通道。
6. 切换中英文检查名称和反馈；再用 `./gradlew runServer` 检查独立服务端加载。
7. 七种熔融金属桶逐个放置、观察流动并用空桶取回源流体，确认桶、源流体和流动态对应同一种材料；
   重载世界后重复取回。检查颜色、桶模型和 en/cn/tw 名称，没有缺失纹理或语言键。
8. 检查 `c:ingots/copper`、`c:storage_blocks/iron` 等标签包含原版 ID，锡的标签包含 `topodemo` ID；
   七种金属各有 `c:molten_metals/<material>` 流体标签。注册表中不应出现 `topodemo:copper_ingot`、
   `topodemo:iron_block` 等重复固体条目，青铜和钢不应生成矿石。
9. 煤炭、木炭、四种宝石及紫水晶碎片分别放入粉碎机，确认输出对应粉；红石矿粉碎输出原版红石粉。
   确认没有红石自转粉、非金属粉熔炼回锭、深层石英矿石或非金属调节器。

物品配方能力使用精确物品输入和具体物品输出，模拟及执行均通过 NeoForge 事务处理。
标签替代、催化剂、流体输入输出和标量资源可按配方能力教程扩展，不属于本阶段机器配方语义。
