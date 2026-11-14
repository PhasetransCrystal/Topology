# 客户端模块内部

面向框架开发者。客户端模块只处理表现与开发期探针，不承担内容注册、持久化或服务端行为。

## 职责与边界

- 负责：机器渲染、UI tooltip 注入、结构工具客户端预览、管道勘测渲染、开发探针。
- 不负责：内容注册、服务端逻辑、持久化；这些在 API/运行时模块。
- 客户端类只在客户端加载，不能出现在服务端公共路径。

## 关键类型与数据流

- `client/machine/render`：机器方块/BE 渲染与聚合状态。
- `client/machine/ui/tooltip`：机器 UI tooltip 注入。
- `client/structure`：结构工具预览、虚影与配置交互。
- `client/survey`：管道勘测状态与渲染。
- `client/debug`：tooltip、JEI 查询、UI 几何转储。

## 为什么这样设计

- 渲染/预览依赖客户端 API，与服务端注册分离，避免服务端加载客户端类。
- 开发探针隔离在 debug 包，只服务开发期，不作为稳定 API。
- 结构/勘测预览读取同步状态，不重新计算服务端结构。

## 与其他模块的联动

- 机器：渲染状态、UI 贡献、机器数据同步。
- 管道：勘测网络与客户端状态。
- 结构工具：服务端检测结果 → 客户端虚影/报告。
- 语言：UI 文本走语言模块；客户端不硬编码文案。

## 扩展规范

- 新渲染用 `MachineRenderComponent`/渲染策略，不在方块类写客户端分支。
- 新 UI 通过 `collectMachineUi` 贡献，不在客户端遍历机器内部。
- 新预览只读同步状态；不要重新执行结构检测。
- 探针放 `client/debug`，并标注开发期用途。
- 补 `docs-content/foundation/*` 中对应的公开 UI/工具说明。

## 常见坑与防止

- 在公共类引用客户端类：专用服务端会崩。
- 客户端重新计算服务端结构：状态不一致且开销高。
- 把调试探针当稳定 API：它们可能随开发变化。
- 在客户端写共享运行时表：应在服务端/common 侧完成。

## 文档

- [悬浮面板缓存](tooltip-cache.md)
- 公开说明见 [`docs-content/foundation/tooltip-panels.md`](../../docs-content/foundation/tooltip-panels.md) 与 [`docs-content/machine/machine-ui.md`](../../docs-content/machine/machine-ui.md)。
