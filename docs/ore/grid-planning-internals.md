# Grid 矿脉规划内部流程

面向框架开发者。说明 Grid 模式从冻结矿脉表生成确定性区块计划的过程，以及形状、宿主、密度、暴露和冲突策略的组合边界。下游参数契约见 [`docs-content/ore/ore-strategies.md`](../../docs-content/ore/ore-strategies.md)。

## 参与者与执行位置

- `OreVein` 保存材料权重条目、环境和放置配置，并引用模式、形状、空气暴露及冲突策略。
- `OreVeinMode` 决定矿脉进入 Feature 通道还是 Grid 规划通道；Grid 模式通过 `collectGrid` 向 planner 提供有效声明。
- `OreVeinPlanner` 在矿脉/维度索引上纯规划区块，不修改世界。Feature 模式不进入此 planner。
- `OreBlockResolver` 把选中的材料和宿主允许生成的形态解析为实际方块；材料形态和掉落定义仍属于材料/产品侧。

## 每区块规划步骤

1. 构造 planner 时调用各矿脉模式的 `collectGrid`，拒绝被收集为 Grid 但 placement 不是 `OrePlacement.Grid` 的声明；按维度建立候选表，并为每条矿脉编译权重表。
2. 查询区块时，根据半径、随机偏移和网格大小枚举可能覆盖此区块的 origin cell；同一 cell 的候选矿脉按权重确定性选一条。
3. cell 的 seed 由世界 seed、维度标识、cell 坐标及用途 salt 派生；中心偏移、矿脉条目、密度和暴露判定不依赖调用顺序。
4. 仅处理与目标区块相交的放置，并按优先级、矿脉 ID、中心坐标排序，保证重叠冲突处理顺序稳定。
5. 对区块内候选方块按顺序应用 Y 范围、形状 `contains`、密度、宿主规则、空气暴露策略，再按矿脉条目权重选材料并解析该宿主允许的生成形态。
6. 同一位置若多个矿脉都生成候选块，由新候选的冲突策略结合优先级决定能否替换已有计划。最终返回不可变的 `PlannedOreBlock` 列表供后续生成阶段消费。

## 为什么规划与放置分开

planner 输入的是 world seed、dimension、chunk 和只读既有方块查询；输出是确定的计划列表。这样可以在不提前改世界的情况下测试冲突、预算与生成选择，也避免策略因被调用次数或顺序变化而随机漂移。对区块扫描数和候选 cell 数设预算，避免极端半径/偏移将规划成本放大到不可控。

## 联动边界

- 材料模块提供材料形态；`OreHostRule.generatedForm()` 限定该宿主允许解析的矿石形态。
- mode 的显示配置在冻结时校验，JEI/客户端显示由 `OreVeinDisplay` 提供；显示不能反向决定生成规则。
- Feature 继续由数据生成桥注册原版 feature；Grid 走运行时 planner，两者不可只凭 placement 参数互换。
- 环境维度规则建立 planner 的维度索引；宿主策略检查当前位置原有方块是否可替换。

## 常见坑

- 把随机数绑定到循环顺序：重排候选或增加无关矿脉会改变世界结果；随机种子应绑定稳定坐标和用途。
- 先选材料再检查宿主/暴露：会为最终丢弃的位置执行无用解析，也容易造成不同策略次序影响语义。
- 将冲突判断只写成固定优先级比较：替换权属于候选 Grid 的 `OreConflictPolicy`，优先级只是输入之一。
- 在 planner 里直接修改世界：规划必须返回值，世界写入由生成入口控制。
- Grid 声明未安装在运行时 planner：已注册矿脉并不等于会生成；Feature bridge 和 Grid planner 是不同接线责任。
- 放宽半径、偏移、网格间距后未重算候选/扫描预算：规划器会在超过预算时明确失败。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/ore/engine/OreVeinPlanner.java`：维度候选、cell 选择、方块计划。
- `src/main/java/net/ptcrys/topo/api/ore/engine/OreGridMath.java`：坐标稳定的 seed 和混合函数。
- `src/main/java/net/ptcrys/topo/api/ore/engine/PlannedOreBlock.java`：规划输出。
- `src/main/java/net/ptcrys/topo/api/ore/OreHostRule.java`、`OreEnvironment.java`：宿主与维度环境。
