---
sidebar_position: 6
---

# 多方块蓝图

`Blueprint` 描述相对控制器的结构布局、方块匹配规则和部件数量约束。
将其通过 `MultiblockController.mount(...)` 挂载到机器后，框架负责结构检测、成型状态、部件接入和诊断。

本页是 [Machine](../machine.md) 的子页。部件角色与状态显示的注册见 [多方块能力](multiblock-ability.md)。
游戏内检测、世界虚影和按配置补建见 [多方块结构工具](multiblock-tool.md)。
检测契约已迁移为 `CellPredicate<I>`、`CellBinding` 与结构元数据；放置扩展、工具解耦和提供器链仍在后续范围，见 [设计与实施范围](multiblock-design.md)。

## 局部坐标与朝向

蓝图在局部坐标系中定义，以控制器 `@` 为原点。`aisle(rows...)` 声明一个 XZ 字符层：
字符沿声明坐标的 `+X` 排列，行沿 `+Z` 排列，连续的 `aisle` 沿 `+Y` 堆叠。
构建时，所有声明位置都归一化为相对 `@` 的坐标。

`frame(up, front)` 设置整个声明相对于控制器的旋转：声明的 `+Y` 映射到 `up`，
`+Z` 映射到 `front`，`+X` 映射到 `up × front`。两方向必须垂直；默认是 `UP` 和 `SOUTH`。
前向与上向的有效组合共有 24 种正交旋转。

世界位置由控制器位置和有效旋转确定：

```text
有效旋转 R = 控制器朝向 ∘ frame
世界位置 = 控制器世界位置 + R × 声明中的控制器相对位置
```

先应用 `frame`，再应用控制器朝向。引擎的 `orientation` 参数表示控制器朝向，
`Blueprint.effectiveOrientation(orientation)` 返回组合后的旋转。

`Orientations.rotations()` 返回 24 种旋转；`Orientations.all()` 还包含 24 种镜像变换。
当前控制器运行时从 `FACING` 通过 `Orientations.forFacing(...)` 选择预设朝向，普通放置使用水平前向。
镜像属于结构检测流程，不是蓝图的许可开关。检测主朝向确定失败后自动尝试镜像；
两者都确定失败时选择匹配比例较高的布局供诊断，未知区块按原有规则保留未知状态。

## 字符与匹配定义

| 字符 | 当前实现中的含义 |
|---|---|
| `@` | 控制器锚点，必须恰好出现一次；框架自动定义控制器谓词 |
| `#` | 空气位置，框架自动定义空气谓词 |
| 空格 | 忽略该位置，不要求放置或清除任何方块 |
| 其他非空字符 | 使用 `where` 绑定谓词，可附加规范状态和属性规则 |

`@`、`#` 是保留符号，不能通过 `where` 重新定义。空气参与匹配、缺失诊断和占地监听；空格不参与。

所有层必须是相同尺寸的矩形。普通符号的定义不能重复、缺失或未被使用。
全空格层可以保留层间距离，但不产生需要匹配的单元格。

## 定义并挂载结构

下面的蓝图为 4×4×6，包含 96 个检测位置；`@` 位于第二层，底层相对控制器位于局部 `Y=-1`。

```java
Blueprint blueprint = Blueprint.of()
    .aisle("CCCC", "CCCC", "CCCC", "CCCC")
    .aisle("C@CC", "C##C", "C##C", "CCCC")
    .aisle("CCCC", "C##C", "C##C", "CCCC")
    .aisle("CCCC", "C##C", "C##C", "CCCC")
    .aisle("CCCC", "C##C", "C##C", "CCCC")
    .aisle("CCCC", "CCCC", "CCCC", "CCCC")
    .where('C', Blocks.IRON_BLOCK)
    .build();

// 在 registerMachines(machine) 中注册
machine.machine("my_multiblock")
    .displayName("My Multiblock", "大型多方块机器")
    .block(FormedMachineBlock::new)
    .component(MultiblockController.mount(blueprint))
    .build();
```

