# AE2 资源访问与配置输入窗口

面向框架开发者。说明 AE2 key 与 Topo resource 的映射、定向网络存取、已配置输入窗口及事务边界。AE 样板配方内容映射另见[AE 样板内容转换](ae-pattern-conversion.md)；通用访问协议见 [`docs/storage/README.md`](../storage/README.md)。

## 两类入口

- `AeStorageAccess` 是通用批量访问入口：对每个请求资源调用 `AeKeyResourceAdapter` 映射为 `AEKey`，再向当前网络执行提取或存入。它按请求定向访问，不枚举网络库存，也不缓存库存快照。
- `AeStockingResourceHandler` 是绑定到机器端口的虚拟输入窗口：配置槽位定义可见资源与目标数量，handler 根据在线网络库存、配置上限和本机在途提取量计算当前可提取量；插入一律拒绝。

两者不要混为一个接口：前者按调用方给定的批量需求访问网络；后者把指定资源的网络存量呈现成机器配方可读取的有限输入端口。

## 资源映射与网络访问

`AeKeyResourceAdapter` 保留 Topo `Resource` 的身份和数据组件，负责映射可支持资源与 `AEKey`。扩展资源要注册映射；无法映射的资源在访问中视为不满足，不得误转成物品或流体。多个 Topo resource 若映射到同一 AE key，真实库存自然共享，不可将独立模拟数量相加。

`AeStorageAccess` 在实际批量操作首次遇到可映射资源时解析一次网络访问器，并复用至该批剩余项目。空请求或全不支持的资源不会无谓查网络。AE 的 `MODULATE` 直接改变网络，不参与 NeoForge 事务；异常携带此前已确认的转移，但失败项的副作用状态可能未知，不能自动重试或声称整批可回滚。

网络中的物品/流体与容器物品中的内容物是不同供给路径。例如水需求既可能由 AE 原生流体键满足，也可能由水桶等物品容器满足。后者还要管理容器提取、内容物转换和容器返还；当前 AE 网络容器候选发现尚未实现，不能仅将物品键改写成流体键。

`AeNetworkAccessor.available()` 是与实际供电提取路径对齐的 live 查询，成本可能遍历网络存储；`availableCached()` 使用网络 tick 缓存，性能较低但可能滞后一个 AE 网络 tick。只有能容忍短暂陈旧的周期性探测才使用缓存值，不能用它替代需要同 tick 一致性的 handler/recipe 判断。

## Stocking handler 的事务处理

`AeStockingResourceHandler` 从配置槽位计算每种资源的总上限，扣除当前事务中的在途提取，再与网络可用量取较小值，作为机器可见输入。执行提取前先模拟，再执行实际提取；成功的资源记录到事务 journal。事务回滚时尝试把资源退回网络；网络拒收的部分放入待返还队列，后续继续处理。

它是专门的输入窗口，不是网络库存镜像，也不承担通用输出、完整资源浏览或跨网络操作事务。槽位配置持久化与网络资源本身分开；加载配置只使派生缓存失效，不应当作新的玩家配置变更通知。

事务 journal 回滚时尝试返还已提取资源；若网络拒收，资源留在 handler 的待返还队列中继续处理。配置反序列化只恢复槽位设置并标记派生缓存失效，不触发运行时配置变更通知。

## 联动边界

- AE2 机器节点和 `AeNetworkAccessor` 提供在线网络入口；业务调用层通过 `AeStorageAccess` 或具体 `AeStockingResourceHandler` 使用它。
- 机器资源端口可包装 stocking handler，让 `RecipeLogic` 看见配置的虚拟输入库存。
- JEI AE2 样板转移负责配方内容映射和编码，不等同于库存访问，详见[样板转换专题](ae-pattern-conversion.md)。

## 常见坑

- 将 `MODULATE` 当作 NeoForge transaction：它已改变 AE 网络。
- 出错后自动重试整批：已确认的部分可能已转移，失败项状态也可能未知。
- 把 stocking 窗口视为完整网络库存：它只显示配置槽位允许的输入目标。
- 用缓存库存判断需要立即正确的可提取量：缓存允许一个网络 tick 的陈旧。
- 将容器物品中的流体与网络原生流体视为同一条资源路径：容器发现、容器回收需要单独适配。

## 实现位置

- `integration/ae2/AeKeyResourceAdapter.java`、`AeResourceKeyResolver.java`：资源 key 映射。
- `integration/ae2/AeStorageAccess.java`、`AeNetworkAccessor.java`：定向仓储操作及网络 facade。
- `integration/ae2/AeStockingResourceHandler.java`、`AeConfigSlot.java`：配置输入视图与事务退款。
- `integration/ae2/AeStockingPort.java`：机器端口组装与配置持久化。
