# 管道网络运行时

面向框架开发者。说明持久化节点记录如何派生为每维度运行时和同类管道连通分量，以及拓扑变化与端口变化如何分别更新状态。下游策略契约见 [`docs-content/pipe/pipe-strategy.md`](../../docs-content/pipe/pipe-strategy.md)。

## 持久化数据和运行态分工

- `PipeNetworksSavedData` 是每维度权威节点记录：定义 ID、玩家侧意图、有效角色和提取端口策略/过滤配置；另维护 chunk → 节点索引。
- `PipeLevelRuntime` 是每个 `ServerLevel` 的主线程协调者：接收方块/邻居/chunk 事件，调度每 tick 提取，并在卸载时取消 tick 句柄。
- `PipeTopology` 负责角色重算、拓扑失效和网络查询。
- `PipeNetwork<R>` 是同资源类型、同 LINK 连通分量的派生运行结构，不写入存档。它保存节点位置索引、邻接表、吞吐、端口 capability cache、路径搜索缓冲、预留账本和展示流量双缓冲。

## 连通分量创建

查询网络时，运行时从已记录节点 flood fill，只沿双方都标记为 `LINK` 的边遍历。填充阶段为节点分配紧凑整数下标，构造邻接表并把相邻 `EXTRACT` / `DESTINATION` 侧包装为 capability cache。不同资源定义不会进入同一个网络；网络实例按需延迟创建。

执行端口的分发策略仍由 `PipeDefinition`/端口配置选择。网络为策略提供批次上下文、资源移动入口和预算约束；策略选择目标与分配量，网络负责路径、逐节点吞吐、事务和账本。

## 两种变更路径

- **LINK 集合或节点成员变化**：旧连通分量退役，后续查询从保存记录重新 flood fill。网络形状不做局部拼补，避免拆分/合并后邻接与索引错位。
- **节点仍在同一分量、只改变端口角色/config**：就地刷新该节点的 extractor/destination 列表并增加 revision；提取端路径表在使用时惰性重算。聚合窗口变化时重建未来预算账本。

传输回调可能重入方块事件；`PipeLevelRuntime` 在 tick 过程中延迟执行结构刷新和网络重建，等当前遍历结束再处理，避免迭代期间改变网络集合。

## 账本与容器边界

`PipeNetwork` 的 `adjacency` 和路径搜索数组是拓扑缓存；`PipeFutureLedger` 记录聚合窗口内的节点未来吞吐预约；端口事务 session 将一次 extraction port 的多个移动合并在一个 transaction 中；当前/上一展示窗口为 Jade 统计服务。它们都是运行态派生信息，保存数据只持久化重建它们所需的配置。

## 为什么这样设计

- 存档只保存用户可配置且需跨重启保留的状态；可从节点记录计算的图、缓存和预算不重复持久化。
- 共享连通分量结构可让抽取端共同受节点吞吐限制；多条剩余 BFS 路径承载节点不相交通道，路径预算与流量统计复用同一节点索引。
- 拓扑变化整网退役、端口变化局部刷新，在正确性和常见配置操作成本间划清边界。
- 主线程约束简化世界 capability 访问和图修改；重入只延迟结构变化，不把整个运行时搬到异步线程。

## 与其他模块联动

- 管道定义持有 `PipeResourceProfile`、每节点吞吐、策略 offer 与聚合窗口。
- 机器资源类型提供相邻目标的 block capability；管道不识别机器实现类。
- TickHub 驱动维度运行时；Jade 读取网络流量窗口；勘测器通过运行时读取连通分量统计。
- `PipeDistributionContext` 暴露策略可用的预算、移动和聚合批次契约，内部图索引不属于下游 API。

## 常见坑

- 将 `PipeNetwork` 视为存档：它随拓扑失效重建，持久配置在 `PipeNetworksSavedData`。
- 将单侧 LINK 当成连接：Flood fill 要求相邻两端互相 LINK。
- 端口 role 变化后退役整个网络：该路径只需刷新端口并 bump revision；拓扑边变化才退役组件。
- 在分发策略里绕过网络预算直接访问世界容器：会破坏路径吞吐、统计和事务约束。
- 在传输回调中立即改图：结构变更需在当前 tick 遍历结束后应用。
- 只验证方块数据生成：连通图、吞吐公平、重入更新与卸载清理需要运行验证。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/pipe/network/PipeNetworksSavedData.java`：持久化节点记录和 chunk 索引。
- `src/main/java/net/ptcrys/topo/api/pipe/network/PipeLevelRuntime.java`：每维度事件与 tick 协调。
- `src/main/java/net/ptcrys/topo/api/pipe/network/PipeTopology.java`：角色和拓扑更新。
- `src/main/java/net/ptcrys/topo/api/pipe/network/PipeNetwork.java`：连通分量运行容器。
- `src/main/java/net/ptcrys/topo/api/pipe/network/PipeFutureLedger.java`：窗口吞吐预约。