`FormedMachineBlock` 提供成型外观状态。需要配方与结构 UI 时，另行挂载对应组件；
控制器通过 `RecipeCondition` 服务提供成型条件，通过成员部件提供配方资源。

## 重复段与可选段

`beginRepeatable(min, max)` 到 `endRepeatable()` 之间的连续层作为一组重复。
每份副本保持组内层序，沿声明坐标 `+Y` 按整组厚度展开；后面的层随重复次数移动。
控制器可以位于重复段之前或之后，但不能包含在重复段内。重复段不能嵌套，且必须包含非空单元格。

```java
Blueprint tower = Blueprint.of()
    .aisle("@")
    .beginRepeatable(1, 3)
    .aisle("A")
    .aisle("B")
    .endRepeatable()
    .aisle("T")
    .where('A', CellPredicates.block(Blocks.IRON_BLOCK))
    .where('B', CellPredicates.block(Blocks.COPPER_BLOCK))
    .where('T', CellPredicates.block(Blocks.GOLD_BLOCK))
    .build();
```

此例的重复单元为铁块层加铜块层，副本数为 1～3，顶端金块层随之移动。
也可写为 `.repeat(1, 3, section -> section.aisle("A").aisle("B"))`。

`min=0` 表示可省略该段。省略只移除该段并调整后续布局，不会额外要求原位置为空气。
使用多个重复段时，每段分别记录自己的副本数。

引擎先检查必需副本，再探测额外副本，并检查相关的后续固定单元格；
控制器之前的重复段先解析到稳定计数，再进行最终检测。

检测返回的 `RepeatResolution` 按重复段声明顺序索引：

```java
result.repeats().count(0);        // 第一段的副本数，不是展开后的层数
blueprint.cells(result.repeats()); // 该计数下的规范坐标单元格
// 施工或目标诊断可明确指定布局，避免未建齐的世界使目标重复次数缩短：
StructureEngine.recognize(view, blueprint, orientation, new RepeatResolution(new int[] { 2 }));
```

成型控制器可通过 `repeatResolution()` 或 `repeatCount(sectionIndex)` 查询；
未成型时分别返回 `null`、`0`。

### 将重复方向旋转到控制器局部 X

```java
Blueprint horizontal = Blueprint.of()
    .frame(Direction.EAST, Direction.SOUTH)
    .aisle("@")
    .beginRepeatable(1, 3)
    .aisle("A")
    .endRepeatable()
    .where('A', CellPredicates.block(Blocks.IRON_BLOCK))
    .build();
```

这里 `frame` 将整个声明的 `+Y` 映射为控制器局部 `+X`，同时旋转其他轴和协变状态属性。

## Cell 检测与内容声明

`CellPredicates` 的工厂返回 `CellBinding` 内容声明，可通过 `anyOf`、`allOf`、`not` 组合。检测算法本身不绑定具体内容：

```java
CellPredicates.block(Blocks.IRON_BLOCK);              // 方块类型
CellPredicates.state(Blocks.STONE.defaultBlockState()); // 精确状态
CellPredicates.air();
CellPredicates.replaceable();
CellPredicates.blockTag(BlockTags.MINEABLE_WITH_PICKAXE);
CellPredicates.property(BlockStateProperties.LIT, true);
CellPredicates.role(itemInput);                      // 指定部件角色
CellPredicates.member();                             // 机器成员
CellPredicates.controller();                         // 控制器位置
CellPredicates.anyOf(predicateA, predicateB);
CellPredicates.allOf(predicateA, predicateB);
CellPredicates.not(predicateA);
```

统一检测接口为：

```java
@FunctionalInterface
public interface CellPredicate<I> {
    CellResult evaluate(I input, CellContext context, DataTransaction metadata);
}
```

