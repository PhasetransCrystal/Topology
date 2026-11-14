# 辅助模块内部

面向框架开发者。辅助模块提供无状态工具，不持有注册表，也不替代领域入口。

## 职责与边界

- 负责：ID、材料、标签、过滤、紧凑数字等纯工具。
- 不负责：注册、冻结、领域表写入；这些属于领域模块。
- 工具方法不持有跨调用状态，不缓存未冻结对象。

## 关键类型与数据流

- `IdHelper`：ID/路径处理。
- `MaterialHelper`：材料 id、形态与物品/流体查询。
- `TagHelper`：物品/方块/流体标签键与注册辅助。
- `ResourceFilterHelper`：资源过滤条件。
- `TopoCompactNumber`：紧凑数字显示。

## 为什么这样设计

- 把重复的键构造与查找逻辑集中，避免各模块各自拼 namespace。
- 工具无状态，调用顺序不影响注册结果。
- 需要注册时只做排队/键构造，实际注册仍由领域入口完成。

## 与其他模块的联动

- 材料：`MaterialHelper` 连接材料 id、形态与内容。
- 数据生成：`TagHelper` 构造标签键，注册仍由 Builder 完成。
- 管道/机器：`ResourceFilterHelper` 供资源过滤使用。
- 语言/UI：`TopoCompactNumber` 只格式化，不负责翻译。

## 扩展规范

- 新工具优先纯函数；需要上下文时显式传参，不用全局单例缓存。
- 涉及注册的方法只返回键/排队，不在工具方法里冻结或初始化领域表。
- 公开辅助方法要补 `docs-content/foundation/helpers.md`。
- 不要在 helper 中读取 `.get()` 或依赖游戏对象已绑定。

## 常见坑与防止

- 在 helper 中隐式注册：调用点不可预期，且可能越过冻结边界。
- 缓存材料/条目对象：绑定前为空或过期。
- 用 helper 替代领域入口：注册归属会丢失。
- 把格式化工具用于翻译：文本仍应走语言模块。

## 文档

公开辅助见 [`docs-content/foundation/helpers.md`](../../docs-content/foundation/helpers.md)。
