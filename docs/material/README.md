# 材料模块内部

面向框架开发者。材料模块负责材料注册、形态匹配、能力数据与批量派生。

## 职责与边界

- 负责：材料句柄、形态匹配、数据类型、后处理器、冻结后的内容查找。
- 不负责：具体形态的物品/方块外观与配方；由形态策略和后处理器实现。
- 形态是否适用由形态的 `appliesTo` 决定，材料不维护包含/排除名单。

## 关键类型与数据流

- `Material` / `MaterialRegistry`：材料句柄与冻结注册表。
- `MaterialFormResolver` / `MaterialFormOptions` / `MaterialFormTargets`：形态匹配、局部选项、外部条目覆盖。
- `MaterialStrategy` / `MaterialContentExecutor` / `MaterialContentContext`：形态生成与后处理器执行。
- `MaterialLookup` / `MaterialBinding`：冻结后的内容索引。
- 数据流：foundation 声明类型与形态 → `registerMaterials` 构建并写数据 → `registerMaterialContributions` 注入 → 关闭写入 → 匹配/校验/冻结 → 形态生成 → 后处理器。

## 为什么这样设计

- 用数据驱动匹配替代材料 ID 黑名单；新增材料只要带对应数据即可入选。
- 贡献窗口让其他插件能给已构建材料补数据，同时避免依赖插件登记顺序。
- 重复数据类型拒绝覆盖，保证同一能力只有一个来源。
- 形态生成与后处理分开，使“先生成物品，再批量派生配方链”的依赖明确。

## 与其他模块的联动

- 装备：读取材料数据（如工具属性）决定 `appliesTo` 并铸造物品。
- 机器：材料形态提供机器方块/物品；机器端口与资源类型由产品实现。
- 配方：材料形态提供输入/输出物品；材料后处理器批量产配方。
- 矿脉：宿主规则引用矿石形态；矿石方块由形态生成。
- 语言：材料显示名与形态条目名由 Builder / 材料句柄提供。

## 扩展规范

- 新数据类型/形态/后处理器必须在 `registerMaterialFoundation` 声明。
- 形态策略用 `appliesTo` 读取材料数据，不用材料 ID 名单；`.form(form, options)` 只配置选项。
- 数据用 `MaterialDataType` 的公开工厂创建，值不可变；不要原地修改共享对象。
- 跨插件补数据优先在 `registerMaterialContributions` 使用 `contributions.data(...)`。
- 补 `docs-content/material/*` 契约、本 README 与 Demo 示例（如适用）。

## 常见坑与防止

- 在 `registerMaterials` 注册新形态：形态表已冻结。
- 认为 `.form(...)` 会自动选中形态：它只配置局部选项与外部覆盖。
- 在形态匹配完成前读取 `forms()`：集合尚不完整。
- 用材料 ID 黑名单收窄形态：应完善数据与 `appliesTo`。
- 在冻结后继续写材料数据：写入关闭，会失败。

## 文档

- [形态解析和计划冻结](form-resolution-and-plans.md)：匹配、全量发布、计划校验和内容生成的阶段边界。
- [材料注册性能](material-registration-performance.md)
- 公开契约见 [`docs-content/material.md`](../../docs-content/material.md)。