`input` 是本次内容声明，`CellContext` 包含一次采集的状态、角色、成员位置、世界坐标、控制器标记和已经变换的规范状态。
检测器必须可重入，只读取这些事实；不能在异步线程临时读取世界，也不能将本轮状态保存在检测器实例中。
`StructureView` 对已加载空气返回空气状态；`null` 表示未提供状态事实，依赖该事实的检测返回 `UNKNOWN`，不能用稀疏 Map 的空返回值代替空气。

```java
CellPredicate<TagKey<Block>> detector = (tag, context, metadata) -> {
    BlockState actual = context.actual();
    if (actual == null) return CellResult.unknown();
    return actual.is(tag) ? CellResult.pass()
        : CellResult.fail(myErrorId,
            Map.of("tag", tag.toString()), Map.of("state", actual.toString()));
};
CellBinding custom = CellBinding.of(detector, BlockTags.MINEABLE_WITH_PICKAXE);
```

`CellResult` 区分 `PASS`、`FAIL`、`UNKNOWN`，携带命名空间错误码、期望／实际数据和子错误。
引擎的 `RecognitionResult.CellIssue` 补充声明坐标与世界坐标；诊断根据结果分类，不重新调用布尔谓词猜原因。
`missingCells()` 与 `unknownCells()` 分别保存最多 64 条最终布局样本；`unknownCount()` 还包含增长探测未知，`unloadedCount()` 仅统计未加载位置。

候选与角色分别通过 `CellBinding.candidateBlocks()`、`partRoles()` 读取；`Cell.binding()` 返回内容声明。
候选只是声明，不证明可以放置，检测器也不构造 `ItemStack`。`Reason.iconBlocks()` 保存方块，由展示层创建图标。
外部错误保留在 `Reason.cellResult()` 中；翻译键约定为 `multiblock.cell.<命名空间>.<错误路径>`，路径中的 `/` 换为 `.`，未提供翻译时显示原错误码。

### 结构元数据

控制器在自身 `data().group("structure_metadata")` 中声明 `DataValue<T>`，正式存储仍属于机器原有的 `MachineDataScope`。
接入方声明 `DataKey<T>`，提供 Codec、默认值和复制函数，再通过 `CellBinding.metadata(key...)` 预声明；同一个键应复用同一个实例。
真正不可变的值可用 `DataKey.immutable(...)`；可变值必须提供纯复制函数。值不能持有世界、实体或玩家，默认值也不能作为“已探测”的标志。

```java
DataKey<String> observed = DataKey.immutable(myMetadataId, Codec.STRING, "");
CellPredicate<DataKey<String>> detector = (key, context, metadata) -> {
    // 在此提取事实；已有值时先读取校验，未探测时才写入。
    if (!metadata.assigned(key)) metadata.set(key, extractValue(context));
    return checkValue(context, metadata.get(key));
};
CellBinding content = CellBinding.of(detector, observed).metadata(observed);
```

`DataSnapshot` 隔离发布值，`DataTransaction` 支持 `fork()`、`accept()` 与丢弃分支。
Cell、组合分支、重复段试探和失败可选模块均不把失败写入带到正式结果。
同一轮本体与所有模块共享元数据，互不相关的结构实例各有自己的工作区。
`StructureMetadata.ORIENTATION` 与 `REPEATS` 由引擎维护，外部能力读取这些字段，不改写其含义。
重复次数使用 `body` 与 `module/模块ID` 区分。

正式字段当前采用持久化、不直接广播客户端的策略；通用 `DataScope.valueField(key)` 仍可自行声明原有同步策略，菜单继续使用 UI 同步。
外部数据含义或编码发生不兼容变化时应变更键的版本／ID。恢复的字段参与重检，不因读取到存档值直接宣告成型。

