---
sidebar_position: 7
---

# 多方块结构工具

面向希望接入多方块结构工具的模组开发者。以下描述现有接口和 Demo 的交互方式；工具仍处于原型阶段，行为可能变化。

Demo 的 `StructureToolItem`（`topodemo:structure_tool`）提供游戏内结构检测、失败报告、世界虚影和按配置补建；
框架保留 `StructureTool` 标记接口、`StructureToolManager` 会话能力、玩家配置页及虚影协议。
蓝图、控制器朝向及叠加模块的定义见 [多方块蓝图](multiblock.md)。

## 操作

| 操作 | 结果 |
|---|---|
| 右键多方块控制器 | 检测选中的本体或模块，在聊天栏报告结果，并显示世界虚影 |
| 右键空气或其他方块 | 打开工具配置页 |
| Shift + 右键多方块控制器 | 根据当前配置尝试补建选中的本体或模块，然后重新检测 |

配置保存到玩家数据并同步给该玩家；不同等级或物品的结构工具共享同一份配置，不写入物品栈。
文本字段需要点击“应用”；选择按钮点击后立即提交。服务端校验配置，配置页绑定玩家而不是某一件工具。

虚影模型保留方块贴图原色、原版与扩展染色及面亮度，仅添加透明度。
线框表示状态：青色为缺失，红色为已有方块冲突，黄色为位置不可读取，紫色为候选状态无法确定。
空气要求发生冲突时只显示线框。已匹配的位置使用世界中的实物，不叠加模型。
检测结果会定期刷新；收起工具、切换维度或报告超时后清除虚影。
聊天栏报告包含匹配数量、失败位置、期望和实际状态、检测器原错误码及子错误，以及部件数量、独占占用或模块依赖问题。
缺失位置只显示有限样本，另外报告未列出的数量。

## 配置

| 字段 | 含义 |
|---|---|
| 模块序号 | `0` 为本体；`1` 起按 `MultiblockControllerMetadata.modules()` 的声明顺序自动递增 |
| 重复次数 | 按蓝图重复段声明顺序填写逗号分隔的副本数；留空使用每段最小值 |
| 放置镜像 | 开启使用镜像，关闭使用普通布局；已有结构的检测结果不会改变此选择 |

模块序号与依赖拓扑顺序无关，也不是模块 ID。选择不存在的模块会报告越界。
重复次数的数量和各段范围必须符合所选蓝图；固定蓝图应留空。
本体与模块都锚定于被点击的控制器。放置镜像只选择施工布局，不限制结构检测；
检测流程统一比较本体与全部模块的普通／镜像整体，主体对称时也结合模块选择，不能单独翻转模块。
补建只处理选中的蓝图；依赖模块需分别选择并补建。本体未成型时，模块报告会明确提示。

工具报告分别显示实际结构的成型状态和所选施工目标的进度。
实际结构检测会自动判断重复次数与镜像；施工目标、虚影和补建严格使用工具配置。
即使重复段尚未建齐，也不会自动缩短施工目标或移动顶盖；已有结构的检测结果也不会改写放置镜像选择。
已有结构超出目标配置时，工具不会通过删除方块缩减结构。
增减重复次数可能使原顶盖占据新的目标位置，需要手动拆除冲突方块后继续补建。

## 放置与材料

服务端先读取世界并生成完整方案，保留已有且匹配的位置，只将空气位置列为新增目标。
已有方块即使可替换，也不会被覆盖；`#` 要求的位置有方块时也不会自动清除。
未知区块、权限限制、未找到合法候选或数量约束不成立，都会阻止整次补建。

生存模式按背包现有材料，从低到高选择本批能放置的目标。有多少可用材料就使用多少，
缺少某种材料不阻止使用其他材料；机器可以跨多次补料完成，不要求一次将所有材料装进背包。
每次反馈本批实际放置数量及剩余缺失数量。只消耗本批实际使用的默认组件方块物品，
避免消耗命名物品、保存了数据的机器或装有内容的容器。已有且匹配的位置不会重复扣料。
创造模式直接放置全部缺失目标，不消耗材料。当前不接入 AE 或其他外部仓储。

