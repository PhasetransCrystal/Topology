# 装备模块内部

面向框架开发者。装备模块按“装备种类 × 材料”铸造物品，并挂接可选行为。

## 职责与边界

- 负责：装备种类注册、材料适用判断、物品铸造、不可变物品记录、可选机器行为。
- 不负责：材料数据本身的定义；策略读取材料数据决定是否适用。
- 装备物品的具体属性、模型与配方仍由策略/产品补充。

## 关键类型与数据流

- `Equipment` / `EquipmentRegistry`：种类与已生成物品记录。
- `EquipmentStrategy`：`appliesTo` 判断适用，`register` 铸造唯一物品，可选 `machineBehavior()`。
- `MachineItemBehavior`：右键机器时的行为契约。
- 数据流：`registerEquipmentKinds` 冻结种类表 → 逐 (材料 × 种类) 调 `appliesTo` → 匹配才 `register` → 发布不可变记录 → common deferred 挂接行为。

## 为什么这样设计

- “种类 × 材料”把装备定义与材料属性解耦：新增材料不需要改装备种类。
- 策略只返回 item entry，框架统一发布记录，避免漏登记/重复登记。
- 行为通过可选接口挂接，避免在装备物品里堆机器分支。

## 与其他模块的联动

- 材料：策略读取材料数据（工具/护甲属性）决定适用与数值。
- 机器：`MachineItemBehavior` 让扳手/调节器等装备参与机器交互。
- 语言/工具提示：条目名由 Builder 提供；装备面板通过运行时贡献接入。
- 配方：装备物品可作为配方输入/输出；配方模块不关心装备种类。

## 扩展规范

- 新装备种类实现 `EquipmentStrategy`，用 `appliesTo` 读取材料数据。
- `register` 只创建并返回 item entry；属性、模型、配方由策略或产品补充。
- 需要机器交互时实现 `machineBehavior()`，不要在物品类里写机器分支。
- 跨插件扩展材料时使用材料的 `context.core()`，保留其归属。
- 补 `docs-content/equipment.md` 契约与 Demo 示例（如适用）。

## 常见坑与防止

- `appliesTo` 返回 true 但 `register` 返回 null/重复物品：记录发布失败或重复。
- 在 `appliesTo` 里读取游戏对象：此时只有材料与数据，没有绑定物品。
- 在物品未绑定时 `.get()`：使用 `record.entry()`，绑定后再读取。
- 把装备行为写成全局监听：优先实现 `MachineItemBehavior`。

## 文档

公开契约见 [`docs-content/equipment.md`](../../docs-content/equipment.md)。