`StructureDetection.detect(...)` 是本体加全部模块的整体检测入口；两个镜像使用各自的工作区，主体对称时按接入模块数选择整体朝向。
周期校验读取已确认基线，不能通过覆写元数据让变化后的结构蒙混过关。
需要明确重新探测时调用 `MultiblockController.requestProbe()`；未知及异步在途阶段保留原正式元数据，完整提交后才替换。
解体使基线失效，区块暂不可读则延后重试。

### 预览坐标

机器预览以**控制器本体的正向**为坐标基准，控制器固定朝 SOUTH。
`Blueprint.cells(...)` 仍使用声明坐标；预览先应用 `frame`，镜像结构还需应用整体镜像，不把世界中的控制器旋转带入预览。
状态按同一变换显示，控制器本体自身不随声明 frame 或结构镜像翻转。
结构页的点击位置、问题定位与实建图均使用这一控制器坐标，查询检测引擎时再转换回声明坐标与世界坐标。

### 机器结构页与结构示例

机器结构页每次刷新使用客户端整体检测得到的本体与全部模块布局，各自采用本次解析出的重复次数和声明 frame，共享整体镜像。
所需图、实建图、位置详情及切层范围同步更新；打开界面后增加或减少重复段、补建模块，不需要重新打开界面。
共享控制器和重叠位置只显示一次。可选模块未搭建时仍可在所需图查看其布局；模块位置的详情读取该模块自己的视图和检测元数据。
报告的成型结论与匹配统计仍针对本体，可选模块缺失不代表本体成型失败。

`MultiblockPreviewPage.build(definition)` 提供不依赖 JEI 的结构示例界面，由 Topo 自带的 `TopoMultiblockJeiPlugin` 承载。
框架统一收集已注册且带有 `MultiblockControllerMetadata` 的机器，内容模组无需重复注册结构展示；分类 ID 为 `topo:multiblock`。
全部机器共用一个 JEI 结构分类；每条预览按自己的控制器建立配方／用途查询索引，查询某台机器时只匹配对应结构。
Topo 的客户端 Mixin 让原生工作方块栏跟随当前可见预览，翻页或返回后同步刷新；同屏有多个结构时显示这些结构的去重控制器。
不把控制器注册成整个结构分类的通用工作方块，也不按机器增加分类页签。其他配方分类使用 JEI 原有逻辑；结构分类、语言键与 `topo.mixins.json` 中的客户端注入均随框架发布。
界面包括结构尺寸、拖动旋转、切层、重置、独立放大窗口，以及按候选和角色数量限制分组的材料清单；点击场景方块会高亮对应材料行。
可切换本体、指定模块（连同其依赖）或全部模块，并调整所显示蓝图的各重复段次数，场景、尺寸和材料数量一起重建。
这些选择只影响示例，不修改世界、机器配置或成型检测。紧凑与放大窗口各自保存展示选项。

## 方向性状态与数量约束

`where(symbol, predicate, expectedState, rules...)` 中的 `expectedState` 是旋转前的规范状态。
引擎先使用谓词判断，再比较经属性规则处理后的预期状态。

```java
Blueprint directional = Blueprint.of()
    .aisle("@D")
    .where('D', CellPredicates.block(Blocks.DISPENSER),
        Blocks.DISPENSER.defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.SOUTH),
        PropertyRules.direction(BlockStateProperties.FACING))
    .build();
```

| 工厂 | 行为 |
|---|---|
| `PropertyRules.direction(properties...)` | 旋转方向属性；属性的合法值必须能表达该旋转 |
| `PropertyRules.axis(property)` | 旋转轴属性 |
| `PropertyRules.half(property)` | 上下半格随竖直轴保持或翻转；不支持侧倾到水平轴 |
| `PropertyRules.derived(properties...)` | 比较时屏蔽由邻居等因素派生的属性 |

