# 材料形态解析与计划冻结

面向框架开发者。说明材料数据、形态策略、逐材料计划和内容执行如何组合；公开注册契约见 [`docs-content/material/material-form.md`](../../docs-content/material/material-form.md)。

## 对象与所有权

- `MaterialForm` 是注册表中的稳定形态句柄，持有适用谓词、所需材料级数据提示与 `MaterialFormStrategy`。
- 每个 `Material` 的 `MaterialStrategy` 持有该材料的数据、选项、最终形态列表和逐形态 `MaterialFormPlan`。
- `MaterialFormPlan` 表示一个 material/form pair 的冻结执行计划，固定输出键和外部目标，并提供校验与注册动作。
- `MaterialContentExecutor` 只执行计划，不重新匹配形态，也不修改材料状态。

## 解析和执行顺序

1. foundation 阶段注册数据类型、形态及其策略；内容声明阶段构造材料并写入全局或形态作用域数据。
2. 材料贡献窗口关闭后，`MaterialFormResolver` 按形态 ID 排序候选形态。`appliesToData(...)` 只提供候选筛选位图；最终是否匹配仍由 `appliesTo(material)` 决定。无提示形态会检查全部材料。
3. resolver 先计算所有材料的完整形态集合，再统一发布；匹配谓词执行期间不能读取正在计算中的其他材料形态集合。
4. 所有形态集合发布后，逐 material/form 调用 `strategy.plan(material, form)`，生成计划表。此时计划可检查其他材料的已解析形态，但不得改变数据或选项。
5. 冻结校验分别检查材料数据、形态作用域数据、计划输出冲突及 `plan.validate()`；后处理器激活组合也在冻结校验阶段验证。
6. 内容阶段先执行全部形态计划的 `register(context)`，然后执行激活的材料后处理器。运行时查找使用冻结计划记录的输出与目标。

## 为什么需要两阶段发布

形态集合由材料数据决定，而一个形态计划可能需要引用另一种形态的输出。如果一边匹配一边建计划，策略会看到不完整的全局状态。先完成匹配并发布全部集合，再建计划，使策略依赖显式且结果确定；计划冻结又让校验与注册使用同一组输出键和外部目标。

## 与其他模块联动

- `MaterialDataType` 为装备适用判断、形态匹配和计划校验提供材料能力数据。
- `MaterialFormPlan.outputs()` 为 `MaterialLookup` 建索引；外部目标通过 plan 的 `targets()` 捕获，运行时绑定对象延迟到注册表可用后解析。
- 装备策略读取材料数据；配方后处理器消费已经生成的材料形态；矿脉宿主规则引用矿石形态。
- 材料后处理器不是形态策略的替代品：形态负责一个 material/form 的内容，后处理器负责跨材料或批量派生内容。

## 常见坑

- 把 `appliesToData` 当作最终匹配条件：它只是跳过明显不满足数据提示的候选，谓词仍是权威条件。
- 在形态 predicate 中依赖其他形态已存在：resolver 尚未发布任何材料的最终集合。
- 在 `plan()` 中注册物品/方块或改材料数据：计划构造只捕获声明与目标；注册和变更窗口已分开。
- 在计划里延迟重新计算输出 ID：会让验证、注册与查找观察到不同声明；应在计划创建时固定输出和目标。
- 把后处理器调用写进每个形态策略：同一材料/处理器组合的激活与批量执行由框架统一管理。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/material/MaterialFormResolver.java`：匹配索引、两阶段发布与计划创建。
- `src/main/java/net/ptcrys/topo/api/material/MaterialContentExecutor.java`：冻结计划的内容执行。
- `src/main/java/net/ptcrys/topo/api/material/form/MaterialFormPlan.java`：计划输出、目标、验证和执行契约。
- `src/main/java/net/ptcrys/topo/api/material/MaterialStrategy.java`：逐材料数据、形态和计划持有者。
