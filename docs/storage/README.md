# StorageAccess 仓储访问设计

面向框架开发者。按主题查阅仓储访问的资源模型、批量操作、适配、组合与验收。

## 职责与边界

- 负责：以资源身份 + `long` 数量表达混合批量请求，返回实际满足与剩余需求；适配与组合封装后端差异。
- 不负责：业务核算、结构判断、世界掉落、材料返还语义；这些由调用方处理。
- 通用模型不出现施工、蓝图、机器数据域、槽位或玩家背包顺序。

## 关键类型与数据流

- `StorageAccess`：批量 `extract` / `insert`；单资源便捷入口。
- `StorageResult`：实际满足与剩余需求。
- `InventoryStorageAccess` / `CompositeStorageAccess` / `AeStorageAccess`：具体适配与组合。
- 数据流：调用方提交需求 → 实现按权限/连接处理 → 返回实际与剩余 → 组合器把剩余交给下一个访问实例。

## 为什么这样设计

- 统一批量协议让多方块、机器供料、流体运输共用同一接口，不暴露槽位或后端类型。
- 返回剩余需求而不是承诺全部满足，支持部分转移与组合链。
- 组合实例与单实例同契约，调用方不必知道背后是箱子、背包还是 AE 网络。
- 业务语义留在调用方，避免通用层偷偷掉落或改变资源身份。

## 与其他模块的联动

- 机器/多方块：工具先由结构元数据生成需求，再调用 `StorageAccess`；仓储接口不接收结构元数据。
- AE2：`AeKeyResourceAdapter` 保留资源身份，`AeStorageAccess` 定向访问网络。
- 物品/流体：通过资源类型与组件表达；适配层负责数量与身份转换。
- 集成：AE 的 `MODULATE` 非事务，回滚由专用处理负责。

## 扩展规范

- 新适配实现 `StorageAccess`，明确权限、目标解析、失效检查与数量转换。
- 新组合实现同一契约，按剩余需求推进，不重复扣减、不自行加业务规则。
- 需要试算时使用独立扩展接口；不要把试算结果当作预留。
- 通用层不默认掉落物品；未接收部分由调用方管理。
- 补 `docs-content/foundation/storage-access.md` 状态说明与验收项。

## 实现状态

代码中已有 `StorageAccess`、`StorageResult`、`StorageAccessException`、`InventoryStorageAccess`、`CompositeStorageAccess` 与 AE 适配。组合器支持按序执行和聚合结果，但没有试算、跨后端事务或同底层目标去重；适配发现/创建入口及公开稳定性尚未完成。

## 常见坑与防止

- 把 AE `MODULATE` 当事务：它直接改网络，失败项状态可能未知，不能自动重试。
- 认为试算会预留资源：`SIMULATE` 不预留，执行仍按实际结果。
- 同一底层存储多个入口重复试算计数：组合层要按剩余需求推进。
- 把当前接口当稳定公开契约：下游用法尚未定稿。

## 文档

- [通用仓储边界](storage-boundaries.md)、[资源](storage-resources.md)、[操作](storage-operations.md)、[适配](storage-adapters.md)、[组合](storage-composition.md)、[多方块接入](storage-multiblock.md)、[范围与验收](storage-scope.md)。AE2 具体实现见[集成专题](../integration/ae2-resource-and-stocking.md)。
- 当前公开状态见 [`docs-content/foundation/storage-access.md`](../../docs-content/foundation/storage-access.md)。