引擎只变换 `COVARIANT` 规则；`FIXED` 不参与旋转，规则声明的屏蔽属性不参与预期状态比较。
无法表达所选有效旋转时，完整检测返回 `ORIENTATION_UNSUPPORTED`。
固定规范状态的比较自动屏蔽 `WATERLOGGED`；需约束该值时通过谓词表达。
携带机器运行时 `FORMED`、`ACTIVE` 属性的规范状态必须显式屏蔽相应属性，否则构建会拒绝。

`count(role, min, max)` 约束匹配单元格中某个部件角色的数量：

```java
Blueprint withInput = Blueprint.of()
    .aisle("@PP")
    .where('P', CellPredicates.anyOf(
        CellPredicates.block(Blocks.IRON_BLOCK),
        CellPredicates.role(itemInput)))
    .count(itemInput, 1, 2)
    .build();
```

角色由部件挂载声明，注册和接入方式见 [多方块能力](multiblock-ability.md)。

## 命名模块

模块是与主蓝图共用控制器世界锚点的额外蓝图。它有自己的尺寸、重复段、`frame` 和数量约束，
可与主蓝图或其他模块重叠。

```java
Blueprint core = Blueprint.of().aisle("@").build();
Blueprint gold = Blueprint.of()
    .aisle("@G")
    .where('G', CellPredicates.block(Blocks.GOLD_BLOCK))
    .build();
Blueprint diamond = Blueprint.of()
    .aisle("D@")
    .where('D', CellPredicates.block(Blocks.DIAMOND_BLOCK))
    .build();

MultiblockController.mount(core,
    MultiblockModule.of("gold", gold),
    MultiblockModule.of("diamond", diamond, "gold"));
```

所有模块均为可选扩展，不作为主结构的必需条件。默认彼此独立；上例显式声明 diamond 依赖 gold。
模块只有自身结构完整且所有依赖满足时才接入部件，部分匹配视为缺失。
挂载时校验重复 ID、未知依赖和依赖环，检测时按拓扑顺序求值。
模块的 `@` 参与结构匹配，但不计入模块报告的匹配数与总数。

控制器提供 `moduleReports()`、`moduleReport(id)`、`satisfiedModules()`、
`moduleRepeatResolution(id)` 和 `moduleRepeatCount(id, sectionIndex)`。
`machineBounds()` 返回主结构与已满足模块的世界边界并集。

## 检测与运行时

`StructureEngine` 的两个入口均接收 `StructureView`、蓝图和控制器朝向：

| 入口 | 返回内容与用途 |
|---|---|
| `recognize(view, blueprint, orientation)` | 完整检测结果：成型状态、角色计数、成员位置、重复次数、边界与缺失样本 |
| `verify(view, blueprint, orientation)` | 快速维护结果：成型状态、重复次数、首个不匹配位置与未加载标记 |

缺失样本最多保存 `StructureEngine.MISSING_SAMPLE_CAP`（64）项，总缺失数单独记录。
`BlueprintLayout` 统一解析某个 `RepeatResolution` 下的控制器相对坐标；`cells`、检测、世界占地和快照反查共用该布局。
`layout.forEachCell(...)` 可直接访问坐标，避免展开所有 `Cell` 对象。单元格编号以最大副本数为基准，缩短重复段不会重排其他单元格。

`StructureView` 提供方块状态、角色和成员位置；实时视图只能在世界所属线程使用。
异步检测使用不持有实时方块实体的 `CompiledSnapshot`，它必须属于同一蓝图及控制器朝向。
快照仅支持位置不随重复计数移动的布局；构建器 `build()` 后不可再写入或重复发布。
固定次数重复段（`min == max`）只产生常量位移，后接顶层或控制器不会因此使布局失去快照资格；可变次数段仍需检查后续位置和锚点是否移动。
`verify.firstMismatchPos()` 在没有方块不匹配时为 `null`；世界原点也是有效的失败位置。

运行时由 `MultiblockController` 与 `MultiblockRecheck` 协作：