写入前检查权限、世界边界、建筑高度、位置占用和实体碰撞；写入后派发 NeoForge 多方块放置事件，
检查本批方块的支撑与匹配，并验证仍可按同一目标布局继续补齐；不要求本批施工后整台机器立即成型。
事件取消或验证失败时还原本批写入的位置；本批成功后才扣除对应材料并通知邻居，不回滚此前已经完成的施工。
工具使用蓝图提供的状态直接放置，不执行普通 `BlockItem` 的完整交互放置流程。
需要特殊放置逻辑、多位置联动或自定义方块实体数据的方块，应先验证其兼容性。

候选由配置扩展、规范状态和谓词候选方块依次提供，并根据现有部件数量与背包材料选择。
每个候选仍须通过蓝图谓词和属性规则，最后校验完整投影，候选列表本身不是可放置证明。
自定义谓词没有可枚举候选时，可通过扩展提供候选状态。
候选选择是逐位置启发式过程，不保证求出所有复杂全局约束下存在的材料组合。

## 接入与扩展

物品实现空的 `StructureTool` 标记接口，只把 `use`／`useOn` 转给 `StructureToolManager`，需要配置页时调用
`StructureToolConfigPage.open(player)`；配置页是玩家菜单，不绑定物品。虚影由 `StructureToolClient` 统一渲染。
Demo 的 `src/demo/java/net/ptcrys/topodemo/api/equipment/common/StructureToolItem.java`
只有交互转发，`BuiltinTools` 与 `BuiltinToolPanels` 提供物品及提示面板注册示例。
工具通过 NeoForge 的 `onItemUseFirst` 在交互权限事件结束后处理，
早于控制器激活和普通物品的放置捕获流程，无需额外注册 `MachineItemBehavior`。

其他 mod 在公共初始化阶段调用 `StructureToolOptions.register(...)` 添加配置。
客户端和服务端须注册相同的选项；字段使用所属 mod 的命名空间，Topo 不预设扩展字段。

```java
StructureToolOptions.register(new StructureToolOptions.Option() {
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("examplemod", "casing_style");
    }

    public Component label() {
        return Component.translatable("examplemod.tool.casing_style");
    }

    public String defaultValue() { return "standard"; }

    public List<String> choices() { return List.of("standard", "alternate"); }

    public String normalize(String value) {
        if (!choices().contains(value)) throw new IllegalArgumentException("Invalid style");
        return value;
    }

    public void configure(StructureToolOptions.Context context, String value,
                          StructureToolOptions.Settings settings) {
        if (value.equals("alternate")) {
            settings.candidates(cell -> List.of(Blocks.DIRT.defaultBlockState()));
        }
    }
});
```

`choices()` 非空时生成循环按钮，否则生成文本框。`displayValue(...)` 可将选项值映射为本地化文本。
`normalize(...)` 在服务端校验并规范化输入，非法值应抛出 `IllegalArgumentException`。
`configure(...)` 在服务端规划前执行，可按控制器、所选蓝图和模块序号调整重复次数、朝向或候选状态。
候选状态采用世界朝向，不能绕过原蓝图的要求。

若选项要参与结构检测，在 `require(...)` 中把配置值写入检测元数据工作区：

```java
public void require(StructureToolOptions.Context context, String value,
                    DataTransaction metadata) {
    StructureToolOptions.require(metadata, ExampleMetadata.CASING, value);
}
```

键必须由蓝图通过 `CellBinding.metadata(...)` 声明，`StructureToolOptions.require(...)` 会跳过未声明的键。
配置要求与探测事实分开保存：检测能力用 `metadata.required(key)`／`metadata.requirement(key)` 读取要求，
只有 `metadata.set(...)` 写入的值才计入已探测事实。
未识别的扩展配置会随工具数据保留。

## 使用限制

单个蓝图最大展开检测位置不超过 16,384；选择模块时，其依赖闭包的总位置数也受此上限约束。
会话锚点距离玩家最多 128 格，虚影模型只绘制距相机 128 格内的位置。
报告每 20 tick 最多刷新一次，80 tick 未收到新报告后客户端清除虚影。
配置字段最多 128 个，单值最多 256 字符。
刷新请求不携带客户端指定的世界位置或放置列表，服务端沿用最近一次右键确认的锚点并重新检查权限。
