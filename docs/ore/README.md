# 矿脉模块内部

面向框架开发者。矿脉模块把环境、矿脉成分、放置模式、形状、策略、显示与世界生成桥组合起来。

## 职责与边界

- 负责：维度/宿主环境、矿脉声明、Feature/Grid 放置、形状、暴露/冲突策略、JEI 显示、Feature 数据生成桥、Grid 运行时规划。
- 不负责：具体矿石方块与掉落；这些由材料形态与产品数据决定。
- 库不内置模式、形状、暴露或冲突策略；这些由产品实现并校验。

## 关键类型与数据流

- `OreEnvironment` / `OreDimensionRule` / `OreHostRule`：生成环境。
- `OreVein` / `OreVeinEntry` / `OreVeins`：矿脉与冻结快照。
- `OrePlacement`：`Feature` 或 `Grid` 配置。
- 子包：`mode`、`shape`、`policy`、`display`、`engine`、`datagen`。
- 数据流：foundation 声明形状/模式/暴露/冲突 → 显示注册并冻结校验 → 矿脉声明并校验放置 → datagen 安装 Feature 桥；Grid 在运行时规划。

## 为什么这样设计

- 环境与策略组合让新维度、宿主、形状、暴露规则不需要改生成引擎。
- 每种模式强制配显示，保证 JEI 预览与生成语义同步。
- Feature 走原版 feature 桥，Grid 走运行时规划，分别复用原版生成与确定性网格。
- 矿脉声明只描述数据，实际放置/冲突判断在对应策略回调中执行。

## 与其他模块的联动

- 材料：宿主规则引用矿石形态；矿石方块与掉落由材料形态/数据提供。
- 语言：矿脉、环境、模式、宿主显示名通过语言模块。
- JEI/客户端：`OreVeinDisplay` 构建预览；显示表冻结时双向校验。
- 数据生成：Feature 通过 `ore.featureDatagen()` 安装 bootstrap；产物由数据生成配置输出。

## 扩展规范

- 新形状/模式/暴露/冲突在 `registerOreFoundation` 声明；模式要明确接受 Feature 还是 Grid。
- 新矿脉在 `registerOreVeins` 声明；放置参数在 `build` 内校验。
- 每种模式都在 `registerOreDisplays` 注册显示；不要在运行时临时查找。
- Feature 模式在 `registerOreDatagen` 安装桥；Grid 模式由运行时规划器处理。
- 补 `docs-content/ore/*` 契约与矿脉验收文档。

## 常见坑与防止

- 新模式没有显示：显示表冻结时启动失败。
- 把 Feature 与 Grid 的参数混用：模式 `validate` 应拒绝不匹配的放置。
- 认为声明矿脉就会生成：Feature 还需要 datagen 桥，Grid 还需要运行时规划。
- 在 `build` 外修改矿脉数据：矿脉表冻结后不可写。
- 只做编译验证：世界生成、JEI 预览与冲突策略需要游戏内验收。

## 文档

- [Grid 矿脉规划内部流程](grid-planning-internals.md)：确定性 cell 选择、方块规划和冲突顺序。
- 公开契约见 [`docs-content/ore.md`](../../docs-content/ore.md)。