1. 监听占地变更，标记控制器待检；已成型结构也按配置周期进行兜底检查。
2. 优先定点重测变更单元格；涉及重复增长、角色计数、成员替换或模块可能占地时执行完整检测。无模块的已成型结构可使用 `verify` 维护。
3. `supportsSnapshot()` 为真时采集最大占地快照；达到 `MULTIBLOCK.asyncCellThreshold` 后可异步识别。其他布局使用实时视图同步检测。
4. 异步结果回到服务端线程后检查任务、朝向及脏标记，再处理独占部件预留、成员接入和成型外观。
5. 遇到 `CHUNK_UNLOADED` 保留当前成型状态，按 `MULTIBLOCK.unloadedRetryIntervalTicks` 延迟重试。

普通结构方块和共享部件不会被独占。带 `ExclusivePartMount` 标记的成员由成型控制器预留；
主蓝图独占冲突阻止成型，模块独占冲突使对应模块及依赖它的模块失效。
`MultiblockClaimIndex` 只管理占地、监听和独占预留，不持有世界或生产快照；世界采集由 `StructureCaptures` 负责。
`tryClaim(...)` 返回 `ClaimUpdate`：`accepted=false` 时旧预留完全保留；成功替换且 `released` 非空时，控制器唤醒相交监听者。
释放范围包含主占地之外的模块独占部件，模块退出而主体仍成型时也会释放并唤醒竞争者。

客户端诊断由 `MultiblockClientDiagnosis` 展示，结构预览使用 `BlueprintPreview`。
诊断总数取解析布局，匹配数取 `RecognitionResult.matchedCount()`，不会把未加载位置算作已匹配或把额外增长探测计入总数。
未知区块与不支持的朝向各有单独提示；“全部匹配”以引擎的 `formed()` 为准。
`RecognitionResult.partCount()` 按携带角色的位置计数，同一位置有多个角色也只计一次。
客户端通过 `MultiblockClientDiagnosis.capture(controller, blueprint)` 创建一次刷新的 `Frame`。报告 `frame.report(max)`、单格详情 `frame.diagnoseCell(localPos)`、预期／实际状态与 `frame.detectedBlocks()` 共用本次识别的朝向和视图；主朝向及镜像的选择与服务端一致。单格直接检测，不受完整报告 64 条缺失样本的限制。稳定布局持有快照，移动布局持有实时视图，后者须在同一客户端世界线程及时使用。

## Demo 与验证

`src/demo/java/net/ptcrys/topodemo/data/machine/BuiltinMachines.java` 提供以下结构机器。
尺寸按声明的字符宽 × 行深 × 展开层数记录；`axis_tower` 的 frame 将层数映射到控制器局部 X。
空气计入检测位置，空格不计入。以下均为真实最小布局尺寸，不按最大重复数凑尺寸。

| 机器 ID | 最小尺寸 | 最小检测位置 | 验收内容 |
|---|---|---|---|
| `alloyer` | 4×4×6 | 96 | 输入输出总线各一个，配方资源接入 |
| `repeatable_tower` | 5×5×6 | 150 | 106 个实体方块；控制器下方 1～2 层、上方 1～3 份双层重复组、移动金顶、可选原木横梁 |
| `variable_tower` | 4×4×6 | 96 | 3～7 层铜壳中段及移动金顶 |
| `layered_tower` | 4×4×6 | 96 | 四层实心底座、2～4 层铁壳及可选金壳 |
| `grouped_tower` | 4×4×7 | 112 | 铁壳加铜壳为一组，重复 2～4 份，后接实心金顶 |
| `axis_tower` | 4×4×6 | 96 | frame 旋转后的局部 X 向扩展，4～8 层铁壳 |
| `optional_module_machine` | 主体 4×4×6 | 主体 96 | 左右各一座 4×4×6 可选舱，共用控制器锚点 |
| `dependent_module_machine` | 主体 4×4×6 | 主体 96 | 左钻石舱依赖右金舱；舱体各 96 个位置，模块报告排除共用的 `@` |
| `predicate_machine` | 4×4×6 | 96 | 内腔空气、石头精确状态、未点燃熔炉、主世界基岩石标签及安山岩组合谓词 |

