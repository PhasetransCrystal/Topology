# 配方能力组合与 I/O 聚合

面向框架开发者。说明 `RecipeCapability` 如何把配方内容行为与机器资源端口解耦，以及 `TopoRecipe` 如何聚合同一能力的声明。公开扩展契约见 [`docs-content/recipe/recipe-capability.md`](../../docs-content/recipe/recipe-capability.md)。

## 能力拥有的行为

`RecipeCapability<I, O>` 对一种配方通道定义输入/输出值类型、两种序列化 codec、对应机器资源类型，以及输入匹配、输出空间检查、事务内消耗/产出和内容缩放。它还提供查找索引键、导入冲突键和动态并行规划所需的数量语义。

能力实例不是某台机器的端口，也不是配方类型。机器侧提供资源处理器；配方类型决定可用的配方通道集合；配方条目引用能力铸造的 `RecipeInputUse` / `RecipeOutputUse`，避免把任意对象伪装成该能力的内容。

## `TopoRecipe` 的聚合边界

`TopoRecipe` 分开保存开始阶段与逐 tick 阶段的输入/输出。构造时先保留声明顺序供序列化和展示，再按 capability identity 派生 grouped entries：

- 同一能力的多个输入交给一次 `matchInput(machine, contents)` 和一次 checked handler，能力可以处理共享库存的竞争需求。
- 输出同理按能力聚合，防止各条目独立检查时重复计算同一容量。
- tick 输入与 tick 输出再按能力合并成配对视图，使纯可行性检查遵守“先移除本 tick 输入，再检查输出空间”的顺序。
- 同一能力内内容身份由 `inputIdentity` / `outputIdentity` 定义；能力可定制配方去重语义。

## 搜索、检查和提交

搜索阶段使用能力的 `indexKeys` / `inputAmount` 构造反向候选索引，再调用 `matchInput` 与 `matchOutput` 做最终判断。开始配方时先纯检查开始输入与输出；随后所有开始输入在一个根 `Transaction` 中提交，任一能力失败则回滚整个输入消耗。配方完成时，所有最终输出也在一个根事务中提交。逐 tick 输入与输出则在同一事务中按“输入后输出”顺序提交。

逐 tick I/O 也先做能力级输入检查和输入移除后的输出检查，再应用变更。能力只有在 check-then-apply 与事务实现严格等价时才能选择 direct tick I/O；默认保留通用事务路径。`RecipeCapability` 默认的并行上限保守返回 1，扩展能力未实现数量规划时不会被推测性放大。

## Slotted 与聚合能力

`SlottedRecipeCapability` 进一步约束具有槽位的资源家族：必须同时实现预览槽数量、配方预览 widget 和机器实况槽 widget。物品/流体等逐槽资源使用该抽象；能源等标量或聚合资源直接继承 `RecipeCapability`，不伪造槽位布局。

## 为什么这样设计

- 机器资源存储和配方语义变化频率不同。端口处理“怎么访问/存放”，能力处理“某配方内容如何匹配、消耗、缩放、序列化”。
- 聚合后再检查，避免两份声明各自通过检查却在同一 handler 上竞争同一资源或容量。
- 声明数组与执行分组并存，分别满足稳定展示顺序和正确批量 I/O。
- 有条件的 direct path 是性能优化而非另一套语义；未证明等价时回退到事务实现。

## 常见坑

- 只实现 `matchInput` 而不实现 checked handler/缩放/编解码：能力契约是一组完整行为，不是单一谓词。
- 将端口类型判断写进具体配方类型：应由能力声明资源类型并通过机器资源视图访问。
- 逐项独立检查多个同能力输出：应先聚合，避免容量被重复计算。
- 用 `double` 缩放大整数并行数量：精确并行使用 long 专用方法，避免超过 `2^53` 后丢失精度。
- 无法缩放的内容静默返回原对象：应拒绝不支持的形状，不能制造输入/输出数量不对称。
- 未证明原子等价就启用 direct I/O：保留默认事务路径。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/recipe/capability/RecipeCapability.java`：能力行为契约。
- `src/main/java/net/ptcrys/topo/api/recipe/capability/SlottedRecipeCapability.java`：槽位能力契约。
- `src/main/java/net/ptcrys/topo/api/recipe/TopoRecipe.java`：内容条目与按能力聚合。
- `src/main/java/net/ptcrys/topo/api/recipe/RecipeSearchState.java`：运行时搜索索引与候选筛选。
- `src/main/java/net/ptcrys/topo/api/machine/component/RecipeLogic.java`：机器配方执行与端口协调。
