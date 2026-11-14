# 锅炉燃料与配方

面向 TopoDemo 维护者。本文说明 Demo 如何用 Topo 的机器组件、资源端口与配方视图组合燃料储备。

## 使用的 Topo 能力

- `MachineComponent`：行为 trait、数据字段、机器加载与 tick。
- `ResourcePort` / 标量资源端口：读取燃料、写入燃烧时间储备。
- `RecipeLogic`：配方搜索与执行；`RecipeInputUse` / 配方视图。
- 机器数据持久化与事务：储备写入、容器返还。

## Demo 如何组合

锅炉的登记配方只声明水输入和热量输出。`BoilerFuel` 从独立端口读取原版熔炉燃料或氢气，将其转换为燃烧时间储备；燃料、容器返还和储备写入使用同一事务。储备位于独立标量端口，由机器数据负责持久化。

`BurnTimeModifier` 为运行期配方视图添加每刻燃烧时间输入；各等级的消耗速率为每刻 1、2、4。原始配方及其展示不含燃料。运行时储备不足时，现有配方输入等待机制保留进度，储备与配方的每刻输出在同一事务或已验证的直接执行路径中处理。

## 验收状态

运行行为、容器返还及重载仍需游戏内验收；燃料种类、换算和锅炉配方是 Demo 的实现，不构成 Topo 通用契约。

## 相关

- 公开契约：[`docs-content/machine.md`](../../docs-content/machine.md)
- 框架内部：[`docs/machine/README.md`](../../docs/machine/README.md)