除 `alloyer` 外，这些结构机器挂载控制器和结构 UI，不包含配方逻辑。
`repeatable_tower` 使用铜壳、石砖底座、玻璃组层、金壳顶盖；控制器所在层指定一个输入总线和一个输出总线。
每个处理组的中心放置朝声明 `EAST` 的发射器；可选原木横梁的轴为声明 `X`，两者都随有效旋转改变。
`#` 位置须清空；可选横梁声明中的空格不要求清空。左右模块通过空格跳过主体，舱体位于主体两侧，和主体保留间隔。

组合回归源码包括：

- `ComplexBlueprintTest`：最小 5×5×6、最大 5×5×12；独立施工样本覆盖 24 向、两种 frame、12 种重复计数组合、空气损坏、方向错误、跨区块未知、成员及角色上下限。另有最大 500 格稳定快照与实时视图的结果对照、采集编号双射及 75 格损坏的诊断上限。
- `MultiblockModuleEvaluationTest`：两座最小 5×5×6 的可变长舱体，覆盖 24 向、逆序声明依赖、局部损坏、依赖失效，以及增长探测未知时的总数。
- `AsyncStructureServiceTest`：4×4×6 非对称实心结构，对照实时视图与快照的主／镜像选择和未知结果传播。
- `MultiblockClaimIndexTest`：4×4×6 主舱及远端模块，覆盖原子冲突拒绝、预留替换、释放唤醒、共享占地、24 向监听与具体布局反查、可变坐标隔离。
- `MultiblockClientDiagnosisTest`：4×4×6 诊断，覆盖未知及不支持状态、增长探测总数、同位置多角色部件计数，超过 64 条样本后的单格查询，24 向镜像结构的报告、详情和预览一致性，以及未绑定物品组件时的候选元数据与缺失报告。
- `StructureEngineTest`：保留小型边界用例，定位尺寸校验、单段增长、同材质顶盖等问题。
- `StructureDetectionTest`：4×4×6 外部能力示例，覆盖 48 个整体变换、跨模块事实共享、周期基线、失败模块和增长回滚、未知及组合错误。
- `DataTransactionTest`：可变值隔离、显式提交、默认值也可作为已探测事实、有限字段刷新与声明冲突。
- `BlueprintPreviewCoordinatesTest` 与诊断扩展用例：非默认 frame 下的控制器正向坐标、镜像属性与 24 向点击定位。
- `MultiblockCompositePreviewTest`：304 格展开本体与侧舱、24 向及整体镜像、模块独立 frame 的状态与点击定位、增长后收缩、模块独立快照读取，以及固定／可变重复次数下顶层和控制器锚点的快照资格对照。

按项目约定，由开发者手动运行 NeoForge 测试和游戏验收：

```bash
./gradlew test --tests 'net.ptcrys.topo.api.machine.multiblock.*' --tests 'net.ptcrys.topo.api.machine.data.*' --rerun
./gradlew runClientData
./gradlew runClient
```

新增诊断语言键须先通过 `runClientData` 生成语言资源。游戏内使用结构页预览搭建，分别检查最小和最大重复数、可选段移除及恢复、非对称结构转向、内腔填入方块、发射器朝向错误、总线拆换、模块最后一块补齐、依赖舱拆除，以及跨区块卸载／重载。
预览验收还应保持机器界面打开，增减重复段、补建或移除模块，切换所需／实建图并检查切层和模块详情；在 JEI 中调整模块、重复数、材料行高亮，打开大图后关闭返回原界面。
运行编译或格式检查不代表这些运行场景已通过；以手动执行的测试报告和游戏日志为准。
构建环境、完整测试命令和日志位置见 [测试与验证](../foundation/testing.md)。
