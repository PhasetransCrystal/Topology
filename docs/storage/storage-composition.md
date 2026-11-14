# 组合也提供 StorageAccess

面向框架开发者。说明 `CompositeStorageAccess` 如何按顺序聚合多个访问入口，以及组合操作的失败和原子性边界。通用仓储契约见 [StorageAccess 仓储访问设计](README.md)。

## 组合行为

`CompositeStorageAccess` 自身实现 `StorageAccess`，构造时接收访问实例列表并保留传入顺序。单资源操作将前一个实例实际转移后的剩余数量传给下一个；批量操作将前一个 `StorageResult` 的 `remaining` 传给下一个，并合并已满足数量。需求清空时提前停止。业务调用方因此可以只持有一个访问入口，具体的先后顺序由组装方决定；组合器不预设背包、箱子或网络优先级。

批量操作遇到 `StorageAccessException` 时，组合器合并异常携带的已确认结果并以当前累计结果重新包装异常。其他运行时异常也会包装为 `StorageAccessException`，其中 `confirmed` 只包含组合器此前已确认的转移。单资源操作也会包装后端运行时异常，并报告进入失败后端之前已完成的数量。返回超出本次剩余需求的转移数量，或批量结果不守恒时，会抛出 `IllegalStateException`，因为该实现违反了仓储契约。

## 原子性与试算边界

组合执行按每个后端返回的实际结果推进，但不构成跨后端事务：此前已完成的取出或存入不会因后续后端失败而自动回滚。`confirmed` 表示组合器已确认的进度，不代表可撤销，也不能推断发生异常的后端是否进行了未报告的副作用。

`CompositeStorageAccess` 只组合 `StorageAccess` 的实际操作，不提供试算，也不检查不同入口是否指向同一个底层存储。只读限制、资源过滤等包装器可实现相同接口后加入组合；不能因此推断包装器具有保留、去重或事务能力。

源仓储取出再向目标仓储存入，是由两个访问实例组成的转运流程，超出单个访问操作的原子范围。目标实际接收不足时，转运方仍持有余量，应按其策略暂存或尝试存回；通用接口不静默丢弃，也不强制生成掉落物。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/api/util/CompositeStorageAccess.java`：顺序组合、结果累计与失败包装。
- `src/main/java/net/ptcrys/topo/api/api/util/StorageAccessException.java`：后端异常及已确认结果。
- `src/main/java/net/ptcrys/topo/api/api/util/StorageResult.java`：批量满足量与剩余需求。
